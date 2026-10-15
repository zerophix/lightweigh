#!/usr/bin/env bash
# ============================================================
# scripts/workflow/wf-lifecycle.sh
# 生命周期一站式收尾 — 迭代 V→M→L→D→P 自动化
#
# 被 GC-decision-flow.md 引用，作为完整工作流示例的最后一步。
#
# 用法:
#   bash scripts/workflow/wf-lifecycle.sh end "<摘要>"          # 结束当前迭代
#   bash scripts/workflow/wf-lifecycle.sh end "<摘要>" --dry-run  # 预览模式
#   bash scripts/workflow/wf-lifecycle.sh begin                 # 开始新迭代
#   bash scripts/workflow/wf-lifecycle.sh status                # 显示生命周期状态
# ============================================================

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$ROOT/scripts/lib/lib-platform.sh" 2>/dev/null || true
CACHE_SCRIPT="$ROOT/scripts/session/ses-context-cache.sh"
VALIDATE_SCRIPT="$ROOT/scripts/governance/gov-validate.sh"
STATUS_SCRIPT="$ROOT/scripts/governance/gov-update-status.sh"

DRY_RUN=false

# ---------- 颜色 ----------
if [ -t 1 ]; then
    RED='\033[0;31m'
    GREEN='\033[0;32m'
    YELLOW='\033[1;33m'
    BOLD='\033[1m'
    NC='\033[0m'
else
    RED='' GREEN='' YELLOW='' BOLD='' NC=''
fi

show_help() {
    cat <<EOF
用法: bash scripts/workflow/wf-lifecycle.sh <command> [args]

命令:
  end "<摘要>"    — 结束当前迭代：验证 → 更新状态 → 记录交接
                    可选: --dec DEC-XXX 指定决策编号
                          --dry-run     预览不执行
  begin            — 开始新迭代：初始化上下文
  status           — 显示生命周期状态

示例:
  bash scripts/workflow/wf-lifecycle.sh end "统一 API 响应格式"
  bash scripts/workflow/wf-lifecycle.sh end "完成用户认证模块" --dec DEC-005
  bash scripts/workflow/wf-lifecycle.sh begin
EOF
    exit 0
}

# ---------- 生命周期状态 ----------

get_step() {
    # 从 context.jsonl 读取当前生命周期步骤
    if [ -f "$CACHE_SCRIPT" ]; then
        bash "$CACHE_SCRIPT" get current-task --limit 1 2>/dev/null | python3 -c "
import json, sys
for block in sys.stdin.read().split('---'):
    block = block.strip()
    if not block: continue
    try:
        e = json.loads(block)
        print(e.get('step', '?'))
        break
    except: pass
" 2>/dev/null || echo "?"
    else
        echo "?"
    fi
}

# ---------- 命令: status ----------

cmd_status() {
    echo -e "${BOLD}========================================${NC}"
    echo -e "${BOLD}  生命周期状态${NC}"
    echo -e "${BOLD}========================================${NC}"
    echo ""

    # 当前步骤
    STEP=$(get_step)
    echo "  当前步骤: $STEP"

    # 迭代计数
    DEC_COUNT=$(find "$ROOT/docs/ai/decisions" -name "DEC-*.md" -type f 2>/dev/null | wc -l | tr -d ' ')
    NOTE_COUNT=$(find "$ROOT/docs/notes" -name "NOTE-*.md" -type f 2>/dev/null | wc -l | tr -d ' ')
    echo "  决策总数: $DEC_COUNT"
    echo "  笔记总数: $NOTE_COUNT"

    # 最新提交
    LAST_COMMIT=$(git -C "$ROOT" log -1 --oneline 2>/dev/null || echo "无")
    echo "  最后提交: $LAST_COMMIT"

    # 校验状态
    echo ""
    if [ -f "$VALIDATE_SCRIPT" ]; then
        echo "  运行校验检查..."
        bash "$VALIDATE_SCRIPT" 2>&1 | grep -E "结论|违规" || echo "    (校验脚本失败)"
    fi

    echo ""
    echo "  完整状态: bash scripts/governance/gov-update-status.sh && cat STATUS.md"
}

