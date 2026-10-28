#!/usr/bin/env bash
# ============================================================
# scripts/governance/gov-mode.sh
# 治理总开关（数据开关，不是改仓库配置）
#
# 用法:
#   bash scripts/governance/gov-mode.sh status
#   bash scripts/governance/gov-mode.sh off [时长]      # 默认 2h；支持 30m / 90s / 2h；上限 8h
#   bash scripts/governance/gov-mode.sh on
#   bash scripts/governance/gov-mode.sh disable         # 永久停用（无到期）+ 摘掉 core.hooksPath
#   bash scripts/governance/gov-mode.sh enable          # 恢复（装回 core.hooksPath）
#
# 作用域（多 AI 并发时按这个顺序判定）:
#   1. 环境变量 GOV_SUSPEND=1     → 只对当前命令生效，不影响任何人
#   2. GOV_SESSION=xxx 时的 Session 文件 → .ai/gov-mode/<session>.json（同 worktree 内多会话隔离）
#   3. 缺省                          → 当前 worktree 的 .ai/gov-mode.json（每个 worktree 各自一份）
#
# 注意（DEC-083）：暂停用 GOV_SUSPEND，不要用 GOV_MODE。
#   GOV_MODE 只表示工作模式（explore/docs/test/code/gov），两者曾是同一个变量。
#
# 设计:
#   - 钩子与门禁脚本都 source 本文件，调 gov_mode_is_off 决定是否放行；
#   - core.hooksPath 指向主仓库的绝对路径，所有 worktree 共用同一份钩子，旧分支无法绕过；
#   - off 有到期时间（最长 8h），过期自动回到 on；
#   - 每次跳过记一行到 .ai/gov-skips/YYYY-MM-DD.log，并在 commit 时打印一行提示（GOV_QUIET=1 可静音）；
#   - 写文件用临时文件 + mv，避免并发读到半截 JSON。
#
# 退出码: 0=off（放行） 1=on（照常门禁） 2=参数错误
# ============================================================
set -uo pipefail

GOV_SUSPEND_MAX_SECS=$(( 8 * 3600 ))

gov_mode_root() {
  # 与 gov-mode-lib.sh 的 _gov_mode_root 一致：测试可用 GOV_STATE_ROOT 隔离状态
  if [ -n "${GOV_STATE_ROOT:-}" ]; then
    printf '%s\n' "$GOV_STATE_ROOT"
    return 0
  fi
  git rev-parse --show-toplevel 2>/dev/null || pwd
}

gov_mode_dir() {
  echo "$(gov_mode_root)/.ai"
}

# Session 作用域优先；否则按 worktree
gov_mode_file() {
  local dir; dir="$(gov_mode_dir)"
  if [ -n "${GOV_SESSION:-}" ]; then
    local safe
    safe="$(printf '%s' "$GOV_SESSION" | tr -c 'A-Za-z0-9._-' '-')"
    echo "$dir/gov-mode/$safe.json"
  else
    echo "$dir/gov-mode.json"
  fi
}

gov_mode_scope() {
  if [ -n "${GOV_SESSION:-}" ]; then
    echo "session:${GOV_SESSION}"
  else
    echo "worktree:$(gov_mode_root)"
  fi
}

# 把 30m / 2h / 90s / 纯数字(分钟) 解析成秒；超过上限则截断
gov_mode_secs() {
  local raw="${1:-2h}" secs
  case "$raw" in
    *h) secs=$(( ${raw%h} * 3600 )) ;;
    *m) secs=$(( ${raw%m} * 60 )) ;;
    *s) secs=$(( ${raw%s} )) ;;
    *[!0-9]*) return 1 ;;
    *) secs=$(( raw * 60 )) ;;
  esac
  [ "$secs" -le 0 ] && return 1
  if [ "$secs" -gt "$GOV_SUSPEND_MAX_SECS" ]; then
    secs="$GOV_SUSPEND_MAX_SECS"
  fi
  echo "$secs"
}

# 读取 JSON 字段（无 jq 依赖）
gov_mode_field() {
  local f="$1" key="$2"
  sed -n "s/.*\"$key\"[[:space:]]*:[[:space:]]*\"\\([^\"]*\\)\".*/\\1/p" "$f" | head -1
}

