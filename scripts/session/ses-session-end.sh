#!/usr/bin/env bash
# ============================================================
# scripts/session/ses-session-end.sh
# Session 收尾与 AI 接力交接 — 闭环强制规范
#
# 每次 AI 协作 session 结束时必须执行。
# 它确保:
#   1. 决策和推理过程不丢失
#   2. 下一个人知道为什么做、做到哪了
#   3. 下一步附带理由，需要人类确认
#   4. 已知风险和陷阱明确记录
#
# 用法:
#   bash scripts/session/ses-session-end.sh                    # 交互式
#   bash scripts/session/ses-session-end.sh --batch <文件>     # 从文件读取
#   bash scripts/session/ses-session-end.sh --check            # 检查未交接项
# ============================================================

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$ROOT/scripts/lib/lib-platform.sh" 2>/dev/null || true

CACHE_SCRIPT="$ROOT/scripts/session/ses-context-cache.sh"
TASK_BOARD="$ROOT/scripts/workflow/wf-task-board.sh"
INBOX_SCRIPT="$ROOT/scripts/workflow/wf-inbox.sh"
VALIDATE_SCRIPT="$ROOT/scripts/governance/gov-validate.sh"
STATUS_SCRIPT="$ROOT/scripts/governance/gov-update-status.sh"
METRICS_SCRIPT="$ROOT/scripts/governance/gov-update-metrics.sh"

now() {
  date -u +%Y-%m-%dT%H:%M:%SZ 2>/dev/null || echo "unknown"
}

TS_SHORT() {
  date -u +%Y%m%d-%H%M%S 2>/dev/null || echo "unknown"
}

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BOLD='\033[1m'
NC='\033[0m'

show_help() {
  cat << EOF
用法: bash scripts/session/ses-session-end.sh [选项]

选项:
  --batch <文件>   从文件读取交接内容（非交互）
  --check          检查是否有未完成的交接
  --help           显示此帮助

交接必须包含 4 项：
  1. 本次完成 — 当前 session 交付了什么
  2. 未完成事项 — 留给下一 session 的工作
  3. 关键决策与理由 — 为什么做此选择（推理过程）
  4. 已知风险 — 陷阱、注意事项、未验证的假设
EOF
  exit 0
}

check_pending() {
  local issues=0

  if python3 -c "
import json
with open('$ROOT/.ai/tasks.json') as f:
    d = json.load(f)
prog = [t for t in d.get('tasks', []) if t.get('status') == 'in_progress']
if prog:
    print('进行中任务:')
    for t in prog:
        print('  ' + t['id'] + ': ' + t.get('description','')[:60])
    exit(0)
exit(1)
" 2>/dev/null; then
    issues=$((issues + 1))
  fi

  local unread
  unread=$(find "$ROOT/docs/ai/inbox" -maxdepth 1 -name "*.md" -not -name ".gitkeep" -exec grep -l "^status: unread" {} \; 2>/dev/null | wc -l | tr -d ' ')
  if [ "$unread" -gt 0 ]; then
    echo -e "  ${YELLOW}未读消息: $unread${NC}"
    issues=$((issues + 1))
  fi

  if [ "$issues" -gt 0 ]; then
    echo -e "  ${YELLOW}有 $issues 项未处理${NC}"
    return 1
  fi
  echo -e "  ${GREEN}无未处理事项${NC}"
  return 0
}

do_batch() {
  local file="$1"
  if [ ! -f "$file" ]; then
    echo "文件不存在: $file" >&2
    exit 1
  fi
  source "$file"
  do_handoff
}

