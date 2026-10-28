#!/usr/bin/env bash
# 统一锁管理 — 任务锁 + 共享文件锁 + 模式锁
#
# 用法:
#   # 任务锁 (向后兼容):
#   wf-lock.sh <task-id> [ttl-minutes]
#   wf-lock.sh --check <task-id>
#
#   # 共享文件锁 (DEC-077):
#   wf-lock.sh file-acquire <file> [--mode docs|gov] [--ttl 240]
#   wf-lock.sh file-release <file>
#   wf-lock.sh file-check <file>
#
# 锁文件: .ai/locks/<key>.lock
#   { "holder": "ses-xxx", "mode": "docs", "file": "AGANTS.md",
#     "acquired_at": 1718..., "expires_at": 1718... }

set -uo pipefail

ROOT="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"
LOCK_DIR="$ROOT/.ai/locks"
LOCK_FILE="$ROOT/.ai/task-lock.json"
SESSION_FILE="$ROOT/.ai/current-session.json"
DEFAULT_TTL=30  # 分钟

mkdir -p "$LOCK_DIR"

get_session() {
  python3 -c "
import json
try:
    with open('$SESSION_FILE') as f: print(json.load(f).get('session_id',''))
except: print('')
" 2>/dev/null
}

# --- Existing: Task Lock (backward compatible) ---

acquire_task() {
  local task_id="$1"
  local ttl="${2:-$DEFAULT_TTL}"
  local session; session="$(get_session)"
  [ -z "$session" ] && session="$$-$(date +%s)"

  python3 - "$task_id" "$session" "$ttl" "$LOCK_FILE" <<'PYEOF'
import json, sys, time, os
task_id, session, ttl, lock_file = sys.argv[1], sys.argv[2], int(sys.argv[3]), sys.argv[4]
now = time.time()
expires = now + ttl * 60

lock = {}
if os.path.exists(lock_file):
    try:
        with open(lock_file) as f: lock = json.load(f)
    except: pass

lock = {k: v for k, v in lock.items() if v.get('expires', 0) > now}

if task_id in lock and lock[task_id].get('session') != session:
    holder = lock[task_id]
    remaining = int((holder['expires'] - now) / 60)
    print(f"LOCKED by {holder['session']} (剩余 {remaining} 分钟)")
    sys.exit(1)

lock[task_id] = {'session': session, 'acquired': now, 'expires': expires}
os.makedirs(os.path.dirname(lock_file), exist_ok=True)
with open(lock_file, 'w') as f:
    json.dump(lock, f, indent=2)
print(f"LOCKED {task_id} by {session} (TTL {ttl}min)")
PYEOF
}

release_task() {
  local session; session="$(get_session)"
  [ -z "$session" ] && session="$$-$(date +%s)"

  python3 - "$session" "$LOCK_FILE" <<'PYEOF'
import json, sys, time, os
session, lock_file = sys.argv[1], sys.argv[2]
now = time.time()
if not os.path.exists(lock_file): sys.exit(0)
try:
    with open(lock_file) as f: lock = json.load(f)
except: sys.exit(0)
lock = {k: v for k, v in lock.items() if v.get('session') != session and v.get('expires', 0) > now}
with open(lock_file, 'w') as f: json.dump(lock, f, indent=2)
print(f"UNLOCKED all task locks held by {session}")
PYEOF
}

check_task() {
  local task_id="$1"
  python3 - "$task_id" "$LOCK_FILE" <<'PYEOF'
import json, sys, time, os
task_id, lock_file = sys.argv[1], sys.argv[2]
now = time.time()
if not os.path.exists(lock_file): print("UNLOCKED"); sys.exit(0)
try:
    with open(lock_file) as f: lock = json.load(f)
except: print("UNLOCKED"); sys.exit(0)
lock = {k: v for k, v in lock.items() if v.get('expires', 0) > now}
if task_id not in lock: print("UNLOCKED"); sys.exit(0)
h = lock[task_id]
remaining = int((h['expires'] - now) / 60)
print(f"LOCKED by {h['session']} (剩余 {remaining} 分钟)")
PYEOF
}

# --- New: Shared File Lock (DEC-077) ---

_file_key() {
    local file="$1"
    # Use sha256 of the absolute path as lock filename (safe for paths with /)
    echo "$(echo "$file" | sha256sum | cut -d' ' -f1).lock"
}

