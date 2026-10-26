"""任务图（依赖 / 就绪 / 检查点）行为测试。

参考 Beads 的 `bd ready` 与 Task Master 的 `next`：
  - 只有所有阻塞依赖都关闭，任务才算 ready
  - 依赖环必须被检出
  - 检查点按时间追加，为长任务保留上下文
"""
import json
import shutil
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CLI = ROOT / "scripts" / "workflow" / "wf-task-board.sh"


def _sandbox(tmp_path):
    board = tmp_path / ".ai" / "tasks.json"
    board.parent.mkdir(parents=True)
    data = {
        "version": 1,
        "groups": [{"id": "demo", "title": "演示组", "tasks": []}],
        "batches": [{"id": "B-demo", "title": "演示批次", "tasks": []}],
        "tasks": [
            {"id": "T-001", "status": "completed", "description": "底座", "group_id": "demo",
             "target_files": ["a.kt"], "acceptance": {"status": "ready", "verify_commands": ["true"]}},
            {"id": "T-002", "status": "pending", "description": "依赖底座", "group_id": "demo",
             "depends_on": ["T-001"], "target_files": ["b.kt"],
             "acceptance": {"status": "ready", "verify_commands": ["true"]}},
            {"id": "T-003", "status": "pending", "description": "依赖第二步", "group_id": "demo",
             "depends_on": ["T-002"], "target_files": ["c.kt"],
             "acceptance": {"status": "ready", "verify_commands": ["true"]}},
        ],
    }
    board.write_text(json.dumps(data, ensure_ascii=False), encoding="utf-8")
    return tmp_path


def _run(root, *args):
    return subprocess.run(
        ["bash", str(CLI), *args], cwd=root, capture_output=True, text=True,
        env={**__import__("os").environ, "WF_BOARD_ROOT": str(root)},
    )


def test_ready_skips_blocked_tasks(tmp_path):
    root = _sandbox(tmp_path)
    out = _run(root, "ready")
    assert out.returncode == 0
    assert "T-002" in out.stdout
    assert "T-003" not in out.stdout


def test_blocked_lists_what_it_waits_for(tmp_path):
    root = _sandbox(tmp_path)
    out = _run(root, "blocked")
    assert "T-003" in out.stdout
    assert "T-002" in out.stdout


def test_cycle_is_detected(tmp_path):
    root = _sandbox(tmp_path)
    _run(root, "dep", "add", "T-001", "T-003")
    out = _run(root, "dep", "cycles")
    assert out.returncode == 1
    assert "环" in out.stdout


def test_checkpoint_appends_progress(tmp_path):
    root = _sandbox(tmp_path)
    out = _run(root, "checkpoint", "T-002", "已完成接口，下一步补测试")
    assert out.returncode == 0
    board = json.loads((root / ".ai" / "tasks.json").read_text(encoding="utf-8"))
    task = next(t for t in board["tasks"] if t["id"] == "T-002")
    assert len(task["progress"]) == 1
    assert "下一步补测试" in task["progress"][0]["note"]


def test_add_with_dependency(tmp_path):
    root = _sandbox(tmp_path)
    out = _run(root, "add", "第四步：联调", "--group", "demo",
               "--source", "gap", "--batch", "B-demo", "--depends-on", "T-003")
    assert out.returncode == 0, out.stderr
    board = json.loads((root / ".ai" / "tasks.json").read_text(encoding="utf-8"))
    created = [t for t in board["tasks"] if t["status"] == "pending" and "联调" in t["description"]][0]
    assert created["depends_on"] == ["T-003"]
