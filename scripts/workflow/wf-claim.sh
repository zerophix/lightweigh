#!/usr/bin/env bash
# ============================================================
# scripts/workflow/wf-claim.sh
# 任务认领锁 CLI（DEC-064）— 用原子目录锁替代共享 tasks.json 的认领字段
#
# 根因: 认领写进 .ai/tasks.json 是共享可变文件，last-writer-wins，
# 没有互斥，多 AI 家族并行时会重复实现同一任务。
#
# 语义: mkdir .ai/locks/<TASK-ID> 是 POSIX 原子操作 —— 成功即持有锁，
# 失败说明已被他人持有。锁目录内容:
#   owner       持有者 session id（取自 .ai/current-session.json）
#   branch      认领时所在分支
#   created_at  ISO8601 UTC 时间戳
#   note        可选备注
# TTL 默认 24 小时，超时锁由 gc 清理（status 会标注 expired）。
#
# 用法:
#   bash scripts/workflow/wf-claim.sh claim <TASK-ID> [--note "说明"]
#   bash scripts/workflow/wf-claim.sh release <TASK-ID>
#   bash scripts/workflow/wf-claim.sh status [TASK-ID]
#   bash scripts/workflow/wf-claim.sh list
#   bash scripts/workflow/wf-claim.sh gc
#   bash scripts/workflow/wf-claim.sh --check-staged    # pre-commit 用: 有他人持锁则拦
#
# 通用参数: --root <dir> 覆盖仓库根（默认 git rev-parse --show-toplevel，
#           再退回脚本相对路径；供测试与 worktree 使用）
#
# 退出码: claim 成功 0 / 已被他人持有 1；release 非持有者 1；
#         --check-staged 他人持锁 / 原型任务未认领 均拦截 1。
#
# 环境变量:
#   WF_CLAIM_SESSION  覆盖 session id（测试或多会话手工指定）
#   WF_CLAIM_TTL      锁 TTL 秒数（默认 86400）
# ============================================================

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
TTL_SECONDS="${WF_CLAIM_TTL:-86400}"

# ---------- 解析通用参数 --root ----------
ROOT_OVERRIDE=""
POSITIONAL=()
while [ $# -gt 0 ]; do
  case "$1" in
    --root)
      if [ $# -lt 2 ]; then
        echo "错误: --root 需要目录参数" >&2
        exit 2
      fi
      ROOT_OVERRIDE="$2"
      shift 2
      ;;
    --root=*)
      ROOT_OVERRIDE="${1#*=}"
      shift
      ;;
    *)
      POSITIONAL+=("$1")
      shift
      ;;
  esac
done
if [ ${#POSITIONAL[@]} -gt 0 ]; then
  set -- "${POSITIONAL[@]}"
else
  set --
fi

# ---------- 定位仓库根 ----------
if [ -n "$ROOT_OVERRIDE" ]; then
  ROOT="$ROOT_OVERRIDE"
else
  ROOT="$(git rev-parse --show-toplevel 2>/dev/null || true)"
  if [ -z "$ROOT" ]; then
    # 退回脚本相对路径（scripts/workflow -> 仓库根）
    ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
  fi
fi
if [ ! -d "$ROOT" ]; then
  echo "错误: 仓库根不存在: $ROOT" >&2
  exit 2
fi
ROOT="$(cd "$ROOT" && pwd)"

LOCK_ROOT="$ROOT/.ai/locks"
TASKS_FILE="$ROOT/.ai/tasks.json"
SESSION_FILE="$ROOT/.ai/current-session.json"

# ---------- 工具函数 ----------

# 当前 session id: 环境变量 > .ai/current-session.json > unknown-session
ensure_session() {
  # 测试/编排可用 WF_CLAIM_SESSION 显式指定（优先级最高）
  if [ -n "${WF_CLAIM_SESSION:-}" ]; then echo "$WF_CLAIM_SESSION"; return; fi
  # 认领必须在真实会话下发生；否则 owner 会写成 unknown-session，
  # 后续 pre-commit 归属检查会拒绝提交（2026-10-24 三个代理都踩过）。
  local f="$ROOT/.ai/current-session.json"
  local sid=""
  [ -f "$f" ] && sid="$(python3 -c "import json;print(json.load(open('$f')).get('session_id',''))" 2>/dev/null || true)"
  case "$sid" in
    ""|unknown-session|None)
      mkdir -p "$ROOT/.ai"
      sid="ses-$(date '+%Y%m%d-%H%M%S')-auto"
      printf '{"session_id": "%s", "timestamp": %s}\n' "$sid" "$(date +%s)" > "$f"
      echo "  已自动创建会话: ${sid}（建议先跑 ses-session-begin.sh）" >&2
      ;;
  esac
  echo "$sid"
}

current_session() {
  if [ -n "${WF_CLAIM_SESSION:-}" ]; then
    printf '%s' "$WF_CLAIM_SESSION"
    return 0
  fi
  local sid=""
  if [ -f "$SESSION_FILE" ]; then
    sid="$(python3 - "$SESSION_FILE" <<'PY' 2>/dev/null
import json, sys
try:
    with open(sys.argv[1], encoding='utf-8') as f:
        print(json.load(f).get('session_id', ''))
except Exception:
    print('')
PY
)"
  fi
  if [ -z "$sid" ]; then
    sid="unknown-session"
  fi
  printf '%s' "$sid"
}

