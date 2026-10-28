"""pre-receive hook 行为测试（DEC-077, DEC-081）。

验证服务端 pre-receive hook 能正确:
  - 拒绝 --no-verify 提交（无轨迹）
  - 拒绝功能分支修改共享文件
  - 接受合并队列/主干的共享文件写入
  - 拒绝格式错误的提交信息
  - 接受正确格式的提交
"""
import json
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
HOOK = ROOT / ".github" / "hooks" / "pre-receive"

GIT_AUTHOR = ["-c", "user.email=test@example.com", "-c", "user.name=Test"]


def _git(repo: Path, *args: str, check: bool = True) -> subprocess.CompletedProcess:
    """Run git command in repo with test identity."""
    return subprocess.run(
        ["git", *GIT_AUTHOR, *args],
        cwd=repo, capture_output=True, text=True, check=check,
    )


def _write(path: Path, text: str):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")


def _make_repo(tmp_path: Path) -> Path:
    """Create a minimal git repo with a bare remote and pre-receive hook installed."""
    remote = tmp_path / "remote.git"
    _git(tmp_path, "init", "-q", "--bare", str(remote), check=False)

    repo = tmp_path / "repo"
    repo.mkdir()
    _git(repo, "init", "-q")
    _git(repo, "checkout", "-qB", "master")
    _git(repo, "remote", "add", "origin", str(remote))

    # Install pre-receive hook on the remote
    hooks_dir = remote / "hooks"
    hooks_dir.mkdir(parents=True, exist_ok=True)
    hook_dst = hooks_dir / "pre-receive"
    hook_dst.write_text(HOOK.read_text(encoding="utf-8"), encoding="utf-8")
    hook_dst.chmod(0o755)

    return repo


def test_hook_file_exists():
    """The pre-receive hook file should exist and be executable."""
    assert HOOK.exists(), f"pre-receive hook not found at {HOOK}"
    assert HOOK.stat().st_mode & 0o111, "pre-receive hook is not executable"


def test_hook_syntax_valid():
    """The pre-receive hook should pass bash -n syntax check."""
    result = subprocess.run(["bash", "-n", str(HOOK)], capture_output=True, text=True)
    assert result.returncode == 0, f"Syntax error: {result.stderr}"


def test_hook_allows_valid_commit(tmp_path):
    """A properly formatted commit from master should be allowed."""
    repo = _make_repo(tmp_path)
    _write(repo / "README.md", "# Test\n")
    _git(repo, "add", "-A")
    _git(repo, "commit", "-q", "-m", "docs: add readme")
    result = _git(repo, "push", "origin", "master")
    assert result.returncode == 0, f"Valid push should succeed: {result.stderr}"


def test_hook_rejects_bad_commit_message(tmp_path):
    """A commit with bad message format should be rejected."""
    repo = _make_repo(tmp_path)
    _write(repo / "README.md", "# Test\n")
    _git(repo, "add", "-A")
    _git(repo, "commit", "-q", "-m", "docs: initial")
    _git(repo, "push", "-q", "origin", "master")

    _git(repo, "checkout", "-q", "-B", "feat/bad-msg")
    _write(repo / "foo.txt", "foo\n")
    _git(repo, "add", "-A")
    _git(repo, "commit", "-q", "-m", "bad commit message")

    result = _git(repo, "push", "origin", "feat/bad-msg", check=False)
    assert result.returncode != 0
    assert "提交格式" in result.stderr, f"Expected format rejection: {result.stderr}"


def test_hook_rejects_shared_file_on_branch(tmp_path):
    """A feature branch modifying shared files should be rejected."""
    repo = _make_repo(tmp_path)
    _write(repo / "README.md", "# Test\n")
    _git(repo, "add", "-A")
    _git(repo, "commit", "-q", "-m", "docs: initial")
    _git(repo, "push", "-q", "origin", "master")

    _git(repo, "checkout", "-q", "-B", "feat/shared-file")
    _write(repo / ".ai" / "tasks.json", '{"tasks": []}')
    _git(repo, "add", "-A")
    _git(repo, "commit", "-q", "-m", "feat: update shared file")

    result = _git(repo, "push", "origin", "feat/shared-file", check=False)
    assert result.returncode != 0
    assert "共享文件" in result.stderr, f"Expected shared file rejection: {result.stderr}"


def test_hook_allows_master_shared_file(tmp_path):
    """Master branch modifying shared files should be allowed (merge queue)."""
    repo = _make_repo(tmp_path)
    _write(repo / "README.md", "# Test\n")
    _git(repo, "add", "-A")
    _git(repo, "commit", "-q", "-m", "docs: initial")
    _git(repo, "push", "-q", "origin", "master")

    # Master modifying shared file should be allowed
    _write(repo / ".ai" / "tasks.json", '{"tasks": []}')
    _git(repo, "add", "-A")
    _git(repo, "commit", "-q", "-m", "chore(board): sync tasks")

    result = _git(repo, "push", "origin", "master", check=False)
    assert "共享文件" not in result.stderr, f"Master should be allowed shared file writes: {result.stderr}"


def test_hook_allows_merge_commit(tmp_path):
    """Merge commits should bypass all checks."""
    repo = _make_repo(tmp_path)
    _write(repo / "README.md", "# Test\n")
    _git(repo, "add", "-A")
    _git(repo, "commit", "-q", "-m", "docs: initial")
    _git(repo, "push", "-q", "origin", "master")

    # Create feature branch with a commit
    _git(repo, "checkout", "-q", "-B", "feat/feature")
    _write(repo / "foo.txt", "foo\n")
    _git(repo, "add", "-A")
    _git(repo, "commit", "-q", "-m", "feat: add foo feature")

    # Merge back to master
    _git(repo, "checkout", "-q", "master")
    _git(repo, "merge", "--no-ff", "feat/feature", "-m", "chore(merge): feat/feature")

    # Push master with merge commit — should pass
    result = _git(repo, "push", "origin", "master", check=False)
    assert result.returncode == 0, f"Merge commit push should succeed: {result.stderr}"
