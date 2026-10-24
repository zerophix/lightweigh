#!/usr/bin/env python3
"""GOV-ATEST 规则负向测试: 故意制造违规, 断言 Android 测试治理规则确实 FAIL。

目的: 证明 GOV-ATEST-001~005 真实生效（治理设计约束 Q3 可验证性）。
没有负向测试的规则属于 hope-ware —— 规则被静默删除时无人会发现。

运行: 由 gov-validate.sh 的 GOV-TEST-005 自动执行, 或手动 pytest tests/unit/。
"""
import contextlib
import io
import sys
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parents[2]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from scripts.governance.modules import gov_validate_core as core
from scripts.governance.modules import gov_validate_android_tests as atest


def _clean_errors_guard():
    """快照-恢复语义 (DEC-007): 只隔离本测试产生的错误, 不吞宿主进程累积的现场。"""
    saved = list(core.errors)
    core.errors.clear()
    yield
    core.errors.clear()
    core.errors.extend(saved)


@pytest.fixture(autouse=True)
def _clean_errors():
    yield from _clean_errors_guard()


def _check(tmp_path: Path, monkeypatch) -> list:
    """在临时目录上运行 Android 测试治理, 返回错误列表（不触碰真实仓库文件）。"""
    monkeypatch.setattr(atest, "ROOT", tmp_path)
    monkeypatch.setattr(atest, "GOV_CONFIG", tmp_path / "scripts" / "governance" / "gov-config.sh")
    monkeypatch.setattr(atest, "REGISTRY_PATH", tmp_path / "tests" / "android-test-registry.yaml")
    monkeypatch.setattr(core, "ROOT", tmp_path)
    buf = io.StringIO()
    with contextlib.redirect_stdout(buf):
        atest.check_all()
    return list(core.errors)


def _has(errors, rule_id: str) -> bool:
    return any(rule_id in e for e in errors)


def _make_config(tmp_path: Path):
    cfg = tmp_path / "scripts" / "governance" / "gov-config.sh"
    cfg.parent.mkdir(parents=True)
    cfg.write_text('SRC_DIRS="domain application infrastructure app"\n', encoding="utf-8")


def _make_test(tmp_path: Path, module: str, rel: str, body: str = ""):
    f = tmp_path / module / "src" / "test" / rel
    f.parent.mkdir(parents=True)
    f.write_text(body, encoding="utf-8")


def _make_registry(tmp_path: Path, paths: list):
    r = tmp_path / "tests" / "android-test-registry.yaml"
    r.parent.mkdir(parents=True)
    lines = ["commands:"]
    for p in paths:
        lines.append(f'- description: test\n  path: {p}\n  status: stable\n  type: gradle-test')
    r.write_text("\n".join(lines), encoding="utf-8")


def test_clean_tree_produces_no_errors(tmp_path, monkeypatch):
    """空白对照: 配置与空注册表存在、无 src/test 时不应触发任何规则（防误报）。"""
    _make_config(tmp_path)
    _make_registry(tmp_path, [])
    assert _check(tmp_path, monkeypatch) == []


def test_atest002_snake_case_filename(tmp_path, monkeypatch):
    """GOV-ATEST-002: snake_case 测试名必须 FAIL（Android 用 PascalCase）。"""
    _make_config(tmp_path)
    _make_test(tmp_path, "domain", "com/xcwk/scalehub/test_measure_record.kt")
    errors = _check(tmp_path, monkeypatch)
    assert _has(errors, "GOV-ATEST-002"), errors


def test_atest002_version_suffix(tmp_path, monkeypatch):
    """GOV-ATEST-002: 版本号后缀文件名必须 FAIL。"""
    _make_config(tmp_path)
    _make_test(tmp_path, "domain", "com/xcwk/scalehub/MeasureRecordTest2.kt")
    errors = _check(tmp_path, monkeypatch)
    assert _has(errors, "GOV-ATEST-002"), errors


def test_atest001_non_ascii_filename(tmp_path, monkeypatch):
    """GOV-ATEST-001: 非 ASCII 文件名必须 FAIL。"""
    _make_config(tmp_path)
    _make_test(tmp_path, "domain", "com/xcwk/scalehub/测试Test.kt")
    errors = _check(tmp_path, monkeypatch)
    assert _has(errors, "GOV-ATEST-001"), errors


def test_atest004_emoji_in_source(tmp_path, monkeypatch):
    """GOV-ATEST-004: Kotlin 测试内含 emoji 必须 FAIL。"""
    _make_config(tmp_path)
    _make_test(tmp_path, "domain", "com/xcwk/scalehub/MeasureRecordTest.kt",
               "// 键帽测试 🏷 必须失败\n")
    errors = _check(tmp_path, monkeypatch)
    assert _has(errors, "GOV-ATEST-004"), errors


def test_atest005_unregistered_test(tmp_path, monkeypatch):
    """GOV-ATEST-005: 磁盘有 Test 文件但注册表没有必须 FAIL。"""
    _make_config(tmp_path)
    _make_test(tmp_path, "domain", "com/xcwk/scalehub/MeasureRecordTest.kt")
    _make_registry(tmp_path, [])
    errors = _check(tmp_path, monkeypatch)
    assert _has(errors, "GOV-ATEST-005"), errors


def test_atest005_feature_branch_downgrades_to_warn(tmp_path, monkeypatch):
    """DEC-065: 特性分支未注册降级 WARN（功能分支不得写共享注册表）。"""
    import subprocess
    _make_config(tmp_path)
    _make_test(tmp_path, "app", "com/x/SampleTest.kt")
    _make_registry(tmp_path, [])
    git = ["git", "-c", "user.email=t@t", "-c", "user.name=t"]
    subprocess.run([*git, "init", "-q"], cwd=tmp_path, check=True)
    subprocess.run([*git, "add", "-A"], cwd=tmp_path, check=True)
    subprocess.run([*git, "commit", "-qm", "b"], cwd=tmp_path, check=True)
    subprocess.run(["git", "checkout", "-qb", "feat/x"], cwd=tmp_path, check=True)

    errors = _check(tmp_path, monkeypatch)

    assert not _has(errors, "GOV-ATEST-005"), errors


def test_atest005_zombie_registry(tmp_path, monkeypatch):
    """GOV-ATEST-005: 注册表登记但磁盘不存在的僵尸条目必须 FAIL。"""
    _make_config(tmp_path)
    _make_registry(tmp_path, ["domain/src/test/com/xcwk/scalehub/GoneTest.kt"])
    errors = _check(tmp_path, monkeypatch)
    assert _has(errors, "GOV-ATEST-005"), errors


def test_fixture_suffix_allowed(tmp_path, monkeypatch):
    """Fixture 后缀应放行（RuleViolationFixture.kt 是真实夹具）, 不触发命名违规。"""
    _make_config(tmp_path)
    _make_test(tmp_path, "app", "com/xcwk/scalehub/trigger/ui/RuleViolationFixture.kt")
    errors = _check(tmp_path, monkeypatch)
    assert not _has(errors, "GOV-ATEST-002"), errors


def test_legitimate_test_passes(tmp_path, monkeypatch):
    """合法 PascalCase+Test 文件且已注册应通过, 不触发任何规则。"""
    _make_config(tmp_path)
    p = "app/src/test/kotlin/com/xcwk/scalehub/ArchitectureTest.kt"
    _make_test(tmp_path, "app", "kotlin/com/xcwk/scalehub/ArchitectureTest.kt")
    _make_registry(tmp_path, [p])
    errors = _check(tmp_path, monkeypatch)
    assert errors == [], errors