current_branch() {
  local br=""
  br="$(git -C "$ROOT" rev-parse --abbrev-ref HEAD 2>/dev/null || true)"
  if [ -z "$br" ]; then
    br="unknown"
  fi
  printf '%s' "$br"
}

now_utc() {
  date -u +%Y-%m-%dT%H:%M:%SZ 2>/dev/null || echo "unknown"
}

# 校验任务 ID 存在于 .ai/tasks.json（防打错号）
task_exists() {
  local tid="$1"
  if [ ! -f "$TASKS_FILE" ]; then
    return 1
  fi
  python3 - "$TASKS_FILE" "$tid" <<'PY' >/dev/null 2>&1
import json, sys
with open(sys.argv[1], encoding='utf-8') as f:
    data = json.load(f)
tid = sys.argv[2]
for t in data.get('tasks', []):
    if t.get('id') == tid:
        sys.exit(0)
sys.exit(1)
PY
}

# 读锁字段: 字段缺失时短暂重试（赢家刚 mkdir、元数据还没落盘的窗口）
read_lock_field() {
  local lock_dir="$1"
  local field="$2"
  python3 - "$lock_dir" "$field" <<'PY'
import os, sys, time
lock_dir, field = sys.argv[1], sys.argv[2]
path = os.path.join(lock_dir, field)
for _ in range(20):
    try:
        with open(path, encoding='utf-8') as f:
            print(f.read().strip())
        sys.exit(0)
    except FileNotFoundError:
        # owner 是锁的必备字段: 缺失可能只是写入竞态，短暂等待
        if field == 'owner' and os.path.isdir(lock_dir):
            time.sleep(0.05)
            continue
        sys.exit(0)
    except Exception:
        sys.exit(0)
sys.exit(0)
PY
}

require_task_id() {
  local tid="${1:-}"
  if [ -z "$tid" ]; then
    echo "用法: wf-claim.sh claim|release|status <TASK-ID>" >&2
    return 2
  fi
  return 0
}

# ---------- 子命令 ----------

