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

# hooksPath 必须指向**主树**的 scripts/hooks：worktree 共享同一份 .git/config，
# 写入哪个 worktree 的绝对路径，全仓就用哪个。2026-10-27 实测事故：
# 在 worktree 内执行 --fix，EXPECTED 被推导成该 worktree 路径；该 worktree 随后被
# gov-merge / ses-session-end 自动删除，core.hooksPath 变成悬空指针，git 静默跳过
# 全部钩子（pre-commit / pre-push / commit-msg 同时失效，无任何报错）。
# 因此这里不用脚本位置推导，改用 git-common-dir 反推主树根。
MAIN_ROOT="$ROOT"
common_dir="$(git rev-parse --path-format=absolute --git-common-dir 2>/dev/null || true)"
if [ -n "$common_dir" ] && [ -d "$common_dir" ]; then
  MAIN_ROOT="$(dirname "$common_dir")"
fi
EXPECTED="$MAIN_ROOT/scripts/hooks"

# 治理整体停用哨兵（gov-mode.sh disable 创建）：此时「钩子没装」是预期状态，
# 不是配置异常，更不能去 --fix 装回来。
GOV_SENTINEL="$MAIN_ROOT/.git/gov-disabled"
if [ -n "$common_dir" ] && [ -d "$common_dir" ]; then
  GOV_SENTINEL="$common_dir/gov-disabled"
fi
if [ -f "$GOV_SENTINEL" ]; then
  if [ "$JSON" -eq 1 ]; then
    printf '{"ok":true,"disabled":true,"sentinel":"%s"}\n' "$GOV_SENTINEL"
  else
    echo "治理已永久停用（哨兵 ${GOV_SENTINEL}）—— 钩子未安装属预期，跳过体检"
  fi
  exit 0
fi

# name:source
pairs=(
  "pre-commit:scripts/governance/gov-pre-commit-simplified"
  "commit-msg:scripts/governance/gov-commit-msg"
  "pre-push:scripts/governance/gov-pre-push"
  "prepare-commit-msg:scripts/governance/gov-prepare-commit-msg"
)

if [ "$FIX" -eq 1 ]; then
  if [ ! -d "$EXPECTED" ]; then
    echo "无法修复: 主树缺少 ${EXPECTED}（git-common-dir 推导异常）" >&2
    exit 1
  fi
  git config core.hooksPath "$EXPECTED"
  chmod +x "$EXPECTED"/* 2>/dev/null || true
  echo "已设置 core.hooksPath=${EXPECTED}（主树: ${MAIN_ROOT}）并补齐执行位"
fi

configured="$(git config --get core.hooksPath 2>/dev/null || true)"
issues=()
[ "$configured" = "$EXPECTED" ] || issues+=("core.hooksPath=${configured:-<未设置>}（应为绝对路径 ${EXPECTED}，所有 worktree 共用一份）")

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
  python3 - "$configured" "$(printf '%s\n' "${issues[@]:-}")" "$EXPECTED" <<'PY'
import json, sys
configured, raw, expected = sys.argv[1], sys.argv[2], sys.argv[3]
issues = [x for x in raw.splitlines() if x.strip()]
print(json.dumps({
    "ok": not issues,
    "hooks_path": configured,
    "expected": expected,
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
