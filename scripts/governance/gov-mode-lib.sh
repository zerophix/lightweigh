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
#   gov_mode_read            # → 输出当前模式 (如 explorer, 无验证)
#   gov_mode_get             # → 输出当前模式 (带 HMAC + 过期验证)
#   gov_mode_read_expires    # → 输出过期 epoch (0 = 永不)
#   gov_mode_read_session    # → 输出 session_id
#   gov_mode_is_valid        # → 返回 0/1 (HMAC + 过期)
#   gov_mode_is_explore      # → 返回 0/1 (explore 或 docs)
#   gov_mode_write <mode> <ttl_seconds> [reason]
#   gov_mode_expire          # → 删除模式文件

set -euo pipefail

# HMAC 密钥: 从 git toplevel 路径派生 (确定性, 无需外部密钥)
GOV_MODE_HMAC_KEY="${GOV_MODE_HMAC_KEY:-$(git rev-parse --show-toplevel 2>/dev/null | sha256sum | cut -d' ' -f1)}"
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

# 内部: HMAC 计算
_gov_mode_hmac() {
    local payload="$1"
    printf '%s' "$payload" | openssl dgst -sha256 -hmac "${GOV_MODE_HMAC_KEY}" | cut -d' ' -f2
}

# 内部: 验证 HMAC (计算并比较)
_gov_mode_verify_hmac() {
    local f="$1"
    local hmac_expected hmac_calc payload

    # 读取文件中的 hmac 字段
    hmac_expected=$(python3 -c "
import json
try:
    with open('$f') as fh: d=json.load(fh)
    print(d.get('hmac',''))
except: print('')
" 2>/dev/null)
    [ -z "$hmac_expected" ] && return 1

    # 移除 hmac 字段, 构造 payload
    payload=$(python3 -c "
import json
try:
    with open('$f') as fh: d=json.load(fh)
    d.pop('hmac', None)
    print(json.dumps(d, separators=(',', ':')))
except: print('')
" 2>/dev/null)
    [ -z "$payload" ] && return 1

    hmac_calc="$(_gov_mode_hmac "$payload")"
    [ "$hmac_calc" = "$hmac_expected" ]
}

# 公共 API: 检查模式文件是否有效 (HMAC + 过期)
gov_mode_is_valid() {
    local f; f="$(gov_mode_file)"
    [ -f "$f" ] || return 1

    # HMAC 验证
    _gov_mode_verify_hmac "$f" || return 1

    # 过期检查
    local expires_epoch
    expires_epoch=$(python3 -c "
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
" 2>/dev/null)

    if [ -n "$expires_epoch" ] && [ "$expires_epoch" -gt 0 ]; then
        [ "$(date +%s)" -ge "$expires_epoch" ] && return 1
    fi

    return 0
}

# 公共 API: 读取当前模式 (带 HMAC + 过期验证)
# 输出: explore|docs|test|code|gov|dev
gov_mode_get() {
    if gov_mode_is_valid; then
        local f; f="$(gov_mode_file)"
        python3 -c "
import json
try:
    with open('$f') as fh: d=json.load(fh)
    print(d.get('mode','dev'))
except: print('dev')
" 2>/dev/null
    else
        echo "dev"
        return 1
    fi
}

# 公共 API: 读取当前模式 (不验证直接读)
gov_mode_read() {
    local f; f="$(gov_mode_file)"
    [ -f "$f" ] || { echo "dev"; return 1; }
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
    [ -f "$f" ] || { echo 0; return 1; }
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
    [ -f "$f" ] || { echo ""; return 1; }
    python3 -c "
import json
try:
    with open('$f') as fh: d=json.load(fh)
    print(d.get('session_id',''))
except: print('')
" 2>/dev/null
}

# 内部: 检查是否为 explore 或 docs 模式
gov_mode_is_explore() {
    local mode; mode=$(gov_mode_get 2>/dev/null || echo "dev")
    [ "$mode" = "explore" ] || [ "$mode" = "docs" ]
}

gov_mode_is_explore_or_docs() {
    local mode; mode=$(gov_mode_get 2>/dev/null || echo "dev")
    [ "$mode" = "explore" ] || [ "$mode" = "docs" ]
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

# 供 source 使用: 不执行 main
if [ "${BASH_SOURCE[0]:-}" = "${0:-}" ]; then
    case "${1:-}" in
        read)   gov_mode_read ;;
        get)    gov_mode_get ;;
        expires) gov_mode_read_expires ;;
        session) gov_mode_read_session ;;
        valid)  gov_mode_is_valid && echo "valid" || echo "invalid" ;;
        write)
            shift
            gov_mode_write "$@"
            ;;
        expire) gov_mode_expire ;;
        *)
            echo "Usage: $0 {read|get|expires|session|valid|write <mode> <ttl> [reason]|expire}"
            exit 1
            ;;
    esac
fi
