#!/usr/bin/env bash
# ============================================================
# scripts/governance/gov-update-metrics.sh
# 模板化指标更新 — 可插拔钩子设计
#
# 用法:
#   bash scripts/governance/gov-update-metrics.sh                          # 全部更新（按脚本优先级）
#   bash scripts/governance/gov-update-metrics.sh --custom <path>          # 运行自定义指标脚本
#
# 自定义脚本钩子:
#   在 scripts/governance/metrics-hooks/ 目录下放可执行脚本，
#   update-metrics.sh 会自动扫描并依次运行。
#
# 每个钩子脚本接收：
#   $1 = METRICS_FILE 路径（06_Metrics.md 的绝对路径）
#   钩子应输出 | key | value | 格式的行，由主脚本收集写入文件
#
# 示例钩子 scripts/governance/metrics-hooks/10-git-stats.sh:
#   #!/usr/bin/env bash
#   echo "| 提交数 | $(git rev-list --count HEAD) |"
# ============================================================

set -euo pipefail

[ "${1:-}" = "--help" ] || [ "${1:-}" = "-h" ] && {
  echo "用法: bash scripts/governance/gov-update-metrics.sh [--custom <path>]"
  echo "  默认: 扫描 metrics-hooks/ 目录并运行全部钩子"
  echo "  --custom <path>: 运行单个自定义指标脚本"
  exit 0
}

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
METRICS_FILE="$ROOT/docs/ai/lifecycle/06_Metrics.md"
HOOKS_DIR="$(cd "$(dirname "$0")" && pwd)/metrics-hooks"

now=$(date +%Y-%m-%d)

# ---------- 收集指标 ----------
declare -a METRICS
DEC_COUNT=$(find "$ROOT/docs/ai/decisions" -name "DEC-*.md" -type f 2>/dev/null | wc -l | tr -d ' ')
NOTE_COUNT=$(find "$ROOT/docs/notes" -name "NOTE-*.md" -type f 2>/dev/null | wc -l | tr -d ' ')
GC_COUNT=$(find "$ROOT/docs/guides" -name "GC-*.md" -type f 2>/dev/null | wc -l | tr -d ' ')
REF_COUNT=$(find "$ROOT/docs/reference" -name "REF-*.md" -type f 2>/dev/null | wc -l | tr -d ' ')

# --- 代码行数统计 ---
SHELL_LINES=$(find "$ROOT/scripts" -name "*.sh" -type f 2>/dev/null | xargs wc -l 2>/dev/null | tail -1 | awk '{print $1}')
PYTHON_LINES=$(find "$ROOT" -name "*.py" -not -path "*/node_modules/*" -not -path "*/.git/*" -type f 2>/dev/null | xargs wc -l 2>/dev/null | tail -1 | awk '{print $1}')
SCRIPT_COUNT=0; for _d in governance session workflow migration bootstrap lib; do [ -d "$ROOT/scripts/$_d" ] && for _f in "$ROOT/scripts/$_d"/*; do [ -f "$_f" ] && [ -x "$_f" ] && SCRIPT_COUNT=$((SCRIPT_COUNT + 1)); done; done
TOTAL_CODE=$(( ${SHELL_LINES:-0} + ${PYTHON_LINES:-0} + 1 )) # +1 for mkdocs.yml

# --- 生命周期文档完成率 ---
LIFECYCLE_EXIST=$(ls "$ROOT/docs/ai/lifecycle"/0[1-6]_*.md 2>/dev/null | wc -l | tr -d ' ')
LIFECYCLE_TOTAL=6
GOLDEN_COUNT=$(ls "$ROOT/docs/architecture/index.md" "$ROOT/docs/guides/index.md" "$ROOT/docs/reference/index.md" "$ROOT/docs/changelog/index.md" 2>/dev/null | wc -l | tr -d ' ')

# --- 合规状态 ---
VALIDATE_OUTPUT=$(bash "$ROOT/scripts/governance/gov-validate.sh" 2>&1 || true)
VIOLATIONS=$(echo "$VALIDATE_OUTPUT" | grep -c "FAIL" || echo "0")
# VIOLATIONS from the python engine section
VIOLATIONS=$(echo "$VALIDATE_OUTPUT" | grep -E '^  违规:' | grep -oE '[0-9]+' | head -1 || echo "?")

# Run external hooks if available
if [ -d "$HOOKS_DIR" ]; then
  for hook in "$HOOKS_DIR"/*.sh; do
    [ -x "$hook" ] || continue
    HOOK_OUTPUT=$(bash "$hook" "$METRICS_FILE" 2>/dev/null) || true
    if [ -n "$HOOK_OUTPUT" ]; then
      METRICS+=("$HOOK_OUTPUT")
    fi
  done
fi

# ---------- 生成 06_Metrics.md ----------
cat > "$METRICS_FILE" << METRICSEOF
# 指标

> 项目健康状况的可量化指标。每次提交或分析后更新。
> 覆盖型文档：每次迭代重写。

---

## 1. 代码质量

| 指标 | 当前值 | 目标 | 趋势 |
|------|--------|------|------|
| 总代码行数 | ${TOTAL_CODE:-—} | — | — |
| Shell 脚本行数 | ${SHELL_LINES:-—} | — | — |
| Python 脚本行数 | ${PYTHON_LINES:-—} | — | — |
| 可执行脚本数 | ${SCRIPT_COUNT:-—} | — | — |
| 测试覆盖率 | — | >=80% | — |
| 技术债务 | — | — | — |

## 2. 文档健康

| 指标 | 当前值 | 说明 |
|------|--------|------|
| 决策日志 (DEC) | $DEC_COUNT | 已记录的工程决策 |
| 设计笔记 (NOTE) | $NOTE_COUNT | 讨论/设计笔记 |
| 开发指南 (GC) | $GC_COUNT | 已发布的规范指南 |
| 技术参考 (REF) | $REF_COUNT | 已发布的技术参考 |
| 生命周期文档 | $LIFECYCLE_EXIST/$LIFECYCLE_TOTAL | PRD/架构/任务图/开发笔记/测试/指标 |
| 黄金标准 | ${GOLDEN_COUNT:-0} | architecture + guides + reference + changelog |
| 校验违规 | ${VIOLATIONS:-?} | 运行 validate.sh 查看 |

## 3. 迭代进度

| 指标 | 值 |
|------|-----|
| 当前迭代 | — |
| 已完成任务 | — |
| 进行中任务 | — |
| 阻塞任务 | — |

## 4. 自定义指标
METRICSEOF

# Append hook outputs if any
if [ ${#METRICS[@]} -gt 0 ]; then
  echo "" >> "$METRICS_FILE"
  echo "| 指标 | 值 | 来源 |" >> "$METRICS_FILE"
  echo "|------|-----|------|" >> "$METRICS_FILE"
  for m in "${METRICS[@]}"; do
    echo "$m" >> "$METRICS_FILE"
  done
fi

echo "" >> "$METRICS_FILE"
echo "---" >> "$METRICS_FILE"
echo "> 自动更新于 $now" >> "$METRICS_FILE"

echo "已更新: $METRICS_FILE"
echo "  DEC: $DEC_COUNT | NOTE: $NOTE_COUNT | GC: $GC_COUNT | REF: $REF_COUNT"
