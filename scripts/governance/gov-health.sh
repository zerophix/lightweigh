#!/usr/bin/env bash
# scripts/governance/gov-health.sh
# 工作区/仓库健康总检（DEC-069 补充）：一个命令回答"现在有没有脏东西"。
#
# 设计目标：让"需要人提醒才发现"变成"每批自动检查"。
#   - 队列每批结束自动跑（wf-queue 内置）
#   - 会话开始 wf-autopilot status 会调用
#   - 任何 AI 也可以随时手动跑
#
# 用法:
#   gov-health.sh            # 只检查（异常时退出码 1）
#   gov-health.sh --fix      # 顺带安全清理（已合入 worktree/分支、孤儿目录、过期锁）
#   gov-health.sh --json
set -uo pipefail

DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT="${WF_AUTOPILOT_ROOT:-$(cd "$DIR/../.." && pwd)}"
cd "$ROOT" || exit 1

FIX=0; JSON=0
for a in "$@"; do
  case "$a" in
    --fix) FIX=1 ;;
    --json) JSON=1 ;;
  esac
done

git fetch -q origin 2>/dev/null || true

issues=()
oks=()

# 1) 任务 worktree 数量与残留
main="$(git rev-parse --show-toplevel 2>/dev/null)"
task_wt=0; merged_wt=0; orphans=0
while IFS= read -r line; do
  case "$line" in
    "worktree "*) wt="${line#worktree }" ;;
    "branch refs/heads/"*)
      br="${line#branch refs/heads/}"
      [ "$wt" = "$main" ] && continue
      case "$br" in chore/integration-*|chore/board-*|chore/fix-*) continue ;; esac
      case "$wt" in *-queue-*) continue ;; esac
      task_wt=$((task_wt + 1))
      git merge-base --is-ancestor "$br" origin/master 2>/dev/null && merged_wt=$((merged_wt + 1))
      ;;
  esac
done < <(git worktree list --porcelain)

repo_name="$(basename "$ROOT")"
for d in "$ROOT"/../"$repo_name"-*; do
  [ -d "$d" ] || continue
  # 注意：*-queue-* 也要查——未注册的队列候选是最常见的残留（2026-10-24 漏报 4 个）
  git worktree list --porcelain | grep -qx "worktree $(cd "$d" && pwd)" || orphans=$((orphans + 1))
done

[ "$task_wt" -gt 3 ] && issues+=("任务 worktree ${task_wt} 个（上限 3）")
[ "$merged_wt" -gt 0 ] && issues+=("有 ${merged_wt} 个已合入未删除的 worktree")
[ "$orphans" -gt 0 ] && issues+=("有 ${orphans} 个孤儿目录")
[ "$task_wt" -le 3 ] && [ "$merged_wt" -eq 0 ] && [ "$orphans" -eq 0 ] && oks+=("worktree 干净（${task_wt} 个任务）")

# 1b) 每个 worktree 必须带本机引导文件（绕过 ses-worktree.sh 手建树会漏）
missing_boot=0
while IFS= read -r line; do
  case "$line" in
    "worktree "*) wt="${line#worktree }" ;;
    "branch refs/heads/"*)
      [ "$wt" = "$main" ] && continue
      [ -f "$wt/local.properties" ] || { missing_boot=$((missing_boot + 1)); issues+=("$(basename "$wt") 缺 local.properties（未用 ses-worktree.sh 建树？）"); }
      [ -d "$wt/gradle/dists" ] || issues+=("$(basename "$wt") 缺 gradle/dists（离线构建会失败）")
      ;;
  esac
done < <(git worktree list --porcelain)

# 2) 已合入但未删的本地分支
stale_br=0
while read -r br; do
  [ -z "$br" ] && continue
  [ "$br" = "master" ] && continue
  git merge-base --is-ancestor "$br" origin/master 2>/dev/null && stale_br=$((stale_br + 1))
done < <(git branch --format='%(refname:short)')
[ "$stale_br" -gt 0 ] && issues+=("${stale_br} 个已合入的本地分支未删除")
[ "$stale_br" -eq 0 ] && oks+=("本地分支干净")

# 3) 主树是否干净 + master 是否同步
if git diff --quiet && [ -z "$(git ls-files --others --exclude-standard | head -1)" ]; then
  oks+=("主树干净")
