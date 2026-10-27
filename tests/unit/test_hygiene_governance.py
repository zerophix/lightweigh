#!/usr/bin/env python3
"""GOV-HYG-001 负向测试: 证明「禁止备份/快照残留文件」真的会 FAIL。

背景（2026-10-27）：4 个测试文件被改名成 `.bak`，31 + 9 个测试随之静默失效，
而 `GOV-ATEST-003` 仍报 `[PASS] 无版本/备份命名`——因为它的候选集只收 `.kt`/`.java`，
`FooTest.kt.bak` 进不了列表。这条规则就是为了补上那个执行者。

没有负向测试的规则属于 hope-ware：规则被静默删除时无人会发现。
"""
import sys
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parents[2]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from scripts.governance.modules import gov_validate_hygiene as hyg


@pytest.fixture(autouse=True)
def _isolate_errors():
    """快照-恢复语义（DEC-007）：只隔离本测试产生的错误。"""
    from scripts.governance.modules import gov_validate_core as core

    saved = list(core.errors)
    core.errors.clear()
    yield
    core.errors.clear()
    core.errors.extend(saved)


def _mk(tmp_path: Path, rel: str) -> Path:
    p = tmp_path / rel
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text("x\n", encoding="utf-8")
    return p


def test_bak_file_is_detected(tmp_path: Path):
    """核心场景：编辑器把测试改名成 .bak，必须被抓到。"""
    _mk(tmp_path, "app/src/test/kotlin/FooTest.kt.bak")
    hits = hyg.scan(tmp_path)
    assert len(hits) == 1, hits
    assert hits[0].startswith("app/src/test/kotlin/FooTest.kt.bak")
    assert ".bak" in hits[0]


def test_bak_variants_are_detected(tmp_path: Path):
    """`.bak` 家族的各种编号写法都要覆盖，否则换个后缀就能绕过。"""
    for name in ("a.kt.bak", "b.kt.bak2", "c.kt.bak.1", "d.backup"):
        _mk(tmp_path, f"src/{name}")
    hits = hyg.scan(tmp_path)
    assert len(hits) == 4, hits


def test_other_backup_artifacts_are_detected(tmp_path: Path):
    """.orig / .rej / ~ / 副本 同属备份残留，一起禁。"""
    for name in ("x.kt.orig", "y.kt.rej", "z.kt~", "需求副本.md", "备份.txt"):
        _mk(tmp_path, f"docs/{name}")
    hits = hyg.scan(tmp_path)
    assert len(hits) == 5, hits


def test_clean_tree_passes(tmp_path: Path):
    """正常文件不得误报——误报会让规则被绕过或被关掉。"""
    for name in (
        "FooTest.kt",
        "README.md",
        ".gitignore",
        "AGENTS.md",
        "libs.versions.toml",
        "gov-validate.py",
        "MeasureRecord.java",
    ):
        _mk(tmp_path, f"src/{name}")
    assert hyg.scan(tmp_path) == []


def test_ignored_dirs_are_skipped(tmp_path: Path):
    """构建产物与工具缓存里的临时文件不算仓库内容。"""
    for d in ("build", ".gradle", "node_modules", "__pycache__", ".cache"):
        _mk(tmp_path, f"{d}/FooTest.kt.bak")
    assert hyg.scan(tmp_path) == []


def test_check_all_reports_fail(tmp_path: Path, monkeypatch, capsys):
    """check_all 必须真的 fail()，而不只是内部返回列表。"""
    from scripts.governance.modules import gov_validate_core as core

    _mk(tmp_path, "app/src/test/kotlin/FooTest.kt.bak")
    monkeypatch.setattr(hyg, "ROOT", tmp_path)

    hyg.check_all()

    assert any("GOV-HYG-001" in e and "FooTest.kt.bak" in e for e in core.errors), core.errors
    out = capsys.readouterr().out
    assert "GOV-HYG-001" in out


def test_check_all_passes_on_clean_tree(tmp_path: Path, monkeypatch, capsys):
    from scripts.governance.modules import gov_validate_core as core

    _mk(tmp_path, "app/src/test/kotlin/FooTest.kt")
    monkeypatch.setattr(hyg, "ROOT", tmp_path)

    hyg.check_all()

    assert core.errors == []
    assert "[PASS]" in capsys.readouterr().out
