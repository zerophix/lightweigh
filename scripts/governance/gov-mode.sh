#!/usr/bin/env bash
# ============================================================
# scripts/governance/gov-mode.sh
# 治理总开关（数据开关，不是改仓库配置）
#
# 用法:
#   bash scripts/governance/gov-mode.sh status
#   bash scripts/governance/gov-mode.sh off [时长]      # 默认 2h；支持 30m / 90s / 2h
#   bash scripts/governance/gov-mode.sh on
#
# 设计:
#   - 钩子与门禁脚本都 source 本文件，调 gov_mode_is_off 决定是否放行；
#   - core.hooksPath / 钩子文件始终不动，开关只是数据；
#   - off 有到期时间，过期自动回到 on，避免忘记恢复；
#   - 每次跳过记一行到 .ai/gov-skips/YYYY-MM-DD.log（可审计）；
#   - 环境变量 GOV_MODE=off 可只对单条命令生效（不影响其他会话）。
#
# 退出码: 0=off（放行） 1=on（照常门禁） 2=参数错误
# ============================================================
set -uo pipefail

gov_mode_root() {
  git rev-parse --show-toplevel 2>/dev/null || pwd
}

gov_mode_file() {
  echo "$(gov_mode_root)/.ai/gov-mode.json"
}

# 把 30m / 2h / 90s / 纯数字(分钟) 解析成秒
gov_mode_secs() {
  local raw="${1:-2h}"
  case "$raw" in
    *h) echo $(( ${raw%h} * 3600 )) ;;
    *m) echo $(( ${raw%m} * 60 )) ;;
    *s) echo $(( ${raw%s} )) ;;
    *[!0-9]*) echo "" ;;
    *) echo $(( raw * 60 )) ;;
  esac
}

# off 时返回 0（放行），on 时返回 1（继续门禁）
gov_mode_is_off() {
  if [ "${GOV_MODE:-}" = "off" ]; then
    return 0
  fi
  local f; f="$(gov_mode_file)"
  [ -f "$f" ] || return 1
  local mode until_epoch now
  mode="$(sed -n 's/.*"mode"[[:space:]]*:[[:space:]]*"\([a-z]*\)".*/\1/p' "$f" | head -1)"
  until_epoch="$(sed -n 's/.*"until_epoch"[[:space:]]*:[[:space:]]*\([0-9]*\).*/\1/p' "$f" | head -1)"
  [ "$mode" = "off" ] || return 1
  now="$(date +%s)"
  if [ -n "$until_epoch" ] && [ "$now" -ge "$until_epoch" ]; then
    rm -f "$f"
    return 1
  fi
  return 0
}

# 记录一次"因 off 而跳过"的门禁
gov_mode_audit_skip() {
  local gate="${1:-unknown}" root dir
  root="$(gov_mode_root)"
  dir="$root/.ai/gov-skips"
  mkdir -p "$dir"
  printf '%s gate=%s branch=%s\n' \
    "$(date -Iseconds)" "$gate" "$(git -C "$root" branch --show-current 2>/dev/null || echo '-')" \
    >> "$dir/$(date +%F).log"
}

gov_mode_write() {
  local secs="$1" reason="${2:-}" root f now until
  root="$(gov_mode_root)"
  f="$(gov_mode_file)"
  mkdir -p "$root/.ai"
  now="$(date +%s)"
  until=$(( now + secs ))
  printf '{"mode":"off","until_epoch":%s,"until":"%s","by":"%s","reason":"%s"}\n' \
    "$until" "$(date -r "$until" '+%Y-%m-%dT%H:%M:%S%z' 2>/dev/null || echo '-')" \
    "${SESSION_ID:-local}" "$reason" > "$f"
}

gov_mode_status() {
  local f; f="$(gov_mode_file)"
  if [ "${GOV_MODE:-}" = "off" ]; then
    echo "governance: off（环境变量 GOV_MODE=off，仅本次命令）"
    return 0
  fi
  if [ -f "$f" ]; then
    local mode until_human until_epoch now skips
    mode="$(sed -n 's/.*"mode"[[:space:]]*:[[:space:]]*"\([a-z]*\)".*/\1/p' "$f" | head -1)"
    until_human="$(sed -n 's/.*"until"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' "$f" | head -1)"
    until_epoch="$(sed -n 's/.*"until_epoch"[[:space:]]*:[[:space:]]*\([0-9]*\).*/\1/p' "$f" | head -1)"
    now="$(date +%s)"
    if [ "$mode" = "off" ] && [ -n "$until_epoch" ] && [ "$now" -lt "$until_epoch" ]; then
      skips="$(cat "$(gov_mode_root)/.ai/gov-skips/$(date +%F).log" 2>/dev/null | wc -l | tr -d ' ')"
      echo "governance: off  到期 $until_human  今日跳过 $skips 次"
      [ $(( until_epoch - now )) -gt 86400 ] && echo "  警告: 关闭时间超过 24h，建议缩短或改回 on"
      return 0
    fi
    rm -f "$f"
  fi
  echo "governance: on（全门禁生效）"
}

gov_mode_cli() {
  case "${1:-status}" in
    on)
      rm -f "$(gov_mode_file)"
      echo "governance: on"
      ;;
    off)
      local secs; secs="$(gov_mode_secs "${2:-2h}")"
      if [ -z "$secs" ]; then
        echo "用法: gov-mode.sh off [30m|2h|90s]" >&2
        exit 2
      fi
      gov_mode_write "$secs" "${3:-}"
      echo "governance: off（$(( secs / 60 )) 分钟，到期自动恢复）"
      ;;
    status)
      gov_mode_status
      ;;
    *)
      echo "用法: gov-mode.sh on|off [时长]|status" >&2
      exit 2
      ;;
  esac
}

# 作为脚本执行时才跑 CLI；被 source 时只提供函数
if [ "${BASH_SOURCE[0]}" = "$0" ]; then
  gov_mode_cli "$@"
fi
