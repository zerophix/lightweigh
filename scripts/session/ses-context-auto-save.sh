#!/usr/bin/env bash
# ============================================================
# scripts/session/ses-context-auto-save.sh
# AI 上下文自动保存辅助 — 让 AI 端通过一条命令完成 context 写入
#
# 前置约束：pre-commit hook 会在每次 commit 时调用 infer-completion 和 record-decision
#           兜底自动更新，保证 context.jsonl 不会断更
# 中置约束：AI 在每个迭代步完成后应主动调用本脚本
# 后置约束：validate.sh GOV-CONTENT-008（记忆格式）+ GOV-CONTENT-009（时效性 30 天）
#
# 用法:
#   bash scripts/session/ses-context-auto-save.sh complete "完成了什么" --dec DEC-XXX
#   bash scripts/session/ses-context-auto-save.sh handoff --next "下一步描述" --dec DEC-XXX
#   bash scripts/session/ses-context-auto-save.sh learning "学到了什么" --principle "原则"
#   bash scripts/session/ses-context-auto-save.sh record-decision docs/ai/decisions/DEC-030.md
#   bash scripts/session/ses-context-auto-save.sh infer-completion "$(git diff --name-only)"
# ============================================================

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
CACHE="$ROOT/scripts/session/ses-context-cache.sh"

# ---------- 辅助函数 ----------

now() { date -u +"%Y-%m-%dT%H:%M:%SZ"; }
agent() { hostname -s 2>/dev/null || echo "unknown"; }

# ---------- 子命令 ----------

case "${1:-}" in
  --help|-h)
    echo "用法: bash scripts/session/ses-context-auto-save.sh <command> [args]"
    echo ""
    echo "命令:"
    echo "  complete <描述> --dec <DEC>           任务完成，更新 current-task"
    echo "  handoff --next <下一步> --dec <DEC>   交接上下文"
    echo "  learning <描述> --principle <原则>    学到新知识"
    echo "  record-decision <DEC文件路径>         记录决策到 context"
    echo "  infer-completion <变更文件列表>       pre-commit 自动推断（内部使用）"
    exit 0
    ;;

  complete)
    DESC="${2:-}"
    DEC="${3:-}"
    if [ -z "$DESC" ]; then
      echo "用法: ... complete <描述> --dec DEC-XXX" >&2; exit 1
    fi
    # 将上一个 current-task 标记完成
    bash "$CACHE" save learning \
      what="任务完成: $DESC" \
      context="completed at $(now)" \
      principle="完成即记录，不拖延" \
      2>/dev/null || true
    # 写入新的 current-task（空，表示等待下一个任务）
    bash "$CACHE" save current-task \
      task="" \
      status="idle" \
      started_at="$(now)" \
      2>/dev/null || true
    echo " current-task 已更新: $DESC"
    ;;

  handoff)
    shift
    NEXT=""
    DEC=""
    FILES=""
    CONTEXT=""
    while [ $# -gt 0 ]; do
      case "$1" in
        --next) NEXT="$2"; shift 2 ;;
        --dec)  DEC="$2"; shift 2 ;;
        --files) FILES="$2"; shift 2 ;;
        --context) CONTEXT="$2"; shift 2 ;;
        *) shift ;;
      esac
    done
    if [ -z "$NEXT" ] && [ -z "$DEC" ]; then
      echo "用法: ... handoff --next <下一步> --dec DEC-XXX" >&2; exit 1
    fi
    bash "$CACHE" save handoff \
      from="$(agent)" \
      to="next-ai" \
      context="${CONTEXT:-handoff at $(now)}" \
      next="${NEXT:-see TaskGraph}" \
      key_decisions="${DEC:-}" \
      key_files="${FILES:-}" \
      2>/dev/null || true
    echo " handoff 已写入: next=$NEXT"
    ;;

  learning)
    WHAT="${2:-}"
    PRINCIPLE="${3:-}"
    if [ -z "$WHAT" ]; then
      echo "用法: ... learning <描述> --principle <原则>" >&2; exit 1
    fi
    bash "$CACHE" save learning \
      what="$WHAT" \
      context="learned at $(now)" \
      principle="$PRINCIPLE" \
      2>/dev/null || true
    echo " learning 已写入: $WHAT"
    ;;

  record-decision)
    DEC_FILE="${2:-}"
    if [ -z "$DEC_FILE" ] || [ ! -f "$DEC_FILE" ]; then
      echo "错误: 决策文件不存在: $DEC_FILE" >&2; exit 1
    fi
    DEC_ID=$(basename "$DEC_FILE" .md)
    DEC_TITLE=$(head -1 "$DEC_FILE" | sed 's/^# *//' | tr '"' "'" | cut -c1-80)
    bash "$CACHE" save decision-context \
      dec="$DEC_ID" \
      title="$DEC_TITLE" \
      source_file="$DEC_FILE" \
      2>/dev/null || true
    echo " decision-context 已写入: $DEC_ID"
    ;;

  infer-completion)
    # pre-commit 内部调用：根据变更文件推断任务完成情况
    CHANGED="${1:-}"
    if [ -z "$CHANGED" ]; then
      exit 0
    fi
    # 如果有 src/ 变更，推断有任务完成
    if echo "$CHANGED" | grep -qE "(^| )(src/|docs/ai/lifecycle/)"; then
      SCOPES=$(echo "$CHANGED" | grep -oE "(src/|docs/ai/lifecycle/[^/]+)" | sort -u | tr '\n' ',' | sed 's/,$//')
      bash "$CACHE" save current-task \
        task="inferred: $SCOPES" \
        status="in_progress" \
        started_at="$(now)" \
        2>/dev/null || true
    fi
    ;;

  *)
    echo "未知命令: ${1:-}" >&2
    echo "运行 bash scripts/session/ses-context-auto-save.sh --help 查看用法" >&2
    exit 1
    ;;
esac
