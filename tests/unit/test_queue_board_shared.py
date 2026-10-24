"""合并队列的共享状态归属契约。

事故背景：queue 一律对共享文件取候选侧（ours）。board 分支是共享状态的
唯一授权写者，它带来的 tasks.json 修正因此在冲突时被静默丢弃，任务状态
回到旧值，而所有人以为已经回填。

这里用静态契约钉住语义（队列本身是 bash，函数式集成测试成本过高，先用
最便宜的方式防止回退）：
  - 默认取 ours（普通特性分支不得改写共享状态）；
  - chore/board-* 取 theirs（授权写者获胜）；
  - 共享路径包含 .ai/tasks.json。
"""

from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
QUEUE = ROOT / "scripts/workflow/wf-queue.sh"


def text() -> str:
    return QUEUE.read_text(encoding="utf-8")


def test_board_branches_take_theirs():
    src = text()
    assert 'local side="--ours"' in src
    assert 'case "$branch" in chore/board-*) side="--theirs" ;; esac' in src


def test_shared_paths_cover_board_state():
    src = text()
    assert ".ai/tasks.json" in src
    assert "../" not in src.split("SHARED_PATHS=")[1].split("\n")[0] if "SHARED_PATHS=" in src else True


def test_queue_skips_zero_commit_branches():
    """提交被钩子拒绝后仍 push 的空分支，曾导致合并空转并删掉 worktree。"""
    assert "rev-list --count" in text()
    assert "零提交" in text()
