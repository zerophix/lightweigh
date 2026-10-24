#!/usr/bin/env python3
"""GOV-PROTO-001~003: AI 协议检查"""
import json, os, re
from datetime import datetime, timezone
from pathlib import Path
from .gov_validate_core import ROOT, DOCS, MEMORY_FILE, errors, fail


def check_protocol() -> None:
    # DEC-063: 纯删除类 push（git push --delete）等零代码动作豁免墙钟门禁（仅 001/002）。
    # pre-push 钩子检测到全删除 refspec 时注入该环境变量；
    # 003/004（任务板健康）与 session 边界（session-end / local-ci）仍强制。
    skip_wallclock = os.environ.get("GOV_SKIP_WALLCLOCK") == "1"
    if skip_wallclock:
        print()
        print("--- GOV-PROTO-001/002: [SKIP] GOV_SKIP_WALLCLOCK=1（零代码动作豁免，DEC-063）---")
    _check_wallclock_relays(skip_wallclock)
    _check_task_board()


def _check_wallclock_relays(skip: bool) -> None:
    """GOV-PROTO-001/002（墙钟接力门禁）。DEC-063: skip=True 时跳过（零代码动作豁免）。"""
    if skip:
        return
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

def _check_task_board() -> None:
    """GOV-PROTO-003/004（任务板健康，不受墙钟豁免影响）。"""
    print()
    print("--- GOV-PROTO-003: 僵尸任务检测 ---")
    tasks_file = ROOT / ".ai" / "tasks.json"
    if tasks_file.is_file():
        try:
            tasks_data = json.loads(tasks_file.read_text(encoding="utf-8"))
            tasks = tasks_data.get("tasks", [])
            # 僵尸 = 认领后 24 小时无任何看板更新。
            # DEC-060: 原实现只判 status==in_progress 就 fail，消息却写「超过 24 小时」，
            # 声明与行为不符 —— 结果是任务一旦被认领就无法通过校验，
            # pre-commit/pre-push 都跑 gov-validate，等于禁止在工作过程中提交。
            # 改为按 updated_at（回退 created_at）计算实际停滞时长。
            zombie = []
            for t in tasks:
                if t.get("status") != "in_progress":
                    continue
                ts = t.get("updated_at") or t.get("created_at")
                if not ts:
                    zombie.append(t.get("id", "?"))  # 无法判定停滞时长 → 保守当僵尸
                    continue
                try:
                    dt = datetime.fromisoformat(str(ts).replace("Z", "+00:00"))
                    if dt.tzinfo is None:
                        dt = dt.replace(tzinfo=timezone.utc)
                    hours = (datetime.now(timezone.utc) - dt).total_seconds() / 3600
                    if hours > 24:
                        zombie.append(t.get("id", "?"))
                except (ValueError, TypeError):
                    zombie.append(t.get("id", "?"))
            if zombie:
                fail(f"GOV-PROTO-003: 存在僵尸任务（in_progress 超过 24 小时无看板更新）: {', '.join(zombie)}")
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
        except (json.JSONDecodeError, Exception):
            pass


def check_all() -> None:
    check_protocol()
