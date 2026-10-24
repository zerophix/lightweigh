#!/usr/bin/env bash
# scripts/governance/gov-local-ci.sh
# 合并前本地门禁（项目自有）：按风险车道决定门禁深度（DEC-064）
#
# 车道（由 scripts/governance/gov-ci-scope.sh --print-lane 判定，单一真源）:
#   A  仅非构建类路径（docs/ design/ .github/ scripts/ tests/ *.md 等）
#      → 跳过 gradle，仍跑 gov-validate + custom-checks + secrets + schema + test-guard
#   B  仅 app/src/** 变更 → 只跑 :app:testDebugUnitTest，不跑全量 build
#   C  domain/ application/ infrastructure/ golden/ schemas / 构建文件 / 未知路径
#      → 保持全量 ./gradlew build
#   混合变更取最高车道（C > B > A）；检测失败按 C。
#   full 等价 C（历史全量行为）。
#
# 用法:
#   bash scripts/governance/gov-local-ci.sh              # 自动选车道
#   GOV_LANE=A|B|C|full bash scripts/governance/gov-local-ci.sh   # 强制车道
#   bash scripts/governance/gov-local-ci.sh --strict     # 等价 GOV_LANE=full
#   GOV_LOCAL_CI_PLAN=1 bash scripts/governance/gov-local-ci.sh  # 只打印决策计划，不执行
#
# DEC-049 secrets；DEC-052 schema-guard；DEC-053 test-guard；DEC-051 证据文件；
# DEC-064 风险车道（lane / lane_reasons 字段）。
#
# 任一步失败即非 0 退出。
set -euo pipefail

ROOT="$(git rev-parse --show-toplevel 2>/dev/null || true)"
if [ -z "${ROOT:-}" ] || [ ! -f "$ROOT/scripts/governance/gov-validate.sh" ]; then
  ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
fi
cd "$ROOT"

# ---- 参数：--strict / --full 等价于强制全量（兼容既有习惯） ----
for arg in "$@"; do
  case "$arg" in
    --strict|--full)
      export GOV_LANE="full"
      ;;
    -h|--help)
      sed -n '2,25p' "$0"
      exit 0
      ;;
    *)
      echo "未知参数: $arg" >&2
      echo "用法: bash scripts/governance/gov-local-ci.sh [--strict|--full]" >&2
      exit 2
      ;;
  esac
done

CACHE_DIR="$ROOT/.cache"
RESULT_FILE="$CACHE_DIR/local-ci-last.json"
mkdir -p "$CACHE_DIR"

PASS=0
FAIL=0
STARTED_AT="$(date '+%Y-%m-%dT%H:%M:%S%z')"
# ISO-ish with colon in offset for JSON consumers
STARTED_AT_ISO="$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
BRANCH="$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo unknown)"
GIT_SHA="$(git rev-parse HEAD 2>/dev/null || echo unknown)"
# step results as name=code pairs
STEPS=()

# ---- 车道判定（DEC-064） ------------------------------------
# 变更检测优先级：
#   1) git diff $(merge-base HEAD origin/master) HEAD
#   2) git diff HEAD~1 HEAD（detached / 无 origin 时）
#   3) 都不行 → 判定失败，按 C
# 另外把工作区未提交改动与未跟踪文件一并计入（保守：多算不亏门禁）。
GIT_BASE=""
GIT_BASE_KIND=""
DETECTED_CHANGES=""
LANE="C"
LANE_FORCED="false"
LANE_REASONS=()
LANE_CONTEXT=()

# 直接写全局变量（不放进命令替换的子 shell，否则 GIT_BASE 会被丢失）
detect_changes() {
  local base diff_out=""
  base="$(git merge-base HEAD origin/master 2>/dev/null || true)"
  if [ -n "$base" ]; then
    GIT_BASE="$base"
    GIT_BASE_KIND="merge-base(origin/master)"
  else
    base="$(git merge-base HEAD origin/main 2>/dev/null || true)"
    if [ -n "$base" ]; then
      GIT_BASE="$base"
      GIT_BASE_KIND="merge-base(origin/main)"
    fi
  fi

  if [ -n "$GIT_BASE" ]; then
    diff_out="$(git diff --name-only "$GIT_BASE" HEAD 2>/dev/null)" || return 1
  elif git rev-parse -q --verify 'HEAD~1' >/dev/null 2>&1; then
    GIT_BASE="HEAD~1"
    GIT_BASE_KIND="HEAD~1"
    diff_out="$(git diff --name-only HEAD~1 HEAD 2>/dev/null)" || return 1
  else
    return 1
  fi

  # 未提交改动（暂存 + 未暂存）与未跟踪文件
  diff_out="${diff_out}"$'\n'"$(git diff --name-only HEAD 2>/dev/null || true)"
  diff_out="${diff_out}"$'\n'"$(git ls-files --others --exclude-standard 2>/dev/null || true)"

  DETECTED_CHANGES="$(printf '%s\n' "$diff_out" | sed '/^[[:space:]]*$/d' | sort -u)"
  return 0
}

