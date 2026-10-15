#!/usr/bin/env bash
# ============================================================
# scripts/workflow/wf-next-decision.sh
# 兼容包装 — 实际实现已收敛到 governance/gov-next-decision.sh（DEC-002）
#
# 原因: 两份实现在模板与锁机制上不一致（双实现漂移），
#       保留单一事实源，原子锁已迁入 gov-next-decision.sh。
#
# 用法不变:
#   bash scripts/workflow/wf-next-decision.sh              # 输出下一个编号
#   bash scripts/workflow/wf-next-decision.sh --file        # 输出完整文件路径
#   bash scripts/workflow/wf-next-decision.sh --create      # 创建模板文件（带原子锁）
#   bash scripts/workflow/wf-next-decision.sh --validate-003  # 验证编号是否可用
# ============================================================

set -euo pipefail

_DIR="$(cd "$(dirname "$0")" && pwd)"
exec bash "$_DIR/../governance/gov-next-decision.sh" "$@"
