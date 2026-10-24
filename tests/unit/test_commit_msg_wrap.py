"""commit-msg 钩子：正文超长时折行而不是拒绝提交。

事故背景：钩子对超 72 字符的正文直接 exit 1，于是「提交失败但仍 push」
出现空分支，合并队列把空分支当已合并清理，worktree 里未提交的改动
被静默丢弃。折行不改变语义，因此改为自动处理。
"""

from __future__ import annotations

import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
HOOK = ROOT / "scripts/governance/gov-commit-msg"


def run_hook(path: Path) -> subprocess.CompletedProcess:
    return subprocess.run(
        ["bash", str(HOOK), str(path)],
        cwd=ROOT,
        capture_output=True,
        text=True,
        timeout=60,
    )


def body_lines(text: str) -> list[str]:
    lines = [ln for ln in text.splitlines() if ln.strip() and not ln.startswith("#")]
    return lines[1:]


def test_long_body_is_wrapped_not_rejected(tmp_path: Path):
    msg = tmp_path / "msg.txt"
    msg.write_text(
        "feat(test): a subject\n\n"
        "This body line is deliberately much longer than the seventy two character limit allows.\n",
        encoding="utf-8",
    )
    res = run_hook(msg)
    assert res.returncode == 0, res.stdout + res.stderr
    assert "auto-wrapped" in res.stdout
    for line in body_lines(msg.read_text(encoding="utf-8")):
        assert len(line) <= 72, line


def test_url_lines_are_left_alone(tmp_path: Path):
    url = "https://example.com/" + "a" * 60
    msg = tmp_path / "msg.txt"
    msg.write_text(f"feat(test): a subject\n\nSee {url} for details.\n", encoding="utf-8")
    res = run_hook(msg)
    assert res.returncode == 0, res.stdout + res.stderr
    assert url in msg.read_text(encoding="utf-8")


def test_short_body_untouched(tmp_path: Path):
    msg = tmp_path / "msg.txt"
    original = "feat(test): a subject\n\nShort body.\n"
    msg.write_text(original, encoding="utf-8")
    res = run_hook(msg)
    assert res.returncode == 0, res.stdout + res.stderr
    assert msg.read_text(encoding="utf-8") == original
