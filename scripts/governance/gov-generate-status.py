#!/usr/bin/env python3
"""
generate-status.py — 生成 STATUS.md 项目仪表盘 / AI 交接看板。
由 update-status.sh 调用。数据源：context.jsonl、git、docs/ 文件系统。
"""

import json
import os
import re
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CACHE_FILE = ROOT / ".cache" / "context.jsonl"
DOCS = ROOT / "docs"
STATUS_PATH = ROOT / "STATUS.md"


def git(*args: str) -> str:
    try:
        return subprocess.run(
            ["git", "-C", str(ROOT)] + list(args),
            capture_output=True, text=True, timeout=5,
        ).stdout.strip()
    except Exception:
        return ""


def read_cache() -> dict:
    """读取 context.jsonl，返回结构化数据。"""
    data = {
        "handoff": {},
        "current_task": {},
        "known_issues": [],
        "lessons": "",
        "decisions": [],
        "recent_notes": [],
    }
    if not CACHE_FILE.is_file():
        return data
    entries = []
    with open(CACHE_FILE) as f:
        for line in f:
            line = line.strip()
            if line:
                try:
                    entries.append(json.loads(line))
                except json.JSONDecodeError:
                    pass
    entries.sort(key=lambda x: x.get("ts", ""), reverse=True)

    # 最新 handoff
    for e in entries:
        if e.get("type") == "handoff":
            data["handoff"] = {
                "from": e.get("from", "?"),
                "context": e.get("context", ""),
                "next": e.get("next", ""),
                "ts": str(e.get("ts", ""))[:10],
                "files": str(e.get("key_files", "")),
            }
            ls = e.get("lessons", "")
            if ls:
                data["lessons"] = ls.strip().replace("\n", "\n> ")
            break

    # 最新 current-task
    for e in entries:
        if e.get("type") == "current-task":
            data["current_task"] = {
                "task": e.get("task", ""),
                "status": e.get("status", ""),
                "dec": e.get("dec", ""),
                "ts": str(e.get("ts", ""))[:10],
            }
            break

    # known-issues
    for e in entries:
        if e.get("type") == "known-issue" and not e.get("stale"):
            data["known_issues"].append({
                "desc": e.get("desc", "?"),
                "severity": e.get("severity", "?"),
                "workaround": e.get("workaround", ""),
            })

    # 最近 DEC
    for f in sorted((DOCS / "ai" / "decisions").glob("DEC-*.md"), reverse=True)[:3]:
        title = f.read_text(encoding="utf-8", errors="replace").split("\n")[0].replace("# ", "").strip()
        data["decisions"].append({"id": f.stem, "title": title[:60]})

    # 最近 NOTE
    for f in sorted((DOCS / "notes").glob("NOTE-*.md"), reverse=True)[:3]:
        title = f.read_text(encoding="utf-8", errors="replace").split("\n")[0].replace("# ", "").strip()
        data["recent_notes"].append({"id": f.stem, "title": title[:60]})

    return data


