#!/usr/bin/env python3
"""GOV-PROTO-003 僵尸任务规则负向测试（DEC-060）。

背景: 该规则原实现只判 `status == in_progress` 就 fail，而消息写的是
「超过 24 小时」—— 声明与行为不符。后果严重：pre-commit 与 pre-push 都跑
gov-validate，等于**任务一旦被认领就无法通过任何提交**，工作过程被禁止。
且该规则零测试，故 bug 长期未暴露（治理设计约束 Q3 缺口）。

本文件锁死正确行为:
  - in_progress 且看板 24 小时内有更新 → 放行（可正常工作与提交）
  - in_progress 但停滞超 24 小时 → FAIL（这才是真正的僵尸）
  - 时间戳缺失或不可解析 → 保守判僵尸（宁可提醒也不放走）

运行: pytest tests/unit/test_zombie_rule.py
"""
import contextlib
import io
import json
import sys
from datetime import datetime, timedelta, timezone
from pathlib import Path

import pytest

ROOT_P = Path(__file__).resolve().parents[2]
if str(ROOT_P) not in sys.path:
    sys.path.insert(0, str(ROOT_P))

from scripts.governance.modules import gov_validate_core as core
from scripts.governance.modules import gov_validate_protocol as proto


def _clean_errors_guard():
    """快照-恢复语义 (DEC-007): 只隔离本测试产生的错误。"""
    saved = list(core.errors)
    core.errors.clear()
    yield
    core.errors.clear()
    core.errors.extend(saved)


@pytest.fixture(autouse=True)
def _clean_errors():
    yield from _clean_errors_guard()


def _has(rule_id: str) -> bool:
    return any(rule_id in e for e in core.errors)


def _iso(hours_ago: float) -> str:
    return (datetime.now(timezone.utc) - timedelta(hours=hours_ago)).strftime(
        "%Y-%m-%dT%H:%M:%SZ"
    )


def _setup(tmp_path: Path, monkeypatch, tasks: list):
    """构造最小可运行环境: 空收件箱 + 空缓存 + 指定任务板。"""
    inbox = tmp_path / "docs" / "ai" / "inbox"
    inbox.mkdir(parents=True, exist_ok=True)
    cache = tmp_path / ".cache"
    cache.mkdir(parents=True, exist_ok=True)
    (cache / "context.jsonl").write_text(
        json.dumps({"type": "handoff", "ts": _iso(1)}), encoding="utf-8"
    )
    ai = tmp_path / ".ai"
    ai.mkdir(parents=True, exist_ok=True)
    (ai / "tasks.json").write_text(
        json.dumps({"tasks": tasks}, ensure_ascii=False), encoding="utf-8"
    )

    monkeypatch.setattr(proto, "ROOT", tmp_path)
    monkeypatch.setattr(proto, "MEMORY_FILE", cache / "context.jsonl")
    monkeypatch.setattr(proto, "DOCS", tmp_path / "docs")
    monkeypatch.setattr(core, "ROOT", tmp_path)


def _run():
    buf = io.StringIO()
    with contextlib.redirect_stdout(buf):
        proto.check_all()


def _task(tid: str, status: str, hours_ago: float | None = None, ts: str | None = None):
    t = {
        "id": tid,
        "status": status,
        "created_at": _iso(48),
        "acceptance": {"status": "ready", "definition_of_done": ["已完成"]},
    }
    if ts is not None:
        t["updated_at"] = ts
    elif hours_ago is not None:
        t["updated_at"] = _iso(hours_ago)
    return t


def test_fresh_in_progress_is_not_zombie(tmp_path, monkeypatch):
    """核心: 刚认领/有更新的 in_progress 必须放行，否则工作过程中无法提交。"""
    _setup(tmp_path, monkeypatch, [_task("T-100", "in_progress", hours_ago=1)])
    _run()
    assert not _has("GOV-PROTO-003"), (
        "24 小时内的 in_progress 被判为僵尸 —— 规则退化回「认领即违规」，"
        f"工作过程将无法提交: {core.errors}"
    )


def test_stale_in_progress_is_zombie(tmp_path, monkeypatch):
    """停滞超 24 小时的 in_progress 必须 FAIL —— 这才是规则本意。"""
    _setup(tmp_path, monkeypatch, [_task("T-101", "in_progress", hours_ago=30)])
    _run()
    assert _has("GOV-PROTO-003"), "停滞 30 小时的任务未被识别为僵尸"


def test_boundary_just_under_24h_passes(tmp_path, monkeypatch):
    _setup(tmp_path, monkeypatch, [_task("T-102", "in_progress", hours_ago=23.5)])
    _run()
    assert not _has("GOV-PROTO-003"), "23.5 小时被误判超时"


def test_boundary_over_24h_fails(tmp_path, monkeypatch):
    _setup(tmp_path, monkeypatch, [_task("T-103", "in_progress", hours_ago=25)])
    _run()
    assert _has("GOV-PROTO-003"), "25 小时未被判超时"


def test_missing_timestamp_is_conservative_zombie(tmp_path, monkeypatch):
    """无法判定停滞时长 → 保守当僵尸（宁可提醒也不放走）。"""
    _setup(tmp_path, monkeypatch, [_task("T-104", "in_progress")])   # 无 updated_at/created_at
    # created_at 由 _task 写入，构造一个真正无时间戳的任务
    t = {"id": "T-105", "status": "in_progress",
         "acceptance": {"status": "ready", "definition_of_done": ["x"]}}
    _setup(tmp_path, monkeypatch, [t])
    _run()
    assert _has("GOV-PROTO-003"), "无时间戳的 in_progress 应保守判为僵尸"


def test_unparsable_timestamp_is_conservative_zombie(tmp_path, monkeypatch):
    _setup(tmp_path, monkeypatch, [_task("T-106", "in_progress", ts="not-a-date")])
    _run()
    assert _has("GOV-PROTO-003"), "时间戳不可解析时应保守判为僵尸"


def test_pending_and_done_tasks_never_flagged(tmp_path, monkeypatch):
    """pending / done 不参与僵尸判定（防误报）。"""
    tasks = [
        _task("T-107", "pending", hours_ago=500),
        _task("T-108", "done", hours_ago=500),
        _task("T-109", "blocked", hours_ago=500),
    ]
    _setup(tmp_path, monkeypatch, tasks)
    _run()
    assert not _has("GOV-PROTO-003"), f"非 in_progress 任务被误判: {core.errors}"


def test_empty_board_passes(tmp_path, monkeypatch):
    _setup(tmp_path, monkeypatch, [])
    _run()
    assert not _has("GOV-PROTO-003")
