#!/usr/bin/env bash
# ============================================================
# scripts/governance/metrics-hooks/00-document.sh
# 轻衡健康 Android 指标文档（项目本地化）
#
# 由 gov-update-metrics.sh 调用：stdout 非空时作为整篇
# docs/ai/lifecycle/06_Metrics.md，取代模板内置的通用指标
# （Shell / Python 脚本行数对本 Android 工程没有意义）。
#
# 约定:
#   输出完整 Markdown 正文；数值全部实时计算，不写死。
#   设 METRICS_SKIP_VALIDATE=1 可跳过 gov-validate.sh 调用（供测试用，
#   避免 pytest -> 钩子 -> gov-validate -> pytest 递归）。
#
# 本目录 metrics-hooks/ 是项目自有扩展区，不在 gov-owned.list，升级不覆盖。
# ============================================================
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
cd "$ROOT"

MODULES="domain application infrastructure app"

_files() {  # $1 = main | test
  find $MODULES -type f \( -name '*.kt' -o -name '*.java' \) \
    -path "*/src/$1/*" -not -path '*/build/*' 2>/dev/null
}
_count() { _files "$1" | wc -l | tr -d ' ' || true; }
_lines() {
  [ "$(_count "$1")" = "0" ] && { echo 0; return; }
  _files "$1" | tr '\n' '\0' | xargs -0 wc -l 2>/dev/null | tail -1 | awk '{print $1}'
}

MAIN_FILES=$(_count main);  MAIN_FILES=${MAIN_FILES:-0}
MAIN_LOC=$(_lines main);    MAIN_LOC=${MAIN_LOC:-0}
TEST_FILES=$(_count test);  TEST_FILES=${TEST_FILES:-0}
TEST_LOC=$(_lines test);    TEST_LOC=${TEST_LOC:-0}

TESTS=$(find $MODULES -type f \( -name '*.kt' -o -name '*.java' \) -path '*/src/test/*' \
  -not -path '*/build/*' -print0 2>/dev/null | xargs -0 grep -ho '@Test' 2>/dev/null | wc -l | tr -d ' ' || true)
TESTS=${TESTS:-0}

GRADLE_MODULES=$(grep -oE 'include\([^)]*\)' settings.gradle.kts 2>/dev/null | grep -oE ':[a-z]+' | wc -l | tr -d ' ' || true)
GRADLE_MODULES=${GRADLE_MODULES:-0}

PROTOCOLS=$(grep -rl 'implements ScaleProtocol' domain/src/main 2>/dev/null | wc -l | tr -d ' ' || true)
PROTOCOLS=${PROTOCOLS:-0}
ALGOS=$(grep -rl 'implements BodyComposition' domain/src/main 2>/dev/null | wc -l | tr -d ' ' || true)
ALGOS=${ALGOS:-0}

GOLDEN=0
GOLDEN_FILE="domain/src/test/resources/golden/body-composition-vectors.tsv"
[ -f "$GOLDEN_FILE" ] && GOLDEN=$(( $(wc -l < "$GOLDEN_FILE") - 1 ))

if [ "${MAIN_LOC:-0}" -gt 0 ]; then
  RATIO=$(awk -v t="$TEST_LOC" -v m="$MAIN_LOC" 'BEGIN{printf "%d%%", int(t*100/m+0.5)}')
else
  RATIO="—"
fi

DEC=$(find docs/ai/decisions -name 'DEC-*.md' 2>/dev/null | wc -l | tr -d ' ' || true)
NOTE=$(find docs/notes -name 'NOTE-*.md' 2>/dev/null | wc -l | tr -d ' ' || true)
GC=$(find docs/guides -name 'GC-*.md' 2>/dev/null | wc -l | tr -d ' ' || true)
REF=$(find docs/reference -name 'REF-*.md' 2>/dev/null | wc -l | tr -d ' ' || true)
LIFECYCLE=$(find docs/ai/lifecycle -maxdepth 1 -name '0[1-6]_*.md' 2>/dev/null | wc -l | tr -d ' ' || true)
GOLDEN_DOCS=$(ls docs/architecture/index.md docs/guides/index.md docs/reference/index.md \
  docs/changelog/index.md docs/notes/index.md 2>/dev/null | wc -l | tr -d ' ' || true)
