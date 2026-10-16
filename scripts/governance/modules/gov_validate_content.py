#!/usr/bin/env python3
"""GOV-CONTENT-001~012: 内容深度校验"""
import json, os, re, subprocess
from datetime import datetime, timezone
from pathlib import Path
from .gov_validate_core import ROOT, DOCS, MEMORY_FILE, errors, fail


def check_lifecycle_binding() -> None:
    print("\n--- GOV-CONTENT-001: 生命周期->黄金标准绑定 ---")
    lc_path = DOCS / "ai" / "lifecycle" / "02_Architecture.md"
    gs_path = DOCS / "architecture" / "index.md"
    if lc_path.is_file():
        lc_lines = len(lc_path.read_text(encoding="utf-8", errors="replace").splitlines())
        gs_lines = len(gs_path.read_text(encoding="utf-8", errors="replace").splitlines()) if gs_path.is_file() else 0
        if lc_lines > 20 and gs_lines < 10:
            fail(f"GOV-CONTENT-001: 生命周期 02_Architecture.md 已更新({lc_lines}行)，但黄金标准 architecture/index.md 仅有{gs_lines}行")
        else:
            print("  [PASS] 生命周期绑定检查通过")
    else:
        print("  [PASS] 生命周期绑定检查通过")


def check_metrics_timeliness() -> None:
    print("\n--- GOV-CONTENT-002: 指标时效性 ---")
    metrics_path = DOCS / "ai" / "lifecycle" / "06_Metrics.md"
    if not metrics_path.is_file():
        print("  [SKIP] 06_Metrics.md 不存在")
        return
    text = metrics_path.read_text(encoding="utf-8", errors="replace")
    dates = re.findall(r"20\d{2}-\d{2}-\d{2}", text)
    if not dates:
        fail("GOV-CONTENT-002: 指标文件无日期，无法判断时效性")
        return
    try:
        latest = max(datetime.fromisoformat(d) for d in dates)
        days_old = (datetime.now(timezone.utc).replace(tzinfo=None) - latest).days
        if days_old > 30:
            fail(f"GOV-CONTENT-002: 指标数据已 {days_old} 天未更新（>30天）")
        else:
            print(f"  [PASS] 指标最近更新 {days_old} 天前")
    except ValueError:
        fail("GOV-CONTENT-002: 指标文件日期格式异常")


def check_changelog() -> None:
    print("\n--- GOV-CONTENT-003: Changelog 非空 ---")
    cl_path = DOCS / "changelog" / "index.md"
    if not cl_path.is_file():
        fail("GOV-CONTENT-003: changelog/index.md 不存在")
        return
    text = cl_path.read_text(encoding="utf-8", errors="replace")
    if len(text.strip()) < 100 or "## " not in text:
        fail("GOV-CONTENT-003: changelog/index.md 内容过少或无版本节")
    else:
        print("  [PASS] Changelog 非空且含版本节")


def check_decisions() -> None:
    print("\n--- GOV-CONTENT-004: 决策日志完整性 ---")
    dec_dir = DOCS / "ai" / "decisions"
    if not dec_dir.is_dir():
        print("  [SKIP] ai/decisions 目录不存在")
        return
    dec_files = sorted(f for f in dec_dir.iterdir() if f.is_file() and re.match(r"DEC-\d{3}\.md$", f.name))
    if not dec_files:
        print("  [SKIP] 无决策文件")
        return
    dec_nums = [int(re.search(r"DEC-(\d{3})", f.name).group(1)) for f in dec_files]
    if dec_nums[0] != 1:
        fail(f"GOV-CONTENT-004: 决策编号不从 DEC-001 开始: DEC-{dec_nums[0]:03d}")
    for i in range(1, len(dec_nums)):
        expected = dec_nums[i - 1] + 1
        if dec_nums[i] != expected:
            fail(f"GOV-CONTENT-004: 决策编号不连续: DEC-{dec_nums[i-1]:03d} -> DEC-{dec_nums[i]:03d} (跳过 DEC-{expected:03d})")
    print(f"  [PASS] 决策日志共 {len(dec_nums)} 条，编号连续")


