"""wf-role.sh 行为测试（DEC-075）：claim/current/show/release/list/check-scope/verify-review。

不变点的不变量测试在 test_role_governance.py；本文件聚焦接口行为与错误处理。
"""
import json
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "scripts" / "workflow" / "wf-role.sh"
ROLES_FILE = ROOT / ".ai" / "roles.json"


def _sh(*args, env=None):
    e = {**__import__("os").environ}
    if env:
        e.update(env)
    return subprocess.run(
        ["bash", str(SCRIPT), *args],
        cwd=ROOT, capture_output=True, text=True, env=e,
    )


def _sandbox(tmp_path: Path, session: str = "ses-test") -> dict:
    """Create a sandbox role dir with roles.json copied."""
    ai_dir = tmp_path / ".ai"
    ai_dir.mkdir(parents=True, exist_ok=True)
    shutil_copy = __import__("shutil").copy
    shutil_copy(ROLES_FILE, ai_dir / "roles.json")
    (ai_dir / "roles").mkdir(exist_ok=True)
    return {"WF_ROLE_ROOT": str(tmp_path), "WF_CLAIM_SESSION": session}


def test_claim_valid_role(tmp_path):
    """claim dev should write role file and output confirmation."""
    env = _sandbox(tmp_path)
    run = _sh("claim", "dev", "--task", "T-999", env=env)
    assert run.returncode == 0, run.stderr
    assert "角色已绑定: dev" in run.stdout
    assert "T-999" in run.stdout
    role_file = tmp_path / ".ai" / "roles" / "ses-test.json"
    assert role_file.exists(), f"Role file not created: {role_file}"
    data = json.loads(role_file.read_text(encoding="utf-8"))
    assert data["role"] == "dev"
    assert data["task"] == "T-999"


def test_claim_invalid_role_rejected():
    """claim with unknown role should fail."""
    env = _sandbox(Path("/tmp/nonexist-test-001"))
    run = _sh("claim", "wizard", env=env)
    assert run.returncode != 0
    assert "未知角色" in run.stderr


def test_claim_review_with_task_lock_rejected(tmp_path):
    """review role cannot claim if session already holds a task lock."""
    import os
    env = _sandbox(tmp_path)
    # Create a mock task lock (simulating wf-claim.sh)
    lock_dir = tmp_path / ".ai" / "locks" / "T-999"
    lock_dir.mkdir(parents=True)
    (lock_dir / "owner").write_text("ses-test\n", encoding="utf-8")
    (lock_dir / "profile").write_text("mixed\n", encoding="utf-8")
    # Claim review — should be rejected because session holds a task lock
    run = _sh("claim", "review", env=env)
    assert run.returncode != 0
    assert "review" in run.stderr or "task lock" in run.stderr


def test_current_returns_role(tmp_path):
    """current should output the claimed role."""
    env = _sandbox(tmp_path)
    _sh("claim", "dev", env=env)
    run = _sh("current", env=env)
    assert run.returncode == 0
    assert run.stdout.strip() == "dev"


def test_current_empty_without_claim(tmp_path):
    """current should output empty string when no role claimed."""
    env = _sandbox(tmp_path)
    run = _sh("current", env=env)
    assert run.returncode == 0
    assert run.stdout.strip() == ""


def test_release_clears_role(tmp_path):
    """release should delete the role file."""
    env = _sandbox(tmp_path)
    _sh("claim", "dev", env=env)
    run = _sh("release", env=env)
    assert run.returncode == 0
    assert "已释放" in run.stdout
    role_file = tmp_path / ".ai" / "roles" / "ses-test.json"
    assert not role_file.exists()


def test_list_shows_all_roles():
    """list should show all 5 roles."""
    run = _sh("list")
    assert run.returncode == 0
    for role in ("dev", "test", "docs", "gov", "review"):
        assert role in run.stdout


def test_show_with_no_role(tmp_path):
    """show should report no role when unclaimed."""
    env = _sandbox(tmp_path)
    run = _sh("show", env=env)
    assert run.returncode == 0
    assert "未声明" in run.stdout


def test_show_with_claimed_role(tmp_path):
    """show should display role details after claim."""
    env = _sandbox(tmp_path)
    _sh("claim", "dev", env=env)
    run = _sh("show", env=env)
    assert run.returncode == 0
    assert "dev" in run.stdout