DEC=${DEC:-0}; NOTE=${NOTE:-0}; GC=${GC:-0}; REF=${REF:-0}
LIFECYCLE=${LIFECYCLE:-0}; GOLDEN_DOCS=${GOLDEN_DOCS:-0}

if [ "${METRICS_SKIP_VALIDATE:-0}" = "1" ]; then
  VIOLATIONS="—"
else
  VIOLATIONS=$(bash scripts/governance/gov-validate.sh 2>&1 \
    | grep -E '^  违规:' | grep -oE '[0-9]+' | head -1 || true)
  VIOLATIONS=${VIOLATIONS:-?}
fi

ITER=$(grep -oE '本迭代：[^（(]+' docs/ai/lifecycle/03_TaskGraph.md 2>/dev/null \
  | head -1 | sed 's/本迭代：//' || true)
ITER=${ITER:-—}
TASKS=$(python3 -c "import json;print(len(json.load(open('.ai/tasks.json')).get('tasks',[])))" 2>/dev/null || echo 0)

NOW=$(date +%Y-%m-%d)

cat << METRICSEOF
# 指标

> 轻衡健康 Android（lightweigh）的量化指标，数值实时计算、随代码自动更新。
> 覆盖型文档：由 gov-update-metrics.sh 重写（本地化钩子 metrics-hooks/00-document.sh）。

---

## 1. 代码规模

| 指标 | 当前值 | 说明 |
|------|--------|------|
| Gradle 模块 | ${GRADLE_MODULES} | domain / application / infrastructure / app |
| 源文件 | ${MAIN_FILES} | Kotlin + Java（各模块 src/main） |
| 源码行数 | ${MAIN_LOC} | 四模块合计 |
| 测试文件 | ${TEST_FILES} | 各模块 src/test |
| 测试代码行数 | ${TEST_LOC} | |
| 测试 / 源码行比 | ${RATIO} | 测试行 ÷ 源码行 |

## 2. 领域资产

| 指标 | 当前值 | 说明 |
|------|--------|------|
| 协议实现 | ${PROTOCOLS} | okok-1 / okok-broadcast-1 / chipsea-cloud-1 |
| 体成分算法 | ${ALGOS} | builtin-1 / okok-bia-1 |
| 黄金向量 | ${GOLDEN} | 体成分跨端一致性向量（domain/src/test/resources/golden） |
| 领域层纯度 | 100% | :domain 不引入 Android 依赖，编译期强制 |

## 3. 质量门禁

| 指标 | 当前值 | 说明 |
|------|--------|------|
| 文档校验违规 | ${VIOLATIONS} | gov-validate.sh |
| 单元测试方法 | ${TESTS} | @Test（debug / release 变体各执行一遍） |

## 4. 文档健康

| 指标 | 当前值 | 说明 |
|------|--------|------|
| 决策日志 (DEC) | ${DEC} | 已记录的工程决策 |
| 设计笔记 (NOTE) | ${NOTE} | 讨论 / 设计笔记 |
| 开发指南 (GC) | ${GC} | 已发布的规范指南 |
| 技术参考 (REF) | ${REF} | 已发布的技术参考 |
| 生命周期文档 | ${LIFECYCLE}/6 | PRD / 架构 / 任务图 / 开发笔记 / 测试 / 指标 |
| 黄金标准 | ${GOLDEN_DOCS}/5 | architecture / guides / reference / changelog / notes |

## 5. 迭代进度

| 指标 | 当前值 |
|------|--------|
| 当前迭代 | ${ITER} |
| 任务板条目 | ${TASKS} |

---

> 自动更新于 ${NOW}
METRICSEOF
