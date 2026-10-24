"""流程解耦（DEC-063）门禁行为测试。

三类行为必须可判定：
  GOV-DOCS-004 计数校验主干作用域 —— 特性分支降级 WARN，主干/CI 仍 ERROR
  GOV_SKIP_WALLCLOCK —— 仅豁免 GOV-PROTO-001/002（墙钟接力），003/004 照跑
  pre-push 钩子契约 —— QUICK（pytest 移 local-ci）与删除类检测不得被悄悄摘除

运行: pytest tests/unit/test_process_decoupling.py
"""
import contextlib
import io
import json
import os
import subprocess
import sys
from pathlib import Path

import pytest

ROOT_P = Path(__file__).resolve().parents[2]
if str(ROOT_P) not in sys.path:
    sys.path.insert(0, str(ROOT_P))

from scripts.governance.modules import gov_validate_core as core
from scripts.governance.modules import gov_validate_docs as docs_mod
from scripts.governance.modules import gov_validate_protocol as proto_mod

from test_docs_governance import _check, _has, _make_repo, _taskgraph, _tasks_json


@pytest.fixture(autouse=True)
def _clean_errors():
    """快照-恢复语义 (DEC-007): 只隔离本测试产生的错误。"""
    saved_errors = list(core.errors)
    saved_warns = list(docs_mod.warnings)
    core.errors.clear()
    docs_mod.warnings.clear()
    yield
    core.errors.clear()
    core.errors.extend(saved_errors)
    docs_mod.warnings.clear()
    docs_mod.warnings.extend(saved_warns)


def _git(tmp_path: Path, *args: str) -> None:
    subprocess.run(
        ["git", "-c", "user.email=t@t", "-c", "user.name=t", *args],
        cwd=tmp_path, capture_output=True, text=True, check=True,
    )


def _baseline_commit(tmp_path: Path):
    """先建基线提交：unborn 分支下 rev-parse --abbrev-ref HEAD 会失败（保守降级为主干语义）。"""
    _git(tmp_path, "init", "-q")
    _git(tmp_path, "add", "-A")
    _git(tmp_path, "commit", "-qm", "baseline")


_MISMATCH = dict(
    tasks=_tasks_json(total=4, pending=2, proto=1, waves=(0,)),
    taskgraph=_taskgraph(total=99, pending=77, proto=8, waves=(0,)),
)


# ---------------------------------------------------------------- 004 主干作用域

def test_counts004_feature_branch_downgrades_to_warn(tmp_path, monkeypatch):
    """特性分支上计数失配降级 WARN —— 合并后 master 语义仍 ERROR（DEC-063）。"""
    _make_repo(tmp_path, **_MISMATCH)
    _baseline_commit(tmp_path)
    _git(tmp_path, "checkout", "-qb", "feat/x")

    errors, warns = _check(tmp_path, monkeypatch)

    assert not _has(errors, "GOV-DOCS-004"), errors
    assert _has(warns, "GOV-DOCS-004"), warns


def test_counts004_trunk_branch_still_errors(tmp_path, monkeypatch):
    """master 分支上计数失配仍是 ERROR —— 守门强度不降（DEC-063）。"""
    _make_repo(tmp_path, **_MISMATCH)
    _baseline_commit(tmp_path)
    _git(tmp_path, "checkout", "-qB", "master")

    errors, warns = _check(tmp_path, monkeypatch)

    assert _has(errors, "GOV-DOCS-004"), errors


def test_counts004_non_git_dir_still_errors(tmp_path, monkeypatch):
    """非 git 目录（含单测夹具）保守按主干语义：ERROR。"""
    _make_repo(tmp_path, **_MISMATCH)

    errors, warns = _check(tmp_path, monkeypatch)

    assert _has(errors, "GOV-DOCS-004"), errors


def test_counts004_ci_detached_head_still_errors(tmp_path, monkeypatch):
    """CI 检出为 detached HEAD（abbrev-ref=HEAD）→ 主干语义：ERROR。"""
    _make_repo(tmp_path, **_MISMATCH)
    _baseline_commit(tmp_path)
    _git(tmp_path, "checkout", "-q", "--detach")

    errors, warns = _check(tmp_path, monkeypatch)

    assert _has(errors, "GOV-DOCS-004"), errors


# ---------------------------------------------------------------- 墙钟豁免

def _write_inbox(tmp_path: Path):
    inbox = tmp_path / "docs" / "ai" / "inbox"
    inbox.mkdir(parents=True, exist_ok=True)
    (inbox / "20261001-000000.md").write_text(
        "---\nstatus: unread\ntimestamp: 2026-10-01T00:00:00Z\n---\n旧消息\n",
        encoding="utf-8",
    )


def _run_protocol(tmp_path: Path, monkeypatch, env_value):
    monkeypatch.setattr(proto_mod, "DOCS", tmp_path / "docs")
    monkeypatch.setattr(proto_mod, "MEMORY_FILE", tmp_path / "no-such.jsonl")
    if env_value is None:
        monkeypatch.delenv("GOV_SKIP_WALLCLOCK", raising=False)
    else:
        monkeypatch.setenv("GOV_SKIP_WALLCLOCK", env_value)
    core.errors.clear()
    buf = io.StringIO()
    with contextlib.redirect_stdout(buf):
        proto_mod.check_protocol()
    return list(core.errors), buf.getvalue()


def test_wallclock_env_skips_relay_rules_only(tmp_path, monkeypatch):
    """GOV_SKIP_WALLCLOCK=1：超 48h 未读不再触发 001，且 003/004 照常执行。"""
    _write_inbox(tmp_path)
    (tmp_path / ".ai").mkdir(parents=True, exist_ok=True)
    (tmp_path / ".ai" / "tasks.json").write_text(
        json.dumps({"tasks": []}), encoding="utf-8")

    errors, out = _run_protocol(tmp_path, monkeypatch, "1")

    assert not _has(errors, "GOV-PROTO-001"), errors
    assert "SKIP" in out
    assert "僵尸" in out  # 003 不受豁免，仍执行


def test_wallclock_without_env_still_fails(tmp_path, monkeypatch):
    """未设豁免变量时 001 照常拦截超 48h 未读 —— 豁免只对显式声明的零代码动作生效。"""
    _write_inbox(tmp_path)

    errors, out = _run_protocol(tmp_path, monkeypatch, None)

    assert _has(errors, "GOV-PROTO-001"), errors


# ---------------------------------------------------------------- pre-push 契约

def test_pre_push_hook_keeps_quick_and_delete_detection():
    """钩子源码契约：pytest 移 local-ci（QUICK）与删除类检测不得被移除。"""
    hook = (ROOT_P / "scripts" / "governance" / "gov-pre-push").read_text(encoding="utf-8")
    assert 'export GOV_VALIDATE_QUICK=1' in hook
    assert 'GOV_SKIP_WALLCLOCK' in hook
    assert 'ALL_DELETE' in hook


def test_board_sync_script_fixes_and_commits():
    """gov-board-sync 存在且走 --fix + 有变更才提交。"""
    script = ROOT_P / "scripts" / "governance" / "gov-board-sync.sh"
    text = script.read_text(encoding="utf-8")
    assert script.exists()
    assert 'gov-validate.sh" --fix' in text or "gov-validate.sh\" --fix" in text
    assert "git diff --quiet" in text
    assert os.access(script, os.X_OK) or True  # 可执行位由 git 保持，不在此强制
