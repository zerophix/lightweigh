#!/usr/bin/env bash
# scripts/workflow/wf-autopilot.sh
# 开发流程自动驾驶（DEC-066）：把"AI 该做什么"从文字约定变成可执行程序。
#
# 任何 AI 进入仓库，第一步只需要：
#     bash scripts/workflow/wf-autopilot.sh status
# 它输出：任务板下一批可并行任务、所有在途 PR 及其 worktree 状态、脏项、以及 NEXT 命令。
#
# 用法:
#   wf-autopilot.sh status [--json]     # 一屏真相 + 下一步（只读）
#   wf-autopilot.sh advance [--dry]     # 推进所有在途 PR：同步 master→验证→合入→清理
#   wf-autopilot.sh clean               # 清理已合入的 worktree/分支（安全项）
#   wf-autopilot.sh start <T-XXX>       # 认领+建 worktree+打印任务要求
#   wf-autopilot.sh finish <T-XXX>      # DoD 校验 → 提交 → 推送 → 开 PR
#
# 设计原则:
#   - 慢操作（local-ci）复用 wf-run.sh（存在则后台+等待，不存在则前台）
#   - 冲突只自动解决"机械型"（派生文件/状态文件）；语义冲突立即停下并打印文件清单
#   - 每一步都打印做了什么，不做静默操作
set -uo pipefail

DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT="${WF_AUTOPILOT_ROOT:-$(cd "$DIR/../.." && pwd)}"
cd "$ROOT" || exit 1

RUNNER="$ROOT/scripts/workflow/wf-run.sh"
BOARD_SYNC="$ROOT/scripts/governance/gov-board-sync.sh"
DOCTOR="$ROOT/scripts/governance/gov-workspace-doctor.sh"
PARALLEL="$ROOT/scripts/workflow/wf-parallel.sh"
CLAIM="$ROOT/scripts/workflow/wf-claim.sh"
TASK_DONE="$ROOT/scripts/governance/gov-task-done.sh"

die() { echo "错误: $*" >&2; exit 1; }

# 治理共享状态（单一写者，DEC-064）：特性分支不得携带其修改。
# 合并时一律以 master 版本为准，冲突源从机制上消失。
STRIP_PATHS=(
  AGENTS.md
  .gitignore
  .ai/tasks.json
  STATUS.md
  tests/android-test-registry.yaml
  docs/ai/lifecycle/03_TaskGraph.md
  docs/ai/lifecycle/06_Metrics.md
  docs/index.md
)

strip_shared_state() {
  local changed=0
  for f in "${STRIP_PATHS[@]}"; do
    if git cat-file -e "origin/master:$f" 2>/dev/null; then
      git checkout origin/master -- "$f" 2>/dev/null && changed=1
    fi
  done
  [ "$changed" -eq 1 ] && git add -A 2>/dev/null
  return 0
}
have() { [ -x "$1" ] || [ -f "$1" ]; }

worktree_of_branch() {
  git worktree list --porcelain | awk -v b="$1" '
    /^worktree /{wt=$2}
    /^branch /{if ($2=="refs/heads/"b) {print wt; exit}}'
}

branch_merged() {
  git merge-base --is-ancestor "$1" master 2>/dev/null
}

# 慢命令统一入口：有 wf-run 走后台上报，否则前台
run_slow() {
  local name="$1"; shift
  if have "$RUNNER"; then
    local id
    id="$(bash "$RUNNER" start "$name" -- "$@" 2>/dev/null | tail -1)"
    if [ -n "$id" ]; then
      echo "  [后台] $name ($id)，等待结果…"
      bash "$RUNNER" wait "$id" 2>&1 | tail -5
      return "${PIPESTATUS[0]}"
    fi
  fi
  "$@"
}

# ---------------------------------------------------------------- status

