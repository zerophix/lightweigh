#!/usr/bin/env bash
# ============================================================
# scripts/governance/gov-affected.sh
# 变更集 → 受影响 pytest 用例（DEC-072：把"全量 333 条"换成"相关的几条"）
#
# 用法:
#   gov-affected.sh                 # 工作区改动（含未跟踪）→ 打印受影响测试
#   gov-affected.sh --staged        # 只看暂存区
#   gov-affected.sh --base origin/master
#   gov-affected.sh --run           # 直接执行受影响测试
#   gov-affected.sh --json
#
# 输出:
#   touched=<n> tests=<n> unmapped=<n> kotlin=<n>
#   tests/unit/test_xxx.py          （每行一个）
#
# 退出码: 0 有映射或无需映射 / 1 存在无法映射的脚本类改动（应回退到慢层）
#
# 设计约束（为什么可以"不准"）:
#   - 映射只求覆盖，不求精确；误判的代价是多跑几条测试，漏判的代价是回退全量。
#   - 任何无法映射的非文档改动都显式报告 unmapped，由调用方决定是否跑全量，
#     绝不静默通过（规则空转比没有规则更危险）。
# ============================================================
set -uo pipefail

DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$DIR/../.." && pwd)"
cd "$ROOT" || exit 2

MODE="worktree"
BASE=""
RUN=0
JSON=0

while [ $# -gt 0 ]; do
  case "$1" in
    --staged) MODE="staged"; shift ;;
    --base) BASE="${2:-}"; MODE="base"; shift 2 ;;
    --run) RUN=1; shift ;;
    --json) JSON=1; shift ;;
    -h|--help) sed -n '2,25p' "$0"; exit 0 ;;
    *) echo "未知参数: $1" >&2; exit 2 ;;
  esac
done

collect_changed() {
  case "$MODE" in
    staged)
      git diff --cached --name-only --diff-filter=ACM 2>/dev/null
      ;;
    base)
      git diff "$BASE"...HEAD --name-only --diff-filter=ACM 2>/dev/null
      ;;
    *)
      # 分支相对 master 的提交 + 工作区改动 + 未跟踪文件。
      # 只取 git diff HEAD 会漏掉分支上已提交的改动，导致"改了却一条测试都不跑"。
      branch_base="$(git merge-base HEAD origin/master 2>/dev/null || echo HEAD~1)"
      git diff "$branch_base"...HEAD --name-only --diff-filter=ACM 2>/dev/null
      git diff HEAD --name-only --diff-filter=ACM 2>/dev/null
      git ls-files --others --exclude-standard 2>/dev/null
      ;;
  esac | sort -u
}

# 文档/设计/数据类改动不产生测试信号
is_static_only() {
  case "$1" in
    docs/*|design/*|.ai/*|.github/*|*.md|*.html|*.css|*.json|*.yaml|*.yml|*.png|*.pdf) return 0 ;;
    *) return 1 ;;
  esac
}

is_kotlin() {
  case "$1" in
    app/*|domain/*|application/*|infrastructure/*|*/src/main/*|*/src/test/*) return 0 ;;
    *) return 1 ;;
  esac
}

CHANGED="$(collect_changed)"
TESTS=""
TOUCHED=0
KOTLIN=0
UNMAPPED=""

add_test() {
  local t="$1"
  [ -f "$t" ] || return 0
  case " $TESTS " in *" $t "*) return 0 ;; esac
  TESTS="$TESTS $t"
}

while IFS= read -r f; do
  [ -n "$f" ] || continue
  TOUCHED=$((TOUCHED + 1))

  # 改动本身是 pytest 文件 → 必须跑它
  case "$f" in
    tests/*.py) add_test "$f"; continue ;;
  esac

  if is_static_only "$f"; then
    continue
  fi

  if is_kotlin "$f"; then
    KOTLIN=$((KOTLIN + 1))
    continue
  fi

  base="$(basename "$f")"
  stem="${base%.*}"
  norm="${stem//-/_}"
  before=$TESTS

  # 1) 同名测试
  add_test "tests/unit/test_${norm}.py"
  # 2) 测试正文提到该文件名/模块名的
  while IFS= read -r hit; do
    [ -n "$hit" ] && add_test "$hit"
  done < <(grep -rlF --include='*.py' -- "$base" tests/unit 2>/dev/null || true)
  # 3) 治理模块：gov_validate_x.py → 提到该模块的测试
  case "$base" in
    gov_validate_*.py)
      mod="gov_validate_${base#gov_validate_}"; mod="${mod%.py}"
      while IFS= read -r hit; do
        [ -n "$hit" ] && add_test "$hit"
      done < <(grep -rlF --include='*.py' -- "$mod" tests/unit 2>/dev/null || true)
      ;;
  esac

  if [ "$before" = "$TESTS" ]; then
    UNMAPPED="$UNMAPPED $f"
  fi
done <<< "$CHANGED"

COUNT=$(printf '%s' "$TESTS" | wc -w | tr -d ' ')
UNMAPPED_COUNT=$(printf '%s' "$UNMAPPED" | wc -w | tr -d ' ')

if [ "$JSON" = "1" ]; then
  printf '{"touched":%s,"tests":%s,"unmapped":%s,"kotlin":%s,"test_files":[%s]}\n' \
    "$TOUCHED" "$COUNT" "$UNMAPPED_COUNT" "$KOTLIN" \
    "$(for t in $TESTS; do printf '"%s",' "$t"; done | sed 's/,$//')"
else
  echo "touched=$TOUCHED tests=$COUNT unmapped=$UNMAPPED_COUNT kotlin=$KOTLIN"
  for t in $TESTS; do echo "$t"; done
  if [ "$UNMAPPED_COUNT" -gt 0 ]; then
    echo "--- 未映射（建议回退全量）---"
    for u in $UNMAPPED; do echo "$u"; done
  fi
fi

if [ "$RUN" = "1" ]; then
  if [ "$COUNT" -eq 0 ]; then
    echo "[affected] 无相关 pytest 用例，跳过"
    [ "$UNMAPPED_COUNT" -gt 0 ] && exit 1
    exit 0
  fi
  echo "[affected] 运行 $COUNT 个测试文件（并行 + 结果缓存）"
  # shellcheck disable=SC2086  # $TESTS 需要按空格拆成多个文件参数
  bash scripts/testing/gov-pytest.sh --key "affected:$(printf '%s' "$CHANGED" | shasum | cut -d' ' -f1)" $TESTS
  exit $?
fi

[ "$UNMAPPED_COUNT" -gt 0 ] && exit 1
exit 0
