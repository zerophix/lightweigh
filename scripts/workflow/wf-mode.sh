#!/usr/bin/env bash
# wf-mode.sh — AI 工作模式管理 (Bash 3.2 compatible)
# 用法:
#   bash scripts/workflow/wf-mode.sh status
#   bash scripts/workflow/wf-mode.sh switch <explore|dev|deliver|audit> [--duration 2h]
#   bash scripts/workflow/wf-mode.sh expire

set -euo pipefail

ROOT="$(git rev-parse --show-toplevel 2>/dev/null || true)"
if [ -z "$ROOT" ]; then
    ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
fi
cd "$ROOT"

CACHE_DIR="$ROOT/.cache/modes"
MODE_FILE="$CACHE_DIR/current.json"
mkdir -p "$CACHE_DIR"

VALID_MODES="explore dev deliver audit"

# 获取模式默认 TTL (小时)
get_default_ttl() {
    case "$1" in
        explore) echo 2 ;;
        dev) echo 8 ;;
        deliver) echo 1 ;;
        audit) echo 0 ;;  # 0 = 永不过期
        *) echo "" ;;
    esac
}

# 获取模式描述
get_mode_desc() {
    case "$1" in
        explore) echo "跳过编译 + 允许 bypass (快速迭代)" ;;
        dev) echo "标准 12 检查 (默认)" ;;
        deliver) echo "强制 DoD + local-ci" ;;
        audit) echo "禁止 bypass + full pytest" ;;
        *) echo "未知模式" ;;
    esac
}

# 检查模式是否有效
is_valid_mode() {
    local mode="$1"
    for m in $VALID_MODES; do
        [ "$m" = "$mode" ] && return 0
    done
    return 1
}

# 读取当前模式 (输出: state mode remaining_seconds)
get_current_mode() {
    if [ ! -f "$MODE_FILE" ]; then
        echo "none dev 0"
        return 0
    fi

    python3 -c "
import json, sys
from datetime import datetime, timezone
try:
    with open('$MODE_FILE') as f:
        d = json.load(f)
    mode = d.get('mode', 'dev')
    expires = d.get('expires_at')
    if expires:
        dt = datetime.fromisoformat(expires.replace('Z', '+00:00'))
        now = datetime.now(timezone.utc)
        diff = int((dt - now).total_seconds())
        if diff <= 0:
            print(f'expired {mode} 0')
        else:
            print(f'active {mode} {diff}')
    else:
        print(f'active {mode} -1')
except:
    print('invalid dev 0')
"
}

cmd_status() {
    if [ ! -f "$MODE_FILE" ]; then
        echo "当前模式: dev (默认)"
        echo "过期时间: 永不"
        echo "影响: $(get_mode_desc dev)"
        return 0
    fi

    local result
    result=$(get_current_mode)
    local state mode remaining
    read -r state mode remaining <<< "$result"

    if [ "$state" = "expired" ]; then
        echo "当前模式: $mode (已过期, 自动回退到 dev)"
        echo "影响: $(get_mode_desc dev)"
        return 0
    fi

    if [ "$remaining" = "-1" ]; then
        echo "当前模式: $mode (永不过期)"
    else
        local hours=$((remaining / 3600))
        local mins=$(((remaining % 3600) / 60))
        echo "当前模式: $mode (剩余: ${hours}h${mins}m)"
    fi

    echo "影响: $(get_mode_desc "$mode")"
}

cmd_switch() {
    local new_mode="${1:-}"
    [ -n "$new_mode" ] || { echo "用法: $0 switch <mode>"; exit 1; }

    is_valid_mode "$new_mode" || {
        echo "❌ 无效模式: $new_mode"
        echo "可用: $VALID_MODES"
        exit 1
    }

    # 检查是否在 master 分支
    local branch
    branch="$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo 'unknown')"
    case "$branch" in
        master|main)
            echo "❌ 禁止在 master/main 分支切换模式"
            exit 1
            ;;
    esac

    # 解析 --duration 参数
    local ttl
    ttl=$(get_default_ttl "$new_mode")
    shift
    while [ $# -gt 0 ]; do
        case "$1" in
            --duration)
                # 提取小时数: 2h -> 2
                local dur="$2"
                ttl="${dur%h}"
                shift 2
                ;;
            *)
                shift
                ;;
        esac
    done

    # 计算过期时间
    local expires_json="null"
    if [ "$ttl" != "0" ]; then
        expires_json=$(python3 -c "
from datetime import datetime, timedelta, timezone
d = datetime.now(timezone.utc) + timedelta(hours=$ttl)
print('\"%s\"' % d.strftime('%Y-%m-%dT%H:%M:%S.000000Z'))
")
    fi

    # 写入模式文件
    python3 -c "
import json
from datetime import datetime, timezone
mode_data = {
    'mode': '$new_mode',
    'expires_at': $expires_json,
    'switched_by': '$USER',
    'switched_at': datetime.now(timezone.utc).strftime('%Y-%m-%dT%H:%M:%S.000000Z')
}
with open('$MODE_FILE', 'w') as f:
    json.dump(mode_data, f, indent=2, ensure_ascii=False)
"

    echo "✅ 已切换到 $new_mode 模式"
    if [ "$ttl" = "0" ]; then
        echo "   过期: 永不过期"
    else
        echo "   过期: ${ttl}小时后自动回退到 dev"
    fi
    echo "   影响: $(get_mode_desc "$new_mode")"
    echo "GOV_MODE=$new_mode"
}

cmd_expire() {
    if [ -f "$MODE_FILE" ]; then
        local result
        result=$(get_current_mode)
        local old_mode
        old_mode=$(echo "$result" | awk '{print $2}')

        python3 -c "
import json
with open('$MODE_FILE') as f:
    d = json.load(f)
d['mode'] = 'dev'
d['expires_at'] = None
with open('$MODE_FILE', 'w') as f:
    json.dump(d, f, indent=2, ensure_ascii=False)
" 2>/dev/null || true
        echo "✅ 模式已过期: $old_mode → dev"
    else
        echo "当前已是 dev 模式"
    fi
}

# 入口
CMD="${1:-status}"
shift 1 2>/dev/null || true

case "$CMD" in
    status) cmd_status ;;
    switch) cmd_switch "${1:-}" ;;
    expire) cmd_expire ;;
    *)
        echo "用法: $0 {status|switch <mode> [--duration Nh]|expire}"
        echo "模式: $VALID_MODES"
        exit 1
        ;;
esac