cmd_claim() {
  local tid="${1:-}"
  shift || true
  local note=""
  while [ $# -gt 0 ]; do
    case "$1" in
      --note)
        note="${2:-}"
        shift 2 || shift
        ;;
      --note=*)
        note="${1#*=}"
        shift
        ;;
      *)
        echo "未知参数: $1" >&2
        return 2
        ;;
    esac
  done

  require_task_id "$tid" || return 2

  if ! task_exists "$tid"; then
    echo "拒绝: 任务 $tid 不在 .ai/tasks.json 中（请核对任务号）" >&2
    return 3
  fi

  local session branch created lock_dir
  session="$(ensure_session)"
  branch="$(current_branch)"
  created="$(now_utc)"
  lock_dir="$LOCK_ROOT/$tid"

  mkdir -p "$LOCK_ROOT" 2>/dev/null || true

  # 原子抢锁: mkdir 是 POSIX 原子操作
  if mkdir "$lock_dir" 2>/dev/null; then
    # 先写临时文件再 rename，避免读者看到半截内容
    printf '%s\n' "$session" > "$lock_dir/.owner.tmp" && mv "$lock_dir/.owner.tmp" "$lock_dir/owner"
    printf '%s\n' "$branch" > "$lock_dir/.branch.tmp" && mv "$lock_dir/.branch.tmp" "$lock_dir/branch"
    printf '%s\n' "$created" > "$lock_dir/.created_at.tmp" && mv "$lock_dir/.created_at.tmp" "$lock_dir/created_at"
    if [ -n "$note" ]; then
      printf '%s\n' "$note" > "$lock_dir/.note.tmp" && mv "$lock_dir/.note.tmp" "$lock_dir/note"
    fi
    echo "认领成功: $tid"
    echo "  持有者: $session"
    echo "  分支:   $branch"
    echo "  时间:   $created"
    echo "  锁目录: $lock_dir"
    return 0
  fi

  # 抢锁失败: 打印现有持有者信息
  local owner their_branch their_created
  owner="$(read_lock_field "$lock_dir" owner)"
  their_branch="$(read_lock_field "$lock_dir" branch)"
  their_created="$(read_lock_field "$lock_dir" created_at)"
  [ -z "$owner" ] && owner="(写入中)"
  [ -z "$their_branch" ] && their_branch="(未知)"
  [ -z "$their_created" ] && their_created="(未知)"

  if [ "$owner" = "$session" ]; then
    echo "你已持有该锁: ${tid}（${their_created}，分支 ${their_branch}）"
  else
    echo "认领失败: 任务 $tid 已被持有" >&2
    echo "  持有者: $owner" >&2
    echo "  分支:   $their_branch" >&2
    echo "  时间:   $their_created" >&2
  fi
  echo "  查看: bash scripts/workflow/wf-claim.sh status $tid" >&2
  if is_expired "$lock_dir"; then
    echo "  提示: 该锁已超过 TTL，可先运行 bash scripts/workflow/wf-claim.sh gc 清理" >&2
  fi
  return 1
}

is_expired() {
  local lock_dir="$1"
  python3 - "$lock_dir" "$TTL_SECONDS" <<'PY'
import datetime, os, sys
lock_dir, ttl = sys.argv[1], int(sys.argv[2])
try:
    with open(os.path.join(lock_dir, 'created_at'), encoding='utf-8') as f:
        raw = f.read().strip()
    ts = datetime.datetime.strptime(raw, '%Y-%m-%dT%H:%M:%SZ').replace(tzinfo=datetime.timezone.utc)
except Exception:
    sys.exit(1)
age = (datetime.datetime.now(datetime.timezone.utc) - ts).total_seconds()
sys.exit(0 if age > ttl else 1)
PY
}

cmd_release() {
  local tid="${1:-}"
  require_task_id "$tid" || return 2
  local lock_dir="$LOCK_ROOT/$tid"

  if [ ! -d "$lock_dir" ]; then
    echo "未持有: 任务 $tid 没有锁" >&2
    return 1
  fi

  local session owner
  session="$(current_session)"
  owner="$(read_lock_field "$lock_dir" owner)"
  if [ "$owner" != "$session" ]; then
    echo "拒绝释放: 任务 $tid 的锁属于 ${owner}（当前会话 ${session}）" >&2
    return 1
  fi

  rm -rf "$lock_dir"
  echo "已释放: ${tid}（持有者 ${session}）"
  return 0
}

