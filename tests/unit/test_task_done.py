#!/usr/bin/env python3
"""gov-task-done.sh 正向/负向测试：DoD 校验必须真的跑命令、真的看文件。

覆盖: 目标文件缺失 -> FAIL；verify_commands 失败 -> FAIL；全部通过 -> 0；
      --dry 不执行命令；未知任务 -> 2；--json 可解析。
运行: python3 -m pytest tests/unit/test_task_done.py -q
"""
import json
import subprocess
import sys
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "scripts" / "governance" / "gov-task-done.sh"


def write_board(root: Path, tasks):
    """在临时根目录写入最小任务板。"""
    ai_dir = root / ".ai"
    ai_dir.mkdir(parents=True, exist_ok=True)
    (ai_dir / "tasks.json").write_text(
        json.dumps({"version": 1, "tasks": tasks}, ensure_ascii=False),
        encoding="utf-8",
    )


def make_task(task_id="T-001", files=None, commands=None, prototype=None):
    """构造最小任务对象。"""
    task = {
        "id": task_id,
        "description": "测试任务",
        "status": "pending",
        "target_files": files if files is not None else ["app/src/main/kotlin/Demo.kt"],
        "acceptance": {
            "status": "ready",
            "verify_commands": commands or [],
        },
    }
    if prototype:
        task["prototype_ref"] = {"file": "design/x.html", "views": ["v-one"]}
    return task


def run(root: Path, *args):
    """调用脚本，返回 CompletedProcess。"""
    return subprocess.run(
        ["bash", str(SCRIPT), *args, "--root", str(root)],
        capture_output=True,
        text=True,
    )


def touch(path: Path):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("// stub\n", encoding="utf-8")


def test_missing_target_file_fails(tmp_path):
    """目标文件缺失必须 FAIL 并列出缺失路径。"""
    write_board(tmp_path, [make_task(files=["app/src/main/kotlin/Missing.kt"])])
    proc = run(tmp_path, "T-001")
    assert proc.returncode == 1, proc.stdout + proc.stderr
    assert "[FAIL]" in proc.stdout
    assert "Missing.kt" in proc.stdout
    assert "总判定: FAIL" in proc.stdout


def test_verify_command_failure_fails(tmp_path):
    """任一 verify_command 非零退出即整体 FAIL。"""
    touch(tmp_path / "app/src/main/kotlin/Demo.kt")
    write_board(tmp_path, [make_task(commands=["true", "exit 7"])])
    proc = run(tmp_path, "T-001")
    assert proc.returncode == 1, proc.stdout + proc.stderr
    assert "总判定: FAIL" in proc.stdout


def test_all_pass_returns_zero(tmp_path):
    """文件存在且命令全通过 -> 退出 0。"""
    touch(tmp_path / "app/src/main/kotlin/Demo.kt")
    write_board(tmp_path, [make_task(commands=["true", "echo ok"])])
    proc = run(tmp_path, "T-001")
    assert proc.returncode == 0, proc.stdout + proc.stderr
    assert "总判定: PASS" in proc.stdout
    assert "[PASS]" in proc.stdout


def test_dry_run_does_not_execute(tmp_path):
    """--dry 只打印，不执行命令（touch marker 不得落地）。"""
    touch(tmp_path / "app/src/main/kotlin/Demo.kt")
    marker = tmp_path / "marker"
    write_board(tmp_path, [make_task(commands=["touch marker"])])
    proc = run(tmp_path, "T-001", "--dry")
    assert proc.returncode == 0, proc.stdout + proc.stderr
    assert not marker.exists(), "--dry 不应执行 verify_commands"
    assert "[PLAN]" in proc.stdout
    assert "dry-run" in proc.stdout


def test_unknown_task_returns_two(tmp_path):
    """未知任务号 -> 退出 2。"""
    write_board(tmp_path, [make_task(task_id="T-001")])
    proc = run(tmp_path, "T-999")
    assert proc.returncode == 2, proc.stdout + proc.stderr
    assert "任务不存在" in proc.stderr


def test_json_output_is_parseable(tmp_path):
    """--json 输出可解析且含 checks 明细。"""
    touch(tmp_path / "app/src/main/kotlin/Demo.kt")
    write_board(tmp_path, [make_task(commands=["true"])])
    proc = run(tmp_path, "T-001", "--json")
    assert proc.returncode == 0, proc.stdout + proc.stderr
    payload = json.loads(proc.stdout)
    assert payload["task"] == "T-001"
    assert payload["ok"] is True
    assert any(item["type"] == "file" for item in payload["checks"])
    assert any(item["type"] == "command" for item in payload["checks"])


if __name__ == "__main__":
    sys.exit(pytest.main([__file__, "-q"]))
