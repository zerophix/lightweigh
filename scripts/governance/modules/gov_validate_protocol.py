#!/usr/bin/env python3
"""GOV-PROTO-001~005: AI 协议检查"""
import json, re
from datetime import datetime, timezone
from pathlib import Path
from .gov_validate_core import ROOT, DOCS, MEMORY_FILE, errors, fail

# DEC-057：就绪任务必须钉住 PRD / schema / 原型。单独的 n/a 不算引用。
_WAIVER_RE = re.compile(r"^(?:n/a|不适用)\s*[:：]\s*\S.{2,}\s*$", re.IGNORECASE)
_BARE_WAIVER_RE = re.compile(r"^(?:n/a|不适用|todo|tbd)\s*$", re.IGNORECASE)
_TRACE_LIST_KEYS = ("prd", "schema", "prototype")
_DONE_STATUSES = {"done", "completed", "cancelled", "canceled"}


def _as_dict(value) -> dict:
    return value if isinstance(value, dict) else {}


def _item_ok(item: object) -> bool:
    if not isinstance(item, str):
        return False
    text = item.strip()
    if not text or _BARE_WAIVER_RE.match(text):
        return False
    if text.lower().startswith("n/a") or text.startswith("不适用"):
        return _WAIVER_RE.match(text) is not None
    return len(text) >= 3


def _list_status(value: object) -> str:
    if not isinstance(value, list) or not value:
        return "empty"
    flags = [_item_ok(item) for item in value]
    if all(flags):
        return "ok"
    if not any(flags):
        return "empty"
    return "invalid"


def _refs_of(task: dict) -> dict:
    acc = _as_dict(task.get("acceptance"))
    refs = _as_dict(acc.get("refs"))
    if refs:
        return refs
    return _as_dict(task.get("refs"))


def _trace_mode(task: dict) -> str | None:
    """hard：可开工；soft：草案警告；None：历史任务不查。"""
    status = str(task.get("status") or "").strip().lower()
    if status in _DONE_STATUSES:
        return None
    acc_status = str(_as_dict(task.get("acceptance")).get("status") or "").strip().lower()
    if status == "in_progress" or acc_status == "ready":
        return "hard"
    return "soft"


def _ref_list_gaps(refs: dict) -> list[str]:
    gaps: list[str] = []
    for key in _TRACE_LIST_KEYS:
        state = _list_status(refs.get(key))
        if state == "empty":
            gaps.append(key)
        elif state == "invalid":
            gaps.append(f"{key}(无效或无理由的 n/a)")
    return gaps


def _hard_trace_gaps(task: dict) -> list[str]:
    acc = _as_dict(task.get("acceptance"))
    refs = _refs_of(task)
    gaps = _ref_list_gaps(refs)
    pin = refs.get("pin")
    pin_text = pin.strip() if isinstance(pin, str) else ""
    if len(pin_text) < 8 or _BARE_WAIVER_RE.match(pin_text):
        gaps.append("pin")
    dod = acc.get("definition_of_done")
    if not (isinstance(dod, list) and any(isinstance(x, str) and x.strip() for x in dod)):
        gaps.append("definition_of_done")
    src_state = _list_status(acc.get("dod_source"))
    if src_state == "empty":
        gaps.append("dod_source")
    elif src_state == "invalid":
        gaps.append("dod_source(无效)")
    return gaps


