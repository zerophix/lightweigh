#!/usr/bin/env bash
# ============================================================
# scripts/dev/dev-loop.sh
# 环 0：编辑-反馈循环（DEC-072）。目标是"改完 2 秒内看到结果"。
#
# 用法:
#   dev-loop.sh                 # 跑一次：增量编译 + 受影响 pytest
#   dev-loop.sh --watch         # 文件变化后自动重跑（2 秒轮询，无额外依赖）
#   dev-loop.sh --no-build      # 只跑测试，不编译
#   dev-loop.sh --budget 5      # 内环预算（默认 5 秒；超了就把剩余验证交给批次环）
#   dev-loop.sh --full          # 编译 + 全量 pytest（等同慢层，慎用）
#
# 与既有命令的分工:
#   dev-loop.sh  = 内环，不碰 git、不产证据、不做全量扫描
#   git commit   = 提交环，只查暂存区（GOV_CODE_CHECK_STAGED=1）
#   gov-local-ci = 批次环，全量测试 + 可达性 + 原型 + 全部校验
#
# 边界：本脚本失败不代表不能提交，只代表"这一小步没通过"。
# ============================================================
set -uo pipefail

DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$DIR/../.." && pwd)"
cd "$ROOT" || exit 2

WATCH=0
BUILD=1
FULL=0
BUDGET="${DEV_LOOP_BUDGET:-5}"
while [ $# -gt 0 ]; do
  case "$1" in
    --watch) WATCH=1; shift ;;
    --no-build) BUILD=0; shift ;;
    --full) FULL=1; shift ;;
    --budget) BUDGET="${2:-5}"; shift 2 ;;
    -h|--help) sed -n '2,24p' "$0"; exit 0 ;;
    *) echo "未知参数: $1" >&2; exit 2 ;;
  esac
done
case "$BUDGET" in
  ''|*[!0-9]*) echo "预算必须是整数秒: $BUDGET" >&2; exit 2 ;;
esac

GRADLE_BIN="${GRADLE_BIN:-./gradlew}"
JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"
export JAVA_HOME

now_ms() { python3 -c 'import time;print(int(time.time()*1000))'; }

has_kotlin_change() {
  ! bash scripts/governance/gov-affected.sh --json 2>/dev/null | grep -q '"kotlin":0'
}

# 改动文件 → 同名 Kotlin 测试文件（Xxx.kt → XxxTest.kt）。
# 只做"改名匹配"这一条确定规则；匹配不到就跳过，绝不猜（猜错比跳过更贵）。
kotlin_changed_tests() {
  local files
  files="$(git diff HEAD --name-only --diff-filter=ACM 2>/dev/null \
    | grep -E '^(app|domain|application|infrastructure)/.*\.(kt|java)$' || true)"
  [ -z "$files" ] && return 0
  local f stem hit
  for f in $files; do
    stem="$(basename "$f")"
    stem="${stem%.*}"
    case "$stem" in
      *Test) echo "$f"; continue ;;
    esac
    hit="$(find app/src/test domain/src/test application/src/test infrastructure/src/test \
      -name "${stem}Test.kt" 2>/dev/null | head -1 || true)"
    [ -n "$hit" ] && echo "$hit"
  done | sort -u
}

run_kotlin_tests() {
  local tests
  tests="$(kotlin_changed_tests)"
  if [ -z "$tests" ]; then
    echo "  [kotlin] 无同名测试文件，跳过（批次环会跑全量）"
    return 0
  fi
  local mod t cls task patterns
  for mod in $(printf '%s\n' "$tests" | cut -d/ -f1 | sort -u); do
    patterns=""
    for t in $(printf '%s\n' "$tests" | grep "^${mod}/" || true); do
      cls="${t#*/src/test/}"
      cls="${cls#kotlin/}"
      cls="${cls%.kt}"
      cls="${cls//\//.}"
      patterns="${patterns} --tests ${cls}"
    done
    if [ "$mod" = "app" ]; then task=":app:testDebugUnitTest"; else task=":${mod}:test"; fi
    echo "  [kotlin] ${task}${patterns}"
    # shellcheck disable=SC2086  # patterns 必须按空格拆成多个 --tests 参数
    if ! "$GRADLE_BIN" "$task" $patterns -q --console=plain; then
      echo "[kotlin] 测试失败"
      return 3
    fi
  done
  return 0
}

elapsed_s() { echo $(( ($(now_ms) - START_MS) / 1000 )); }
over_budget() { [ "$(elapsed_s)" -ge "$BUDGET" ]; }

run_once() {
  local end
  START_MS=$(now_ms)

  if [ "$BUILD" = "1" ] && has_kotlin_change; then
    echo "[1/3] 增量编译"
    if ! "$GRADLE_BIN" :domain:compileKotlin :application:compileKotlin \
        :infrastructure:compileDebugKotlin :app:compileDebugKotlin -q --console=plain; then
      echo "[编译] 失败"
      return 2
    fi
  else
    echo "[1/3] 跳过编译（无 Kotlin 改动或 --no-build）"
  fi

  if [ "$FULL" = "1" ]; then
    echo "[2/2] 全量 pytest（--full 不受预算约束）"
    python3 -m pytest tests/unit -q || return 3
  else
    echo "[2/3] 受影响 Kotlin 测试"
    run_kotlin_tests || return 3

    if over_budget; then
      echo "[预算] 已用 $(elapsed_s)s ≥ ${BUDGET}s，跳过受影响 pytest"
      echo "       剩余验证交给批次环: bash scripts/governance/gov-local-ci.sh"
    else
      echo "[3/3] 受影响 pytest"
      bash scripts/governance/gov-affected.sh --run || return 3
    fi
  fi

  end=$(now_ms)
  echo "完成，用时 $(( (end - START_MS) / 1000 ))s（预算 ${BUDGET}s）"
  return 0
}

if [ "$WATCH" = "0" ]; then
  run_once
  exit $?
fi

echo "监视中（每 2 秒检查一次，Ctrl-C 退出）"
LAST=""
while true; do
  SIG="$(git status --porcelain 2>/dev/null | shasum | cut -d' ' -f1)"
  if [ "$SIG" != "$LAST" ]; then
    LAST="$SIG"
    echo ""
    echo "=== $(date '+%H:%M:%S') 检测到变更 ==="
    run_once
  fi
  sleep 2
done
