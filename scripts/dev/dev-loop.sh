#!/usr/bin/env bash
# ============================================================
# scripts/dev/dev-loop.sh
# 环 0：编辑-反馈循环（DEC-072）。目标是"改完 2 秒内看到结果"。
#
# 用法:
#   dev-loop.sh                 # 跑一次：增量编译 + 受影响 pytest
#   dev-loop.sh --watch         # 文件变化后自动重跑（2 秒轮询，无额外依赖）
#   dev-loop.sh --no-build      # 只跑测试，不编译
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
while [ $# -gt 0 ]; do
  case "$1" in
    --watch) WATCH=1; shift ;;
    --no-build) BUILD=0; shift ;;
    --full) FULL=1; shift ;;
    -h|--help) sed -n '2,22p' "$0"; exit 0 ;;
    *) echo "未知参数: $1" >&2; exit 2 ;;
  esac
done

GRADLE_BIN="${GRADLE_BIN:-./gradlew}"
JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"
export JAVA_HOME

now_ms() { python3 -c 'import time;print(int(time.time()*1000))'; }

has_kotlin_change() {
  ! bash scripts/governance/gov-affected.sh --json 2>/dev/null | grep -q '"kotlin":0'
}

run_once() {
  local start end ok=0
  start=$(now_ms)

  if [ "$BUILD" = "1" ] && has_kotlin_change; then
    echo "[1/2] 增量编译"
    if ! "$GRADLE_BIN" :domain:compileKotlin :application:compileKotlin \
        :infrastructure:compileDebugKotlin :app:compileDebugKotlin -q --console=plain; then
      echo "[编译] 失败"
      return 2
    fi
    ok=1
  else
    echo "[1/2] 跳过编译（无 Kotlin 改动或 --no-build）"
  fi

  echo "[2/2] 受影响测试"
  if [ "$FULL" = "1" ]; then
    python3 -m pytest tests/unit -q || return 3
  else
    bash scripts/governance/gov-affected.sh --run || return 3
  fi

  end=$(now_ms)
  echo "完成，用时 $(( (end - start) / 1000 ))s"
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
