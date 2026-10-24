"""任务认领原子锁（DEC-064）的端到端测试。

锁语义: mkdir .ai/locks/<TASK-ID> 成功即持有（POSIX 原子），失败即被他人持有。
本文件用 subprocess 调用真实脚本 + 临时目录做仓库根，验证:
  1. 抢锁成功 / 重复抢锁失败
  2. 并发抢锁恰好一个成功
  3. 非持有者 release 被拒、持有者 release 成功
  4. TTL 过期判定与 gc 清理
  5. 未知任务 ID 被拒
  6. pre-commit 接口 --check-staged 的拦截逻辑
"""
from __future__ import annotations

import datetime
import json
import os
import subprocess
from pathlib import Path

CLAIM = Path(__file__).resolve().parents[2] / "scripts" / "workflow" / "wf-claim.sh"

TASKS = {
    "tasks": [
        {"id": "T-001", "target_files": ["app/src/a.kt", "app/src/b.kt"]},
        {"id": "T-002", "target_files": ["app/src/c.kt"]},
    ]
}


def _make_root(tmp_path: Path, session: str = "ses-A") -> Path:
    """构造最小仓库根: .ai/tasks.json + .ai/current-session.json。"""
    root = tmp_path / "repo"
    (root / ".ai").mkdir(parents=True, exist_ok=True)
    (root / ".ai" / "tasks.json").write_text(
        json.dumps(TASKS, ensure_ascii=False), encoding="utf-8")
    (root / ".ai" / "current-session.json").write_text(
        json.dumps({"session_id": session}), encoding="utf-8")
    return root


def _run(root: Path, *args: str, session: str | None = None) -> subprocess.CompletedProcess:
    env = {k: v for k, v in os.environ.items() if k != "WF_CLAIM_SESSION"}
    if session is not None:
        env["WF_CLAIM_SESSION"] = session
    return subprocess.run(
        ["bash", str(CLAIM), "--root", str(root), *args],
        capture_output=True, text=True, env=env,
    )


def _lock_dir(root: Path, tid: str) -> Path:
    return root / ".ai" / "locks" / tid


# ---------------------------------------------------------------- 抢锁


def test_claim_success_writes_lock_metadata(tmp_path):
    root = _make_root(tmp_path)
    res = _run(root, "claim", "T-001", session="ses-A")
    assert res.returncode == 0, res.stderr
    assert "认领成功" in res.stdout

    lock = _lock_dir(root, "T-001")
    assert lock.is_dir()
    assert (lock / "owner").read_text(encoding="utf-8").strip() == "ses-A"
    assert (lock / "created_at").read_text(encoding="utf-8").strip()
    assert (lock / "branch").is_file()


def test_claim_twice_by_other_session_fails(tmp_path):
    root = _make_root(tmp_path)
    assert _run(root, "claim", "T-001", session="ses-A").returncode == 0

    res = _run(root, "claim", "T-001", session="ses-B")
    assert res.returncode != 0
    # 必须告诉调用方锁在谁手里，调用方据此放弃
    assert "ses-A" in res.stderr
    assert (root / ".ai" / "locks" / "T-001" / "owner").read_text(
        encoding="utf-8").strip() == "ses-A"


def test_concurrent_claim_exactly_one_winner(tmp_path):
    root = _make_root(tmp_path)
    procs = []
    for i in range(8):
        env = {k: v for k, v in os.environ.items() if k != "WF_CLAIM_SESSION"}
        env["WF_CLAIM_SESSION"] = "ses-{}".format(i)
        procs.append(subprocess.Popen(
            ["bash", str(CLAIM), "--root", str(root), "claim", "T-001"],
            stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, env=env,
        ))
    results = [(p.wait(), p.communicate()) for p in procs]

    winners = [i for i, (rc, _) in enumerate(results) if rc == 0]
    assert len(winners) == 1, "并发抢锁必须恰好一个成功: {}".format(results)
    winner = "ses-{}".format(winners[0])
    assert (root / ".ai" / "locks" / "T-001" / "owner").read_text(
        encoding="utf-8").strip() == winner


def test_claim_rejects_unknown_task_id(tmp_path):
    root = _make_root(tmp_path)
    res = _run(root, "claim", "T-999", session="ses-A")
    assert res.returncode != 0
    assert "T-999" in res.stderr
    assert not _lock_dir(root, "T-999").exists()


def test_claim_uses_session_file_when_env_absent(tmp_path):
    root = _make_root(tmp_path, session="ses-from-file")
    res = _run(root, "claim", "T-002")
    assert res.returncode == 0, res.stderr
    assert (root / ".ai" / "locks" / "T-002" / "owner").read_text(
        encoding="utf-8").strip() == "ses-from-file"


# ---------------------------------------------------------------- 释放


def test_release_by_non_owner_refused(tmp_path):
    root = _make_root(tmp_path)
    assert _run(root, "claim", "T-001", session="ses-A").returncode == 0

    res = _run(root, "release", "T-001", session="ses-B")
    assert res.returncode != 0
    assert _lock_dir(root, "T-001").is_dir()


