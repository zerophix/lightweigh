#!/usr/bin/env bash
# scripts/session/ses-worktree.sh
# 多 AI 并发：创建 / 列出 / 移除 git worktree（DEC-050）
#
# 用法:
#   bash scripts/session/ses-worktree.sh add <type>/<topic> [--base <branch>] [--in-repo]
#   bash scripts/session/ses-worktree.sh list
#   bash scripts/session/ses-worktree.sh remove <path|branch> [--force]
#   bash scripts/session/ses-worktree.sh clean [--dry-run]   # 清理已合入主干的分支 worktree
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
  sed -n '2,13p' "$0" | sed 's/^# \{0,1\}//'
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

# worktree 数量硬上限（不含主仓）：超过即拒绝新建，强制先清理（DEC-069）
WT_LIMIT="${WF_WORKTREE_LIMIT:-3}"

count_task_worktrees() {
  # 统计除主仓以外的 worktree 数
  local n=0 main
  main="$(git rev-parse --show-toplevel 2>/dev/null)"
  while IFS= read -r line; do
    case "$line" in
      "worktree "*) [ "${line#worktree }" = "$main" ] || n=$((n + 1)) ;;
    esac
  done < <(git worktree list --porcelain)
  echo "$n"
}

cmd_add() {
  local branch="" base="$TRUNK" in_repo=0 force=0
  while [ $# -gt 0 ]; do
    case "$1" in
      --base) base="$2"; shift 2 ;;
      --in-repo) in_repo=1; shift ;;
      --force) force=1; shift ;;
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
    echo "本地分支已存在: ${branch}（改用: git worktree add \"$wt\" \"$branch\"）" >&2
    exit 1
  fi

  if [ "$in_repo" = "1" ]; then
    mkdir -p "$ROOT/.worktrees"
  fi

  # 上限守卫：worktree 堆积是真实事故（一天堆到 12 个），必须硬拦
  local current
  current="$(count_task_worktrees)"
  if [ "$force" -ne 1 ] && [ "$current" -ge "$WT_LIMIT" ]; then
    echo "❌ 当前有 ${current} 个任务 worktree（上限 ${WT_LIMIT}），拒绝继续创建。" >&2
    echo "   先清理: bash scripts/session/ses-worktree.sh clean" >&2
    echo "   或查审计: bash scripts/governance/gov-workspace-doctor.sh" >&2
    echo "   确需临时突破: 加 --force（不推荐）" >&2
    exit 1
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
  # Gradle 分发包按 PROJECT 目录存放（gitignore）：不带过去则 wrapper 要联网下载
  if [ -d "$ROOT/gradle/dists" ] && [ ! -d "$wt/gradle/dists" ]; then
    mkdir -p "$wt/gradle"
    cp -R "$ROOT/gradle/dists" "$wt/gradle/dists" 2>/dev/null && \
      echo "  已复制 gradle/dists（离线可用）"
  fi

  # 登记：谁、什么时候、为哪个分支创建（供 status/doctor 审计年龄）
  if [ -d "$ROOT/.cache" ]; then
    printf '{"branch":"%s","created_at":"%s","session":"%s"}\n' \
      "$branch" "$(date -u '+%Y-%m-%dT%H:%M:%SZ')" \
      "$(python3 -c "import json;print(json.load(open('$ROOT/.ai/current-session.json')).get('session_id',''))" 2>/dev/null || echo '')" \
      >> "$ROOT/.cache/worktrees.jsonl" 2>/dev/null || true
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

cmd_clean() {
  # 清理已合入主干的 worktree（DEC-064）：
  #   只处理「分支已是主干祖先」的 worktree，未合入的一律保留。
  #   顺序: 移除 worktree（脏树不强制）→ 清残留目录 → worktree prune。
  local dry=0
  while [ $# -gt 0 ]; do
    case "$1" in
      --dry-run|--dry|-n) dry=1; shift ;;
      -h|--help) usage 0 ;;
      *)
        echo "未知选项: $1" >&2
        echo "用法: ses-worktree.sh clean [--dry-run]" >&2
        exit 1
        ;;
    esac
  done

  # 判定「已合入」的基线：优先远端主干（本地 master 可能落后）
  local base="" base_kind=""
  if git show-ref --verify --quiet "refs/remotes/origin/$TRUNK"; then
    base="origin/$TRUNK"
    base_kind="远端"
  elif git show-ref --verify --quiet "refs/heads/$TRUNK"; then
    base="$TRUNK"
    base_kind="本地"
  else
    echo "找不到基线分支: ${TRUNK}（无法判断哪些分支已合入）" >&2
    exit 1
  fi

  if [ "$dry" = "0" ]; then
    echo "基线: ${base}（${base_kind}）"
  else
    echo "基线: ${base}（${base_kind}，dry-run 不执行）"
  fi

  local main_wt
  main_wt="$(git worktree list --porcelain | awk '/^worktree /{print $2; exit}')"

  local cleaned=0 skipped=0
  local p b
  while IFS=$'\t' read -r p b; do
    [ -n "$p" ] || continue
    if [ "$p" = "$main_wt" ]; then
      continue
    fi
    if [ -z "$b" ]; then
      echo "保留（detached HEAD）: $p"
      skipped=$((skipped + 1))
      continue
    fi
    if ! git show-ref --verify --quiet "refs/heads/$b"; then
      echo "保留（分支不存在）: ${p}（${b}）"
      skipped=$((skipped + 1))
      continue
    fi
    if ! git merge-base --is-ancestor "refs/heads/$b" "$base" 2>/dev/null; then
      echo "保留（未合入 ${base}）: ${p}（${b}）"
      skipped=$((skipped + 1))
      continue
    fi

    echo "已合入 $base: $b → $p"
    if [ "$dry" = "1" ]; then
      echo "  [dry-run] 将移除 worktree: $p"
      if [ -d "$p" ]; then
        echo "  [dry-run] 将清理残留目录: $p"
      fi
      cleaned=$((cleaned + 1))
      continue
    fi

    # 不默认 --force: 有未提交改动时保留现场，交给人类确认
    if ! git worktree remove "$p" 2>/dev/null; then
      echo "  跳过（有未提交改动或占用中）: $p"
      echo "  确认丢弃后: bash scripts/session/ses-worktree.sh remove \"$p\" --force"
      skipped=$((skipped + 1))
      continue
    fi
    # 残留兄弟目录（worktree 元信息已删但目录还在）
    if [ -d "$p" ]; then
      rm -rf "$p"
      echo "  已清理残留目录: $p"
    fi
    cleaned=$((cleaned + 1))
  done < <(git worktree list --porcelain | awk '
    /^worktree / { p=$2; b="" }
    /^branch /   { b=$2; sub("^refs/heads/", "", b) }
    /^$/         { if (p != "") print p "\t" b; p=""; b="" }
    END          { if (p != "") print p "\t" b }
  ')

  if [ "$dry" = "0" ]; then
    git worktree prune
  fi

  echo ""
  if [ "$dry" = "1" ]; then
    echo "dry-run 汇总: 可清理 $cleaned 个 worktree，保留 $skipped 个"
  else
    echo "清理完成: 移除 $cleaned 个 worktree，保留 $skipped 个"
  fi
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
  echo "若 hook 用 dirname/\$0 回推，可能落到: ${hook_guess}（worktree 下常为错误主树）"
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
    clean) cmd_clean "$@" ;;
    doctor) cmd_doctor "$@" ;;
    -h|--help|help|"") usage 0 ;;
    *)
      echo "未知命令: $cmd" >&2
      usage 1
      ;;
  esac
}

main "$@"
