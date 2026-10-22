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
from typing import Optional

ROOT = Path(__file__).resolve().parents[2]
CACHE_FILE = ROOT / ".cache" / "context.jsonl"
DOCS = ROOT / "docs"


def doc_title(path: Path) -> str:
    """取文档首个 H1 作标题；跳过 YAML 前置元数据。

    gov-next-decision.sh --create 会写入 task_groups 前置元数据，直接取第一行会得到
    "---" 而不是真实标题（DEC-019 修复）。
    """
    lines = path.read_text(encoding="utf-8", errors="replace").split("\n")
    idx = 0
    if lines and lines[0].strip() == "---":
        for i in range(1, len(lines)):
            if lines[i].strip() == "---":
                idx = i + 1
                break
    for line in lines[idx:]:
        if line.startswith("#"):
            return line.lstrip("#").strip()
    return path.stem
STATUS_PATH = ROOT / "STATUS.md"


def git(*args: str) -> str:
    try:
        return subprocess.run(
            ["git", "-C", str(ROOT)] + list(args),
            capture_output=True, text=True, timeout=5,
        ).stdout.strip()
    except Exception:
        return ""


LOCAL_CI_EVIDENCE = ROOT / ".cache" / "local-ci-last.json"


def read_local_ci_evidence() -> Optional[dict]:
    """读取 gov-local-ci 落盘的机读结果（DEC-051）。"""
    if not LOCAL_CI_EVIDENCE.is_file():
        return None
    try:
        data = json.loads(LOCAL_CI_EVIDENCE.read_text(encoding="utf-8"))
    except (json.JSONDecodeError, OSError):
        return None
    if not isinstance(data, dict) or data.get("schema_version") != 1:
        return None
    if data.get("source") != "gov-local-ci":
        return None
    return data


def read_actions_evidence(branch: str) -> Optional[dict]:
    """可选：gh run list 最近一次 Actions（无 Pro 时仍可读；失败则忽略）。"""
    if not branch or branch in ("HEAD", "unknown", "(initializing)"):
        return None
    try:
        r = subprocess.run(
            [
                "gh", "run", "list",
                "--branch", branch,
                "--limit", "1",
                "--json", "conclusion,status,displayTitle,url,headSha,updatedAt,databaseId,name,workflowName",
            ],
            cwd=str(ROOT),
            capture_output=True,
            text=True,
            timeout=12,
        )
    except (FileNotFoundError, subprocess.TimeoutExpired, OSError):
        return None
    if r.returncode != 0 or not r.stdout.strip():
        return None
    try:
        rows = json.loads(r.stdout)
    except json.JSONDecodeError:
        return None
    if not rows:
        return None
    return rows[0]


def format_local_ci_row(ev: Optional[dict]) -> tuple:
    """返回 (状态中文, 详情)。"""
    if not ev:
        return "无证据", "尚无 `.cache/local-ci-last.json` — 请运行 `bash scripts/governance/gov-local-ci.sh`"
    result = ev.get("result", "?")
    sha = str(ev.get("git_sha", ""))[:7]
    finished = ev.get("finished_at", "?")
    branch = ev.get("branch", "?")
    steps = ev.get("steps") or []
    step_summary = ",".join(
        f"{s.get('name', '?')}:{'ok' if s.get('ok') else 'fail'}" for s in steps
    ) or "no-steps"
    if result == "pass":
        return "通过", f"local-ci PASS @ {finished} sha={sha} branch={branch} [{step_summary}]"
    if result == "fail":
        return "失败", f"local-ci FAIL @ {finished} sha={sha} branch={branch} [{step_summary}]"
    return "未知", f"local-ci result={result} @ {finished} sha={sha}"


def format_actions_row(run: Optional[dict]) -> tuple:
    if not run:
        return "无数据", "gh 不可用或无最近 run"
    conclusion = (run.get("conclusion") or run.get("status") or "?").lower()
    title = run.get("workflowName") or run.get("name") or run.get("displayTitle") or "Actions"
    sha = str(run.get("headSha") or "")[:7]
    when = run.get("updatedAt") or "?"
    url = run.get("url") or ""
    detail = f"{title} @ {when} sha={sha}"
    if url:
        detail += f" {url}"
    if conclusion in ("success",):
        return "通过", detail
    if conclusion in ("failure", "cancelled", "timed_out", "startup_failure"):
        return "失败", detail
    if conclusion in ("in_progress", "queued", "pending", "waiting", "requested"):
        return "进行中", detail
    return conclusion or "未知", detail



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
        title = doc_title(f)
        data["decisions"].append({"id": f.stem, "title": title[:60]})

    # 最近 NOTE
    for f in sorted((DOCS / "notes").glob("NOTE-*.md"), reverse=True)[:3]:
        title = doc_title(f)
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

    # 构建/门禁证据（DEC-051）：优先读 local-ci 落盘结果；绝不对本 Android 仓跑 npm build
    _ = check_build  # 保留 CLI 兼容；不再触发 npm
    local_ci_ev = read_local_ci_evidence()
    build, build_detail = format_local_ci_row(local_ci_ev)
    actions_run = read_actions_evidence(branch)
    actions_status, actions_detail = format_actions_row(actions_run)

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
    lines.append(f"| 本地门禁 (gov-local-ci) | {build} | {build_detail} |")
    lines.append(f"| GitHub Actions | {actions_status} | {actions_detail} |")
    validate_ok = (ROOT / "scripts" / "governance" / "gov-validate.sh").is_file()
    # 若 local-ci 证据含 validate 步骤，标注实测结果
    validate_detail = "运行: bash scripts/governance/gov-validate.sh"
    validate_label = "就绪" if validate_ok else "缺失"
    if local_ci_ev:
        for s in local_ci_ev.get("steps") or []:
            if s.get("name") == "gov-validate":
                validate_label = "通过" if s.get("ok") else "失败"
                validate_detail = (
                    f"来自 local-ci @ {local_ci_ev.get('finished_at', '?')} "
                    f"sha={str(local_ci_ev.get('git_sha', ''))[:7]}"
                )
                break
    lines.append(f"| 文档校验 | {validate_label} | {validate_detail} |")
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
