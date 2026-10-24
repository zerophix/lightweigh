#!/usr/bin/env python3
"""工作区医生（DEC-065）的行为测试。

`scripts/governance/gov-workspace-doctor.sh` 负责审计 worktree / 分支 / 认领锁 /
后台日志 / local-ci 证据，并在 `--fix` 时**保守地**清理安全项。本文件用临时
git 仓库复现真实脏场景，钉住三条底线：

  1. 已合入的 worktree / 分支可被清理，未合入的必须保留；
  2. 孤儿残留目录只有确认归属时才删（独立仓库、归属不明一律保留）；
  3. `--strict` 下 WARN 影响退出码，`--json` 可被机器解析。

运行: pytest tests/unit/test_workspace_doctor.py -q
"""

import json
import os
import shutil
import subprocess
import time
from datetime import datetime, timedelta, timezone
from pathlib import Path

import pytest

ROOT_P = Path(__file__).resolve().parents[2]
DOCTOR_SRC = ROOT_P / "scripts" / "governance" / "gov-workspace-doctor.sh"

DOCTOR_REL = Path("scripts") / "governance" / "gov-workspace-doctor.sh"


def _clean_git_env() -> dict:
    """剥离宿主仓库/钩子注入的 GIT_* 变量，避免临时仓库操作打到真实仓库。"""
    return {k: v for k, v in os.environ.items() if not k.startswith("GIT_")}


def _git(cwd: Path, *args: str) -> str:
    r = subprocess.run(
        ["git", *args],
        cwd=str(cwd),
        capture_output=True,
        text=True,
        timeout=60,
        env=_clean_git_env(),
    )
    assert r.returncode == 0, "git %s 失败: %s" % (" ".join(args), r.stderr)
    return r.stdout


def _commit(cwd: Path, filename: str, content: str, message: str) -> None:
    (cwd / filename).write_text(content, encoding="utf-8")
    _git(cwd, "add", filename)
    _git(cwd, "commit", "-m", message)


@pytest.fixture()
def repo(tmp_path: Path) -> Path:
    """临时主仓：master + 一次提交 + 内嵌 doctor 脚本（脚本按自身位置回推仓库根）。"""
    root = tmp_path / "repo"
    root.mkdir()
    init = subprocess.run(
        ["git", "init", "-b", "master"],
        cwd=str(root),
        capture_output=True,
        text=True,
        env=_clean_git_env(),
    )
    if init.returncode != 0:
        _git(root, "init")
        _git(root, "checkout", "-b", "master")
    _git(root, "config", "user.email", "doctor@example.com")
    _git(root, "config", "user.name", "doctor-test")
    _git(root, "config", "commit.gpgsign", "false")

    dest = root / DOCTOR_REL
    dest.parent.mkdir(parents=True)
    shutil.copy2(DOCTOR_SRC, dest)

    _commit(root, "README.md", "init\n", "chore: init")
    return root


def _doctor(repo: Path, *args: str) -> subprocess.CompletedProcess:
    return subprocess.run(
        ["bash", str(repo / DOCTOR_REL), *args],
        cwd=str(repo),
        capture_output=True,
        text=True,
        timeout=180,
        env=_clean_git_env(),
    )


def _payload(repo: Path, *args: str) -> dict:
    r = _doctor(repo, "--json", *args)
    assert r.stdout.strip(), "doctor 无输出，stderr=%s" % r.stderr
    return json.loads(r.stdout)


def _warns(payload: dict) -> list:
    out = []
    for check in payload["checks"]:
        for item in check["items"]:
            if item["level"] == "warn":
                out.append(item["text"])
    return out


def _check(payload: dict, cid: str) -> dict:
    for check in payload["checks"]:
        if check["id"] == cid:
            return check
    raise AssertionError("缺少检查项: %s" % cid)


# ---------------------------------------------------------------- worktree / 分支


