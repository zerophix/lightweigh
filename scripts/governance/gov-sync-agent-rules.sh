#!/usr/bin/env bash
# ============================================================
# scripts/governance/gov-sync-agent-rules.sh
# 单一真源 → 各 AI 工具的规则文件（DEC-076）
#
# 问题：不同厂商的 AI 读不同文件（Cursor 读 .cursorrules，Cline 读
# .clinerules，OpenCode 读 .opencode/rules.md，Claude Code 读 AGENTS.md）。
# 过去这些文件各写一份，已经漂移出"反思≥50字是硬约束"这种早已退役的规则。
# 现在它们一律是**生成物**：内容只做指针，真源在 AGENTS.md。
#
# 用法:
#   gov-sync-agent-rules.sh            # 重新生成（会覆盖）
#   gov-sync-agent-rules.sh --check    # 只校验是否与生成结果一致（漂移则退出 1）
#
# 退出码: 0 一致 / 1 漂移 / 2 用法错误
# ============================================================
set -uo pipefail

DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$DIR/../.." && pwd)"
cd "$ROOT" || exit 2

CHECK=0
[ "${1:-}" = "--check" ] && CHECK=1
[ "${1:-}" = "" ] || [ "${1:-}" = "--check" ] || { echo "用法: gov-sync-agent-rules.sh [--check]" >&2; exit 2; }

render() {
  local vendor="$1"
  cat <<EOF
# ${vendor} 规则 — 生成物，勿手改
# 生成: bash scripts/governance/gov-sync-agent-rules.sh
# 真源: AGENTS.md（本文件只做指针；两者冲突以 AGENTS.md 为准）

## 开工三条命令（顺序固定）
bash scripts/session/ses-session-begin.sh
bash scripts/workflow/wf-role.sh claim <dev|gov|docs|review>
bash scripts/workflow/wf-autopilot.sh status

## 角色（DEC-075）
- 一个会话只持有一个角色；review 只读，且不能由持任务锁的会话认领。
- 越出角色 allow/deny 的改动，pre-commit 直接拒绝。
- 改代码用 dev；改治理脚本用 gov；写文档用 docs；评审用 review。

## 画像（DEC-074）
- 门禁深度由任务画像决定：docs < test < code < gov < mixed。
- 画像由认领声明 + 实际改动路径合并，只能升不能降。

## 硬规则（每条都有机械执行者）
1. 禁止直提 master —— pre-commit
2. 禁止 --no-verify —— 评审 + 推送审计
3. 未认领不开工 —— wf-claim + pre-commit
4. 功能分支不写共享文件 —— pre-commit
5. 提交格式 <type>(<scope>): <subject> —— commit-msg

## 权威入口
- 规则到执行者映射：AGENTS.md 第 10 节
- 为什么这么做：docs/ai/decisions/DEC-*.md
- 当前任务：.ai/tasks.json 与 wf-autopilot.sh status
EOF
}

TARGETS=(
  ".cursorrules"
  ".clinerules"
  ".opencode/rules.md"
)

DRIFT=0
for t in "${TARGETS[@]}"; do
  case "$t" in
    .cursorrules) vendor="Cursor" ;;
    .clinerules) vendor="Cline" ;;
    *) vendor="OpenCode" ;;
  esac
  expected="$(render "$vendor")"

  if [ "$CHECK" = "1" ]; then
    if [ ! -f "$t" ]; then
      echo "  [FAIL] $t 缺失（运行 gov-sync-agent-rules.sh）"
      DRIFT=1
      continue
    fi
    if [ "$(cat "$t")" != "$expected" ]; then
      echo "  [FAIL] $t 与真源不一致（被手改？运行 gov-sync-agent-rules.sh 重新生成）"
      DRIFT=1
    fi
  else
    printf '%s\n' "$expected" > "$t"
    echo "  已生成 $t"
  fi
done

if [ "$CHECK" = "1" ]; then
  if [ "$DRIFT" = "1" ]; then
    echo "  说明: 三个规则文件都是生成物；要改规则请改 AGENTS.md 与本脚本模板"
    exit 1
  fi
  echo "  [PASS] 三个 AI 规则文件与真源一致"
fi
exit 0
