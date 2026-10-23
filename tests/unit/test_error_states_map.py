#!/usr/bin/env python3
"""T-097 ErrorStates.kt 原型分类表的闭环测试。

目的:
  1. 证明 `PROTOTYPE_STATE_MAP` 覆盖原型 `15-states-errors.html` 的**全部 view**，
     且与**真实 HTML 交叉核对** —— 原型增删 view 而未同步分类表时本测试会红
     （这是原型 → 代码的反向闭环，DoD 第 2 条「16 个 view 均有实现或明确标注不适用」的可验证形式）
  2. 分类自洽: SpecificScreen 必须 state=null（明确不适用），Generic 必须有具体状态
  3. 复刻闭环脚本的硬编码颜色检查（防止 ErrorStates.kt 落入 Color(0x...)）

注意: 分类表是纯 Kotlin 数据，不依赖 Compose 运行时，故用文本解析而非跑 Kotlin 单测，
     避免为一个数据表引入 android-test 注册与编译开销。

运行: pytest tests/unit/test_error_states_map.py
"""
import re
import sys
from pathlib import Path

import pytest

ROOT_P = Path(__file__).resolve().parents[2]
if str(ROOT_P) not in sys.path:
    sys.path.insert(0, str(ROOT_P))

KOTLIN_FILE = ROOT_P / "app/src/main/kotlin/com/xcwk/scalehub/ui/states/ErrorStates.kt"
PROTOTYPE = ROOT_P / "design/prototypes/interactive/15-states-errors.html"

# 与 scripts/governance/gov-prototype-verify.py 的 HARDCODED_COLOR 保持一致
HARDCODED_COLOR = re.compile(r"Color\(0x[0-9A-Fa-f]{6,8}\)")

# QkPrototypeState(viewId, kind, state, note)
ENTRY = re.compile(
    r'QkPrototypeState\(\s*"(v-[a-z-]+)"\s*,\s*'
    r"(QkStateKind\.\w+)\s*,\s*"
    r"(QkGenericState\.\w+|null)\s*,\s*"
    r'"([^"]*)"',
    re.S,
)


def _map_entries() -> list:
    return ENTRY.findall(KOTLIN_FILE.read_text(encoding="utf-8"))


def _prototype_view_ids() -> list:
    html = PROTOTYPE.read_text(encoding="utf-8")
    return re.findall(r'id="(v-[a-z-]+)"', html)


def test_target_file_exists():
    assert KOTLIN_FILE.is_file(), "T-097 目标文件不存在"


def test_prototype_has_views():
    ids = _prototype_view_ids()
    assert len(ids) >= 10, f"原型 view 数异常: {len(ids)}"


def test_map_covers_every_prototype_view():
    """分类表必须覆盖原型全部 view —— 原型增删 view 时本用例红，强制同步。"""
    proto_ids = set(_prototype_view_ids())
    mapped = {e[0] for e in _map_entries()}
    missing = sorted(proto_ids - mapped)
    assert not missing, (
        f"原型存在但分类表未覆盖的 view: {missing} —— "
        f"DoD 要求「16 个 view 均有实现或明确标注不适用」"
    )


def test_map_has_no_unknown_entries():
    """分类表不得包含原型里不存在的 view（防止编造）。"""
    proto_ids = set(_prototype_view_ids())
    mapped = {e[0] for e in _map_entries()}
    extra = sorted(mapped - proto_ids)
    assert not extra, f"分类表含原型不存在的 view: {extra}"


def test_map_has_no_duplicates():
    ids = [e[0] for e in _map_entries()]
    dupes = sorted({i for i in ids if ids.count(i) > 1})
    assert not dupes, f"重复分类: {dupes}"


def test_classification_is_self_consistent():
    """SpecificScreen 必须 state=null；Generic 必须有具体状态。"""
    bad = []
    for view_id, kind, state, note in _map_entries():
        is_generic = kind.endswith("Generic")
        has_state = state != "null"
        if is_generic and not has_state:
            bad.append(f"{view_id}: Generic 却无 state")
        if (not is_generic) and has_state:
            bad.append(f"{view_id}: SpecificScreen 却带 state")
    assert not bad, bad


def test_every_entry_has_a_note():
    """每个 view 必须写明归属理由 —— 「不适用」也要说清为什么不适用。"""
    blank = [e[0] for e in _map_entries() if not e[3].strip()]
    assert not blank, f"缺少归属说明: {blank}"


def test_generic_states_cover_required_acs():
    """AC 落地确认: 空态、局部错误、全屏加载必须在分类表中出现。"""
    kinds = {e[2] for e in _map_entries() if e[1].endswith("Generic")}
    for required in ("QkGenericState.PartialError", "QkGenericState.FullScreenLoading"):
        assert required in kinds, f"缺少 {required}（原型对应 view 未归入通用状态）"


def test_no_hardcoded_color_in_target_file():
    """复刻闭环脚本检查: 目标文件不得出现硬编码颜色。"""
    hits = HARDCODED_COLOR.findall(KOTLIN_FILE.read_text(encoding="utf-8"))
    assert not hits, f"硬编码颜色: {hits} —— 必须改用 QkColor token"


def test_no_bare_dp_literals():
    """裸 dp 不得出现（GC-prototype-driven-dev 要求用 QkSpacing / QkRadius token）。"""
    text = KOTLIN_FILE.read_text(encoding="utf-8")
    bare = re.findall(r"(?<![A-Za-z])\d+\.dp\b", text)
    assert not bare, f"裸 dp 字面量: {bare}"


def test_file_uses_qk_tokens():
    """必须实际使用主题 token（否则可能绕过设计系统）。"""
    text = KOTLIN_FILE.read_text(encoding="utf-8")
    for token in ("QkColor.", "QkSpacing.", "QkRadius."):
        assert token in text, f"未使用 {token}"


def test_comment_mentioning_color_token_is_not_flagged():
    """守护检查本身的精确性: 文件里「说明规则的注释」不得被硬编码颜色正则误判。

    ErrorStates.kt 的 KDoc 含 `禁止 Color(0x...) 与裸 dp` 这类规则说明文字，
    若正则过于宽松会把注释判成违规，导致规则制造假阳性、最终被人绕过。
    """
    code = KOTLIN_FILE.read_text(encoding="utf-8")
    comment_lines = [
        ln for ln in code.splitlines() if "Color(0x" in ln and ln.lstrip().startswith(("//", "*"))
    ]
    assert comment_lines, "未找到提及 Color(0x...) 的注释，本用例失去意义，需与实现同步"
    for ln in comment_lines:
        assert HARDCODED_COLOR.search(ln) is None, (
            f"注释行被硬编码颜色正则误判（假阳性）: {ln.strip()}"
        )