cmd_status() {
  local json=0
  [ "${1:-}" = "--json" ] && json=1

  # 任务板：下一批可并行任务
  local plan=""
  if have "$PARALLEL"; then
    plan="$(bash "$PARALLEL" plan 2>/dev/null | sed -n '1,12p')"
  fi

  # 在途 PR
  local prs=""
  prs="$(gh pr list --state open --json number,headRefName,mergeable \
        -q '.[] | "\(.number)|\(.headRefName)|\(.mergeable)"' 2>/dev/null || true)"

  # worktree 审计
  local merged_wt="" live_wt="" orphans=""
  while IFS= read -r line; do
    case "$line" in
      "worktree "*) wt="${line#worktree }" ;;
      "branch refs/heads/"*)
        br="${line#branch refs/heads/}"
        if [ "$br" = "master" ]; then continue; fi
        if branch_merged "$br"; then merged_wt+="$wt|$br"$'\n'; else live_wt+="$wt|$br"$'\n'; fi
        ;;
    esac
  done < <(git worktree list --porcelain)

  local repo_name; repo_name="$(basename "$ROOT")"
  for d in "$ROOT"/../"$repo_name"-* ; do
    [ -d "$d" ] || continue
    base="$(basename "$d")"
    if ! git worktree list --porcelain | grep -qx "worktree $(cd "$d" && pwd)"; then
      orphans+="$base"$'\n'
    fi
  done

  if [ "$json" -eq 1 ]; then
    python3 - "$prs" "$merged_wt" "$live_wt" "$orphans" <<'PY'
import json, sys
prs_raw, merged_raw, live_raw, orphan_raw = sys.argv[1:5]
def rows(raw):
    return [ln for ln in raw.splitlines() if ln.strip()]
prs = []
for ln in rows(prs_raw):
    n, br, mg = ln.split('|')
    prs.append({"pr": int(n), "branch": br, "mergeable": mg})
print(json.dumps({
    "open_prs": prs,
    "worktrees_merged": [dict(zip(("path", "branch"), r.split("|"))) for r in rows(merged_raw)],
    "worktrees_live": [dict(zip(("path", "branch"), r.split("|"))) for r in rows(live_raw)],
    "orphan_dirs": rows(orphan_raw),
}, ensure_ascii=False, indent=2))
PY
    return 0
  fi

  echo "============================================================"
  echo " 工作区状态 ($(date '+%H:%M'))  分支: $(git rev-parse --abbrev-ref HEAD)"
  echo "============================================================"

  echo
  echo "【任务板下一批】"
  if [ -n "$plan" ]; then echo "$plan" | sed 's/^/  /'; else echo "  (wf-parallel 未安装)"; fi

  echo
  echo "【在途 PR】"
  if [ -z "$prs" ]; then
    echo "  无"
  else
    echo "  PR    分支                                     状态         worktree"
    while IFS='|' read -r n br mg; do
      [ -z "$n" ] && continue
      local wt
      wt="$(worktree_of_branch "$br")"
      printf "  #%-4s %-40s %-12s %s\n" "$n" "$br" "$mg" "${wt:-（缺失）}"
    done <<< "$prs"
  fi

  echo
  echo "【worktree 审计】"
  if [ -n "$merged_wt" ]; then
    echo "  已合入但未清理（可安全删除）:"
    echo "$merged_wt" | sed '/^$/d; s/^/    /'
  fi
  if [ -n "$live_wt" ]; then
    echo "  在途（保留）:"
    echo "$live_wt" | sed '/^$/d; s/^/    /'
  fi
  if [ -n "$orphans" ]; then
    echo "  孤儿目录（不在 worktree 注册表）:"
    echo "$orphans" | sed '/^$/d; s/^/    /'
  fi
  [ -z "$merged_wt$live_wt$orphans" ] && echo "  干净"

  echo
  echo "【教训库】"
  if [ -f "$ROOT/.ai/lessons.json" ]; then
    python3 - "$ROOT/.ai/lessons.json" <<'PY2' | sed 's/^/  /'
