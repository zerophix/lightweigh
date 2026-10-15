#!/usr/bin/env bash
# ============================================================
# scripts/workflow/wf-promote.sh
# 晋升写入 — 将内容写入黄金标准文件并自动添加来源标注
#
# 用法:
#   cat content.md | scripts/workflow/wf-promote.sh <target-file> --decision DEC-XXX
#
# 示例:
#   cat 架构摘要.md | scripts/workflow/wf-promote.sh docs/architecture/index.md --decision DEC-003
#
# 行为:
#   读取 stdin → 写入 target → 追加/更新 ## 来源: 标注
# ============================================================

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"

# ---------- 工具函数 ----------

# 安全追加单行到 markdown 表格（按日期去重，避免死循环）
append_entry() {
  local file="$1" entry="$2"
  local tmp="${file}.tmp.$$"
  # 提取条目中的日期和内容做指纹
  local fingerprint
  fingerprint=$(echo "$entry" | sed 's/^| *//;s/ *| *$//' | cut -d'|' -f1-3 | tr -d ' ')
  # 检查是否已存在
  if grep -qF "$fingerprint" "$file" 2>/dev/null; then
    return 0
  fi
  # 找到第一个数据行（日期行）的位置，在其前插入
  local insert_line
  insert_line=$(grep -n '^| *[0-9]\{4\}-[0-9]\{2\}-[0-9]\{2\}' "$file" 2>/dev/null | head -1 | cut -d: -f1)
  if [ -n "$insert_line" ]; then
    head -n "$((insert_line - 1))" "$file" > "$tmp"
    echo "$entry" >> "$tmp"
    tail -n "+${insert_line}" "$file" >> "$tmp"
    mv "$tmp" "$file"
  else
    # 没有表格：在文件末尾（来源标注前）追加
    echo "" >> "$file"
    echo "$entry" >> "$file"
  fi
}

# ---------- 允许目标 ----------

ALLOWED_TARGETS=(
    "docs/architecture/index.md"
    "docs/guides/index.md"
    "docs/reference/index.md"
    "docs/changelog/index.md"
    "docs/notes/index.md"
)
ALLOWED_REFERENCE_RE='^docs/reference/REF-[a-z0-9-]+\.md$'

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BOLD='\033[1m'
NC='\033[0m'

DECISION=""
TARGET=""
VERIFY=false
APPEND=false

