#!/usr/bin/env bash
# ============================================================
# scripts/workflow/wf-inbox.sh
# AI 接力收件箱 — 跨 session 消息留信
#
# 用法:
#   bash scripts/workflow/wf-inbox.sh send "主题" "内容"     # 留信给下一个 AI
#   bash scripts/workflow/wf-inbox.sh list                   # 列出所有未读消息
#   bash scripts/workflow/wf-inbox.sh read <id>              # 读取消息详情
#   bash scripts/workflow/wf-inbox.sh mark <id>              # 标记已读
#   bash scripts/workflow/wf-inbox.sh clean                  # 清理 30 天前的消息
# ============================================================

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$ROOT/scripts/lib/lib-platform.sh" 2>/dev/null || true
INBOX_DIR="$ROOT/docs/ai/inbox"

mkdir -p "$INBOX_DIR"

now() {
  date -u +%Y-%m-%dT%H:%M:%SZ 2>/dev/null || echo "unknown"
}

ts() {
  date -u +%Y%m%d-%H%M%S 2>/dev/null || echo "unknown"
}

case "${1:-}" in
  send)
    SUBJECT="${2:-}"
    CONTENT="${3:-}"
    if [ -z "$SUBJECT" ]; then
      echo "用法: inbox.sh send \"主题\" \"内容\"" >&2
      exit 1
    fi
    TIMESTAMP=$(ts)
    # 署名走 lib-platform.sh 的统一身份检测（DEC-009: 不冒充）；此前这里硬编码
    # "claude", 导致任何平台的接力信都署 claude
    AGENT="$(detect_agent 2>/dev/null || echo unknown-ai)"
    FILE="$INBOX_DIR/$TIMESTAMP.md"
    cat > "$FILE" << EOF
---
to: next-ai
from: $AGENT
timestamp: $(now)
subject: $SUBJECT
status: unread
---

# $SUBJECT

$CONTENT

---

## 交接清单

### 已完成
<!-- 列出当前 session 完成的任务 -->

### 未完成
<!-- 列出留给下一个 AI 的任务 -->

### 已知坑
<!-- 当前遇到的陷阱和注意事项 -->

### 下一步
<!-- 下一个人应该做什么 -->
EOF
    echo "已留信: $FILE"
    ;;

  list)
    echo "=== AI 收件箱 ==="
    echo ""
    FILES=$(find "$INBOX_DIR" -maxdepth 1 -name "*.md" -not -name ".gitkeep" | sort -r)
    if [ -z "$FILES" ]; then
      echo "  收件箱为空"
      exit 0
    fi
    for f in $FILES; do
      TS=$(grep "^timestamp:" "$f" 2>/dev/null | sed 's/.*: *//' | head -1)
      SUBJ=$(grep "^subject:" "$f" 2>/dev/null | sed 's/.*: *//' | head -1)
      STAT=$(grep "^status:" "$f" 2>/dev/null | sed 's/.*: *//' | head -1)
      FROM=$(grep "^from:" "$f" 2>/dev/null | sed 's/.*: *//' | head -1)
      BASE=$(basename "$f" .md)
      ICON=""
      [ "$STAT" = "read" ] && ICON=""
      echo "  $ICON [$BASE] $SUBJ"
      echo "      $FROM @ $TS | $STAT"
      echo ""
    done
    ;;

  read)
    ID="${2:-}"
    if [ -z "$ID" ]; then
      echo "用法: inbox.sh read <id>" >&2
      echo "  id 是文件名中的时间戳，如 20260710-083000"
      echo "  运行 inbox.sh list 查看可用 id"
      exit 1
    fi
    FILE=$(find "$INBOX_DIR" -maxdepth 1 -name "$ID.md" | head -1)
    if [ -z "$FILE" ]; then
      echo "未找到消息: $ID" >&2
      exit 1
    fi
    cat "$FILE"
    ;;

  mark)
    ID="${2:-}"
    if [ -z "$ID" ]; then
      echo "用法: inbox.sh mark <id>" >&2
      exit 1
    fi
    FILE=$(find "$INBOX_DIR" -maxdepth 1 -name "$ID.md" | head -1)
    if [ -z "$FILE" ]; then
      echo "未找到消息: $ID" >&2
      exit 1
    fi
    sed_i "s/^status: .*/status: read/" "$FILE"
    echo "已标记已读: $ID"
    ;;

  clean)
    python3 -c "
import os, time, glob
inbox = '$INBOX_DIR'
now = time.time()
limit = 30 * 86400
for f in glob.glob(os.path.join(inbox, '*.md')):
    if os.path.getmtime(f) < now - limit:
        os.remove(f)
        print(f'删除过期: {os.path.basename(f)}')
print('清理完成')
"
    ;;

  *)
    echo "用法: bash scripts/workflow/wf-inbox.sh <命令> [参数]"
    echo ""
    echo "命令:"
    echo "  send \"主题\" \"内容\"   留信给下一个 AI"
    echo "  list                 列出所有消息"
    echo "  read <id>            读取消息"
    echo "  mark <id>            标记已读"
    echo "  clean                清理 30 天前的旧消息"
    ;;
esac