def check_reviews() -> None:
    print("\n--- GOV-CONTENT-005: 会审报告规范 ---")
    REVIEW_DIR_RE = re.compile(r"^\d{8}_[a-z][a-z0-9_-]*$")
    REVIEW_AGENT_FILE_RE = re.compile(r"^[a-z][a-z0-9_-]*__\d{8}_\d{6}\.md$")
    reviews_dir = DOCS / "ai" / "reviews"
    if not reviews_dir.is_dir():
        print("  [PASS] 无会审报告目录")
        return
    review_count = 0
    naming_issues: list[str] = []
    fm_issues: list[str] = []
    for subdir in sorted(reviews_dir.iterdir()):
        if not subdir.is_dir():
            continue
        if not REVIEW_DIR_RE.match(subdir.name):
            naming_issues.append(f"评审目录名不符合规范: {subdir.name}")
        for report in sorted(subdir.iterdir()):
            if not report.name.endswith(".md"):
                continue
            if report.name == "conclusion.md":
                continue
            if not REVIEW_AGENT_FILE_RE.match(report.name):
                naming_issues.append(f"评审文件名不符合规范: {subdir.name}/{report.name}")
            review_count += 1
            text = report.read_text(encoding="utf-8", errors="replace")
            lines = text.splitlines()
            if not lines or lines[0] != "---":
                fm_issues.append(f"评审报告缺少 front-matter: ai/reviews/{subdir.name}/{report.name}")
                continue
            fm_end = 1
            for j in range(1, len(lines)):
                if lines[j] == "---":
                    fm_end = j; break
            fm_text = "\n".join(lines[1:fm_end])
            required_fields = ["agent:", "model:", "timestamp:", "mode:", "type:", "topic:"]
            for field in required_fields:
                if field not in fm_text:
                    fm_issues.append(f"评审报告缺少字段 {field}: ai/reviews/{subdir.name}/{report.name}")
    if naming_issues:
        for issue in naming_issues:
            fail(issue)
    if fm_issues:
        for issue in fm_issues:
            fail(issue)
    if review_count == 0:
        print("  [PASS] 无会审报告")
    else:
        issues = len(naming_issues) + len(fm_issues)
        if issues == 0:
            print(f"  [PASS] 会审报告 {review_count} 份，全部通过")
        else:
            print(f"  会审报告 {review_count} 份，{issues} 项问题")


def check_memory() -> None:
    print("\n--- GOV-CONTENT-008: 项目记忆格式 ---")
    if not MEMORY_FILE.is_file():
        print("  [SKIP] context.jsonl 不存在")
        return
    format_fail = 0
    line_num = 0
    for line in MEMORY_FILE.read_text(encoding="utf-8").splitlines():
        line_num += 1
        line = line.strip()
        if not line:
            continue
        try:
            d = json.loads(line)
        except json.JSONDecodeError:
            fail(f"GOV-CONTENT-008: context.jsonl 第 {line_num} 行不是合法 JSON")
            format_fail += 1
            continue
        missing = [f_ for f_ in ("id", "type", "ts", "agent") if f_ not in d]
        if missing:
            fail(f"GOV-CONTENT-008: context.jsonl 第 {line_num} 行缺少必填字段: {', '.join(missing)}")
            format_fail += 1
    if format_fail == 0:
        print(f"  [PASS] 项目记忆格式合法（{line_num} 条）")


