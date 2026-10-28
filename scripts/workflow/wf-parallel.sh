#!/usr/bin/env bash
# ============================================================
# scripts/workflow/wf-parallel.sh
# 可并行批次规划 —— 从任务板机械算出"哪几条能同时开工"
#
# 用法:
#   bash scripts/workflow/wf-parallel.sh plan [--json] [--limit N] [--root <仓库根>]
#
# 筛选条件（全部满足才进入候选）:
#   status=pending、acceptance.status=ready、target_files 非空
# 排除条件:
#   已被锁定（.ai/locks/<ID> 存在，或 .ai/task-lock.json 中未过期条目）
# 分批规则（贪心）:
#   两条任务 target_files 无交集才可同批；模块不同或同模块但文件不相交均可
# 退出码: 0 正常 / 2 参数错误
# 约束:   只读任务板与锁文件，不修改任何状态
# ============================================================
set -uo pipefail

GOV_DEFAULT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
export GOV_DEFAULT_ROOT

python3 - "$@" <<'PYEOF'
"""可并行批次规划主逻辑：筛选 -> 排锁 -> 文件不重叠贪心分批。"""
import json
import os
import sys
import time
from pathlib import Path

EXIT_OK = 0
EXIT_USAGE = 2
DEFAULT_LIMIT = 3

USAGE = """用法: wf-parallel.sh plan [--json] [--limit N] [--root <仓库根>]

  --limit N  每批最多 N 条任务（默认 3，避免机器过载）
  --json     输出机器可读结果
  --root     覆盖仓库根（默认取脚本所在仓库）"""


def parse_args(argv):
    """解析参数：必须显式给出 plan 子命令。"""
    root = Path(os.environ.get("GOV_DEFAULT_ROOT", os.getcwd())).resolve()
    json_mode = False
    limit = DEFAULT_LIMIT
    command = None
    i = 0
    while i < len(argv):
        arg = argv[i]
        if arg == "--root":
            i += 1
            if i >= len(argv):
                return None, None, None, None, "--root 需要路径参数"
            root = Path(argv[i]).resolve()
        elif arg == "--json":
            json_mode = True
        elif arg == "--limit":
            i += 1
            if i >= len(argv):
                return None, None, None, None, "--limit 需要数字参数"
            try:
                limit = int(argv[i])
            except ValueError:
                return None, None, None, None, "--limit 必须是正整数: " + argv[i]
            if limit < 1:
                return None, None, None, None, "--limit 必须大于 0"
        elif arg in ("-h", "--help"):
            print(USAGE)
            sys.exit(EXIT_OK)
        elif arg.startswith("-"):
            return None, None, None, None, "未知参数: " + arg
        elif command is None:
            command = arg
        else:
            return None, None, None, None, "未知子命令参数: " + arg
        i += 1
    if command != "plan":
        return None, None, None, None, "只支持 plan 子命令"
    return command, root, limit, json_mode, None


def load_tasks(root):
    """读取任务列表；支持 v1 (扁平) 和 v2 (Layer 2) 结构。"""
    tasks_file = root / ".ai" / "tasks.json"
    if not tasks_file.exists():
        raise SystemExit("任务板不存在: " + str(tasks_file))
    with tasks_file.open(encoding="utf-8") as f:
        data = json.load(f)
    
    # v2 结构: {version: 2, schema: "task-flow@v2", tasks: [...]}
    # v1 结构: {version: 1, tasks: [...]} 或直接是数组
    if isinstance(data, dict):
        if data.get("version") == 2 and data.get("schema") == "task-flow@v2":
            return data.get("tasks", [])
        return data.get("tasks", []) if "tasks" in data else data
    return data if isinstance(data, list) else []


def is_candidate(task):
    """候选条件：工程任务 status=ready + 有 acceptance 定义 + 声明了产物文件。"""
    # 新结构: status in ["ready", "pending"]; acceptance 是对象而非有 status 字段
    status = task.get("status", "")
    if status not in ("ready", "pending"):
        return False
    # acceptance 必须是非空对象（有 verify_commands 或 definition_of_done）
    acceptance = task.get("acceptance")
    if not isinstance(acceptance, dict) or not acceptance:
        return False
    # 状态为 draft 的任务不进入候选池
    if acceptance.get("status") == "draft":
        return False
    return bool(task.get("target_files"))


def read_lock_owner(lock_path):
    """尽量读出锁持有者：支持 JSON（session/owner/assigned_to）或纯文本。"""
    try:
        text = lock_path.read_text(encoding="utf-8").strip()
    except OSError:
        return "未知"
    if not text:
        return "未知"
    try:
        data = json.loads(text)
    except ValueError:
        return text.splitlines()[0].strip() or "未知"
    if isinstance(data, dict):
        for key in ("session", "owner", "assigned_to", "session_id"):
            value = data.get(key)
            if value:
                return str(value)
    return "未知"


def lock_holders(root):
    """收集任务锁：.ai/locks/<ID> 文件优先，其次兼容 .ai/task-lock.json。"""
    holders = {}
    locks_dir = root / ".ai" / "locks"
    if locks_dir.is_dir():
        for entry in sorted(locks_dir.iterdir()):
            if entry.is_file():
                holders[entry.name] = read_lock_owner(entry)

    legacy = root / ".ai" / "task-lock.json"
    if legacy.is_file():
        now = time.time()
        try:
            data = json.loads(legacy.read_text(encoding="utf-8"))
        except (OSError, ValueError):
            data = {}
        if isinstance(data, dict):
            for task_id, info in data.items():
                if not isinstance(info, dict):
                    continue
                if info.get("expires", 0) > now and task_id not in holders:
                    holders[task_id] = str(info.get("session", "未知"))
    return holders