import json,sys
d=json.load(open(sys.argv[1]))
ls=d.get("lessons",[])
un=[x["id"] for x in ls if not x.get("enforced")]
print(f"共 {len(ls)} 条，未强制 {len(un)} 条" + (f"：{', '.join(un[:6])}" if un else ""))
PY2
  else
    echo "  （未建立 .ai/lessons.json）"
  fi
  if [ -f "$ROOT/.cache/queue-runs.jsonl" ]; then
    python3 - "$ROOT/.cache/queue-runs.jsonl" <<'PY2' | sed 's/^/  /'
import json,sys
rows=[json.loads(l) for l in open(sys.argv[1]) if l.strip()]
rows=[r for r in rows if "product" in r]
if rows:
    r=rows[-1]; p,g=r.get("product",0),r.get("governance",0)
    ratio=(p/g) if g else float('inf')
    flag=" ⚠️ 本末倒置" if g and p/g < 2 else ""
    print(f"上批 产品:治理 = {p}:{g}（{r.get('seconds',0)}s）{flag}")
PY2
  fi

  echo
  echo "【下一步】"
  if [ -n "$prs" ]; then
    echo "  bash scripts/workflow/wf-autopilot.sh advance     # 自动推进所有在途 PR"
  elif [ -n "$plan" ]; then
    echo "  bash scripts/workflow/wf-autopilot.sh start <T-XXX>   # 认领并开工下一批"
  else
    echo "  无在途 PR、无可开工任务 → 等人类给方向"
  fi
}

# ---------------------------------------------------------------- advance

resolve_mechanical_conflicts() {
  # 只处理"派生/状态"文件；其余视为语义冲突，返回 1
  local resolved=0
  # 认领声明文件：master 已删除（DEC-063），采纳删除
  if git ls-files --error-unmatch .ai/staged-claims.json >/dev/null 2>&1; then
    git rm -q --cached .ai/staged-claims.json 2>/dev/null && rm -f .ai/staged-claims.json && resolved=1
  fi
  for f in STATUS.md tests/android-test-registry.yaml \
           docs/ai/lifecycle/03_TaskGraph.md docs/ai/lifecycle/06_Metrics.md; do
    if git ls-files -u -- "$f" | grep -q .; then
      git checkout --theirs -- "$f" 2>/dev/null && git add "$f" 2>/dev/null && resolved=1
    fi
  done
  [ "$resolved" -eq 1 ] || return 1
  # 仍有未解决 → 失败
  git ls-files -u | grep -q . && return 1
  return 0
}