gov_mode_epoch_field() {
  local f="$1"
  sed -n 's/.*"until_epoch"[[:space:]]*:[[:space:]]*\([0-9]*\).*/\1/p' "$f" | head -1
}

# 永久停用哨兵：无到期时间，存在即「治理整体关闭」。
# 放在 git 公共目录（所有 worktree 共享同一份），不进版本库、不脏工作区。
gov_mode_sentinel() {
  local common
  common="$(git rev-parse --path-format=absolute --git-common-dir 2>/dev/null || true)"
  [ -n "$common" ] && [ -d "$common" ] || common="$(gov_mode_root)/.git"
  echo "$common/gov-disabled"
}

# 环境变量一次性暂停（DEC-083）：
#   GOV_SUSPEND=1   → 仅本次命令跳过快层门禁
#   GOV_MODE=off    → 历史写法，保留兼容但告警
# GOV_MODE 现在只表示工作模式（explore/docs/test/code/gov），不再表示"关"。
gov_mode_env_suspended() {
    case "${GOV_SUSPEND:-}" in
        1|true|yes|on|off) return 0 ;;
    esac
    if [ "${GOV_MODE:-}" = "off" ]; then
        echo "[gov] GOV_MODE=off 已废弃，请改用 GOV_SUSPEND=1（GOV_MODE 现在只表示工作模式）" >&2
        return 0
    fi
    return 1
}

# off 时返回 0（放行），on 时返回 1（继续门禁）
gov_mode_is_off() {
  if gov_mode_env_suspended; then
    return 0
  fi
  # 永久停用优先于一切（无到期时间，只能显式 enable 解除）
  [ -f "$(gov_mode_sentinel)" ] && return 0
  local f; f="$(gov_mode_file)"
  [ -f "$f" ] || return 1
  local mode until_epoch now
  mode="$(gov_mode_field "$f" mode)"
  until_epoch="$(gov_mode_epoch_field "$f")"
  [ "$mode" = "off" ] || return 1
  now="$(date +%s)"
  if [ -n "$until_epoch" ] && [ "$now" -ge "$until_epoch" ]; then
    rm -f "$f"
    return 1
  fi
  return 0
}

# 一行描述（给 commit 时的提示与 status 用）
gov_mode_describe() {
  if [ "${GOV_SUSPEND:-}" = "1" ] || [ "${GOV_SUSPEND:-}" = "true" ]; then
    echo "env GOV_SUSPEND=${GOV_SUSPEND}（仅本次命令）"
    return
  fi
  if [ "${GOV_MODE:-}" = "off" ]; then
    echo "env GOV_MODE=off（已废弃，仅本次命令；请改用 GOV_SUSPEND=1）"
    return
  fi
  if [ -f "$(gov_mode_sentinel)" ]; then
    echo "已永久停用（gov-mode.sh enable 恢复）"
    return
  fi
  local f; f="$(gov_mode_file)"
  [ -f "$f" ] || { echo "on"; return; }
  local until_human by reason until_epoch now left
  until_human="$(gov_mode_field "$f" until)"
  by="$(gov_mode_field "$f" by)"
  reason="$(gov_mode_field "$f" reason)"
  until_epoch="$(gov_mode_epoch_field "$f")"
  now="$(date +%s)"
  left=$(( (until_epoch - now) / 60 ))
  printf 'off，剩 %s 分钟（到 %s，by %s%s）' "$left" "$until_human" "${by:-local}" "${reason:+，$reason}"
}

# 记录一次"因 off 而跳过"的门禁
gov_mode_audit_skip() {
  local gate="${1:-unknown}" root dir
  root="$(gov_mode_root)"
  dir="$root/.ai/gov-skips"
  mkdir -p "$dir"
  printf '%s gate=%s scope=%s branch=%s\n' \
    "$(date -Iseconds)" "$gate" "$(gov_mode_scope)" \
    "$(git -C "$root" branch --show-current 2>/dev/null || echo '-')" \
    >> "$dir/$(date +%F).log"
}