acquire_file() {
  local file="$1"
  local mode="docs"
  local ttl="$DEFAULT_TTL"

  # Parse options
  shift
  while [ $# -gt 0 ]; do
    case "$1" in
      --mode) mode="$2"; shift 2 ;;
      --ttl) ttl="$2"; shift 2 ;;
      *) shift ;;
    esac
  done

  local session; session="$(get_session)"
  [ -z "$session" ] && session="$$-$(date +%s)"

  local lock_key; lock_key="$(_file_key "$file")"
  local lock_path="$LOCK_DIR/$lock_key"
  local now; now=$(date +%s)
  local expires=$(( now + ttl * 60 ))
  local branch; branch="$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo 'unknown')"

  # Check for existing lock
  if [ -f "$lock_path" ]; then
    local lock_info; lock_info=$(python3 -c "
import json, sys, time
try:
    with open('$lock_path') as f: d=json.load(f)
    if d.get('expires_at', 0) > time.time():
        print(d.get('holder','unknown'))
        print(d.get('mode','unknown'))
        sys.exit(0)
    else:
        print('')  # expired
except:
    print('')
" 2>/dev/null)
    if [ -n "$lock_info" ]; then
      local lock_holder lock_mode
      lock_holder=$(echo "$lock_info" | sed -n '1p')
      lock_mode=$(echo "$lock_info" | sed -n '2p')
      local remaining=$(( expires - now ))
      echo "❌ 文件已锁定: $file"
      echo "   持有者: $lock_holder (模式: $lock_mode)"
      echo "   请联系该会话结束后再编辑, 或过期后重试"
      echo "   紧急: bash scripts/workflow/wf-lock.sh file-release \"$file\""
      return 1
    fi
  fi

  # Acquire lock
  python3 -c "
import json, time
d = {
    'file': '$file',
    'holder': '$session',
    'mode': '$mode',
    'branch': '$branch',
    'acquired_at': $now,
    'expires_at': $expires
}
print(json.dumps(d, indent=2, ensure_ascii=False))
" > "$lock_path"

  echo "✅ 锁定共享文件: $file"
  echo "   模式: $mode  持有者: $session  TTL: ${ttl}min"
  echo "   解锁: bash scripts/workflow/wf-lock.sh file-release \"$file\""

  # Log to audit
  mkdir -p "$ROOT/.ai/audit"
  printf '[%s] session=%s file=%s mode=%s action=file_lock_acquired ttl=%smin\n' \
    "$(date -u '+%Y-%m-%dT%H:%M:%SZ')" "$session" "$file" "$mode" "$ttl" \
    >> "$ROOT/.ai/audit/bypass.log"
}

release_file() {
  local file="$1"
  local lock_key; lock_key="$(_file_key "$file")"
  local lock_path="$LOCK_DIR/$lock_key"

  if [ ! -f "$lock_path" ]; then
    echo "  未锁定: $file"
    return 0
  fi

  local session; session="$(get_session)"
  local holder; holder=$(python3 -c "
import json
try:
    with open('$lock_path') as f: d=json.load(f)
    print(d.get('holder',''))
except: print('')
" 2>/dev/null)

  if [ -n "$holder" ] && [ "$holder" != "$session" ]; then
    echo "❌ 不能释放他人的锁: $file"
    echo "   持有者: $holder  当前会话: ${session:-unknown}"
    echo "   紧急: 管理员手动删除 .ai/locks/$(basename "$lock_path")"
    return 1
  fi

  rm -f "$lock_path"
  echo "✅ 释放共享文件锁: $file"
}

check_file() {
  local file="$1"
  local lock_key; lock_key="$(_file_key "$file")"
  local lock_path="$LOCK_DIR/$lock_key"

  if [ ! -f "$lock_path" ]; then
    echo "UNLOCKED: $file"
    return 0
  fi

  python3 -c "
import json, time
try:
    with open('$lock_path') as f: d=json.load(f)
    if d.get('expires_at', 0) <= time.time():
        print(f'EXPIRED: $file')
        import os; os.unlink('$lock_path')
    else:
        remaining = int((d['expires_at'] - time.time()) / 60)
        print(f'LOCKED: $file (holder={d.get(\"holder\",\"?\")}, mode={d.get(\"mode\",\"?\")}, 剩余={remaining}min)')
except Exception as e:
    print(f'ERROR: $file ({e})')
" 2>/dev/null
}

# --- Entry point ---

case "${1:-}" in
  --check)
    shift
    check_task "$@"
    ;;
  file-acquire) shift; acquire_file "$@" ;;
  file-release) shift; release_file "$@" ;;
  file-check)   shift; check_file "$@" ;;
  file-list)
    ls -la "$LOCK_DIR"/*.lock 2>/dev/null || echo "  无活跃共享文件锁"
    ;;
  --help|-h|help)
    head -15 "$0"
    ;;
  *)
    # Backward compat: treat as task lock acquire
    acquire_task "$@"
    ;;
esac
