"""Rules: Documentation structure & links (GOV-DOCS-001~005)."""
import json
import re
from datetime import date, datetime
from pathlib import Path
from typing import List
from ..shared import Context, Violation, Severity
from ..shared.filesystem import read_text, find_files


LINK_RE = re.compile(r"\]\(([^)\s]+)\)")
DATE_RE = re.compile(r"更新时间：\s*(\d{4}-\d{2}-\d{2})")
SKIP_PREFIX = ("http://", "https://", "mailto:", "notion://", "#")

# Guides index must collect all GC-*.md
# Reference index should collect all REF-*.md (WARN only)
# Mirror docs must declare truth source

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

FRESHNESS_DOCS = (
    "docs/index.md",
    "docs/ai/lifecycle/03_TaskGraph.md",
)
MAX_AGE_DAYS = 14


def check_docs_structure(ctx: Context) -> List[Violation]:
    """GOV-DOCS-001: Index coverage for guides/ and reference/."""
    violations = []
    
    # Check guides/index.md
    guides_idx = ctx.root / "docs" / "guides" / "index.md"
    if guides_idx.exists():
        content = read_text(guides_idx)
        for f in sorted(find_files(ctx.root, "docs/guides/GC-*.md")):
            fname = f.name
            if fname not in content:
                violations.append(Violation(
                    rule_id="GOV-DOCS-001",
                    message=f"docs/guides/{fname} 未被 docs/guides/index.md 收录",
                    severity=Severity.ERROR,
                    file=f,
                    tags=("push", "lane-a", "lane-b", "lane-c", "dod"),
                ))
    
    # Check reference/index.md (WARN only)
    ref_idx = ctx.root / "docs" / "reference" / "index.md"
    if ref_idx.exists():
        content = read_text(ref_idx)
        for f in sorted(find_files(ctx.root, "docs/reference/REF-*.md")):
            fname = f.name
            if fname not in content:
                violations.append(Violation(
                    rule_id="GOV-DOCS-001",
                    message=f"docs/reference/{fname} 未被 index.md 收录（晋升待办）",
                    severity=Severity.WARN,
                    file=f,
                    tags=("push", "lane-a", "lane-b", "lane-c", "dod"),
                ))
    
    return violations


def check_links(ctx: Context) -> List[Violation]:
    """GOV-DOCS-002: Local link reachability in docs/."""
    violations = []
    
    for f in find_files(ctx.root, "docs/**/*.md"):
        full = ctx.root / f
        text = read_text(full)
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
                resolved = ctx.root / rel.lstrip("/")
            else:
                resolved = full.parent / rel
            
            resolved_norm = Path(os.path.normpath(str(resolved)))
            if not resolved_norm.exists():
                violations.append(Violation(
                    rule_id="GOV-DOCS-002",
                    message=f"断链 -> {target}",
                    severity=Severity.ERROR,
                    file=f,
                    tags=("push", "lane-a", "lane-b", "lane-c", "dod"),
                ))
    
    return violations


def check_truth_source(ctx: Context) -> List[Violation]:
    """GOV-DOCS-003: Mirror docs must declare truth source."""
    violations = []
    
    for rel in MIRROR_DOCS:
        p = ctx.root / rel
        if not p.is_file():
            continue
        text = read_text(p)
        if "真源声明" not in text:
            violations.append(Violation(
                rule_id="GOV-DOCS-003",
                message=f"{rel} 缺「真源声明」——与 Notion 并存的文档必须标明哪边是权威",
                severity=Severity.ERROR,
                file=Path(rel),
                tags=("push", "lane-a", "lane-b", "lane-c", "dod"),
            ))
    
    return violations


def check_doc_counts(ctx: Context) -> List[Violation]:
    """GOV-DOCS-004: TaskGraph counts match tasks.json."""
    violations = []
    
    taskgraph = ctx.root / "docs" / "ai" / "lifecycle" / "03_TaskGraph.md"
    tasks_json = ctx.root / ".ai" / "tasks.json"
    
    if not taskgraph.exists() or not tasks_json.exists():
        return violations
    
    try:
        task_data = json.loads(read_text(tasks_json))
        tasks = task_data.get("tasks", [])
        pending = sum(1 for t in tasks if t.get("status") == "pending")
        in_progress = sum(1 for t in tasks if t.get("status") == "in_progress")
        done = sum(1 for t in tasks if t.get("status") == "done")
        
        tg_text = read_text(taskgraph)
        # Check if counts are mentioned and match
        if f"pending: {pending}" not in tg_text and f"待办: {pending}" not in tg_text:
            violations.append(Violation(
                rule_id="GOV-DOCS-004",
                message=f"03_TaskGraph.md 计数不一致：tasks.json pending={pending} 但文档未同步",
                severity=Severity.ERROR,
                file=taskgraph.relative_to(ctx.root),
                tags=("push", "lane-a", "lane-b", "lane-c", "dod"),
            ))
    except (json.JSONDecodeError, KeyError):
        pass
    
    return violations


def check_timestamps(ctx: Context) -> List[Violation]:
    """GOV-DOCS-005: Freshness timestamps on key docs."""
    violations = []
    today = date.today()
    
    for rel in FRESHNESS_DOCS:
        p = ctx.root / rel
        if not p.is_file():
            continue
        text = read_text(p)
        m = DATE_RE.search(text)
        if not m:
            violations.append(Violation(
                rule_id="GOV-DOCS-005",
                message=f"{rel} 缺「更新时间：YYYY-MM-DD」标记",
                severity=Severity.WARN,
                file=Path(rel),
                tags=("push", "lane-a", "lane-b", "lane-c", "dod"),
            ))
            continue
        try:
            d = datetime.strptime(m.group(1), "%Y-%m-%d").date()
        except ValueError:
            violations.append(Violation(
                rule_id="GOV-DOCS-005",
                message=f"{rel} 更新时间无法解析: {m.group(1)}",
                severity=Severity.WARN,
                file=Path(rel),
                tags=("push", "lane-a", "lane-b", "lane-c", "dod"),
            ))
            continue
        age = (today - d).days
        if age > MAX_AGE_DAYS:
            violations.append(Violation(
                rule_id="GOV-DOCS-005",
                message=f"{rel} 更新时间已过期 {age} 天（阈值 {MAX_AGE_DAYS} 天）",
                severity=Severity.WARN,
                file=Path(rel),
                tags=("push", "lane-a", "lane-b", "lane-c", "dod"),
            ))
    
    return violations


def check_all(ctx: Context) -> List[Violation]:
    """Combined entry for orchestrator."""
    violations = []
    violations.extend(check_docs_structure(ctx))
    violations.extend(check_links(ctx))
    violations.extend(check_truth_source(ctx))
    violations.extend(check_doc_counts(ctx))
    violations.extend(check_timestamps(ctx))
    return violations


import os