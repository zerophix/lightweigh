#!/usr/bin/env bash
# ============================================================
# scripts/governance/gov-mode-lib.sh
# AI 工作模式库：读取/验证/创建 .ai/current-mode.json
# 支持 HMAC 签名 + 过期检查 + 模式感知治理
#
# 文件格式 (.ai/current-mode.json):
# {
#   "mode": "explore|docs|test|code|gov|dev",
#   "session_id": "ses-xxx",
#   "set_at": "2026-10-28T00:00:00Z",
#   "expires_at": "2026-10-28T02:00:00Z",  # ISO8601, 可选
#   "reason": "mode switched via wf-mode.sh",
#   "hmac": "sha256=..."                     # HMAC-SHA256(payload, key)
# }
#
# 用法:
#   source scripts/governance/gov-mode-lib.sh
#   gov_mode_validate   # → 输出 JSON: {"mode":"...","valid":true,"expired":false,"session":"...","reason":"..."}
#   gov_mode_get        # → 输出当前模式 (valid && !expired else "dev")
#   gov_mode_warn       # → 显示过期/HMAC 警告 (to stderr)
#   gov_mode_read       # → 输出当前模式 (无验证, 直接读)
#   gov_mode_read_expires   # → 输出过期 epoch (0 = 永不)
#   gov_mode_read_session   # → 输出 session_id
#   gov_mode_write <mode> <ttl_seconds> [reason]
#   gov_mode_expire     # → 删除模式文件
# ============================================================

# Source 安全: 不激活 set -e (防止 kill 调用者的 errexit)
# 库自身只需 -u + pipefail
__gov_mode_save_opts="$(set +o 2>/dev/null; echo 'set -uo pipefail')"
eval "$__gov_mode_save_opts"

# HMAC 密钥: 从 git toplevel 路径派生 (确定性, 无需外部密钥)
# export 确保子进程 (bash -c) 可以读取
export GOV_MODE_HMAC_KEY="${GOV_MODE_HMAC_KEY:-$(git rev-parse --show-toplevel 2>/dev/null | sha256sum | cut -d' ' -f1)}"
GOV_MODE_MAX_SECS=$(( 8 * 3600 ))  # 8 小时上限

# 内部: 仓库根目录
_gov_mode_root() {
    git rev-parse --show-toplevel 2>/dev/null || pwd
}

# 内部: 模式文件路径
gov_mode_file() {
    local root; root="$(_gov_mode_root)"
    echo "$root/.ai/current-mode.json"
}

# 内部: HMAC 计算 (供 gov_mode_write 使用)
_gov_mode_hmac() {
    local payload="$1"
    printf '%s' "$payload" | openssl dgst -sha256 -hmac "${GOV_MODE_HMAC_KEY}" | cut -d' ' -f2
}

# ============================================================
# 核心验证函数：返回结构化 JSON，供所有 hook 强制调用
# 输出: {"mode":"explore","valid":true,"expired":false,"session":"ses-xxx","reason":"..."}
# 退出码: 0=valid且未过期, 1=无效/过期/无文件
# ============================================================
gov_mode_validate() {
    local f; f="$(gov_mode_file)"
    if [ ! -f "$f" ]; then
        printf '{"mode":"dev","valid":false,"expired":false,"session":"","reason":"no mode file"}\n'
        return 1
    fi

    python3 -c "
import json, sys, datetime, hmac, hashlib, os

f = '$f'
hmac_key = os.environ.get('GOV_MODE_HMAC_KEY', '')

try:
    with open(f) as fp:
        d = json.load(fp)
except Exception as e:
    print(json.dumps({'mode': 'dev', 'valid': False, 'expired': False, 'session': '', 'reason': 'read error: ' + str(e)}))
    sys.exit(1)

mode = d.get('mode', 'dev')
session = d.get('session_id', '')
reason = d.get('reason', '')
hmac_stored = d.get('hmac', '')

# 重新计算 HMAC（排除 hmac 字段）
payload_dict = {k: v for k, v in d.items() if k != 'hmac'}
payload = json.dumps(payload_dict, separators=(',', ':'))
hmac_calc = hmac.new(hmac_key.encode(), payload.encode(), hashlib.sha256).hexdigest()

if hmac_calc != hmac_stored:
    print(json.dumps({'mode': mode, 'valid': False, 'expired': False, 'session': session, 'reason': 'HMAC mismatch'}))
    sys.exit(1)

# 检查过期
expires_at = d.get('expires_at', '')
expired = False
if expires_at:
    try:
        dt = datetime.datetime.fromisoformat(expires_at.replace('Z', '+00:00'))
        if dt.tzinfo is None:
            dt = dt.replace(tzinfo=datetime.timezone.utc)
        now = datetime.datetime.now(datetime.timezone.utc)
        if now >= dt:
            expired = True
    except:
        expired = True  # 解析失败视为过期

print(json.dumps({
    'mode': mode,
    'valid': True,
    'expired': expired,
    'session': session,
    'reason': reason
}))
sys.exit(0 if not expired else 1)
"
}

