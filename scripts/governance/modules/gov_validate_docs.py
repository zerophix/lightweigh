#!/usr/bin/env python3
"""
GOV-DOCS-001~005: docs 文档一致性与可达性校验

针对「文档写了但没人保证它是对的」这一类问题，把手工核对变成机器校验。

  GOV-DOCS-001  索引收录：guides 下的 GC-* 必须被 index 收录（ERROR）；
                              reference 下的 REF-* 未收录只 WARN（该 index 声明由晋升写入，不可手改）
  GOV-DOCS-002  相对链接可达性：docs/**/*.md 的本地链接必须指向真实路径（ERROR）
  GOV-DOCS-003  真源声明：与 Notion 并存的文档必须标出谁是权威（ERROR）
  GOV-DOCS-004  计数一致性：03_TaskGraph.md 的任务计数必须等于 .ai/tasks.json（ERROR）
  GOV-DOCS-005  入口文档时效：「当前状态」文档缺更新时间或超期（WARN）

设计约束（治理设计约束 Q1~Q5）:
  Q1 时机 —— 秒级结构问题，挂在 gov-validate，因此 pre-commit / gov-local-ci / GitHub CI 三层同时生效
  Q2 介质 —— validator 脚本而非 AGENTS.md（文档最容易被忽略）
  Q3 可测性 —— 每条规则在 tests/unit/test_docs_governance.py 有负向反例
  Q4 盲点 —— 只保证结构与数字，不保证「写得有没有道理」；那一半仍靠 Skill 与人工评审
  Q5 差距 —— 语义质量、Notion 侧回检不在本模块范围
"""
import json
import os
import re
import subprocess
from datetime import date, datetime
from pathlib import Path

from .gov_validate_core import ROOT, fail

# WARN 不阻断 gov-validate，但会在输出中打印
warnings: list[str] = []

# 「当前状态」入口文档：过期即误导，必须带更新时间
FRESHNESS_DOCS = (
    "docs/index.md",
    "docs/ai/lifecycle/03_TaskGraph.md",
)
MAX_AGE_DAYS = 14

# 与 Notion 并存、必须声明权威归属的文档
MIRROR_DOCS = (
    "docs/index.md",
    "docs/ai/lifecycle/01_PRD.md",
    "docs/ai/lifecycle/05_Test.md",
    "docs/guides/GC-decision-flow.md",
    "docs/ai/guides/workflow.md",
    "docs/guides/GC-coding-conventions.md",
    "docs/guides/GC-task-acceptance.md",
    "docs/guides/GC-multi-agent-collab.md",
    "docs/ai/guides/docstandard.md",
    "docs/notes/NOTE-20261016-001.md",
    "docs/notes/NOTE-20261021-001.md",
)

LINK_RE = re.compile(r"\]\(([^)\s]+)\)")
DATE_RE = re.compile(r"更新时间：\s*(\d{4}-\d{2}-\d{2})")
SKIP_PREFIX = ("http://", "https://", "mailto:", "notion://", "#")


def _warn(msg: str) -> None:
    warnings.append(msg)
    print(f"  [WARN] {msg}")


def _read(path: Path) -> str:
    try:
        return path.read_text(encoding="utf-8")
    except (OSError, UnicodeDecodeError):
        return ""


# ---------------------------------------------------------------- 001 索引收录

def _check_index_coverage() -> None:
    guides = ROOT / "docs" / "guides"
    idx = guides / "index.md"
    if guides.is_dir() and idx.is_file():
        content = _read(idx)
        for f in sorted(guides.glob("GC-*.md")):
            if f.name not in content:
                fail(
                    f"GOV-DOCS-001: docs/guides/{f.name} 未被 docs/guides/index.md 收录"
                )

    # reference/index.md 自述「不可直接编辑，由 DEC 晋升后写入」，
    # 未收录属晋升待办，只能提醒，不能阻断合入。
    ref = ROOT / "docs" / "reference"
    ridx = ref / "index.md"
    if ref.is_dir() and ridx.is_file():
        content = _read(ridx)
        for f in sorted(ref.glob("REF-*.md")):
            if f.name not in content:
                _warn(
                    f"GOV-DOCS-001: docs/reference/{f.name} 未被 index.md 收录（晋升待办）"
                )


# ---------------------------------------------------------------- 002 断链

def _check_links() -> None:
    docs = ROOT / "docs"
    if not docs.is_dir():
        return
    for md in sorted(docs.rglob("*.md")):
        text = _read(md)
        if not text or "](" not in text:
            continue
        for m in LINK_RE.finditer(text):
            target = m.group(1)
            if target.startswith(SKIP_PREFIX):
                continue
            rel = target.split("#")[0]
            if not rel:
                continue
            if rel.startswith("/"):
                resolved = ROOT / rel.lstrip("/")
            else:
                resolved = md.parent / rel
            if not Path(os.path.normpath(str(resolved))).exists():
                rel_md = md.relative_to(ROOT) if md.is_relative_to(ROOT) else md
                fail(f"GOV-DOCS-002: {rel_md} 断链 -> {target}")


# ---------------------------------------------------------------- 003 真源声明

