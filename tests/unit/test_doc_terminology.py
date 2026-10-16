#!/usr/bin/env python3
"""术语与口径漂移守卫 (DEC-019): 禁止裸口径的数量表述。

治理设计约束 Q3(可验证性): 口径规则若只写在文档里, 下一位 AI 照样会写「21 项测试」。
本测试扫描现行文档, 命中「N 项测试 / N 个用例」这类不写口径的表述即 FAIL。

范围: README.md + docs/**/*.md。
豁免: 追加型历史记录(decisions/changelog/inbox/reviews/notes)不改写正文, 其旧表述由
      reference/index.md §6「历史记录口径对照」统一解释; 术语表本身要引用旧说法做对照,
      一并豁免。
运行: pytest tests/unit/, 或由 gov-validate.sh 的 GOV-TEST-005 执行。
"""
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
GLOSSARY = ROOT / "docs" / "reference" / "index.md"
DOCSTANDARD = ROOT / "docs" / "ai" / "guides" / "docstandard.md"

EXCLUDE_DIRS = (
    "docs/ai/decisions",
    "docs/ai/inbox",
    "docs/ai/reviews",
    "docs/changelog",
    "docs/notes",
)
EXCLUDE_FILES = ("docs/reference/index.md",)

# 裸口径: 「21 项测试」「13 个用例」「7 个测试用例」。
# 规范写法「N 个单元测试方法」「N 个治理测试用例」不匹配（量词后紧跟限定词）。
BARE_COUNT = re.compile(r"\d+\s*[项个条]\s*(?:测试用例|测试|用例)")


def _scanned_files():
    """返回 (相对路径, 绝对路径) 列表: README + docs 下非历史记录文档。"""
    files = [ROOT / "README.md", *sorted((ROOT / "docs").rglob("*.md"))]
    selected = []
    for path in files:
        rel = path.relative_to(ROOT).as_posix()
        if rel in EXCLUDE_FILES:
            continue
        if any(rel.startswith(prefix + "/") for prefix in EXCLUDE_DIRS):
            continue
        selected.append((rel, path))
    return selected


def test_current_docs_use_canonical_terms_only():
    """现行文档不得出现不写口径的数量表述。"""
    offenders = []
    for rel, path in _scanned_files():
        for lineno, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
            hit = BARE_COUNT.search(line)
            if hit:
                offenders.append(f"{rel}:{lineno}: {hit.group(0)}")
    assert not offenders, (
        "以下位置使用了不写口径的数量表述, 请改用术语表原名"
        "（单元测试方法 / 测试执行数 / 治理测试用例）:\n  " + "\n  ".join(offenders)
    )


def test_glossary_is_the_single_source_of_truth():
    """术语表必须存在并声明关键术语与历史对照节。"""
    text = GLOSSARY.read_text(encoding="utf-8")
    for term in ("术语与口径", "单元测试方法", "测试执行数", "治理测试用例", "历史记录口径对照"):
        assert term in text, f"术语表缺少: {term}"


def test_doc_standard_carries_the_rule():
    """写作规则必须把口径要求写进文档标准, 否则规则只活在术语表里。"""
    text = DOCSTANDARD.read_text(encoding="utf-8")
    assert "数量与口径" in text, "docstandard.md 缺少「数量与口径」规则"
    assert "reference/index.md" in text, "写作规则未指向术语表"
