#!/usr/bin/env bash
# ============================================================
# scripts/workflow/wf-run.sh
# 后台任务执行器 — 让长命令（gradle 构建、全量 CI）不再阻塞会话
#
# 用法:
#   wf-run.sh start <name> -- <command...>   启动后台任务，立即回显 job id
#   wf-run.sh wait <id> [--timeout 秒]       前台等待结束，打印日志尾部与退出码
#   wf-run.sh list                           列出全部 job 及状态
#   wf-run.sh tail <id>                      打印日志尾部
#   wf-run.sh status <id>                    单行状态（running / exit=N）
#   wf-run.sh gc [--days N]                  清理 N 天前（默认 7）的已完成 job
#
# 布局（默认仓库根 .cache/jobs，可用 WF_RUN_JOBS_DIR 覆盖，测试用）:
#   <id>.log    stdout + stderr
#   <id>.exit   退出码（原子写：临时文件 + mv）
#   <id>.meta   启动时间、工作目录、原始命令
#
# id 形如 20261024-041500-lane-ci-4821：时间戳 + 名称 slug + 发起进程 PID
# （PID 保证同秒同前缀并发时 id 不冲突）。
#
# 说明: 本脚本刻意不用 `set -e`，wait 需要精确回传被等待命令的退出码。
# ============================================================

set -uo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
JOBS_DIR="${WF_RUN_JOBS_DIR:-$ROOT/.cache/jobs}"
mkdir -p "$JOBS_DIR" 2>/dev/null || {
  echo "wf-run: 无法创建 job 目录: $JOBS_DIR" >&2
  exit 2
}

usage() {
  cat <<'EOF'
用法:
  wf-run.sh start <name> -- <command...>   启动后台任务，立即回显 job id
  wf-run.sh wait <id> [--timeout 秒]       前台等待结束，打印日志尾部与退出码
  wf-run.sh list                           列出全部 job 及状态
  wf-run.sh tail <id>                      打印日志尾部
  wf-run.sh status <id>                    单行状态（running / exit=N）
  wf-run.sh gc [--days N]                  清理 N 天前（默认 7）的已完成 job

示例:
  bash scripts/workflow/wf-run.sh start lane-ci -- bash scripts/governance/gov-local-ci.sh
  bash scripts/workflow/wf-run.sh wait 20261024-041500-lane-ci-4821
  bash scripts/workflow/wf-run.sh list
EOF
}

die_usage() {
  echo "wf-run: $1" >&2
  usage >&2
  exit 2
}

die() {
  echo "wf-run: $1" >&2
  exit 2
}

now() {
  date +%s
}

# 文件修改时间（epoch 秒），兼容 macOS(BSD) 与 Linux(GNU)
mtime() {
  stat -f %m "$1" 2>/dev/null || stat -c %Y "$1" 2>/dev/null || echo 0
}

valid_id() {
  case "${1:-}" in
    ''|*[!A-Za-z0-9._-]*) return 1 ;;
    *) return 0 ;;
  esac
}

require_id() {
  local id="${1:-}"
  valid_id "$id" || die_usage "缺少或非法的 job id"
  printf '%s' "$id"
}

fmt_duration() {
  # $1 = 秒数（整数）
  local s="${1:-0}"
  [ "$s" -ge 0 ] 2>/dev/null || s=0
  printf '%ss' "$s"
}