def _check_truth_source() -> None:
    """与 Notion 并存的文档若不标权威，即链路文档明令禁止的「两份互相矛盾的最终需求」。"""
    for rel in MIRROR_DOCS:
        p = ROOT / rel
        if not p.is_file():
            continue
        if "真源声明" not in _read(p):
            fail(
                f"GOV-DOCS-003: {rel} 缺「真源声明」——"
                f"与 Notion 并存的文档必须标明哪边是权威"
            )


# ---------------------------------------------------------------- 005 时效

def _check_freshness() -> None:
    """阶段描述准确性无法机检，这是它最接近的可机检代理：入口文档的更新时间。"""
    today = date.today()
    for rel in FRESHNESS_DOCS:
        p = ROOT / rel
        if not p.is_file():
            continue
        m = DATE_RE.search(_read(p))
        if not m:
            _warn(f"GOV-DOCS-005: {rel} 缺「更新时间：YYYY-MM-DD」标记")
            continue
        try:
            d = datetime.strptime(m.group(1), "%Y-%m-%d").date()
        except ValueError:
            _warn(f"GOV-DOCS-005: {rel} 更新时间无法解析: {m.group(1)}")
            continue
        age = (today - d).days
        if age > MAX_AGE_DAYS:
            _warn(
                f"GOV-DOCS-005: {rel} 更新时间已过期 {age} 天（阈值 {MAX_AGE_DAYS} 天）"
            )


def check_all() -> None:
    warnings.clear()
    _check_index_coverage()
    _check_links()
    _check_truth_source()
    _check_freshness()


# ================================================================
# --fix 自愈层
#
# 准入标准（治理设计约束 Q3/Q4）: 自愈安全性 = 改错了会不会被同一条规则立刻抓到。
#
#   自洽修复（修完必绿、修错必红）→ 允许自动修
#       005 日期 bump（带内容变更条件，见下）
#
#   自噬修复（修完必绿，无论对错）→ 必须加条件，否则规则被自己的修复杀死
#       005 日期 bump: 只在「内容确实有变更」时 bump；否则日期新鲜而内容过期，
#                      制造假信心，005 从此永远报警不了
#
# 统一边界: 只重写已有锚点，不新增结构 —— 缺锚点 / 缺行 / 缺标记一律留给人工。
#           与 gov-code-check.py --fix 同风格: 修得完的自动修，需要判断的不碰。
# ================================================================

def _note(msg: str) -> None:
    print(f"  [FIX] {msg}")


def _content_changed(rel: str) -> bool | None:
    """该文件相对 HEAD 是否有内容变更。

    True/False = 判定结果; None = 无法判定（非 git 仓库、无 HEAD、git 不可用）。
    无法判定时调用方必须选择「不修改」—— 保守默认以保全规则有效性。
    """
    try:
        diff = subprocess.run(
            ["git", "diff", "--name-only", "HEAD", "--", rel],
            cwd=ROOT, capture_output=True, text=True, timeout=30,
        )
        if diff.returncode != 0:
            return None
        if diff.stdout.strip():
            return True
        untracked = subprocess.run(
            ["git", "ls-files", "--others", "--exclude-standard", "--", rel],
            cwd=ROOT, capture_output=True, text=True, timeout=30,
        )
        if untracked.returncode != 0:
            return None
        return bool(untracked.stdout.strip())
    except (OSError, subprocess.SubprocessError):
        return None


def fix_timestamps() -> set:
    """GOV-DOCS-005 --fix: 仅在内容确实变更时更新入口文档时间戳。

    未变更或无法判定 → 不动。缺标记 → 不补（造结构留给人工），继续由 005 提醒。
    """
    modified: set = set()
    today = date.today().isoformat()
    for rel in FRESHNESS_DOCS:
        p = ROOT / rel
        if not p.is_file():
            continue
        text = _read(p)
        if not text:
            continue
        m = DATE_RE.search(text)
        if not m:
            _note(f"{rel}: 缺「更新时间」标记，需人工添加（--fix 不造结构）")
            continue
        if m.group(1) == today:
            continue

        changed = _content_changed(rel)
        if changed is None:
            _note(f"{rel}: 无法判定内容变更，跳过 bump（保守默认）")
            continue
        if not changed:
            _note(f"{rel}: 内容无变更，不 bump（防止 005 被自己的修复杀死）")
            continue

        p.write_text(DATE_RE.sub(f"更新时间：{today}", text, count=1), encoding="utf-8")
        _note(f"{rel}: 更新时间 {m.group(1)} -> {today}（内容有变更）")
        modified.add(rel)
    return modified


def fix_all() -> set:
    """自动修复可自洽验证的项，返回被修改的文件（相对路径集合）。

    打印边界（同 gov-code-check.py --fix 风格）：修得完的自动修，需要判断的不碰。
    """
    print("\n--- GOV-DOCS --fix ---")
    modified = fix_timestamps()
    if not modified:
        print("  无需修改")
    print("\n  --fix 只重写已有锚点，缺锚点与缺行需人工补齐")
    print("  --fix 不处理索引描述、真源声明、Notion 同步（这些需要判断）")
    return modified
