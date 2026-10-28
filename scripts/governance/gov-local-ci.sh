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
SECONDS=0

# ---- 治理总开关（scripts/governance/gov-mode.sh off 时直接放行）----
_gov_root="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"
# shellcheck source=/dev/null
if [ -f "$_gov_root/scripts/governance/gov-mode.sh" ]; then
  . "$_gov_root/scripts/governance/gov-mode.sh"
  if gov_mode_is_off; then
    gov_mode_audit_skip "local-ci"
    exit 0
  fi
fi

if gov_mode_is_off; then echo "[gov-local-ci] governance off，跳过本地门禁（构建/测试仍可手动跑）"; fi

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

# ---- 任务画像（DEC-074）：决定这次该跑多深，而不是一律全量 ----
# docs → 车道 A（不构建不测试）；gov/test → 全量测试；code/mixed 沿用路径车道
# explore/docs 模式（GOV_MODE）强制车道 A，跳过 gradle/可达性/原型强校验
PROFILE="$(bash scripts/governance/gov-profile.sh resolve 2>/dev/null || echo mixed)"
# 如果 GOV_MODE 环境变量未设置, 从 .ai/current-mode.json 读取 (DEC-077 模式感知)
if [ -z "${GOV_MODE:-}" ] && [ -f "$ROOT/scripts/governance/gov-mode-lib.sh" ]; then
  # shellcheck source=/dev/null
  . "$ROOT/scripts/governance/gov-mode-lib.sh"
  GOV_MODE=$(gov_mode_get 2>/dev/null || true)
fi
GOV_MODE="${GOV_MODE:-dev}"
echo "  任务画像: $PROFILE (GOV_MODE=$GOV_MODE)"
if [ "$LANE_FORCED" != "true" ]; then
  case "$PROFILE" in
    docs)
      if [ "$LANE" != "A" ]; then
        LANE="A"
        LANE_REASONS+=("画像 docs → 车道 A（文档任务不触发构建与测试）")
      fi
      ;;
    gov|test)
      export GOV_CI_FULL=1
      LANE_REASONS+=("画像 ${PROFILE} → 全量测试")
      ;;
  esac
fi
# GOV_MODE=explore|docs 强制车道 A，跳过 gradle/可达性/原型强校验
if [ "$GOV_MODE" = "explore" ] || [ "$GOV_MODE" = "docs" ]; then
  if [ "$LANE" != "A" ]; then
    LANE="A"
    LANE_REASONS+=("GOV_MODE=$GOV_MODE → 强制车道 A（跳过 gradle/可达性/原型强校验）")
  fi
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
  "gov-hook-doctor"
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
  if [ "$LANE" = "A" ]; then
    echo "  2. pytest 跳过（车道 A：变更不涉及代码）"
  else
    echo "  2. pytest tests/unit"
  fi
  i=3
  for s in "${DOC_STEPS[@]}"; do
    echo "  $i. $s"
    i=$((i + 1))
  done
  if [ "$LANE" = "A" ]; then
    echo "  （车道 A：custom-checks 走增量模式，reachability 跳过）"
  fi
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

# ---------------- 结果缓存（DEC-072）----------------
# 同一份输入（脚本内容 + 相关文件内容）跑第二次直接复用输出。
# 只缓存纯读检查；会写共享状态的步骤不进缓存。
CACHE_ROOT="$ROOT/.cache/gov"
CI_STEP_DIR="$ROOT/.cache/ci-steps"
mkdir -p "$CACHE_ROOT"

cache_key() {
  local p
  {
    for p in "$@"; do
      if [ -d "$p" ]; then
        find "$p" -type f \( -name '*.kt' -o -name '*.java' -o -name '*.py' \
          -o -name '*.sh' -o -name '*.json' -o -name '*.yaml' -o -name '*.yml' \
          -o -name '*.md' -o -name '*.xml' -o -name '*.kts' \) -exec shasum {} + 2>/dev/null
      elif [ -f "$p" ]; then
        shasum "$p" 2>/dev/null
      fi
    done
  } | shasum | cut -d' ' -f1
}

