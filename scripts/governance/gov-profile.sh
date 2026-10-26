#!/usr/bin/env bash
# ============================================================
# scripts/governance/gov-profile.sh
# 任务画像（profile）：docs | test | gov | code | mixed
#
# 解决的问题：doc 修改、治理脚本修改、测试修改、产品代码修改，是四类
# 完全不同的任务。过去一律按"改了哪些文件"触发门禁，导致每类任务都被
# 套上最重的流程。画像把"任务类型"作为一等公民，让钩子与 CI 只跑该跑的东西。
#
# 用法:
#   gov-profile.sh resolve [--staged]   # 输出一个画像（多条来源取最严）
#   gov-profile.sh explain [--staged]   # 输出画像 + 每个来源的理由
#   gov-profile.sh check-scope --staged # 校验暂存区是否越出画像允许范围
#   gov-profile.sh paths <profile>      # 打印该画像允许的路径前缀
#
# 解析优先级（先到先得，但多来源会合并取最严）:
#   1. GOV_PROFILE（必须同时设 GOV_PROFILE_FORCE=1，否则忽略：防止 AI 自我降级）
#   2. 当前会话持有的认领锁里的 profile
#   3. 变更路径推断
#   4. 都没有 → mixed（最严）
#
# 严格度: docs < test < code < gov < mixed
#   （gov 最高是因为治理脚本错了会让所有门禁失真，比单个功能错更贵）
#
# 退出码: resolve/explain 恒 0；check-scope 有越界返回 1。
# ============================================================
set -uo pipefail

DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$DIR/../.." && pwd)"
cd "$ROOT" || exit 2

rank_of() {
  case "$1" in
    docs) echo 0 ;;
    test) echo 1 ;;
    code) echo 2 ;;
    gov) echo 3 ;;
    *) echo 4 ;;
  esac
}

classify() {
  case "$1" in
    docs/*|design/*|*.md|.ai/notes/*|.opencode/*) echo docs ;;
    scripts/*|.github/*) echo gov ;;
    gradle.properties|gradle/*|*.gradle.kts|settings.gradle.kts|build.gradle.kts) echo code ;;
    tests/*|app/src/test/*|domain/src/test/*|application/src/test/*|infrastructure/src/test/*) echo test ;;
    app/src/main/*|domain/src/*|application/src/*|infrastructure/src/*) echo code ;;
    .ai/*|.cache/*) echo docs ;;
    *) echo mixed ;;
  esac
}

changed_files() {
  local mode="${1:-worktree}"
  if [ "$mode" = "staged" ]; then
    git diff --cached --name-only --diff-filter=ACM 2>/dev/null
  else
    local base
    base="$(git merge-base HEAD origin/master 2>/dev/null || echo HEAD~1)"
    git diff "$base"...HEAD --name-only --diff-filter=ACM 2>/dev/null
    git diff HEAD --name-only --diff-filter=ACM 2>/dev/null
    git ls-files --others --exclude-standard 2>/dev/null
  fi | sed '/^[[:space:]]*$/d' | sort -u
}

profile_from_paths() {
  local mode="$1" best="docs" p prof r br
  br=0
  while IFS= read -r p; do
    [ -n "$p" ] || continue
    prof="$(classify "$p")"
    r="$(rank_of "$prof")"
    if [ "$r" -gt "$br" ]; then best="$prof"; br="$r"; fi
  done <<< "$(changed_files "$mode")"
  echo "$best"
}

profile_from_claim() {
  local lock_dir="$ROOT/.ai/locks" f prof sid sid_now best=""
  [ -d "$lock_dir" ] || return 1
  sid_now="$(python3 -c "import json;print(json.load(open('$ROOT/.ai/current-session.json')).get('session_id',''))" 2>/dev/null || true)"
  for f in "$lock_dir"/*/profile; do
    [ -f "$f" ] || continue
    sid="$(cat "$(dirname "$f")/owner" 2>/dev/null || true)"
    [ -n "$sid_now" ] && [ "$sid" != "$sid_now" ] && continue
    prof="$(cat "$f" 2>/dev/null || true)"
    [ -n "$prof" ] && best="$prof"
  done
  [ -n "$best" ] && { echo "$best"; return 0; }
  return 1
}