def test_release_by_owner_succeeds(tmp_path):
    root = _make_root(tmp_path)
    assert _run(root, "claim", "T-001", session="ses-A").returncode == 0

    res = _run(root, "release", "T-001", session="ses-A")
    assert res.returncode == 0, res.stderr
    assert not _lock_dir(root, "T-001").exists()

    # 释放后可被其他会话抢到
    assert _run(root, "claim", "T-001", session="ses-B").returncode == 0


def test_release_without_lock_fails(tmp_path):
    root = _make_root(tmp_path)
    res = _run(root, "release", "T-001", session="ses-A")
    assert res.returncode != 0


# ---------------------------------------------------------------- TTL / gc


def _age_lock(root: Path, tid: str, hours: float) -> None:
    """把锁的 created_at 改早，模拟超时。"""
    old = (datetime.datetime.now(datetime.timezone.utc)
           - datetime.timedelta(hours=hours)).strftime("%Y-%m-%dT%H:%M:%SZ")
    (root / ".ai" / "locks" / tid / "created_at").write_text(old + "\n", encoding="utf-8")


def test_status_marks_expired_and_gc_removes_only_expired(tmp_path):
    root = _make_root(tmp_path)
    assert _run(root, "claim", "T-001", session="ses-A").returncode == 0
    assert _run(root, "claim", "T-002", session="ses-A").returncode == 0
    _age_lock(root, "T-002", hours=30)

    status = _run(root, "status")
    assert status.returncode == 0
    assert "EXPIRED" in status.stdout
    # T-001 新鲜，不应被标过期
    fresh_line = [ln for ln in status.stdout.splitlines() if "T-001" in ln][0]
    assert "EXPIRED" not in fresh_line

    single = _run(root, "status", "T-002")
    assert "EXPIRED" in single.stdout

    gc = _run(root, "gc")
    assert gc.returncode == 0
    assert "T-002" in gc.stdout
    assert not _lock_dir(root, "T-002").exists()
    assert _lock_dir(root, "T-001").is_dir()


def test_status_without_lock_reports_free(tmp_path):
    root = _make_root(tmp_path)
    res = _run(root, "status", "T-001")
    assert res.returncode == 0
    assert "无锁" in res.stdout


def test_list_shows_locks(tmp_path):
    root = _make_root(tmp_path)
    assert _run(root, "claim", "T-001", session="ses-A").returncode == 0
    res = _run(root, "list")
    assert res.returncode == 0
    assert "T-001" in res.stdout
    assert "ses-A" in res.stdout


# ---------------------------------------------------------------- pre-commit 接口


def _git(repo: Path, *args: str) -> str:
    return subprocess.run(
        ["git", "-C", str(repo), *args],
        capture_output=True, text=True, check=True,
    ).stdout.strip()


def _make_git_root(tmp_path: Path, session: str = "ses-A") -> Path:
    root = _make_root(tmp_path, session=session)
    _git(root, "init", "-q")
    _git(root, "config", "user.name", "t")
    _git(root, "config", "user.email", "t@t")
    (root / "app" / "src").mkdir(parents=True)
    for name in ("a.kt", "b.kt", "c.kt"):
        (root / "app" / "src" / name).write_text("// x\n", encoding="utf-8")
    _git(root, "add", "-A")
    return root


def test_check_staged_blocks_when_lock_held_by_other_session(tmp_path):
    root = _make_git_root(tmp_path)
    assert _run(root, "claim", "T-001", session="ses-A").returncode == 0

    res = _run(root, "--check-staged", session="ses-B")
    assert res.returncode != 0
    assert "T-001" in res.stdout
    assert "ses-A" in res.stdout
    assert "app/src/a.kt" in res.stdout
    assert "wf-claim.sh claim" in res.stdout


def test_check_staged_passes_for_lock_owner(tmp_path):
    root = _make_git_root(tmp_path)
    assert _run(root, "claim", "T-001", session="ses-A").returncode == 0
    res = _run(root, "--check-staged", session="ses-A")
    assert res.returncode == 0, res.stdout + res.stderr


def test_check_staged_passes_when_no_lock(tmp_path):
    # 锁不存在不拦（渐进式：认领是推荐而非强制）
    root = _make_git_root(tmp_path)
    res = _run(root, "--check-staged", session="ses-B")
    assert res.returncode == 0, res.stdout + res.stderr


def test_check_staged_ignores_files_outside_target_files(tmp_path):
    root = _make_git_root(tmp_path)
    assert _run(root, "claim", "T-002", session="ses-A").returncode == 0
    _git(root, "reset", "-q")
    _git(root, "add", "app/src/a.kt")  # 属于 T-001，不属于 T-002

    res = _run(root, "--check-staged", session="ses-B")
    assert res.returncode == 0, res.stdout + res.stderr


def test_check_staged_no_staged_files(tmp_path):
    root = _make_git_root(tmp_path)
    assert _run(root, "claim", "T-001", session="ses-A").returncode == 0
    _git(root, "reset", "-q")
    res = _run(root, "--check-staged", session="ses-B")
    assert res.returncode == 0
