"""
GOV-SKILL-001~003: 技能约束系统化校验

将 .claude/skills/ 中的内容/结构约束从 AI 行为指引迁移为自动化校验:
  - GOV-SKILL-001: 阅读记录节段完整性 (来源: .claude/skills/阅读/SKILL.md)
  - GOV-SKILL-002: 评审报告 frontmatter 完整性 (来源: .claude/skills/评审/SKILL.md)
  - GOV-SKILL-003: Aha 顿悟卡片 ASCII 图表规范 (来源: .claude/skills/灵感/SKILL.md)
"""
import re, sqlite3
from pathlib import Path
from .gov_validate_core import ROOT


def _warn(msg):
    print(f'  [WARN] {msg}')


# ============================================================
# GOV-SKILL-001: 阅读记录章节完整性
# ============================================================

READING_SECTIONS = {
    "fiction": {
        "quick": {"## 核心结论", "## 世界模型", "## 可迁移式"},
        "phase": {"## 核心结论", "## 阅读轨迹", "## 世界模型", "## 假设流转", "## 可迁移式"},
        "deep": {"## 核心结论", "## 阅读轨迹", "## 认知闪光", "## 世界模型", "## 假设流转", "## 情绪焦点", "## 可迁移式"},
    },
    "code": {
        "quick": {"## 核心结论", "## 接口快照", "## 可迁移式"},
        "phase": {"## 核心结论", "## 阅读轨迹", "## 调用图", "## 设计意图", "## 可迁移式"},
        "deep": {"## 核心结论", "## 阅读轨迹", "## 认知闪光", "## 调用图", "## 设计意图", "## 阻塞点", "## 可迁移式"},
    },
    "doc": {
        "quick": {"## 核心结论", "## 接口快照", "## 可迁移式"},
        "phase": {"## 核心结论", "## 阅读轨迹", "## 接口模型", "## 约束条件", "## 可迁移式"},
        "deep": {"## 核心结论", "## 阅读轨迹", "## 认知闪光", "## 接口模型", "## 约束条件", "## 未解问题", "## 可迁移式"},
    },
    "news": {
        "quick": {"## 核心结论", "## 事实快照", "## 可迁移式"},
        "phase": {"## 核心结论", "## 阅读轨迹", "## 事实模型", "## 信源评估", "## 可迁移式"},
        "deep": {"## 核心结论", "## 阅读轨迹", "## 认知闪光", "## 事实模型", "## 信源评估", "## 因果链", "## 可迁移式"},
    },
}


def _detect_material(content: str) -> str | None:
    content_lower = content[:10].lower()
    if any(w in content_lower for w in ["小说", "fiction", "故事"]):
        return "fiction"
    if any(w in content_lower for w in ["代码", "code", "源码", "repo"]):
        return "code"
    if any(w in content_lower for w in ["文档", "doc", "规格", "spec"]):
        return "doc"
    if any(w in content_lower for w in ["新闻", "news", "咨询"]):
        return "news"
    return ""


def _detect_reading_note(filepath: Path) -> dict | None:
    try:
        content = filepath.read_text(encoding="utf-8")
    except Exception:
        return None
    material = None
    for pattern in ["material_type", "材料类型", "类型: ", "type:", "读物", "阅读区域"]:
        if pattern in content[:500]:
            material = _detect_material(content)
            break
    if not material:
        material = _detect_material(content)
    if not material:
        return None
    depth = "phase"
    if re.search(r"\bL1\b|速读|depth:\s*quick", content):
        depth = "quick"
    elif re.search(r"\bL3\b|深读|depth:\s*deep", content):
        depth = "deep"
    elif re.search(r"\bL2\b|精读|depth:\s*phase", content):
        depth = "phase"

    required = READING_SECTIONS.get(material, {}).get(depth, set())
    if not required:
        return None
    headings = set(
        m.group(1).strip()
        for m in re.finditer(r"^## (.+)$", content, re.MULTILINE)
    )
    missing = required - headings
    return {
        "path": str(filepath),
        "material": material,
        "depth": depth,
        "heading_s": headings,
        "required": list(required),
        "missing": list(missing),
    }


def check_01():
    notes_dir = ROOT / "docs" / "notes"
    if not notes_dir.is_dir():
        return
    reading_records = []
    for f_ in sorted(notes_dir.glob("NOTE-*.md")):
        info = _detect_reading_note(f_)
        if info and info["missing"]:
            reading_records.append(info)
    if not reading_records:
        return
    total = len(reading_records)
    for rec in reading_records:
        fname = Path(rec["path"]).name
        missing_headers = ", ".join(rec["missing"])
        _warn(
            f"GOV-SKILL-001: {fname} 缺少章节 ({rec['material']}/{rec['depth']}): {missing_headers}"
        )
    if reading_records:
        _warn(f"GOV-SKILL-001: {total} 篇阅读记录章节不完整")


# ============================================================
# GOV-SKILL-002: 评审报告 frontmatter 完整性
# ============================================================

