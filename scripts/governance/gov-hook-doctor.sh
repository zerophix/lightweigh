#!/usr/bin/env bash
# scripts/governance/gov-hook-doctor.sh
# 钩子副本漂移检查（DEC-067）
#
# 背景：.git/hooks/* 是 scripts/governance/gov-pre-* 的**安装副本**（DEC-047）。
# 改了仓库里的脚本不会自动生效；副本过期 = 门禁静默消失或规则版本不一致。
# 这是一个"看不见的失效"：检查脚本写得很严，实际跑的还是旧版。
#
# 用法:
#   gov-hook-doctor.sh            # 检查（漂移即非 0）
#   gov-hook-doctor.sh --fix      # 漂移则调用 bs-install-hooks 重装
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

hooks_dir="$(git rev-parse --git-path hooks 2>/dev/null || echo "$ROOT/.git/hooks")"
installer="$ROOT/scripts/bootstrap/bs-install-hooks.sh"

# 源 → 安装名
# 只登记安装器真的会装的钩子；pre-commit-full 是手动排障工具，登记它只会永远红。
pairs=(
  "scripts/governance/gov-pre-commit-simplified:pre-commit"
  "scripts/governance/gov-pre-push:pre-push"
  "scripts/governance/gov-commit-msg:commit-msg"
  "scripts/governance/gov-prepare-commit-msg:prepare-commit-msg"
)

drift=()
missing=()
ok=()

for pair in "${pairs[@]}"; do
  src_rel="${pair%%:*}"; name="${pair##*:}"
  src="$ROOT/$src_rel"; dst="$hooks_dir/$name"
  [ -f "$src" ] || continue
  if [ ! -f "$dst" ]; then
    missing+=("$name|$src_rel")
  elif grep -q "由 bs-install-hooks.sh 生成的包装器" "$dst" 2>/dev/null; then
    # DEC-072: 装的是薄包装器，它永远执行当前 worktree 的源脚本 → 不存在漂移。
    # 只要包装器指向的源路径没变就算一致。
    if grep -q "源: $src_rel" "$dst" 2>/dev/null; then
      ok+=("$name")
    else
      drift+=("$name|$src_rel")
    fi
  elif ! cmp -s "$src" "$dst"; then
    # 旧式拷贝：与源不一致（或与本分支源不一致）都算漂移，建议重装为包装器。
    drift+=("$name|$src_rel")
  else
    ok+=("$name")
  fi
done

if [ "$JSON" -eq 1 ]; then
  python3 - "$(printf '%s\n' "${ok[@]:-}")" "$(printf '%s\n' "${drift[@]:-}")" "$(printf '%s\n' "${missing[@]:-}")" <<'PY'
import json, sys
ok, drift, missing = (x.splitlines() for x in sys.argv[1:4])
def clean(xs): return [x for x in xs if x.strip()]
print(json.dumps({
    "ok": clean(ok),
    "drift": [dict(zip(("hook","source"), d.split("|"))) for d in clean(drift)],
    "missing": [dict(zip(("hook","source"), m.split("|"))) for m in clean(missing)],
}, ensure_ascii=False, indent=2))
PY
else
  echo "--- 钩子副本检查（${hooks_dir}）---"
  for n in "${ok[@]:-}"; do [ -n "$n" ] && echo "  [OK] $n 与仓库源一致"; done
  for d in "${drift[@]:-}"; do
    [ -n "$d" ] || continue
    echo "  [DRIFT] ${d%%:*} 与 ${d##*:} 不一致（安装副本过期）"
  done
  for m in "${missing[@]:-}"; do
    [ -n "$m" ] || continue
    echo "  [MISSING] ${m%%:*} 未安装（源: ${m##*:}）"
  done
fi

if [ "${#drift[@]}" -eq 0 ] && [ "${#missing[@]}" -eq 0 ]; then
  echo "钩子副本一致"
  exit 0
fi

if [ "$FIX" -eq 1 ] && [ -x "$installer" ]; then
  echo "--- 重装钩子 ---"
  bash "$installer" >/tmp/gov-hook-doctor-install.log 2>&1 && echo "已重装（详见 /tmp/gov-hook-doctor-install.log）" && exit 0
  echo "重装失败，见 /tmp/gov-hook-doctor-install.log" >&2
fi

[ "$JSON" -eq 0 ] && echo "修复: bash scripts/bootstrap/bs-install-hooks.sh"
exit 1
