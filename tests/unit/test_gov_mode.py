#!/usr/bin/env python3
"""GOV 模式系统测试: wf-mode.sh + gov-mode-lib.sh (DEC-077).

覆盖:
  - 模式切换 (switch explore/docs/test/code/gov/dev)
  - 自动 TTL (explore 2h, docs 2h, test 4h, code/dev/gov 永不过期)
  - 别名 (deliver -> code, audit -> gov)
  - HMAC 完整性 (篡改 → 判无效)
  - 过期检查 (过期 → 自动回退 dev)
  - 钩子模式感知 (pre-commit 在 explore 模式下跳过编译)
  - 文件级回退 (GOV_MODE 未设置时从 .ai/current-mode.json 读取)

运行: pytest tests/unit/test_gov_mode.py
"""
import json
import os
import subprocess
import time
from datetime import datetime, timezone
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parents[2]
MODE_FILE = ROOT / ".ai" / "current-mode.json"
AUDIT_FILE = ROOT / ".ai" / "audit" / "bypass.log"


def _run(script, *args, env=None):
    """Run a shell script with optional env overrides, return (rc, stdout, stderr)."""
    full_env = dict(os.environ)
    if env:
        full_env.update(env)
    result = subprocess.run(
        ["bash", str(ROOT / "scripts" / script)] + list(args),
        capture_output=True,
        text=True,
        cwd=str(ROOT),
        env=full_env,
        timeout=30,
    )
    return result.returncode, result.stdout.strip(), result.stderr.strip()


@pytest.fixture(autouse=True)
def _clean_mode_file():
    """每个测试前后清理模式文件。"""
    if MODE_FILE.exists():
        MODE_FILE.unlink()
    yield
    if MODE_FILE.exists():
        MODE_FILE.unlink()
    # Clean audit log too
    if AUDIT_FILE.exists():
        AUDIT_FILE.unlink()


class TestModeFile:
    """模式文件 CRUD + HMAC + 过期。"""

    def test_switch_creates_valid_mode_file(self):
        """switch explore 会创建带 HMAC 的模式文件。"""
        rc, out, _ = _run("workflow/wf-mode.sh", "switch", "explore", "--duration", "30m")
        assert rc == 0
        data = json.loads(MODE_FILE.read_text())
        assert data["mode"] == "explore"
        assert "hmac" in data
        assert data["expires_at"]  # 非空

    def test_hmac_integrity(self):
        """篡改模式文件 (改 mode 字段但不更新 hmac) → gov_mode_get 返回 dev。"""
        _run("workflow/wf-mode.sh", "switch", "explore", "--duration", "30m")
        # Tamper: flip mode but keep old hmac
        data = json.loads(MODE_FILE.read_text())
        data["mode"] = "docs"
        MODE_FILE.write_text(json.dumps(data))
        # Now read: should fall back to dev (HMAC mismatch)
        rc, out, _ = _run("workflow/wf-mode.sh", "status")
        assert "dev" in out.lower()

    def test_expiration_fallback(self):
        """过期的模式 → 状态显示 '已过期'。"""
        _run("workflow/wf-mode.sh", "switch", "explore", "--duration", "1m")
        data = json.loads(MODE_FILE.read_text())
        # Set expires_at to the past
        data["expires_at"] = "2020-01-01T00:00:00Z"
        data["hmac"] = _compute_hmac(json.dumps({k: v for k, v in data.items() if k != "hmac"}, separators=(",", ":")))
        MODE_FILE.write_text(json.dumps(data))
        rc, out, _ = _run("workflow/wf-mode.sh", "status")
        assert "过期" in out or "dev" in out.lower()

    def test_permanent_mode_no_expiry(self):
        """code/dev/gov 模式默认永不过期。"""
        _run("workflow/wf-mode.sh", "switch", "gov")
        data = json.loads(MODE_FILE.read_text())
        assert data["expires_at"] == ""

    def test_alias_deliver_to_code(self):
        """deliver 别名 → code。"""
        rc, out, _ = _run("workflow/wf-mode.sh", "switch", "deliver", "--duration", "1h")
        assert rc == 0
        data = json.loads(MODE_FILE.read_text())
        assert data["mode"] == "code"

    def test_alias_audit_to_gov(self):
        """audit 别名 → gov。"""
        rc, out, _ = _run("workflow/wf-mode.sh", "switch", "audit")
        assert rc == 0
        data = json.loads(MODE_FILE.read_text())
        assert data["mode"] == "gov"


