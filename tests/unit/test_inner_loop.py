"""环 0/1 解耦不变量测试（DEC-072）。

钉死四件事，防止"快层又被塞回慢层"：

  - gov-validate 默认不跑 pytest（否则一次静态校验又要 67 秒）
  - GOV_VALIDATE_WITH_TESTS=1 才是显式全量
  - gov-code-check 的增量模式只扫给定文件，且空集合是合法结果
  - pre-commit 与 local-ci 按新分工接线（提交查暂存区 / 批次跑 pytest）

运行: pytest tests/unit/test_inner_loop.py
"""
import contextlib
import importlib.util
import io
import subprocess
import sys
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parents[2]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from scripts.governance.modules import gov_validate_tests as tests_mod


@pytest.fixture(autouse=True)
def _no_env(monkeypatch):
    monkeypatch.delenv("GOV_VALIDATE_WITH_TESTS", raising=False)
    monkeypatch.delenv("GOV_VALIDATE_QUICK", raising=False)
    monkeypatch.setattr(tests_mod, "walk_test_files", lambda: None)


def test_validate_skips_pytest_by_default(monkeypatch):
    called = []
    monkeypatch.setattr(tests_mod, "check_pytest", lambda: called.append(1))
    buf = io.StringIO()
    with contextlib.redirect_stdout(buf):
        tests_mod.check_all()
    assert called == []
    assert "默认不跑 pytest" in buf.getvalue()


def test_validate_runs_pytest_only_when_requested(monkeypatch):
    called = []
    monkeypatch.setattr(tests_mod, "check_pytest", lambda: called.append(1))
    monkeypatch.setenv("GOV_VALIDATE_WITH_TESTS", "1")
    with contextlib.redirect_stdout(io.StringIO()):
        tests_mod.check_all()
    assert called == [1]


def test_quick_env_still_wins(monkeypatch):
    called = []
    monkeypatch.setattr(tests_mod, "check_pytest", lambda: called.append(1))
    monkeypatch.setenv("GOV_VALIDATE_WITH_TESTS", "1")
    monkeypatch.setenv("GOV_VALIDATE_QUICK", "1")
    with contextlib.redirect_stdout(io.StringIO()):
        tests_mod.check_all()
    assert called == []


def _load_code_check():
    path = ROOT / "scripts" / "governance" / "gov-code-check.py"
    spec = importlib.util.spec_from_file_location("gov_code_check_under_test", path)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


def test_code_check_paths_scope_narrows_scan():
    mod = _load_code_check()
    target = ROOT / "app" / "src" / "main" / "kotlin" / "com" / "xcwk" / "scalehub" / "feature" / "ai" / "AiScreen.kt"
    assert mod._parse_scope(["--paths", str(target)]) is True
    files = mod._source_files()
    assert files, "增量模式应命中给定文件"
    assert all(f.resolve() == target.resolve() for f in files)


def test_code_check_empty_scope_is_legal():
    """暂存区只有文档时，增量模式必须返回空集合而不是退出 2（配置错误）。"""
    mod = _load_code_check()
    assert mod._parse_scope(["--paths", str(ROOT / "docs" / "index.md")]) is True
    assert mod._source_files() == []


def test_pre_commit_uses_staged_scope():
    hook = (ROOT / "scripts" / "governance" / "gov-pre-commit-simplified").read_text(encoding="utf-8")
    assert "GOV_CODE_CHECK_STAGED=1" in hook


def test_local_ci_runs_pytest_as_separate_step():
    script = (ROOT / "scripts" / "governance" / "gov-local-ci.sh").read_text(encoding="utf-8")
    assert 'run_step "pytest tests/unit"' in script


def test_affected_emits_machine_readable_summary():
    proc = subprocess.run(
        ["bash", "scripts/governance/gov-affected.sh", "--json"],
        cwd=ROOT, capture_output=True, text=True,
    )
    lines = [ln for ln in proc.stdout.splitlines() if ln.strip()]
    assert lines, proc.stderr
    summary = lines[-1]
    assert summary.startswith("{") and '"touched"' in summary and '"test_files"' in summary