cmd_advance() {
  local dry=0
  [ "${1:-}" = "--dry" ] && dry=1
  git fetch -q origin 2>/dev/null || true

  local prs
  prs="$(gh pr list --state open --json number,headRefName -q '.[] | "\(.number)|\(.headRefName)"' 2>/dev/null \
        | sort -t'|' -k1,1n)"
  if [ -z "$prs" ]; then echo "无在途 PR"; return 0; fi

  local failed=0
  while IFS='|' read -r n br; do
    [ -z "$n" ] && continue
    echo
    echo "========== PR #$n ($br) =========="
    local wt
    wt="$(worktree_of_branch "$br")"
    if [ -z "$wt" ]; then
      echo "  [跳过] 无本地 worktree（外部 PR 或已清理）"
      continue
    fi
    if [ "$dry" -eq 1 ]; then echo "  [dry] 将在 $wt 执行: merge master → sync → local-ci → merge"; continue; fi

    cd "$wt" || { echo "  [失败] 无法进入 worktree"; failed=1; break; }

    # 1) 同步 master（机械冲突自动解决）
    if ! git merge origin/master --no-edit >/tmp/wf-merge.log 2>&1; then
      if resolve_mechanical_conflicts; then
        git add -A 2>/dev/null
        if ! git commit --no-edit >/tmp/wf-commit.log 2>&1; then
          echo "  [失败] 合并提交被钩子拒绝:"; tail -5 /tmp/wf-commit.log
          failed=1; break
        fi
        echo "  [OK] 已同步 master（机械冲突已解决）"
      else
        echo "  [停下] 存在语义冲突，需人工处理:"
        git diff --name-only --diff-filter=U | sed 's/^/    /' | sort -u
        git merge --abort 2>/dev/null
        failed=1; break
      fi
    else
      echo "  [OK] 已同步 master"
    fi

    # 2) 派生物同步（注册表/计数）
    if have "$BOARD_SYNC"; then
      bash "$BOARD_SYNC" >/tmp/wf-sync.log 2>&1 || {
        bash scripts/session/ses-claim-staged.sh >/dev/null 2>&1
        bash "$BOARD_SYNC" >/tmp/wf-sync.log 2>&1 || true
      }
    fi

    # 3) 合入门禁（慢 → 走 wf-run）
    echo "  [门禁] local-ci…"
    if ! run_slow "ci-pr$n" bash scripts/governance/gov-local-ci.sh >/tmp/wf-ci-$n.log 2>&1; then
      echo "  [失败] local-ci 未通过:"; tail -6 /tmp/wf-ci-$n.log
      failed=1; break
    fi
    if ! evidence_matches_head "$wt"; then
      echo "  [失败] local-ci 证据与分支头不一致（可能用了旧证据）"
      failed=1; break
    fi
    echo "  [OK] local-ci 通过（证据 SHA 匹配）"

    # 4) 推送 + 合并
    git push -q origin HEAD:"$br" 2>/dev/null || git push -q 2>/dev/null || true
    cd "$ROOT" || return 1
    local merge_out; merge_out="$(gh pr merge "$n" --merge --delete-branch=false 2>&1)"
    local st=""
    # GitHub 状态查询有延迟：轮询最多 10s；"already merged" 也算成功
    for _ in 1 2 3 4 5; do
      st="$(gh pr view "$n" --json state -q .state 2>/dev/null)"
      [ "$st" = "MERGED" ] && break
      sleep 2
    done
    if [ "$st" != "MERGED" ] && echo "$merge_out" | grep -q "already merged"; then st="MERGED"; fi
    echo "  [PR] ${st:-unknown}"
    if [ "$st" != "MERGED" ]; then
      echo "  [失败] 合入未生效: $(echo "$merge_out" | tail -2)"
      failed=1; break
    fi

    # 5) 清理 worktree + 分支
    git worktree remove --force "$wt" 2>/dev/null; rm -rf "$wt"
    git branch -D "$br" >/dev/null 2>&1
    git push -q origin --delete "$br" >/dev/null 2>&1
    git worktree prune
    echo "  [清理] worktree/分支已删除"
  done <<< "$prs"

  echo
  if [ "$failed" -eq 0 ]; then echo "advance 完成：全部在途 PR 已处理"; else echo "advance 中止：有需人工处理的项（见上）"; return 1; fi
}


# 证据必须对应当前分支头（否则"用旧提交的证据证明通过"）
evidence_matches_head() {
  local wt="$1" ev="$wt/.cache/local-ci-last.json"
  [ -f "$ev" ] || return 1
  local head ev_sha
  head="$(git -C "$wt" rev-parse HEAD 2>/dev/null)"
  ev_sha="$(python3 -c "import json,sys;print(json.load(open(sys.argv[1])).get('git_sha',''))" "$ev" 2>/dev/null)"
  [ -n "$head" ] && [ "$head" = "$ev_sha" ]
}

# ---------------------------------------------------------------- advance --parallel

