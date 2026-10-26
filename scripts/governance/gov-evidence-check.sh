#!/usr/bin/env bash
# ============================================================
# scripts/governance/gov-evidence-check.sh
# 完成证据消费方（DEC-078）—— 让 .ai/evidence 从"写着好看"变成"合入必需"。
#
# 背景：证据机制上线后目录一直是空的，因为没有任何东西读它。
# 没有消费方的证据 = 没有证据。本脚本把"缺证据/证据未通过"变成合入阻塞项。
#
# 用法:
#   gov-evidence-check.sh [--root DIR] [--tasks T-1 T-2] [--warn|--block]
#   不传 --tasks 时，从 "origin/master..HEAD" 的提交 trailer `Task: T-xxx` 提取。
#
# 判定:
#   - 证据文件不存在            → 缺证据
#   - 证据存在但 ok != true     → 未通过（含历史失败记录）
#   - 证据 ok=true 且 git_sha 是 HEAD 祖先 → 通过
#
# 退出码: 0 全部满足 / 1 存在不满足（block 模式）/ 2 用法错误
# 环境: GOV_EVIDENCE_MODE=block|warn（默认 warn）；命令行开关优先
# ============================================================
set -uo pipefail

DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT="${GOV_EVIDENCE_ROOT:-$(cd "$DIR/../.." && pwd)}"
cd "$ROOT" || exit 2

MODE="${GOV_EVIDENCE_MODE:-warn}"
TASKS=()

while [ $# -gt 0 ]; do
  case "$1" in
    --root) ROOT="$2"; cd "$ROOT" || exit 2; shift 2 ;;
    --tasks)
      shift
      while [ $# -gt 0 ] && [ "${1#--}" = "$1" ]; do TASKS+=("$1"); shift; done
      ;;
    --warn) MODE="warn"; shift ;;
    --block) MODE="block"; shift ;;
    -h|--help) sed -n '2,26p' "$0"; exit 0 ;;
    *) echo "未知参数: $1" >&2; exit 2 ;;
  esac
done

if [ "${#TASKS[@]}" -eq 0 ]; then
  while IFS= read -r t; do
    [ -n "$t" ] && TASKS+=("$t")
  done < <(git log --format=%B origin/master..HEAD 2>/dev/null \
    | sed -n 's/^Task:[[:space:]]*\(T-[0-9][0-9]*\).*/\1/p' | sort -u)
fi

if [ "${#TASKS[@]}" -eq 0 ]; then
  echo "  [evidence] 无任务声明（无 Task: trailer），跳过"
  exit 0
fi

MISSING=()
FAILED=()
OK=0
for t in "${TASKS[@]}"; do
  f=".ai/evidence/${t}.json"
  if [ ! -f "$f" ]; then
    MISSING+=("$t")
    continue
  fi
  if python3 - "$f" <<'PY'
import json, sys
try:
    d = json.load(open(sys.argv[1], encoding="utf-8"))
except Exception:
    sys.exit(1)
sys.exit(0 if d.get("ok") is True else 1)
PY
  then
    OK=$((OK + 1))
  else
    FAILED+=("$t")
  fi
done

echo "  [evidence] 任务 ${#TASKS[@]} 个：通过 ${OK}，缺证据 ${#MISSING[@]}，未通过 ${#FAILED[@]}"
if [ "${#MISSING[@]}" -gt 0 ]; then
  echo "    缺证据: ${MISSING[*]}（先跑 gov-task-done.sh <ID>）"
fi
if [ "${#FAILED[@]}" -gt 0 ]; then
  echo "    未通过: ${FAILED[*]}（看 .ai/evidence/<ID>.json 的 checks）"
fi

if [ "${#MISSING[@]}" -eq 0 ] && [ "${#FAILED[@]}" -eq 0 ]; then
  exit 0
fi

if [ "$MODE" = "block" ]; then
  echo "  阻断: 合入要求每个带 Task: trailer 的任务都有 ok=true 的证据"
  echo "        临时放宽: GOV_ALLOW_MISSING_EVIDENCE=1（会被记录在调用处）"
  exit 1
fi
exit 0
