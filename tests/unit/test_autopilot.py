"""wf-autopilot.sh（DEC-066）行为测试：status 只读、JSON 可解析、worktree 审计正确。

autopilot 是"流程即程序"的唯一入口，它自己必须可信：
- status 不得写入任何文件（只读承诺）
- status --json 结构稳定（供其他自动化消费）
- 已合入 / 未合入 worktree 的判定必须准确（决定 advance 能否安全清理）
"""
import json
import subprocess
import sys
from pathlib import Path

ROOT_P = Path(__file__).resolve().parents[2]
SCRIPT = ROOT_P / "scripts" / "workflow" / "wf-autopilot.sh"


def _sh(script: Path, *args: str, root: Path | None = None, check: bool = False):
    env = None
    if root is not None:
        import os
        env = {**os.environ, "WF_AUTOPILOT_ROOT": str(root)}
    return subprocess.run(
        ["bash", str(script), *args],
        capture_output=True, text=True, env=env, check=check,
    )


def test_script_syntax_and_help():
    assert subprocess.run(["bash", "-n", str(SCRIPT)]).returncode == 0
    res = _sh(SCRIPT, "--help")
    assert res.returncode == 0
    assert "status" in res.stdout and "advance" in res.stdout


def test_unknown_subcommand_fails():
    res = _sh(SCRIPT, "nonsense")
    assert res.returncode != 0
    assert "未知子命令" in res.stderr


def test_start_without_task_id_fails():
    res = _sh(SCRIPT, "start")
    assert res.returncode != 0
    assert "用法" in res.stderr


def _git(root: Path, *args: str):
    subprocess.run(
        ["git", "-c", "user.email=t@t", "-c", "user.name=t", *args],
        cwd=root, capture_output=True, text=True, check=True,
    )


def _make_repo(tmp_path: Path) -> Path:
    root = tmp_path / "repo"
    root.mkdir()
    _git(root, "init", "-q")
    _git(root, "checkout", "-qB", "master")
    (root / "README.md").write_text("x\n", encoding="utf-8")
    _git(root, "add", "-A")
    _git(root, "commit", "-qm", "init")
    return root


def test_status_json_detects_worktree_states(tmp_path):
    root = _make_repo(tmp_path)

    # 已合入的分支 + worktree（应出现在 worktrees_merged）
    _git(root, "branch", "chore/done-one")
    done_wt = tmp_path / "wt-done"
    _git(root, "worktree", "add", "-q", str(done_wt), "chore/done-one")

    # 未合入的分支 + worktree（应出现在 worktrees_live）
    _git(root, "checkout", "-qb", "feat/wip")
    (root / "wip.txt").write_text("x\n", encoding="utf-8")
    _git(root, "add", "-A")
    _git(root, "commit", "-qm", "wip")
    _git(root, "checkout", "-q", "master")
    live_wt = tmp_path / "wt-live"
    _git(root, "worktree", "add", "-q", str(live_wt), "feat/wip")

    # 孤儿目录（不以 worktree 注册）
    (tmp_path / "repo-orphan").mkdir()

    res = _sh(SCRIPT, "status", "--json", root=root)
    assert res.returncode == 0, res.stderr
    data = json.loads(res.stdout)

    merged_branches = {w["branch"] for w in data["worktrees_merged"]}
    live_branches = {w["branch"] for w in data["worktrees_live"]}
    assert "chore/done-one" in merged_branches
    assert "feat/wip" in live_branches
    assert "chore/done-one" not in live_branches
    assert any("repo-orphan" in d for d in data["orphan_dirs"])


def test_status_is_read_only(tmp_path):
    root = _make_repo(tmp_path)
    before = subprocess.run(
        ["git", "status", "--porcelain"], cwd=root, capture_output=True, text=True
    ).stdout

    _sh(SCRIPT, "status", root=root)

    after = subprocess.run(
        ["git", "status", "--porcelain"], cwd=root, capture_output=True, text=True
    ).stdout
    assert before == after


def test_status_prints_next_step(tmp_path):
    root = _make_repo(tmp_path)
    res = _sh(SCRIPT, "status", root=root)
    assert res.returncode == 0, res.stderr
    assert "下一步" in res.stdout