def test_merged_worktree_and_branch_removed_unmerged_kept(repo: Path, tmp_path: Path):
    merged_wt = tmp_path / "repo-merged"
    unmerged_wt = tmp_path / "repo-unmerged"
    _git(repo, "worktree", "add", "-b", "feat/merged", str(merged_wt), "master")
    _git(repo, "worktree", "add", "-b", "feat/unmerged", str(unmerged_wt), "master")
    _commit(unmerged_wt, "work.txt", "wip\n", "feat: unmerged work")

    data = _payload(repo)
    warns = _warns(data)
    assert any("feat/merged" in w for w in warns), warns
    assert not any("feat/unmerged" in w for w in warns), warns
    assert _check(data, "worktree")["status"] == "warn"
    assert unmerged_wt.exists()

    fixed = _payload(repo, "--fix")
    assert fixed["fixes"], fixed
    assert not merged_wt.exists(), "已合入 worktree 应被删除"
    assert unmerged_wt.exists(), "未合入 worktree 必须保留"

    branches = _git(repo, "branch", "--format=%(refname:short)").split()
    assert "feat/merged" not in branches
    assert "feat/unmerged" in branches

    listing = _git(repo, "worktree", "list", "--porcelain")
    assert str(merged_wt) not in listing
    assert str(unmerged_wt) in listing


def test_dirty_merged_worktree_is_preserved(repo: Path, tmp_path: Path):
    wt = tmp_path / "repo-dirty"
    _git(repo, "worktree", "add", "-b", "feat/dirty", str(wt), "master")
    (wt / "uncommitted.txt").write_text("dirty\n", encoding="utf-8")

    data = _payload(repo, "--fix")
    assert wt.exists(), "脏工作树不得自动删除"
    assert not any("删除已合入 worktree" in f and "repo-dirty" in f for f in data["fixes"])
    assert any("feat/dirty" in w for w in _warns(data)) or any(
        "保留" in i["text"] for i in _check(data, "worktree")["items"]
    )


# ---------------------------------------------------------------- 孤儿残留


def test_orphan_dirs_cleaned_but_foreign_repo_preserved(repo: Path, tmp_path: Path):
    orphan = tmp_path / "repo-orphan"
    orphan.mkdir()
    (orphan / ".git").write_text(
        "gitdir: %s\n" % (repo / ".git" / "worktrees" / "gone"), encoding="utf-8"
    )
    empty = tmp_path / "repo-empty"
    empty.mkdir()
    foreign = tmp_path / "repo-foreign"
    foreign.mkdir()
    _git(foreign, "init")
    _commit(foreign, "a.txt", "a\n", "chore: foreign")

    data = _payload(repo)
    warns = "\n".join(_warns(data))
    assert "repo-orphan" in warns
    assert "repo-empty" in warns
    assert "repo-foreign" in warns

    _payload(repo, "--fix")
    assert not orphan.exists(), "指向本仓库失效 worktree 的孤儿目录应被清理"
    assert not empty.exists(), "空目录应被清理"
    assert foreign.exists(), "独立 git 仓库不得自动删除"
    assert (foreign / ".git").exists()


# ---------------------------------------------------------------- 认领锁


def test_expired_and_orphan_locks_cleaned_fresh_kept(repo: Path):
    now = time.time()
    locks = repo / ".ai" / "locks"
    locks.mkdir(parents=True)
    sessions = repo / ".ai" / "sessions"
    sessions.mkdir(parents=True)
    (sessions / "ses-live.json").write_text(
        json.dumps({"session_id": "ses-live"}), encoding="utf-8"
    )

    expired = locks / "task-expired.json"
    expired.write_text(
        json.dumps({"owner": "ses-old", "acquired": now - 3 * 86400, "expires": now - 86400}),
        encoding="utf-8",
    )
    old = now - 3 * 86400
    os.utime(expired, (old, old))

    # 未写 expires，但 owner 没有对应 session（2h 前，未超 24h TTL）→ 孤儿锁
    ghost = locks / "task-ghost.json"
    ghost.write_text(
        json.dumps({"owner": "ses-ghost", "acquired": now - 7200}), encoding="utf-8"
    )

    fresh = locks / "task-fresh.json"
    fresh.write_text(
        json.dumps({"owner": "ses-live", "acquired": now, "expires": now + 3600}),
        encoding="utf-8",
    )

    legacy = repo / ".ai" / "task-lock.json"
    legacy.write_text(
        json.dumps(
            {
                "T-1": {"session": "ses-old", "acquired": now - 2 * 86400, "expires": now - 3600},
                "T-2": {"session": "ses-live", "acquired": now, "expires": now + 1800},
            }
        ),
        encoding="utf-8",
    )

    data = _payload(repo)
    warns = "\n".join(_warns(data))
    assert "task-expired.json" in warns
    assert "task-ghost.json" in warns
    assert "task-fresh.json" not in warns
    assert "T-1" in warns
    assert "T-2" not in warns

    _payload(repo, "--fix")
    assert not expired.exists()
    assert not ghost.exists()
    assert fresh.exists(), "未过期且 owner 有效的锁必须保留"
    remaining = json.loads(legacy.read_text(encoding="utf-8"))
    assert "T-1" not in remaining
    assert "T-2" in remaining


