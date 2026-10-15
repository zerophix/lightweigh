#!/usr/bin/env bash
# scripts/bootstrap/bs-install-hooks.sh
# 安装全部 git hooks：
#   1. pre-commit   — src 变更自动流转 + docs 校验
#   2. commit-msg   — 强制 Conventional Commits 格式
#   3. pre-push     — 分支命名规范校验
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
HOOK_DIR="$ROOT/.git/hooks"
HOOK_SRC_DIR="$ROOT/scripts/governance"

HOOKS=(
  "pre-commit:auto-flow + docs 校验"
  "commit-msg:Conventional Commits 格式"
  "pre-push:分支命名规范"
)

if [ ! -d "$HOOK_DIR" ]; then
  echo "错误: 未找到 .git/hooks 目录，请确认在 git 仓库根目录执行"
  exit 1
fi

echo "========================================"
echo "  安装 git hooks"
echo "========================================"
echo ""

for ENTRY in "${HOOKS[@]}"; do
  NAME="${ENTRY%%:*}"
  DESC="${ENTRY##*:}"
  SOURCE="$HOOK_SRC_DIR/gov-$NAME"
  TARGET="$HOOK_DIR/$NAME"

  if [ ! -f "$SOURCE" ]; then
    echo "    未找到: $SOURCE，跳过 $NAME"
    continue
  fi

  cp "$SOURCE" "$TARGET"
  chmod +x "$TARGET"
  echo "   $NAME  — $DESC"
done

echo ""
echo "========================================"
echo "  安装完成"
echo "========================================"
echo ""
echo "  如需跳过 hooks：git commit --no-verify"
exit 0
