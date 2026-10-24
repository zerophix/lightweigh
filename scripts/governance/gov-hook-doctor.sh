#!/usr/bin/env bash
# scripts/governance/gov-hook-doctor.sh
# 钩子配置检查（DEC-073）
#
# 背景：钩子曾经是 .git/hooks 下的**拷贝**。改了仓库里的源脚本不生效，
# 不同 worktree 还会互相覆盖安装，门禁因此静默降级过两次。
#
# 现在 core.hooksPath 指向仓库内的 scripts/hooks/（版本化包装器），
# 包装器永远执行当前 worktree 的源脚本，因此不存在"副本过期"这个概念。
# 这里只校验三件事：配置指向正确、包装器齐全可执行、源脚本存在。
#
# 用法:
#   gov-hook-doctor.sh            # 检查（不通过即非 0）
#   gov-hook-doctor.sh --fix      # 重设 core.hooksPath 并补齐执行位
#   gov-hook-doctor.sh --json
set -uo pipefail

DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT="${WF_AUTOPILOT_ROOT:-$(cd "$DIR/../.." && pwd)}"
cd "$ROOT" || exit 1

JSON=0; FIX=0
for a in "$@"; do
  case "$a" in
    --json) JSON=1 ;;
    --fix) FIX=1 ;;
  esac
done

EXPECTED="scripts/hooks"

# name:source
pairs=(
  "pre-commit:scripts/governance/gov-pre-commit-simplified"
  "commit-msg:scripts/governance/gov-commit-msg"
  "pre-push:scripts/governance/gov-pre-push"
  "prepare-commit-msg:scripts/governance/gov-prepare-commit-msg"
)

if [ "$FIX" -eq 1 ]; then
  git config core.hooksPath "$EXPECTED"
  chmod +x scripts/hooks/* 2>/dev/null || true
  echo "已设置 core.hooksPath=$EXPECTED 并补齐执行位"
fi

configured="$(git config --get core.hooksPath 2>/dev/null || true)"
issues=()
[ "$configured" = "$EXPECTED" ] || issues+=("core.hooksPath=${configured:-<未设置>}（应为 ${EXPECTED}）")

for pair in "${pairs[@]}"; do
  name="${pair%%:*}"; src="${pair##*:}"
  hook="scripts/hooks/$name"
  if [ ! -f "$hook" ]; then
    issues+=("缺少包装器: ${hook}")
  elif [ ! -x "$hook" ]; then
    issues+=("包装器不可执行: ${hook}（chmod +x）")
  elif ! grep -q "$src" "$hook" 2>/dev/null; then
    issues+=("包装器未指向源: ${hook} -> ${src}")
  fi
  [ -f "$src" ] || issues+=("源脚本缺失: ${src}")
done

if [ "$JSON" -eq 1 ]; then
  python3 - "$configured" "$(printf '%s\n' "${issues[@]:-}")" <<'PY'
import json, sys
configured, raw = sys.argv[1], sys.argv[2]
issues = [x for x in raw.splitlines() if x.strip()]
print(json.dumps({
    "ok": not issues,
    "hooks_path": configured,
    "expected": "scripts/hooks",
    "issues": issues,
}, ensure_ascii=False, indent=2))
PY
  [ "${#issues[@]}" -eq 0 ] && exit 0 || exit 1
fi

if [ "${#issues[@]}" -eq 0 ]; then
  echo "钩子配置正确（core.hooksPath=${EXPECTED}，包装器齐全）"
  exit 0
fi

echo "钩子配置有问题:"
for i in "${issues[@]}"; do echo "  - $i"; done
echo "修复: bash scripts/governance/gov-hook-doctor.sh --fix"
exit 1
