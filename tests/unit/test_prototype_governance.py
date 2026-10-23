#!/usr/bin/env python3
"""原型任务规则负向测试 + 规则编号唯一性守卫（DEC-059）。

背景: gov_validate_prototype.py 初版使用 GOV-PROTO-001~007，与既有
gov_validate_protocol.py 的 GOV-PROTO-001~004（收件箱 48h / session-end 24h /
僵尸任务 / AC DoD）**撞号** —— 同一编号两种含义。该歧义在 2026-10-23 排障时
实际发生两次，属已证实缺陷而非假设。改名为 GOV-PTASK-* 后由本文件锁死。

三类约束:
  1. 编号唯一性 —— 任何编号不得在两个模块中同时「被输出」（通用守卫）
     判据用带冒号的消息串（GOV-XXX-NNN:），避免把 docstring 里的
     「镜像 GOV-SCRIPT-001~012」这类提及误判为定义
  2. 防回退 —— prototype 模块不得再出现 GOV-PROTO
  3. 规则本身的正负例 —— 该模块初版零测试，属治理设计约束 Q3 缺口，本文件补齐

运行: pytest tests/unit/test_prototype_governance.py
"""
import collections
import contextlib
import io
import json
import re
import sys
from pathlib import Path

import pytest

ROOT_P = Path(__file__).resolve().parents[2]
if str(ROOT_P) not in sys.path:
    sys.path.insert(0, str(ROOT_P))

from scripts.governance.modules import gov_validate_core as core
from scripts.governance.modules import gov_validate_prototype as pmod


def _clean_errors_guard():
    """快照-恢复语义 (DEC-007): 只隔离本测试产生的错误。"""
    saved = list(core.errors)
    core.errors.clear()
    yield
    core.errors.clear()
    core.errors.extend(saved)


@pytest.fixture(autouse=True)
def _clean_errors():
    yield from _clean_errors_guard()


def _has(items, rule_id: str) -> bool:
    return any(rule_id in e for e in items)


# ------------------------------------------------- 1. 编号唯一性守卫

# 只匹配「会真正输出」的编号: GOV-XXX-NNN 后跟冒号
EMITTED_ID = re.compile(r"GOV-[A-Z]+-\d{3}:")


def _emitted_ids_by_module() -> dict:
    out = {}
    for f in sorted((ROOT_P / "scripts" / "governance" / "modules").glob("gov_*.py")):
        src = f.read_text(encoding="utf-8", errors="ignore")
        out[f.name] = {m.rstrip(":") for m in EMITTED_ID.findall(src)}
    return out


def test_no_cross_module_rule_id_collision():
    """任何规则编号不得在两个模块中同时被输出 —— 本用例在改名前必然红。"""
    by_module = _emitted_ids_by_module()
    owners = collections.defaultdict(set)
    for mod, ids in by_module.items():
        for i in ids:
            owners[i].add(mod)
    dupes = {i: sorted(m) for i, m in owners.items() if len(m) > 1}
    assert not dupes, f"规则编号跨模块碰撞: {dupes}"


def test_both_key_modules_still_define_their_rules():
    """守卫自身要有效，前提是两模块确实都还在输出规则（防被删空导致假绿）。"""
    by_module = _emitted_ids_by_module()
    assert by_module["gov_validate_protocol.py"], "protocol 模块未输出任何规则"
    assert by_module["gov_validate_prototype.py"], "prototype 模块未输出任何规则"


def test_prototype_module_does_not_emit_protocol_ids():
    """防回退: prototype 模块不得再输出 GOV-PROTO-*（那是 protocol 的领地）。"""
    ids = _emitted_ids_by_module()["gov_validate_prototype.py"]
    leaked = sorted(i for i in ids if i.startswith("GOV-PROTO-"))
    assert not leaked, f"prototype 模块仍在输出 protocol 前缀: {leaked}"


def test_prototype_module_uses_ptask_prefix():
    """改名落地确认: 001~007 全部为 GOV-PTASK。"""
    ids = _emitted_ids_by_module()["gov_validate_prototype.py"]
    assert ids == {f"GOV-PTASK-{n:03d}" for n in range(1, 8)}, ids


# ------------------------------------------------- 2. 规则正负例

def _write_board(tmp_path: Path, tasks: list):
    d = tmp_path / ".ai"
    d.mkdir(parents=True, exist_ok=True)
    (d / "tasks.json").write_text(
        json.dumps({"tasks": tasks}, ensure_ascii=False), encoding="utf-8"
    )