do_interactive() {
  echo -e "${BOLD}========================================${NC}"
  echo -e "${BOLD}  AI Session 接力交接（闭环）${NC}"
  echo -e "${BOLD}========================================${NC}"
  echo ""
  echo -e "填写以下 4 项交接内容，缺一不可："
  echo ""

  echo -e "${BOLD}1  本次完成的工作（交付了什么）：${NC}"
  read -r DONE_LIST

  echo -e "${BOLD}2  未完成/留给下一 Session 的工作：${NC}"
  read -r TODO_LIST

  echo -e "${BOLD}3  关键决策与理由（为什么这么做，考察过哪些方案）：${NC}"
  read -r REASONING

  echo -e "${BOLD}4  已知风险/陷阱/未验证假设：${NC}"
  read -r RISKS

  echo -e "${BOLD}5  下一个 AI 应该做什么（附带理由）：${NC}"
  read -r NEXT_ACTION
  echo -e "${BOLD}   为什么做这个：${NC}"
  read -r NEXT_WHY

  echo ""
  do_handoff
}

do_handoff() {
  local TIMESTAMP
  TIMESTAMP=$(now)
  local TS
  TS=$(TS_SHORT)

  echo ""
  echo -e "${BOLD}执行接力收尾...${NC}"
  echo ""

  # ── 1. 校验 ──
  echo -e "  ${GREEN}[1/6]${NC} 校验文档..."
  if [ -f "$VALIDATE_SCRIPT" ]; then
    bash "$VALIDATE_SCRIPT" 2>&1 | grep -E "结论|违规" || true
  fi

  # ── 2. 更新任务板（含 why） ──
  echo -e "  ${GREEN}[2/6]${NC} 更新任务板..."
  if [ -f "$TASK_BOARD" ]; then
    if [ -n "${NEXT_ACTION:-}" ]; then
      bash "$TASK_BOARD" next "${NEXT_ACTION:-}" "${NEXT_WHY:-}" 2>/dev/null || true
    fi
    if [ -n "${RISKS:-}" ]; then
      bash "$TASK_BOARD" risk "${RISKS:-}" 2>/dev/null || true
    fi
  fi

  # ── 3. 写入收件箱（结构化接力信） ──
  echo -e "  ${GREEN}[3/6]${NC} 写入接力信..."
  if [ -f "$INBOX_SCRIPT" ]; then
    local CONTENT
    CONTENT=$'## 1. 本次完成\n\n'"${DONE_LIST:-（未填写）}"$'\n\n## 2. 未完成\n\n'"${TODO_LIST:-（未填写）}"$'\n\n## 3. 关键决策与理由\n\n'"${REASONING:-（未填写）}"$'\n\n## 4. 已知风险\n\n'"${RISKS:-（未填写）}"
    bash "$INBOX_SCRIPT" send "接力交接 - $TS" "$CONTENT" 2>/dev/null || true
  fi

  # ── 4. 写入上下文缓存（含推理过程） ──
  echo -e "  ${GREEN}[4/6]${NC} 写入上下文缓存..."
  if [ -f "$CACHE_SCRIPT" ]; then
    # agent 身份按环境检测, 不冒充 (DEC-009); to= 为 handoff 必填字段, 漏传会被
    # save 拒写且错误被 2>/dev/null 吞掉 — 契约由静态测试钉住
    # 身份由 lib-platform.sh 的 detect_agent 统一检测（DEC-009: 不冒充）,
    # 与 wf-inbox.sh 的接力信署名共用同一实现, 避免两处漂移
    local AGENT_NAME
    AGENT_NAME="$(detect_agent 2>/dev/null || echo unknown-ai)"
    bash "$CACHE_SCRIPT" save handoff \
      from="${AGENT_NAME}" \
      to="next-ai" \
      context="完成: ${DONE_LIST:-} | 决策: ${REASONING:-}" \
      next="${NEXT_ACTION:-}" \
      key_files=".ai/tasks.json,docs/ai/inbox/" \
      lessons="${REASONING:-}" \
      2>/dev/null || true
  fi

  # ── 5. 刷新 STATUS.md ──
echo -e " ${GREEN}[5/7]${NC} 刷新状态..."
if [ -f "$STATUS_SCRIPT" ]; then
bash "$STATUS_SCRIPT" 2>/dev/null | tail -1 || true
fi

# ── 6. 更新指标 ──
echo -e " ${GREEN}[6/7]${NC} 更新指标..."
if [ -f "$METRICS_SCRIPT" ]; then
bash "$METRICS_SCRIPT" 2>/dev/null | tail -1 || true
fi

# ── 7. 工作区审计（DEC-065）──
# 收尾时自动清理已合入 worktree / 分支、过期锁、孤儿目录、陈旧后台日志。
# --fix 只处理安全项，剩余 WARN 由人判断；脚本自身失败不阻断收尾。
echo -e " ${GREEN}[7/7]${NC} 工作区审计（worktree/分支/锁/证据）..."
WORKSPACE_DOCTOR="$ROOT/scripts/governance/gov-workspace-doctor.sh"
if [ -f "$WORKSPACE_DOCTOR" ]; then
  bash "$WORKSPACE_DOCTOR" --fix 2>&1 | tail -20 || true
  echo "  工作区已审计（剩余 WARN 需人工确认）"
else
  echo "  跳过: 未找到 $WORKSPACE_DOCTOR"
fi

# ── 8. 生成摘要 ──
echo -e "${BOLD}========================================${NC}"
echo -e "${GREEN} Session 交接 + 指标刷新完成${NC}"
echo -e "${BOLD}========================================${NC}"
echo -e "${GREEN} Session 交接 + 指标刷新完成${NC}"
echo -e "${BOLD}========================================${NC}"
  echo ""
  echo "  交接摘要:"
  echo "    完成: ${DONE_LIST:-（未填写）}"
  echo "    未完成: ${TODO_LIST:-（未填写）}"
  echo "    决策理由: ${REASONING:-（未填写）}"
  echo "    风险: ${RISKS:-（未填写）}"
  echo "    下一步: ${NEXT_ACTION:-（未填写）}"
  if [ -n "${NEXT_WHY:-}" ]; then
    echo "    理由: ${NEXT_WHY}"
  fi
  echo ""
  echo "  ${YELLOW}下一个人启动时:${NC}"
  echo "    1. Claude Code 启动自动读取交接"
  echo "    2. 必须向人类确认下一步方向"
  echo "    3. 确认后继续：bash scripts/workflow/wf-task-board.sh confirm"

# ── 8. 清理会话标识，并剔除本会话 claims（DEC-047）──
SESSION_FILE="$ROOT/.ai/current-session.json"
CLAIM_FILE="$ROOT/.ai/staged-claims.json"
if [ -f "$SESSION_FILE" ]; then
  python3 - "$SESSION_FILE" "$CLAIM_FILE" <<"PY"
import json, sys
from pathlib import Path
sf, cf = Path(sys.argv[1]), Path(sys.argv[2])
try:
    sid = json.loads(sf.read_text(encoding="utf-8")).get("session_id", "")
except Exception:
    sid = ""
if sid and cf.is_file():
    d = json.loads(cf.read_text(encoding="utf-8") or "{}")
    kept = {k: v for k, v in d.items() if v != sid}
    cf.write_text(json.dumps(kept, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f" 已清理本会话 claims: {sid} 剩余 {len(kept)} 条")
PY
  echo " 清理会话标识: $SESSION_FILE"
  rm -f "$SESSION_FILE"
fi

# DEC-077: 清理过期模式文件 (防止 stale explore/docs 模式残留)
if [ -f "$ROOT/.ai/current-mode.json" ]; then
  echo " 清理过期模式文件: .ai/current-mode.json"
  rm -f "$ROOT/.ai/current-mode.json"
fi

}

case "${1:-}" in
  --batch) shift; do_batch "${1:-}" ;;
  --check) check_pending ;;
  --help|-h) show_help ;;
  *) do_interactive ;;
esac