def main():
    # Parse args
    check_build = "--check-build" in sys.argv

    data = read_cache()
    now = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")

    # Project name from mkdocs.yml
    project_name = "{{Project Name}}"
    mkdocs_path = ROOT / "mkdocs.yml"
    if mkdocs_path.is_file():
        try:
            with open(mkdocs_path) as f:
                for line in f:
                    m = re.match(r'^site_name:\s*(.+)$', line)
                    if m:
                        pn = m.group(1).strip().strip("'\"")
                        if pn and pn != "{{Project Name}}":
                            project_name = pn
                        break
        except Exception:
            pass
    # Fallback to directory name
    if project_name == "{{Project Name}}":
        project_name = ROOT.name

    # Git info
    branch = git("symbolic-ref", "--short", "HEAD") or "(initializing)"
    last_commit = git("log", "-1", "--oneline") or "none"
    recent = git("log", "--oneline", "-5")

    # Version — project version file takes priority, then package.json
    version = "?"
    ver_file = ROOT / "VERSION"
    if ver_file.is_file():
        try:
            version = ver_file.read_text().strip()
        except Exception:
            pass
    if version == "?":
        tv = ROOT / "scripts" / "governance" / "gov-template-version"
        if tv.is_file():
            try:
                version = tv.read_text().strip()
            except Exception:
                pass
    if version == "?":
        pkg = ROOT / "package.json"
        if pkg.is_file():
            try:
                version = json.loads(pkg.read_text()).get("version", "?")
            except Exception:
                pass

    # Language
    lang = "zh-CN"
    lang_file = ROOT / ".language"
    if lang_file.is_file():
        m = re.match(r"^([a-zA-Z-]+)", lang_file.read_text())
        if m:
            lang = m.group(1)

    # Build (默认跳过，仅 --check-build 时执行)
    build = "跳过"
    build_detail = "skipped"
    if check_build and (ROOT / "package.json").is_file():
        try:
            r = subprocess.run(["npm", "run", "build"], cwd=str(ROOT), capture_output=True, timeout=30)
            if r.returncode != 0:
                build = "失败"
                build_detail = "fail"
            else:
                build = "通过"
                build_detail = "pass"
        except Exception:
            build = "跳过"
            build_detail = "skipped"

    # Hooks status
    hooks = "通过"
    for hook in ("pre-commit", "commit-msg", "pre-push"):
        if not (ROOT / ".git" / "hooks" / hook).is_file():
            hooks = "未安装"
            break

    # Counts
    dec_count = len(list((DOCS / "ai" / "decisions").glob("DEC-[0-9][0-9][0-9].md")))
    note_count = len(list((DOCS / "notes").glob("NOTE-*.md")))
    gc_count = len(list((DOCS / "guides").glob("GC-*.md")))
    ref_count = len(list((DOCS / "reference").glob("REF-*.md")))

    # 构建 markdown
    lines = []
    lines.append("# 项目状态")
    lines.append("")
    lines.append(f"> 由 generate-status.py 自动生成。最后更新: {now}")
    lines.append("> 新 AI 启动时: `cat STATUS.md && bash scripts/session/ses-context-load.sh`")
    lines.append("")
    lines.append("---")
    lines.append("")

    # ── 项目快照 ──
    lines.append("## 项目快照")
    lines.append("")
    lines.append("| 字段 | 值 |")
    lines.append("|------|-----|")
    lines.append(f"| 名称 | {project_name} |")
    lines.append(f"| 版本 | {version} |")
    lines.append(f"| 文档语言 | {lang} |")
    lines.append(f"| Git 分支 | {branch} |")
    lines.append(f"| 最后提交 | {last_commit} |")
    lines.append("| 文档校验 | 运行: `bash scripts/governance/gov-validate.sh` |")
    lines.append("")

    # ── AI 交接 ──
    if data["handoff"].get("context"):
        h = data["handoff"]
        lines.append("## AI 交接")
        lines.append("")
        lines.append("> 上一个 AI session 的上下文。新 AI 启动时**必须阅读此节**。")
        lines.append("")
        lines.append("| 字段 | 值 |")
        lines.append("|------|-----|")
        lines.append(f"| 来自 | {h['from']} |")
        lines.append(f"| 时间 | {h['ts']} |")
        lines.append(f"| 关键文件 | {h['files'][:120]} |")
        lines.append("")
        lines.append("**上下文：**")
        lines.append("")
        lines.append(f"> {h['context']}")
        lines.append("")
        lines.append("**下一步：**")
        lines.append("")
        lines.append(f"> {h['next'] or '（未指定）'}")
        lines.append("")

    # ── 当前任务 ──
    if data["current_task"].get("task"):
        t = data["current_task"]
        lines.append("## 当前任务")
        lines.append("")
        lines.append("| 字段 | 值 |")
        lines.append("|------|-----|")
        lines.append(f"| 任务 | {t['task']} |")
        lines.append(f"| 状态 | {t['status']} |")
        lines.append(f"| 关联 DEC | {t['dec']} |")
        lines.append(f"| 开始时间 | {t['ts']} |")
        lines.append("")

    # ── 最近动态 ──
    if recent:
        lines.append("## 最近动态")
        lines.append("")
        lines.append("最近 5 次提交：")
        lines.append("")
        lines.append("```")
        lines.append(recent)
        lines.append("```")
        lines.append("")

    # ── 工程指标 ──
    lines.append("## 工程指标")
    lines.append("")
    lines.append("| 指标 | 数量 |")
    lines.append("|------|------|")
    lines.append(f"| 决策日志 (DEC) | {dec_count} |")
    lines.append(f"| 设计笔记 (NOTE) | {note_count} |")
    lines.append(f"| 开发指南 (GC) | {gc_count} |")
    lines.append(f"| 技术参考 (REF) | {ref_count} |")
    lines.append("")

    if data["decisions"]:
        lines.append("最近决策：")
        lines.append("")
        for d in data["decisions"]:
            lines.append(f"- [{d['id']}](docs/ai/decisions/{d['id']}.md): {d['title']}")
        lines.append("")

    if data["recent_notes"]:
        lines.append("最近笔记：")
        lines.append("")
        for n in data["recent_notes"]:
            lines.append(f"- [{n['id']}](docs/notes/{n['id']}.md): {n['title']}")
        lines.append("")

    # ── 已知问题 ──
    if data["known_issues"]:
        lines.append("## 已知问题")
        lines.append("")
        for i in data["known_issues"]:
            lines.append(f"- [{i['severity']}] {i['desc']}")
            if i["workaround"]:
                lines.append(f"  - 临时方案: {i['workaround']}")
        lines.append("")

    # ── 健康检查 ──
    lines.append("## 健康检查")
    lines.append("")
    lines.append("| 检查项 | 状态 | 详情 |")
    lines.append("|-------|------|------|")
    lines.append(f"| 构建 | {build} | {build_detail} |")
    validate_ok = (ROOT / "scripts" / "governance" / "gov-validate.sh").is_file()
    lines.append(f"| 文档校验 | {'就绪' if validate_ok else '缺失'} | {'运行: bash scripts/governance/gov-validate.sh' if validate_ok else '校验脚本未安装'} |")
    lines.append(f"| Git 钩子 | {hooks} | 安装: bash scripts/bootstrap/bs-install-hooks.sh |")
    lines.append("")

    # ── 反思 ──
    if data["lessons"]:
        lines.append("## 反思")
        lines.append("")
        lines.append("> 上次 AI session 的经验教训。新 AI 启动时必读。")
        lines.append("")
        lines.append(data["lessons"])
        lines.append("")

    # 写入
    STATUS_PATH.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f" STATUS.md updated: {now}")


if __name__ == "__main__":
    main()