# commit/push 时的一行可见提示（GOV_QUIET=1 静音）
gov_mode_banner() {
  [ "${GOV_QUIET:-}" = "1" ] && return 0
  echo "[gov] governance $(gov_mode_describe) → 跳过 ${1:-gate}" >&2
}

# 原子写：临时文件 + mv，避免并发读到半截
gov_mode_write() {
  local secs="$1" reason="${2:-}" root dir f now until tmp
  root="$(gov_mode_root)"
  dir="$(gov_mode_dir)"
  f="$(gov_mode_file)"
  mkdir -p "$dir" "$(dirname "$f")"
  now="$(date +%s)"
  until=$(( now + secs ))
  tmp="$f.tmp.$$"
  printf '{"mode":"off","until_epoch":%s,"until":"%s","by":"%s","scope":"%s","reason":"%s"}\n' \
    "$until" "$(date -r "$until" '+%Y-%m-%dT%H:%M:%S%z' 2>/dev/null || echo '-')" \
    "${SESSION_ID:-local}" "$(gov_mode_scope)" "$reason" > "$tmp"
  mv -f "$tmp" "$f"
}

gov_mode_status() {
  local f; f="$(gov_mode_file)"
  local hooks_path
  hooks_path="$(git config --get core.hooksPath 2>/dev/null || echo '<未设置>')"
  if gov_mode_is_off; then
    local skips
    skips="$(cat "$(gov_mode_dir)/gov-skips/$(date +%F).log" 2>/dev/null | wc -l | tr -d ' ')"
    echo "governance: $(gov_mode_describe)"
    echo "  作用域: $(gov_mode_scope)"
    echo "  钩子来源: $hooks_path"
    echo "  今日跳过: ${skips} 次"
    return 0
  fi
  echo "governance: on（全门禁生效）"
  echo "  作用域: $(gov_mode_scope)"
  echo "  钩子来源: $hooks_path"
  case "$hooks_path" in
    /*) ;;
    *) echo "  提示: 钩子路径不是绝对路径，旧 worktree 可能用到过期钩子；用 gov-hook-doctor.sh --fix 修正" ;;
  esac
}

gov_mode_cli() {
  case "${1:-status}" in
    on)
      rm -f "$(gov_mode_file)"
      echo "governance: on（作用域 $(gov_mode_scope)）"
      ;;
    off)
      local secs
      if ! secs="$(gov_mode_secs "${2:-2h}")"; then
        echo "用法: gov-mode.sh off [30m|2h|90s]（上限 8h）" >&2
        exit 2
      fi
      gov_mode_write "$secs" "${3:-}"
      echo "governance: off（$(( secs / 60 )) 分钟，上限 8h，到期自动恢复；作用域 $(gov_mode_scope)）"
      ;;
    status)
      gov_mode_status
      ;;
    disable)
      # 永久关闭：哨兵 + 摘掉钩子（钩子不跑，连一次都不用等）
      local s; s="$(gov_mode_sentinel)"
      mkdir -p "$(dirname "$s")"
      printf '%s\n' "disabled $(date -Iseconds) by ${SESSION_ID:-local}" > "$s"
      git config --unset core.hooksPath 2>/dev/null || true
      echo "governance: 已永久停用（哨兵 ${s}）"
      echo "  core.hooksPath 已摘除 → git 不再执行 pre-commit / commit-msg / pre-push"
      echo "  恢复: bash scripts/governance/gov-mode.sh enable"
      ;;
    enable)
      local s; s="$(gov_mode_sentinel)"
      rm -f "$s" "$(gov_mode_file)"
      if [ -d "$(gov_mode_root)/scripts/hooks" ]; then
        git config core.hooksPath "$(gov_mode_root)/scripts/hooks"
        echo "governance: on（已恢复 core.hooksPath）"
      else
        echo "governance: on（未找到 scripts/hooks，钩子路径未设置）"
      fi
      ;;
    *)
      echo "用法: gov-mode.sh on|off [时长]|disable|enable|status" >&2
      exit 2
      ;;
  esac
}

# 作为脚本执行时才跑 CLI；被 source 时只提供函数
if [ "${BASH_SOURCE[0]}" = "$0" ]; then
  gov_mode_cli "$@"
fi