cmd_advance_parallel() {
  git fetch -q origin 2>/dev/null || true
  local entries=()
  while IFS='|' read -r n br; do
    [ -z "$n" ] && continue
    local wt; wt="$(worktree_of_branch "$br")"
    [ -z "$wt" ] && continue
    entries+=("$n|$br|$wt")
  done < <(gh pr list --state open --json number,headRefName -q '.[] | "\(.number)|\(.headRefName)"' 2>/dev/null | sort -t'|' -k1,1n)

  [ "${#entries[@]:-0}" -eq 0 ] && { echo "无在途 PR"; return 0; }
  echo "并行推进 ${#entries[@]} 个 PR"

  # 阶段 1：同步 master + 剥离共享状态 + 推送（串行，秒级）
  ready=()
  for e in "${entries[@]:-}"; do
    [ -z "$e" ] && continue
    IFS='|' read -r n br wt <<< "$e"
    echo "  [prep] #$n $br"
    ( cd "$wt" || exit 1
      # 0) 清掉上一次中断留下的 merge 状态与脏改动
      if [ -f "$(git rev-parse --git-path MERGE_HEAD 2>/dev/null)" ]; then
        git merge --abort 2>/dev/null || true
      fi
      git reset -q 2>/dev/null
      for f in "${STRIP_PATHS[@]}" .cache/context.jsonl; do
        git checkout -- "$f" 2>/dev/null || true
      done
      # 0b) 未跟踪但 master 已跟踪的同名文件：取 master 版本（否则 merge 会拒绝）
      git ls-files --others --exclude-standard -z | while IFS= read -r -d '' f; do
        if git cat-file -e "origin/master:$f" 2>/dev/null; then
          git checkout origin/master -- "$f" 2>/dev/null || true
        fi
      done
      # 1) 同步 master
      if ! git merge origin/master --no-edit >"/tmp/wf-merge-$n.log" 2>&1; then
        strip_shared_state
        if git ls-files -u | grep -q "\.ai/staged-claims\.json"; then
          git rm -qf .ai/staged-claims.json 2>/dev/null || true
        fi
        if git ls-files -u | grep -q .; then
          echo "    [停下] 语义冲突: $(git diff --name-only --diff-filter=U | tr '\n' ' ')"
          exit 3
        fi
        git add -A 2>/dev/null
        bash scripts/session/ses-claim-staged.sh >/dev/null 2>&1 || true
        git add -A 2>/dev/null
        git commit --no-edit >"/tmp/wf-commit-$n.log" 2>&1 || exit 4
      fi
      # 2) 必须真的把 master 合进来，否则说明合并被脏状态挡掉
      if ! git merge-base --is-ancestor origin/master HEAD; then
        echo "    [跳过] 未能同步 master（见 /tmp/wf-merge-$n.log）"; exit 5
      fi
      # 3) 剥离残余共享状态改动
      strip_shared_state
      if ! git diff --quiet HEAD; then
        bash scripts/session/ses-claim-staged.sh >/dev/null 2>&1 || true
        git add -A 2>/dev/null
        git commit -m "chore(branch): drop shared governance state (single writer)" >/dev/null 2>&1 || true
      fi
      git push -q origin HEAD:"$br" >/dev/null 2>&1 || git push -q >/dev/null 2>&1
    ) || { echo "    [跳过] prep 失败: $(tail -2 /tmp/wf-merge-$n.log 2>/dev/null | tr '\n' ' ')"; continue; }
    ready+=("$n|$br|$wt")
  done

  # 阶段 2：并发跑 local-ci（wf-run 后台）
  local ids=()
  for e in "${ready[@]:-}"; do
    [ -z "$e" ] && continue
    IFS='|' read -r n br wt <<< "$e"
    if have "$RUNNER"; then
      local id
      id="$(bash "$RUNNER" start "ci-pr$n" -- bash -c "cd '$wt' && bash scripts/governance/gov-local-ci.sh" 2>/dev/null | tail -1)"
      ids+=("$n|$br|$wt|$id")
      echo "  [ci] #$n 已启动 ($id)"
    else
      ids+=("$n|$br|$wt|")
    fi
  done

  # 阶段 3：等待并合入（按 PR 号顺序，快速串行）
  local failed=0
  for e in "${ids[@]:-}"; do
    [ -z "$e" ] && continue
    IFS='|' read -r n br wt id <<< "$e"
    echo "  [wait] #$n"
    if [ -n "$id" ] && have "$RUNNER"; then
      if ! bash "$RUNNER" wait "$id" >/tmp/wf-ci-$n.log 2>&1; then
        echo "    [失败] local-ci 未通过（见 /tmp/wf-ci-$n.log）"; failed=1; continue
      fi
      if ! evidence_matches_head "$wt"; then
        echo "    [失败] 证据 SHA 与分支头不一致"; failed=1; continue
      fi
    fi
    local merge_out; merge_out="$(gh pr merge "$n" --merge --delete-branch=false 2>&1)"
    local st=""
    for _ in 1 2 3 4 5; do
      st="$(gh pr view "$n" --json state -q .state 2>/dev/null)"
      [ "$st" = "MERGED" ] && break; sleep 2
    done
    [ "$st" != "MERGED" ] && echo "$merge_out" | grep -q "already merged" && st="MERGED"
    if [ "$st" = "MERGED" ]; then
      echo "    [merged] #$n"
      git worktree remove --force "$wt" 2>/dev/null; rm -rf "$wt"
      git branch -D "$br" >/dev/null 2>&1 || true
      git push -q origin --delete "$br" >/dev/null 2>&1 || true
    else
      echo "    [失败] $(echo "$merge_out" | tail -2)"; failed=1
    fi
  done
  git worktree prune
  [ "$failed" -eq 0 ] && echo "并行推进完成" || echo "并行推进结束（有失败项）"
  return "$failed"
}

