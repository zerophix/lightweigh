#!/usr/bin/env bash
# ============================================================
# scripts/governance/gov-next-note.sh
# 设计笔记生命周期管理 — 创建 / 晋升 / 状态更新
#
# 前置: 创建时自动编号 + 写入索引
# 中置: promote 时自动更新 NOTE 状态 + index
# 后置: gov-validate.sh 兜底校验（结构与命名规则族）
#
# 用法:
#   bash scripts/governance/gov-next-note.sh create "标题" --source AI-DISCUSSION
#   bash scripts/governance/gov-next-note.sh promote NOTE-20260706-002
#   bash scripts/governance/gov-next-note.sh status NOTE-20260706-002 已废弃
# ============================================================

set -euo pipefail

_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$_DIR/../lib/lib-root.sh"
ROOT="$PROJECT_ROOT"
DOCS_DIR="$ROOT/docs"
NOTES_DIR="$DOCS_DIR/notes"
INDEX_FILE="$NOTES_DIR/index.md"

# ---------- 工具函数 ----------

# 原子创建 note：O_EXCL 编号竞争 + 一次性写入，关闭 TOCTOU 窗口
create_note_atomic() {
  local title="$1"
  local source="$2"
  local stdin_content="$3"
  local today
  today=$(date -u +"%Y%m%d")
  local display_date
  display_date=$(date -u +"%Y-%m-%d")
  local num=1
  local note_file

  while [ "$num" -le 999 ]; do
    note_file="$NOTES_DIR/NOTE-${today}-$(printf "%03d" $num).md"
    if python3 -c '
import os, sys
path = sys.argv[1]
title = sys.argv[2]
source = sys.argv[3]
display_date = sys.argv[4]
stdin_content = sys.argv[5]
lines = [
    "# " + title,
    "",
    "> **来源:** " + source,
    "> **日期:** " + display_date,
    "> **状态:** 活跃",
    "> **相关决策:** ",
    "",
    "---",
    "",
    stdin_content,
]
frontmatter = "\n".join(lines) + "\n"
try:
    fd = os.open(path, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o644)
    os.write(fd, frontmatter.encode("utf-8"))
    os.close(fd)
    sys.exit(0)
except FileExistsError:
    sys.exit(1)
' "$note_file" "$title" "$source" "$display_date" "$stdin_content" 2>/dev/null; then
      NOTE_ID="NOTE-${today}-$(printf "%03d" $num)"
      NOTE_FILE="$note_file"
      return 0
    fi
    num=$((num + 1))
  done
  echo "错误: 当天 NOTE 编号已满 (001-999)" >&2
  return 1
}

get_next_number() {
  # 已废弃——保留 stub 以防旧调用
  local today
  today=$(date -u +"%Y%m%d")
  local num=1
  while [ "$num" -le 999 ]; do
    local note_file="$NOTES_DIR/NOTE-${today}-$(printf "%03d" $num).md"
    if python3 -c "
import os, sys
try:
    fd = os.open(sys.argv[1], os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o644)
    os.close(fd)
    sys.exit(0)
except FileExistsError:
    sys.exit(1)
" "$note_file" 2>/dev/null; then
      rm -f "$note_file"
      printf "%03d" $num
      return 0
    fi
    num=$((num + 1))
  done
  echo "错误: 当天 NOTE 编号已满 (001-999)" >&2
  return 1
}

extract_title_from_index() {
  local note_file="$1"
  local note_id
  note_id=$(basename "$note_file" .md)
  if [ -f "$INDEX_FILE" ]; then
    grep -F "[$note_id]" "$INDEX_FILE" 2>/dev/null | \
      sed 's/.*| *\([^|]*\) *| *\([A-Z].*\)/\2/' | \
      head -1 | sed 's/ *$//' || true
  fi
}

# ---------- 子命令 ----------

