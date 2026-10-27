"""任务入库去重门（根治重复建单）。

两个行为：
  - 描述与既有任务高度相似 → 拒绝，并给出 supersedes / distinct 两条出路
  - 明确声明差异后 → 允许创建，并落到指定分组

所有测试使用 tmp_path 沙箱副本，避免修改真实 .ai/tasks.json。
"""
import json
import os
import shutil
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CLI = ROOT / "scripts" / "workflow" / "wf-task-board.sh"


def _sandbox(tmp_path):
    """Copy real .ai/ to tmp_path/.ai/ for full isolation."""
    ai_dir = tmp_path / ".ai"
    ai_dir.mkdir(parents=True, exist_ok=True)
    shutil.copy(ROOT / ".ai" / "tasks.json", ai_dir / "tasks.json")
    archive = ROOT / ".ai" / "tasks-archive.json"
    if archive.exists():
        shutil.copy(archive, ai_dir / "tasks-archive.json")
    return tmp_path


def _add(root, *args):
    return subprocess.run(
        ["bash", str(CLI), "add", *args],
        cwd=root, capture_output=True, text=True,
        env={**os.environ, "WF_BOARD_ROOT": str(root)},
    )


def _task_ids(sandbox):
    """Return the set of task IDs currently in the sandbox board."""
    board = json.loads((sandbox / ".ai" / "tasks.json").read_text(encoding="utf-8"))
    return {t["id"] for t in board["tasks"]}


def test_rejects_near_duplicate(tmp_path):
    root = _sandbox(tmp_path)
    before = _task_ids(root)
    run = _add(
        root,
        "AI 结构化卡片（饮食卡解析入库/运动卡/指标解读卡/拍照入口/欢迎示例）",
        "--group", "ai", "--source", "gap", "--batch", "B-w1-screens",
    )
    assert run.returncode == 1
    assert "相似度" in run.stderr
    assert "--distinct" in run.stderr or "--supersedes" in run.stderr
    # 确保沙箱没有被修改
    after = _task_ids(root)
    assert before == after, "沙箱被意外修改"


def test_accepts_with_declared_difference(tmp_path):
    root = _sandbox(tmp_path)
    before = _task_ids(root)
    run = _add(
        root,
        "AI 结构化卡片（饮食卡解析入库/运动卡/指标解读卡/拍照入口/欢迎示例）",
        "--group", "ai", "--source", "gap", "--batch", "B-w1-screens",
        "--distinct", "本次只做拍照入口的裁剪与压缩",
    )
    assert run.returncode == 0, run.stderr
    after = _task_ids(root)
    new_ids = after - before
    assert len(new_ids) == 1, f"期望创建 1 个新任务，实际创建 {len(new_ids)} 个"
    board = json.loads((root / ".ai" / "tasks.json").read_text(encoding="utf-8"))
    new_task = next(t for t in board["tasks"] if t["id"] in new_ids)
    assert new_task["notes"].startswith("差异:"), f"notes 不以'差异:'开头: {new_task['notes']}"


def test_unknown_group_is_rejected(tmp_path):
    root = _sandbox(tmp_path)
    before = _task_ids(root)
    run = _add(root, "某个全新能力", "--group", "no-such-group",
               "--source", "gap", "--batch", "B-w1-screens")
    assert run.returncode == 1
    assert "未知分组" in run.stderr
    after = _task_ids(root)
    assert before == after, "沙箱被意外修改"


def test_missing_source_is_rejected(tmp_path):
    root = _sandbox(tmp_path)
    before = _task_ids(root)
    run = _add(root, "没有来源的任务", "--group", "ai", "--batch", "B-w1-screens")
    assert run.returncode == 1
    assert "--source" in run.stderr
    after = _task_ids(root)
    assert before == after, "沙箱被意外修改"


def test_dec_source_requires_ref(tmp_path):
    root = _sandbox(tmp_path)
    before = _task_ids(root)
    run = _add(root, "决策衍生但没写决策号", "--group", "ai",
               "--source", "dec", "--batch", "B-w1-screens")
    assert run.returncode == 1
    assert "source-ref" in run.stderr
    after = _task_ids(root)
    assert before == after, "沙箱被意外修改"
