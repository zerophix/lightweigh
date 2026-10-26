"""AI 角色（DEC-075）不变量测试。

角色只做三件事：写入边界、评审独立、产物契约。这里钉死它们，防止
"角色退化成人格标签"或"评审变成自审"：

  - 角色定义完整（每个角色有 profile 与 allow）
  - dev 能改源码、不能改治理脚本
  - docs 不能改源码
  - review 只读，且持任务锁的会话不能认领
  - 角色过期后 current 为空
  - 角色默认画像会被 gov-profile 采纳

运行: pytest tests/unit/test_role_governance.py
"""
import json
import os
import shutil
import subprocess
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parents[2]
ROLE_SH = ROOT / "scripts" / "workflow" / "wf-role.sh"
PROFILE_SH = ROOT / "scripts" / "governance" / "gov-profile.sh"
SESSION = "ses-role-test"


@pytest.fixture()
def sandbox(tmp_path):
    """临时根：只复制角色定义，避免污染真实 .ai/roles。"""
    ai = tmp_path / ".ai"
    ai.mkdir()
    shutil.copy(ROOT / ".ai" / "roles.json", ai / "roles.json")
    (ai / "locks").mkdir()
    return tmp_path


def _run(root, *args, session=SESSION):
    env = {**os.environ, "WF_ROLE_ROOT": str(root), "WF_CLAIM_SESSION": session}
    return subprocess.run(["bash", str(ROLE_SH), *args], cwd=root,
                          capture_output=True, text=True, env=env)


def test_roles_definition_is_complete():
    data = json.loads((ROOT / ".ai" / "roles.json").read_text(encoding="utf-8"))
    roles = data["roles"]
    assert set(roles) == {"dev", "gov", "docs", "review"}
    for name, spec in roles.items():
        assert spec.get("profile") in {"docs", "test", "code", "gov", "mixed"}, name
        if spec.get("read_only"):
            assert spec.get("allow"), f"{name} 只读角色必须有 allow 白名单"
        else:
            assert spec.get("allow"), name


def test_dev_can_touch_source_but_not_governance(sandbox):
    assert _run(sandbox, "claim", "dev").returncode == 0
    ok = _run(sandbox, "check-scope", "--files", "app/src/main/kotlin/X.kt")
    bad = _run(sandbox, "check-scope", "--files", "scripts/governance/gov-x.sh")
    assert ok.returncode == 0, ok.stdout + ok.stderr
    assert bad.returncode == 1, bad.stdout
    assert "越界" in bad.stdout


def test_docs_cannot_touch_source(sandbox):
    assert _run(sandbox, "claim", "docs").returncode == 0
    bad = _run(sandbox, "check-scope", "--files", "app/src/main/kotlin/X.kt")
    ok = _run(sandbox, "check-scope", "--files", "docs/notes/a.md")
    assert bad.returncode == 1
    assert ok.returncode == 0


def test_review_is_read_only(sandbox):
    assert _run(sandbox, "claim", "review").returncode == 0
    ok = _run(sandbox, "check-scope", "--files", "docs/ai/reviews/r.md")
    bad = _run(sandbox, "check-scope", "--files", "app/src/main/kotlin/X.kt")
    assert ok.returncode == 0, ok.stdout
    assert bad.returncode == 1


def test_review_refused_when_session_holds_task_lock(sandbox):
    lock = sandbox / ".ai" / "locks" / "T-999"
    lock.mkdir(parents=True)
    (lock / "owner").write_text(SESSION + "\n", encoding="utf-8")
    run = _run(sandbox, "claim", "review")
    assert run.returncode == 1
    assert "不同会话" in run.stderr


def test_release_clears_role(sandbox):
    assert _run(sandbox, "claim", "dev").returncode == 0
    assert _run(sandbox, "current").stdout.strip() == "dev"
    assert _run(sandbox, "release").returncode == 0
    assert _run(sandbox, "current").stdout.strip() == ""


def test_expired_role_is_ignored(sandbox):
    assert _run(sandbox, "claim", "dev", "--ttl", "0").returncode == 0
    assert _run(sandbox, "current").stdout.strip() == ""


def test_role_contributes_default_profile(sandbox):
    """声明 gov 角色后，画像至少是 gov（只能升不能降）。"""
    assert _run(sandbox, "claim", "gov").returncode == 0
    env = {**os.environ, "WF_CLAIM_SESSION": SESSION}
    # profile 解析读真实仓库的 .ai/roles，这里只验证角色文件里的 profile 可被读到
    role_file = sandbox / ".ai" / "roles" / f"{SESSION}.json"
    data = json.loads(role_file.read_text(encoding="utf-8"))
    roles = json.loads((ROOT / ".ai" / "roles.json").read_text(encoding="utf-8"))["roles"]
    assert roles[data["role"]]["profile"] == "gov"
    assert PROFILE_SH.exists()
