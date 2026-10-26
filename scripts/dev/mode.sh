#!/usr/bin/env bash
# ============================================================
# scripts/dev/mode.sh
# 提交钩子的物理开关（dev / full）。
#
# 为什么还要这个：`gov-mode.sh off` 是"数据开关"——钩子仍然会启动、仍然打印
# 跳过日志，每次提交多几百毫秒；它是给"临时跳过"用的，有 8 小时上限。
# 开发模式需要的是"这段时间提交根本不进钩子"，所以直接把 core.hooksPath
# 指到一个空目录，切回来时恢复。两种开关并存，各管一层：
#
#   mode.sh   物理层：钩子跑不跑（本机，全部 worktree 共享）
#   gov-mode  逻辑层：单条规则跳不跳（有期限、有审计）
#
# 用法:
#   mode.sh dev      # 关钩子（纯开发）
#   mode.sh full     # 恢复钩子（交付/合入前）
#   mode.sh status   # 当前模式
#
# 注意：dev 模式只影响本机提交；PR/合入仍走 local-ci 全量门禁。
# ============================================================
set -uo pipefail

DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$DIR/../.." && pwd)"
cd "$ROOT" || exit 2

GIT_DIR="$(git rev-parse --git-dir 2>/dev/null || echo .git)"
EMPTY_DIR="$GIT_DIR/no-hooks"
CANONICAL="scripts/hooks"
MARKER="$ROOT/.cache/dev-mode"

case "${1:-status}" in
  dev)
    mkdir -p "$EMPTY_DIR"
    git config core.hooksPath "$EMPTY_DIR"
    mkdir -p "$(dirname "$MARKER")"
    date '+%Y-%m-%dT%H:%M:%S%z' > "$MARKER"
    echo "已进入 dev 模式：提交不触发任何钩子"
    echo "  core.hooksPath = $(git config core.hooksPath)"
    echo "  恢复: bash scripts/dev/mode.sh full"
    ;;
  full)
    if [ ! -d "$ROOT/$CANONICAL" ]; then
      echo "错误: $CANONICAL 不存在，无法恢复钩子" >&2
      exit 1
    fi
    git config core.hooksPath "$CANONICAL"
    rm -f "$MARKER"
    echo "已恢复 full 模式：提交触发钩子"
    echo "  core.hooksPath = $(git config core.hooksPath)"
    ;;
  status)
    hp="$(git config core.hooksPath 2>/dev/null || echo '<未设置>')"
    if [ "$hp" = "$EMPTY_DIR" ] || [ "$hp" = ".git/no-hooks" ]; then
      echo "模式: dev（钩子已关闭）"
      [ -f "$MARKER" ] && echo "  进入时间: $(cat "$MARKER")"
    else
      echo "模式: full（钩子启用）"
    fi
    echo "  core.hooksPath = $hp"
    ;;
  -h|--help)
    sed -n '2,26p' "$0"
    ;;
  *)
    echo "用法: mode.sh {dev|full|status}" >&2
    exit 2
    ;;
esac
