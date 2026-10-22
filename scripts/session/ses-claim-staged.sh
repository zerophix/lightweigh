#!/usr/bin/env bash
# scripts/session/ses-claim-staged.sh
# 声明当前暂存区文件归属于当前会话
#
# 用法:
#   bash scripts/session/ses-claim-staged.sh          # 声明所有已暂存文件
#   bash scripts/session/ses-claim-staged.sh <files>  # 声明指定文件

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
SESSION_FILE="$ROOT/.ai/current-session.json"
CLAIM_FILE="$ROOT/.ai/staged-claims.json"

# 读取当前会话 ID
if [ ! -f "$SESSION_FILE" ]; then
  echo " 未找到会话标识文件: $SESSION_FILE"
  echo "   请先启动会话: bash scripts/session/ses-session-begin.sh"
  exit 1
fi

SESSION_ID=$(python3 -c "
import json
with open('$SESSION_FILE') as f:
    d = json.load(f)
print(d.get('session_id', ''))
" 2>/dev/null)

if [ -z "$SESSION_ID" ]; then
  echo " 会话标识文件格式错误: $SESSION_FILE"
  exit 1
fi

echo " 当前会话: $SESSION_ID"

# 读取现有声明
if [ -f "$CLAIM_FILE" ]; then
  CLAIMS=$(cat "$CLAIM_FILE")
else
  CLAIMS='{}'
fi

# 获取要声明的文件
if [ $# -eq 0 ]; then
  # 声明所有已暂存文件
  STAGED_FILES=$(git diff --cached --name-only --diff-filter=ACMR | tr '\n' ' ')
else
  STAGED_FILES="$*"
fi

if [ -z "$STAGED_FILES" ]; then
  echo "ℹ  没有需要声明的文件"
  exit 0
fi

# 更新声明（包含声明文件本身，用相对路径）
CLAIM_FILE_REL=".ai/staged-claims.json"
ALL_FILES="$STAGED_FILES $CLAIM_FILE_REL"
NEW_CLAIMS=$(python3 -c "
import json, sys
claims = json.loads('''$CLAIMS''')
session_id = '''$SESSION_ID'''
for f in '''$ALL_FILES'''.split():
    claims[f] = session_id
print(json.dumps(claims, ensure_ascii=False))
")

echo "$NEW_CLAIMS" > "$CLAIM_FILE"

echo " 已声明归属 $SESSION_ID:"
for f in $STAGED_FILES; do
  echo "   $f"
done
echo "   $CLAIM_FILE_REL (声明文件)"

# 自动 stage 声明文件本身
git add "$CLAIM_FILE_REL" 2>/dev/null || true