def check_memory_freshness() -> None:
    print("\n--- GOV-CONTENT-009: 项目记忆时效性 ---")
    if not MEMORY_FILE.is_file():
        print("  [SKIP] context.jsonl 不存在")
        return
    last_entry = ""
    for line in MEMORY_FILE.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if line:
            last_entry = line
    if not last_entry:
        fail("GOV-CONTENT-009: context.jsonl 为空")
        return
    try:
        d = json.loads(last_entry)
        ts = d.get("ts", "")
    except json.JSONDecodeError:
        fail("GOV-CONTENT-009: context.jsonl 最后一行格式错误")
        return
    if not ts:
        fail("GOV-CONTENT-009: context.jsonl 最后一条缺少 ts 字段")
        return
    try:
        last_dt = datetime.fromisoformat(ts.replace("Z", "+00:00"))
        days_old = (datetime.now(timezone.utc) - last_dt).days
        if days_old > 30:
            fail(f"GOV-CONTENT-009: 项目记忆已 {days_old} 天未更新")
        else:
            print(f"  [PASS] 项目记忆最近更新（{days_old}天前）")
    except ValueError:
        fail(f"GOV-CONTENT-009: context.jsonl 时间戳格式错误: {ts}")


def check_readme() -> None:
    print("\n--- GOV-CONTENT-011: README 内容时效性 ---")
    readme_path = ROOT / "README.md"
    if not readme_path.is_file():
        fail("GOV-CONTENT-011: README.md 不存在")
        return
    text = readme_path.read_text(encoding="utf-8", errors="replace")

    from .gov_emoji import EMOJI_RE
    if EMOJI_RE.search(text):
        m = EMOJI_RE.search(text)
        line_num = text[:m.start()].count("\n") + 1
        fail(f"GOV-CONTENT-011: README.md 含 Emoji 字符（第 {line_num} 行）")

    placeholders = ["TODO", "待补充", "FIXME", "{{"]
    found_ph = [p for p in placeholders if p in text]
    if found_ph:
        fail(f"GOV-CONTENT-011: README.md 含模板占位符: {', '.join(found_ph)}")

    print("  [PASS] README.md 内容时效性检查通过")


# gov-next-decision.sh --create 模板里的占位符 token（单一事实源是那个模板；
# test_placeholder_list_covers_template 钉住二者不漂移）。
# 刻意用白名单而非通用 /<[^>]+>/ 正则：DEC-010 正文合法地使用 <源> 这类尖括号，
# 通用正则会把合法决策误判为未填充模板。
PLACEHOLDER_TOKENS: tuple[str, ...] = (
    "<第 1 步待办>", "<第 2 步待办>", "<关联文件>",
    "<决策标题>", "<一句话描述>", "<决策内容>",
    "<假设>", "<依赖什么>", "<如果错了会怎样>",
    "<方案A>", "<方案B>", "<理由>", "<触发条件>",
)


def check_decision_placeholders() -> None:
    print("\n--- GOV-CONTENT-012: 决策模板占位符 ---")
    dec_dir = DOCS / "ai" / "decisions"
    if not dec_dir.is_dir():
        print("  [SKIP] ai/decisions 目录不存在")
        return
    dec_files = sorted(
        f for f in dec_dir.iterdir()
        if f.is_file() and re.match(r"DEC-\d{3}\.md$", f.name)
    )
    if not dec_files:
        print("  [SKIP] 无决策文件")
        return
    offenders: list[str] = []
    for f in dec_files:
        text = f.read_text(encoding="utf-8", errors="replace")
        hits = [t for t in PLACEHOLDER_TOKENS if t in text]
        if hits:
            offenders.append(f"{f.name}（未填充: {'、'.join(hits)}）")
    if offenders:
        for o in offenders:
            fail(f"GOV-CONTENT-012: 决策含未填充模板占位符，拒绝入库: {o}")
    else:
        print(f"  [PASS] {len(dec_files)} 份决策均无未填充占位符")


def check_all() -> None:
    check_lifecycle_binding()
    check_metrics_timeliness()
    check_changelog()
    check_decisions()
    check_decision_placeholders()
    check_reviews()
    check_memory()
    check_memory_freshness()
    check_readme()
