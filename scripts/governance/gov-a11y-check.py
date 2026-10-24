#!/usr/bin/env python3
"""
gov-a11y-check.py — 无障碍静态门禁（T-070）

无障碍没有原型文案可比对，验收只能落在**可机械检测的行为断言**上。
本脚本不跑编译、不连设备，只做 Compose 源码的静态扫描：

  A11Y-001 图标按钮缺 contentDescription
            Material3 `IconButton { Icon(...) }` 的图标必须带非空 contentDescription
            （`null` / `""` 都不算，禁止用空标签糊弄门禁）。
            自绘图标按钮（`clickable` + 纯字形内容）由 A11Y-002 判定其 semantics 标签。
  A11Y-002 可点击组件无可访问名
            `Modifier.clickable` 的容器必须「有可见文本」或「有 contentDescription」，
            否则 TalkBack 聚焦时只念「按钮」。只含图标字形（"×" / glyph 参数）不算可见文本。
  A11Y-003 文本容器固定高度
            文本容器使用 `.height(N.dp)` 会在系统字体放大到 200% 时裁掉文字；
            应用 `.heightIn(min = N.dp)`（最小高度保留视觉，内容可撑开）。
            同一语句里已有 `heightIn(min = ...)` 的视为已修，不报。

退出码：0 = 无命中；1 = 有命中（文件:行 + 原值），可直接被 CI/pre-push 消费。

已知盲区（规则边界，宁可少报也不制造噪音）：
  - 只扫 `app/src/main/kotlin` 的 main 源集；测试与 androidTest 不在范围。
  - 高度只认字面量 `N.dp`：`val itemHeight = 44.dp` 这类变量不进检测
    （滚轮选择器等几何自洽组件需人工复核，见 QkWheelPicker 的 fontScale 处理）。
  - 高度 < 16dp 视为分隔线/进度条/色块，不属文本容器。
  - 静态文本判定靠 `Text(` 的实参形态，无法解析跨文件自定义文本组件。
  - 对比度不在本脚本：色彩断言由 `ContrastTest.kt` 在 JVM 侧做（Color 类可离线求值）。

用法：
  python3 scripts/governance/gov-a11y-check.py            # 全库检查
  python3 scripts/governance/gov-a11y-check.py --verbose  # 同时打印通过的规则
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent.parent
SRC_DIR = ROOT / "app" / "src" / "main" / "kotlin"

# 视为「装饰性高度」的下限：低于此值的 height 是分隔线/进度条/色块，不承载文字
MIN_TEXT_BOX_HEIGHT = 16.0
# 文本字形实参名：这些参数装的是图标字符（"×"、"⌄"），不是可朗读的文本
GLYPH_ARG = re.compile(r"^(glyph|symbol|iconGlyph|iconChar|char|mark)$")
DECL_START = re.compile(r"^\s*(@|(private|internal|public|override|suspend)\s+)*fun\s+")

# 跨代理写域豁免：(相对仓库路径, 规则号) -> 理由。
# 豁免只用于「确实违规但本分支无权改」的项：必须打印为 [EXEMPT] 并写明归属，
# 由所属代理修复后删除本条目。豁免不是通过——它是挂账。
EXEMPT: dict[tuple[str, str], str] = {}


def mask_source(src: str) -> str:
    """把注释与字符串字面量的内容替换成空格（保留换行与长度）。

    结构扫描（花括号匹配、函数边界）必须在「无注释、无字符串」的文本上做，
    否则注释里的 `{`、文案里的 `"` 会让块边界错位。
    偏移量与原文一一对应，因此可以用同一区间回原文取字符串字面量。
    """
    out = list(src)
    i, n = 0, len(src)
    while i < n:
        c = src[i]
        if c == "/" and i + 1 < n and src[i + 1] == "/":
            while i < n and src[i] != "\n":
                out[i] = " "
                i += 1
        elif c == "/" and i + 1 < n and src[i + 1] == "*":
            while i < n and not (src[i] == "*" and i + 1 < n and src[i + 1] == "/"):
                if src[i] != "\n":
                    out[i] = " "
                i += 1
            if i < n:
                out[i] = " "
                if i + 1 < n:
                    out[i + 1] = " "
            i += 2
        elif c == '"':
            if src.startswith('"""', i):
                j = i + 3
                while j < n and not src.startswith('"""', j):
                    if src[j] != "\n":
                        out[j] = " "
                    j += 1
                i = j + 3
            else:
                i += 1
                while i < n and src[i] not in '"\n':
                    if src[i] == "\\" and i + 1 < n:
                        out[i] = " "
                        i += 1
                    if src[i] != "\n":
                        out[i] = " "
                    i += 1
                i += 1
        else:
            i += 1
    return "".join(out)