# 公共 API: 获取当前模式 (valid=true 且 !expired)
# 输出: explore|docs|test|code|gov|dev
# return 0: 模式有效; return 0: 回退到 dev (永远不 kill set -e)
gov_mode_get() {
    local result
    result="$(gov_mode_validate 2>/dev/null)"
    local mode
    mode="$(printf '%s' "$result" | python3 -c "import json,sys; d=json.load(sys.stdin); print(d.get('mode','dev'))" 2>/dev/null)"
    local valid
    valid="$(printf '%s' "$result" | python3 -c "import json,sys; d=json.load(sys.stdin); print(d.get('valid',False))" 2>/dev/null)"
    local expired
    expired="$(printf '%s' "$result" | python3 -c "import json,sys; d=json.load(sys.stdin); print(d.get('expired',False))" 2>/dev/null)"

    if [ "$valid" = "True" ] && [ "$expired" = "False" ]; then
        printf '%s\n' "$mode"
        return 0
    else
        printf 'dev\n'
        return 0
    fi
}

# 警告模式状态 (供 hook 显示, 不影响控制流)
# 用法: gov_mode_warn
gov_mode_warn() {
    local json
    json="$(gov_mode_validate 2>/dev/null)" || true
    [ -z "$json" ] && return 0
    printf '%s' "$json" | python3 -c "
import json, sys
d = json.load(sys.stdin)
if not d.get('valid', False):
    print('  [WARNING] 模式 HMAC 校验失败 (' + d.get('reason','') + '), 回退到 dev')
elif d.get('expired', False):
    print('  [WARNING] 模式 ' + d.get('mode','') + ' 已过期, 回退到 dev')
" >&2 2>/dev/null || true
}

# 公共 API: 读取模式 (不验证直接读)
gov_mode_read() {
    local f; f="$(gov_mode_file)"
    [ -f "$f" ] || { echo "dev"; return 0; }
    python3 -c "
import json
try:
    with open('$f') as fh: d=json.load(fh)
    print(d.get('mode','dev'))
except: print('dev')
" 2>/dev/null
}

# 公共 API: 读取过期时间 (epoch, 0=永不过期)
gov_mode_read_expires() {
    local f; f="$(gov_mode_file)"
    [ -f "$f" ] || { echo 0; return 0; }
    python3 -c "
import json, datetime
try:
    with open('$f') as fh: d=json.load(fh)
    ts = d.get('expires_at')
    if ts:
        dt = datetime.datetime.fromisoformat(ts.replace('Z','+00:00'))
        if dt.tzinfo is None: dt = dt.replace(tzinfo=datetime.timezone.utc)
        print(int(dt.timestamp()))
    else:
        print(0)
except: print(0)
" 2>/dev/null
}

# 公共 API: 读取 session_id
gov_mode_read_session() {
    local f; f="$(gov_mode_file)"
    [ -f "$f" ] || { echo ""; return 0; }
    python3 -c "
import json
try:
    with open('$f') as fh: d=json.load(fh)
    print(d.get('session_id',''))
except: print('')
" 2>/dev/null
}

