"""任务入库去重门（根治重复建单）。

两个行为：
  - 描述与既有任务高度相似 → 拒绝，并给出 supersedes / distinct 两条出路
  - 明确声明差异后 → 允许创建，并落到指定分组
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
    shutil.copy(ROOT / ".ai" / "tasks.json", board)
    return tmp_path


def _add(root, *args):
    return subprocess.run(
        ["bash", str(CLI), "add", *args],
        cwd=root, capture_output=True, text=True,
        env={**__import__("os").environ, "WF_BOARD_ROOT": str(root)},
    )


def test_rejects_near_duplicate(tmp_path):
    root = _sandbox(tmp_path)
    run = _add(
        root,
        "AI 结构化卡片（饮食卡解析入库/运动卡/指标解读卡/拍照入口/欢迎示例）",
        "--group", "ai",
    )
    assert run.returncode == 1
    assert "相似度" in run.stderr
    assert "--distinct" in run.stderr or "--supersedes" in run.stderr


def test_accepts_with_declared_difference(tmp_path):
    root = _sandbox(tmp_path)
    run = _add(
        root,
        "AI 结构化卡片（饮食卡解析入库/运动卡/指标解读卡/拍照入口/欢迎示例）",
        "--group", "ai",
        "--distinct", "本次只做拍照入口的裁剪与压缩",
    )
    assert run.returncode == 0, run.stderr
    board = json.loads((root / ".ai" / "tasks.json").read_text(encoding="utf-8"))
    created = [t for t in board["tasks"] if t.get("group_id") == "ai" and t["status"] == "pending"]
    assert created, "应创建一条 pending 任务"
    assert created[-1]["notes"].startswith("差异:")


def test_unknown_group_is_rejected(tmp_path):
    root = _sandbox(tmp_path)
    run = _add(root, "某个全新能力", "--group", "no-such-group")
    assert run.returncode == 1
    assert "未知分组" in run.stderr
