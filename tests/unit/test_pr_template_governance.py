#!/usr/bin/env python3
"""PR 模板治理负向测试: 证明模板可用、且不会静默关掉 CI 门禁。

目的: `.github/PULL_REQUEST_TEMPLATE.md` 被 GC-multi-agent-collab §7 强制要求，
但「模板写得不巧」有两种隐形破坏，必须被测试锁住：

  1. 模板里出现放行关键词字面量
     gov-test-guard 用 `allow-no-tests`、gov-schema-guard 用 `allow-schema-change`
     去匹配 PR body —— 模板自带这两个词，等于所有 PR 永远放行，门禁形同虚设。

  2. 模板里出现 DEC 加三位数字
     gov-schema-guard 用 `\\bDEC-\\d{3}\\b` 认可「已引用决策」，模板若自带（如引用
     规范出处时写 DEC-048），未填写的 PR 也会被判为「已放行」，schema 门禁失效。

  以上都属于治理设计约束 Q4「盲点」与 Q5「demo gap」：规则存在但被自己人废掉。

运行: pytest tests/unit/test_pr_template_governance.py
"""
import re
import sys
from pathlib import Path

ROOT_P = Path(__file__).resolve().parents[2]
if str(ROOT_P) not in sys.path:
    sys.path.insert(0, str(ROOT_P))

TEMPLATE = ROOT_P / ".github" / "PULL_REQUEST_TEMPLATE.md"

# 与 scripts/governance/gov-test-guard.sh / gov-schema-guard.sh 保持一致
ALLOW_NO_TESTS = "allow-no-tests"
ALLOW_SCHEMA = "allow-schema-change"
DEC_RE = re.compile(r"\bDEC-\d{3}\b", re.I)


def _body() -> str:
    return TEMPLATE.read_text(encoding="utf-8")


def test_template_exists():
    """模板必须存在于规范指定路径（GC-multi-agent-collab §7）。"""
    assert TEMPLATE.is_file(), (
        f"缺少 {TEMPLATE.relative_to(ROOT_P)} —— "
        f"GC-multi-agent-collab §7 要求开 PR 必须使用该模板"
    )


def test_template_does_not_emit_allow_no_tests_token():
    """模板不得出现无测试放行词面量，否则 gov-test-guard 永久静默放行。"""
    body = _body()
    assert ALLOW_NO_TESTS not in body.lower(), (
        f"模板含 `{ALLOW_NO_TESTS}` —— gov-test-guard 按该词匹配 PR body，"
        f"模板自带会使所有 PR 自动放行，门禁失效"
    )


def test_template_does_not_emit_allow_schema_token():
    """模板不得出现 schema 放行词面量，否则 gov-schema-guard 永久静默放行。"""
    body = _body()
    assert ALLOW_SCHEMA not in body.lower(), (
        f"模板含 `{ALLOW_SCHEMA}` —— gov-schema-guard 按该词匹配 PR body，"
        f"模板自带会使黄金向量/Room schema 变更自动放行，门禁失效"
    )


def test_template_does_not_satisfy_schema_guard_dec_check():
    """模板不得自带 DEC 加三位数字，否则未填写的 PR 也会被判「已引用决策」。

    gov-schema-guard 只要 body 命中 DEC-\\d{3} 即放行；模板引用规范出处时
    顺手写个 DEC-048 就足以废掉这条门禁 —— 本用例防这个。
    """
    body = _body()
    hit = DEC_RE.search(body)
    assert hit is None, (
        f"模板含 `{hit.group(0)}` —— 会被 gov-schema-guard 误判为「已引用决策」，"
        f"使未填写的 PR 自动放行；出处请改用文档路径或章节号表述"
    )


def test_template_has_required_dec048_items():
    """模板必须覆盖 GC-multi-agent-collab §7 规定的四项硬要求。

    - 已跑 gov-local-ci.sh 且粘贴 PASS 摘要
    - 任务 AC / DoD 已覆盖
    - 未静默改黄金向量 / Room schema（有改必写决策）
    - 决策 / 关联任务 ID
    """
    body = _body()
    required = {
        "gov-local-ci": "已跑 gov-local-ci 并粘贴 PASS 摘要",
        "验收 Done when": "任务 AC / DoD 已覆盖",
        "黄金向量": "未静默改黄金向量",
        "Room schema": "未静默改 Room schema",
        "关联决策": "决策 ID 字段",
        "关联任务": "任务 ID 字段",
    }
    missing = [k for k in required if k not in body]
    assert not missing, f"模板缺少 §7 规定项: {missing}"


def test_template_asks_for_gov_validate_on_doc_changes():
    """文档变更须勾选 gov-validate（衔接本仓库 GOV-DOCS 门禁）。"""
    body = _body()
    assert "gov-validate" in body, "模板未要求文档变更跑 gov-validate"


def test_template_asks_for_prototype_verify_on_task_changes():
    """原型映射任务须勾选闭环验证（衔接 T-072~T-105 与 GOV-PROTO）。"""
    body = _body()
    assert "gov-prototype-verify" in body, "模板未要求原型任务跑闭环验证"
