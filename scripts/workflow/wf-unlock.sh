#!/usr/bin/env bash
bash "$(dirname "$0")/wf-lock.sh" --release 2>/dev/null || {
  SESSION_FILE=".ai/current-session.json"
  LOCK_FILE=".ai/task-lock.json"
  session=$(python3 -c "
import json
try:
    with open('$SESSION_FILE') as f: print(json.load(f).get('session_id',''))
except: print('')
" 2>/dev/null)
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
