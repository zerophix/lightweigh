#!/usr/bin/env bash
# gov-upgrade.sh — 模板升级工具（DEC-010）
#
# 用法:
#   gov-upgrade.sh --check <模板源路径>   # 体检: 只报告与上游的差异, 不修改
#   gov-upgrade.sh --apply <模板源路径>   # 备份后同步上游拥有区
#
# <模板源路径>: 另一份模板 checkout（本模板 clone 或更新后的本地副本）。
# 只同步 scripts/governance/gov-owned.list 声明的上游拥有区文件;
# 分叉自有的扩展文件与项目自有配置(gov-config.sh/gov-custom-checks.sh)
# 不在清单内, 永不触碰。
#
# 升级后必须执行: bash scripts/governance/gov-validate.sh 全量校验
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
MANIFEST="$ROOT/scripts/governance/gov-owned.list"
BACKUP_ROOT="$ROOT/.cache/upgrade-backup"

MODE="${1:-}"
SRC="${2:-}"

usage() { sed -n '2,13p' "$0"; exit 1; }
case "$MODE" in --check|--apply) ;; *) usage ;; esac
[ -n "$SRC" ] || usage
[ -d "$SRC" ] || { echo "错误: 源路径不存在: $SRC" >&2; exit 1; }
SRC="$(cd "$SRC" && pwd)"
[ -d "$SRC/scripts/governance" ] || { echo "错误: 源路径不是模板（缺 scripts/governance）: $SRC" >&2; exit 1; }
[ "$SRC" != "$ROOT" ] || { echo "错误: 源与本地相同, 无需升级" >&2; exit 1; }
[ -f "$MANIFEST" ] || { echo "错误: 拥有区清单不存在: $MANIFEST" >&2; exit 1; }

local_ver="$(cat "$ROOT/scripts/governance/gov-template-version" 2>/dev/null || echo "unknown")"
src_ver="$(cat "$SRC/scripts/governance/gov-template-version" 2>/dev/null || echo "unknown")"
echo "模板版本: 本地 $local_ver → 源 $src_ver"
echo "来源: $SRC"
echo ""

BACKUP_DIR="$BACKUP_ROOT/$(date -u +%Y%m%dT%H%M%SZ)"
diffs=0
missing=0

handle_file() {
  local rel="$1"
  local src_file="$SRC/$rel"
  local local_file="$ROOT/$rel"
  if [ ! -f "$src_file" ]; then
    echo "  [跳过] 源缺: $rel"
    return 0
  fi
  if [ ! -f "$local_file" ]; then
    echo "  [新增] $rel"
    missing=$((missing + 1))
    if [ "$MODE" = "--apply" ]; then
      mkdir -p "$(dirname "$local_file")"
      cp "$src_file" "$local_file"
    fi
  elif cmp -s "$src_file" "$local_file"; then
    echo "  [同步] $rel"
  else
    echo "  [差异] $rel"
    diffs=$((diffs + 1))
    if [ "$MODE" = "--apply" ]; then
      mkdir -p "$(dirname "$BACKUP_DIR/$rel")"
      cp "$local_file" "$BACKUP_DIR/$rel"
      cp "$src_file" "$local_file"
    fi
  fi
}

while IFS= read -r entry || [ -n "$entry" ]; do
  entry="${entry%%#*}"
  entry="$(echo "$entry" | tr -d '[:space:]')"
  [ -n "$entry" ] || continue
  case "$entry" in
    */\*)
      dir="${entry%/\*}"
      for src_file in "$SRC/$dir"/*; do
        [ -f "$src_file" ] || continue
        handle_file "$dir/$(basename "$src_file")"
      done
      ;;
    *)
      handle_file "$entry"
      ;;
  esac
done < "$MANIFEST"

echo ""
echo "汇总: 差异 $diffs, 新增 $missing"
if [ "$MODE" = "--check" ]; then
  if [ "$diffs" -eq 0 ] && [ "$missing" -eq 0 ]; then
    echo "结论: 与源完全同步"
  else
    echo "结论: 落后于源。执行 --apply 升级（差异文件先备份到 $BACKUP_ROOT/）"
  fi
else
  echo "已同步。下一步: bash scripts/governance/gov-validate.sh 全量校验 + pytest tests/"
fi