resolve() {
  local mode="worktree"
  [ "${1:-}" = "--staged" ] && mode="staged"

  local best="" best_rank=-1 cand r reason=""
  # FORCE 是唯一允许"降级"的入口：显式承担，且 explain 能看到来源
  if [ "${GOV_PROFILE_FORCE:-0}" = "1" ] && [ -n "${GOV_PROFILE:-}" ]; then
    echo "$GOV_PROFILE"
    echo "env(GOV_PROFILE_FORCE)" >&2
    return 0
  fi

  if cand="$(profile_from_claim)"; then
    r="$(rank_of "$cand")"
    if [ "$r" -gt "$best_rank" ]; then best="$cand"; best_rank="$r"; reason="claim"; fi
  fi

  cand="$(profile_from_paths "$mode")"
  r="$(rank_of "$cand")"
  if [ "$r" -gt "$best_rank" ]; then best="$cand"; best_rank="$r"; reason="paths"; fi

  [ -z "$best" ] && best="mixed"
  echo "$best"
  echo "$reason" >&2
}

allowed() {
  local prof="$1" path="$2"
  case "$prof" in
    docs)
      case "$path" in
        docs/*|design/*|*.md|.ai/notes/*|.opencode/*) return 0 ;;
        *) return 1 ;;
      esac
      ;;
    test)
      case "$path" in
        tests/*|app/src/test/*|domain/src/test/*|application/src/test/*|infrastructure/src/test/*|docs/*|*.md) return 0 ;;
        *) return 1 ;;
      esac
      ;;
    gov)
      case "$path" in
        scripts/*|tests/*|docs/*|.github/*|*.md|.opencode/*) return 0 ;;
        *) return 1 ;;
      esac
      ;;
    code)
      case "$path" in
        .ai/locks/*|.cache/*) return 1 ;;
        *) return 0 ;;
      esac
      ;;
    *) return 0 ;;
  esac
}

check_scope() {
  local mode="${1:---staged}"
  mode="${mode#--}"
  local prof p bad=0
  prof="$(resolve "--$mode" 2>/dev/null || echo mixed)"
  while IFS= read -r p; do
    [ -n "$p" ] || continue
    if ! allowed "$prof" "$p"; then
      echo "  [越界] profile=${prof} 不允许修改: $p"
      bad=1
    fi
  done <<< "$(changed_files "$mode")"
  if [ "$bad" = "1" ]; then
    echo "  处理: 重新认领更合适的画像（wf-claim.sh claim T-xxx --profile <docs|test|gov|code|mixed>）"
    echo "        或确认这是混合任务后用 --profile mixed"
    return 1
  fi
  return 0
}

# 供单元测试 source 使用：只加载函数，不执行分发
if [ "${GOV_PROFILE_LIB:-0}" = "1" ]; then
  return 0 2>/dev/null || exit 0
fi

case "${1:-}" in
  resolve) shift; resolve "${1:-}" ;;
  explain)
    shift
    mode="${1:-worktree}"
    [ "$mode" = "--staged" ] && mode="staged"
    echo "profile=$(resolve "$mode" 2>/dev/null)"
    echo "changed:"
    changed_files "$mode" | sed 's/^/  /'
    ;;
  check-scope) shift; check_scope "${1:---staged}" ;;
  paths)
    case "${2:-}" in
      docs) echo "docs/ design/ *.md .ai/notes/" ;;
      test) echo "tests/ app/src/test/" ;;
      gov) echo "scripts/ tests/ docs/ .github/" ;;
      code) echo "app/ domain/ application/ infrastructure/" ;;
      *) echo "*" ;;
    esac
    ;;
  -h|--help) sed -n '2,30p' "$0" ;;
  *) echo "用法: gov-profile.sh {resolve|explain|check-scope|paths}" >&2; exit 2 ;;
esac
