"""开发/质量解耦（DEC-064）不变量测试。

DEC-063 的中间形态（004 分支作用域、GOV_SKIP_WALLCLOCK 豁免）已被 DEC-064 取代。
本文件钉死最终不变量，防止被回归：

  - 墙钟规则 GOV-PROTO-001/002 已退役：超时未读 / 旧 handoff 不再拦任何 git 操作
  - GOV-DOCS-004 计数校验已退役：任务板计数不再手写进 03_TaskGraph（派生数据不入库）
  - 任务板健康规则 003/004 仍生效：in_progress 缺 AC DoD 必须 FAIL
  - pre-push 契约：QUICK 开启（pytest 归 local-ci），不再有墙钟豁免分支

运行: pytest tests/unit/test_process_decoupling.py
"""
import contextlib
import io
import json
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


def _write_inbox(tmp_path: Path, status: str = "unread") -> None:
    inbox = tmp_path / "docs" / "ai" / "inbox"
    inbox.mkdir(parents=True, exist_ok=True)
    (inbox / "20260101-000000.md").write_text(
        f"---\nstatus: {status}\ntimestamp: 2026-01-01T00:00:00Z\n---\n旧接力信\n",
        encoding="utf-8",
    )


def _run_protocol(tmp_path: Path, monkeypatch):
    monkeypatch.setattr(proto_mod, "ROOT", tmp_path)
    core.errors.clear()
    buf = io.StringIO()
    with contextlib.redirect_stdout(buf):
        proto_mod.check_protocol()
    return list(core.errors), buf.getvalue()


def _write_tasks(tmp_path: Path, tasks: list) -> None:
    (tmp_path / ".ai").mkdir(parents=True, exist_ok=True)
    (tmp_path / ".ai" / "tasks.json").write_text(
        json.dumps({"tasks": tasks}, ensure_ascii=False), encoding="utf-8")


# ---------------------------------------------------------------- 墙钟退役

def test_wallclock_rules_retired(tmp_path, monkeypatch):
    """超 48h 未读留言不再产生 001/002 错误（DEC-064 退役墙钟门禁）。"""
    _write_inbox(tmp_path, "unread")
    _write_tasks(tmp_path, [])

    errors, out = _run_protocol(tmp_path, monkeypatch)

    assert not _has(errors, "GOV-PROTO-001"), errors
    assert not _has(errors, "GOV-PROTO-002"), errors
    assert "收件箱 48h" not in out


# ---------------------------------------------------------------- 任务板健康保留

def test_task_board_rules_still_enforced(tmp_path, monkeypatch):
    """003/004 保留：in_progress 缺 AC DoD 必须 FAIL（质量不放松）。"""
    _write_tasks(tmp_path, [{
        "id": "T-001",
        "status": "in_progress",
        "created_at": "2026-10-24T00:00:00Z",
        "updated_at": "2026-10-24T00:00:00Z",
    }])

    errors, _ = _run_protocol(tmp_path, monkeypatch)

    assert _has(errors, "GOV-PROTO-004"), errors


# ---------------------------------------------------------------- 计数退役

def test_counts_check_retired(tmp_path, monkeypatch):
    """摘要数字漂移不再报 GOV-DOCS-004：计数是派生数据，不入库手写。"""
    _make_repo(
        tmp_path,
        tasks=_tasks_json(total=4, pending=2, proto=1, waves=(0,)),
        taskgraph=_taskgraph(total=99, pending=77, proto=8, waves=(0,)),
    )

    errors, warns = _check(tmp_path, monkeypatch)

    assert not _has(errors, "GOV-DOCS-004"), errors
    assert not _has(warns, "GOV-DOCS-004"), warns


def test_taskgraph_has_no_handwritten_counts():
    """真仓库的 03_TaskGraph 不再出现手写计数锚点（防回潮）。"""
    text = (ROOT_P / "docs" / "ai" / "lifecycle" / "03_TaskGraph.md").read_text(
        encoding="utf-8")
    assert "条 pending" not in text
    assert "三波合计" not in text


# ---------------------------------------------------------------- pre-push 契约

def test_pre_push_contract():
    """push 只做秒级检查：QUICK 开启（pytest 归 local-ci），无墙钟豁免残留。"""
    hook = (ROOT_P / "scripts" / "governance" / "gov-pre-push").read_text(
        encoding="utf-8")
    assert "export GOV_VALIDATE_QUICK=1" in hook
    assert "GOV_SKIP_WALLCLOCK" not in hook


def test_board_sync_still_available():
    """派生物同步入口仍在（合并队列依赖它）。"""
    text = (ROOT_P / "scripts" / "governance" / "gov-board-sync.sh").read_text(
        encoding="utf-8")
    assert "--fix" in text
    assert "git diff --quiet" in text