def normalize_files(task):
    """task 的文件集合，用于判断重叠。"""
    return {str(item) for item in (task.get("target_files") or [])}


def compatible(task, batch):
    """同批兼容判定：与批内每条任务的文件集合都不相交。"""
    files = normalize_files(task)
    for other in batch:
        if files & normalize_files(other):
            return False
    return True


def task_module(task):
    """任务模块名，缺省从 target_files 路径推断。"""
    module = task.get("module")
    if module:
        return str(module)
    for rel in task.get("target_files") or []:
        parts = Path(rel).parts
        if "feature" in parts:
            idx = parts.index("feature")
            if idx + 1 < len(parts):
                return parts[idx + 1]
    return "misc"


def branch_name(task):
    """建议分支名：feat/t072-auth（避免斜杠后出现大写 ID）。"""
    return "feat/%s-%s" % (str(task["id"]).lower(), task_module(task))


def batch_branch_name(task):
    """批次分支名：batch/B-xxx（任务归属批次）"""
    b = task.get("batch", "")
    if b:
        return "batch/%s" % b
    return ""


def build_batches(candidates, limit):
    """贪心分批：按顺序塞进第一个仍有空位且文件不冲突的批。"""
    batches = []
    for task in candidates:
        placed = False
        for batch in batches:
            if len(batch["tasks"]) >= limit:
                continue
            if not compatible(task, batch["tasks"]):
                continue
            batch["tasks"].append(task)
            placed = True
            break
        if not placed:
            batches.append({"tasks": [task]})
    return batches


def annotate(batch, index):
    """为批次内任务补充分支/子代理建议与同模块提示。"""
    tasks = batch["tasks"]
    modules = [task_module(t) for t in tasks]
    # 批次内任务可能来自不同的 batch 字段，取第一个非空作为批次分支
    batch_branches = [batch_branch_name(t) for t in tasks if batch_branch_name(t)]
    batch_branch = batch_branches[0] if batch_branches else ""
    result = []
    for slot, task in enumerate(tasks, start=1):
        module_name = modules[slot - 1]
        result.append(
            {
                "id": task["id"],
                "module": module_name,
                "title": task.get("description", ""),
                "wave": task.get("wave"),
                "branch": branch_name(task),
                "batch_branch": batch_branch,
                "task_batch": task.get("batch", ""),
                "subagent": "sub-%s" % str(task["id"]).lower(),
                "slot": slot,
                "target_files": list(task.get("target_files") or []),
                "same_module_as_batch": module_name in modules[: slot - 1],
            }
        )
    return {"batch": index, "size": len(result), "tasks": result, "batch_branch": batch_branch}


def render_human(payload):
    """人类可读批次建议。"""
    batches = payload["batches"]
    print("=== 可并行批次规划 ===")
    print("候选 %d 条 / 已排 %d 批 / 每批上限 %d" % (
        payload["summary"]["candidate_count"], len(batches), payload["limit"]))
    print("")
    if not batches:
        print("无可并行任务（检查 pending + acceptance.status=ready + target_files）")
    for batch in batches:
        batch_branch = batch.get("batch_branch", "")
        header = "批次%d" % batch["batch"]
        if batch_branch:
            header += " -> %s" % batch_branch
        print("%s: %s" % (header, ", ".join(
            "%s(%s)" % (t["id"], batch_branch if t.get("task_batch") else t["branch"]) for t in batch["tasks"])))
        for task in batch["tasks"]:
            task_batch = task.get("task_batch", "")
            # 显示分支：若属于批次则显示批次分支，否则显示单任务分支
            display_branch = batch_branch if task_batch else task["branch"]
            line = "  - %s | 模块=%s | 分支=%s | 子代理=%s | 文件%d个" % (
                task["id"], task["module"], display_branch, task["subagent"], len(task["target_files"]))
            if task_batch:
                line += " | 批次=%s" % task_batch
            print(line)
            if task["same_module_as_batch"]:
                print("    提示: 与同批任务同模块，仅文件不相交，注意回归范围")
        print("")
    excluded = payload["excluded"]
    if excluded:
        print("未纳入 %d 条:" % len(excluded))
        for item in excluded:
            print("  - %s（%s）" % (item["id"], item["reason"]))
    print("")
    print("说明: 只读规划，未修改任何状态")


def main(argv):
    command, root, limit, json_mode, error = parse_args(argv)
    if error:
        print(error, file=sys.stderr)
        print(USAGE, file=sys.stderr)
        return EXIT_USAGE

    tasks = load_tasks(root)
    holders = lock_holders(root)

    candidates = []
    excluded = []
    for task in tasks:
        if not is_candidate(task):
            continue
        task_id = task.get("id")
        if task_id in holders:
            excluded.append({"id": task_id, "reason": "已锁定，持有者 %s" % holders[task_id]})
            continue
        candidates.append(task)

    # 稳定排序：先波次再 ID，保证每次规划结果可复现
    candidates.sort(key=lambda t: (t.get("wave") or 99, str(t.get("id"))))
    batches = build_batches(candidates, limit)

    payload = {
        "limit": limit,
        "summary": {
            "candidate_count": len(candidates),
            "batch_count": len(batches),
            "excluded_count": len(excluded),
        },
        "batches": [annotate(batch, idx) for idx, batch in enumerate(batches, start=1)],
        "excluded": excluded,
    }

    if json_mode:
        print(json.dumps(payload, ensure_ascii=False, indent=2))
    else:
        render_human(payload)
    return EXIT_OK


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
PYEOF
