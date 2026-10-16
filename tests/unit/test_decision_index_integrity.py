#!/usr/bin/env python3
"""决策索引完整性守卫 (DEC-019): 编号唯一 + 标题非空。

背景: 并行会话手工新建决策时复用了已占编号, 合并后索引出现重复编号行; 生成器又把带
YAML 前置元数据的决策标题解析成 "---"。同号多义就是多一种解释, 必须由测试挡住。

运行: pytest tests/unit/, 或由 gov-validate.sh 的 GOV-TEST-005 执行。
"""
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DECISIONS_DIR = ROOT / "docs" / "ai" / "decisions"
INDEX = DECISIONS_DIR / "index.md"

ROW = re.compile(r"^\|\s*\[(DEC-\d{3})\]\(DEC-\d{3}\.md\)\s*\|([^|]*)\|")


def _index_rows():
    """返回索引表格中的 (编号, 标题) 列表。"""
    rows = []
    for line in INDEX.read_text(encoding="utf-8").splitlines():
        hit = ROW.match(line)
        if hit:
            rows.append((hit.group(1), hit.group(2).strip()))
    return rows


def test_index_lists_every_decision_exactly_once():
    """索引必须与磁盘一致: 每个 DEC 恰好一行, 无重复编号。"""
    rows = _index_rows()
    indexed = [num for num, _ in rows]
    on_disk = sorted(p.stem for p in DECISIONS_DIR.glob("DEC-*.md"))

    duplicates = sorted({num for num in indexed if indexed.count(num) > 1})
    assert not duplicates, (
        "决策索引出现重复编号（同号多义）: "
        + ", ".join(duplicates)
        + "；请运行 bash scripts/governance/gov-generate-index.sh 重建"
    )
    assert sorted(indexed) == on_disk, (
        "决策索引与磁盘不一致：\n"
        f"  索引独有: {sorted(set(indexed) - set(on_disk))}\n"
        f"  磁盘独有: {sorted(set(on_disk) - set(indexed))}"
    )


def test_index_titles_are_real_titles():
    """标题列不得是 YAML 前置元数据残留的 '---' 或空。"""
    bad = [num for num, title in _index_rows() if title in ("", "---")]
    assert not bad, (
        "决策索引标题未解析（YAML 前置元数据残留）: "
        + ", ".join(bad)
        + "；生成器应跳过前置元数据后再取 H1"
    )


def test_status_board_decision_titles_are_real():
    """STATUS 看板的「最近决策」标题同样不得是 '---'（同一前置元数据缺陷）。"""
    status = ROOT / "STATUS.md"
    assert status.is_file(), "缺少自动生成的 STATUS.md"
    bad = [
        line.strip()
        for line in status.read_text(encoding="utf-8").splitlines()
        if line.startswith("- [DEC-") and line.rstrip().endswith("---")
    ]
    assert not bad, (
        "STATUS.md 决策标题未解析（YAML 前置元数据残留）:\n  " + "\n  ".join(bad)
    )