if detect_changes; then
  LANE_OUT="$(printf '%s\n' "$DETECTED_CHANGES" | bash scripts/governance/gov-ci-scope.sh --print-lane 2>/dev/null || true)"
  while IFS= read -r line; do
    case "$line" in
      lane=*) LANE="${line#lane=}" ;;
      forced=*) LANE_FORCED="${line#forced=}" ;;
      reason=*) LANE_REASONS+=("${line#reason=}") ;;
      path=*) LANE_CONTEXT+=("${line#path=}") ;;
    esac
  done <<< "$LANE_OUT"
  if [ -z "$LANE" ]; then
    LANE="C"
    LANE_FORCED="false"
    LANE_REASONS=("车道判定脚本无输出 → 保守按 C 处理")
  fi
else
  # 检测失败：不猜，全量
  DETECTED_CHANGES=""
  LANE="C"
  LANE_FORCED="false"
  LANE_REASONS=("变更基线不可用（无 merge-base / 无 HEAD~1）→ 保守按 C 处理")
fi

# 车道 → gradle 步骤（空 = 跳过 gradle）

# ---- 全局构建锁（DEC-069）：多 worktree 并发时串行化 gradle，避免 daemon 争抢 ----
BUILD_LOCK="$ROOT/.cache/build.lock.d"
acquire_build_lock() {
  local waited=0
  while ! mkdir "$BUILD_LOCK" 2>/dev/null; do
    # 超过 40 分钟视为僵尸锁
    if [ -d "$BUILD_LOCK" ] && [ -n "$(find "$BUILD_LOCK" -maxdepth 0 -mmin +40 2>/dev/null)" ]; then
      rm -rf "$BUILD_LOCK" 2>/dev/null || true
      continue
    fi
    [ "$waited" -eq 0 ] && echo "  [锁] 等待其他构建完成（.cache/build.lock.d）…"
    waited=$((waited + 2))
    [ "$waited" -gt 1800 ] && { echo "  [锁] 超时 30 分钟，放弃"; return 1; }
    sleep 2
  done
  echo $$ > "$BUILD_LOCK/pid" 2>/dev/null || true
  return 0
}
release_build_lock() { rm -rf "$BUILD_LOCK" 2>/dev/null || true; }

GRADLE_TASK=""
case "$LANE" in
  A) GRADLE_TASK="" ;;
  B) GRADLE_TASK=":app:testDebugUnitTest" ;;
  # DEC-068: 全量车道 = 编译 + 全部单测；打包/lint/release 对"能否合入"无增量信号，
  # 却占掉一大半构建时间（实测 build 6~10 分钟 vs test 1~3 分钟）。
  # 发布前需要完整构建时: GOV_CI_PROFILE=build
  C|full)
    if [ "${GOV_CI_PROFILE:-test}" = "build" ]; then
      GRADLE_TASK="build"
    else
      GRADLE_TASK="test"
    fi ;;
  *) LANE="C"; GRADLE_TASK="${GRADLE_TASK:-test}" ;;
esac

# 车道 A/B 仍然执行的非 gradle 步骤（质量不降）
DOC_STEPS=(
  "gov-validate"
  "gov-custom-checks"
  "gov-secrets-scan"
  "gov-schema-guard"
  "gov-test-guard"
  "gov-a11y-check"
  "gov-reachability-check"
)

echo "gov-local-ci — 仓库: $ROOT"
echo "开始: $(date '+%Y-%m-%d %H:%M:%S %z')  sha=${GIT_SHA:0:7}  branch=$BRANCH"
echo ""
echo "=== 车道决策（DEC-064） ==="
if [ "$LANE_FORCED" = "true" ]; then
  echo "  车道: ${LANE}（GOV_LANE 强制指定，忽略变更路径）"
elif [ "$LANE" = "full" ]; then
  echo "  车道: full（GOV_LANE=full 强制全量，等价历史行为）"
else
  echo "  车道: ${LANE}（自动判定，混合变更取最高 C > B > A）"
fi
if [ -n "$GIT_BASE" ]; then
  echo "  变更基线: ${GIT_BASE_KIND} ${GIT_BASE:0:7} → HEAD（含工作区未提交改动）"
fi
echo "  变更路径: $(printf '%s\n' "$DETECTED_CHANGES" | sed '/^[[:space:]]*$/d' | wc -l | tr -d ' ') 个"
if [ "${#LANE_REASONS[@]}" -gt 0 ]; then
  echo "  决定因素:"
  for r in "${LANE_REASONS[@]}"; do
    echo "    - $r"
  done
fi
if [ "${#LANE_CONTEXT[@]}" -gt 0 ]; then
  echo "  变更路径清单:"
  for c in "${LANE_CONTEXT[@]}"; do
    echo "    - $c"
  done
