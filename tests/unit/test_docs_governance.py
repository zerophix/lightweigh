#!/usr/bin/env python3
"""GOV-DOCS 规则负向测试: 故意制造违规, 断言 docs 治理规则确实 FAIL。

目的: 证明 GOV-DOCS-001~005 真实生效（治理设计约束 Q3 可验证性）。
没有负向测试的规则属于 hope-ware —— 规则被静默删除时无人会发现。

每个用例对应一次真实手工修复:
  - GOV-DOCS-001 guides 漏收  : 2026-10-23 发现 GC-prototype-driven-dev.md 未进索引
  - GOV-DOCS-002 相对链接断链 : 同日发现 ../guides 应为 ../../guides
  - GOV-DOCS-003 真源声明缺失 : 同日发现 01_PRD.md 与 Notion 两份 PRD 并存不标权威
  - GOV-DOCS-005 入口文档过期 : 「阶段描述过期」的可机检代理

运行: 由 gov-validate.sh 的 GOV-TEST 族自动执行, 或手动 pytest tests/unit/。
"""
import contextlib
import io
import json
import sys
from datetime import date, timedelta
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parents[2]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from scripts.governance.modules import gov_validate_core as core
from scripts.governance.modules import gov_validate_docs as docs_mod


def _clean_errors_guard():
    """快照-恢复语义 (DEC-007): 只隔离本测试产生的错误, 不吞宿主进程累积的现场。"""
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


def _check(tmp_path: Path, monkeypatch) -> tuple:
    """在临时目录上运行 docs 治理, 返回 (errors, warnings)。不触碰真实仓库。"""
    monkeypatch.setattr(docs_mod, "ROOT", tmp_path)
    monkeypatch.setattr(core, "ROOT", tmp_path)
    buf = io.StringIO()
    with contextlib.redirect_stdout(buf):
        docs_mod.check_all()
    return list(core.errors), list(docs_mod.warnings)


def _has(items, rule_id: str) -> bool:
    return any(rule_id in e for e in items)


# ---------------------------------------------------------------- 基线夹具

def _write(path: Path, text: str):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")


def _tasks_json(total: int = 4, pending: int = 2, proto: int = 1, waves=(0, 1, 0)) -> str:
    """构造可算出确定计数的任务板: total / pending / 含 prototype_ref / 各 wave 数。"""
    tasks = []
    proto_left = proto
    wave_left = list(waves)
    for i in range(1, total + 1):
        t = {"id": f"T-{i:03d}", "status": "pending" if i <= pending else "done"}
        if proto_left > 0:
            w = wave_left.pop(0) if wave_left else 0
            t["prototype_ref"] = {"file": "x.html", "views": ["v-a"]}
            t["wave"] = w
            proto_left -= 1
        tasks.append(t)
    return json.dumps({"tasks": tasks}, ensure_ascii=False)


def _taskgraph(total: int = 4, pending: int = 2, proto: int = 1,
               waves=(0, 1, 0), updated: str | None = None) -> str:
    """生成与 tasks.json 计数一致的摘要文档（004 校验的锚点文本）。"""
    updated = updated or date.today().isoformat()
    w_counts = {0: 0, 1: 0, 2: 0}
    for w in waves[:proto]:
        w_counts[w] = w_counts.get(w, 0) + 1
    rows = "\n".join(f"| {w} | {n} | 说明 |" for w, n in sorted(w_counts.items()))
    return (
        f"# 任务图\n\n> 更新时间：{updated}\n\n"
        f"任务板共 {total} 条，其中 {pending} 条 pending（T-001~T-002）。\n\n"
        f"| wave | 任务数 | 内容 |\n|------|--------|------|\n{rows}\n\n"
        f"三波合计 {proto} 条。\n"
    )


def _make_repo(tmp_path: Path, *, guide_index: str = "- [样本](GC-sample.md)\n",
               guide_file: bool = True,
               docs_index: str | None = None,
               prd: str = "# PRD\n\n**真源声明**：镜像。\n",
               taskgraph: str | None = None,
               tasks: str | None = None,
               link_doc: str | None = None,
               reference_index: str | None = None):
    if docs_index is None:
        # 入口页必须同时具备真源声明(003)与新鲜更新时间(005)
        docs_index = (f"# 入口\n\n## 真源声明\n\n产品权威在 Notion。\n\n"
                      f"更新时间：{date.today().isoformat()}\n")
    _write(tmp_path / "docs" / "index.md", docs_index)
    _write(tmp_path / "docs" / "guides" / "index.md", guide_index)
    if guide_file:
        _write(tmp_path / "docs" / "guides" / "GC-sample.md", "# 样本\n")
    _write(tmp_path / "docs" / "ai" / "lifecycle" / "01_PRD.md", prd)
    _write(tmp_path / "docs" / "ai" / "lifecycle" / "03_TaskGraph.md",
           taskgraph if taskgraph is not None else _taskgraph())
    _write(tmp_path / ".ai" / "tasks.json", tasks if tasks is not None else _tasks_json())
    (tmp_path / "docs" / "ai" / "inbox").mkdir(parents=True, exist_ok=True)
    if link_doc is not None:
        _write(tmp_path / "docs" / "notes" / "NOTE-link.md", link_doc)
    if reference_index is not None:
        _write(tmp_path / "docs" / "reference" / "index.md", reference_index)


