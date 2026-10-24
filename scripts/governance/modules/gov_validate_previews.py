#!/usr/bin/env python3
"""GOV-PREVIEW-001: 交付屏必须有 Compose 预览。

## 为什么需要这条门禁

20+ 个功能屏已交付，但设计稿与实现的差异从未被肉眼确认过。没有 `@Preview`，
"看起来和原型一致"就只能靠嘴说。本模块把"每个对外呈现式 composable 至少有一个
预览"变成机器可判定的规则，让新屏不会悄悄掉队。

## 判定口径

- 扫描 `feature/**` 下所有**对外**（非 private / internal）的 `@Composable`
  且函数名以 `Screen` / `Flow` / `Route` 结尾的函数。
- **排除接线层**：参数里出现 `ViewModel` 类型或以 `viewModel(` 注入的，属 ViewModel
  接线函数，预览时无法构造——不要求预览（它们本身不承载布局）。
- 同模块（`feature/<模块>/` 及其子目录）内，只要有一个被 `@Preview` 标注的函数
  在其函数体里调用了该屏，即视为已覆盖。预览可放在同文件，也可放在
  `<Module>Previews.kt`。
- 缺一个即失败，输出 `文件:屏名 — 缺预览`。

## 盲点（明确不覆盖）

- 不校验预览是否"好看"或是否覆盖空态/错误态——只保证入口存在。
- 不做 Kotlin 语义分析：靠括号/字符串感知的文本扫描，极端写法（例如把屏函数名
  当字符串字面量传递）可能被误判为已覆盖。
- 只治理 `feature/**`；`ui/shell`、`ui/components` 不在范围内。
"""
import re
from pathlib import Path

from .gov_validate_core import ROOT, errors

FEATURE_REL = Path("app") / "src" / "main" / "kotlin" / "com" / "xcwk" / "scalehub" / "feature"
NAME_SUFFIXES = ("Screen", "Flow", "Route")

FUN_RE = re.compile(r"^((?:private|internal|public)\s+)?fun\s+([A-Z][A-Za-z0-9]*)\s*\(", re.MULTILINE)
CALL_RE = re.compile(r"\b([A-Z][A-Za-z0-9]*)\s*\(")

# 向上收集注解时遇到这些行即停止（上一个声明结束或文件头）。
_STOP_PREFIXES = (
    "package ", "import ", "fun ", "}", "class ", "object ", "val ", "var ",
    "typealias ", "enum ", "data ", "sealed ", "companion ", "override ",
    "internal ", "private ", "public ", "protected ", "abstract ", "open ",
)


def _blank_literals(text: str) -> str:
    """把字符串字面量与注释替换成等长空白，保留换行与位置以便做括号配对。"""
    out = []
    i, n = 0, len(text)
    while i < n:
        ch = text[i]
        if ch == "/" and i + 1 < n and text[i + 1] == "/":
            j = text.find("\n", i)
            j = n if j == -1 else j
            out.append(" " * (j - i))
            i = j
            continue
        if ch == "/" and i + 1 < n and text[i + 1] == "*":
            j = text.find("*/", i + 2)
            j = n if j == -1 else j + 2
            out.append("".join("\n" if c == "\n" else " " for c in text[i:j]))
            i = j
            continue
        if text.startswith('"""', i):
            j = text.find('"""', i + 3)
            j = n if j == -1 else j + 3
            out.append("".join("\n" if c == "\n" else " " for c in text[i:j]))
            i = j
            continue
        if ch == '"':
            j = i + 1
            while j < n and text[j] != '"':
                j += 2 if text[j] == "\\" else 1
            j = min(j + 1, n)
            out.append(" " * (j - i))
            i = j
            continue
        out.append(ch)
        i += 1
    return "".join(out)


def _balanced(clean: str, start: int, opener: str, closer: str) -> int:
    """clean[start] 应为 opener；返回配对 closer 的下标，找不到返回 -1。"""
    depth = 0
    for i in range(start, len(clean)):
        c = clean[i]
        if c == opener:
            depth += 1
        elif c == closer:
            depth -= 1
            if depth == 0:
                return i
    return -1


def _annotations_above(lines, idx, max_up: int = 12):
    """收集 idx 行上方的注解块（可跨行），最多向上 12 行。"""
    ann = []
    k = idx - 1
    steps = 0
    while k >= 0 and steps < max_up:
        s = lines[k].strip()
        if not s:
            k -= 1
            steps += 1
            continue
        if s.startswith("@"):
            ann.append(s)
            k -= 1
            steps += 1
            continue
        if s.startswith((")", ",")) or s.endswith(","):
            # 跨行注解的续行
            ann.append(s)
            k -= 1
            steps += 1
            continue
        break
    return " ".join(reversed(ann))


def scan(root: Path = ROOT):
    """返回 (屏列表, 已覆盖屏名集合)。

    屏列表元素：(相对路径, 屏名, 模块名)。
    """
    feature_root = Path(root) / FEATURE_REL
    screens = []
    covered = set()
    if not feature_root.is_dir():
        return screens, covered

    for path in sorted(feature_root.rglob("*.kt")):
        if "build" in path.parts:
            continue
        text = path.read_text(encoding="utf-8")
        if "fun " not in text:
            continue
        clean = _blank_literals(text)
        lines = clean.split("\n")
        # 每行在 clean 中的全局起始偏移，供括号配对使用
        starts = [0]
        for line in lines[:-1]:
            starts.append(starts[-1] + len(line) + 1)
        module = path.relative_to(feature_root).parts[0]
        rel = path.relative_to(root).as_posix() if Path(root) != ROOT else path.relative_to(root).as_posix()

        for idx, line in enumerate(lines):
            m = FUN_RE.match(line)
            if not m:
                continue
            ann = _annotations_above(lines, idx)
            if "@Composable" not in ann:
                continue
            name = m.group(2)
            open_paren = starts[idx] + line.index("(", m.start(2) + len(name))
            close_paren = _balanced(clean, open_paren, "(", ")")
            if close_paren == -1:
                continue
            params = clean[open_paren + 1:close_paren]

            if "@Preview" in ann:
                brace = clean.find("{", close_paren)
                if brace != -1:
                    end = _balanced(clean, brace, "{", "}")
                    if end != -1:
                        covered.update(CALL_RE.findall(clean[brace:end]))
                continue

            if m.group(1) in ("private ", "internal "):
                continue
            if not name.endswith(NAME_SUFFIXES):
                continue
            if "ViewModel" in params or "viewModel(" in params:
                continue  # 接线层，不承载布局，无法在预览里构造
            screens.append((rel, name, module))

    return screens, covered


def collect_missing(root: Path = ROOT):
    """返回 [(相对路径, 屏名)]，只含缺少预览的屏。"""
    screens, covered = scan(root)
    return [(rel, name) for rel, name, _ in screens if name not in covered]


def check_all() -> None:
    for rel, name in collect_missing():
        errors.append(f"{rel}:{name} — 缺预览")
        print(f"  [FAIL] {rel}:{name} — 缺预览（新增 *Previews.kt 并加 @Preview）")
