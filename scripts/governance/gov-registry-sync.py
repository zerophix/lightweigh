#!/usr/bin/env python3
"""Android 测试注册表自动同步（DEC-065）。

注册表 `tests/android-test-registry.yaml` 是**派生数据**：磁盘上的 `*Test.kt` /
`*Test.java`（src/test 与 src/androidTest）才是真源。功能分支不得手写它
（单一写者，DEC-064），由合并队列/收尾用本脚本自动补齐 / 清理僵尸条目。

设计取舍：**块级保留**——已注册条目原样保留（不改写描述/格式），只做两类最小编辑：
  - 删除僵尸条目（注册了但磁盘不存在）
  - 追加缺失条目（磁盘存在但未注册）

用法：
    python3 scripts/governance/gov-registry-sync.py            # 就地同步
    python3 scripts/governance/gov-registry-sync.py --dry      # 只报告
    python3 scripts/governance/gov-registry-sync.py --root DIR # 指定仓库根（测试用）

退出码：0 = 无需变更或已同步。
"""
from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

MODULES = ("domain", "application", "infrastructure", "app")
REGISTRY_REL = "tests/android-test-registry.yaml"
AUTO_DESC = "自动登记（gov-registry-sync）"


def _module_tests(root: Path) -> set[str]:
    """模块 src/test 与 src/androidTest 下的 *Test.kt/*Test.java（相对 posix 路径）。"""
    found: set[str] = set()
    for module in MODULES:
        for kind in ("test", "androidTest"):
            base = root / module / "src" / kind
            if not base.is_dir():
                continue
            for path in base.rglob("*"):
                if not path.is_file():
                    continue
                if "build" in path.parts:
                    continue
                if path.suffix not in (".kt", ".java"):
                    continue
                if not path.stem.endswith("Test"):
                    continue
                found.add(path.relative_to(root).as_posix())
    return found


def _split_sections(text: str):
    """按顶层键切段；返回 (前导行, {段名: 行列表}, 段顺序)。保留原始行文本。"""
    lines = text.splitlines()
    preamble: list[str] = []
    sections: dict[str, list[str]] = {}
    order: list[str] = []
    current: str | None = None
    for line in lines:
        m = re.match(r"^([A-Za-z_][\w-]*):", line)
        if m:
            current = m.group(1)
            if current not in sections:
                order.append(current)
            sections.setdefault(current, [])
            if current != "commands":
                sections[current].append(line)
            continue
        if current is None:
            preamble.append(line)
        else:
            sections[current].append(line)
    return preamble, sections, order


def _entry_blocks(lines: list[str]) -> list[list[str]]:
    """把 commands 段切成条目块（以 '- ' 开头），保留块内原始行。"""
    blocks: list[list[str]] = []
    cur: list[str] | None = None
    for line in lines:
        if line.startswith("- "):
            if cur is not None:
                blocks.append(cur)
            cur = [line]
        elif cur is not None:
            cur.append(line)
    if cur is not None:
        blocks.append(cur)
    return blocks


def _block_path(block: list[str]) -> str:
    for line in block:
        m = re.match(r"^\s+path:\s*(\S+)\s*$", line)
        if m:
            return m.group(1)
    return ""


def _entry_block(path: str) -> list[str]:
    return [
        f"- description: {AUTO_DESC}",
        f"  path: {path}",
        "  status: stable",
        "  type: gradle-test",
    ]


def sync(root: Path, dry: bool) -> int:
    registry = root / REGISTRY_REL
    if not registry.is_file():
        print(f"[registry-sync] 跳过：{REGISTRY_REL} 不存在")
        return 0

    text = registry.read_text(encoding="utf-8")
    preamble, sections, order = _split_sections(text)
    blocks = _entry_blocks(sections.get("commands", []))
    registered = {p for p in (_block_path(b) for b in blocks) if p}
    on_disk = _module_tests(root)

    missing = sorted(on_disk - registered)
    zombies = sorted(registered - on_disk)

    if not missing and not zombies:
        print("[registry-sync] 注册表与磁盘一致，无需变更")
        return 0

    for p in missing:
        print(f"[registry-sync] 补登记: {p}")
    for p in zombies:
        print(f"[registry-sync] 移除僵尸: {p}")
    if dry:
        print("[registry-sync] --dry：未写文件")
        return 0

    kept = [b for b in blocks if _block_path(b) not in zombies]
    out = list(preamble)
    out.append("commands:")
    for b in kept:
        out.extend(b)
    for p in missing:
        out.extend(_entry_block(p))
    for name in order:
        if name == "commands":
            continue
        out.extend(sections.get(name, []))

    registry.write_text("\n".join(out).rstrip("\n") + "\n", encoding="utf-8")
    print(
        f"[registry-sync] 已写入 {REGISTRY_REL}"
        f"（新增 {len(missing)} 条，移除 {len(zombies)} 条）"
    )
    return 0


def main() -> int:
    ap = argparse.ArgumentParser(description="同步 Android 测试注册表（派生数据）")
    ap.add_argument("--root", default=None, help="仓库根（默认按脚本位置推断）")
    ap.add_argument("--dry", action="store_true", help="只报告，不写文件")
    args = ap.parse_args()

    root = Path(args.root).resolve() if args.root else Path(__file__).resolve().parents[2]
    return sync(root, args.dry)


if __name__ == "__main__":
    sys.exit(main())