while [ $# -gt 0 ]; do
    [ "$1" = "--verify" ] && VERIFY=true && shift && continue
    case "$1" in
        --decision) DECISION="$2"; shift 2 ;;
        --verify) VERIFY=true; shift ;;
        --append) APPEND=true; shift ;;
        --auto)
            # 自动补全 docs/ 前缀
            AUTO_SOURCE="$2"
            if [[ "$AUTO_SOURCE" != docs/* ]]; then
                AUTO_SOURCE="docs/$AUTO_SOURCE"
            fi
            if [ ! -f "$ROOT/$AUTO_SOURCE" ]; then
                echo -e "${RED}[FAIL] 源文件不存在: $AUTO_SOURCE${NC}" >&2
                exit 1
            fi
            shift 2
            ;;
        -h|--help)
            echo "用法: cat content | scripts/workflow/wf-promote.sh <target> --decision DEC-XXX [--verify]"
            echo ""
            echo "参数:"
            echo "  --decision DEC-XXX  来源决策编号（必填）"
            echo "  --verify            写入后自动运行 validate.sh"
            echo "  --append            追加模式（不覆盖，用于 changelog）"
            echo ""
            echo "允许的 target:"; for T in "${ALLOWED_TARGETS[@]}"; do echo "  $T"; done; echo "  docs/reference/REF-{slug}.md"
            exit 0 ;;
        -*)
            echo -e "${RED}未知参数: $1${NC}" >&2; exit 1 ;;
        *)
            [ -n "$TARGET" ] && { echo -e "${RED}多余参数: $1${NC}" >&2; exit 1; }
            TARGET="$1"; shift ;;
    esac
done

[ -z "$TARGET" ] && { echo -e "${RED}[FAIL] 缺少 target 参数${NC}" >&2; exit 1; }
[ -z "$DECISION" ] && { echo -e "${RED}[FAIL] 缺少 --decision 参数${NC}" >&2; exit 1; }

VALID=0
for ALLOWED in "${ALLOWED_TARGETS[@]}"; do
    [ "$TARGET" = "$ALLOWED" ] && VALID=1
done
# 也允许 reference/REF-{slug}.md
[[ "$TARGET" =~ $ALLOWED_REFERENCE_RE ]] && VALID=1
[ $VALID -eq 0 ] && { echo -e "${RED}[FAIL] 不允许的目标: $TARGET${NC}" >&2; exit 1; }
! echo "$DECISION" | grep -qE '^DEC-[0-9]{3}$' && { echo -e "${RED}[FAIL] 决策编号格式: $DECISION${NC}" >&2; exit 1; }

# --auto 模式：从生命周期文档自动提取摘要
if [ -n "${AUTO_SOURCE:-}" ]; then
    SOURCE_PATH="$ROOT/$AUTO_SOURCE"
    echo -e "${BOLD}自动提取: $AUTO_SOURCE → $TARGET${NC}"

    # 提取所有 ## 二级标题及其下内容（过滤掉 ### 及版本历史）
    CONTENT=$(python3 -c "
import re, sys
with open('$SOURCE_PATH') as f:
    text = f.read()

# 按 ## 分割
sections = re.split(r'\n(?=## )', text)

# 提取标题和第一段关键内容
extracted = []
for s in sections:
    lines = s.strip().split('\n')
    title = lines[0] if lines else ''
    # 跳过版本历史
    if '版本历史' in title or '版本' in title:
        continue
    # 取前 3 段非空内容
    body = []
    for line in lines[1:]:
        stripped = line.strip()
        if stripped and not stripped.startswith('>') and not stripped.startswith('---'):
            body.append(stripped)
        if len(body) >= 3:
            break
    if body:
        extracted.append(title)
        extracted.extend(body)
        extracted.append('')

sys.stdout.write('\n'.join(extracted))
")
    echo -e "${GREEN}[INFO]${NC} 从 $AUTO_SOURCE 提取了摘要"
else
    # 读 stdin
    CONTENT=$(cat)
    [ -z "$CONTENT" ] && { echo -e "${RED}[FAIL] stdin 为空${NC}" >&2; exit 1; }
fi

FULL_PATH="$ROOT/$TARGET"

# git 检查：如果目标文件在 git 中没有被 track，先警告
if [ -f "$FULL_PATH" ]; then
    if ! git -C "$ROOT" ls-files --error-unmatch "$FULL_PATH" &>/dev/null 2>&1; then
        echo -e "${YELLOW}[WARN]${NC} 目标文件未被 git 追踪: $TARGET"
        echo -e "       建议先 ${BOLD}git add $TARGET && git commit${NC} 再替换，否则旧版本会丢失"
    fi
fi

mkdir -p "$(dirname "$FULL_PATH")"

# 用 Python 写入（跨平台，准确处理来源标注）
python3 -c "
import sys, re, os

content = sys.stdin.read()
target = '$FULL_PATH'
decision = '$DECISION'
append_mode = '$APPEND' == 'true'

if append_mode:
    # 追加模式：在末尾插入新内容，保留原有来源标注
    existing = ''
    if os.path.exists(target):
        with open(target) as f:
            existing = f.read()
    # 找到旧的来源标注位置
    src_marker = '## 来源:'
    before_src = existing
    after_src = ''
    if src_marker in existing:
        idx = existing.rfind(src_marker)
        before_src = existing[:idx].rstrip()
        after_src = existing[idx:]
    # 拼接：旧内容（去掉来源） + 新内容 + 旧来源
    combined = before_src + '\n\n' + content.strip()
    if after_src:
        combined += '\n\n' + after_src
    else:
        combined += '\n\n## 来源: ai/decisions/%s\n' % decision
    with open(target, 'w') as f:
        f.write(combined.strip() + '\n')
else:
    # 覆盖模式：移除旧来源标注，写入新内容
    content_clean = re.sub(r'^## 来源:.*\n?', '', content, flags=re.MULTILINE)
    content_clean = content_clean.strip()
    with open(target, 'w') as f:
        f.write(content_clean)
        f.write('\n\n## 来源: ai/decisions/%s\n' % decision)
" <<< "$CONTENT"

echo -e "${GREEN}[PASS]${NC} 已写入 $TARGET (来源: ai/decisions/$DECISION)"

# --verify 模式：写入后自动校验
if $VERIFY; then
    echo ""
    echo -e "${BOLD}写入后校验:${NC}"
    VALIDATE_SCRIPT="$(cd "$(dirname "$0")" && pwd)/validate.sh"
    if [ -f "$VALIDATE_SCRIPT" ]; then
        bash "$VALIDATE_SCRIPT"
        VERIFY_RESULT=$?
        if [ $VERIFY_RESULT -ne 0 ]; then
            echo -e "${YELLOW}[WARN]${NC} 校验发现问题，请检查"
        fi
    fi
fi
