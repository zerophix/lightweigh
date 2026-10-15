#!/usr/bin/env bash
# ============================================================
# scripts/workflow/wf-next-note.sh
# 兼容包装 — 实际实现已收敛到 governance/gov-next-note.sh（DEC-002）
#
# 原因: 两份实现命令集相同（create / promote / status）但逻辑各自演化，
#       保留单一事实源，避免双实现漂移。
#
# 用法不变:
#   bash scripts/workflow/wf-next-note.sh create "标题" --source AI-DISCUSSION
#   bash scripts/workflow/wf-next-note.sh promote NOTE-20260706-002
#   bash scripts/workflow/wf-next-note.sh status NOTE-20260706-002 已废弃
# ============================================================

set -euo pipefail

_DIR="$(cd "$(dirname "$0")" && pwd)"
exec bash "$_DIR/../governance/gov-next-note.sh" "$@"
