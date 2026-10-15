#!/usr/bin/env python3
"""共享基础设施"""
import json
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


def fail(msg: str) -> None:
    errors.append(msg)
    print(f"  [FAIL] {msg}")