cmd_start() {
  local name="${1:-}"
  [ -n "$name" ] || die_usage "start 缺少 <name>"
  shift

  if [ "${1:-}" != "--" ]; then
    die_usage "start 缺少 -- 分隔符（用法: start <name> -- <command...>）"
  fi
  shift

  [ "$#" -gt 0 ] || die_usage "start 缺少要执行的命令"

  local slug
  slug="$(printf '%s' "$name" \
    | tr '[:upper:]' '[:lower:]' \
    | sed 's/[^a-z0-9._-]/-/g' \
    | sed 's/^-*//; s/-*$//')"
  [ -n "$slug" ] || slug="job"

  local id log exitf meta
  id="$(date +%Y%m%d-%H%M%S)-${slug}-$$"
  log="$JOBS_DIR/$id.log"
  exitf="$JOBS_DIR/$id.exit"
  meta="$JOBS_DIR/$id.meta"

  # .exit 绝不能提前存在（否则 wait 会误判已完成）
  rm -f "$exitf"

  # 元数据原子写入（临时文件 + mv）
  local meta_tmp="$meta.tmp.$$"
  {
    printf 'start=%s\n' "$(now)"
    printf 'cwd=%s\n' "$PWD"
    printf 'cmd='
    printf '%q ' "$@"
    printf '\n'
  } >"$meta_tmp" && mv -f "$meta_tmp" "$meta"

  # 后台执行：命令退出后把退出码原子写入 .exit
  # bash -c 的 $0 占位为 wf-run，剩余参数原样传给内嵌脚本
  WF_RUN_EXIT="$exitf" nohup bash -c '
    "$@"
    rc=$?
    tmp="$WF_RUN_EXIT.tmp.$$"
    printf "%s\n" "$rc" > "$tmp" && mv -f "$tmp" "$WF_RUN_EXIT"
  ' wf-run "$@" >"$log" 2>&1 </dev/null &
  local pid=$!
  disown "$pid" 2>/dev/null || true

  # stdout 只输出 id，方便脚本捕获
  printf '%s\n' "$id"
}

cmd_wait() {
  local id
  id="$(require_id "${1:-}")"
  shift || true

  local timeout=""
  while [ "$#" -gt 0 ]; do
    case "$1" in
      --timeout)
        timeout="${2:-}"
        [ -n "$timeout" ] || die_usage "--timeout 缺少秒数"
        case "$timeout" in
          ''|*[!0-9]*) die_usage "--timeout 需要非负整数秒" ;;
        esac
        shift 2
        ;;
      *)
        die_usage "wait 未知参数: $1"
        ;;
    esac
  done

  local log="$JOBS_DIR/$id.log"
  local exitf="$JOBS_DIR/$id.exit"

  [ -f "$log" ] || [ -f "$exitf" ] || die "未知 job id: $id"

  local deadline=""
  if [ -n "$timeout" ]; then
    deadline=$(( $(now) + timeout ))
  fi

  while [ ! -f "$exitf" ]; do
    if [ -n "$deadline" ] && [ "$(now)" -ge "$deadline" ]; then
      echo "wf-run: 等待 $id 超时（${timeout}s），任务可能仍在后台运行" >&2
      exit 124
    fi
    sleep 0.2
  done

  tail -n 20 "$log" 2>/dev/null || true

  local rc
  rc="$(cat "$exitf" 2>/dev/null)"
  case "$rc" in
    ''|*[!0-9]*) rc=1 ;;
  esac
  echo "exit=$rc"
  exit "$rc"
}