# ---------- 命令: begin ----------

cmd_begin() {
    echo -e "${BOLD}开始新迭代...${NC}"
    echo ""

    # 检查是否需要初始化
    if [ -f "$CACHE_SCRIPT" ]; then
        # 检查是否有未结束的任务
        EXISTING=$(bash "$CACHE_SCRIPT" get current-task --limit 1 2>/dev/null | python3 -c "
import json, sys
for block in sys.stdin.read().split('---'):
    block = block.strip()
    if not block: continue
    try:
        e = json.loads(block)
        print(e.get('task', ''))
        break
    except: pass
" 2>/dev/null)
        if [ -n "$EXISTING" ]; then
            echo -e "  ${YELLOW}检测到未结束的迭代: $EXISTING${NC}"
            echo "  建议先运行 lifecycle.sh end 结束当前迭代"
            echo ""
        fi
    fi

    # 创建新条目
    ITER_NUM=$(( $(find "$ROOT/docs/ai/decisions" -name "DEC-*.md" -type f 2>/dev/null | wc -l | tr -d ' ') + 1 ))
    echo "  新建迭代 #$ITER_NUM"

    # 刷新状态
    if [ -f "$STATUS_SCRIPT" ]; then
        bash "$STATUS_SCRIPT" 2>&1 | tail -1
    fi

    echo ""
    echo -e "${GREEN}迭代 #$ITER_NUM 已开始。${NC}"
    echo "  下一步:  完成迭代内容 → lifecycle.sh end"
}

# ---------- 命令: end ----------

cmd_end() {
    local SUMMARY="${1:-}"
    local DEC_ID=""

    # 解析参数
    shift 2>/dev/null || true
    while [ $# -gt 0 ]; do
        case "$1" in
            --dec) DEC_ID="$2"; shift 2 ;;
            --dry-run) DRY_RUN=true; shift ;;
            *) echo "未知参数: $1"; shift ;;
        esac
    done

    if [ -z "$SUMMARY" ]; then
        echo -e "${RED}错误: 缺少迭代摘要参数${NC}" >&2
        echo "用法: bash scripts/workflow/wf-lifecycle.sh end \"<摘要>\"" >&2
        exit 1
    fi

    echo -e "${BOLD}========================================${NC}"
    echo -e "${BOLD}  生命周期结束 — 迭代收尾${NC}"
    echo -e "${BOLD}========================================${NC}"
    echo "  摘要: $SUMMARY"
    $DRY_RUN && echo "  ${YELLOW}[DRY-RUN] 预览模式，不会实际写入${NC}"
    echo ""

    # ── 步骤 V: 校验 ──
    if $DRY_RUN; then
        echo -e "  ${YELLOW}[V]${NC} 校验 (跳过: dry-run)"
    else
        echo -e "  ${GREEN}[V]${NC} 校验..."
        if [ -f "$VALIDATE_SCRIPT" ]; then
            bash "$VALIDATE_SCRIPT" || {
                echo -e "  ${RED}[FAIL] 校验失败，中止收尾${NC}"
                echo "  请修复后重试，或使用 --no-verify 临时跳过"
                exit 1
            }
        fi
    fi

    # ── 步骤 M: 更新指标 ──
    if $DRY_RUN; then
        echo -e "  ${YELLOW}[M]${NC} 更新状态 (跳过: dry-run)"
    else
        echo -e "  ${GREEN}[M]${NC} 更新状态..."
        if [ -f "$STATUS_SCRIPT" ]; then
            bash "$STATUS_SCRIPT" 2>&1 | tail -1
        fi
        # 尝试更新 metrics（可选，不阻塞）
        if [ -f "$ROOT/scripts/governance/gov-update-metrics.sh" ]; then
            bash "$ROOT/scripts/governance/gov-update-metrics.sh" 2>/dev/null || true
        fi
    fi

    # ── 步骤 L: 记录决策 ──
    if $DRY_RUN; then
        echo -e "  ${YELLOW}[L]${NC} 记录决策 (跳过: dry-run)"
    else
        if [ -z "$DEC_ID" ]; then
            echo -e "  ${GREEN}[L]${NC} 创建 DEC 占位..."
            DEC_OUTPUT=$(bash "$ROOT/scripts/governance/gov-next-decision.sh" --create 2>&1 || true)
            echo "    $DEC_OUTPUT"
            # 提取 DEC-ID
            DEC_ID=$(echo "$DEC_OUTPUT" | grep "编号:" | sed 's/.*编号: *//' || true)
        else
            echo -e "  ${GREEN}[L]${NC} 使用已有决策: $DEC_ID"
        fi
    fi

    # ── 步骤 D + P: 更新文档 & 晋升 ──
    if $DRY_RUN; then
        echo -e "  ${YELLOW}[D]${NC} 更新生命周期文档 (跳过: dry-run)"
        echo -e "  ${YELLOW}[P]${NC} 晋升黄金标准 (跳过: dry-run)"
    else
        echo -e "  ${GREEN}[D]${NC} 生命周期文档覆盖..."

        # 更新 06_Metrics.md 的日期
        TODAY=$(date +%Y-%m-%d)
        METRICS_FILE="$ROOT/docs/ai/lifecycle/06_Metrics.md"
        if [ -f "$METRICS_FILE" ]; then
            sed_i "s|{{date}}|$TODAY|g" "$METRICS_FILE" 2>/dev/null || true
        fi

        # 晋升
        if [ -n "$DEC_ID" ] && [ -f "$ROOT/scripts/governance/gov-sync-standards.sh" ]; then
            echo -e "  ${GREEN}[P]${NC} 同步黄金标准..."
            bash "$ROOT/scripts/governance/gov-sync-standards.sh" --dec "$DEC_ID" 2>&1 | tail -3 || {
                echo -e "  ${YELLOW}[WARN]${NC} 同步失败（可能无 staged 变更）"
            }
        fi
    fi

    # ── 记录交接 → context cache ──
    if ! $DRY_RUN && [ -f "$CACHE_SCRIPT" ]; then
        echo -e "  ${GREEN}[save]${NC} 记录 session 摘要..."
        bash "$CACHE_SCRIPT" save handoff \
            from="lifecycle.sh" \
            context="$SUMMARY" \
            next="" \
            key_files="$DEC_ID" \
            lessons="自动收尾: $SUMMARY" \
            2>/dev/null || true

        bash "$CACHE_SCRIPT" save session-summary \
            dec="$DEC_ID" \
            scope="$SUMMARY" \
            2>/dev/null || true

        bash "$CACHE_SCRIPT" save current-task \
            task="$SUMMARY" \
            status="completed" \
            dec="$DEC_ID" \
            started_at="$TODAY" \
            2>/dev/null || true
    fi

    echo ""
    echo -e "${BOLD}========================================${NC}"
    if $DRY_RUN; then
        echo -e "${YELLOW}  预览完成（未实际写入）${NC}"
    else
        echo -e "${GREEN}  迭代收尾完成${NC}"
    fi
    echo -e "${BOLD}========================================${NC}"
}

# ============================================================
# 入口
# ============================================================

case "${1:-}" in
    end)
        shift
        cmd_end "$@"
        ;;
    begin)
        cmd_begin
        ;;
    status)
        cmd_status
        ;;
    --help|-h|help)
        show_help
        ;;
    *)
        echo -e "${RED}未知命令: ${1:-}${NC}" >&2
        echo ""
        show_help
        ;;
esac
