#!/usr/bin/env bash
# scripts/dev/merge-mine.sh
# 把当前分支合进目标分支（默认 master），带自动 rebase + 重试。
#
# 为什么需要它：多 AI 同时推 master，后到的会被"非快进"拒绝。这不是冲突，
# 只是基线旧了。脚本会自动 fetch + rebase + 重推，只有真冲突才停下来。
#
# 用法:
#   scripts/dev/merge-mine.sh [--to master] [--check] [--branch-only] [--dry-run] [--no-fetch]
#   --check        推送前跑一次 :app:assembleDebug（默认跳过，求快）
#   --branch-only  只推分支，不动目标分支（单写者模式用）
#   --dry-run      只 rebase 和打印，不推送
#   --no-fetch     不 fetch，直接用当前 origin/<目标>（离线/测试用）
#
# 退出码: 0 成功 / 2 冲突或前置条件不满足 / 3 重试耗尽
set -uo pipefail

TARGET=master
CHECK=0
BRANCH_ONLY=0
DRY=0
FETCH=1
ATTEMPTS=5

while [ $# -gt 0 ]; do
  case "$1" in
    --to) TARGET="$2"; shift 2 ;;
    --check) CHECK=1; shift ;;
    --branch-only) BRANCH_ONLY=1; shift ;;
    --dry-run) DRY=1; shift ;;
    --no-fetch) FETCH=0; shift ;;
    -h|--help) sed -n '2,18p' "$0"; exit 0 ;;
    *) echo "未知参数: $1" >&2; exit 2 ;;
  esac
done

cd "$(git rev-parse --show-toplevel)" || exit 2
BRANCH="$(git branch --show-current)"
[ -n "$BRANCH" ] || { echo "分离 HEAD 状态，先切到分支" >&2; exit 2; }
[ "$BRANCH" != "$TARGET" ] || { echo "当前就在 ${TARGET} 上，先切分支" >&2; exit 2; }

if [ -n "$(git status --porcelain)" ]; then
  echo "工作区有未提交改动，先提交或 stash：$(git status --porcelain | wc -l | tr -d ' ') 个文件" >&2
  exit 2
fi

run_check() {
  [ "$CHECK" -eq 1 ] || return 0
  [ -x ./gradlew ] || return 0
  echo "[check] :app:assembleDebug ..."
  JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}" \
    ./gradlew :app:assembleDebug --max-workers=4 -q || {
      echo "[check] 编译失败，已停止（未推送）" >&2; exit 2; }
}

for attempt in $(seq 1 "$ATTEMPTS"); do
  echo "[$attempt/$ATTEMPTS] rebase onto origin/${TARGET}"
  if [ "$FETCH" -eq 1 ]; then
    git fetch origin --quiet || { echo "fetch 失败" >&2; exit 2; }
  fi

  if ! git rebase "origin/${TARGET}" >/tmp/merge-mine-rebase.log 2>&1; then
    echo "发现真实冲突（需要人工处理），涉及文件：" >&2
    git diff --name-only --diff-filter=U >&2
    git rebase --abort >/dev/null 2>&1 || true
    echo "处理方式：手动 git rebase origin/${TARGET}，解决后重跑本脚本" >&2
    exit 2
  fi

  run_check

  if [ "$DRY" -eq 1 ]; then
    echo "[dry-run] 已 rebase 到 origin/${TARGET}，未推送（HEAD=$(git rev-parse --short HEAD)）"
    exit 0
  fi

  if [ "$BRANCH_ONLY" -eq 1 ]; then
    if git push origin "HEAD:refs/heads/${BRANCH}"; then
      echo "分支已推送: ${BRANCH}"
      exit 0
    fi
    echo "分支推送被拒（远端分支已变），重试" >&2
  else
    if git push origin "HEAD:refs/heads/${TARGET}" 2>/tmp/merge-mine-push.log; then
      echo "已合入 ${TARGET}: $(git rev-parse --short HEAD) ← ${BRANCH}"
      exit 0
    fi
    if grep -qi "non-fast-forward\|fetch first\|rejected" /tmp/merge-mine-push.log; then
      echo "目标分支被别人推进了，重新 rebase 后重试"
    else
      echo "推送失败：" >&2
      tail -5 /tmp/merge-mine-push.log >&2
      exit 2
    fi
  fi

  sleep $(( RANDOM % 4 + 1 ))
done

echo "重试 ${ATTEMPTS} 次仍未推上，请手动检查" >&2
exit 3