# ---------------------------------------------------------------- 输出契约

def test_docs_errors_print_detail_to_stdout(tmp_path, monkeypatch):
    """GOV-DOCS 违规必须打印明细 —— 只给计数用户不知道错在哪。

    2026-10-23 真实缺陷: 模块用裸 errors.append 而非 core.fail,
    导致 gov-validate 只输出「违规: N 项」却没有任何明细。
    本用例防止该缺陷回归。
    """
    _make_repo(tmp_path, prd="# PRD\n\n没有声明谁是权威。\n")
    monkeypatch.setattr(docs_mod, "ROOT", tmp_path)
    monkeypatch.setattr(core, "ROOT", tmp_path)
    buf = io.StringIO()
    with contextlib.redirect_stdout(buf):
        docs_mod.check_all()
    out = buf.getvalue()
    assert "GOV-DOCS-003" in out, f"违规明细未打印，用户只会看到一个数字: {out!r}"
    assert "[FAIL]" in out, f"缺少与其它治理模块一致的 [FAIL] 前缀: {out!r}"


def test_docs_warnings_print_to_stdout(tmp_path, monkeypatch):
    """WARN 同样要打印，否则晋升待办这类非阻断提醒会永久不可见。"""
    _make_repo(tmp_path, reference_index="# 技术参考\n")
    _write(tmp_path / "docs" / "reference" / "REF-api-spec.md", "# API\n")
    monkeypatch.setattr(docs_mod, "ROOT", tmp_path)
    monkeypatch.setattr(core, "ROOT", tmp_path)
    buf = io.StringIO()
    with contextlib.redirect_stdout(buf):
        docs_mod.check_all()
    assert "GOV-DOCS-001" in buf.getvalue()


# ---------------------------------------------------------------- 空白对照

def test_clean_tree_produces_no_errors(tmp_path, monkeypatch):
    """空白对照: 一份完全合规的 docs 不应触发任何规则（防误报）。"""
    _make_repo(tmp_path)
    errors, warnings = _check(tmp_path, monkeypatch)
    assert errors == [], errors
    assert warnings == [], warnings


# ---------------------------------------------------------------- 001 索引收录

def test_docs001_guide_not_indexed(tmp_path, monkeypatch):
    """GOV-DOCS-001: guides 下存在但 index 未收录的 GC 文档必须 FAIL。"""
    _make_repo(tmp_path, guide_index="- [样本](GC-sample.md)\n")
    _write(tmp_path / "docs" / "guides" / "GC-forgot.md", "# 被漏掉的指南\n")
    errors, _ = _check(tmp_path, monkeypatch)
    assert _has(errors, "GOV-DOCS-001"), errors


def test_docs001_guide_indexed_ok(tmp_path, monkeypatch):
    """已收录的 GC 文档不应触发 001。"""
    _make_repo(tmp_path, guide_index="- [样本](GC-sample.md)\n")
    errors, _ = _check(tmp_path, monkeypatch)
    assert not _has(errors, "GOV-DOCS-001"), errors


def test_docs001_reference_file_is_warning_not_error(tmp_path, monkeypatch):
    """reference/index.md 声明「不可直接编辑、由晋升写入」，
    未收录只能是 WARN（晋升待办），不能当 ERROR 阻断。"""
    _make_repo(tmp_path, reference_index="# 技术参考\n")
    _write(tmp_path / "docs" / "reference" / "REF-api-spec.md", "# API\n")
    errors, warnings = _check(tmp_path, monkeypatch)
    assert not _has(errors, "GOV-DOCS-001"), errors
    assert _has(warnings, "GOV-DOCS-001"), warnings


# ---------------------------------------------------------------- 002 断链

def test_docs002_broken_relative_link(tmp_path, monkeypatch):
    """GOV-DOCS-002: 指向不存在文件的相对链接必须 FAIL。"""
    _make_repo(tmp_path, link_doc="[坏链接](../guides/GC-missing.md)\n")
    errors, _ = _check(tmp_path, monkeypatch)
    assert _has(errors, "GOV-DOCS-002"), errors


def test_docs002_valid_relative_link_ok(tmp_path, monkeypatch):
    """指向真实文件的相对链接不应触发 002。"""
    _make_repo(tmp_path, link_doc="[好链接](../guides/GC-sample.md)\n")
    errors, _ = _check(tmp_path, monkeypatch)
    assert not _has(errors, "GOV-DOCS-002"), errors


