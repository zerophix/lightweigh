#!/usr/bin/env bash
# ============================================================
# scripts/governance/gov-sync-standards.sh
# 一键同步黄金标准文件 — 根据 DEC 内容自动判断需更新的目标
#
# 用法:
#   bash scripts/governance/gov-sync-standards.sh --dec DEC-029
#   bash scripts/governance/gov-sync-standards.sh --dec DEC-029 --dry-run
#   bash scripts/governance/gov-sync-standards.sh --dec DEC-029 --target architecture
#
# 同步规则:
#   02_Architecture.md staged → 同步 docs/architecture/index.md
#   01_PRD.md staged        → 同步 docs/guides/index.md
#   DEC-xxx staged           → 追加 docs/changelog/index.md
#   NOTE-*.md staged         → 更新 docs/notes/index.md 索引
# ============================================================

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
DOCS_DIR="$ROOT/docs"
PROMOTE="$ROOT/scripts/workflow/wf-promote.sh"
VALIDATE="$ROOT/scripts/governance/gov-validate.sh"
NOTE_SCRIPT="$ROOT/scripts/governance/gov-next-note.sh"

DECISION=""
DRY_RUN=false
TARGET_FILTER=""

while [ $# -gt 0 ]; do
  case "$1" in
    --dec)   DECISION="$2"; shift 2 ;;
    --dry-run) DRY_RUN=true; shift ;;
    --target) TARGET_FILTER="$2"; shift 2 ;;
    -h|--help)
      echo "用法: bash scripts/governance/gov-sync-standards.sh --dec DEC-XXX [--dry-run] [--target <目标>]"
      echo ""
      echo "参数:"
      echo "  --dec DEC-XXX   决策编号（必填）"
      echo "  --dry-run       预览模式，不实际写入"
      echo "  --target <目标>  只同步指定目标（architecture|guides|changelog|notes|reference）"
      echo ""
      echo "同步规则:"
      echo "  02_Architecture.md staged → docs/architecture/index.md"
      echo "  01_PRD.md staged        → docs/guides/index.md"
      echo "  DEC-xxx staged           → docs/changelog/index.md (追加)"
      echo "  NOTE-*.md staged         → docs/notes/index.md (更新索引)"
      exit 0
      ;;
    *) echo "未知参数: $1" >&2; exit 1 ;;
  esac
done

if [ -z "$DECISION" ]; then
  echo "错误: 缺少 --dec DEC-XXX 参数" >&2
  exit 1
fi

# 查找 DEC 文件
DEC_FILE="$ROOT/docs/ai/decisions/${DECISION}.md"
if [ ! -f "$DEC_FILE" ]; then
  echo "错误: 决策文件不存在: $DEC_FILE" >&2
  exit 1
fi

echo "========================================"
echo " 黄金标准同步"
echo "========================================"
echo "决策: $DECISION"
echo ""

# ---------- 扫描 staged 文件，判断需要同步的目标 ----------
# 优先用 git diff --cached，fallback 到 git diff
STAGED=$(git diff --cached --name-only --diff-filter=ACMR 2>/dev/null || git diff --name-only 2>/dev/null || true)

check_staged() {
  echo "$STAGED" | grep -qE "(^|/)$1(/|$)" 2>/dev/null || \
  echo "$STAGED" | grep -qE "(^|/)$1" 2>/dev/null
}

TARGETS=""

# 01_PRD.md / 02_Architecture.md 变更 → 同步对应黄金标准
if [ -z "$TARGET_FILTER" ] || [ "$TARGET_FILTER" = "guides" ]; then
  if check_staged "01_PRD.md" || check_staged "ai/lifecycle/01_PRD.md"; then
    TARGETS="$TARGETS guides"
  fi
fi

if [ -z "$TARGET_FILTER" ] || [ "$TARGET_FILTER" = "architecture" ]; then
  if check_staged "02_Architecture.md" || check_staged "ai/lifecycle/02_Architecture.md"; then
    TARGETS="$TARGETS architecture"
  fi
fi

# DEC 变更 → changelog
if [ -z "$TARGET_FILTER" ] || [ "$TARGET_FILTER" = "changelog" ]; then
  if echo "$STAGED" | grep -qE "ai/decisions/DEC-"; then
    TARGETS="$TARGETS changelog"
  fi
fi

# NOTE 变更 → notes 索引
if [ -z "$TARGET_FILTER" ] || [ "$TARGET_FILTER" = "notes" ]; then
  if echo "$STAGED" | grep -qE "notes/NOTE-[0-9]{8}-[0-9]{3}\.md"; then
    TARGETS="$TARGETS notes"
  fi
fi

# DEC 中包含 reference 相关 → reference（人工维护，仅提醒）
if [ -z "$TARGET_FILTER" ] || [ "$TARGET_FILTER" = "reference" ]; then
  if grep -qE "(接口|API|协议|reference)" "$DEC_FILE" 2>/dev/null; then
    echo " $DECISION 涉及接口/协议变更，reference/index.md 需人工更新"
  fi
