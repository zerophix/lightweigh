#!/usr/bin/env bash
# scripts/bootstrap/bs-install-hooks.sh
# 启用版本化 git 钩子（DEC-073）
#
# 不再向 .git/hooks 拷贝脚本：那份副本会随「在哪个 worktree 安装」固定成
# 某一版，改源不生效、分支之间互相覆盖，门禁因此静默降级过两次。
#
# 现在 core.hooksPath 指向仓库内的 scripts/hooks/（入库、版本化），
# 每个 worktree 用的是自己那份包装器，永远执行当前 worktree 的源脚本。
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

echo "========================================"
echo "  启用版本化 git hooks"
echo "========================================"

chmod +x scripts/hooks/* 2>/dev/null || true
git config core.hooksPath scripts/hooks

echo "   core.hooksPath = $(git config --get core.hooksPath)"
echo "   包装器目录     = $ROOT/scripts/hooks"
for f in scripts/hooks/*; do
  [ -f "$f" ] || continue
  echo "     $(basename "$f") -> $(grep -o 'scripts/governance/[a-z-]*' "$f" | head -1)"
done

echo ""
echo "========================================"
echo "  完成（无需重装；改动源脚本立即生效）"
echo "========================================"
echo ""
echo "  跳过 hooks: git commit --no-verify"
echo "  合并前建议: bash scripts/governance/gov-local-ci.sh"
