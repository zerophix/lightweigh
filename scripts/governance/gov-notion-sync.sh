#!/usr/bin/env bash
# scripts/governance/gov-notion-sync.sh
# Notion 同步统一入口（免费版轮询模式）
#
# 用法:
#   gov-notion-sync.sh pull [--dry-run] [--lookback-hours N]     # 拉取同步 Notion -> 仓库
#   gov-notion-sync.sh push [--task T-XXX] [--all]               # 推送反馈 仓库 -> Notion
#   gov-notion-sync.sh full [--dry-run]                          # 完整双向同步
#   gov-notion-sync.sh status                                    # 查看同步状态
#   gov-notion-sync.sh init                                      # 初始化配置/数据库
#   gov-notion-sync.sh test                                      # 测试连接
#
# 环境变量:
#   NOTION_TOKEN              - Notion 内部集成 Token
#   NOTION_WORKSPACE_ID       - 工作区 ID
#   NOTION_DB_EPICS           - Product Epics 数据库 ID
#   NOTION_DB_FEATURES        - Product Features 数据库 ID
#   NOTION_DB_STORIES         - Product Stories 数据库 ID
#   NOTION_DB_DECISIONS       - Decisions 数据库 ID
#   NOTION_POLL_INTERVAL      - 轮询间隔(分钟，默认15)
#   NOTION_LOOKBACK_HOURS     - 回看小时数(默认24)

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
cd "$ROOT"

export PYTHONPATH="$ROOT:${PYTHONPATH:-}"

# 加载 .env 文件（如果存在）
if [ -f "$ROOT/.env" ]; then
    set -a
    source "$ROOT/.env"
    set +a
fi

usage() {
    sed -n '3,20p' "$0" | sed 's/^# \{0,1\}//'
}

log() { echo "[$(date '+%H:%M:%S')] $*"; }
die() { log "错误: $*" >&2; exit 1; }

# 检查必要环境变量（支持 OAuth 或内部集成 Token）
check_env() {
    local missing=()
    
    # 认证方式：OAuth (client_id + secret) 或 内部集成 Token
    has_oauth=0
    [ -n "${NOTION_OAUTH_CLIENT_ID:-}" ] && [ -n "${NOTION_OAUTH_CLIENT_SECRET:-}" ] && has_oauth=1
    has_token=0
    [ -n "${NOTION_TOKEN:-}" ] && has_token=1
    
    if [ $has_oauth -eq 0 ] && [ $has_token -eq 0 ]; then
        missing+=("NOTION_OAUTH_CLIENT_ID+SECRET 或 NOTION_TOKEN")
    fi
    
    [ -z "${NOTION_WORKSPACE_ID:-}" ] && missing+=("NOTION_WORKSPACE_ID")
    [ -z "${NOTION_DB_EPICS:-}" ] && missing+=("NOTION_DB_EPICS")
    [ -z "${NOTION_DB_FEATURES:-}" ] && missing+=("NOTION_DB_FEATURES")
    [ -z "${NOTION_DB_STORIES:-}" ] && missing+=("NOTION_DB_STORIES")
    
    if [ ${#missing[@]} -gt 0 ]; then
        die "缺少环境变量: ${missing[*]} (请在 .env 或 GitHub Secrets 中设置)"
    fi
}

cmd_pull() {
    local dry_run=0
    local lookback=24
    
    while [ $# -gt 0 ]; do
        case "$1" in
            --dry-run) dry_run=1 ;;
            --lookback-hours) lookback="$2"; shift ;;
            *) die "未知参数: $1" ;;
        esac
        shift
    done
    
    check_env
    log "开始拉取同步 (lookback=${lookback}h, dry_run=$dry_run)..."
    
    python3 -m scripts.governance.notion_sync.main pull \
        --lookback-hours "$lookback" \
        $( [ "$dry_run" -eq 1 ] && echo "--dry-run" )
}

cmd_push() {
    local task_id=""
    local all=0
    
    while [ $# -gt 0 ]; do
        case "$1" in
            --task) task_id="$2"; shift ;;
            --all) all=1 ;;
            *) die "未知参数: $1" ;;
        esac
        shift
    done
    
    check_env
    
    if [ -n "$task_id" ]; then
        log "推送任务 $task_id 状态..."
        python3 -m scripts.governance.notion_sync.main push --task "$task_id"
    elif [ "$all" -eq 1 ]; then
        log "推送所有任务状态..."
        python3 -m scripts.governance.notion_sync.main push --all
    else
        die "请指定 --task T-XXX 或 --all"
    fi
}

cmd_full() {
    local dry_run=0
    
    while [ $# -gt 0 ]; do
        case "$1" in
            --dry-run) dry_run=1 ;;
            *) die "未知参数: $1" ;;
        esac
        shift
    done
    
    check_env
    log "完整双向同步..."
    
    # 1. Pull
    cmd_pull $( [ "$dry_run" -eq 1 ] && echo "--dry-run" )
    
    # 2. Push（可选：仅推送有变更的任务）
    # python3 -m scripts.governance.notion_sync.main push --changed
}

cmd_status() {
    check_env
    log "同步状态:"
    python3 -m scripts.governance.notion_sync.main status
}

cmd_init() {
    log "初始化 Notion 同步配置..."
    python3 -m scripts.governance.notion_sync.main init
}

cmd_test() {
    check_env
    log "测试 Notion 连接..."
    python3 -m scripts.governance.notion_sync.main test
}

cmd_auth() {
    check_env
    log "启动 OAuth 授权流程..."
    python3 -m scripts.governance.notion_sync.main auth
}

main() {
    local cmd="${1:-help}"
    shift || true
    
    case "$cmd" in
        pull) cmd_pull "$@" ;;
        push) cmd_push "$@" ;;
        full) cmd_full "$@" ;;
        status) cmd_status ;;
        init) cmd_init ;;
        test) cmd_test ;;
        auth) cmd_auth ;;
        help|--help|-h) usage ;;
        *) die "未知命令: $cmd (用法: gov-notion-sync.sh help)" ;;
    esac
}

main "$@"