def _make_proto(tmp_path: Path, name: str = "01-login.html"):
    d = tmp_path / "design" / "prototypes" / "interactive"
    d.mkdir(parents=True, exist_ok=True)
    (d / name).write_text('<div id="v-email">邮箱登录</div>', encoding="utf-8")


def _good_task(**over):
    t = {
        "id": "T-900",
        "prototype_ref": {
            "file": "design/prototypes/interactive/01-login.html",
            "views": ["v-email"],
        },
        "target_files": ["app/src/main/kotlin/com/xcwk/scalehub/feature/auth/X.kt"],
        "wave": 1,
        "module": "auth",
        "ac_refs": ["AC-AUTH-01 邮箱登录"],
        "prd_ref": "https://app.notion.com/p/abc",
    }
    t.update(over)
    return t


def _check(tmp_path: Path, monkeypatch) -> tuple:
    """在临时目录运行，返回 (errors, stdout)。不触碰真实仓库。"""
    monkeypatch.setattr(pmod, "ROOT", tmp_path)
    monkeypatch.setattr(core, "ROOT", tmp_path)
    buf = io.StringIO()
    with contextlib.redirect_stdout(buf):
        pmod.check_all()
    return list(core.errors), buf.getvalue()


def test_clean_task_produces_no_errors_or_warns(tmp_path, monkeypatch):
    """空白对照: 完整合规任务不应触发任何规则（防误报）。"""
    _make_proto(tmp_path)
    _write_board(tmp_path, [_good_task()])
    errors, out = _check(tmp_path, monkeypatch)
    assert errors == [], errors
    assert "[WARN]" not in out, out


def test_missing_prototype_file_fails(tmp_path, monkeypatch):
    """GOV-PTASK-001: prototype_ref.file 指向不存在的文件必须 FAIL。"""
    _write_board(tmp_path, [_good_task()])   # 不创建 html
    errors, _ = _check(tmp_path, monkeypatch)
    assert _has(errors, "GOV-PTASK-001"), errors


def test_bad_file_path_format_fails(tmp_path, monkeypatch):
    """GOV-PTASK-001: file 路径不符合原型目录约定必须 FAIL。"""
    _make_proto(tmp_path)
    _write_board(tmp_path, [_good_task(
        prototype_ref={"file": "somewhere/else.html", "views": ["v-email"]},
    )])
    errors, _ = _check(tmp_path, monkeypatch)
    assert _has(errors, "GOV-PTASK-001"), errors


def test_missing_trace_fields_warn(tmp_path, monkeypatch):
    """GOV-PTASK-005/006/007: 缺 module / ac_refs / prd_ref 必须提醒。

    这三个字段是 DOC-CHAIN §3「M# + AC + 上层链接」的落地，缺一不得标 Done。
    """
    _make_proto(tmp_path)
    task = _good_task()
    task.pop("module"), task.pop("ac_refs"), task.pop("prd_ref")
    _write_board(tmp_path, [task])
    errors, out = _check(tmp_path, monkeypatch)
    for rule in ("GOV-PTASK-005", "GOV-PTASK-006", "GOV-PTASK-007"):
        assert rule in out, f"{rule} 未提醒: {out}"
    assert errors == [], errors


def test_invalid_wave_warns(tmp_path, monkeypatch):
    """GOV-PTASK-004: wave 只允许 0/1/2。"""
    _make_proto(tmp_path)
    _write_board(tmp_path, [_good_task(wave=7)])
    errors, out = _check(tmp_path, monkeypatch)
    assert "GOV-PTASK-004" in out, out


def test_empty_views_warns(tmp_path, monkeypatch):
    """GOV-PTASK-002: views 为空必须提醒（没有 view 就没有参考对象）。"""
    _make_proto(tmp_path)
    _write_board(tmp_path, [_good_task(
        prototype_ref={"file": "design/prototypes/interactive/01-login.html", "views": []},
    )])
    errors, out = _check(tmp_path, monkeypatch)
    assert "GOV-PTASK-002" in out, out


def test_task_without_prototype_ref_is_skipped(tmp_path, monkeypatch):
    """非原型任务（无 prototype_ref）应被跳过，不得误报。"""
    _write_board(tmp_path, [{"id": "T-001", "status": "pending"}])
    errors, out = _check(tmp_path, monkeypatch)
    assert errors == [], errors
    assert "GOV-PTASK" not in out
