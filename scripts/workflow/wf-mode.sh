#!/usr/bin/env bash
# wf-mode.sh — AI 工作模式管理 (v2: 集成 gov-mode-lib.sh)
# 用法:
#   bash scripts/workflow/wf-mode.sh status
#   bash scripts/workflow/wf-mode.sh switch <explore|docs|test|code|gov|dev> [--duration 2h]
#   bash scripts/workflow/wf-mode.sh expire
#   bash scripts/workflow/wf-mode.sh suspend [2h]   # 暂停门禁（原 gov-mode.sh off 的收编入口）
#   bash scripts/workflow/wf-mode.sh resume         # 恢复门禁
#   bash scripts/workflow/wf-mode.sh post-hook <mode>
#
# 写入 .ai/current-mode.json (HMAC + 过期检查)
# 钩子通过 gov-mode-lib.sh 读取当前模式, 决定跳过哪些检查

set -euo pipefail

ROOT="$(git rev-parse --show-toplevel 2>/dev/null || true)"
if [ -z "${ROOT:-}" ]; then
    ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
fi
cd "$ROOT"

LIB="$ROOT/scripts/governance/gov-mode-lib.sh"
if [ ! -f "$LIB" ]; then
    echo "❌ 找不到 gov-mode-lib.sh: $LIB"
    exit 1
fi
# shellcheck source=/dev/null
. "$LIB"

# Alias mapping: deliver -> code, audit -> gov (backwards compat)
_mode_alias() {
    case "$1" in
        deliver) echo "code" ;;
        audit) echo "gov" ;;
        *) echo "$1" ;;
    esac
}

# 模式描述
get_mode_desc() {
    case "$1" in
        explore) echo "跳过编译 + 允许 bypass (快速迭代)" ;;
        docs) echo "跳过编译 + 仅 docs 校验" ;;
        test) echo "跳过编译 + 运行 pytest (跳过 gradle build)" ;;
        code) echo "标准校验 (默认)" ;;
        gov) echo "严格校验 + full pytest (无 bypass)" ;;
        dev) echo "标准校验 (默认)" ;;
        *) echo "未知模式" ;;
    esac
}

# 默认 TTL (秒)
get_default_ttl() {
    case "$1" in
        explore) echo 7200 ;;   # 2h
        docs) echo 7200 ;;     # 2h
        test) echo 14400 ;;    # 4h
        code|dev) echo 0 ;;    # 永不过期
        gov) echo 0 ;;          # 永不过期
        *) echo "" ;;
    esac
}

cmd_status() {
    local mode; mode=$(gov_mode_get 2>/dev/null) || true
    [ -z "$mode" ] && mode="dev"
    local session; session=$(gov_mode_read_session 2>/dev/null) || true
    [ -z "$session" ] && session=""
    local expires_epoch; expires_epoch=$(gov_mode_read_expires 2>/dev/null) || true
    [ -z "$expires_epoch" ] && expires_epoch=0

    if [ "$mode" = "dev" ] || [ "$mode" = "code" ]; then
        if [ "$expires_epoch" -gt 0 ]; then
            local remaining=$(( expires_epoch - $(date +%s) ))
            if [ "$remaining" -le 0 ]; then
                echo "当前模式: $mode (已过期, 自动回退到 dev)"
                echo "影响: $(get_mode_desc dev)"
                return 0
            fi
            echo "当前模式: $mode (剩余: ${remaining}s)"
            echo "过期: $(date -u -r $expires_epoch '+%Y-%m-%dT%H:%M:%SZ' 2>/dev/null || echo "$expires_epoch")"
        else
            echo "当前模式: $mode (默认)"
            echo "过期: 永不过期"
        fi
        echo "影响: $(get_mode_desc "$mode")"
        if [ -n "$session" ]; then
            echo "会话: $session"
        fi
        return 0
    fi

    if [ "$expires_epoch" -gt 0 ]; then
        local remaining=$(( expires_epoch - $(date +%s) ))
        if [ "$remaining" -le 0 ]; then
            echo "当前模式: $mode (已过期, 自动回退到 dev)"
            echo "影响: $(get_mode_desc dev)"
            return 0
        fi
        echo "当前模式: $mode (剩余: ${remaining}s)"
        echo "过期: $(date -u -r $expires_epoch '+%Y-%m-%dT%H:%M:%SZ' 2>/dev/null || echo "$expires_epoch")"
        if [ -n "$session" ]; then
            echo "会话: $session"
        fi
        echo "影响: $(get_mode_desc "$mode")"
    else
        echo "当前模式: $mode (永不过期)"
        if [ -n "$session" ]; then
            echo "会话: $session"
        fi
        echo "影响: $(get_mode_desc "$mode")"
    fi
}