run_with_cache() {
  local name="$1" key="$2"
  shift 2
  local stamp="$CACHE_ROOT/$name.$key"
  if [ -f "$stamp.out" ]; then
    echo "[cache hit] $name"
    cat "$stamp.out"
    return "$(cat "$stamp.rc" 2>/dev/null || echo 0)"
  fi
  local out rc
  out="$("$@" 2>&1)"
  rc=$?
  printf '%s\n' "$out"
  # 原子落盘：并发跑两个 local-ci 时不留下半截缓存
  printf '%s\n' "$out" > "$stamp.out.$$" && mv "$stamp.out.$$" "$stamp.out"
  printf '%s\n' "$rc" > "$stamp.rc.$$" && mv "$stamp.rc.$$" "$stamp.rc"
  # 每个步骤只保留最近 10 份缓存，避免无限增长
  ls -t "$CACHE_ROOT/$name".*.out 2>/dev/null | tail -n +11 | while IFS= read -r old; do
    rm -f "$old" "${old%.out}.rc"
  done
  return "$rc"
}

# 每步的 key 只包含它真正读的输入：改文档不应让源码检查失效，反之亦然
step_validate()     { bash scripts/governance/gov-validate.sh; }
step_secrets()      { bash scripts/governance/gov-secrets-scan.sh; }
step_custom_checks() {
  if [ "$LANE" = "A" ]; then
    GOV_CODE_CHECK_STAGED=1 bash scripts/governance/gov-custom-checks.sh
    return $?
  fi
  run_with_cache "gov-custom-checks" \
    "$(cache_key app/src/main domain/src/main application/src/main infrastructure/src/main \
       scripts/governance/gov-code-check.py scripts/governance/gov-custom-checks.sh)" \
    bash scripts/governance/gov-custom-checks.sh
}
step_schema_guard() {
  run_with_cache "gov-schema-guard" \
    "$(cache_key infrastructure/schemas infrastructure/src/main scripts/governance/gov-schema-guard.sh)" \
    bash scripts/governance/gov-schema-guard.sh
}
step_test_guard() {
  run_with_cache "gov-test-guard" \
    "$(cache_key tests scripts/governance/gov-test-guard.sh)" \
    bash scripts/governance/gov-test-guard.sh
}
step_a11y() {
  run_with_cache "gov-a11y-check" \
    "$(cache_key app/src/main scripts/governance/gov-a11y-check.py)" \
    python3 scripts/governance/gov-a11y-check.py
}
step_reachability() {
  run_with_cache "gov-reachability-check" \
    "$(cache_key app/src/main scripts/governance/gov-reachability-check.py scripts/governance/gov-reach-baseline.txt)" \
    python3 scripts/governance/gov-reachability-check.py
}
step_hook_doctor() {
  run_with_cache "gov-hook-doctor" \
    "$(cache_key scripts/hooks scripts/governance/gov-hook-doctor.sh scripts/governance/gov-mode.sh)" \
    bash scripts/governance/gov-hook-doctor.sh
}

bg_step() {
  local name="$1"
  shift
  local log="$CI_STEP_DIR/$name"
  ( "$@" > "$log.log" 2>&1; echo $? > "$log.rc" ) &
}

collect_bg() {
  local name="$1"
  local rc=0
  [ -f "$CI_STEP_DIR/$name.rc" ] && rc="$(cat "$CI_STEP_DIR/$name.rc")"
  echo ""
  echo "=== [$name]（并行）==="
  cat "$CI_STEP_DIR/$name.log" 2>/dev/null
  if [ "$rc" = "0" ]; then
    echo "  PASS: $name"
    PASS=$((PASS + 1))
    STEPS+=("$name=0")
  else
    echo "  FAIL: $name (exit $rc)"
    FAIL=$((FAIL + 1))
    STEPS+=("$name=$rc")
  fi
}

