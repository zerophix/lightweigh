#!/usr/bin/env bash
# 任务互斥锁 — 多 AI 并发开发防冲突
# 用法: wf-lock.sh <task-id> [ttl-minutes]
#       wf-unlock.sh
#       wf-lock.sh --check <task-id>

LOCK_FILE=".ai/task-lock.json"
SESSION_FILE=".ai/current-session.json"
DEFAULT_TTL=30  # 分钟

get_session() {
  python3 -c "
import json
try:
    with open('$SESSION_FILE') as f: print(json.load(f).get('session_id',''))
except: print('')
" 2>/dev/null
}

acquire() {
  local task_id="$1"
  local ttl="${2:-$DEFAULT_TTL}"
  local session=$(get_session)
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

# Clean expired locks
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

release() {
  local session=$(get_session)
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
print(f"UNLOCKED all locks held by {session}")
PYEOF
}

check() {
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

case "$1" in
  --check) check "$2" ;;
  *) acquire "$@" ;;
esac