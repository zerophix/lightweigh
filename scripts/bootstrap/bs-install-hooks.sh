#!/usr/bin/env bash
# scripts/bootstrap/bs-install-hooks.sh
# 安装全部 git hooks：
#   1. pre-commit   — 项目精简版（编译 + custom-checks）；见 DEC-046/047
#   2. commit-msg   — 强制 Conventional Commits 格式
#   3. pre-push     — 分支命名规范校验
#   4. prepare-commit-msg — 若存在则安装
#
# DEC-047: pre-commit 优先安装 gov-pre-commit-simplified，避免覆盖回模板 261 行长版。
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
# worktree: .git 可能是文件
GIT_DIR=$(git -C "$ROOT" rev-parse --git-dir 2>/dev/null || echo "$ROOT/.git")
case "$GIT_DIR" in
  /*) ;;
  *) GIT_DIR="$ROOT/$GIT_DIR" ;;
esac
HOOK_DIR="$GIT_DIR/hooks"
HOOK_SRC_DIR="$ROOT/scripts/governance"

if [ ! -d "$HOOK_DIR" ]; then
  echo "错误: 未找到 hooks 目录: $HOOK_DIR"
  echo "      worktree 请确认 git 可用；或在主工作树执行本脚本"
  exit 1
fi

echo "========================================"
echo "  安装 git hooks"
echo "========================================"
echo ""

install_one() {
  local name="$1"
  local desc="$2"
  local source="$3"
  local target="$HOOK_DIR/$name"
  if [ ! -f "$source" ]; then
    echo "    未找到: $source，跳过 $name"
    return 0
  fi
  cp "$source" "$target"
  chmod +x "$target"
  echo "   $name  — $desc"
  echo "            源: $(basename "$source")"
}

# pre-commit: 精简版优先（DEC-047）
PRE_SRC="$HOOK_SRC_DIR/gov-pre-commit-simplified"
PRE_DESC="精简版: master 约束 + 会话归属 + compile + custom-checks"
if [ ! -f "$PRE_SRC" ]; then
  PRE_SRC="$HOOK_SRC_DIR/gov-pre-commit"
  PRE_DESC="模板长版（未找到 simplified 时的回退）"
  echo "  警告: gov-pre-commit-simplified 缺失，回退模板 gov-pre-commit"
fi
install_one "pre-commit" "$PRE_DESC" "$PRE_SRC"

install_one "commit-msg" "Conventional Commits 格式" "$HOOK_SRC_DIR/gov-commit-msg"
install_one "pre-push" "分支命名 + gov-validate" "$HOOK_SRC_DIR/gov-pre-push"

if [ -f "$HOOK_SRC_DIR/gov-prepare-commit-msg" ]; then
  install_one "prepare-commit-msg" "自动注入 Session-Id" "$HOOK_SRC_DIR/gov-prepare-commit-msg"
fi

echo ""
echo "========================================"
echo "  安装完成"
echo "========================================"
echo ""
echo "  跳过 hooks: git commit --no-verify"
echo "  合并前建议: bash scripts/governance/gov-local-ci.sh"
exit 0