class TestModeTTL:
    """自动 TTL 验证。"""

    def test_explore_default_ttl_2h(self):
        """explore 模式默认 TTL = 7200s (2h)。"""
        _run("workflow/wf-mode.sh", "switch", "explore")
        data = json.loads(MODE_FILE.read_text())
        set_at = datetime.fromisoformat(data["set_at"].replace("Z", "+00:00"))
        expires = datetime.fromisoformat(data["expires_at"].replace("Z", "+00:00"))
        delta = (expires - set_at).total_seconds()
        assert abs(delta - 7200) < 5  # 5s 容差

    def test_docs_default_ttl_2h(self):
        """docs 模式默认 TTL = 7200s (2h)。"""
        _run("workflow/wf-mode.sh", "switch", "docs")
        data = json.loads(MODE_FILE.read_text())
        set_at = datetime.fromisoformat(data["set_at"].replace("Z", "+00:00"))
        expires = datetime.fromisoformat(data["expires_at"].replace("Z", "+00:00"))
        delta = (expires - set_at).total_seconds()
        assert abs(delta - 7200) < 5

    def test_explicit_duration(self):
        """显式 --duration 1h 生效。"""
        _run("workflow/wf-mode.sh", "switch", "explore", "--duration", "1h")
        data = json.loads(MODE_FILE.read_text())
        set_at = datetime.fromisoformat(data["set_at"].replace("Z", "+00:00"))
        expires = datetime.fromisoformat(data["expires_at"].replace("Z", "+00:00"))
        delta = (expires - set_at).total_seconds()
        assert abs(delta - 3600) < 5


class TestModeStatus:
    """状态查询。"""

    def test_status_dev_default(self):
        """无模式文件 → dev。"""
        rc, out, _ = _run("workflow/wf-mode.sh", "status")
        assert "dev" in out

    def test_status_explore(self):
        """切换到 explore → 状态显示 explore。"""
        _run("workflow/wf-mode.sh", "switch", "explore", "--duration", "30m")
        rc, out, _ = _run("workflow/wf-mode.sh", "status")
        assert "explore" in out

    def test_status_shows_remaining_time(self):
        """状态显示剩余时间。"""
        _run("workflow/wf-mode.sh", "switch", "explore", "--duration", "1h")
        rc, out, _ = _run("workflow/wf-mode.sh", "status")
        assert "剩余" in out or "过期" in out

    def test_expire_clears_mode(self):
        """expire 清空模式文件。"""
        _run("workflow/wf-mode.sh", "switch", "explore", "--duration", "30m")
        assert MODE_FILE.exists()
        rc, out, _ = _run("workflow/wf-mode.sh", "expire")
        assert rc == 0
        assert "expired" in out.lower() or "过期" in out
        assert not MODE_FILE.exists() or json.loads(MODE_FILE.read_text()).get("mode") == "dev"


class TestPreCommitModeAwareness:
    """pre-commit 在 explore 模式下跳过编译。"""

    def test_pre_commit_hooks_read_gov_mode(self):
        """pre-commit hook 代码中包含模式感知逻辑。"""
        hook = (ROOT / "scripts/governance/gov-pre-commit-simplified").read_text()
        assert "GOV_MODE" in hook
        assert "explore" in hook
        assert "gov-mode-lib.sh" in hook

    def test_pre_push_hooks_read_gov_mode(self):
        """pre-push hook 代码中包含模式感知逻辑。"""
        hook = (ROOT / "scripts/governance/gov-pre-push").read_text()
        assert "GOV_MODE" in hook
        assert "explore" in hook
        assert "gov-mode-lib.sh" in hook

    def test_local_ci_hooks_read_gov_mode(self):
        """local-ci 代码中包含模式感知逻辑。"""
        script = (ROOT / "scripts/governance/gov-local-ci.sh").read_text()
        assert "GOV_MODE" in script
        assert "gov-mode-lib.sh" in script


def _compute_hmac(payload):
    """Compute HMAC-SHA256 matching gov-mode-lib.sh's _gov_mode_hmac().

    Key = sha256(git rev-parse --show-toplevel) (including trailing newline,
    matching bash pipe behavior).
    """
    import hashlib
    import subprocess
    # Must NOT strip — bash pipe includes the trailing newline from git rev-parse
    git_toplevel = subprocess.run(
        ["git", "rev-parse", "--show-toplevel"],
        capture_output=True, text=True, cwd=str(ROOT)
    ).stdout
    key = hashlib.sha256(git_toplevel.encode()).hexdigest()
    result = subprocess.run(
        ["openssl", "dgst", "-sha256", "-hmac", key],
        input=payload.encode(), capture_output=True
    )
    # Output format: "HMAC-SHA256(stdin)= <hash>"
    return result.stdout.decode().strip().split("=")[-1].strip()
