#!/usr/bin/env python3
"""GOV-DOCS --fix 自愈行为测试。

设计原则: 自愈安全性 = 改错了会不会被同一条规则立刻抓到。

  自洽修复（修完必绿、修错必红）→ 允许自动修
      GOV-DOCS-004 计数同步: 依据 tasks.json 重算, 改错了 004 下次照样红

  自噬修复（修完必绿，无论对错）→ 必须加条件，否则规则被自己的修复杀死
      GOV-DOCS-005 日期 bump: 无脑改今天会让 005 永久失效,
                             因此只在「内容确实有变更」时才 bump

边界（与 gov-code-check.py --fix 同风格）:
      --fix 不处理索引描述、真源声明、Notion 同步 —— 这些需要判断

运行: pytest tests/unit/test_docs_autofix.py
"""
import subprocess
import sys
from datetime import date, timedelta
from pathlib import Path

import pytest

ROOT_P = Path(__file__).resolve().parents[2]
if str(ROOT_P) not in sys.path:
    sys.path.insert(0, str(ROOT_P))
_UNIT_DIR = str(Path(__file__).resolve().parent)
if _UNIT_DIR not in sys.path:
    sys.path.insert(0, _UNIT_DIR)

from scripts.governance.modules import gov_validate_core as core
from scripts.governance.modules import gov_validate_docs as docs_mod

from test_docs_governance import _make_repo, _taskgraph, _tasks_json, _check


def _clean_errors_guard():
    """快照-恢复语义 (DEC-007): 只隔离本测试产生的错误。"""
    saved_errors = list(core.errors)
    saved_warns = list(docs_mod.warnings)
    core.errors.clear()
    docs_mod.warnings.clear()
    yield
    core.errors.clear()
    core.errors.extend(saved_errors)
    docs_mod.warnings.clear()
    docs_mod.warnings.extend(saved_warns)


@pytest.fixture(autouse=True)
def _clean_errors():
    yield from _clean_errors_guard()


def _has(items, rule_id: str) -> bool:
    return any(rule_id in e for e in items)


def _git(tmp_path: Path, *args: str) -> str:
    r = subprocess.run(
        ["git", "-c", "user.email=t@t", "-c", "user.name=t", *args],
        cwd=tmp_path, capture_output=True, text=True,
    )
    return r.stdout


def _init_and_commit(tmp_path: Path):
    """建临时 git 仓库并提交当前 docs 状态（供「内容是否变更」判定）。"""
    _git(tmp_path, "init", "-q")
    _git(tmp_path, "add", "-A")
    _git(tmp_path, "commit", "-qm", "baseline")


# ---------------------------------------------------------------- 004 自洽修复

def test_fix004_repairs_stale_counts(tmp_path, monkeypatch):
    """GOV-DOCS-004: 计数脱节必须被 --fix 修好，且修完 004 变绿。"""
    _make_repo(
        tmp_path,
        tasks=_tasks_json(total=4, pending=2, proto=1, waves=(0,)),
        taskgraph=_taskgraph(total=99, pending=77, proto=8, waves=(0,)),
    )
    monkeypatch.setattr(docs_mod, "ROOT", tmp_path)
    monkeypatch.setattr(core, "ROOT", tmp_path)

    docs_mod.fix_all()

    errors, _ = _check(tmp_path, monkeypatch)
    assert not _has(errors, "GOV-DOCS-004"), errors

    text = (tmp_path / "docs/ai/lifecycle/03_TaskGraph.md").read_text(encoding="utf-8")
    assert "任务板共 4 条" in text
    assert "2 条 pending" in text
    assert "三波合计 1 条" in text


def test_fix004_writes_correct_wave_row(tmp_path, monkeypatch):
    """GOV-DOCS-004: wave 行的第二列必须被重算为任务板真实值。"""
    _make_repo(
        tmp_path,
        tasks=_tasks_json(total=4, pending=3, proto=3, waves=(0, 1, 2)),
        taskgraph=_taskgraph(total=4, pending=3, proto=3, waves=(0, 0, 0)),
    )
    monkeypatch.setattr(docs_mod, "ROOT", tmp_path)
    monkeypatch.setattr(core, "ROOT", tmp_path)

    docs_mod.fix_all()

    errors, _ = _check(tmp_path, monkeypatch)
    assert not _has(errors, "GOV-DOCS-004"), errors
    text = (tmp_path / "docs/ai/lifecycle/03_TaskGraph.md").read_text(encoding="utf-8")
    assert "| 1 | 1 |" in text, text


