#!/usr/bin/env python3
"""gov-task-done.sh 正向/负向测试：DoD 校验必须真的跑命令、真的看文件。

覆盖: 目标文件缺失 -> FAIL；verify_commands 失败 -> FAIL；全部通过 -> 0；
    --dry 不执行命令；未知任务 -> 2；--json 可解析。
运行: python3 -m pytest tests/unit/test_task_done.py -q
"""
import json
import os
import subprocess
import sys
import shutil
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "scripts" / "governance" / "gov-task-done.sh"


def _sandbox(root: Path):
    """复制最小项目结构到沙箱。"""
    root.mkdir(parents=True, exist_ok=True)
    # 复制 scripts/ + AGENTS.md + .language + 必要的根文件
    shutil.copytree(ROOT / "scripts", root / "scripts", dirs_exist_ok=True)
    shutil.copy2(ROOT / "AGENTS.md", root / "AGENTS.md")
    for fname, content in [
        ("README.md", "# lightweigh\n\n## 快速开始\n\n```bash\nbuild/install.sh\n```\n\n## 相关文档\n\n- [索引](docs/index.md)\n"),
        ("LICENSE", "MIT License\n"),
        ("VERSION", "1.0.0\n"),
        ("STATUS.md", "# Status\n"),
        ("setup.sh", "#!/usr/bin/env bash\ntrue\n"),
        (".gitignore", "build/\n*.iml\n.gradle/\n"),
        (".language", "zh-CN\n"),
        (".editorconfig", "[*]\nend_of_line = lf\ncharset = utf-8\n"),
        (".cursorrules", "# rules\n"),
        (".clinerules", "# rules\n"),
    ]:
        (root / fname).write_text(content, encoding="utf-8")
    opencode_dir = root / ".opencode"
    opencode_dir.mkdir(exist_ok=True)
    (opencode_dir / "rules.md").write_text("# rules\n", encoding="utf-8")
    hooks_dir = root / "scripts" / "hooks"
    hooks_dir.mkdir(parents=True, exist_ok=True)
    for hook in ["pre-commit", "commit-msg", "pre-push"]:
        (hooks_dir / hook).write_text("#!/usr/bin/env bash\nexit 0\n", encoding="utf-8")
    docs_dir = root / "docs"
    docs_dir.mkdir(parents=True, exist_ok=True)
    (docs_dir / "index.md").write_text("# Docs Index\n\n## 指南\n\n- [概览](guides/index.md)\n", encoding="utf-8")
    (docs_dir / "README.md").write_text("# Docs\n", encoding="utf-8")  # placeholder
    tests_dir = root / "tests"
    tests_dir.mkdir(exist_ok=True)
    (tests_dir / "registry.yaml").write_text("---\nconfigs: []\n", encoding="utf-8")
    (tests_dir / "android-test-registry.yaml").write_text("---\n", encoding="utf-8")
    (root / ".worktrees").mkdir(exist_ok=True)
    (root / ".cache").mkdir(exist_ok=True)
    # Minimal git repo
    subprocess.run(["git", "init", "-q"], cwd=root, capture_output=True)
    subprocess.run(["git", "add", "-A"], cwd=root, capture_output=True)
    subprocess.run(["git", "-c", "user.email=t@t", "-c", "user.name=t", "commit", "-qm", "init"], cwd=root, capture_output=True)
    # Set hooksPath to satisfy gov-validate git hooks check
    subprocess.run(["git", "config", "core.hooksPath", str(root / "scripts" / "hooks")], cwd=root, capture_output=True)


def write_board(root: Path, tasks):
    """在临时根目录写入最小任务板。"""
    ai_dir = root / ".ai"
    ai_dir.mkdir(parents=True, exist_ok=True)
    (ai_dir / "tasks.json").write_text(
        json.dumps({"version": 1, "tasks": tasks}, ensure_ascii=False),
        encoding="utf-8",
    )
    copy_project = ROOT / ".ai" / "tasks-archive.json"
    if copy_project.exists():
        shutil.copy2(copy_project, ai_dir / "tasks-archive.json")
    _sandbox(root)


def make_task(task_id="T-001", files=None, commands=None, prototype=None):
    """构造最小任务对象。"""
    task = {
        "id": task_id,
        "description": "测试任务",
        "status": "pending",
        "target_files": files if files is not None else ["docs/README.md"],
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
        env={**os.environ, "GOV_DOD_SKIP": "1"},
    )


def touch(path: Path):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("// stub\n", encoding="utf-8")


def test_missing_target_file_fails(tmp_path):
    """目标文件缺失必须 FAIL 并列出缺失路径。"""
    write_board(tmp_path, [make_task(files=["docs/Missing.md"])])
    proc = run(tmp_path, "T-001")
    assert proc.returncode == 1, proc.stdout + proc.stderr
    assert "[FAIL]" in proc.stdout
    assert "Missing.md" in proc.stdout
    assert "总判定: FAIL" in proc.stdout


def test_verify_command_failure_fails(tmp_path):
    """任一 verify_command 非零退出即整体 FAIL。"""
    touch(tmp_path / "docs/README.md")
    write_board(tmp_path, [make_task(commands=["true", "exit 7"])])
    proc = run(tmp_path, "T-001")
    assert proc.returncode == 1, proc.stdout + proc.stderr
    assert "总判定: FAIL" in proc.stdout


def test_all_pass_returns_zero(tmp_path):
    """文件存在且命令全通过 -> 退出 0。"""
    touch(tmp_path / "docs/README.md")
    write_board(tmp_path, [make_task(commands=["true", "echo ok"])])
    proc = run(tmp_path, "T-001")
    assert proc.returncode == 0, proc.stdout + proc.stderr
    assert "总判定: PASS" in proc.stdout
    assert "[PASS]" in proc.stdout


def test_dry_run_does_not_execute(tmp_path):
    """--dry 只打印，不执行命令（touch marker 不得落地）。"""
    touch(tmp_path / "docs/README.md")
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
    touch(tmp_path / "docs/README.md")
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
