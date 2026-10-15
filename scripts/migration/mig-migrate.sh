#!/usr/bin/env bash
# ============================================================
# scripts/migration/mig-migrate.sh
# 迁移工具 — 将已有项目的散乱文件归位到 docs/ 结构
#
# 用法:
#   bash scripts/migration/mig-migrate.sh            # 扫描并生成迁移计划
#   bash scripts/migration/mig-migrate.sh --apply     # 执行迁移
#   bash scripts/migration/mig-migrate.sh --archive   # 归档所有不合规的文件
# ============================================================

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
DOCS_DIR="$ROOT/docs"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BOLD='\033[1m'
NC='\033[0m'

case "${1:-}" in
    --help|-h)
        echo "用法: bash scripts/migration/mig-migrate.sh [scan|apply|archive]"
        echo "  scan    扫描并预览迁移计划（默认）"
        echo "  archive  归档所有不合规文件到 docs/archive/"
        echo "  apply    自动分类迁移"
        exit 0 ;;
esac
MODE="${1:-scan}"  # scan / apply / archive

echo -e "${BOLD}========================================${NC}"
echo -e "${BOLD}  docs 迁移工具${NC}"
echo -e "${BOLD}========================================${NC}"
echo "  模式: $MODE"
echo "  根目录: $ROOT"
echo ""

# 扫描全项目，找所有 .md 文件
SHOULD_MOVE=()
SHOULD_ARCHIVE=()
ALREADY_OK=0

while IFS= read -r -d '' FILE; do
    REL="${FILE#$ROOT/}"

    # 跳过内置目录
    case "$REL" in
        .git/*|node_modules/*|.opencode/node_modules/*|.claude/*|.cache/*|.idea/*|venv/*|.venv/*|__pycache__/*|site/*|dist/*)
            continue ;;
    esac

    case "$REL" in
        README.md|AGENTS.md|CONTRIBUTING.md|LICENSE|STATUS.md|.clinerules|.opencode/rules.md)
            ALREADY_OK=$((ALREADY_OK + 1))
            continue ;;
    esac

    # 已经在 docs/ 内的
    if [[ "$REL" == docs/* ]]; then
        ALREADY_OK=$((ALREADY_OK + 1))
        continue
    fi

    SHOULD_MOVE+=("$REL")
done < <(find "$ROOT" -name "*.md" -type f -print0 2>/dev/null)

echo -e "  已合规: ${GREEN}${ALREADY_OK}${NC} 个文件"
echo -e "  需迁移: ${YELLOW}${#SHOULD_MOVE[@]}${NC} 个文件"
echo ""

# ---- 模式: 归档所有不合规文件 ----
if [ "$MODE" = "archive" ]; then
    if [ ${#SHOULD_MOVE[@]} -eq 0 ]; then
        echo "没有需要归档的文件。"
        exit 0
    fi

    ARCHIVE_DIR="$DOCS_DIR/archive"
    mkdir -p "$ARCHIVE_DIR"

    for FILE in "${SHOULD_MOVE[@]}"; do
        BASENAME=$(basename "$FILE" .md)
        SLUG=$(echo "$BASENAME" | tr 'A-Z ' 'a-z-' | sed 's/[^a-z0-9-]//g' | sed 's/--*/-/g' | sed 's/^-//;s/-$//')
        [ -z "$SLUG" ] && SLUG="doc-$(date +%s)"
        TARGET="$ARCHIVE_DIR/ARCH-$SLUG.md"
        cp "$ROOT/$FILE" "$TARGET"
        echo "  [ARCHIVE] $FILE → $TARGET"
    done

    echo ""
    echo -e "${GREEN}已归档 ${#SHOULD_MOVE[@]} 个文件到 docs/archive/${NC}"
    echo "提示: 运行 bash scripts/governance/gov-validate.sh 检查结构"
    exit 0
fi

