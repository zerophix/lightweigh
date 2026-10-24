#!/usr/bin/env python3
"""共享基础设施"""
import json, re, subprocess
from pathlib import Path


def _find_project_root():
    p = Path(__file__).resolve()
    for _ in range(10):
        if any((p / m).is_file() for m in [
            "setup.py", "pyproject.toml", "setup.sh", "package.json", "AGENTS.md",
        ]):
            return p
        p = p.parent
    return Path(__file__).resolve().parent.parent.parent.parent.parent


ROOT = _find_project_root()
DOCS = ROOT / "docs"
MEMORY_FILE = ROOT / ".cache" / "context.jsonl"
errors: list[str] = []


def on_feature_branch(root: Path | None = None) -> bool:
    """当前工作树是否为特性分支（DEC-064/065 派生数据降级判据）。

    `feat|fix|docs|refactor|chore|test|ci/*` → True；trunk/CI detached/非 git/异常 → False（保守）。
    """
    target = root or ROOT
    try:
        r = subprocess.run(
            ["git", "-C", str(target), "rev-parse", "--abbrev-ref", "HEAD"],
            capture_output=True, text=True, timeout=10,
        )
    except (OSError, subprocess.SubprocessError):
        return False
    return bool(re.match(r"^(feat|fix|docs|refactor|chore|test|ci)/", (r.stdout or "").strip()))


def fail(msg: str) -> None:
    errors.append(msg)
    print(f"  [FAIL] {msg}")
