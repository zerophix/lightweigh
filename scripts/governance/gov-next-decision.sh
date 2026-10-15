#!/usr/bin/env bash
# ============================================================
# scripts/governance/gov-next-decision.sh
# 决策编号自增分配 — 防止多 AI 写入冲突
#
# 用法:
#   bash scripts/governance/gov-next-decision.sh              # 输出下一个编号（如 DEC-004）
#   bash scripts/governance/gov-next-decision.sh --file        # 输出完整文件路径
#   bash scripts/governance/gov-next-decision.sh --create      # 创建空模板文件并输出路径
#   bash scripts/governance/gov-next-decision.sh --validate-003  # 验证 DEC-003 是否被占用
# ============================================================

set -euo pipefail

_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$_DIR/../lib/lib-root.sh"
source "$_DIR/../lib/lib-platform.sh" 2>/dev/null || true
ROOT="$PROJECT_ROOT"
DECISIONS_DIR="$ROOT/docs/ai/decisions"
INDEX_FILE="$DECISIONS_DIR/index.md"
LOCK_DIR="$DECISIONS_DIR/.lock"

# 原子锁：防止多 AI 同时创建导致编号冲突（DEC-002 自 wf-next-decision.sh 迁入）
acquire_lock() {
    # 清理超过 60 秒的残留锁（防止 SIGKILL 后死锁）
    if [ -d "$LOCK_DIR" ]; then
        LOCK_AGE=$(($(date +%s) - $(file_mod_time "$LOCK_DIR" 2>/dev/null || echo 0)))
        if [ "$LOCK_AGE" -gt 60 ] 2>/dev/null; then
            rmdir "$LOCK_DIR" 2>/dev/null || true
        fi
    fi
    while ! mkdir "$LOCK_DIR" 2>/dev/null; do
        sleep 0.$((RANDOM % 100))
    done
    trap 'rmdir "$LOCK_DIR" 2>/dev/null || true' EXIT
}

# 扫描所有 DEC-XXX.md，找最大编号
MAX_NUM=0
for F in "$DECISIONS_DIR"/DEC-*.md; do
    [ -f "$F" ] || continue
    BASE=$(basename "$F" .md)
    if [[ "$BASE" =~ ^DEC-([0-9]{3})$ ]]; then
        NUM=$((10#${BASH_REMATCH[1]}))
        if [ "$NUM" -gt "$MAX_NUM" ]; then
            MAX_NUM=$NUM
        fi
    fi
done

NEXT_NUM=$((MAX_NUM + 1))
NEXT_DEC=$(printf "DEC-%03d" "$NEXT_NUM")
NEXT_PATH="$DECISIONS_DIR/$NEXT_DEC.md"

# 参数处理
case "${1:-}" in
    --file)
        echo "$NEXT_PATH"
        ;;
    --create)
        acquire_lock
        # 锁内重新扫描编号（防止抢锁期间编号被占用）
        MAX_NUM=0
        for F in "$DECISIONS_DIR"/DEC-*.md; do
            [ -f "$F" ] || continue
            BASE=$(basename "$F" .md)
            if [[ "$BASE" =~ ^DEC-([0-9]{3})$ ]]; then
                NUM=$((10#${BASH_REMATCH[1]}))
                [ "$NUM" -gt "$MAX_NUM" ] && MAX_NUM=$NUM
            fi
        done
        NEXT_NUM=$((MAX_NUM + 1))
        NEXT_DEC=$(printf "DEC-%03d" "$NEXT_NUM")
        NEXT_PATH="$DECISIONS_DIR/$NEXT_DEC.md"

        if [ -f "$NEXT_PATH" ]; then
            echo "错误: $NEXT_PATH 已存在" >&2
            exit 1
        fi
        cat > "$NEXT_PATH" <<TEMPLATE
---
task_groups:
  - id: A
    status: pending
    priority: P0
    desc: "<第 1 步待办>"
    files: "<关联文件>"
    claimed_by: null
    claimed_at: null
  - id: B
    status: pending
    priority: P1
    desc: "<第 2 步待办>"
    files: "<关联文件>"
    claimed_by: null
    claimed_at: null
---

# $NEXT_DEC: <决策标题>

> <一句话描述>

---

## 决策

<决策内容>

## Assumptions

| ID | 假设 | 依赖条件 | 失败影响 | 验证状态 |
|----|------|----------|----------|:--------:|
| A1 | <假设> | <依赖什么> | <如果错了会怎样> |  待验证 |

## 方案评分

| 方案 | 评分 | 说明 |
|------|------|------|
| <方案A>（采纳） | 5/5 | <理由> |
| <方案B> | 3/5 | <理由> |

## 状态

- [ ] 决策时间: $(date +%Y-%m-%d)
- [ ] 决策者: ${BRIDGE_AI_ID:-${AGENT_ID:-AI}}
- [ ] 来源: <触发条件>
TEMPLATE
        echo "已创建: $NEXT_PATH"
        echo "编号: $NEXT_DEC"
        ;;
    --validate-*)
        CHECK_DEC="${1#--validate-}"
        CHECK_NUM=$(echo "$CHECK_DEC" | grep -oE '[0-9]+$' || echo "")
        if [ -n "$CHECK_NUM" ]; then
            CHECK_PATH="$DECISIONS_DIR/DEC-$(printf "%03d" "$CHECK_NUM").md"
            if [ -f "$CHECK_PATH" ]; then
                echo "占用: $CHECK_PATH"
                exit 1
            else
                echo "可用"
                exit 0
            fi
        fi
        echo "参数格式: --validate-DEC-XXX" >&2
        exit 1
        ;;
    *)
        echo "$NEXT_DEC"
        ;;
esac
