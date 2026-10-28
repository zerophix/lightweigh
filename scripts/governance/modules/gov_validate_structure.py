#!/usr/bin/env python3
"""GOV-STRUCT-001~008: 文件遍历+命名+emoji+语言一致性"""
import os, re
from pathlib import Path
from .gov_validate_core import ROOT, DOCS, errors, fail

ALLOWED_FILES = {
    "index.md", "standard.md", "architecture/index.md",
    "guides/index.md", "reference/index.md", "changelog/index.md",
    "notes/index.md", "ai/index.md",
    "ai/lifecycle/index.md", "ai/lifecycle/01_PRD.md",
    "ai/lifecycle/02_Architecture.md", "ai/lifecycle/03_TaskGraph.md",
    "ai/lifecycle/04_DevNotes.md", "ai/lifecycle/05_Test.md",
    "ai/lifecycle/06_Metrics.md",
    "ai/decisions/index.md", "ai/guides/index.md",
    "ai/guides/workflow.md", "ai/guides/docstandard.md",
    "ai/guides/consensus.md", "ai/constraints/index.md",
    "ai/reviews/index.md",
}

DECISION_RE = re.compile(r"^ai/decisions/DEC-\d{3}\.md$")
NOTES_RE = re.compile(r"^notes/NOTE-\d{8}-\d{3}\.md$")
GUIDES_RE = re.compile(r"^guides/GC-[a-z0-9-]+\.md$")
REFERENCE_RE = re.compile(r"^reference/REF-[a-z0-9-]+\.md$")
REVIEW_FILE_RE = re.compile(r"^ai/reviews/\d{8}_[^/]+/[^/]+\.md$")
ARCHIVE_RE = re.compile(r"^archive/ARCH-[a-z0-9-]+\.md$")
INBOX_RE = re.compile(r"^ai/inbox/\d{8}-\d{6}\.md$")

ALLOWED_ROOT = {
    "README.md", "AGENTS.md",
    ".clinerules", ".cursorrules", "CHANGELOG.md", "STATUS.md",
}

SKIP_DIRS = {
    ".git", "node_modules", "venv", ".venv", "__pycache__", ".ruff_cache",
    ".next", ".cache", ".idea", "site", "dist", ".pytest_cache",
    ".mypy_cache", ".eggs", "egg-info", ".claude",
    # Android/Gradle 工程产物（gitignore 内，不属治理对象）
    "build", "dists", "outputs", "generated", "tmp",
    # 本地设计导出（已 gitignore；中文文件名，不属治理对象）— DEC-048
    "design",
}

from .gov_emoji import EMOJI_RE, contains_emoji, strip_emoji

LANG_CONFIG_PATH = ROOT / ".language"
if not LANG_CONFIG_PATH.is_file():
    LANG_CONFIG_PATH = ROOT / "scripts" / "language"
PRIMARY_LANG = ""
if LANG_CONFIG_PATH.is_file():
    match = re.match(r"^([a-zA-Z-]+)", LANG_CONFIG_PATH.read_text(encoding="utf-8"))
    if match:
        PRIMARY_LANG = match.group(1)

CN_RE = re.compile(r"[\u4e00-\u9fff]")
EN_RE = re.compile(r"[a-zA-Z]{3,}")

ALLOWED_REGEX = re.compile(
    r"^ai/decisions/DEC-\d{3}\.md$"
    r"|^notes/NOTE-\d{8}-\d{3}\.md$"
    r"|^guides/GC-[a-z0-9-]+\.md$"
    r"|^reference/REF-[a-z0-9-]+\.md$"
    r"|^ai/reviews/\d{8}_[^/]+/[^/]+\.md$"
    r"|^ops/GC-[a-z0-9-]+\.md$"
    r"|^archive/ARCH-[a-z0-9-]+\.md$"
    r"|^ai/inbox/\d{8}-\d{6}\.md$"
)

NAMING_RULES: list[tuple[str, str, str]] = [
    ("ai/lifecycle/", r"^\d{2}_[A-Za-z]+\.md$", "生命周期文件命名错误"),
    ("ai/decisions/", r"^DEC-\d{3}\.md$", "决策日志命名错误"),
    ("notes/", r"^NOTE-\d{8}-\d{3}\.md$", "笔记文件命名错误"),
    ("guides/", r"^GC-[a-z0-9-]+\.md$", "指南文件命名错误"),
    ("reference/", r"^REF-[a-z0-9-]+\.md$", "参考文件命名错误"),
    ("archive/", r"^ARCH-[a-z0-9-]+\.md$", "归档文件命名错误"),
    ("ops/", r"^GC-[a-z0-9-]+\.md$", "ops 文件命名错误"),
]


def _is_doc_allowed(doc_rel: str) -> bool:
    return doc_rel in ALLOWED_FILES or bool(ALLOWED_REGEX.match(doc_rel))


