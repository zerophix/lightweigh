"""批次执行（wf-batch）行为测试。

批次是"一次开工/收工"的单位；角色决定开发期开关与测试计划：
  - 只有 dev 的批次 → dev 模式（关钩子）
  - 含 gov 的批次 → full 模式（开钩子）
  - 未完成就收工 → 拒绝
"""
import json
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CLI = ROOT / "scripts" / "workflow" / "wf-batch.sh"


def _sandbox(tmp_path, role="dev", status="pending"):
    board = tmp_path / ".ai" / "tasks.json"
    board.parent.mkdir(parents=True)
    board.write_text(json.dumps({
        "version": 1,
        "groups": [{"id": "demo", "title": "演示", "tasks": ["T-001"]}],
        "tasks": [{
            "id": "T-001", "status": status, "group_id": "demo", "batch": "B-demo",
            "role": role, "description": "演示任务", "target_files": ["a.kt"],
            "acceptance": {"status": "ready", "verify_commands": ["true"]},
        }],
    }, ensure_ascii=False), encoding="utf-8")
    return tmp_path


def _run(root, *args):
    return subprocess.run(
        ["bash", str(CLI), *args], cwd=root, capture_output=True, text=True,
        env={**__import__("os").environ, "WF_BATCH_ROOT": str(root), "WF_BATCH_DRY": "1"},
    )


def test_list_shows_progress(tmp_path):
    root = _sandbox(tmp_path, status="completed")
    out = _run(root, "list")
    assert out.returncode == 0
    assert "B-demo" in out.stdout
    assert "1/ 1" in out.stdout or "1/1" in out.stdout


def test_dev_batch_switches_to_dev_mode(tmp_path):
    root = _sandbox(tmp_path)
    out = _run(root, "start", "B-demo")
    assert out.returncode == 0
    assert "mode=dev" in out.stdout
    assert "testDebugUnitTest" in out.stdout


def test_gov_batch_keeps_gates_on(tmp_path):
    root = _sandbox(tmp_path, role="gov")
    out = _run(root, "start", "B-demo")
    assert "mode=full" in out.stdout
    assert "gov-local-ci" in out.stdout


def test_finish_refuses_incomplete(tmp_path):
    root = _sandbox(tmp_path, status="pending")
    out = _run(root, "finish", "B-demo")
    assert out.returncode == 1
    assert "未完成" in out.stderr


def test_finish_accepts_complete(tmp_path):
    root = _sandbox(tmp_path, status="completed")
    out = _run(root, "finish", "B-demo")
    assert out.returncode == 0
    assert "全部完成" in out.stdout
