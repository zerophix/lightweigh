#!/usr/bin/env bash
# scripts/dev/check-superseded.sh
# 检查"已被新设计替换的旧实现"有没有回流。
#
# 为什么需要：原型换代时最常见的退化是"新 UI 加上了，旧 UI 还留着"，
# 两套并存会再次出现假流程、重复页面、改一处漂一处。
set -uo pipefail
cd "$(git rev-parse --show-toplevel)" || exit 2

LIST="scripts/dev/superseded.txt"
[ -f "$LIST" ] || { echo "缺少 $LIST"; exit 2; }

bad=0
while IFS= read -r path; do
  case "$path" in ''|\#*) continue ;; esac
  if [ -e "$path" ]; then
    echo "旧实现回流: ${path}（已被新设计替换，应删除而不是并存）" >&2
    bad=$((bad + 1))
  fi
done < "$LIST"

if [ "$bad" -gt 0 ]; then
  echo "共 $bad 个旧实现回流；删掉它们，或确认替换计划后从 $LIST 移除登记。" >&2
  exit 1
fi
echo "superseded: 干净（无旧实现回流）"