# pytest 策略：默认只跑受影响用例；脚本变更或 GOV_CI_FULL=1 时全量。
# 全量 pytest 的并行度取决于是否装了 xdist（不强制依赖）。
run_pytest_step() {
  if [ "$LANE" = "A" ]; then
    echo ""
    echo "=== [pytest] 跳过（车道 A：变更不涉及代码）==="
    return 0
  fi
  local scripts_changed=0
  case $'\n'"$DETECTED_CHANGES" in
    *$'\n'scripts/*) scripts_changed=1 ;;
  esac
  if [ "${GOV_CI_FULL:-0}" = "1" ] || [ "$scripts_changed" = "1" ]; then
    echo ""
    echo "=== [pytest] 全量（脚本变更或显式全量，分片并行 + 结果缓存）==="
    run_step "pytest tests/unit (full)" bash scripts/testing/gov-pytest.sh --all
  else
    run_step "pytest affected" bash scripts/governance/gov-affected.sh --run
  fi
}

set +e
rm -rf "$CI_STEP_DIR"
mkdir -p "$CI_STEP_DIR"

if [ "${GOV_CI_PARALLEL:-1}" = "0" ]; then
  # 串行回退（排查并行相关问题时用）
  run_step "gov-validate" step_validate
  run_step "gov-custom-checks" step_custom_checks
  run_step "gov-secrets-scan" step_secrets
  run_step "gov-schema-guard" step_schema_guard
  run_step "gov-test-guard" step_test_guard
  run_step "gov-a11y-check" step_a11y
  if [ "$LANE" != "A" ]; then run_step "gov-reachability-check" step_reachability; fi
  run_step "gov-hook-doctor" step_hook_doctor
  if [ -n "$GRADLE_TASK" ]; then
    run_step "gradlew $GRADLE_TASK" ./gradlew "$GRADLE_TASK" --stacktrace
  else
    echo ""
    echo "=== [gradle] 跳过（车道 A：变更不参与 gradle 构建）==="
  fi
  run_pytest_step
else
  # 静态检查先并行启动（多数可命中缓存），与 gradle/pytest 重叠
  bg_step gov-validate step_validate
  bg_step gov-custom-checks step_custom_checks
  bg_step gov-secrets-scan step_secrets
  bg_step gov-schema-guard step_schema_guard
  bg_step gov-test-guard step_test_guard
  bg_step gov-a11y-check step_a11y
  if [ "$LANE" != "A" ]; then bg_step gov-reachability-check step_reachability; fi
  bg_step gov-hook-doctor step_hook_doctor

  if [ -n "$GRADLE_TASK" ]; then
    run_step "gradlew $GRADLE_TASK" ./gradlew "$GRADLE_TASK" --stacktrace
  else
    echo ""
    echo "=== [gradle] 跳过（车道 A：变更不参与 gradle 构建）==="
  fi

  run_pytest_step
  wait

  for s in gov-validate gov-custom-checks gov-secrets-scan gov-schema-guard \
           gov-test-guard gov-a11y-check gov-hook-doctor; do
    collect_bg "$s"
  done
  if [ "$LANE" != "A" ]; then collect_bg gov-reachability-check; fi
fi
set -e

echo ""
echo "=== 汇总 ==="
echo "  车道: $LANE"
echo "  通过: $PASS  失败: $FAIL"
# 成本可见化（DEC-080）：人类等待预算 5s；后台/队列才允许更长。
CI_BUDGET="${GOV_CI_BUDGET:-}"
if [ -z "$CI_BUDGET" ]; then
  if [ "${GOV_CI_BACKGROUND:-0}" = "1" ]; then CI_BUDGET=600; else CI_BUDGET=5; fi
fi
CI_MODE="前台"; [ "${GOV_CI_BACKGROUND:-0}" = "1" ] && CI_MODE="后台"
echo "  耗时: ${SECONDS}s（预算 ${CI_BUDGET}s，${CI_MODE}）"
if [ "$SECONDS" -gt "$CI_BUDGET" ]; then
  echo "  [超预算] ${SECONDS}s > ${CI_BUDGET}s：前台调用应改用 ship.sh 把全量放到后台"
fi
if [ "$FAIL" -ne 0 ]; then
  write_result fail
  # 后台验证不写共享状态（DEC-081）：验证不该弄脏工作区
  [ "${GOV_CI_BACKGROUND:-0}" = "1" ] || bash scripts/governance/gov-update-status.sh 2>/dev/null || true
  echo "FAIL gov-local-ci"
  exit 1
fi
write_result pass
[ "${GOV_CI_BACKGROUND:-0}" = "1" ] || bash scripts/governance/gov-update-status.sh 2>/dev/null || true
echo "PASS gov-local-ci"
exit 0