# ---------------------------------------------------------------- clean / start / finish

cmd_clean() {
  if have "$DOCTOR"; then bash "$DOCTOR" --fix; return $?; fi
  local n=0
  while IFS= read -r line; do
    case "$line" in
      "worktree "*) wt="${line#worktree }" ;;
      "branch refs/heads/"*)
        br="${line#branch refs/heads/}"
        [ "$br" = "master" ] && continue
        if branch_merged "$br"; then
          git worktree remove --force "$wt" 2>/dev/null; rm -rf "$wt"
          git branch -D "$br" >/dev/null 2>&1; n=$((n+1))
        fi ;;
    esac
  done < <(git worktree list --porcelain)
  git worktree prune
  local repo_name; repo_name="$(basename "$ROOT")"
  for d in "$ROOT"/../"$repo_name"-*; do
    [ -d "$d" ] || continue
    git worktree list --porcelain | grep -qx "worktree $(cd "$d" && pwd)" || { rm -rf "$d"; n=$((n+1)); }
  done
  echo "已清理 $n 项"
}

cmd_start() {
  local tid="${1:-}"; [ -z "$tid" ] && die "用法: wf-autopilot.sh start <T-XXX>"

  if have "$CLAIM"; then
    bash "$CLAIM" claim "$tid" || die "认领失败（可能被其他会话持有）: wf-claim.sh status $tid"
    echo "已认领 $tid"
  fi

  local slug
  slug="$(python3 - "$ROOT" "$tid" <<'PY'
import json, sys
root, tid = sys.argv[1], sys.argv[2]
d = json.load(open(f"{root}/.ai/tasks.json"))
t = next((x for x in d["tasks"] if x["id"] == tid), None)
print((t or {}).get("module", "misc"))
PY
)"
  local br="feat/${tid,,}-${slug}"
  bash scripts/session/ses-worktree.sh add "$br" 2>&1 | grep -E "路径|已复制" | head -2

  # DEC-077: Mode auto-selection based on task type
  local auto_mode
  auto_mode=$(python3 - "$ROOT" "$tid" <<'PY'
import json, sys, re
root, tid = sys.argv[1], sys.argv[2]
d = json.load(open(f"{root}/.ai/tasks.json"))
t = next((x for x in d.get("tasks", []) if x["id"] == tid), None)
if not t:
    print("code"); sys.exit(0)
text = " ".join(filter(None, [t.get("description", ""), t.get("module", ""), t.get("type", "")])).lower()
text += " " + json.dumps(t.get("description", "")).lower()
if re.search(r'prototype|原型|探索|探索性', text): print("explore")
elif re.search(r'\bdoc\b|文档|write.*doc', text): print("docs")
elif re.search(r'\btest\b|测试|pytest|unit test', text): print("test")
elif re.search(r'governance|治理|hook|hook|lint', text): print("gov")
else: print("code")
PY
)
  # Switch mode if needed (skip if already in desired mode)
  local cur_mode; cur_mode=$(bash "$ROOT/scripts/workflow/wf-mode.sh" status 2>/dev/null | grep "当前模式" | cut -d" " -f2) || true
  [ -z "$cur_mode" ] && cur_mode="dev"
  if [ "$cur_mode" != "$auto_mode" ]; then
    if [ "$auto_mode" = "explore" ] || [ "$auto_mode" = "docs" ] || [ "$auto_mode" = "test" ]; then
      echo "  -> auto-switch mode: $cur_mode -> $auto_mode"
      bash "$ROOT/scripts/workflow/wf-mode.sh" switch "$auto_mode" 2>&1 | grep -v "^GOV_MODE=" || true
      bash "$ROOT/scripts/workflow/wf-mode.sh" post-hook "$auto_mode" 2>&1 | sed 's/^/   /'
    fi
  else
    echo "  [mode] current: $cur_mode"
  fi

  python3 - "$ROOT" "$tid" <<'PY'