def match_block(masked: str, start: int) -> tuple[int, int] | None:
    """从 start 起找到第一个 `{`，返回与之配对的块内容区间 (begin, end)。"""
    open_at = masked.find("{", start)
    if open_at < 0:
        return None
    depth = 0
    for i in range(open_at, len(masked)):
        ch = masked[i]
        if ch == "{":
            depth += 1
        elif ch == "}":
            depth -= 1
            if depth == 0:
                return open_at + 1, i
    return None


def match_paren(masked: str, start: int) -> int:
    """start 指向 `(` 之后；返回配对的 `)` 下标（找不到返回 -1）。"""
    depth = 1
    for i in range(start, len(masked)):
        ch = masked[i]
        if ch == "(":
            depth += 1
        elif ch == ")":
            depth -= 1
            if depth == 0:
                return i
    return -1


def line_of(masked: str, pos: int) -> int:
    return masked.count("\n", 0, pos) + 1


def enclosing_fun(masked: str, pos: int) -> str:
    """向上找最近的函数声明，取函数名用于定位报告。"""
    head = masked[:pos]
    matches = list(re.finditer(r"\bfun\s+(\w+)\s*\(", head))
    return matches[-1].group(1) if matches else "<top-level>"


def string_args(original: str, masked: str, begin: int, end: int) -> list[str]:
    """取区间内的字符串字面量内容（原文取、掩码定位）。"""
    return [m.group(1) for m in re.finditer(r'"((?:[^"\\\n]|\\.)*)"', original[begin:end])]


def has_nonempty_content_description(original: str, begin: int, end: int) -> bool:
    """区间内是否给了非空 contentDescription。

    接受字面量（`"关闭"`）与变量（`contentDescription` 这类 String 参数）：
    静态扫描无法证明变量非空，但把 null/空串显式排除，禁止用空标签糊弄门禁。
    """
    chunk = original[begin:end]
    for m in re.finditer(r"contentDescription\s*=\s*([^,\n)]+)", chunk):
        value = m.group(1).strip()
        if value in ("null", '""', '"" +'):
            continue
        return True
    return False


def statement_start(masked: str, pos: int) -> int | None:
    """向上回溯到承载该 modifier 链的调用语句起始行首。

    形如：
        Box(
            modifier = modifier
                .height(58.dp)      <- pos
    回溯到 `Box(` 所在行，才能拿到该调用的尾随 lambda。回溯不到调用
    （例如 `val body = if (...) base.clickable(...) else base`）时返回 None：
    这种语句没有属于自己的内容 lambda，无法判定可见文本，按盲区跳过。
    """
    line_start = masked.rfind("\n", 0, pos) + 1
    cur = line_start
    for _ in range(60):  # 防御：最多回看 60 行
        prev_start = masked.rfind("\n", 0, cur - 1) + 1
        prev = masked[prev_start:cur].strip()
        if not prev:
            break
        if re.match(r"^[A-Z]\w*\s*\(|^Modifier\b", prev):
            return prev_start
        # 赋值 / 分支 / 声明行：modifier 链的宿主不是一次调用，放弃判定
        if DECL_START.match(prev) or re.match(r"^(val|var|if|else|when|return)\b", prev) or prev.endswith("="):
            return None
        if cur == 0:
            break
        cur = prev_start
    return None


