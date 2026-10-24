#!/usr/bin/env bash
# ============================================================
# scripts/governance/gov-ci-scope.sh
# CI 变更范围判定 — 决定 build-and-guard 是否执行重型 gradle 构建
#
# 动机（DEC-058）: 原内联正则只认 docs/ 与 *.md，导致 scripts/、tests/、
# design/、.opencode/ 这类**不参与 gradle 构建**的变更被判定为 code=true，
# 白跑全量构建。pull_request 事件以 master 为基线（整条分支），
# 因此开 PR 时几乎必然触发重型构建。
#
# 用法:
#   git diff --name-only A B | bash scripts/governance/gov-ci-scope.sh
#   git diff --name-only A B | bash scripts/governance/gov-ci-scope.sh --print-lane
#
# 两种模式（DEC-064）:
#   scope       stdout 仅一行 `code=true` 或 `code=false` —— 供 CI 追加到 $GITHUB_OUTPUT
#   --print-lane 纯查询模式：按变更路径算出风险车道，stdout 为机器可读的
#                 `lane=<A|B|C|full>` / `forced=<true|false>` /
#                 `reason=<决定因素>` / `path=<变更路径>` 行。
#                 供 gov-local-ci.sh 消费，也供 tests/unit/test_ci_lane.py 直接断言。
#                 stderr 为人读诊断，不污染 stdout。
#
# 风险车道（DEC-064）:
#   A  不参与 gradle 构建的路径（docs/ design/ .github/ .ai/ .cache/ .opencode/
#     .claude/ scripts/ tests/ *.md mkdocs.yml 以及 .gitignore 等配置类文件）
#     → 只跑 gov-validate + custom-checks + secrets + schema-guard + test-guard
#   B  app/src/** 且未触及构建文件 → 只跑 :app:testDebugUnitTest，不跑全量 build
#   C  domain/ application/ infrastructure/ golden/ schemas / gradle 构建文件 /
#     未知路径 / 判定失败 → 保持全量 ./gradlew build
#   混合变更取最高车道（C > B > A）；空输入或检测失败一律 C（保守）。
#
# 环境变量:
#   GOV_LANE=auto|A|B|C|full  覆盖自动判定。full 等价 C（全量 build）。
#                              默认 auto。非法值按 auto 处理并在 stderr 告警。
#
# 安全性（治理设计约束 Q4）:
#   采用**白名单**而非黑名单 —— 只有落在已知非构建路径内的变更才跳过构建。
#   gradle 模块固定为 app / application / domain / infrastructure 加根部
#   构建脚本，它们**均不在白名单内**，故只要动到任一源码或构建文件，
#   判定必为 code=true。扩展白名单只减少假阳性，不会让真源码漏过。
#
#   车道判定额外加一道源码扩展名闸门（LANE_C_EXT_RE）：即使某源码文件
#   恰好落在 docs/ 之类白名单目录下，也一律升到 C，避免出现「文档目录里
#   藏真实代码」的盲点。
#
#   保守分支: 空输入或无法列举变更 → code=true / lane=C（宁可多跑一轮，
#   也不留门禁空洞，与 ci.yml 中「基线不可用按代码处理」一致）。
# ============================================================
set -euo pipefail

# 变更路径全部落在本模式内 → 非构建类，跳过重型构建
ALLOW_RE='^(docs/|design/|scripts/|tests/|\.ai/|\.cache/|\.opencode/|\.claude/|mkdocs\.yml$|.*\.md$)'

# ---- 车道模式（DEC-064）的分类规则 -------------------------
# A: 非构建类路径 + CI 配置 + 仓库配置类文件
LANE_A_RE='^(docs/|design/|\.github/|\.ai/|\.cache/|\.opencode/|\.claude/|scripts/|tests/|mkdocs\.yml$|.*\.md$|\.gitignore$|\.gitattributes$|\.editorconfig$|\.language$|LICENSE$)'
# B: 仅 app 模块源码（可只跑 :app:testDebugUnitTest）
LANE_B_RE='^app/src/'
# C 特征: 源码/构建产物扩展名。命中即 C，优先于 A（防止白名单目录藏代码）
LANE_C_EXT_RE='\.(kt|kts|java|xml|toml|properties|pro|gradle|jar|aar)$'
# 稳定的车道排序辅助: A=1 B=2 C=3，取最大
lane_rank() {
  case "$1" in
    A) echo 1 ;;
    B) echo 2 ;;
    *) echo 3 ;;
  esac
}

# 单文件 → 车道。顺序：B（app/src）> C（源码扩展名/构建文件）> A（白名单）> C（未知）
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

# 规范化 GOV_LANE。未设置/非法一律 auto。
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

# 去空行
FILES="$(printf '%s\n' "$FILES" | sed '/^[[:space:]]*$/d')"

# ---------------------------------------------------------------- 车道模式
if [ "$MODE" = "lane" ]; then
  FORCED="$(normalize_forced_lane)"

  if [ -n "$FORCED" ] && [ "$FORCED" != "auto" ]; then
    # 强制指定：路径判定仅供参考（作为 path= 上下文输出）
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
    # 保守：diff 为空或检测失败时不猜，按全量处理
    echo "lane=C"
    echo "forced=false"
    echo "reason=未列举到变更文件（diff 为空或检测失败）→ 保守按 C 处理"
    echo "[lane] 判定 lane=C：无变更清单可判定，保守跑全量" >&2
    exit 0
  fi

  DECIDED=""       # 当前最高车道（空 = 尚未命中）
  DECIDED_RANK=0
  REASONS=()
  # 第一轮：求最高车道
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
  # 决定因素：命中最该车道的路径（最多 20 条，避免超长输出）
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