# 公共 API: 写入模式文件 (带 HMAC + 过期)
# 用法: gov_mode_write <mode> <ttl_seconds> [reason]
gov_mode_write() {
    local mode="${1:-dev}"
    local ttl="${2:-0}"
    local reason="${3:-}"

    # 验证模式
    case "$mode" in
        explore|docs|test|code|gov|dev) ;;
        *) echo "Invalid mode: $mode" >&2; return 1 ;;
    esac

    local root; root="$(_gov_mode_root)"
    local f="$root/.ai/current-mode.json"
    mkdir -p "$(dirname "$f")"

    local now; now=$(date -u +%s)
    local until_epoch=0
    if [ "$ttl" -gt 0 ]; then
        until=$(( now + ttl ))
        [ $until -gt $(( now + GOV_MODE_MAX_SECS )) ] && until=$(( now + GOV_MODE_MAX_SECS ))
        until_epoch=$until
    fi

    local session_id="${SESSION_ID:-$(date +%s)-$$}"

    # 构造 payload (不含 hmac)
    local payload
    payload=$(python3 -c "
import json, datetime
payload = {
    'mode': '$mode',
    'session_id': '$session_id',
    'set_at': datetime.datetime.utcnow().isoformat() + 'Z',
    'expires_at': (datetime.datetime.utcfromtimestamp($until_epoch).isoformat() + 'Z') if $until_epoch > 0 else '',
    'reason': '$reason'
}
print(json.dumps(payload, separators=(',', ':')))
" 2>/dev/null)

    local hmac; hmac="$(_gov_mode_hmac "$payload")"

    # 写入文件 (payload + hmac)
    python3 -c "
import json
d = json.loads('''$payload''')
d['hmac'] = '$hmac'
print(json.dumps(d, separators=(',', ':')))
" 2>/dev/null > "$f"

    echo "Mode set to $mode (ttl=${ttl}s, expires_epoch=$until_epoch)"
}

# 公共 API: 过期当前模式 (删除文件)
gov_mode_expire() {
    local f; f="$(gov_mode_file)"
    [ -f "$f" ] && rm -f "$f"
    echo "Mode expired"
}

# 公共 API: 检查 explore/docs bypass 记录 (供 merge/autopilot 使用)
# 用法: gov_mode_check_audit <audit_file> <branch>
# 输出: "FORCE_LANE_C" 或 "OK"
gov_mode_check_audit() {
    local audit_file="${1:-}"
    local branch="${2:-}"
    [ -z "$audit_file" ] && { echo "OK"; return 0; }
    [ ! -f "$audit_file" ] && { echo "OK"; return 0; }
    python3 - "$audit_file" "$branch" <<'PYEOF'
import json, sys
audit_file, branch = sys.argv[1], sys.argv[2]
try:
    with open(audit_file) as f:
        entries = [json.loads(l) for l in f if l.strip()]
except:
    print("OK"); sys.exit(0)
for e in entries:
    mode = e.get("mode", "")
    check = e.get("check", "")
    if mode in ("explore", "docs") and check in ("compile_skipped", "bypass_audit"):
        print("FORCE_LANE_C"); sys.exit(0)
print("OK")
PYEOF
}

# 供 source 使用: 不执行 main
if [ "${BASH_SOURCE[0]:-}" = "${0:-}" ]; then
    case "${1:-}" in
        read)    gov_mode_read ;;
        get)     gov_mode_get ;;
        validate) gov_mode_validate ;;
        warn)    gov_mode_warn ;;
        expires) gov_mode_read_expires ;;
        session) gov_mode_read_session ;;
        valid)   gov_mode_validate >/dev/null 2>&1 && echo "valid" || echo "invalid" ;;
        write)
            shift
            gov_mode_write "$@"
            ;;
        expire)  gov_mode_expire ;;
        check-audit) gov_mode_check_audit "$@" ;;
        *)
            echo "Usage: $0 {read|get|validate|warn|check-audit <audit_file> <branch>|expires|session|valid|write <mode> <ttl> [reason]|expire}"
            exit 1
            ;;
    esac
fi
