"""wf-batch.sh 行为测试（DEC-068）：list/status/start/finish/create。

不变点的不变量测试在 test_batch_execution.py；本文件聚焦接口输出格式、
错误处理与 dry-run 模式。
"""
import json
import os
import subprocess
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "scripts" / "workflow" / "wf-batch.sh"
BOARD = ROOT / ".ai" / "tasks.json"


def _sh(*args, root: Path = None, dry: bool = False, check: bool = False):
    env = None
    if root is not None:
        env = {**os.environ, "WF_BATCH_ROOT": str(root)}
    elif dry:
        env = {**os.environ, "WF_BATCH_DRY": "1"}
    return subprocess.run(
        ["bash", str(SCRIPT), *args],
        cwd=root or ROOT, capture_output=True, text=True, env=env, check=check,
    )


def _sandbox(tmp_path: Path) -> Path:
    """Copy real tasks.json to tmp_path for isolation."""
    ai_dir = tmp_path / ".ai"
    ai_dir.mkdir(parents=True, exist_ok=True)
    shutil.copy(BOARD, ai_dir / "tasks.json")
    return tmp_path


def test_syntax():
    """Script should pass bash -n syntax check."""
    run = subprocess.run(["bash", "-n", str(SCRIPT)], cwd=ROOT, capture_output=True, text=True)
    assert run.returncode == 0, run.stderr


def test_help():
    """Script should print usage on --help."""
    run = _sh("--help")
    if run.returncode != 0:
        # Some scripts exit 0 for help, some 2
        assert "list" in run.stdout or "list" in run.stderr
    else:
        assert "list" in run.stdout


def test_list(tmp_path):
    """list should output all batches."""
    root = _sandbox(tmp_path)
    run = _sh("list", root=root)
    assert run.returncode == 0
    # Should mention at least one batch from the task board
    board = json.loads((root / ".ai" / "tasks.json").read_text(encoding="utf-8"))
    batch_ids = [b["id"] for b in board.get("batches", [])]
    assert any(bid in run.stdout for bid in batch_ids)


def test_status_unknown_batch_fails(tmp_path):
    """status should fail for unknown batch."""
    root = _sandbox(tmp_path)
    run = _sh("status", "B-nonexistent", root=root)
    assert run.returncode != 0
    assert "未找到" in run.stderr


def test_status_known_batch(tmp_path):
    """status should show task count for known batch."""
    root = _sandbox(tmp_path)
    # Use a batch that always exists (from the shared task board)
    board = json.loads(BOARD.read_text(encoding="utf-8"))
    known_batch = next((b["id"] for b in board.get("batches", []) if b["id"] != "B-test-new"), "B-infra")
    run = _sh("status", known_batch, root=root)
    assert run.returncode == 0
    assert known_batch in run.stdout


def test_start_dry_run(tmp_path):
    """start with dry-run should print mode and test plan without switching."""
    root = _sandbox(tmp_path)
    board = json.loads((root / ".ai" / "tasks.json").read_text(encoding="utf-8"))
    known_batch = next((b["id"] for b in board.get("batches", []) if b["id"] != "B-test-new"), "B-infra")
    env = {"WF_BATCH_DRY": "1"}
    run = subprocess.run(
        ["bash", str(SCRIPT), "start", known_batch],
        cwd=root, capture_output=True, text=True,
        env={**os.environ, "WF_BATCH_ROOT": str(root), **env},
    )
    assert run.returncode == 0
    assert "dry" in run.stdout.lower() or "mode=" in run.stdout


def test_create_new_batch(tmp_path):
    """create should add a new batch to the board."""
    root = _sandbox(tmp_path)
    run = _sh("create", "B-test-new", "Test Batch", root=root)
    assert run.returncode == 0
    assert "创建" in run.stdout or "B-test-new" in run.stdout
    # Verify it was added
    board = json.loads((root / ".ai" / "tasks.json").read_text(encoding="utf-8"))
    batch_ids = [b["id"] for b in board.get("batches", [])]
    assert "B-test-new" in batch_ids


def test_create_duplicate_batch_fails(tmp_path):
    """create should fail for existing batch."""
    root = _sandbox(tmp_path)
    board = json.loads((root / ".ai" / "tasks.json").read_text(encoding="utf-8"))
    existing_batch = next((b["id"] for b in board.get("batches", []) if b["id"] != "B-test-new"), "B-infra")
    run = _sh("create", existing_batch, "Duplicate", root=root)
    assert run.returncode != 0
    assert "已存在" in run.stderr


def test_finish_dry_run_all_done(tmp_path):
    """finish with dry-run on a batch with all tasks done should print plan."""
    root = _sandbox(tmp_path)
    # Create a batch with all tasks completed
    board = json.loads((root / ".ai" / "tasks.json").read_text(encoding="utf-8"))
    batch_ids = {b["id"] for b in board.get("batches", [])}
    test_batch = None
    for b in board["batches"]:
        # Find a batch that has completed tasks
        batch_tasks = [t for t in board["tasks"] if t.get("batch") == b["id"]]
        if batch_tasks and all(t.get("status") in ("completed", "done") for t in batch_tasks):
            test_batch = b["id"]
            break
    if test_batch:
        env = {**os.environ, "WF_BATCH_ROOT": str(root), "WF_BATCH_DRY": "1"}
        run = subprocess.run(
            ["bash", str(SCRIPT), "finish", test_batch],
            cwd=root, capture_output=True, text=True, env=env,
        )
        assert run.returncode == 0
        assert "all" in run.stdout.lower() or "完成" in run.stdout