fi
case "$LANE" in
  A) echo "  gradle: 跳过（变更不参与 gradle 构建）" ;;
  B) echo "  gradle: ./gradlew ${GRADLE_TASK}（仅 app 模块单测）" ;;
  *) echo "  gradle: ./gradlew build（全量）" ;;
esac
echo "  其余步骤: ${DOC_STEPS[*]}"

# 只打印计划、不执行（供测试与排查用）
if [ "${GOV_LOCAL_CI_PLAN:-0}" = "1" ]; then
  echo ""
  echo "=== 计划步骤 ==="
  if [ -n "$GRADLE_TASK" ]; then
    echo "  1. gradlew $GRADLE_TASK"
  else
    echo "  1. gradle 跳过"
  fi
  i=2
  for s in "${DOC_STEPS[@]}"; do
    echo "  $i. $s"
    i=$((i + 1))
  done
  echo ""
  echo "PLAN lane=$LANE gradle=${GRADLE_TASK:-skip}"
  exit 0
fi

run_step() {
  local name="$1"
  shift
  echo ""
  echo "=== [$name] ==="
  if "$@"; then
    echo "  PASS: $name"
    PASS=$((PASS + 1))
    STEPS+=("${name}=0")
    return 0
  else
    local rc=$?
    echo "  FAIL: $name (exit $rc)"
    FAIL=$((FAIL + 1))
    STEPS+=("${name}=$rc")
    return 1
  fi
}

write_result() {
  local overall="$1"  # pass | fail
  local finished_iso
  finished_iso="$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
  # Export env for python
  OVERALL="$overall" \
  STARTED_AT_ISO="$STARTED_AT_ISO" \
  FINISHED_ISO="$finished_iso" \
  BRANCH="$BRANCH" \
  GIT_SHA="$GIT_SHA" \
  PASS_N="$PASS" \
  FAIL_N="$FAIL" \
  RESULT_FILE="$RESULT_FILE" \
  LANE_VALUE="$LANE" \
  LANE_FORCED_VALUE="$LANE_FORCED" \
  LANE_REASONS_TEXT="$(printf '%s\n' "${LANE_REASONS[@]:-}")" \
  STEPS_CSV="$(IFS=,; echo "${STEPS[*]-}")" \
  python3 - <<'PY'
import json, os
from pathlib import Path

steps = []
for part in filter(None, os.environ.get("STEPS_CSV", "").split(",")):
    if "=" not in part:
        continue
    name, code = part.rsplit("=", 1)
    try:
        code_i = int(code)
    except ValueError:
        code_i = 1
    steps.append({"name": name, "exit_code": code_i, "ok": code_i == 0})

# DEC-064: lane 与 lane_reasons 为新增字段，schema_version 保持 1（只加字段不加语义）
lane_reasons = [ln for ln in os.environ.get("LANE_REASONS_TEXT", "").splitlines() if ln.strip()]

payload = {
    "schema_version": 1,
    "source": "gov-local-ci",
    "result": os.environ["OVERALL"],
    "started_at": os.environ["STARTED_AT_ISO"],
    "finished_at": os.environ["FINISHED_ISO"],
    "branch": os.environ["BRANCH"],
    "git_sha": os.environ["GIT_SHA"],
    "pass_count": int(os.environ["PASS_N"]),
    "fail_count": int(os.environ["FAIL_N"]),
    "lane": os.environ.get("LANE_VALUE", "C"),
    "lane_forced": os.environ.get("LANE_FORCED_VALUE", "false") == "true",
    "lane_reasons": lane_reasons,
    "steps": steps,
}
path = Path(os.environ["RESULT_FILE"])
path.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(f"  wrote evidence: {path}")
PY
}

set +e
if [ -n "$GRADLE_TASK" ]; then
  run_step "gradlew $GRADLE_TASK" ./gradlew "$GRADLE_TASK" --stacktrace
else
  echo ""
  echo "=== [gradle] 跳过（车道 A：变更不参与 gradle 构建）==="
fi
run_step "gov-validate" bash scripts/governance/gov-validate.sh
run_step "gov-custom-checks" bash scripts/governance/gov-custom-checks.sh
run_step "gov-secrets-scan" bash scripts/governance/gov-secrets-scan.sh
run_step "gov-schema-guard" bash scripts/governance/gov-schema-guard.sh
run_step "gov-test-guard" bash scripts/governance/gov-test-guard.sh
run_step "gov-a11y-check" python3 scripts/governance/gov-a11y-check.py
run_step "gov-reachability-check" python3 scripts/governance/gov-reachability-check.py
set -e

echo ""
echo "=== 汇总 ==="
echo "  车道: $LANE"
echo "  通过: $PASS  失败: $FAIL"
if [ "$FAIL" -ne 0 ]; then
  write_result fail
  bash scripts/governance/gov-update-status.sh 2>/dev/null || true
  echo "FAIL gov-local-ci"
  exit 1
fi
write_result pass
bash scripts/governance/gov-update-status.sh 2>/dev/null || true
echo "PASS gov-local-ci"
exit 0