def owned_block(masked: str, pos: int) -> tuple[int, int] | None:
    """取 pos 所属调用语句的尾随 lambda 内容区间。

    只认「紧跟调用右括号」的块，跳过同一语句里的内联 lambda
    （例如 `.clickable { ... }` 的点击回调、`if (x) { ... }` 分支），
    否则会把回调体当成容器内容，判定全错。
    """
    start = statement_start(masked, pos)
    if start is None:
        return None
    open_at = masked.find("(", start)
    if open_at < 0 or open_at > pos:
        return None
    close = match_paren(masked, open_at + 1)
    if close < 0:
        return None
    gap = masked[close + 1 : close + 200]
    if not gap.lstrip().startswith("{"):
        return None
    return match_block(masked, close + 1)


def is_glyph_text(original: str, call_args: str) -> bool:
    """判断 Text(...) 的首个实参是不是「图标字形」而非可朗读文本。"""
    first = call_args.split(",", 1)[0].strip()
    if GLYPH_ARG.match(first):
        return True
    # 单字符字面量：'×' / '›' / '＋' 这类图标字符
    if first.startswith('"') and first.endswith('"'):
        body = first[1:-1]
        return len(body) <= 1 or (len(body) <= 2 and not body.isascii())
    return False


class Finding:
    def __init__(self, rule: str, path: Path, line: int, where: str, detail: str):
        self.rule = rule
        self.path = path
        self.line = line
        self.where = where
        self.detail = detail

    def render(self) -> str:
        rel = self.path.relative_to(ROOT)
        return f"{rel}:{self.line}: [{self.rule}] {self.where} — {self.detail}"


def check_icon_buttons(path: Path, original: str, masked: str) -> list[Finding]:
    """A11Y-001：图标按钮必须有 contentDescription。"""
    findings: list[Finding] = []

    # 1) Material3 IconButton { Icon(...) }：图标即按钮语义
    for m in re.finditer(r"(?<![\w.])IconButton\s*\(", masked):
        close = match_paren(masked, m.end())
        if close < 0:
            continue
        block = match_block(masked, close)
        if block is None:
            continue
        begin, end = block
        icons = list(re.finditer(r"(?<![\w.])(?:Qk)?Icon\s*\(", masked[begin:end]))
        if not icons:
            continue  # 内含 Text 的 IconButton 由 A11Y-002 判可见文本
        if not has_nonempty_content_description(original, begin, end):
            findings.append(
                Finding(
                    "A11Y-001",
                    path,
                    line_of(masked, m.start()),
                    f"IconButton（{enclosing_fun(masked, m.start())}）",
                    "图标缺 contentDescription（TalkBack 只念「按钮」）",
                )
            )

    return findings


def check_clickable_names(path: Path, original: str, masked: str) -> list[Finding]:
    """A11Y-002：可点击容器必须有可见文本或 contentDescription。"""
    findings: list[Finding] = []
    for m in re.finditer(r"\.clickable\s*\(", masked):
        block = owned_block(masked, m.start())
        if block is None:
            continue  # 无自有内容 lambda（槽位容器 / 赋值语句），无法判定，按盲区跳过
        begin, end = block
        body = masked[begin:end]
        # contentDescription 通常挂在 modifier 链上（semantics 修饰符），不是内容 lambda 里，
        # 因此从语句起点一直查到内容块末尾。
        start = statement_start(masked, m.start())
        chain_begin = start if start is not None else begin
        has_name = has_nonempty_content_description(original, chain_begin, end) or re.search(
            r"onClickLabel\s*=\s*\"[^\"]+\"", original[chain_begin:end]
        )
        if has_name:
            continue
        visible_text = False
        for t in re.finditer(r"(?<![\w.])(?:Basic)?Text\s*\(", body):
            close = match_paren(masked, begin + t.end())
            if close < 0:
                continue
            args = original[begin + t.end() : close]
            if not is_glyph_text(original, args):
                visible_text = True
                break
        if visible_text:
            continue
        findings.append(
            Finding(
                "A11Y-002",
                path,
                line_of(masked, m.start()),
                f"clickable（{enclosing_fun(masked, m.start())}）",
                "可点击容器既无可见文本也无 contentDescription",
            )
        )
    return findings