def test_docs002_external_and_anchor_links_skipped(tmp_path, monkeypatch):
    """外链与页内锚点不参与本地存在性检查（防误报）。"""
    _make_repo(tmp_path, link_doc="[外链](https://example.com/a.md)\n[锚点](#sec)\n")
    errors, _ = _check(tmp_path, monkeypatch)
    assert not _has(errors, "GOV-DOCS-002"), errors


def test_docs002_link_without_extension_ok(tmp_path, monkeypatch):
    """目录链接（如 ai/inbox/）与无扩展名目标按路径存在性判断，真实存在则放行。"""
    _make_repo(tmp_path, link_doc="[目录](../ai/inbox/)\n")
    errors, _ = _check(tmp_path, monkeypatch)
    assert not _has(errors, "GOV-DOCS-002"), errors


# ---------------------------------------------------------------- 003 真源声明

def test_docs003_prd_missing_truth_source(tmp_path, monkeypatch):
    """GOV-DOCS-003: 01_PRD.md 缺「真源声明」必须 FAIL —— 两份 PRD 不标权威即违规。"""
    _make_repo(tmp_path, prd="# PRD\n\n没有声明谁是权威。\n")
    errors, _ = _check(tmp_path, monkeypatch)
    assert _has(errors, "GOV-DOCS-003"), errors


def test_docs003_entry_index_missing_truth_source(tmp_path, monkeypatch):
    """docs/index.md 缺「真源声明」必须 FAIL（入口页必须交代 Notion 与仓库分工）。"""
    _make_repo(tmp_path, docs_index="# 入口\n\n没有真源说明。\n")
    errors, _ = _check(tmp_path, monkeypatch)
    assert _has(errors, "GOV-DOCS-003"), errors


def test_docs003_with_marker_ok(tmp_path, monkeypatch):
    """带真源声明标记的文档不应触发 003。"""
    _make_repo(tmp_path)
    errors, _ = _check(tmp_path, monkeypatch)
    assert not _has(errors, "GOV-DOCS-003"), errors


def test_docs003_extended_mirror_missing_truth_source(tmp_path, monkeypatch):
    """GOV-DOCS-003: 扩展后的 MIRROR_DOCS（如 05_Test.md）缺真源声明也必须 FAIL。"""
    _make_repo(tmp_path)
    _write(tmp_path / "docs" / "ai" / "lifecycle" / "05_Test.md", "# 测试\n\n没有声明权威。\n")
    errors, _ = _check(tmp_path, monkeypatch)
    assert _has(errors, "GOV-DOCS-003"), errors


def test_docs003_extended_mirror_with_marker_ok(tmp_path, monkeypatch):
    """扩展后的 MIRROR_DOCS 带真源声明标记不应触发 003。"""
    _make_repo(tmp_path)
    _write(tmp_path / "docs" / "ai" / "lifecycle" / "05_Test.md",
           "# 测试\n\n**真源声明**：Notion 为权威。\n")
    errors, _ = _check(tmp_path, monkeypatch)
    assert not _has(errors, "GOV-DOCS-003"), errors


# ---------------------------------------------------------------- 005 过期

def test_docs005_missing_updated_marker_is_warning(tmp_path, monkeypatch):
    """GOV-DOCS-005: 「当前状态」入口文档缺更新时间标记必须 WARN。"""
    _make_repo(tmp_path, docs_index="# 入口\n\n## 真源声明\n\n权威在 Notion。\n")
    _write(tmp_path / "docs" / "ai" / "lifecycle" / "03_TaskGraph.md",
           _taskgraph(updated=None) .split("更新时间：")[0] + "更新时间： 无\n")
    errors, warnings = _check(tmp_path, monkeypatch)
    assert _has(warnings, "GOV-DOCS-005"), warnings
    assert not _has(errors, "GOV-DOCS-005"), errors


def test_docs005_stale_date_is_warning(tmp_path, monkeypatch):
    """GOV-DOCS-005: 更新时间超过阈值必须 WARN（过期的阶段描述会误导）。"""
    old = (date.today() - timedelta(days=90)).isoformat()
    _make_repo(tmp_path, taskgraph=_taskgraph(updated=old))
    errors, warnings = _check(tmp_path, monkeypatch)
    assert _has(warnings, "GOV-DOCS-005"), warnings
    assert not _has(errors, "GOV-DOCS-005"), errors


def test_docs005_recent_date_ok(tmp_path, monkeypatch):
    """当天日期不应触发 005。"""
    _make_repo(tmp_path, taskgraph=_taskgraph(updated=date.today().isoformat()))
    errors, warnings = _check(tmp_path, monkeypatch)
    assert not _has(warnings, "GOV-DOCS-005"), warnings
    assert errors == [], errors
