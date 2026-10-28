#!/usr/bin/env bash
# ============================================================
# scripts/governance/gov-ci-scope.sh
# CI 变更范围判定 — 决定 build-and-guard 是否执行重型 gradle 构建
#
# 用法:
#   git diff --name-only A B | bash scripts/governance/gov-ci-scope.sh
#   git diff --name-only A B | bash scripts/governance/gov-ci-scope.sh --print-lane
#
# 模式感知（DEC-077）:
#   explore/docs 模式 → 强制 lane=A（跳过 gradle/可达性/原型强校验）
#   优先级：模式 > GOV_LANE 强制 > 路径判定
# ============================================================
set -euo pipefail

# 变更路径全部落在本模式内 → 非构建类，跳过重型构建
ALLOW_RE='^(docs/|design/|scripts/|tests/|\.ai/|\.cache/|\.opencode/|\.claude/|mkdocs\.yml$|.*\.md$)'

# ---- 车道模式（DEC-064）的分类规则 -------------------------
LANE_A_RE='^(docs/|design/|\.github/|\.ai/|\.cache/|\.opencode/|\.claude/|scripts/|tests/|mkdocs\.yml$|.*\.md$|\.gitignore$|\.gitattributes$|\.editorconfig$|\.language$|LICENSE$)'
LANE_B_RE='^app/src/'
LANE_C_EXT_RE='\.(kt|kts|java|xml|toml|properties|pro|gradle|jar|aar)$'

lane_rank() {
  case "$1" in
    A) echo 1 ;;
    B) echo 2 ;;
    *) echo 3 ;;
  esac
}

lane_of_file() {
  local f="$1"
  if [[ "$f" =~ $LANE_B_RE ]]; then
    echo "B"
    return 0
  fi
  if [[ "$f" =~ $LANE_C_EXT_RE ]]; then
    echo "C"
    return 0
  fi
  if [[ "$f" =~ $LANE_A_RE ]]; then
    echo "A"
    return 0
  fi
  echo "C"
}

normalize_forced_lane() {
  local v="${GOV_LANE:-auto}"
  case "$v" in
    A|a) echo "A" ;;
    B|b) echo "B" ;;
    C|c) echo "C" ;;
    full|FULL|Full) echo "full" ;;
    auto|AUTO|Auto|"") echo "auto" ;;
    *)
      echo "[lane] 未知 GOV_LANE=${v}，按 auto 处理" >&2
      echo "auto"
      ;;
  esac
}

# 读取当前模式（通过独立进程调用 gov-mode-lib.sh，避免 set -u 污染）
read_gov_mode() {
  local root="${GOV_CI_ROOT:-$(git rev-parse --show-toplevel 2>/dev/null || pwd)}"
  local lib="$root/scripts/governance/gov-mode-lib.sh"
  if [ -f "$lib" ]; then
    local validation
    validation=$(bash -c "source '$lib' && gov_mode_validate" 2>/dev/null || true)
    local mode valid expired
    mode=$(printf '%s' "$validation" | python3 -c "import json,sys; d=json.load(sys.stdin); print(d.get('mode','dev'))" 2>/dev/null)
    valid=$(printf '%s' "$validation" | python3 -c "import json,sys; d=json.load(sys.stdin); print(d.get('valid',False))" 2>/dev/null)
    expired=$(printf '%s' "$validation" | python3 -c "import json,sys; d=json.load(sys.stdin); print(d.get('expired',False))" 2>/dev/null)
    if [ "$valid" = "True" ] && [ "$expired" = "False" ]; then
      printf '%s\n' "$mode"
      return 0
    fi
  fi
  printf 'dev\n'
  return 0
}

MODE="scope"
case "${1:-}" in
  --print-lane) MODE="lane" ;;
  "") ;;
  *)
    echo "[scope] 未知参数: $1" >&2
    echo "用法: git diff --name-only A B | bash scripts/governance/gov-ci-scope.sh [--print-lane]" >&2
    exit 2
    ;;
esac

FILES="$(cat || true)"
FILES="$(printf '%s\n' "$FILES" | sed '/^[[:space:]]*$/d')"

