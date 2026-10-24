#!/usr/bin/env bash
# scripts/session/ses-worktree.sh
# 多 AI 并发：创建 / 列出 / 移除 git worktree（DEC-050）
#
# 用法:
#   bash scripts/session/ses-worktree.sh add <type>/<topic> [--base <branch>] [--in-repo]
#   bash scripts/session/ses-worktree.sh list
#   bash scripts/session/ses-worktree.sh remove <path|branch> [--force]
#   bash scripts/session/ses-worktree.sh doctor   # 检查当前树 hooks ROOT 是否指向本工作树
#
# 默认路径: 与主仓同级 ../<repo>-<topic>
# --in-repo: 使用 <repo>/.worktrees/<topic>（已 gitignore）
set -euo pipefail

ROOT="$(git rev-parse --show-toplevel 2>/dev/null || true)"
if [ -z "${ROOT:-}" ]; then
  ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
fi
cd "$ROOT"

REPO_NAME="$(basename "$ROOT")"
TRUNK=""
for c in master main; do
  if git show-ref --verify --quiet "refs/heads/$c" || git show-ref --verify --quiet "refs/remotes/origin/$c"; then
    TRUNK="$c"
    break
  fi
done
TRUNK="${TRUNK:-master}"

BRANCH_RE='^(feat|fix|docs|refactor|chore|test|ci)/[a-zA-Z0-9][a-zA-Z0-9._-]+$'

usage() {
  sed -n '2,12p' "$0" | sed 's/^# \{0,1\}//'
  exit "${1:-0}"
}

slug_of() {
  # feat/my-topic -> my-topic
  local b="$1"
  echo "${b#*/}" | tr '/' '-' | tr '[:upper:]' '[:lower:]'
}

default_path_for() {
  local branch="$1" in_repo="$2" slug
  slug="$(slug_of "$branch")"
  if [ "$in_repo" = "1" ]; then
    echo "$ROOT/.worktrees/$slug"
  else
    echo "$(dirname "$ROOT")/${REPO_NAME}-${slug}"
  fi
}

print_next_steps() {
  local wt="$1" branch="$2"
  cat <<HINT

下一步（在 worktree 内执行）:
  cd "$wt"
  bash scripts/session/ses-session-begin.sh
  # 改代码后:
  git add <files>
  bash scripts/session/ses-claim-staged.sh
  git commit -m "type(scope): summary"
  bash scripts/governance/gov-local-ci.sh
  git push -u origin "$branch"
  # 开 PR 合入 master；结束后:
  bash scripts/session/ses-session-end.sh
  # 清理: bash scripts/session/ses-worktree.sh remove "$wt"

提示: hooks 已优先用 git rev-parse --show-toplevel，应指向本 worktree（见 doctor）。
HINT
}

cmd_add() {
  local branch="" base="$TRUNK" in_repo=0
  while [ $# -gt 0 ]; do
    case "$1" in
      --base) base="$2"; shift 2 ;;
      --in-repo) in_repo=1; shift ;;
      -h|--help) usage 0 ;;
      -*)
        echo "未知选项: $1" >&2
        usage 1
        ;;
      *)
        if [ -z "$branch" ]; then branch="$1"; shift; else echo "多余参数: $1" >&2; usage 1; fi
        ;;
    esac
  done
  if [ -z "$branch" ]; then
    echo "用法: ses-worktree.sh add <type>/<topic> [--base master] [--in-repo]" >&2
    exit 1
  fi
  if ! echo "$branch" | grep -qE "$BRANCH_RE"; then
    echo "分支名不符合规范: $branch" >&2
    echo "  期望: feat|fix|docs|refactor|chore|test|ci/<topic>" >&2
    exit 1
  fi

  local wt
  wt="$(default_path_for "$branch" "$in_repo")"
  if [ -e "$wt" ]; then
    echo "路径已存在: $wt" >&2
    exit 1
  fi
  if git show-ref --verify --quiet "refs/heads/$branch"; then
    echo "本地分支已存在: $branch（改用: git worktree add \"$wt\" \"$branch\"）" >&2
    exit 1
  fi

  if [ "$in_repo" = "1" ]; then
    mkdir -p "$ROOT/.worktrees"
  fi

  # 尽量从最新 trunk 起分支
  if git show-ref --verify --quiet "refs/remotes/origin/$base"; then
    git fetch origin "$base" --quiet 2>/dev/null || true
    git worktree add -b "$branch" "$wt" "origin/$base"
  elif git show-ref --verify --quiet "refs/heads/$base"; then
    git worktree add -b "$branch" "$wt" "$base"
  else
    echo "找不到基线分支: $base" >&2
    exit 1
  fi

  # local.properties 被 gitignore（sdk.dir 是本机路径），不会随 checkout 带出；
  # 缺它则新树 gradle 报 "SDK location not found"（2026-10-24 三次实测踩坑）。
  if [ -f "$ROOT/local.properties" ] && [ ! -f "$wt/local.properties" ]; then
    cp "$ROOT/local.properties" "$wt/local.properties"
    echo "  已复制 local.properties（gitignore，不入库）"
  fi

  echo "已创建 worktree"
  echo "  路径: $wt"
  echo "  分支: $branch"
  echo "  基线: $base"
  print_next_steps "$wt" "$branch"
}