def check_fixed_heights(path: Path, original: str, masked: str) -> list[Finding]:
    """A11Y-003：文本容器禁止固定 height（200% 字体缩放会裁剪）。"""
    findings: list[Finding] = []
    for m in re.finditer(r"\.height\(\s*(\d+(?:\.\d+)?)\.dp\s*\)", masked):
        value = float(m.group(1))
        if value < MIN_TEXT_BOX_HEIGHT:
            continue  # 分隔线 / 进度条 / 色块
        # 当前语句的尾随 lambda 才是「容器内容」；内联 lambda 是回调/分支
        block = owned_block(masked, m.start())
        if block is None:
            continue
        begin, end = block
        start = statement_start(masked, m.start())
        if start is not None and re.search(r"heightIn\(\s*min\s*=", masked[start:begin]):
            continue  # 同语句已用最小高度，可随字体撑开
        if not re.search(r"(?<![\w.])(?:Basic)?Text\s*\(", masked[begin:end]):
            continue  # 非文本容器（图表 / 色块 / 骨架条）
        findings.append(
            Finding(
                "A11Y-003",
                path,
                line_of(masked, m.start()),
                f"{enclosing_fun(masked, m.start())}",
                f"文本容器固定 height({m.group(1)}.dp)，200% 字体缩放会裁剪；改 heightIn(min = ...)",
            )
        )
    return findings


def scan() -> list[Finding]:
    findings: list[Finding] = []
    if not SRC_DIR.exists():
        print(f"[gov-a11y-check] 源码目录不存在: {SRC_DIR}", file=sys.stderr)
        return findings
    for path in sorted(SRC_DIR.rglob("*.kt")):
        original = path.read_text(encoding="utf-8")
        masked = mask_source(original)
        findings += check_icon_buttons(path, original, masked)
        findings += check_clickable_names(path, original, masked)
        findings += check_fixed_heights(path, original, masked)
    return findings


def main(argv: list[str]) -> int:
    verbose = "--verbose" in argv or "-v" in argv
    findings = scan()
    print(f"[gov-a11y-check] 扫描 {SRC_DIR.relative_to(ROOT)}/**/*.kt")
    blocking: list[Finding] = []
    exempted: list[Finding] = []
    for f in findings:
        key = (str(f.path.relative_to(ROOT)), f.rule)
        if key in EXEMPT:
            print(f"[EXEMPT] {f.render()} — {EXEMPT[key]}")
            exempted.append(f)
        else:
            blocking.append(f)
            print(f.render())
    if exempted:
        print(f"[gov-a11y-check] 挂账 {len(exempted)} 项（跨代理写域，未计入门禁）")
    findings = blocking
    if not findings:
        print("[gov-a11y-check] 通过：A11Y-001/002/003 全部 0 命中")
        return 0
    by_rule: dict[str, int] = {}
    for f in findings:
        by_rule[f.rule] = by_rule.get(f.rule, 0) + 1
    summary = "，".join(f"{k}={v}" for k, v in sorted(by_rule.items()))
    print(f"[gov-a11y-check] 失败：{len(findings)} 处命中（{summary}）")
    if verbose:
        print("[gov-a11y-check] 修复指引：图标按钮补 contentDescription；文本容器改 heightIn(min = ...)")
    return 1


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