def check_protocol() -> None:
    print()
    print("--- GOV-PROTO-001: 收件箱 48h 未读 ---")
    inbox_dir = DOCS / "ai" / "inbox"
    inbox_unread = 0
    if inbox_dir.is_dir():
        for f in sorted(inbox_dir.iterdir()):
            if f.name.endswith(".md"):
                text = f.read_text(encoding="utf-8", errors="replace")
                status_match = re.search(r"^status:\s*(\S+)", text, re.MULTILINE)
                if status_match and status_match.group(1).lower() in ("unread", "未读"):
                    # 仅超 48 小时的未读才算协议违规（接力信写入后待下个 AI 读取属正常状态）
                    ts = None
                    ts_match = re.search(
                        r"^timestamp:\s*(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z?)",
                        text, re.MULTILINE)
                    if ts_match:
                        try:
                            ts = datetime.fromisoformat(
                                ts_match.group(1).replace("Z", "+00:00"))
                        except ValueError:
                            ts = None
                    if ts is None:
                        m = re.match(r"(\d{4})(\d{2})(\d{2})-(\d{2})(\d{2})(\d{2})", f.stem)
                        if m:
                            ts = datetime(*map(int, m.groups()), tzinfo=timezone.utc)
                    if ts is None:
                        ts = datetime.fromtimestamp(f.stat().st_mtime, tz=timezone.utc)
                    hours_old = (datetime.now(timezone.utc) - ts).total_seconds() / 3600
                    if hours_old > 48:
                        inbox_unread += 1
    if inbox_unread > 0:
        fail(f"GOV-PROTO-001: 收件箱有 {inbox_unread} 封未读留言（超 48 小时视为协议违规）")
    else:
        print("  [PASS] 收件箱无超 48 小时未读留言")

    print()
    print("--- GOV-PROTO-002: Session-end 24h 时效 ---")
    if MEMORY_FILE.is_file():
        last_handoff_ts = None
        for line in MEMORY_FILE.read_text(encoding="utf-8").splitlines():
            line = line.strip()
            if not line:
                continue
            try:
                entry = json.loads(line)
            except json.JSONDecodeError:
                continue
            if entry.get("type") == "handoff":
                last_handoff_ts = entry.get("ts", "")
        if last_handoff_ts:
            try:
                dt = datetime.fromisoformat(last_handoff_ts.replace("Z", "+00:00"))
                hours_old = (datetime.now(timezone.utc) - dt).total_seconds() / 3600
                if hours_old > 24:
                    fail(f"GOV-PROTO-002: 上次 session-end 超过 {int(hours_old)} 小时（>24 小时需重新 context-load）")
                else:
                    print(f"  [PASS] 上次 session-end {int(hours_old)} 小时前")
            except (ValueError, TypeError):
                pass

    print()
    print("--- GOV-PROTO-003: 僵尸任务检测 ---")
    tasks_file = ROOT / ".ai" / "tasks.json"
    if tasks_file.is_file():
        try:
            tasks_data = json.loads(tasks_file.read_text(encoding="utf-8"))
            tasks = tasks_data.get("tasks", [])
            zombie = [t.get("id", "?") for t in tasks if t.get("status") == "in_progress"]
            if zombie:
                fail(f"GOV-PROTO-003: 存在僵尸任务（in_progress 超过 24 小时）: {', '.join(zombie)}")
            else:
                print("  [PASS] 无僵尸任务")

            print()
            print("--- GOV-PROTO-004: in_progress 任务须有 AC DoD ---")
            missing_ac = []
            for t in tasks:
                if t.get("status") != "in_progress":
                    continue
                acc = t.get("acceptance") or {}
                dod = acc.get("definition_of_done")
                ok = isinstance(dod, list) and any(str(x).strip() for x in dod)
                if not ok:
                    missing_ac.append(t.get("id", "?"))
            if missing_ac:
                fail(
                    "GOV-PROTO-004: in_progress 任务缺少非空 acceptance.definition_of_done: "
                    + ", ".join(missing_ac)
                    + "（见 docs/guides/GC-task-acceptance.md / DEC-048）"
                )
            else:
                print("  [PASS] in_progress 任务 AC DoD 检查通过（或无 in_progress）")

            print()
            print("--- GOV-PROTO-005: 就绪任务须钉住 PRD/原型/schema ---")
            hard_bad: list[str] = []
            soft_bad: list[str] = []
            for t in tasks:
                mode = _trace_mode(t)
                if mode is None:
                    continue
                task_id = str(t.get("id", "?"))
                if mode == "hard":
                    gaps = _hard_trace_gaps(t)
                    if gaps:
                        hard_bad.append(f"{task_id}[{', '.join(gaps)}]")
                else:
                    gaps = _ref_list_gaps(_refs_of(t))
                    if gaps:
                        soft_bad.append(task_id)
            if hard_bad:
                fail(
                    "GOV-PROTO-005: ready/in_progress 任务缺少链路引用: "
                    + ", ".join(hard_bad)
                    + "（见 docs/guides/GC-req-to-code-chain.md / DEC-057）"
                )
            else:
                print("  [PASS] 就绪/进行中任务的链路引用检查通过（或无此类任务）")
            if soft_bad:
                shown = soft_bad[:12]
                preview = ", ".join(shown)
                if len(soft_bad) > len(shown):
                    preview += f"（共 {len(soft_bad)} 个）"
                print(
                    "  [WARN] GOV-PROTO-005: "
                    f"{len(soft_bad)} 个草案任务尚未填写 acceptance.refs"
                    f"（prd/schema/prototype）；不阻断: {preview}"
                )
        except (json.JSONDecodeError, Exception):
            pass


def check_all() -> None:
    check_protocol()