else
  issues+=("主树有未提交改动")
fi
git fetch -q origin 2>/dev/null || true
[ "$(git rev-parse HEAD)" = "$(git rev-parse origin/master 2>/dev/null)" ] && oks+=("master 与远端同步") || issues+=("master 与 origin/master 不一致")

# 4) 验证证据新鲜度
ev=".cache/local-ci-last.json"
if [ -f "$ev" ]; then
  py="$(python3 - "$ev" <<'PY'
import json,sys,datetime
try:
    d=json.load(open(sys.argv[1]))
    ts=d.get('finished_at') or ''
    dt=datetime.datetime.fromisoformat(ts.replace('Z','+00:00'))
    age=(datetime.datetime.now(datetime.timezone.utc)-dt).total_seconds()/3600
    print(f"{age:.1f}")
except Exception:
    print("999")
PY
)"
  awk -v a="$py" 'BEGIN{exit !(a>24)}' && issues+=("local-ci 证据已过期 ${py}h") || oks+=("local-ci 证据新鲜（${py}h）")
else
  issues+=("缺少 local-ci 证据")
fi

# 5) 后台任务残留
oldjobs=0
[ -d .cache/jobs ] && oldjobs=$(find .cache/jobs -name '*.exit' -mtime +7 2>/dev/null | wc -l | tr -d ' ')
[ "$oldjobs" -gt 0 ] && issues+=("${oldjobs} 个 7 天前的后台任务日志")

# 6) 过期认领锁
if [ -x scripts/workflow/wf-claim.sh ]; then
  expired="$(bash scripts/workflow/wf-claim.sh status 2>/dev/null | grep -c "EXPIRED" || true)"
  [ "${expired:-0}" -gt 0 ] && issues+=("${expired} 个过期认领锁")
fi

# 7) 主干文档校验（秒级；pytest 由 local-ci 负责）
if ! bash scripts/governance/gov-validate.sh >/tmp/gov-health-validate.log 2>&1; then
  issues+=("gov-validate 未通过（见 /tmp/gov-health-validate.log）")
else
  oks+=("gov-validate 通过")
fi

if [ "$JSON" -eq 1 ]; then
  python3 - "$(printf '%s\n' "${oks[@]:-}")" "$(printf '%s\n' "${issues[@]:-}")" <<'PY'
import json,sys
o=[x for x in sys.argv[1].splitlines() if x.strip()]
i=[x for x in sys.argv[2].splitlines() if x.strip()]
print(json.dumps({"ok":o,"issues":i,"healthy":not i},ensure_ascii=False,indent=2))
PY
else
  echo "=== 健康检查 ==="
  for o in "${oks[@]:-}"; do [ -n "$o" ] && echo "  [OK] $o"; done
  for i in "${issues[@]:-}"; do [ -n "$i" ] && echo "  [问题] $i"; done
fi

if [ "${#issues[@]}" -eq 0 ]; then
  [ "$JSON" -eq 0 ] && echo "健康"
  exit 0
fi

if [ "$FIX" -eq 1 ]; then
  echo "--- 自动修复（只处理安全项）---"
  bash scripts/session/ses-worktree.sh clean 2>/dev/null | sed 's/^/  /' || true
  while read -r br; do
    [ -z "$br" ] && continue; [ "$br" = "master" ] && continue
    git merge-base --is-ancestor "$br" origin/master 2>/dev/null && git branch -D "$br" >/dev/null 2>&1
  done < <(git branch --format='%(refname:short)')
  for d in "$ROOT"/../"$repo_name"-*; do
    [ -d "$d" ] || continue
    git worktree list --porcelain | grep -qx "worktree $(cd "$d" && pwd)" || rm -rf "$d"
  done
  git worktree prune 2>/dev/null
  [ -x scripts/workflow/wf-claim.sh ] && bash scripts/workflow/wf-claim.sh gc >/dev/null 2>&1 || true
  [ -d .cache/jobs ] && find .cache/jobs -name '*.log' -mtime +7 -delete 2>/dev/null || true
  echo "  修复完成，重新检查："
  SELF="$(cd "$(dirname "$0")" && pwd)/$(basename "$0")"
  exec bash "$SELF"
fi

[ "$JSON" -eq 0 ] && echo "修复: bash scripts/governance/gov-health.sh --fix"
exit 1
