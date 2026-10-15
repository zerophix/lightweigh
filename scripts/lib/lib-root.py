"""scripts/lib-root.py — 项目根目录定位（Python 版）

用法:
    from scripts.lib.root import get_project_root
    PROJECT_ROOT = get_project_root()

策略: 从本文件所在目录开始，向上遍历目录树查找 marker 文件（setup.py / pyproject.toml）,
最多 10 层。找不到则 fallback 到 scripts/ 的父目录（即项目根）。
"""

from pathlib import Path


def get_project_root(marker: str = "setup.py") -> str:
    """返回项目根目录绝对路径（str）。策略：从本文件所在目录出发，向上查找 marker 文件，找不到则 fallback 到 scripts/ 的父目录（即 3 层上）。"""
    current = Path(__file__).resolve().parent
    for _ in range(10):
        if (current / marker).is_file() or (current / "pyproject.toml").is_file():
            return str(current)
        parent = current.parent
        if parent == current:
            break
        current = parent
    return str(Path(__file__).resolve().parent.parent.parent)
