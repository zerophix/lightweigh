#!/usr/bin/env python3
"""wf-parallel.sh plan 测试：候选筛选、锁排除、文件重叠分批、limit、JSON 输出。

覆盖: ready AC 筛选；锁排除；文件重叠不同批；非重叠同批；--limit 生效；--json 可解析。
运行: python3 -m pytest tests/unit/test_parallel_planner.py -q
"""
import json
import subprocess
import sys
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "scripts" / "workflow" / "wf-parallel.sh"


def make_task(task_id, module, files, status="pending", ac_status="ready", wave=1):
    """构造最小任务对象。"""
    return {
        "id": task_id,
        "description": "任务 " + task_id,
        "status": status,
        "module": module,
        "wave": wave,
        "target_files": files,
        "acceptance": {"status": ac_status, "verify_commands": []},
    }


def write_board(root: Path, tasks):
    ai_dir = root / ".ai"
    ai_dir.mkdir(parents=True, exist_ok=True)
    (ai_dir / "tasks.json").write_text(
        json.dumps({"version": 1, "tasks": tasks}, ensure_ascii=False),
        encoding="utf-8",
    )


def write_lock(root: Path, task_id: str, owner: str = "ai-1"):
    """写入 .ai/locks/<ID> 锁文件。"""
    locks = root / ".ai" / "locks"
    locks.mkdir(parents=True, exist_ok=True)
    (locks / task_id).write_text(json.dumps({"session": owner}), encoding="utf-8")


def plan(root: Path, *args):
    return subprocess.run(
        ["bash", str(SCRIPT), "plan", *args, "--root", str(root)],
        capture_output=True,
        text=True,
    )


def plan_json(root: Path, *args):
    proc = plan(root, "--json", *args)
    assert proc.returncode == 0, proc.stdout + proc.stderr
    return json.loads(proc.stdout)


def batch_ids(payload):
    """把批次结果压平成每批 ID 列表。"""
    return [[task["id"] for task in batch["tasks"]] for batch in payload["batches"]]


def test_filters_by_ready_acceptance(tmp_path):
    """只有 pending + ready + 有 target_files 的任务进入候选。"""
    write_board(
        tmp_path,
        [
            make_task("T-001", "auth", ["a/One.kt"]),
            make_task("T-002", "auth", ["a/Two.kt"], ac_status="draft"),
            make_task("T-003", "auth", ["a/Three.kt"], status="in_progress"),
            make_task("T-004", "auth", []),
        ],
    )
    payload = plan_json(tmp_path)
    ids = [task for batch in batch_ids(payload) for task in batch]
    assert ids == ["T-001"]
    assert payload["summary"]["candidate_count"] == 1


def test_locked_task_excluded_with_holder(tmp_path):
    """存在 .ai/locks/<ID> 的任务被排除并标注持有者。"""
    write_board(
        tmp_path,
        [
            make_task("T-001", "auth", ["a/One.kt"]),
            make_task("T-002", "guide", ["g/Two.kt"]),
        ],
    )
    write_lock(tmp_path, "T-002", owner="ses-abc")
    payload = plan_json(tmp_path)
    ids = [task for batch in batch_ids(payload) for task in batch]
    assert ids == ["T-001"]
    excluded = {item["id"]: item["reason"] for item in payload["excluded"]}
    assert "T-002" in excluded
    assert "ses-abc" in excluded["T-002"]


def test_overlapping_files_split_batches(tmp_path):
    """文件重叠的两条任务不得同批。"""
    write_board(
        tmp_path,
        [
            make_task("T-001", "auth", ["app/A.kt", "app/Shared.kt"]),
            make_task("T-002", "auth", ["app/Shared.kt", "app/B.kt"]),
        ],
    )
    payload = plan_json(tmp_path)
    groups = batch_ids(payload)
    assert ["T-001"] in groups
    assert ["T-002"] in groups
    assert len(groups) == 2


def test_non_overlapping_files_share_batch(tmp_path):
    """文件不相交的两条任务可同批。"""
    write_board(
        tmp_path,
        [
            make_task("T-001", "auth", ["app/auth/A.kt"]),
            make_task("T-002", "guide", ["app/guide/B.kt"]),
        ],
    )
    payload = plan_json(tmp_path)
    assert batch_ids(payload) == [["T-001", "T-002"]]
    first = payload["batches"][0]["tasks"]
    assert first[0]["branch"] == "feat/t-001-auth"
    assert first[0]["subagent"] == "sub-t-001"


def test_limit_caps_batch_size(tmp_path):
    """--limit 限制每批条数。"""
    write_board(
        tmp_path,
        [make_task("T-%03d" % i, "m%d" % i, ["app/f%d.kt" % i]) for i in range(1, 5)],
    )
    payload = plan_json(tmp_path, "--limit", "2")
    groups = batch_ids(payload)
    assert all(len(group) <= 2 for group in groups)
    assert len(groups) == 2


def test_json_payload_shape(tmp_path):
    """--json 输出结构稳定可解析。"""
    write_board(tmp_path, [make_task("T-001", "auth", ["app/auth/A.kt"])])
    payload = plan_json(tmp_path)
    assert set(payload) >= {"limit", "summary", "batches", "excluded"}
    assert payload["limit"] == 3
    assert payload["batches"][0]["tasks"][0]["target_files"] == ["app/auth/A.kt"]


def test_requires_plan_subcommand(tmp_path):
    """缺少 plan 子命令 -> 退出 2。"""
    write_board(tmp_path, [])
    proc = subprocess.run(
        ["bash", str(SCRIPT), "--root", str(tmp_path)],
        capture_output=True,
        text=True,
    )
    assert proc.returncode == 2


if __name__ == "__main__":
    sys.exit(pytest.main([__file__, "-q"]))
