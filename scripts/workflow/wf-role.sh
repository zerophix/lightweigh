#!/usr/bin/env bash
# ============================================================
# scripts/workflow/wf-role.sh
# AI 角色（DEC-075）：一个会话同一时刻只持有一个角色。
#
# 角色解决三件事，且只解决这三件事：
#   1. 写入边界 —— 越出 allow/deny 的角色范围，提交前拒绝；
#   2. 评审独立 —— review 角色只读，且不能由持任务锁的会话认领；
#   3. 产物契约 —— 每个角色声明 must_produce，交接时人工/脚本核对。
# 门禁深度不在这里决定：那是 gov-profile.sh（画像）的职责。
#
# 用法:
#   wf-role.sh claim <dev|gov|docs|review> [--task T-xxx] [--ttl 28800]
#   wf-role.sh current            # 输出当前角色（无则空）
#   wf-role.sh show               # 角色 + 允许/禁止范围 + 必须产物
#   wf-role.sh release            # 释放当前角色
#   wf-role.sh list               # 列出全部角色
#   wf-role.sh check-scope [--staged] [--files a b c]
#   wf-role.sh verify-review <报告文件>
#
# 状态位置: .ai/roles/<session>.json（本机状态，不入库）
# 覆盖根目录（测试用）: WF_ROLE_ROOT
# 指定会话（测试/多会话）: WF_CLAIM_SESSION
#
# 退出码: 0 成功 / 1 拒绝或越界 / 2 用法错误
# ============================================================
set -uo pipefail

DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT="${WF_ROLE_ROOT:-$(cd "$DIR/../.." && pwd)}"
cd "$ROOT" || exit 2

ROLES_FILE="$ROOT/.ai/roles.json"
ROLES_DIR="$ROOT/.ai/roles"
LOCKS_DIR="$ROOT/.ai/locks"
SESSION_FILE="$ROOT/.ai/current-session.json"
DEFAULT_TTL=28800

die() { echo "错误: $*" >&2; exit 2; }
usage() { sed -n '2,30p' "$0"; exit 0; }

[ -f "$ROLES_FILE" ] || die "缺少角色定义: $ROLES_FILE"

session_id() {
  if [ -n "${WF_CLAIM_SESSION:-}" ]; then printf '%s' "$WF_CLAIM_SESSION"; return; fi
  local sid=""
  [ -f "$SESSION_FILE" ] && sid="$(python3 -c "import json;print(json.load(open('$SESSION_FILE')).get('session_id',''))" 2>/dev/null || true)"
  [ -z "$sid" ] && sid="ses-local-$(id -u)"
  printf '%s' "$sid"
}

role_file() { printf '%s/%s.json' "$ROLES_DIR" "$(session_id)"; }

# 读取角色定义里的字段（点号路径，如 roles.dev.profile）
role_field() {
  ROLES_FILE="$ROLES_FILE" ROLE="$1" FIELD="$2" python3 - <<'PY'
import json, os
data = json.load(open(os.environ["ROLES_FILE"], encoding="utf-8"))
role = data.get("roles", {}).get(os.environ["ROLE"], {})
val = role
for part in os.environ["FIELD"].split("."):
    if isinstance(val, dict):
        val = val.get(part)
    else:
        val = None
        break
if isinstance(val, (list, dict)):
    print(json.dumps(val, ensure_ascii=False))
elif val is None:
    print("")
else:
    print(val)
PY
}

role_exists() {
  ROLES_FILE="$ROLES_FILE" ROLE="$1" python3 - <<'PY'
import json, os, sys
data = json.load(open(os.environ["ROLES_FILE"], encoding="utf-8"))
sys.exit(0 if os.environ["ROLE"] in data.get("roles", {}) else 1)
PY
}

current_role() {
  local f
  f="$(role_file)"
  [ -f "$f" ] || return 0
  ROLE_FILE="$f" python3 - <<'PY'
import datetime, json, os, sys
try:
    data = json.load(open(os.environ["ROLE_FILE"], encoding="utf-8"))
except Exception:
    sys.exit(0)
exp = data.get("expires_at", "")
if exp:
    try:
        if datetime.datetime.fromisoformat(exp.replace("Z", "+00:00")) < datetime.datetime.now(datetime.timezone.utc):
            sys.exit(0)
    except Exception:
        pass
print(data.get("role", ""))
PY
}