def walk_all() -> None:
    nonascii_files: list[str] = []
    abandoned_files: list[str] = []
    rogue_md: list[str] = []
    disallowed_files: list[str] = []
    naming_errors: list[str] = []
    missing_source: list[str] = []
    shallow_golden: list[str] = []
    emoji_files: list[str] = []
    lang_failures: list[str] = []

    for dirpath_str, dirs, files in os.walk(str(ROOT)):
        dirpath = Path(dirpath_str)
        dirname = dirpath.name
        if dirname in SKIP_DIRS or dirname.startswith("."):
            dirs.clear()
            continue
        rel_dir = dirpath.relative_to(ROOT).as_posix() if dirpath != ROOT else "."
        for fname in files:
            rel = f"{rel_dir}/{fname}" if rel_dir != "." else fname
            fpath = dirpath / fname

            if not all(32 <= ord(c) <= 126 for c in rel):
                nonascii_files.append(rel)

            if re.search(r"(^|/)old/|(/|^)legacy/|(/|^)v\d+/", rel):
                abandoned_files.append(rel)

            if fname.endswith(".md"):
                if rel.startswith("docs/"):
                    pass
                elif rel in ALLOWED_ROOT:
                    pass
                else:
                    rogue_md.append(rel)

            if not rel.startswith("docs/"):
                continue

            doc_rel = rel[len("docs/"):]

            if not fname.endswith(".md"):
                continue

            if not _is_doc_allowed(doc_rel):
                disallowed_files.append(doc_rel)
                continue

            basename = fname
            for prefix, pattern, msg in NAMING_RULES:
                if doc_rel.startswith(prefix) and doc_rel != prefix + "index.md":
                    if not re.match(pattern, basename):
                        naming_errors.append(
                            f"{msg}: {doc_rel} (期望格式: {pattern})")
                        break

            try:
                text = fpath.read_text(encoding="utf-8", errors="replace")
            except Exception:
                continue

            is_golden = doc_rel in (
                "architecture/index.md", "guides/index.md",
                "reference/index.md", "changelog/index.md",
                "notes/index.md",
            )
            is_ref_doc = bool(REFERENCE_RE.match(doc_rel))

            if not (is_golden or is_ref_doc):
                continue

            if '## 来源:' not in text:
                missing_source.append(doc_rel)

            valid_lines = 0
            for line in text.splitlines():
                stripped = line.strip()
                if stripped and not stripped.startswith("#") and not stripped.startswith(">"):
                    valid_lines += 1
            table_lines = text.count("|")
            if valid_lines + table_lines < 5:
                shallow_golden.append(f"{doc_rel} (仅{valid_lines + table_lines}行有效内容)")

            if EMOJI_RE.search(text):
                m = EMOJI_RE.search(text)
                line_num = text[:m.start()].count("\n") + 1
                emoji_files.append(f"{doc_rel}:{line_num}")

            if not PRIMARY_LANG:
                continue
            head = text[:300]
            cn_count = len(CN_RE.findall(head))
            en_count = len(EN_RE.findall(head))
            if len(head) < 100:
                continue
            LANG_SKIP_PREFIX = ("ai/decisions/DEC-", "notes/NOTE-", "changelog/")
            if any(doc_rel.startswith(p) for p in LANG_SKIP_PREFIX):
                continue
            if PRIMARY_LANG.startswith("zh") and cn_count < 3 and en_count > 10:
                lang_failures.append(f"文档应为中文但开头为英文: {doc_rel}")
            elif PRIMARY_LANG.startswith("en") and cn_count > 10:
                lang_failures.append(f"文档应为英文但含中文: {doc_rel}")
            if cn_count > 15 and en_count > 25:
                lang_failures.append(f"中英文混写: {doc_rel}")

    print()
    print("--- GOV-STRUCT-001: 文件名禁止非 ASCII ---")
    if nonascii_files:
        for f_ in nonascii_files:
            fail(f"GOV-STRUCT-001: 文件名含非 ASCII 字符: {f_}")
    else:
        print("  [PASS] 所有文件名使用 ASCII 字符")

    print()
    print("--- GOV-STRUCT-002: 遗弃文件检测 ---")
    if abandoned_files:
        for f_ in abandoned_files:
            fail(f"GOV-STRUCT-002: 遗弃文件（含 old/legacy/vN 路径）: {f_}")
    else:
        print("  [PASS] 无遗弃文件")

    print()
    print("--- GOV-STRUCT-003: 非法 Markdown 文件 ---")
    if rogue_md:
        for f_ in rogue_md:
            fail(f"GOV-STRUCT-003: 非法 Markdown 文件: {f_}")
    else:
        print("  [PASS] 无非 docs/ 的 Markdown 文件")

    print()
    print("--- GOV-STRUCT-004: 文件结构检查 ---")
    if disallowed_files:
        for f_ in disallowed_files:
            fail(f"GOV-STRUCT-004: 不允许的文件: docs/{f_}")
    else:
        print("  [PASS] 所有文件在允许清单中")

    print()
    print("--- GOV-STRUCT-005: 命名规范检查 ---")
    if naming_errors:
        for e in naming_errors:
            fail(e)
    else:
        print("  [PASS] 所有文件命名合规")

    print()
    print("--- GOV-STRUCT-006: 来源标注检查 ---")
    if missing_source:
        for f_ in missing_source:
            fail(f"GOV-STRUCT-006: 黄金标准缺少 ## 来源: 标注: docs/{f_}")
    else:
        print("  [PASS] 来源标注检查通过")

    print()
    print("--- GOV-STRUCT-007: 黄金标准内容深度 ---")
    if shallow_golden:
        for f_ in shallow_golden:
            fail(f"GOV-STRUCT-007: 黄金标准内容不足: docs/{f_}")
    else:
        print("  [PASS] 内容深度检查通过")

    print()
    print("--- GOV-STRUCT-019: Emoji 检测 ---")
    if emoji_files:
        for f_ in emoji_files:
            fail(f"GOV-STRUCT-019: 含 Emoji 字符: {f_}")
    else:
        print("  [PASS] 无 Emoji 字符")

    print()
    print("--- GOV-STRUCT-020: 语言一致性 ---")
    if lang_failures:
        for f_ in lang_failures:
            fail(f"GOV-STRUCT-020: {f_}")
    else:
        print(f"  [PASS] 语言一致（{PRIMARY_LANG or '未配置'}）")


def check_all() -> None:
    walk_all()