# ---- 模式: 扫描（预览迁移计划）----
if [ "$MODE" = "scan" ]; then
    if [ ${#SHOULD_MOVE[@]} -eq 0 ]; then
        echo -e "${GREEN}所有文件已在合规位置。${NC}"
        exit 0
    fi

    echo -e "${BOLD}迁移计划：${NC}"
    echo ""
    echo "这些文件不在 docs/ 结构内，建议迁移："
    echo ""

    for FILE in "${SHOULD_MOVE[@]}"; do
        # 尝试分类
        BASENAME=$(basename "$FILE" .md)
        CONTENT=$(head -50 "$ROOT/$FILE" 2>/dev/null || echo "")

        if echo "$CONTENT" | grep -qiE "架构|architecture|design|系统设计"; then
            SUGGEST="→ docs/architecture/  (ARCH-{slug}.md 或 ARCH-${BASENAME}.md)"
        elif echo "$CONTENT" | grep -qiE "指南|guide|规范|convention|标准"; then
            SUGGEST="→ docs/guides/  (GC-{slug}.md 或 GC-${BASENAME}.md)"
        elif echo "$CONTENT" | grep -qiE "接口|API|协议|reference|参考"; then
            SUGGEST="→ docs/reference/  (REF-{slug}.md 或 REF-${BASENAME}.md)"
        elif echo "$CONTENT" | grep -qiE "笔记|note|设计讨论|记录"; then
            SUGGEST="→ docs/notes/  (NOTE-$(date +%Y%m%d)-NNN.md)"
        else
            SUGGEST="→ docs/archive/  (ARCH-${BASENAME}.md)"
        fi

        echo "   $FILE"
        echo "     $SUGGEST"
        echo ""
    done

    echo "执行迁移: bash scripts/migration/mig-migrate.sh --archive"
    echo ""
    exit 0
fi

# ---- 模式: apply（自动分类迁移）----
if [ "$MODE" = "apply" ]; then
    if [ ${#SHOULD_MOVE[@]} -eq 0 ]; then
        echo "没有需要迁移的文件。"
        exit 0
    fi

    echo -e "${YELLOW}自动分类迁移可能不准确，建议先用 scan 预览。${NC}"
    echo -n "继续？(y/N) "
    read -r CONFIRM
    if [ "$CONFIRM" != "y" ] && [ "$CONFIRM" != "Y" ]; then
        echo "已取消。"
        exit 0
    fi

    for FILE in "${SHOULD_MOVE[@]}"; do
        BASENAME=$(basename "$FILE" .md)
        SLUG=$(echo "$BASENAME" | tr 'A-Z ' 'a-z-' | sed 's/[^a-z0-9-]//g' | sed 's/--*/-/g' | sed 's/^-//;s/-$//')
        [ -z "$SLUG" ] && SLUG="doc-$(date +%s)"
        CONTENT=$(head -50 "$ROOT/$FILE" 2>/dev/null || echo "")

        if echo "$CONTENT" | grep -qiE "架构|architecture|design|系统设计"; then
            mkdir -p "$DOCS_DIR/architecture"
            TARGET="$DOCS_DIR/architecture/ARCH-$SLUG.md"
            echo "  [MOVE] $FILE → $TARGET"
            cp "$ROOT/$FILE" "$TARGET"
        elif echo "$CONTENT" | grep -qiE "指南|guide|规范|convention|标准"; then
            mkdir -p "$DOCS_DIR/guides"
            TARGET="$DOCS_DIR/guides/GC-$SLUG.md"
            echo "  [MOVE] $FILE → $TARGET"
            cp "$ROOT/$FILE" "$TARGET"
        elif echo "$CONTENT" | grep -qiE "接口|API|协议|reference|参考"; then
            mkdir -p "$DOCS_DIR/reference"
            TARGET="$DOCS_DIR/reference/REF-$SLUG.md"
            echo "  [MOVE] $FILE → $TARGET"
            cp "$ROOT/$FILE" "$TARGET"
        else
            mkdir -p "$DOCS_DIR/archive"
            TARGET="$DOCS_DIR/archive/ARCH-$SLUG.md"
            echo "  [ARCHIVE] $FILE → $TARGET"
            cp "$ROOT/$FILE" "$TARGET"
        fi
    done

    echo ""
    echo -e "${GREEN}迁移完成。${NC}"
    echo "运行 bash scripts/governance/gov-validate.sh 检查结构合规性。"
    echo ""
    echo "注意: 迁移使用 cp 保留原始文件。确认无误后手动删除。"
    exit 0
fi

# 未知模式
echo "未知模式: $MODE" >&2
echo "用法: bash scripts/migration/mig-migrate.sh [scan|apply|archive]" >&2
exit 1