# ---------------------------------------------------------------- 后台日志


def test_stale_finished_job_logs_cleaned(repo: Path):
    now = time.time()
    jobs = repo / ".cache" / "jobs"
    jobs.mkdir(parents=True)

    stale_log = jobs / "old.log"
    stale_exit = jobs / "old.exit"
    stale_log.write_text("done\n", encoding="utf-8")
    stale_exit.write_text("0\n", encoding="utf-8")
    old = now - 10 * 86400
    os.utime(stale_log, (old, old))

    fresh_log = jobs / "new.log"
    fresh_exit = jobs / "new.exit"
    fresh_log.write_text("done\n", encoding="utf-8")
    fresh_exit.write_text("0\n", encoding="utf-8")

    # 没有 .exit（仍可能运行）→ 不得清理
    running_log = jobs / "running.log"
    running_log.write_text("...\n", encoding="utf-8")
    os.utime(running_log, (old, old))

    data = _payload(repo)
    warns = "\n".join(_warns(data))
    assert "old.log" in warns
    assert "new.log" not in warns
    assert "running.log" not in warns

    _payload(repo, "--fix")
    assert not stale_log.exists()
    assert not stale_exit.exists()
    assert fresh_log.exists()
    assert running_log.exists(), "未完成的后台任务日志必须保留"


# ---------------------------------------------------------------- 证据 / 退出码 / JSON


def _write_evidence(repo: Path, hours_ago: float, result: str = "pass") -> Path:
    cache = repo / ".cache"
    cache.mkdir(parents=True, exist_ok=True)
    finished = datetime.now(timezone.utc) - timedelta(hours=hours_ago)
    path = cache / "local-ci-last.json"
    path.write_text(
        json.dumps(
            {
                "schema_version": 1,
                "source": "gov-local-ci",
                "result": result,
                "finished_at": finished.strftime("%Y-%m-%dT%H:%M:%SZ"),
            }
        )
        + "\n",
        encoding="utf-8",
    )
    return path


def test_stale_evidence_warns_and_is_not_deleted(repo: Path):
    path = _write_evidence(repo, hours_ago=48)
    data = _payload(repo)
    evidence = _check(data, "evidence")
    assert evidence["status"] == "warn"
    assert "超过 24h" in evidence["message"]
    assert "过期" in " ".join(i["text"] for i in evidence["items"])

    _payload(repo, "--fix")
    assert path.exists(), "证据文件只提示重跑，不自动删除"


def test_fresh_evidence_is_ok(repo: Path):
    _write_evidence(repo, hours_ago=1)
    data = _payload(repo)
    assert _check(data, "evidence")["status"] == "ok"


def test_strict_makes_warn_exit_1(repo: Path):
    _write_evidence(repo, hours_ago=48)

    loose = _doctor(repo)
    assert loose.returncode == 0, "仅 WARN 时默认退出码应为 0"

    strict = _doctor(repo, "--strict")
    assert strict.returncode == 1, "--strict 下 WARN 应返回 1"
    assert "[WARN] evidence" in strict.stdout
    assert "汇总" in strict.stdout


def test_json_output_is_parseable_and_complete(repo: Path):
    r = _doctor(repo, "--json")
    assert r.returncode == 0
    data = json.loads(r.stdout)
    assert data["schema_version"] == 1
    assert data["source"] == "gov-workspace-doctor"
    ids = [c["id"] for c in data["checks"]]
    assert ids == ["worktree", "branch", "remote", "locks", "jobs", "evidence", "sync"]
    assert set(data["summary"]) == {"ok", "warn", "fail"}
    assert data["exit_code"] == 0
    assert isinstance(data["fixes"], list)
    assert data["flags"]["fix"] is False


def test_missing_evidence_reported(repo: Path):
    data = _payload(repo)
    evidence = _check(data, "evidence")
    assert evidence["status"] == "warn"
    assert "local-ci-last.json" in " ".join(i["text"] for i in evidence["items"]) or (
        "local-ci" in evidence["message"]
    )