# ---------------------------------------------------------------- 车道模式
if [ "$MODE" = "lane" ]; then
  # 1. 先读模式：explore/docs 强制 Lane A
  GOV_MODE="$(read_gov_mode)"
  if [ "$GOV_MODE" = "explore" ] || [ "$GOV_MODE" = "docs" ]; then
    echo "lane=A"
    echo "forced=false"
    echo "reason=GOV_MODE=$GOV_MODE 强制车道 A（跳过 gradle/可达性/原型强校验）"
    if [ -n "$FILES" ]; then
      printf '%s\n' "$FILES" | sed 's/^/path=/' | head -50
    fi
    echo "[lane] GOV_MODE=$GOV_MODE → 强制 lane=A（忽略 GOV_LANE 与路径判定）" >&2
    exit 0
  fi

  FORCED="$(normalize_forced_lane)"

  if [ -n "$FORCED" ] && [ "$FORCED" != "auto" ]; then
    echo "lane=$FORCED"
    echo "forced=true"
    echo "reason=GOV_LANE=$FORCED 强制指定，跳过自动路径判定"
    if [ -n "$FILES" ]; then
      printf '%s\n' "$FILES" | sed 's/^/path=/' | head -50
    fi
    echo "[lane] GOV_LANE=$FORCED 强制指定车道（忽略变更路径）" >&2
    exit 0
  fi

  if [ -z "$FILES" ]; then
    echo "lane=C"
    echo "forced=false"
    echo "reason=未列举到变更文件（diff 为空或检测失败）→ 保守按 C 处理"
    echo "[lane] 判定 lane=C：无变更清单可判定，保守跑全量" >&2
    exit 0
  fi

  DECIDED=""
  DECIDED_RANK=0
  REASONS=()
  while IFS= read -r f; do
    [ -z "$f" ] && continue
    L="$(lane_of_file "$f")"
    R="$(lane_rank "$L")"
    if [ "$R" -gt "$DECIDED_RANK" ]; then
      DECIDED="$L"
      DECIDED_RANK="$R"
      REASONS=("$f")
    elif [ "$R" -eq "$DECIDED_RANK" ]; then
      REASONS+=("$f")
    fi
  done <<< "$FILES"

  echo "lane=${DECIDED:-C}"
  echo "forced=false"
  N=0
  for f in "${REASONS[@]}"; do
    N=$((N + 1))
    if [ "$N" -le 20 ]; then
      echo "reason=$f"
    fi
  done
  if [ "${#REASONS[@]}" -gt 20 ]; then
    echo "reason=... 另有 $(( ${#REASONS[@]} - 20 )) 条同车道路径"
  fi

  echo "[lane] 判定 lane=${DECIDED}（共 $(printf '%s\n' "$FILES" | wc -l | tr -d ' ') 个变更路径，混合取最高）" >&2
  if [ "${#REASONS[@]}" -gt 0 ]; then
    printf '%s\n' "${REASONS[@]}" | head -20 | sed 's/^/  [lane] 决定因素: /' >&2
  fi
  exit 0
fi

# ---------------------------------------------------------------- 范围模式
if [ -z "$FILES" ]; then
  echo "code=true"
  echo "[scope] 未列举到变更文件（diff 为空或失败）→ 保守按 code=true 处理" >&2
  exit 0
fi

# 范围模式也受模式影响：explore/docs → code=false
GOV_MODE="$(read_gov_mode)"
if [ "$GOV_MODE" = "explore" ] || [ "$GOV_MODE" = "docs" ]; then
  echo "code=false"
  echo "[scope] GOV_MODE=$GOV_MODE → 强制 code=false（跳过重型构建）" >&2
  exit 0
fi

NON_CODE="$(printf '%s\n' "$FILES" | grep -vE "$ALLOW_RE" || true)"

if [ -n "$NON_CODE" ]; then
  echo "code=true"
  echo "[scope] 判定 code=true：以下变更不属于非构建类路径" >&2
  printf '%s\n' "$NON_CODE" | sed 's/^/  /' >&2
  echo "[scope] 重型构建不可跳过（gov-local-ci 亦应本地跑过）" >&2
else
  echo "code=false"
  echo "[scope] 判定 code=false：全部变更落在非构建类路径，跳过重型 gradle 构建" >&2
  echo "[scope] 覆盖路径: docs/ design/ scripts/ tests/ .ai/ .cache/ .opencode/ .claude/ *.md mkdocs.yml" >&2
  echo "[scope] gov-validate 与 pytest 仍由 docs-validate job 执行，门禁不空缺" >&2
fi