case "${1:-}" in
  --help|-h)
    echo "用法: bash scripts/governance/gov-next-note.sh <command> [args]"
    echo ""
    echo "命令:"
    echo "  create <标题> --source <来源>   创建新笔记（自动编号+写索引）"
    echo "  promote <NOTE-ID>               晋升 NOTE → DEC（更新状态）"
    echo "  status <NOTE-ID> <新状态>       更新笔记状态"
    echo ""
    echo "来源标签: AI-DISCUSSION | OSS | HUMAN | INCIDENT | SECURITY | TEAM"
    echo "状态值:    活跃 | 待验证 | 已晋升 | 已废弃"
    exit 0
    ;;

  create)
    if [ -z "${2:-}" ]; then
      echo "用法: bash scripts/governance/gov-next-note.sh create \"标题\" --source <来源>" >&2
      exit 1
    fi
    TITLE="$2"
    SOURCE="${4:-AI-DISCUSSION}"

    # 收集 stdin 内容（如果有管道输入）
    BODY=""
    if [ ! -t 0 ]; then
      BODY=$(cat)
    fi

    if ! create_note_atomic "$TITLE" "$SOURCE" "$BODY"; then
      exit 1
    fi

    echo "已创建: $NOTE_FILE"
    echo "编号: $NOTE_ID"

    if [ "$SOURCE" != "AI-DISCUSSION" ]; then
      echo "来源: $SOURCE"
    fi

    # ---------- 自动更新 notes/index.md ----------
    # 找到索引表格位置，在表头后插入新行
    TABLE_HEADER="| 日期 | 来源 | 标题 | 状态 | 文件 |"
    TODAY_DISPLAY=$(date -u +"%Y-%m-%d %H:%M")

    if [ -f "$INDEX_FILE" ]; then
      # 在表格分隔行后插入
      awk -v note_id="$NOTE_ID" -v today="$TODAY_DISPLAY" -v source="$SOURCE" -v title="$TITLE" '
        /^\| 日期 \| 来源 \|/{seen_header=1; print; next}
        /^\|------\|/{if(seen_header){print; print "| " today " | " source " | " title " | 活跃 | [" note_id ".md](" note_id ".md) |"; seen_header=0; next}}
        {print}
      ' "$INDEX_FILE" > "${INDEX_FILE}.tmp" && mv "${INDEX_FILE}.tmp" "$INDEX_FILE"
      echo "索引已更新: $INDEX_FILE"
    else
      echo " 索引文件不存在: $INDEX_FILE（需要手动创建或运行 validate.sh）" >&2
    fi
    ;;
  
  promote)
    NOTE_ID="${2:-}"
    if [ -z "$NOTE_ID" ]; then
      echo "用法: bash scripts/governance/gov-next-note.sh promote <NOTE-ID>" >&2
      echo "示例: bash scripts/governance/gov-next-note.sh promote NOTE-20260706-002" >&2
      exit 1
    fi

    # 查找 NOTE 文件
    NOTE_FILE=""
    for f in "$NOTES_DIR"/${NOTE_ID}.md; do
      [ -f "$f" ] && NOTE_FILE="$f" && break
    done
    if [ -z "$NOTE_FILE" ]; then
      echo "错误: 找不到笔记 $NOTE_ID" >&2
      exit 1
    fi

    # 提取标题
    NOTE_TITLE=$(head -1 "$NOTE_FILE" | sed 's/^# *//' || echo "$NOTE_ID")

    # 获取下一个 DEC 编号
    DEC_NUM=$(bash "$ROOT/scripts/governance/gov-next-decision.sh" 2>/dev/null || echo "XXX")

    # 创建 DEC 模板（内容需人工补充）
    DEC_FILE="$ROOT/docs/ai/decisions/DEC-${DEC_NUM}.md"
    if [ -f "$DEC_FILE" ]; then
      echo "错误: $DEC_FILE 已存在（可能是并发创建）" >&2
      exit 1
    fi

    cat > "$DEC_FILE" <<DEC
# DEC-${DEC_NUM}: $(echo "$NOTE_TITLE" | sed 's/"/\\"/g')

> $(echo "$NOTE_TITLE" | sed 's/"/\\"/g')
> 来源: notes/${NOTE_ID}.md

## 决策

（从 NOTE 内容归纳决策）

## 方案评分

| 方案 | 评分 | 说明 |
|------|------|------|
| （待补充） | - | - |

## 状态

