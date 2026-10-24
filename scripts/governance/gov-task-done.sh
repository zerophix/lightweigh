#!/usr/bin/env bash
# ============================================================
# scripts/governance/gov-task-done.sh
# 任务完成定义（DoD）机械校验 —— 让"做完了"有证据，而不是声称
#
# 用法:
#   bash scripts/governance/gov-task-done.sh <T-XXX> [--dry] [--json] [--root <仓库根>]
#
# 校验内容:
#   1. target_files 每一项真实存在（缺失即 FAIL）
#   2. 任务带 prototype_ref 时执行 gov-prototype-verify.py <T-XXX> --strict
#   3. acceptance.verify_commands 逐条执行（单条超时 900 秒，工作目录=仓库根）
#
# 退出码: 0 全部通过 / 1 存在失败 / 2 任务不存在或参数错误
# 约束:   只校验，不修改任务板状态（置 completed 由合并队列/收尾负责）
# ============================================================
set -uo pipefail

GOV_DEFAULT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
export GOV_DEFAULT_ROOT

python3 - "$@" <<'PYEOF'
"""任务 DoD 校验主逻辑：文件存在性 + 原型闭环 + verify_commands。"""
import json
import os
import subprocess
import sys
import time
from pathlib import Path

EXIT_OK = 0
EXIT_FAIL = 1
EXIT_USAGE = 2
CMD_TIMEOUT = 900
TAIL_LINES = 15

USAGE = """用法: gov-task-done.sh <T-XXX> [--dry] [--json] [--root <仓库根>]

  --dry    只打印将要执行的命令与文件检查结果，不执行任何 verify_command
  --json   输出机器可读结果
  --root   覆盖仓库根（默认取脚本所在仓库）

退出码: 0 通过 / 1 失败 / 2 任务不存在或参数错误"""


def parse_args(argv):
    """解析参数：任务号 + 三个开关。"""
    root = Path(os.environ.get("GOV_DEFAULT_ROOT", os.getcwd())).resolve()
    task_id = None
    dry = False
    json_mode = False
    i = 0
    while i < len(argv):
        arg = argv[i]
        if arg == "--root":
            i += 1
            if i >= len(argv):
                return None, None, None, None, "--root 需要路径参数"
            root = Path(argv[i]).resolve()
        elif arg == "--dry":
            dry = True
        elif arg == "--json":
            json_mode = True
        elif arg in ("-h", "--help"):
            print(USAGE)
            sys.exit(EXIT_OK)
        elif arg.startswith("-"):
            return None, None, None, None, "未知参数: " + arg
        elif task_id is not None:
            return None, None, None, None, "只接受一个任务号: " + arg
        else:
            task_id = arg
        i += 1
    if not task_id:
        return None, None, None, None, "缺少任务号（如 T-072）"
    return task_id, root, dry, json_mode, None


def load_task(root, task_id):
    """从 .ai/tasks.json 读取指定任务；找不到返回 (None, 错误说明)。"""
    tasks_file = root / ".ai" / "tasks.json"
    if not tasks_file.exists():
        return None, "任务板不存在: " + str(tasks_file)
    try:
        with tasks_file.open(encoding="utf-8") as f:
            data = json.load(f)
    except (OSError, ValueError) as exc:
        return None, "任务板解析失败: " + str(exc)
    tasks = data.get("tasks", []) if isinstance(data, dict) else data
    for task in tasks:
        if task.get("id") == task_id:
            return task, None
    return None, "任务不存在: " + task_id


def run_command(command, cwd, timeout=CMD_TIMEOUT):
    """执行单条校验命令，返回 (退出码, 输出文本, 耗时毫秒)。"""
    started = time.time()
    try:
        proc = subprocess.run(
            command,
            shell=True,
            cwd=str(cwd),
            capture_output=True,
            text=True,
            timeout=timeout,
        )
        output = (proc.stdout or "") + (proc.stderr or "")
        code = proc.returncode
    except subprocess.TimeoutExpired:
        code = 124
        output = "超时（%d 秒）：%s" % (timeout, command)
    except Exception as exc:  # noqa: BLE001 - 校验脚本需报告任何执行失败
        code = 125
        output = "执行异常：%s" % exc
    elapsed_ms = int((time.time() - started) * 1000)
    return code, output, elapsed_ms


def tail(text, lines=TAIL_LINES):
    """截取输出末尾若干行，避免刷屏。"""
    rows = [row for row in text.strip().splitlines() if row.strip()]
    return "\n".join(rows[-lines:])