cmd_list() {
  [ "$#" -eq 0 ] || die_usage "list 不接受参数"

  printf '%-40s %-8s %-6s %-10s %s\n' "ID" "STATUS" "EXIT" "DURATION" "LOG"
  local found=0
  local exitf id log meta start end dur rc status
  # 先取一份 id 快照再分类。
  # 不能分两趟（先扫 *.exit 再扫 *.log）：某个 job 若恰好在这两趟之间结束，
  # 第一趟还看不到它的 .exit，第二趟又会因 .exit 已存在而被 continue 跳过，
  # 于是整个 job 从列表里消失（2026-10-27 并发用例稳定复现）。
  local -a ids=()
  local seen=" "
  local f base
  for f in "$JOBS_DIR"/*.exit "$JOBS_DIR"/*.log; do
    [ -e "$f" ] || continue
    base="$(basename "$f")"
    base="${base%.exit}"
    base="${base%.log}"
    case "$seen" in *" $base "*) continue ;; esac
    seen="$seen$base "
    ids+=("$base")
  done

  for id in "${ids[@]}"; do
    found=1
    log="$JOBS_DIR/$id.log"
    exitf="$JOBS_DIR/$id.exit"
    meta="$JOBS_DIR/$id.meta"
    start="$(sed -n 's/^start=//p' "$meta" 2>/dev/null | head -n 1)"
    if [ -f "$exitf" ]; then
      [ -n "$start" ] || start="$(mtime "$log")"
      end="$(mtime "$exitf")"
      dur=$(( end - start ))
      [ "$dur" -ge 0 ] 2>/dev/null || dur=0
      rc="$(cat "$exitf" 2>/dev/null)"
      case "$rc" in
        ''|*[!0-9]*) rc="?" ;;
      esac
      printf '%-40s %-8s %-6s %-10s %s\n' \
        "$id" "done" "$rc" "$(fmt_duration "$dur")" "$log"
    else
      [ -n "$start" ] || start="$(mtime "$log")"
      dur=$(( $(now) - start ))
      [ "$dur" -ge 0 ] 2>/dev/null || dur=0
      printf '%-40s %-8s %-6s %-10s %s\n' \
        "$id" "running" "-" "$(fmt_duration "$dur")" "$log"
    fi
  done

  if [ "$found" -eq 0 ]; then
    echo "(没有 job)"
  fi
}

cmd_tail() {
  local id
  id="$(require_id "${1:-}")"
  [ "$#" -le 1 ] || die_usage "tail 只接受一个 job id"
  local log="$JOBS_DIR/$id.log"
  [ -f "$log" ] || die "未知 job id: $id"
  tail -n 20 "$log"
}

cmd_status() {
  local id
  id="$(require_id "${1:-}")"
  [ "$#" -le 1 ] || die_usage "status 只接受一个 job id"
  local log="$JOBS_DIR/$id.log"
  local exitf="$JOBS_DIR/$id.exit"
  [ -f "$log" ] || [ -f "$exitf" ] || die "未知 job id: $id"

  if [ -f "$exitf" ]; then
    local rc
    rc="$(cat "$exitf" 2>/dev/null)"
    case "$rc" in
      ''|*[!0-9]*) rc="?" ;;
    esac
    printf 'exit=%s\n' "$rc"
  else
    printf 'running\n'
  fi
}

cmd_gc() {
  local days=7
  while [ "$#" -gt 0 ]; do
    case "$1" in
      --days)
        days="${2:-}"
        [ -n "$days" ] || die_usage "gc --days 缺少天数"
        case "$days" in
          ''|*[!0-9]*) die_usage "gc --days 需要非负整数" ;;
        esac
        shift 2
        ;;
      *)
        die_usage "gc 未知参数: $1"
        ;;
    esac
  done

  local cutoff=$(( $(now) - days * 86400 ))
  local removed=0
  local exitf id mt

  for exitf in "$JOBS_DIR"/*.exit; do
    [ -e "$exitf" ] || continue
    mt="$(mtime "$exitf")"
    # --days 0 视为“全部已完成 job”；否则只清理早于 cutoff 的
    if [ "$days" -eq 0 ] || [ "$mt" -lt "$cutoff" ]; then
      id="$(basename "$exitf" .exit)"
      rm -f "$JOBS_DIR/$id.exit" "$JOBS_DIR/$id.log" "$JOBS_DIR/$id.meta"
      removed=$(( removed + 1 ))
    fi
  done

  echo "已清理 $removed 个 job（阈值: ${days} 天）"
}

main() {
  local action="${1:-}"
  [ -n "$action" ] || die_usage "缺少子命令"
  shift || true

  case "$action" in
    start)  cmd_start "$@" ;;
    wait)   cmd_wait "$@" ;;
    list)   cmd_list "$@" ;;
    tail)   cmd_tail "$@" ;;
    status) cmd_status "$@" ;;
    gc)     cmd_gc "$@" ;;
    -h|--help|help) usage ;;
    *)      die_usage "未知子命令: $action" ;;
  esac
}

main "$@"
