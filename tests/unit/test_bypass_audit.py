"""--no-verify 审计（DEC-077）不变量测试。

保障能被"发现"而不是假装"不可绕过"：
  - 没有轨迹文件时不误报（首次使用）
  - 有轨迹且 tree 命中 → 通过
  - 轨迹缺失该 tree → block 模式拒绝
  - 合并提交豁免
"""
import subprocess
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
AUDIT = ROOT / "scripts" / "governance" / "gov-audit-bypass.sh"


def _git(repo, *args):
    return subprocess.run(
        ["git", "-c", "user.name=t", "-c", "user.email=t@example.com", *args],
        cwd=repo, capture_output=True, text=True, check=True,
    ).stdout.strip()


def _make_repo(tmp_path):
    repo = tmp_path
    _git(repo, "init", "-q")
    (repo / "a.txt").write_text("a\n", encoding="utf-8")
    _git(repo, "add", "a.txt")
    _git(repo, "commit", "-q", "-m", "feat(x): base")
    return repo, _git(repo, "rev-parse", "HEAD"), _git(repo, "rev-parse", "HEAD^{tree}")


def _audit(repo, *args):
    env = {**__import__("os").environ, "GOV_AUDIT_ROOT": str(repo)}
    return subprocess.run(["bash", str(AUDIT), *args], cwd=repo,
                          capture_output=True, text=True, env=env)


def test_no_trail_is_not_an_alarm(tmp_path):
    repo, sha, _tree = _make_repo(tmp_path)
    run = _audit(repo, "--commits", sha, "--block")
    assert run.returncode == 0
    assert "无钩子轨迹" in run.stdout


def test_trail_hit_passes(tmp_path):
    repo, sha, tree = _make_repo(tmp_path)
    cache = repo / ".cache"
    cache.mkdir()
    # Use the commit's own timestamp to avoid boundary timing issues in CI
    ct = _git(repo, "log", "-1", "--format=%ct", sha)
    (cache / "hook-trail.log").write_text(f"{tree} {ct}\n", encoding="utf-8")
    run = _audit(repo, "--commits", sha, "--block")
    assert run.returncode == 0, run.stdout
    assert "均有钩子轨迹" in run.stdout


def test_missing_trail_blocks_in_block_mode(tmp_path):
    repo, sha, _tree = _make_repo(tmp_path)
    cache = repo / ".cache"
    cache.mkdir()
    # 轨迹存在但只要有一个更早的条目，审计就会启动
    (cache / "hook-trail.log").write_text(f"deadbeef {int(time.time()) - 10}\n", encoding="utf-8")
    blocked = _audit(repo, "--commits", sha, "--block")
    warned = _audit(repo, "--commits", sha, "--warn")
    assert blocked.returncode == 1
    assert "疑似 --no-verify" in blocked.stdout
    assert warned.returncode == 0


def test_merge_commit_is_exempt(tmp_path):
    repo, _sha, tree = _make_repo(tmp_path)
    _git(repo, "checkout", "-q", "-b", "side")
    (repo / "b.txt").write_text("b\n", encoding="utf-8")
    _git(repo, "add", "b.txt")
    _git(repo, "commit", "-q", "-m", "feat(y): side")
    _git(repo, "checkout", "-q", "-")
    _git(repo, "merge", "-q", "--no-ff", "-m", "chore(merge): side", "side")
    merge_sha = _git(repo, "rev-parse", "HEAD")
    cache = repo / ".cache"
    cache.mkdir()
    (cache / "hook-trail.log").write_text(f"{tree} {int(time.time()) - 10}\n", encoding="utf-8")
    run = _audit(repo, "--commits", merge_sha, "--block")
    assert run.returncode == 0, run.stdout
