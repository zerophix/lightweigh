"""worktree 生命周期策略（DEC-069）测试。

事故背景：一天之内累积到 12 个 worktree，没有任何机制阻止继续创建。
本文件钉死三条硬规则：
  1. 任务 worktree 数量上限（默认 3），超过必须清理
  2. --force 才能临时突破
  3. 队列候选目录必须在退出时删除（trap），失败/被 kill 也不留垃圾
"""
import os
import re
import subprocess
from pathlib import Path

ROOT_P = Path(__file__).resolve().parents[2]
SES_WORKTREE = ROOT_P / "scripts" / "session" / "ses-worktree.sh"
QUEUE = ROOT_P / "scripts" / "workflow" / "wf-queue.sh"


def _git(root: Path, *args: str):
    subprocess.run(
        ["git", "-c", "user.email=t@t", "-c", "user.name=t", *args],
        cwd=root, capture_output=True, text=True, check=True)


def _repo(tmp_path: Path) -> Path:
    root = tmp_path / "repo"
    root.mkdir()
    _git(root, "init", "-q")
    _git(root, "checkout", "-qB", "master")
    (root / "README.md").write_text("x\n")
    (root / ".ai").mkdir()
    (root / ".ai" / "current-session.json").write_text('{"session_id": "ses-test"}')
    (root / ".cache").mkdir()
    _git(root, "add", "-A")
    _git(root, "commit", "-qm", "init")
    return root


def _install(root: Path, src: Path, rel: str):
    dst = root / rel
    dst.parent.mkdir(parents=True, exist_ok=True)
    dst.write_text(src.read_text(encoding="utf-8"), encoding="utf-8")
    dst.chmod(0o755)


def _run(root: Path, *args: str, env=None):
    e = {**os.environ, **(env or {})}
    return subprocess.run(["bash", str(root / "scripts/session/ses-worktree.sh"), *args],
                          cwd=root, capture_output=True, text=True, env=e)


def test_blocks_creating_beyond_limit(tmp_path):
    root = _repo(tmp_path)
    _install(root, SES_WORKTREE, "scripts/session/ses-worktree.sh")
    for i in range(3):
        r = _run(root, "add", f"feat/task-{i}", env={"WF_WORKTREE_LIMIT": "3"})
        assert r.returncode == 0, r.stdout + r.stderr

    r = _run(root, "add", "feat/task-over", env={"WF_WORKTREE_LIMIT": "3"})

    assert r.returncode != 0
    assert "拒绝继续创建" in (r.stdout + r.stderr)


def test_force_overrides_limit(tmp_path):
    root = _repo(tmp_path)
    _install(root, SES_WORKTREE, "scripts/session/ses-worktree.sh")
    for i in range(3):
        _run(root, "add", f"feat/task-{i}", env={"WF_WORKTREE_LIMIT": "3"})

    r = _run(root, "add", "feat/task-over", "--force", env={"WF_WORKTREE_LIMIT": "3"})

    assert r.returncode == 0, r.stdout + r.stderr


def test_registers_worktree_metadata(tmp_path):
    root = _repo(tmp_path)
    _install(root, SES_WORKTREE, "scripts/session/ses-worktree.sh")

    _run(root, "add", "feat/task-a", env={"WF_WORKTREE_LIMIT": "3"})

    log = root / ".cache" / "worktrees.jsonl"
    assert log.is_file()
    assert "feat/task-a" in log.read_text(encoding="utf-8")


def test_queue_has_trap_cleanup():
    """队列候选必须由 trap 兜底删除（被 kill 也不留目录）。"""
    text = QUEUE.read_text(encoding="utf-8")
    assert re.search(r"trap\s+cleanup_candidate\s+EXIT", text), "缺少 EXIT trap"
    assert "remove --force \"$candidate\"" in text or 'remove --force "$candidate"' in text