holding_task_lock() {
  local sid f
  sid="$(session_id)"
  [ -d "$LOCKS_DIR" ] || return 1
  for f in "$LOCKS_DIR"/*/owner; do
    [ -f "$f" ] || continue
    [ "$(cat "$f" 2>/dev/null || true)" = "$sid" ] && return 0
  done
  return 1
}

cmd_claim() {
  local role="${1:-}" task="" ttl="$DEFAULT_TTL"
  shift || true
  while [ $# -gt 0 ]; do
    case "$1" in
      --task) task="${2:-}"; shift 2 ;;
      --ttl) ttl="${2:-}"; shift 2 ;;
      *) die "未知参数: $1" ;;
    esac
  done
  [ -n "$role" ] || die "用法: wf-role.sh claim <role> [--task T-xxx]"
  role_exists "$role" || die "未知角色: ${role}（可用: $(ROLES_FILE="$ROLES_FILE" python3 -c "import json,os;print(','.join(json.load(open(os.environ['ROLES_FILE']))['roles']))"))"

  if [ "$role" = "review" ] && holding_task_lock; then
    echo "拒绝: review 角色必须由不同会话承担，当前会话仍持有任务锁" >&2
    echo "      处理: 先 release 任务锁，或另开一个会话做评审" >&2
    return 1
  fi

  mkdir -p "$ROLES_DIR"
  local f; f="$(role_file)"
  ROLE_FILE="$f" ROLE="$role" TASK="$task" TTL="$ttl" SID="$(session_id)" python3 - <<'PY'
import datetime, json, os
now = datetime.datetime.now(datetime.timezone.utc)
ttl = int(os.environ["TTL"])
payload = {
    "role": os.environ["ROLE"],
    "session": os.environ["SID"],
    "task": os.environ["TASK"],
    "created_at": now.strftime("%Y-%m-%dT%H:%M:%SZ"),
    "expires_at": (now + datetime.timedelta(seconds=ttl)).strftime("%Y-%m-%dT%H:%M:%SZ"),
}
tmp = os.environ["ROLE_FILE"] + ".tmp"
with open(tmp, "w", encoding="utf-8") as fh:
    json.dump(payload, fh, ensure_ascii=False)
os.replace(tmp, os.environ["ROLE_FILE"])
PY
  echo "角色已绑定: $role"
  echo "  会话: $(session_id)"
  [ -n "$task" ] && echo "  任务: $task"
  echo "  允许: $(role_field "$role" allow)"
  echo "  禁止: $(role_field "$role" deny)"
  echo "  必须产出: $(role_field "$role" must_produce)"
  echo "  有效期: ${ttl}s"
}

cmd_show() {
  local role; role="$(current_role)"
  if [ -z "$role" ]; then echo "当前未声明角色（按画像兜底）"; return 0; fi
  echo "角色: $role"
  echo "  画像: $(role_field "$role" profile)"
  echo "  允许: $(role_field "$role" allow)"
  echo "  禁止: $(role_field "$role" deny)"
  echo "  必须产出: $(role_field "$role" must_produce)"
  echo "  只读: $(role_field "$role" read_only)"
}

cmd_list() {
  ROLES_FILE="$ROLES_FILE" python3 - <<'PY'
import json, os
roles = json.load(open(os.environ["ROLES_FILE"], encoding="utf-8"))["roles"]
for name, spec in roles.items():
    print(f"{name:8s} profile={spec.get('profile','-'):5s} allow={','.join(spec.get('allow', []))}")
PY
}

cmd_check_scope() {
  local mode="staged" files=""
  while [ $# -gt 0 ]; do
    case "$1" in
      --staged) mode="staged"; shift ;;
      --files) shift; files="$*"; mode="files"; break ;;
      *) die "未知参数: $1" ;;
    esac
  done

  local role; role="$(current_role)"
  if [ -z "$role" ]; then
    echo "  [角色] 未声明，跳过角色范围校验（画像仍生效）"
    return 0
  fi

  if [ "$mode" = "staged" ]; then
    files="$(git diff --cached --name-only --diff-filter=ACMR 2>/dev/null || true)"
  fi

  ROLE="$role" FILES="$files" ROLES_FILE="$ROLES_FILE" python3 - <<'PY'
import fnmatch, json, os, sys

def matches(path, pattern):
    if pattern.endswith("/**"):
        return path == pattern[:-3] or path.startswith(pattern[:-3] + "/")
    if "*" in pattern:
        return fnmatch.fnmatch(path, pattern)
    return path == pattern

roles = json.load(open(os.environ["ROLES_FILE"], encoding="utf-8"))["roles"]
role = roles[os.environ["ROLE"]]
allow = role.get("allow", [])
deny = role.get("deny", [])
read_only = bool(role.get("read_only"))
files = [f for f in os.environ.get("FILES", "").split() if f]

bad = []
for f in files:
    ok_allow = any(matches(f, p) for p in allow)
    hit_deny = any(matches(f, p) for p in deny)
    if read_only:
        ok = ok_allow
    else:
        ok = ok_allow and not hit_deny
    if not ok:
        bad.append(f)

if bad:
    print(f"  [越界] 角色 {os.environ['ROLE']} 不允许修改：")
    for f in bad:
        print(f"    - {f}")
    print("  处理: 换一个角色（wf-role.sh claim ...），或把改动拆到对应角色")
    sys.exit(1)
print(f"  [角色] {os.environ['ROLE']} 范围校验通过（{len(files)} 个文件）")
PY
}

cmd_verify_review() {
  local report="${1:-}"
  [ -n "$report" ] || die "用法: wf-role.sh verify-review <报告文件>"
  [ -f "$report" ] || { echo "拒绝: 评审报告不存在: $report" >&2; return 1; }
  local role; role="$(current_role)"
  [ "$role" = "review" ] || { echo "拒绝: 当前角色不是 review（实际: ${role:-未声明}）" >&2; return 1; }
  local sid; sid="$(session_id)"
  if ! grep -qE "^[>* ]*(reviewer_session|reviewer):.*${sid}" "$report" 2>/dev/null; then
    echo "拒绝: 报告未署名当前会话（需包含 reviewer_session: ${sid}）" >&2
    return 1
  fi
  echo "评审报告校验通过: ${report}（reviewer=${sid}）"
}

case "${1:-}" in
  claim) shift; cmd_claim "$@" ;;
  current) current_role ;;
  show) cmd_show ;;
  list) cmd_list ;;
  release) rm -f "$(role_file)"; echo "角色已释放" ;;
  check-scope) shift; cmd_check_scope "$@" ;;
  verify-review) shift; cmd_verify_review "$@" ;;
  -h|--help) usage ;;
  *) usage ;;
esac