def collect_checks(task, root, dry):
    """汇总三类校验项，返回 checks 列表（dry 时命令标记为 PLAN）。"""
    checks = []
    target_files = task.get("target_files") or []

    if not target_files:
        checks.append(
            {
                "type": "file",
                "name": "(target_files)",
                "status": "FAIL",
                "detail": "任务未声明 target_files，无法校验产物",
                "duration_ms": 0,
            }
        )
    for rel in target_files:
        path = root / rel
        checks.append(
            {
                "type": "file",
                "name": rel,
                "status": "PASS" if path.exists() else "FAIL",
                "detail": "存在" if path.exists() else "缺失: " + str(path),
                "duration_ms": 0,
            }
        )

    if task.get("prototype_ref"):
        script = root / "scripts" / "governance" / "gov-prototype-verify.py"
        command = "python3 scripts/governance/gov-prototype-verify.py %s --strict" % task["id"]
        checks.append(
            build_command_check(command, root, dry, kind="prototype", guard=script)
        )

    commands = (task.get("acceptance") or {}).get("verify_commands") or []
    for command in commands:
        checks.append(build_command_check(command, root, dry, kind="command"))

    return checks


def build_command_check(command, root, dry, kind, guard=None):
    """构造单个命令校验项；dry 只登记不执行。"""
    if guard is not None and not guard.exists():
        return {
            "type": kind,
            "name": command,
            "status": "FAIL",
            "detail": "校验脚本缺失: " + str(guard),
            "duration_ms": 0,
        }
    if dry:
        return {
            "type": kind,
            "name": command,
            "status": "PLAN",
            "detail": "待执行（dry-run 未执行）",
            "duration_ms": 0,
        }
    code, output, elapsed_ms = run_command(command, root)
    detail = "退出码 %d" % code
    if code != 0 and output.strip():
        detail += "\n" + tail(output)
    return {
        "type": kind,
        "name": command,
        "status": "PASS" if code == 0 else "FAIL",
        "detail": detail,
        "duration_ms": elapsed_ms,
    }


def render_human(task_id, checks, dry):
    """人类可读输出：逐项 PASS/FAIL + 总判定。"""
    print("=== 任务完成定义校验: %s ===" % task_id)
    if dry:
        print("模式: dry-run（文件检查照做，命令只登记不执行）")
    print("")
    for item in checks:
        label = {"PASS": "[PASS]", "FAIL": "[FAIL]", "PLAN": "[PLAN]"}.get(item["status"], "[????]")
        line = "%s %-9s %s" % (label, item["type"], item["name"])
        if item.get("duration_ms"):
            line += "  (%dms)" % item["duration_ms"]
        print(line)
        if item["status"] == "FAIL" and item.get("detail"):
            for row in item["detail"].splitlines():
                print("         " + row)
    print("")
    failed = [c for c in checks if c["status"] == "FAIL"]
    if dry:
        print("总判定: DRY-RUN（未执行命令，退出码 0）")
        return EXIT_OK
    if failed:
        print("总判定: FAIL（%d 项未通过）" % len(failed))
        return EXIT_FAIL
    print("总判定: PASS（%d 项全部通过）" % len(checks))
    return EXIT_OK


def main(argv):
    task_id, root, dry, json_mode, error = parse_args(argv)
    if error:
        if json_mode:
            print(json.dumps({"ok": False, "error": error}, ensure_ascii=False))
        else:
            print(error, file=sys.stderr)
            print(USAGE, file=sys.stderr)
        return EXIT_USAGE

    task, load_error = load_task(root, task_id)
    if task is None:
        if json_mode:
            print(json.dumps({"task": task_id, "ok": False, "error": load_error}, ensure_ascii=False))
        else:
            print(load_error, file=sys.stderr)
        return EXIT_USAGE

    checks = collect_checks(task, root, dry)
    exit_code = EXIT_OK if dry else (EXIT_FAIL if any(c["status"] == "FAIL" for c in checks) else EXIT_OK)

    if json_mode:
        payload = {
            "task": task_id,
            "root": str(root),
            "dry": dry,
            "ok": exit_code == EXIT_OK,
            "exit_code": exit_code,
            "checks": checks,
        }
        print(json.dumps(payload, ensure_ascii=False, indent=2))
        return exit_code

    return render_human(task_id, checks, dry)


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
PYEOF
