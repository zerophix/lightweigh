"""多会话 worktree 守卫（DEC-054）的检测原语测试。

守卫根因: 多 AI 会话共享一个 .git，HEAD/配置全局，互相覆盖（抢分支、
提交落错分支、core.bare 误设致 git 不可用）。worktree 各有独立 HEAD，
从结构上免疫。守卫在 pre-commit 拦「主共享树 + 并发会话」的提交。

本文件测两个可独立验证的检测原语（不跑完整 bash 钩子，避免耦合
gradlew/gov-custom-checks）:
  1. 主树判定: 主树 git-dir == git-common-dir；worktree 两者不同。
  2. 并发计数: context.jsonl 近 2h 的不同 session-start 数。
"""
from __future__ import annotations

import datetime
import json
import subprocess
import time
from pathlib import Path


def _git(repo: Path, *args: str) -> str:
    return subprocess.run(
        ["git", "-C", str(repo), *args],
        capture_output=True, text=True, check=True,
    ).stdout.strip()


def _init_repo(tmp_path: Path) -> Path:
    repo = tmp_path / "r"
    repo.mkdir()
    _git(repo, "init", "-q")
    _git(repo, "config", "user.name", "t")
    _git(repo, "config", "user.email", "t@t")
    (repo / "f.txt").write_text("x", encoding="utf-8")
    _git(repo, "add", "-A")
    _git(repo, "commit", "-qm", "init")
    return repo


def _is_main_tree(repo: Path) -> bool:
    """复刻守卫的主树判定: git-dir == git-common-dir。"""
    gd = _git(repo, "rev-parse", "--git-dir")
    gcd = _git(repo, "rev-parse", "--git-common-dir")
    return gd == gcd


def _recent_session_count(ctx: Path, window_s: int = 7200) -> int:
    """复刻守卫的并发计数: 近 window_s 秒内不同 session-start 数。"""
    ids: set[str] = set()
    now = time.time()
    if not ctx.is_file():
        return 0
    for line in ctx.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line:
            continue
        try:
            e = json.loads(line)
        except json.JSONDecodeError:
            continue
        if e.get("type") != "session-start":
            continue
        try:
            t = datetime.datetime.fromisoformat(
                e.get("ts", "").replace("Z", "+00:00")).timestamp()
        except ValueError:
            continue
        if now - t <= window_s:
            ids.add(e.get("id") or e.get("ts"))
    return len(ids)


def test_main_tree_detected(tmp_path):
    """主工作树: git-dir == git-common-dir → 判定为主树。"""
    repo = _init_repo(tmp_path)
    assert _is_main_tree(repo), "主树应被判定为 IS_MAIN"


def test_worktree_not_main(tmp_path):
    """worktree: git-dir 指向 .git/worktrees/<n>，与 git-common-dir 不同 → 非主树。"""
    repo = _init_repo(tmp_path)
    wt = tmp_path / "wt"
    _git(repo, "worktree", "add", "-q", "-b", "feat/x", str(wt))
    assert not _is_main_tree(wt), "worktree 不应被判定为 IS_MAIN（守卫应放行）"


def _write_ctx(ctx: Path, entries):
    ctx.parent.mkdir(parents=True, exist_ok=True)
    ctx.write_text("\n".join(json.dumps(e) for e in entries), encoding="utf-8")


def _iso(minutes_ago: float) -> str:
    t = datetime.datetime.now(datetime.timezone.utc) - datetime.timedelta(
        minutes=minutes_ago)
    return t.isoformat().replace("+00:00", "Z")


def test_concurrency_two_recent_sessions_counts_two(tmp_path):
    """近 2h 两个不同 session-start → 计数 2 → 守卫应拦。"""
    ctx = tmp_path / ".cache" / "context.jsonl"
    _write_ctx(ctx, [
        {"type": "session-start", "ts": _iso(5), "id": "s1"},
        {"type": "session-start", "ts": _iso(30), "id": "s2"},
    ])
    assert _recent_session_count(ctx) == 2


def test_old_sessions_not_counted(tmp_path):
    """超 2h 的 session-start 不计入（避免历史噪音误拦）。"""
    ctx = tmp_path / ".cache" / "context.jsonl"
    _write_ctx(ctx, [
        {"type": "session-start", "ts": _iso(5), "id": "s1"},
        {"type": "session-start", "ts": _iso(180), "id": "old"},  # 3h 前
    ])
    assert _recent_session_count(ctx) == 1, "3h 前的不应计入，应为 1"


def test_handoff_entries_not_counted(tmp_path):
    """只有 session-start 计入；handoff 等其它 type 不算。"""
    ctx = tmp_path / ".cache" / "context.jsonl"
    _write_ctx(ctx, [
        {"type": "session-start", "ts": _iso(5), "id": "s1"},
        {"type": "handoff", "ts": _iso(5), "id": "h1"},
        {"type": "handoff", "ts": _iso(6), "id": "h2"},
    ])
    assert _recent_session_count(ctx) == 1


def test_missing_ctx_returns_zero(tmp_path):
    """context.jsonl 缺失（干净 checkout）→ 0，不误拦。"""
    assert _recent_session_count(tmp_path / "nope.jsonl") == 0