- [ ] 决策时间: $(date -u +"%Y-%m-%d")
- [ ] 决策者: AI
- [ ] 来源: notes/${NOTE_ID}.md
DEC

    echo "已创建决策: $DEC_FILE"
    echo "编号: DEC-${DEC_NUM}"

    # ---------- 更新 NOTE 文件状态 ----------
    if grep -q "> \*\*状态:\*\*" "$NOTE_FILE" 2>/dev/null; then
      sed -i '' "s|> \*\*状态:\*\* .*|> **状态:** 已晋升 DEC-${DEC_NUM}|" "$NOTE_FILE"
      echo "NOTE 状态已更新: 活跃 → 已晋升 DEC-${DEC_NUM}"
    elif grep -q "^状态:" "$NOTE_FILE" 2>/dev/null; then
      sed -i '' "s|^状态:.*|状态: 已晋升 DEC-${DEC_NUM}|" "$NOTE_FILE"
      echo "NOTE 状态已更新: 活跃 → 已晋升 DEC-${DEC_NUM}"
    else
      echo " 未找到 NOTE 状态字段，需要手动更新" >&2
    fi

    # ---------- 更新 notes/index.md ----------
    if [ -f "$INDEX_FILE" ]; then
      sed -i '' "s|\[$NOTE_ID\.md\]\($NOTE_ID\.md\)|[$NOTE_ID\.md]($NOTE_ID\.md) |" "$INDEX_FILE" 2>/dev/null || true
      # 更新状态列
      awk -v note_id="$NOTE_ID" -v dec="DEC-${DEC_NUM}" '
        {
          if (index($0, "[" note_id ".md]")) {
            sub(/\| 活跃 \|/, "| 已晋升 " dec " |")
          }
          print
        }
      ' "$INDEX_FILE" > "${INDEX_FILE}.tmp" && mv "${INDEX_FILE}.tmp" "$INDEX_FILE"
      echo "索引状态已更新: $INDEX_FILE"
    fi

    echo ""
    echo "下一步:"
    echo "  1. 编辑 $DEC_FILE 补充决策内容"
    echo "  2. bash scripts/governance/gov-next-decision.sh --validate-DEC-${DEC_NUM} 验证编号"
    echo "  3. git add $NOTE_FILE $DEC_FILE $INDEX_FILE"
    ;;

  status)
    NOTE_ID="${2:-}"
    NEW_STATUS="${3:-}"
    if [ -z "$NOTE_ID" ] || [ -z "$NEW_STATUS" ]; then
      echo "用法: bash scripts/governance/gov-next-note.sh status <NOTE-ID> <新状态>" >&2
      echo "示例: bash scripts/governance/gov-next-note.sh status NOTE-20260706-002 已废弃" >&2
      exit 1
    fi

    NOTE_FILE="$NOTES_DIR/${NOTE_ID}.md"
    if [ ! -f "$NOTE_FILE" ]; then
      echo "错误: 找不到笔记 $NOTE_ID" >&2
      exit 1
    fi

    # 更新 NOTE 文件内部状态
    if grep -q "> \*\*状态:\*\*" "$NOTE_FILE"; then
      sed -i '' "s|> \*\*状态:\*\* .*|> **状态:** $NEW_STATUS|" "$NOTE_FILE"
    elif grep -q "^状态:" "$NOTE_FILE"; then
      sed -i '' "s|^状态:.*|状态: $NEW_STATUS|" "$NOTE_FILE"
    fi

    # 更新 index.md 表格
    if [ -f "$INDEX_FILE" ]; then
      awk -v note_id="$NOTE_ID" -v status="$NEW_STATUS" '
        {
          if (index($0, "[" note_id ".md]")) {
            sub(/\| 活跃 \|/, "| " status " |")
            sub(/\| 已晋升 [^|]+ \|/, "| " status " |")
            sub(/\| 待验证 \|/, "| " status " |")
          }
          print
        }
      ' "$INDEX_FILE" > "${INDEX_FILE}.tmp" && mv "${INDEX_FILE}.tmp" "$INDEX_FILE"
    fi

    echo " $NOTE_ID 状态已更新: $NEW_STATUS"
    ;;

  *)
    echo "未知命令: ${1:-}" >&2
    echo "运行 bash scripts/governance/gov-next-note.sh --help 查看用法" >&2
    exit 1
    ;;
esac
