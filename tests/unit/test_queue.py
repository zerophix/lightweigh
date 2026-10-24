"""wf-queue.sh（DEC-068）测试：批次合并队列的候选收集与安全边界。

队列是唯一能推送 master 的自动化，它的错误代价最高，因此至少钉死：
  - 语法/帮助/未知子命令
  - status 只收集"有本地 worktree"的候选
  - --dry 不创建 worktree、不推送
"""
import json
import os
import stat
import subprocess
from pathlib import Path

ROOT_P = Path(__file__).resolve().parents[2]
SCRIPT = ROOT_P / "scripts" / "workflow" / "wf-queue.sh"


def _git(root: Path, *args: str):
    subprocess.run(
        ["git", "-c", "user.email=t@t", "-c", "user.name=t", *args],
        cwd=root, capture_output=True, text=True, check=True)


def _fake_gh(tmp_path: Path, prs: list) -> Path:
    """假的 gh：只实现队列用到的两种查询，避免测试依赖网络与认证。"""
    bindir = tmp_path / "bin"
    bindir.mkdir(exist_ok=True)
    gh = bindir / "gh"
    gh.write_text(
        "#!/usr/bin/env python3\n"
        "import json, sys\n"
        f"PRS = {json.dumps(prs)}\n"
        "args = sys.argv[1:]\n"
        "if 'list' in args:\n"
        "    if '--head' in args:\n"
        "        br = args[args.index('--head') + 1]\n"
        "        hit = [p for p in PRS if p['headRefName'] == br]\n"
        "        print(hit[0]['number'] if hit else '')\n"
        "    else:\n"
        "        for p in PRS:\n"
        "            print(p['headRefName'])\n"
    )
    gh.chmod(0o755)
    return bindir


def _repo(tmp_path: Path) -> Path:
    root = tmp_path / "repo"
    root.mkdir()
    _git(root, "init", "-q")
    _git(root, "checkout", "-qB", "master")
    (root / "README.md").write_text("x\n")
    _git(root, "add", "-A")
    _git(root, "commit", "-qm", "init")
    # 队列需要 origin：建一个本地 bare 仓库当远端
    remote = tmp_path / "remote.git"
    subprocess.run(["git", "init", "-q", "--bare", str(remote)], check=True)
    _git(root, "remote", "add", "origin", str(remote))
    _git(root, "push", "-q", "-u", "origin", "master")
    return root


def _queue(root: Path, bindir: Path, *args: str):
    env = {**os.environ, "PATH": f"{bindir}:{os.environ['PATH']}"}
    return subprocess.run(["bash", str(root / "scripts/workflow/wf-queue.sh"), *args],
                          cwd=root, capture_output=True, text=True, env=env)


def _install_script(root: Path):
    dst = root / "scripts" / "workflow"
    dst.mkdir(parents=True, exist_ok=True)
    (dst / "wf-queue.sh").write_text(SCRIPT.read_text(encoding="utf-8"))
    (dst / "wf-queue.sh").chmod(0o755)


def test_syntax_and_help():
    assert subprocess.run(["bash", "-n", str(SCRIPT)]).returncode == 0
    res = subprocess.run(["bash", str(SCRIPT), "--help"], capture_output=True, text=True)
    assert res.returncode == 0
    assert "run" in res.stdout


def test_unknown_subcommand_fails():
    res = subprocess.run(["bash", str(SCRIPT), "bogus"], capture_output=True, text=True)
    assert res.returncode != 0


def test_status_only_counts_branches_with_worktree(tmp_path):
    root = _repo(tmp_path)
    _install_script(root)
    _git(root, "branch", "feat/with-wt")
    _git(root, "worktree", "add", "-q", str(tmp_path / "wt"), "feat/with-wt")
    bindir = _fake_gh(tmp_path, [
        {"number": 1, "headRefName": "feat/with-wt"},
        {"number": 2, "headRefName": "feat/no-wt"},
    ])

    res = _queue(root, bindir, "status")

    assert "共 1 个可进入队列" in res.stdout, res.stdout + res.stderr
    assert "feat/with-wt" in res.stdout


def test_dry_run_creates_no_worktree(tmp_path):
    root = _repo(tmp_path)
    _install_script(root)
    _git(root, "branch", "feat/with-wt")
    _git(root, "push", "-q", "-u", "origin", "feat/with-wt")
    _git(root, "worktree", "add", "-q", str(tmp_path / "wt"), "feat/with-wt")
    bindir = _fake_gh(tmp_path, [{"number": 1, "headRefName": "feat/with-wt"}])
    before = subprocess.run(["git", "worktree", "list"], cwd=root, capture_output=True, text=True).stdout

    res = _queue(root, bindir, "run", "--dry")

    after = subprocess.run(["git", "worktree", "list"], cwd=root, capture_output=True, text=True).stdout
    assert res.returncode == 0, res.stdout + res.stderr
    assert before == after
    assert "[dry]" in res.stdout