fi

if [ -z "$TARGETS" ]; then
  echo "没有检测到需要同步的黄金标准变更。"
  echo "提示: 确保相关 lifecycle 文档已 git add 后再执行 gov-sync-standards.sh"
  exit 0
fi

echo "检测到需要同步的目标: $TARGETS"
echo ""

# ---------- 执行同步 ----------
FAILED=0

for TARGET in $TARGETS; do
  echo "--- 同步 $TARGET ---"

  case "$TARGET" in
    architecture)
      SOURCE="docs/ai/lifecycle/02_Architecture.md"
      if [ ! -f "$ROOT/$SOURCE" ]; then
        echo "   源文件不存在: $SOURCE，跳过"
        continue
      fi
      if $DRY_RUN; then
        echo "  [dry-run] $SOURCE → docs/architecture/index.md"
      else
        echo "   $SOURCE → docs/architecture/index.md"
        cat "$ROOT/$SOURCE" | bash "$PROMOTE" "docs/architecture/index.md" --decision "$DECISION" 2>&1 | tail -1
      fi
      ;;

    guides)
      SOURCE="docs/ai/lifecycle/01_PRD.md"
      if [ ! -f "$ROOT/$SOURCE" ]; then
        echo "   源文件不存在: $SOURCE，跳过"
        continue
      fi
      if $DRY_RUN; then
        echo "  [dry-run] $SOURCE → docs/guides/index.md"
      else
        echo "   $SOURCE → docs/guides/index.md"
        cat "$ROOT/$SOURCE" | bash "$PROMOTE" "docs/guides/index.md" --decision "$DECISION" 2>&1 | tail -1
      fi
      ;;

    changelog)
      # changelog 用 --append 追加，不 --auto
      if $DRY_RUN; then
        echo "  [dry-run] 追加 $DECISION 到 docs/changelog/index.md"
      else
        echo "   追加 $DECISION 到 docs/changelog/index.md"
        DEC_TITLE=$(head -1 "$DEC_FILE" | sed 's/^# *//' | sed 's/"/\\"/g')
        echo "| $(date +%Y-%m-%d) | $DEC_TITLE | $DECISION |" | \
          bash "$PROMOTE" "docs/changelog/index.md" --decision "$DECISION" --append 2>&1 | tail -1
      fi
      ;;

    notes)
      # notes 专用逻辑：扫描 NOTE 文件，更新索引
      if $DRY_RUN; then
        NOTE_COUNT=$(find "$DOCS_DIR/notes" -maxdepth 1 -name "NOTE-*.md" -not -name "index.md" 2>/dev/null | wc -l | tr -d ' ')
        echo "  [dry-run] 更新 docs/notes/index.md（发现 $NOTE_COUNT 条 NOTE）"
      else
        echo "   更新 docs/notes/index.md 索引"
        bash "$NOTE_SCRIPT" --help >/dev/null 2>&1 || true
        # 用 gov-next-note.sh 的索引重建功能：再次创建会触发索引更新
        # 这里只做索引刷新，不创建新 NOTE
        if [ -f "$NOTE_SCRIPT" ]; then
          NOTE_FILES=$(find "$DOCS_DIR/notes" -maxdepth 1 -name "NOTE-*.md" -not -name "index.md" 2>/dev/null | sort)
          # 直接调用 python3 刷新索引表格
          python3 -c "
import re, os, glob
index_path = '$DOCS_DIR/notes/index.md'
note_files = sorted(glob.glob('$DOCS_DIR/notes/NOTE-*.md'))
rows = []
for nf in note_files:
    nid = os.path.basename(nf).replace('.md', '')
    with open(nf) as f:
        title = f.readline().strip().replace('# ', '').strip()
    rows.append(f'| {nid[5:13]} {nid[9:13]} | ... | {title} | ... | [{nid}.md]({nid}.md) |')
with open(index_path) as f:
    text = f.read()
header_end = text.find('|------|')
if header_end > 0:
    new_table = '\n'.join(rows)
    marker_end = text.find('\n', header_end)
    after = text[marker_end:]
    # 保留状态说明部分
    status_part = after[after.find('## 状态说明'):] if '## 状态说明' in after else ''
    new_text = text[:marker_end+1] + '\n' + new_table + '\n\n' + status_part
    with open(index_path, 'w') as f:
        f.write(new_text)
    print(f'  索引已更新: {len(rows)} 条 NOTE')
" 2>/dev/null || echo "   索引更新失败（python3 不可用）"
        fi
      fi
      ;;
  esac
  echo ""
done

# ---------- 可选：运行 validate.sh 校验 ----------
if ! $DRY_RUN && [ -n "$TARGETS" ]; then
  echo "--- 运行 validate.sh 校验 ---"
  bash "$VALIDATE" 2>&1 | grep -E "(FAIL|WARN|PASS)" | tail -5
  echo ""
fi

echo "========================================"
echo " 同步完成"
echo "========================================"
