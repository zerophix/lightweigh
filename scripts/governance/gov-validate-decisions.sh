#!/usr/bin/env bash
#
# 决策真实性校验 — 防止 AI 编造空洞内容
#

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
DECISIONS_DIR="docs/ai/decisions"

# 获取所有 staged 的决策文件
DECISIONS=$(git diff --cached --name-only --diff-filter=ACMR | grep "^${DECISIONS_DIR}/DEC-" || true)

if [ -z "$DECISIONS" ]; then
  exit 0
fi

FAIL=0
WARN=0

echo ""
echo "=== Decision Authenticity Validation ==="
for dec in $DECISIONS; do
  echo "→ $dec"

  # 1. 文件非空
  if [ ! -s "$dec" ]; then
    echo "    File is empty"
    FAIL=1
    continue
  fi

  # 2. 标题非占位符
  TITLE=$(grep -E '^# DEC-[0-9]+:' "$dec" | head -1 || true)
  if echo "$TITLE" | grep -qE '<.*>'; then
    echo "    Title contains placeholder"
    FAIL=1
  fi

  # 3. "## 决策" 章节存在且内容充足
  if grep -q '^## 决策' "$dec"; then
    # Extract lines between "## 决策" and the next "## " section or EOF
    CONTENT=$(sed -n '/^## 决策/,/^## /{/^## /d;p;}' "$dec")
    WORD=$(echo "$CONTENT" | wc -w | tr -d ' ')
    if [ "$WORD" -lt 20 ]; then
      echo "    '## 决策' section too brief ($WORD words)"
      FAIL=1
    fi
  else
    echo "    Missing '## 决策' section"
    FAIL=1
  fi

  # 4. 方案评分表格完整性
  if grep -q '^## 方案评分' "$dec"; then
    ROWS=$(sed -n '/^## 方案评分/,/^## /p' "$dec" | grep -c '|')
    if [ "$ROWS" -lt 4 ]; then
      echo "     Sparse table ($ROWS rows). Add full comparison."
      WARN=1
    fi
  fi

  # 5. AI痕迹检测（中英文双语）
  AI_PATTERNS_EN=("as an AI" "based on the analysis" "I recommend" "we recommend" "as a language model" "I cannot" "I'm sorry")
  for pat in "${AI_PATTERNS_EN[@]}"; do
    if grep -iq "$pat" "$dec"; then
      echo "     AI-generated phrase: '$pat'"
      WARN=1
    fi
  done
  AI_PATTERNS_CN=("根据我的分析" "作为AI" "作为一个AI" "我建议" "我不能" "对不起" "很抱歉")
  for pat in "${AI_PATTERNS_CN[@]}"; do
    if grep -iq "$pat" "$dec"; then
      echo "     AI-generated phrase: '$pat'"
      WARN=1
    fi
  done

  # 6. 决策编号一致性
  DEC_ID=$(basename "$dec" .md)
  if ! grep -q "^# $DEC_ID:" "$dec"; then
    echo "    Decision ID '$DEC_ID' missing in header"
    FAIL=1
  fi

  echo ""
done

if [ "$WARN" -ne 0 ]; then
  echo "  $WARN warning(s) found."
fi

if [ "$FAIL" -ne 0 ]; then
  echo " Decision validation FAILED."
  exit 1
fi

echo " All decisions passed authenticity checks."
exit 0