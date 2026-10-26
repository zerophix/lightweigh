"""完成证据消费（DEC-078）行为测试。

证据机制缺的从来不是"写"，而是"读"。这里钉死消费方的行为：
  - 缺证据 → block 模式拒绝
  - ok=false → block 模式拒绝（失败也留痕，但不能算完成）
  - ok=true → 通过
  - 无 Task trailer → 不阻塞（chore/docs 分支不受影响）

另外验证 gov-task-done 在失败时也会写证据（否则失败现场消失）。
"""
import json
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CHECK = ROOT / "scripts" / "governance" / "gov-evidence-check.sh"
TASK_DONE = ROOT / "scripts" / "governance" / "gov-task-done.sh"


def _check(root, *args):
    return subprocess.run(["bash", str(CHECK), "--root", str(root), *args],
                          capture_output=True, text=True)


def _write(root, task, ok):
    d = root / ".ai" / "evidence"
    d.mkdir(parents=True, exist_ok=True)
    (d / f"{task}.json").write_text(
        json.dumps({"task": task, "ok": ok, "git_sha": "deadbeef"}), encoding="utf-8")


def test_missing_evidence_blocks(tmp_path):
    run = _check(tmp_path, "--tasks", "T-1", "--block")
    assert run.returncode == 1
    assert "缺证据" in run.stdout


def test_failed_evidence_blocks(tmp_path):
    _write(tmp_path, "T-1", False)
    run = _check(tmp_path, "--tasks", "T-1", "--block")
    assert run.returncode == 1
    assert "未通过" in run.stdout


def test_passing_evidence_allows(tmp_path):
    _write(tmp_path, "T-1", True)
    run = _check(tmp_path, "--tasks", "T-1", "--block")
    assert run.returncode == 0, run.stdout


def test_no_task_trailer_does_not_block(tmp_path):
    run = _check(tmp_path, "--tasks", *[], "--block")
    # 空 --tasks 会回落到 git log；临时目录不是 git 仓库 → 视为无任务
    assert run.returncode == 0
    assert "跳过" in run.stdout or "无任务" in run.stdout


def test_task_done_writes_evidence_even_on_failure(tmp_path):
    """失败也必须留证据：否则无法回答"卡在哪一步"。"""
    task = {
        "id": "T-900",
        "status": "in_progress",
        "target_files": ["ok.txt"],
        "acceptance": {
            "status": "ready",
            "verify_commands": ["false"],
        },
    }
    (tmp_path / ".ai").mkdir()
    (tmp_path / ".ai" / "tasks.json").write_text(
        json.dumps({"tasks": [task]}), encoding="utf-8")
    (tmp_path / "ok.txt").write_text("x", encoding="utf-8")

    run = subprocess.run(
        ["bash", str(TASK_DONE), "T-900", "--root", str(tmp_path)],
        capture_output=True, text=True,
    )
    assert run.returncode == 1, run.stdout + run.stderr
    ev = tmp_path / ".ai" / "evidence" / "T-900.json"
    assert ev.exists(), run.stdout
    data = json.loads(ev.read_text(encoding="utf-8"))
    assert data["ok"] is False
    assert data["checks"], "证据里必须保留逐条检查结果"


def test_queue_consumes_evidence_before_merge():
    queue = (ROOT / "scripts" / "workflow" / "wf-queue.sh").read_text(encoding="utf-8")
    assert "gov-evidence-check.sh" in queue
    assert "GOV_ALLOW_MISSING_EVIDENCE" in queue
