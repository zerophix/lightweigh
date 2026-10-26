#!/usr/bin/env bash
# ============================================================
# scripts/dev/ship.sh
# 一条命令把改动送到远端（DEC-080）。人等待预算 5 秒。
#
# 用法:
#   ship.sh -m "fix(x): ..." <path> [path...]   # 只提交指定路径
#   ship.sh -m "..." --staged                   # 只提交已暂存内容
#   ship.sh -m "..." --no-push                  # 只提交
#
# 它做什么（按顺序，全部有 5s 预算意识）:
#   1. 暂存指定路径（不碰未指定的工作区改动）
#   2. commit（钩子仍会跑：角色/画像/语法/增量编译）
#   3. push（首次自动 --set-upstream）
#   4. 已有 PR 则自动追加提交；没有则开 PR
#   5. 全量 local-ci 丢到后台（不占人类等待时间）
#
# 它不做什么:
#   - 不跑全量测试（后台）
#   - 不碰未指定路径（避免把别人的 WIP 带上）
#   - 不重跑钩子已经做过的检查
# ============================================================
set -uo pipefail

DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$DIR/../.." && pwd)"
cd "$ROOT" || exit 2

BUDGET="${SHIP_BUDGET:-5}"
MSG=""
PATHS=()
USE_STAGED=0
PUSH=1

while [ $# -gt 0 ]; do
  case "$1" in
    -m) MSG="${2:-}"; shift 2 ;;
    --staged) USE_STAGED=1; shift ;;
    --no-push) PUSH=0; shift ;;
    -h|--help) sed -n '2,26p' "$0"; exit 0 ;;
    -*) echo "未知参数: $1" >&2; exit 2 ;;
    *) PATHS+=("$1"); shift ;;
  esac
done

[ -n "$MSG" ] || { echo "用法: ship.sh -m \"type(scope): subject\" <path...>" >&2; exit 2; }

now_ms() { python3 -c 'import time;print(int(time.time()*1000))'; }
START=$(now_ms)
elapsed() { echo $(( ($(now_ms) - START) / 1000 )); }

# 1) 暂存（只碰指定路径）
if [ "$USE_STAGED" -eq 0 ]; then
  [ "${#PATHS[@]}" -gt 0 ] || { echo "错误: 请指定路径或使用 --staged" >&2; exit 2; }
  git add -- "${PATHS[@]}" || exit 2
fi
if git diff --cached --quiet; then
  echo "没有可提交的改动"
  exit 0
fi

# 2) 提交（钩子是唯一的快层门禁，不重复跑）
echo "[1/4] commit"
if ! git commit -q -m "$MSG"; then
  echo "提交被拒绝（看上方钩子输出）"
  exit 1
fi

BRANCH="$(git rev-parse --abbrev-ref HEAD)"

# 3) 推送
if [ "$PUSH" -eq 1 ]; then
  echo "[2/4] push ($BRANCH)"
  if ! git rev-parse --abbrev-ref --symbolic-full-name '@{u}' >/dev/null 2>&1; then
    git push -q -u origin "$BRANCH" || { echo "推送失败"; exit 1; }
  else
    git push -q || { echo "推送失败"; exit 1; }
  fi

  # 4) PR：有则复用，无则创建
  echo "[3/4] pull request"
  if command -v gh >/dev/null 2>&1; then
    if ! gh pr view --json number >/dev/null 2>&1; then
      gh pr create --fill --base master >/dev/null 2>&1 || true
    fi
  fi
fi

# 5) 全量验证放后台，不占人等待
echo "[4/4] 后台全量验证"
if [ -x scripts/workflow/wf-run.sh ]; then
  JOB="$(GOV_CI_BACKGROUND=1 bash scripts/workflow/wf-run.sh start "ship-$(date +%H%M%S)" -- bash scripts/governance/gov-local-ci.sh 2>/dev/null | tail -1)"
  [ -n "$JOB" ] && echo "      job=$JOB（bash scripts/workflow/wf-run.sh wait $JOB）"
fi

echo "完成，用时 $(elapsed)s（预算 ${BUDGET}s）"
