#!/usr/bin/env python3
"""GOV-PROTO-003/004: 任务板健康检查（001/002 墙钟规则已于 DEC-064 退役）"""
import json, re
from datetime import datetime, timezone
from pathlib import Path
from .gov_validate_core import ROOT, errors, fail


def check_protocol() -> None:
    """GOV-PROTO-003/004（任务板健康）。

    DEC-064: 原 001/002（收件箱 48h / session-end 24h）墙钟门禁已退役——
    它们不保护任何产物，只按时间惩罚行为；交接质量由内容决定，不由计时器决定。
    接力仍写在 docs/ai/inbox/，但不再拦截任何 git 操作。
    """
    _check_task_board()


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
