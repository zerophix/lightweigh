#!/usr/bin/env bash
# custom-checks.sh — 项目自定义检查脚本
# 在 pre-commit 时自动运行（如果存在），退出码非 0 阻止提交
set -euo pipefail
echo "  (custom-checks.sh — 编辑此文件添加项目自定义检查)"

ROOT="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"

# 反射收口（DEC-021 执行记录 2026-10-17）：表现层禁止反射直摸类路径。
# ArchUnit 只查字节码直接依赖，静态 grep 是可用的补充面——运行时拼接类名字符串
# 仍属已声明盲区（不假装全覆盖）。
TARGETS=(
  "$ROOT/app/src/main/kotlin/com/xcwk/scalehub/feature"
  "$ROOT/app/src/main/kotlin/com/xcwk/scalehub/ui"
)
EXISTING=()
for d in "${TARGETS[@]}"; do [ -d "$d" ] && EXISTING+=("$d"); done
if [ "${#EXISTING[@]}" -gt 0 ]; then
  HITS="$(grep -rn --include='*.kt' -E 'Class\.forName|java\.lang\.reflect|kotlin\.reflect\.' "${EXISTING[@]}" || true)"
  if [ -n "$HITS" ]; then
    echo "  [FAIL] 表现层禁止反射（Class.forName / reflect 包）——该依赖请经 application 端口表达："
    echo "$HITS"
    exit 1
  fi
fi

exit 0
