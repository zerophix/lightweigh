#!/usr/bin/env bash
# ============================================================
# scripts/governance/gov-generate-index.sh
# 自动生成决策日志索引（从实际 DEC 文件读取）
#
# 用法: bash scripts/governance/gov-generate-index.sh
# 时机: 每次有新决策后运行
# ============================================================

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
DECISIONS_DIR="$ROOT/docs/ai/decisions"
INDEX_FILE="$DECISIONS_DIR/index.md"

# 收集所有 DEC-XXX.md
ENTRIES=""
for F in "$DECISIONS_DIR"/DEC-*.md; do
    [ -f "$F" ] || continue
    BASE=$(basename "$F" .md)

    # 从文件内容提取标题（第一行 H1）
    TITLE=$(head -1 "$F" | sed 's/^# *//' || echo "$BASE")
    # 提取日期（从 markdown checkbox 或 yaml 中查找）
    DATE=$(grep -oE '[0-9]{4}-[0-9]{2}-[0-9]{2}' "$F" 2>/dev/null | head -1 || echo "-")
    # 提取来源
    SOURCE=$(grep -E "来源:" "$F" 2>/dev/null | head -1 | sed 's/.*来源: *//' || echo "-")

    ENTRIES="$ENTRIES\n| [$BASE]($BASE.md) | $TITLE | $DATE | $SOURCE |"
done

# 按编号降序排列
if [ -n "$ENTRIES" ]; then
    ENTRIES=$(echo -e "$ENTRIES" | sort -t'|' -k2 -r)
fi

cat > "$INDEX_FILE" << EOF
# 决策日志

> **职责：** 系统记忆（唯一追加写文档）
> **规则：** 只追加，不覆盖，不删除。所有"为什么"都在这里。

---

## 索引

| 编号 | 标题 | 日期 | 来源 |
|------|------|------|------|
$ENTRIES

## 使用规范

1. 新决策用 \`bash scripts/governance/gov-next-decision.sh --create\` 自增编号
2. 追加写，不覆盖，不删除
3. 所有 6 个核心文档引用 DEC-xxx
EOF

echo "已生成: $INDEX_FILE ($(echo "$ENTRIES" | grep -c .) 条)"
