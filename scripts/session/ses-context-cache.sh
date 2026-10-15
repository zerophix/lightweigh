#!/usr/bin/env bash
#
# scripts/session/ses-context-cache.sh
# 跨平台 AI 上下文缓存 — 任何 AI 工具可读写
#
# 用法:
#   context-cache.sh save <type> <key=val>...   # 写入一条缓存
#   context-cache.sh get <type> [--limit N]      # 读取指定类型
#   context-cache.sh search <keyword>            # 搜索上下文
#   context-cache.sh status                      # 缓存统计
#   context-cache.sh expire <id>                 # 标记某条过期
#   context-cache.sh init                        # 初始化缓存（首次运行）
#
# 类型:
#   user-preference     — 用户偏好（禁用emoji、严格模式等）
#   decision-context    — 决策上下文（被否决的方案、权衡）
#   known-issue         — 已知技术债务和遗留问题
#   approach-attempted  — 尝试过但放弃的方案
#   current-task        — 当前正在做的任务
#   session-summary     — 本 session 结束时写入的摘要
set -euo pipefail

CACHE_DIR="$(cd "$(dirname "$0")/../.." && pwd)/.cache"
CACHE_FILE="$CACHE_DIR/context.jsonl"
CACHE_VERSION=1

# 合法类型及必填字段
VALID_TYPES="user-preference known-issue approach-tried session-summary session-start handoff current-task learning decision-context"

# 每种类型的必填字段（空格分隔）
REQUIRED_FIELDS_user_preference="key value"
REQUIRED_FIELDS_known_issue="desc severity workaround"
REQUIRED_FIELDS_approach_tried="what outcome why"
REQUIRED_FIELDS_session_summary="dec scope"
REQUIRED_FIELDS_session_start="dec next_task"
REQUIRED_FIELDS_handoff="from to context next key_files"
REQUIRED_FIELDS_current_task="task status started_at"
REQUIRED_FIELDS_learning="what context principle"
REQUIRED_FIELDS_decision_context="dec"

help() {
  sed -n '3,17p' "$0"
  exit 0
}

[ "${1:-}" = "--help" ] && help

init() {
  mkdir -p "$CACHE_DIR"
  if [ ! -f "$CACHE_FILE" ]; then
    echo '[]' > "$CACHE_FILE"
    # Use a simple line-based format instead
    : > "$CACHE_FILE"
    echo "cache initialized at $CACHE_FILE"
  else
    echo "cache already exists"
  fi
}

save() {
  local type="$1"; shift

  # 校验类型
  local valid=false
  for t in $VALID_TYPES; do
    if [ "$t" = "$type" ]; then valid=true; break; fi
  done
  if ! $valid; then
    echo "错误: 未知类型 '$type'，合法类型: $VALID_TYPES" >&2
    return 1
  fi

  # 校验必填字段
  local varname="REQUIRED_FIELDS_$(echo "$type" | tr '-' '_')"; eval "required=\"\$$varname\""
  if [ -n "$required" ]; then
    local kv_args="$@"
    local missing=""
    for field in $required; do
      if ! echo "$kv_args" | grep -qE "(^| )${field}="; then
        missing="$missing $field"
      fi
    done
    if [ -n "$missing" ]; then
      echo "错误: 类型 '$type' 缺少必填字段:$missing" >&2
      return 1
    fi
  fi

  local entry="{}"
  entry=$(echo "$entry" | python3 -c "
import json, sys
entry = {}
for arg in sys.argv[1:]:
    if '=' in arg:
        k, v = arg.split('=', 1)
        entry[k] = v
# Add metadata
entry['type'] = '$type'
entry['ts'] = '$(date -u +%Y-%m-%dT%H:%M:%SZ)'
entry['agent'] = '$(hostname -s 2>/dev/null || echo "unknown")'
entry['id'] = 'ctx-$(date +%s)-$$'
print(json.dumps(entry))
" "$@")
  # Ensure file starts with newline to avoid concatenation
  if [ -f "$CACHE_FILE" ] && [ -s "$CACHE_FILE" ]; then
    last_char=$(tail -c 1 "$CACHE_FILE" 2>/dev/null || echo "")
    if [ -n "$last_char" ] && [ "$last_char" != $'\n' ]; then
      echo "" >> "$CACHE_FILE"
    fi
  fi
  echo "$entry" >> "$CACHE_FILE"
  echo "saved: $(echo "$entry" | python3 -c 'import json,sys; d=json.load(sys.stdin); print(d.get("id","?"))')"
}

get() {
  local type="$1"; shift
  local limit=10
  while [ $# -gt 0 ]; do
    case "$1" in --limit) limit="$2"; shift ;; *) ;; esac; shift
  done
  python3 -c "
import json, sys
entries = []
try:
    with open('$CACHE_FILE') as f:
        for line in f:
            line = line.strip()
            if not line: continue
            try:
                e = json.loads(line)
                if e.get('type') == '$type' and not e.get('stale'):
                    entries.append(e)
            except: pass
except: pass
entries.sort(key=lambda x: x.get('ts', ''), reverse=True)
for e in entries[:$limit]:
    print(json.dumps(e, indent=2))
    print('---')
if not entries:
    print('(no entries)')
" 2>/dev/null || echo "(cache not initialized)"
}

search() {
  local kw="$1"
  python3 -c "
import json, sys
try:
    with open('$CACHE_FILE') as f:
        for line in f:
            line = line.strip()
            if not line: continue
            try:
                e = json.loads(line)
                if '$kw'.lower() in json.dumps(e).lower():
                    print(json.dumps(e, indent=2))
                    print('---')
            except: pass
except: pass
" 2>/dev/null || echo "(cache not initialized)"
}

status() {
  if [ ! -f "$CACHE_FILE" ]; then echo "cache not initialized"; exit 1; fi
  python3 -c "
import json
from collections import Counter
types = Counter()
agents = Counter()
try:
    with open('$CACHE_FILE') as f:
        for line in f:
            line = line.strip()
            if not line: continue
            try:
                e = json.loads(line)
                if e.get('stale'): types['STALE'] += 1
                else: types[e.get('type','?')] += 1
                agents[e.get('agent','?')] += 1
            except: pass
except: pass
total = sum(types.values())
print(f'Total entries: {total}')
print(f'Types:')
for t, c in types.most_common():
    print(f'  {t}: {c}')
print(f'Agents:')
for a, c in agents.most_common():
    print(f'  {a}: {c}')
" 2>/dev/null
}

expire() {
  local id="$1"
  python3 -c "
import json
entries = []
try:
    with open('$CACHE_FILE') as f:
        for line in f:
            line = line.strip()
            if not line: continue
            try:
                e = json.loads(line)
                if e.get('id') == '$id':
                    e['stale'] = True
                    e['expired_ts'] = '$(date -u +%Y-%m-%dT%H:%M:%SZ)'
                entries.append(e)
            except: pass
    with open('$CACHE_FILE', 'w') as f:
        for e in entries:
            f.write(json.dumps(e) + '\n')
    print(f'expired: $id')
except: print('not found')
" 2>/dev/null
}

case "${1:-}" in
  init) init ;;
  save) shift; save "$@" ;;
  get) shift; get "$@" ;;
  search) shift; search "$@" ;;
  status) status ;;
  expire) shift; expire "$@" ;;
  *) help ;;
esac