REVIEW_REQUIRED_FIELDS = {
    "title": str, "date": str, "agent": str, "model": str, "timestamp": str,
    "mode": str, "type": str, "topic": str, "dimensions": list,
}

REVIEW_FRONT_MATTER_RE = re.compile(r"^---\s*\n(.*?)\n---\s*\n", re.DOTALL)
FINDING_NNN_RE = re.compile(r"^###\s+F-\d+\s*\|", re.MULTILINE)


def _parse_simple_yaml(text: str) -> dict:
    result = {}
    for line in text.split("\n"):
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        if match := re.match(r"^(\w+)\s*:\s*\[(.+)]\s*$", line):
            key = match.group(1)
            items = [i.strip().strip("\"'") for i in match.group(2).split(",") if i.strip()]
            result[key] = items
        elif match := re.match(r"^(\w+)\s*:\s*(.+)\s*$", line):
            key = match.group(1)
            val = match.group(2).strip().strip("\"'")
            try:
                val = int(val)
            except ValueError:
                pass
            result[key] = val
    return result


def check_02():
    reviews_dir = ROOT / "docs" / "ai" / "reviews"
    if not reviews_dir.is_dir():
        return
    review_files = list(reviews_dir.rglob("*.md"))
    if not review_files:
        return
    failures = 0
    for fp in sorted(review_files):
        try:
            content = fp.read_text(encoding="utf-8")
        except Exception:
            continue
        fm_match = REVIEW_FRONT_MATTER_RE.match(content)
        if not fm_match:
            _warn(f"GOV-SKILL-002: {fp.name} 缺少 YAML frontmatter (--- ... ---)")
            failures += 1
            continue
        fm_text = fm_match.group(1)
        fm = _parse_simple_yaml(fm_text)
        missing = []
        for field, expected_type in REVIEW_REQUIRED_FIELDS.items():
            if field not in fm:
                missing.append(field)
            elif expected_type == str and not isinstance(fm[field], str):
                missing.append(f"{field}(类型错误: 需文字)")
            elif expected_type == list and not isinstance(fm[field], list):
                missing.append(f"{field}(类型错误: 需列表)")
        if missing:
            _warn(f"GOV-SKILL-002: {fp.name} 缺少字段: {', '.join(missing)}")
            failures += 1
        if "finding_count" in fm and "critical" in fm and "high" in fm and "medium" in fm and "low" in fm:
            try:
                fc = int(fm["finding_count"])
                sev_sum = int(fm["critical"]) + int(fm["high"]) + int(fm["medium"]) + int(fm["low"])
                if fc != sev_sum:
                    _warn(f"GOV-SKILL-002: {fp.name} finding_count={fc} != 严重度合计={sev_sum}")
                    failures += 1
            except (ValueError, TypeError):
                pass
        body = content[fm_match.end():]
        if "find" in body.lower() or "fix" in body.lower() or "issue" in body.lower():
            if not FINDING_NNN_RE.search(body):
                _warn(f"GOV-SKILL-002: {fp.name} 缺少 F-NNN 格式的发现 ID")
                failures += 1
    if failures:
        _warn(f"GOV-SKILL-002: {failures} 个评审报告存在问题")


# ============================================================
# GOV-SKILL-003: Aha 顿悟卡片 ASCII 图表质量
# ============================================================

BOX_DRAWING_CHARS = set("┌┐└┘│├┤┬┴┼─→↓↑←►▼▲◄►◄=")


def check_03():
    db_path = ROOT / "data" / "learning-log.db"
    if not db_path.exists():
        return
    try:
        conn = sqlite3.connect(str(db_path))
        conn.row_factory = sqlite3.Row
        cur = conn.execute(
            "SELECT id, source, content FROM entries WHERE source = 'aha-moment'"
        )
        rows = cur.fetchall()
    except sqlite3.Error:
        return
    finally:
        conn.close()
    failures = 0
    for row in rows:
        entry_id = row["id"] if "id" in row.keys() else "?"
        content = row["content"] or ""
        diagram_blocks = re.finditer(r"```(?:text)?\n(.*?)\n```", content, re.DOTALL)
        found_valid = False
        for block_match in diagram_blocks:
            block_lines = block_match.group(1).split("\n")
            drawing_lines = [
                ln for ln in block_lines
                if any(ch in BOX_DRAWING_CHARS for ch in ln)
            ]
            if len(drawing_lines) >= 5:
                has_connector = any(ch in block_match.group(1) for ch in "→│└─┌┐┘►▼▲■")
                if has_connector:
                    found_valid = True
                    break
        if not found_valid:
            _warn(f"GOV-SKILL-003: aha entry #{entry_id} 缺少规范 ASCII 图表 (↙5 行含连接字符)")
            failures += 1
    if failures:
        _warn(f"GOV-SKILL-003: {failures} 个 aha 卡片缺少规范 ASCII 图表")


def check_all():
    check_01()
    check_02()
    check_03()