def test_fix004_does_not_fabricate_missing_row(tmp_path, monkeypatch):
    """边界: 摘要缺整个 wave 行时 --fix 不得凭空造结构，004 仍须 FAIL。

    造结构属于「判断」，交给人工；--fix 只重写已有锚点。
    """
    tasks = _tasks_json(total=4, pending=3, proto=3, waves=(0, 1, 2))
    tg = _taskgraph(total=4, pending=3, proto=3, waves=(0, 1, 2))
    # 删掉 wave=2 那一行，模拟结构缺失
    tg = "\n".join(ln for ln in tg.splitlines() if not ln.startswith("| 2 |"))
    _make_repo(tmp_path, tasks=tasks, taskgraph=tg + "\n")

    monkeypatch.setattr(docs_mod, "ROOT", tmp_path)
    monkeypatch.setattr(core, "ROOT", tmp_path)
    docs_mod.fix_all()

    errors, _ = _check(tmp_path, monkeypatch)
    assert _has(errors, "GOV-DOCS-004"), "缺行必须仍被 004 拦住，不得静默放行"


def test_fix_leaves_valid_docs_untouched(tmp_path, monkeypatch):
    """已一致的摘要不应被改写（--fix 不能做无意义的写操作）。"""
    _make_repo(
        tmp_path,
        tasks=_tasks_json(total=4, pending=2, proto=1, waves=(0,)),
        taskgraph=_taskgraph(total=4, pending=2, proto=1, waves=(0,)),
    )
    path = tmp_path / "docs/ai/lifecycle/03_TaskGraph.md"
    before = path.read_text(encoding="utf-8")

    monkeypatch.setattr(docs_mod, "ROOT", tmp_path)
    monkeypatch.setattr(core, "ROOT", tmp_path)
    docs_mod.fix_all()

    assert path.read_text(encoding="utf-8") == before


# ---------------------------------------------------------------- 005 自噬防护

_OLD = (date.today() - timedelta(days=90)).isoformat()


def test_fix005_does_not_bump_unchanged_file(tmp_path, monkeypatch):
    """GOV-DOCS-005 关键防护: 内容未变更时禁止 bump。

    无条件改日期会让 005 永久失效 —— 日期新鲜但内容过期，制造假信心。
    """
    _make_repo(tmp_path, taskgraph=_taskgraph(updated=_OLD))
    path = tmp_path / "docs/ai/lifecycle/03_TaskGraph.md"
    _init_and_commit(tmp_path)          # 干净工作树 = 无内容变更

    monkeypatch.setattr(docs_mod, "ROOT", tmp_path)
    monkeypatch.setattr(core, "ROOT", tmp_path)
    docs_mod.fix_all()

    text = path.read_text(encoding="utf-8")
    assert _OLD in text, f"内容未变却 bump 了日期，规则会被自己的修复杀死: {text[:200]}"
    errors, warnings = _check(tmp_path, monkeypatch)
    assert _has(warnings, "GOV-DOCS-005"), "未 bump 时 005 应继续提醒过期"


def test_fix005_bumps_when_content_changed(tmp_path, monkeypatch):
    """内容确实变更时才 bump —— 这才是合法的时间戳更新。"""
    _make_repo(tmp_path, taskgraph=_taskgraph(updated=_OLD))
    path = tmp_path / "docs/ai/lifecycle/03_TaskGraph.md"
    _init_and_commit(tmp_path)

    path.write_text(path.read_text(encoding="utf-8") + "\n新增一段内容。\n",
                    encoding="utf-8")

    monkeypatch.setattr(docs_mod, "ROOT", tmp_path)
    monkeypatch.setattr(core, "ROOT", tmp_path)
    docs_mod.fix_all()

    text = path.read_text(encoding="utf-8")
    assert date.today().isoformat() in text
    assert _OLD not in text


def test_fix005_safe_default_without_git(tmp_path, monkeypatch):
    """无法判定变更（非 git 仓库）时必须不 bump —— 保守默认保全规则有效性。"""
    _make_repo(tmp_path, taskgraph=_taskgraph(updated=_OLD))
    path = tmp_path / "docs/ai/lifecycle/03_TaskGraph.md"

    monkeypatch.setattr(docs_mod, "ROOT", tmp_path)
    monkeypatch.setattr(core, "ROOT", tmp_path)
    docs_mod.fix_all()

    assert _OLD in path.read_text(encoding="utf-8")


def test_fix_does_not_touch_nongoverned_files(tmp_path, monkeypatch):
    """--fix 只动 FRESHNESS_DOCS 与 TaskGraph，不得波及其他文档。"""
    _make_repo(
        tmp_path,
        link_doc="# 另一份文档\n\n更新时间：2026-01-01\n",
    )
    path = tmp_path / "docs/notes/NOTE-link.md"
    before = path.read_text(encoding="utf-8")

    monkeypatch.setattr(docs_mod, "ROOT", tmp_path)
    monkeypatch.setattr(core, "ROOT", tmp_path)
    docs_mod.fix_all()

    assert path.read_text(encoding="utf-8") == before