cmd_status() {
  local tid="${1:-}"
  mkdir -p "$LOCK_ROOT" 2>/dev/null || true
  python3 - "$LOCK_ROOT" "$TTL_SECONDS" "$tid" <<'PY'
import datetime, os, sys

lock_root, ttl, only = sys.argv[1], int(sys.argv[2]), sys.argv[3]
now = datetime.datetime.now(datetime.timezone.utc)


def read(path):
    try:
        with open(path, encoding='utf-8') as f:
            return f.read().strip()
    except Exception:
        return ''


def lock_info(name):
    d = os.path.join(lock_root, name)
    if not os.path.isdir(d):
        return None
    raw = read(os.path.join(d, 'created_at'))
    expired = False
    age_txt = '?'
    try:
        ts = datetime.datetime.strptime(raw, '%Y-%m-%dT%H:%M:%SZ').replace(tzinfo=datetime.timezone.utc)
        secs = (now - ts).total_seconds()
        expired = secs > ttl
        hours = secs / 3600.0
        age_txt = '{:.1f}h'.format(hours)
    except Exception:
        pass
    return {
        'id': name,
        'owner': read(os.path.join(d, 'owner')) or '(写入中)',
        'branch': read(os.path.join(d, 'branch')) or '(未知)',
        'created_at': raw or '(未知)',
        'note': read(os.path.join(d, 'note')),
        'age': age_txt,
        'expired': expired,
    }


def fmt(info):
    state = 'EXPIRED' if info['expired'] else 'ok'
    line = '  {}  @{}  branch={}  {}  age={}  [{}]'.format(
        info['id'], info['owner'], info['branch'], info['created_at'], info['age'], state)
    if info['note']:
        line += '  note={}'.format(info['note'])
    return line


if only:
    info = lock_info(only)
    if info is None:
        print('任务 {}: 无锁'.format(only))
    else:
        print('任务 {}: 已锁定'.format(only))
        print(fmt(info))
    sys.exit(0)

names = sorted(n for n in os.listdir(lock_root)
               if os.path.isdir(os.path.join(lock_root, n)) and not n.startswith('.'))
if not names:
    print('当前无任务锁')
    sys.exit(0)

print('任务锁（TTL {}h）:'.format(ttl // 3600))
expired_count = 0
for n in names:
    info = lock_info(n)
    if info is None:
        continue
    if info['expired']:
        expired_count += 1
    print(fmt(info))
if expired_count:
    print('  提示: {} 个锁已过期，运行 gc 清理'.format(expired_count))
PY
}

cmd_list() {
  mkdir -p "$LOCK_ROOT" 2>/dev/null || true
  python3 - "$LOCK_ROOT" "$TTL_SECONDS" <<'PY'
import datetime, os, sys

lock_root, ttl = sys.argv[1], int(sys.argv[2])
now = datetime.datetime.now(datetime.timezone.utc)


def read(path):
    try:
        with open(path, encoding='utf-8') as f:
            return f.read().strip()
    except Exception:
        return ''


names = sorted(n for n in os.listdir(lock_root)
               if os.path.isdir(os.path.join(lock_root, n)) and not n.startswith('.'))
if not names:
    print('当前无任务锁')
    sys.exit(0)

for n in names:
    d = os.path.join(lock_root, n)
    owner = read(os.path.join(d, 'owner')) or '(写入中)'
    branch = read(os.path.join(d, 'branch')) or '(未知)'
    created = read(os.path.join(d, 'created_at')) or '(未知)'
    mark = ''
    try:
        ts = datetime.datetime.strptime(created, '%Y-%m-%dT%H:%M:%SZ').replace(tzinfo=datetime.timezone.utc)
        if (now - ts).total_seconds() > ttl:
            mark = '  [expired]'
    except Exception:
        pass
    print('  {}  {}  {}  {}{}'.format(n, owner, branch, created, mark))
PY
}

cmd_gc() {
  mkdir -p "$LOCK_ROOT" 2>/dev/null || true
  local removed
  removed="$(python3 - "$LOCK_ROOT" "$TTL_SECONDS" <<'PY'
import datetime, os, shutil, sys

lock_root, ttl = sys.argv[1], int(sys.argv[2])
now = datetime.datetime.now(datetime.timezone.utc)


def read(path):
    try:
        with open(path, encoding='utf-8') as f:
            return f.read().strip()
    except Exception:
        return ''


removed = 0
for n in sorted(os.listdir(lock_root)):
    d = os.path.join(lock_root, n)
    if not os.path.isdir(d) or n.startswith('.'):
        continue
    raw = read(os.path.join(d, 'created_at'))
    try:
        ts = datetime.datetime.strptime(raw, '%Y-%m-%dT%H:%M:%SZ').replace(tzinfo=datetime.timezone.utc)
    except Exception:
        # 时间戳缺失/损坏的锁不擅自删除，留给人判断
        continue
    if (now - ts).total_seconds() <= ttl:
        continue
    owner = read(os.path.join(d, 'owner')) or '(未知)'
    shutil.rmtree(d)
    print('  清理: {}  @{}  {}'.format(n, owner, raw))
    removed += 1

if removed == 0:
    print('  无过期锁')
sys.exit(0)
PY
)"
  echo "过期锁清理（TTL $((TTL_SECONDS / 3600))h）:"
  echo "$removed"
  return 0
}

# pre-commit 接口: 暂存文件命中某任务 target_files，且该任务锁被他人持有时拦截
cmd_check_staged() {
  # 合并提交（冲突解决/同步 master）属于集成动作，不是新开工，跳过认领检查
  if [ -f "$ROOT/.git/MERGE_HEAD" ] || git -C "$ROOT" rev-parse -q --verify MERGE_HEAD >/dev/null 2>&1; then
    return 0
  fi
  local staged
  staged="$(git -C "$ROOT" diff --cached --name-only --diff-filter=ACMR 2>/dev/null || true)"
  if [ -z "$staged" ]; then
    return 0
  fi

  local session
  session="$(current_session)"

  # 注意: 暂存清单经环境变量传入 —— heredoc 已占用 stdin，不能再 pipe
  STAGED_FILES="$staged" SESSION_ID="$session" LOCK_ROOT="$LOCK_ROOT" TASKS_FILE="$TASKS_FILE" \
    python3 - <<'PY'
import json, os, sys

session = os.environ.get('SESSION_ID', '')
lock_root = os.environ['LOCK_ROOT']
tasks_file = os.environ['TASKS_FILE']

files = [ln.strip() for ln in os.environ.get('STAGED_FILES', '').splitlines() if ln.strip()]
if not files:
    sys.exit(0)

try:
    with open(tasks_file, encoding='utf-8') as f:
        tasks = json.load(f).get('tasks', [])
except Exception:
    sys.exit(0)


def owner_of(tid):
    path = os.path.join(lock_root, tid, 'owner')
    try:
        with open(path, encoding='utf-8') as f:
            return f.read().strip()
    except Exception:
        return ''


blocked = []        # (tid, owner, files, kind)
for task in tasks:
    targets = task.get('target_files') or []
    if not targets:
        continue
    tid = task.get('id', '')
    owner = owner_of(tid)
    hit = [f for f in files if f in targets]
    if not hit:
        continue
    if owner and owner != session:
        blocked.append((tid, owner, hit, 'other'))
    elif not owner and task.get('prototype_ref'):
        # DEC-065 收紧: 原型映射任务（有 prototype_ref）必须先在锁内再动代码,
        # 否则多 AI 家族会重复实现（2026-10-24 实测 4 个 PR 废弃）。
        blocked.append((tid, '', hit, 'unclaimed'))

if not blocked:
    sys.exit(0)

print('[拒绝] 任务认领检查未通过（DEC-064/065 任务锁）:')
for tid, owner, hit, kind in blocked:
    if kind == 'other':
        print('   {} 已被 {} 持有:'.format(tid, owner))
    else:
        print('   {} 尚未认领（原型映射任务必须先认领）:'.format(tid))
    for f in hit:
        print('     - {}'.format(f))
print('   查看锁: bash scripts/workflow/wf-claim.sh status <TASK-ID>')
print('   认领锁: bash scripts/workflow/wf-claim.sh claim <TASK-ID>')
print('   （确认是并行重复实现时请放弃本次提交，避免撞车）')
sys.exit(1)
PY
}

usage() {
  echo "用法: bash scripts/workflow/wf-claim.sh <命令> [参数] [--root <dir>]"
  echo ""
  echo "命令:"
  echo "  claim <TASK-ID> [--note 说明]  原子抢锁（成功 0，已被持有 1）"
  echo "  release <TASK-ID>              释放锁（仅持有者）"
  echo "  status [TASK-ID]               查看锁与过期状态"
  echo "  list                           简短列出所有锁"
  echo "  gc                             清理过期锁（TTL ${TTL_SECONDS}s）"
  echo "  --check-staged                 pre-commit 用: 他人持锁且命中 target_files 则拦"
}

CMD="${1:-}"
if [ -n "$CMD" ]; then
  shift
fi

case "$CMD" in
  claim) cmd_claim "$@" ;;
  release) cmd_release "$@" ;;
  status) cmd_status "$@" ;;
  list) cmd_list "$@" ;;
  gc) cmd_gc "$@" ;;
  --check-staged) cmd_check_staged "$@" ;;
  ""|-h|--help|help) usage; exit 0 ;;
  *)
    echo "未知命令: $CMD" >&2
    usage >&2
    exit 2
    ;;
esac
