#!/usr/bin/env bash
# ============================================================
# scripts/governance/gov-audit-bypass.sh
# 检测"未经 pre-commit 的提交"（DEC-077）—— 也就是 --no-verify 的痕迹。
#
# 原理：pre-commit 通过后把提交树（git write-tree 的结果）记进
# .cache/hook-trail.log。推送时反查每个待推提交的 tree 是否在轨迹里：
# 在 = 走过钩子；不在 = 要么用了 --no-verify，要么来自别的机器。
#
# 局限（必须写清楚，否则就是假的保障）：
#   - 只对"本机产生且轨迹存在"的提交有效；换机器/换 clone 会产生假阳性；
#   - 合并提交与队列自动提交豁免（它们本来就不走 pre-commit）；
#   - 轨迹可被删除，所以这是"可发现"而不是"不可绕过"。
#     真正的不可绕过需要服务端 pre-receive 或 CI required checks。
#
# 用法:
#   gov-audit-bypass.sh --commits <sha...> [--warn|--block]
#   gov-audit-bypass.sh --range origin/master..HEAD [--warn|--block]
#   （不传 --commits/--range 时审计 HEAD 上未推送的提交）
#
# 退出码: 0 无异常 / 1 发现异常且 mode=block / 2 用法错误
# ============================================================
set -uo pipefail

DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT="${GOV_AUDIT_ROOT:-$(cd "$DIR/../.." && pwd)}"
cd "$ROOT" || exit 2

TRAIL="$ROOT/.cache/hook-trail.log"
MODE="warn"
COMMITS=()
RANGE=""

while [ $# -gt 0 ]; do
  case "$1" in
    --commits)
      shift
      # 只吞掉 sha，遇到下一个开关就停（否则 --block 会被一起吃掉）
      while [ $# -gt 0 ] && [ "${1#--}" = "$1" ]; do
        COMMITS+=("$1")
        shift
      done
      ;;
    --range) RANGE="${2:-}"; shift 2 ;;
    --warn) MODE="warn"; shift ;;
    --block) MODE="block"; shift ;;
    -h|--help) sed -n '2,25p' "$0"; exit 0 ;;
    *) echo "未知参数: $1" >&2; exit 2 ;;
  esac
done

if [ ! -f "$TRAIL" ]; then
  echo "  [audit] 无钩子轨迹，跳过（首次使用或轨迹已清理）"
  exit 0
fi

# 只审计轨迹开始之后的提交，避免把历史提交全判成"绕过"
OLDEST="$(head -1 "$TRAIL" | awk '{print $2}')"
case "$OLDEST" in ''|*[!0-9]*) OLDEST=0 ;; esac

if [ "${#COMMITS[@]}" -eq 0 ]; then
  if [ -n "$RANGE" ]; then
    # shellcheck disable=SC2207
    COMMITS=($(git rev-list --max-count=100 "$RANGE" 2>/dev/null))
  else
    # shellcheck disable=SC2207
    COMMITS=($(git rev-list --max-count=100 HEAD --not --remotes 2>/dev/null))
  fi
fi

MISSING=()
CHECKED=0
for sha in "${COMMITS[@]}"; do
  [ -n "$sha" ] || continue
  git cat-file -e "${sha}^{commit}" 2>/dev/null || continue

  # 合并提交与队列/派生提交本来就不走 pre-commit
  parents="$(git rev-list --parents -n 1 "$sha" 2>/dev/null | wc -w | tr -d ' ')"
  [ "$parents" -gt 2 ] && continue
  subject="$(git log -1 --format=%s "$sha" 2>/dev/null || true)"
  case "$subject" in
    "chore(merge):"*|"chore(board):"*|"chore(lifecycle):"*|"Merge "*) continue ;;
  esac

  ct="$(git log -1 --format=%ct "$sha" 2>/dev/null || echo 0)"
  [ "$ct" -lt "$OLDEST" ] && continue

  tree="$(git rev-parse "${sha}^{tree}" 2>/dev/null || true)"
  [ -n "$tree" ] || continue
  CHECKED=$((CHECKED + 1))
  if ! grep -q "^${tree} " "$TRAIL" 2>/dev/null; then
    MISSING+=("$sha $subject")
  fi
done

if [ "$CHECKED" -eq 0 ]; then
  echo "  [audit] 无可审计提交（合并在豁免范围）"
  exit 0
fi

if [ "${#MISSING[@]}" -eq 0 ]; then
  echo "  [audit] ${CHECKED} 个提交均有钩子轨迹"
  exit 0
fi

echo "  [audit] 发现 ${#MISSING[@]} 个提交没有 pre-commit 轨迹（疑似 --no-verify 或他机提交）："
for m in "${MISSING[@]}"; do
  echo "    - $m"
done
echo "  说明: 轨迹只覆盖本机钩子运行过的提交；若确认合法（他机/重建），"
echo "        可用 GOV_BYPASS_POLICY=warn 放行，并在 PR 描述里说明。"

if [ "$MODE" = "block" ]; then
  exit 1
fi
exit 0