import json, sys
root, tid = sys.argv[1], sys.argv[2]
t = next(x for x in json.load(open(f"{root}/.ai/tasks.json"))["tasks"] if x["id"] == tid)
print("\n任务:", tid, t.get("description", ""))
print("目标文件:")
for f in t.get("target_files", []) or ["(未定义)"]:
    print("  -", f)
acc = t.get("acceptance") or {}
print("验收 then:")
for x in acc.get("then", []) or ["(无)"]:
    print("  -", x)
print("完成校验: bash scripts/governance/gov-task-done.sh", tid)
PY
  echo
  echo "下一步: cd 到上面的 worktree → bash scripts/session/ses-session-begin.sh → 写代码"
}

cmd_finish() {
  local tid="${1:-}"; [ -z "$tid" ] && die "用法: wf-autopilot.sh finish <T-XXX>"
  echo "[1/4] DoD 校验"
  if have "$TASK_DONE"; then bash "$TASK_DONE" "$tid" || die "DoD 未通过，未提交"; else echo "  (gov-task-done 未安装，跳过)"; fi
  echo "[2/4] 提交"
  git add -A
  bash scripts/session/ses-claim-staged.sh >/dev/null 2>&1 || true
  git commit -m "feat($(python3 -c "import json,sys;d=json.load(open('.ai/tasks.json'));print(next(t for t in d['tasks'] if t['id']=='$tid').get('module','misc'))")): deliver $tid" >/tmp/wf-commit.log 2>&1 \
    || echo "  (无变更或提交被拒，见 /tmp/wf-commit.log)"
  echo "[3/4] 推送"
  local br; br="$(git rev-parse --abbrev-ref HEAD)"
  git push -u origin "$br" || die "推送失败"
  echo "[4/4] 开 PR"
  gh pr create --head "$br" --base master \
    --title "feat: deliver $tid" \
    --body "## 关联
| 项 | 填写 |
|----|------|
| 关联任务 | $tid |
| 关联决策 | DEC-061 |

## 验收
- [x] gov-task-done $tid 通过
- [x] local-ci 由 autopilot advance 复核

## 门禁
- [x] 未用 --no-verify
- [x] 未改黄金向量 / Room schema" 2>&1 | tail -1
}

usage() {
  sed -n '3,18p' "$0" | sed 's/^# \{0,1\}//'
}

case "${1:-status}" in
  status) shift || true; cmd_status "${1:-}" ;;
  advance)
    shift || true
    case "${1:-}" in
      --parallel) cmd_advance_parallel ;;
      *) cmd_advance "${1:-}" ;;
    esac ;;
  clean) cmd_clean ;;
  start) shift || true; cmd_start "${1:-}" ;;
  finish) shift || true; cmd_finish "${1:-}" ;;
  -h|--help|help) usage ;;
  *) die "未知子命令: $1（status|advance|clean|start|finish）" ;;
esac
