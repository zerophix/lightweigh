#!/usr/bin/env python3
"""gov-preview-check.py — 交付屏 Compose 预览覆盖检查。

用法：
    python3 scripts/governance/gov-preview-check.py [--root <仓库根>]

输出：
    每个缺预览的屏一行 `文件:屏名 — 缺预览`，最后一行汇总。
    全部覆盖 exit 0，否则 exit 1。

判定口径与排除项见 `scripts/governance/modules/gov_validate_previews.py` 的模块文档。
"""
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent.parent
sys.path.insert(0, str(ROOT))

from scripts.governance.modules.gov_validate_previews import collect_missing, scan


def main(argv) -> int:
    root = ROOT
    if "--root" in argv:
        i = argv.index("--root")
        if i + 1 < len(argv):
            root = Path(argv[i + 1]).resolve()

    screens, _ = scan(root)
    missing = collect_missing(root)

    for rel, name in missing:
        print(f"{rel}:{name} — 缺预览")

    total = len(screens)
    if missing:
        print(f"\n预览覆盖：{total - len(missing)}/{total} 屏，缺 {len(missing)} 个")
        return 1

    print(f"预览覆盖：{total}/{total} 屏全部有 @Preview")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
