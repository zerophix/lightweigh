#!/usr/bin/env bash
#
# scripts/governance/gov-sync-mkdocs.sh
# 自动同步 mkdocs.yml nav 与实际 docs/ 文件结构
#
# 扫描 docs/ 下的 .md 文件，自动生成 mkdocs.yml 的 nav 部分。
# 保持 mkdocs.yml 的 theme/plugins/extensions 等配置不变。
#
# 用法:
#   bash scripts/governance/gov-sync-mkdocs.sh            # 同步（写入）
#   bash scripts/governance/gov-sync-mkdocs.sh --check    # 只检查是否一致
# ============================================================

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
MKDOCS="$ROOT/mkdocs.yml"
DOCS_DIR="$ROOT/docs"

CHECK_MODE=false
[ "${1:-}" = "--check" ] && CHECK_MODE=true

# 生成 nav 结构（有序：先标准文档，后 AI 文档）
generate_nav() {
  echo "  - 首页: index.md"
  echo ""
  echo "  - 标准文档:"

  # 扫描标准文档目录
  for dir in architecture guides reference changelog; do
    local index="$DOCS_DIR/$dir/index.md"
    if [ -f "$index" ]; then
      local title
      title=$(head -1 "$index" 2>/dev/null | sed -E 's/^#+ *//' | sed 's/^① *//' | sed 's/ *$//')
      [ -z "$title" ] && title="$dir"
      echo "      - ${title}: ${dir}/index.md"
    fi
    # 包含非 index.md 的子文档（如 REF-*.md）
    for f in "$DOCS_DIR/$dir/"*.md; do
      [ -f "$f" ] || continue
      local base
      base=$(basename "$f")
      [ "$base" = "index.md" ] && continue
      local title
      title=$(grep -m1 '^# ' "$f" 2>/dev/null | sed -E 's/^#+ *//' | sed 's/^① *//' | sed 's/ *$//')
      [ -z "$title" ] && title="${base%.md}"
      echo "      - ${title}: ${dir}/${base}"
    done
  done

  echo ""
  echo "  - AI 文档:"
  echo "      - 概览: ai/index.md"
  echo "      - 约束规则: ai/constraints/index.md"
  echo "      - 决策日志: ai/decisions/index.md"
  echo "      - 设计笔记: notes/index.md"

  # 工作指南
  if [ -d "$DOCS_DIR/ai/guides" ]; then
    echo "      - 工作指南:"
    echo "          - 概览: ai/guides/index.md"
    for f in "$DOCS_DIR/ai/guides/"*.md; do
      [ -f "$f" ] || continue
      local base
      base=$(basename "$f")
      [ "$base" = "index.md" ] && continue
      local title
      title=$(grep -m1 '^# ' "$f" 2>/dev/null | sed -E 's/^#+ *//' | sed 's/^① *//' | sed 's/ *$//')
      [ -z "$title" ] && title="${base%.md}"
      echo "          - ${title}: ai/guides/${base}"
    done
  fi

  # 生命周期
  if [ -d "$DOCS_DIR/ai/lifecycle" ]; then
    echo "      - 生命周期:"
    echo "          - 概览: ai/lifecycle/index.md"
    for f in "$DOCS_DIR/ai/lifecycle/"*.md; do
      [ -f "$f" ] || continue
      local base
      base=$(basename "$f")
      [ "$base" = "index.md" ] && continue
      local title
      title=$(grep -m1 '^# ' "$f" 2>/dev/null | sed -E 's/^#+ *//' | sed 's/^① *//' | sed 's/ *$//')
      [ -z "$title" ] && title="${base%.md}"
      echo "          - ${title}: ai/lifecycle/${base}"
    done
  fi

  # 记忆文档
  if [ -f "$DOCS_DIR/ai/memory/index.md" ]; then
    echo "      - 项目记忆: ai/memory/index.md"
  fi
}

# 生成完整 mkdocs.yml（保留 header 配置，替换 nav）
sync_mkdocs() {
  local tmp=$(mktemp)
  local nav_content
  nav_content=$(generate_nav)

  # 提取 mkdocs.yml 中 nav: 之前的部分
  awk 'BEGIN { found=0 } /^nav:/ { found=1 } found==0 { print }' "$MKDOCS" > "$tmp"
  echo "" >> "$tmp"
  echo "nav:" >> "$tmp"
  echo "$nav_content" >> "$tmp"

  # 保留 markdown_extensions 和 extra
  awk 'BEGIN { found=0 } /^(markdown_extensions|extra):/ { found=1; print; next } found==1 { print }' "$MKDOCS" >> "$tmp"

  mv "$tmp" "$MKDOCS"
  echo "  mkdocs.yml synced"
}

# 检查 nav 是否一致（只检查 nav 部分 md 文件引用）
check_nav() {
  local errors=0

  # 提取 nav 中所有 .md 引用
  local nav_files
  nav_files=$(grep -oE '[a-z]+/[a-zA-Z0-9_/-]+\.md' "$MKDOCS" | sort -u)

  for f in $nav_files; do
    if [ ! -f "$DOCS_DIR/$f" ]; then
      echo "  MISSING: $f (referenced in mkdocs.yml but not on disk)" >&2
      errors=$((errors + 1))
    fi
  done

  # 检查 docs/ 下的 .md 是否都在 nav 中（跳过 index 和 DEC/NOTE）
  for f in $(find "$DOCS_DIR" -name "*.md" -not -path "*/node_modules/*" -not -path "*/reviews/*" | sort); do
    local rel="${f#$DOCS_DIR/}"
    case "$rel" in
      index.md|ai/index.md|ai/decisions/index.md|ai/guides/index.md|ai/lifecycle/index.md|ai/constraints/index.md|ai/reviews/index.md|ai/memory/index.md) continue ;;
      ai/decisions/DEC-*) continue ;;
      notes/NOTE-*) continue ;;
      ai/reviews/*) continue ;;
      ai/inbox/*) continue ;;
      guides/GC-*) continue ;;
    esac
    if ! grep -q "$rel" "$MKDOCS" 2>/dev/null; then
      echo "  MISSING: $rel (on disk but not in mkdocs.yml nav)" >&2
      errors=$((errors + 1))
    fi
  done

  if [ "$errors" -gt 0 ]; then
    echo "  FAIL: $errors nav issues found" >&2
    return 1
  fi
  echo "  PASS: mkdocs.yml nav is complete"
  return 0
}

echo "=== mkdocs.yml Sync ==="
if $CHECK_MODE; then
  echo "  Mode: check"
  check_nav
else
  echo "  Mode: sync"
  sync_mkdocs
  echo "  Done"
fi
