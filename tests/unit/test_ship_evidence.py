"""ship.sh 证据自动入提交测试（DEC-078 + DEC-080）。

验证: ship.sh 在 commit 前自动暂存 .ai/evidence/ 中 ok=true 的证据文件，
    不把 ok=false 的失败证据带进分支。
"""
import json
import shutil
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SHIP = ROOT / "scripts" / "dev" / "ship.sh"
SHIP_SRC = SHIP.read_text(encoding="utf-8")


def _git(repo: Path, *args: str, check: bool = True) -> subprocess.CompletedProcess:
    return subprocess.run(
        ["git", "-c", "user.email=t@t", "-c", "user.name=t", *args],
        cwd=repo, capture_output=True, text=True, check=check)


def _write(path: Path, text: str, mode: int = 0o644):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")
    path.chmod(mode)


def _make_repo(tmp_path: Path) -> Path:
    """Create a minimal git repo with ship.sh and .ai/evidence/."""
    repo = tmp_path / "repo"
    repo.mkdir()
    _git(repo, "init", "-q")
    _git(repo, "checkout", "-qB", "master")

    # ship.sh needs scripts/dev/ subdir
    _write(repo / "scripts" / "dev" / "ship.sh", SHIP_SRC, 0o755)
    # wf-run.sh is optional (guarded by -x check)
    # gh is optional (guarded by command -v check)

    return repo


def _make_evidence(repo: Path, tid: str, ok: bool, sha: str = "abc123"):
    ev_dir = repo / ".ai" / "evidence"
    ev_dir.mkdir(parents=True, exist_ok=True)
    data = {
        "task_id": tid,
        "ok": ok,
        "git_sha": sha,
        "checks": [],
        "created_at": "2026-10-27T00:00:00Z",
        "by_session": "test",
    }
    (ev_dir / f"{tid}.json").write_text(json.dumps(data, indent=2), encoding="utf-8")


def test_ship_auto_adds_ok_evidence(tmp_path):
    """ship.sh should auto-stage ok=true evidence files."""
    repo = _make_repo(tmp_path)
    _write(repo / "a.txt", "hello\n")
    _git(repo, "add", "-A")

    # Create evidence: one ok=True, one ok=False
    _make_evidence(repo, "T-100", ok=True)
    _make_evidence(repo, "T-101", ok=False)

    # Run ship.sh with --staged --no-push
    run = subprocess.run(
        ["bash", str(repo / "scripts/dev/ship.sh"),
         "-m", "feat: test evidence",
         "--staged", "--no-push"],
        cwd=repo, capture_output=True, text=True,
    )

    assert run.returncode == 0, f"ship.sh failed: {run.stderr}"
    # The commit should include T-100.json (ok=True) but NOT T-101.json (ok=False)
    committed = _git(repo, "log", "--format=%B", "-1").stdout
    assert "test evidence" in committed
    files = _git(repo, "show", "--name-only", "--format=", "HEAD").stdout
    assert "T-100.json" in files, f"T-100.json (ok=True) should be committed: {files}"
    assert "T-101.json" not in files, f"T-101.json (ok=False) should NOT be committed: {files}"
    assert "a.txt" in files


def test_ship_skips_evidence_when_none_exist(tmp_path):
    """ship.sh should work normally when no evidence files exist."""
    repo = _make_repo(tmp_path)
    _write(repo / "b.txt", "world\n")
    _git(repo, "add", "-A")

    run = subprocess.run(
        ["bash", str(repo / "scripts/dev/ship.sh"),
         "-m", "feat: no evidence",
         "--staged", "--no-push"],
        cwd=repo, capture_output=True, text=True,
    )

    assert run.returncode == 0, f"ship.sh failed: {run.stderr}"
    files = _git(repo, "show", "--name-only", "--format=", "HEAD").stdout
    assert "b.txt" in files


def test_ship_with_path_args_includes_evidence(tmp_path):
    """ship.sh with explicit path args should still auto-add evidence."""
    repo = _make_repo(tmp_path)
    _write(repo / "c.txt", "test\n")
    _make_evidence(repo, "T-102", ok=True)

    run = subprocess.run(
        ["bash", str(repo / "scripts/dev/ship.sh"),
         "-m", "feat: path args",
         "--no-push", "c.txt"],
        cwd=repo, capture_output=True, text=True,
    )

    assert run.returncode == 0, f"ship.sh failed: {run.stderr}"
    files = _git(repo, "show", "--name-only", "--format=", "HEAD").stdout
    assert "c.txt" in files, f"c.txt should be committed: {files}"
    assert "T-102.json" in files, f"T-102.json should be auto-added: {files}"