cmd_list() {
  git worktree list
}

cmd_remove() {
  local target="" force=0
  while [ $# -gt 0 ]; do
    case "$1" in
      --force|-f) force=1; shift ;;
      -*)
        echo "未知选项: $1" >&2
        exit 1
        ;;
      *)
        if [ -z "$target" ]; then target="$1"; shift; else echo "多余参数: $1" >&2; exit 1; fi
        ;;
    esac
  done
  if [ -z "$target" ]; then
    echo "用法: ses-worktree.sh remove <path|branch> [--force]" >&2
    exit 1
  fi

  local wt="" branch=""
  if [ -d "$target" ]; then
    wt="$(cd "$target" && pwd)"
  else
    # treat as branch name
    branch="$target"
    wt="$(git worktree list --porcelain | awk -v b="refs/heads/$branch" '
      /^worktree / { p=$2 }
      /^branch / && $2==b { print p; exit }
    ')"
    if [ -z "$wt" ]; then
      echo "未找到分支对应的 worktree: $branch" >&2
      exit 1
    fi
  fi

  # refuse removing primary worktree
  local main_wt
  main_wt="$(git worktree list --porcelain | awk '/^worktree /{print $2; exit}')"
  if [ "$wt" = "$main_wt" ] || [ "$wt" = "$ROOT" ]; then
    echo "拒绝移除主工作树: $wt" >&2
    exit 1
  fi

  local args=(worktree remove)
  if [ "$force" = "1" ]; then args+=(--force); fi
  args+=("$wt")
  git "${args[@]}"
  echo "已移除 worktree: $wt"
  echo "提示: 本地分支如不再需要，可: git branch -d <branch>（未合入用 -D）"
}

cmd_doctor() {
  local tl gd hooks
  tl="$(git rev-parse --show-toplevel)"
  gd="$(git rev-parse --git-dir)"
  hooks="$(git rev-parse --git-path hooks)"
  echo "show-toplevel: $tl"
  echo "git-dir:       $gd"
  echo "hooks path:    $hooks"
  if [ "$tl" != "$ROOT" ] && [ "$(cd "$ROOT" && pwd)" != "$tl" ]; then
    echo "WARN: 脚本启动 ROOT 与 show-toplevel 不一致"
  else
    echo "ROOT OK（本工作树）"
  fi
  # simulate hook ROOT preference
  local hook_guess
  hook_guess="$(cd "$(dirname "$hooks")/.." 2>/dev/null && pwd || true)"
  echo "若 hook 用 dirname/\$0 回推，可能落到: $hook_guess（worktree 下常为错误主树）"
  echo "DEC-050 后 pre-commit/pre-push 优先 --show-toplevel → 应为: $tl"
  if [ -f "$hooks/pre-commit" ]; then
    if grep -q 'show-toplevel' "$hooks/pre-commit" 2>/dev/null; then
      echo "installed pre-commit: 含 show-toplevel ✅"
    else
      echo "installed pre-commit: 可能仍是旧 ROOT 逻辑 — 请运行 bash scripts/bootstrap/bs-install-hooks.sh"
    fi
  fi
}

main() {
  local cmd="${1:-}"
  shift || true
  case "$cmd" in
    add) cmd_add "$@" ;;
    list|ls) cmd_list "$@" ;;
    remove|rm) cmd_remove "$@" ;;
    doctor) cmd_doctor "$@" ;;
    -h|--help|help|"") usage 0 ;;
    *)
      echo "未知命令: $cmd" >&2
      usage 1
      ;;
  esac
}

main "$@"