cmd_switch() {
    local new_mode="${1:-}"
    [ -n "$new_mode" ] || { echo "用法: $0 switch <mode>"; exit 1; }

    # 应用别名
    new_mode=$(_mode_alias "$new_mode")

    # 验证模式
    case "$new_mode" in
        explore|docs|test|code|gov|dev) ;;
        *)
            echo "❌ 无效模式: $new_mode"
            echo "可用: explore docs test code gov dev"
            echo "(别名: deliver→code, audit→gov)"
            exit 1
            ;;
    esac

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
                local dur="$2"
                # 解析: 2h -> 7200, 30m -> 1800, 90s -> 90
                case "$dur" in
                    *h) ttl=$(( ${dur%h} * 3600 )) ;;
                    *m) ttl=$(( ${dur%m} * 60 )) ;;
                    *s) ttl=$(( ${dur%s} )) ;;
                    *)  ttl=$(( dur * 3600 )) ;;
                esac
                shift 2
                ;;
            *) shift ;;
        esac
    done

    # 写入模式文件 (通过 gov-mode-lib.sh, 带 HMAC + 过期)
    # gov_mode_write <mode> <ttl_seconds> [reason]
    local reason="mode switched via wf-mode.sh"
    local output
    output=$(gov_mode_write "$new_mode" "$ttl" "$reason" 2>&1)
    echo "$output"

    # 输出供调用者 eval 的环境变量
    echo "GOV_MODE=$new_mode"
}

cmd_post_hook() {
    local mode="${1:-}"
    [ -n "$mode" ] || mode=$(gov_mode_get 2>/dev/null || echo "dev")

    echo "✅ 模式: $mode"
    echo "   影响: $(get_mode_desc "$mode")"

    # Auto-stage the mode file (DEC-077: audit trail)
    if [ -f "$ROOT/.ai/current-mode.json" ]; then
        git add "$ROOT/.ai/current-mode.json" 2>/dev/null && echo "   📌 已暂存 .ai/current-mode.json"
    fi

    # Show hook behavior prompt
    echo ""
    echo "📋 Hook 行为提示:"
    case "$mode" in
        explore|docs)
            echo "  - pre-commit: 跳过 gradle compile, 记录 audit"
            echo "  - pre-push:   audit-only (GOV_BYPASS_POLICY=block 可阻断)"
            echo "  - local-ci:   Lane A (静态校验, skip gradle)"
            ;;
        test)
            echo "  - local-ci:   skip gradle, 仅 pytest"
            ;;
        code|gov|dev)
            echo "  - 标准 hook 无调整 (full CI)"
            ;;
    esac

    # Show lock status for shared files
    if [ -f "$ROOT/scripts/workflow/wf-lock.sh" ]; then
        local lock_info
        lock_info=$(bash "$ROOT/scripts/workflow/wf-lock.sh" file-list 2>/dev/null || true)
        if [ -n "$lock_info" ]; then
            echo ""
            echo "🔒 当前会话持有锁:"
            echo "$lock_info" | sed 's/^/   /'
        fi
    fi
}

cmd_expire() {
    local mode; mode=$(gov_mode_get 2>/dev/null || echo "dev")
    gov_mode_expire
    echo "✅ 模式已过期: $mode → dev"
    echo "GOV_MODE=dev"
}

# 暂停/恢复门禁：收编 gov-mode.sh off/on，对外只留 wf-mode.sh 一个入口（DEC-083）
cmd_suspend() {
    local dur="${1:-2h}"
    bash "$ROOT/scripts/governance/gov-mode.sh" off "$dur"
    echo "提示: 这是「治理暂停」，不是工作模式；current-mode 未改变。"
    echo "     工作模式请用: bash scripts/workflow/wf-mode.sh status"
}

cmd_resume() {
    bash "$ROOT/scripts/governance/gov-mode.sh" on
}

cmd_help() {
    cat <<'EOF'
wf-mode.sh — AI 工作模式管理

用法:
  bash scripts/workflow/wf-mode.sh status
  bash scripts/workflow/wf-mode.sh switch <mode> [--duration <Nh|Nm|Ns>]
  bash scripts/workflow/wf-mode.sh expire
  bash scripts/workflow/wf-mode.sh suspend [2h]   # 暂停门禁（不是切模式）
  bash scripts/workflow/wf-mode.sh resume         # 恢复门禁

模式:
  explore  跳过编译 + 允许 bypass (快速迭代)    [2h 自动过期]
  docs     跳过编译 + 仅 docs 校验               [2h 自动过期]
  test     跳过编译 + 运行 pytest                 [4h 自动过期]
  code     标准校验 (默认)                        [永不过期]
  gov      严格校验 + full pytest                 [永不过期]
  dev      别名: code                             [永不过期]

别名:
  deliver  → code
  audit    → gov

两件事别混（DEC-083）:
  工作模式  switch explore|docs|test|code|gov   改 current-mode，有 HMAC 和过期
  治理暂停  suspend / resume                    跳过快层门禁，不改工作模式

示例:
  bash scripts/workflow/wf-mode.sh switch explore --duration 1h
  bash scripts/workflow/wf-mode.sh expire
  bash scripts/workflow/wf-mode.sh post-hook <mode>  # git add mode file + show hook 提示
EOF
}

# 入口
CMD="${1:-status}"
shift 1 2>/dev/null || true

case "$CMD" in
    status) cmd_status ;;
    switch) cmd_switch "$@" ;;
    expire) cmd_expire ;;
    suspend) cmd_suspend "$@" ;;
    resume) cmd_resume "$@" ;;
    post-hook) cmd_post_hook "$@" ;;
    help|-h|--help) cmd_help ;;
    *)
        echo "❌ 未知命令: $CMD"
        cmd_help
        exit 1
        ;;
esac
