#!/usr/bin/env bash
# scripts/governance/gov-workspace-doctor.sh
# 工作区医生（DEC-065）：审计 worktree / 分支 / 认领锁 / 后台日志 / 证据新鲜度，
# 让任何一个 AI 进来自动跑一次就知道「本地有没有脏东西、能不能安全清理」。
#
# 用法:
#   bash scripts/governance/gov-workspace-doctor.sh            # 只读审计
#   bash scripts/governance/gov-workspace-doctor.sh --fix      # 顺带清理安全项
#   bash scripts/governance/gov-workspace-doctor.sh --json     # 结构化输出
#   bash scripts/governance/gov-workspace-doctor.sh --strict   # WARN 也返回 1
#   bash scripts/governance/gov-workspace-doctor.sh --network  # 额外审计远端分支（会 fetch）
#
# 检查项（每项 [OK]/[WARN]/[FAIL] + 一行说明）:
#   1. worktree   已合入 trunk / 注册但目录不存在 / 兄弟目录孤儿残留
#   2. 本地分支   除 trunk 外已合入 trunk 的分支
#   3. 远端分支   --network 时 fetch --prune 后已合入 trunk 的 origin/*
#   4. 认领锁     .ai/locks/* 超过 TTL 或 owner session 不存在；.ai/task-lock.json 过期项
#   5. 后台任务   .cache/jobs/*.log 超过 7 天且对应 .exit 存在
#   6. 证据新鲜度 .cache/local-ci-last.json 的 finished_at 超过 24h
#   7. trunk 同步 本地 trunk 落后 origin/trunk
#
# 退出码: 有 FAIL -> 1；仅 WARN -> 0（--strict 时 WARN 也返回 1）
#
# --fix 只处理安全项（不确定的只报告不删）:
#   - 已合入且工作树干净的 worktree（ses-worktree.sh clean，退化为 remove）
#   - 已合入的本地分支（git branch -d，不强制）
#   - 过期认领锁（wf-claim.sh gc，若不存在则本地剪枝）
#   - 确认属于本仓库命名模式的孤儿残留目录（空目录或 .git 指向本仓库 worktrees）
#   - git worktree prune
#   - 超过 7 天且已完成的 .cache/jobs 日志
set -euo pipefail

SCRIPT_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"

exec python3 - "$SCRIPT_ROOT" "$@" <<'PY'
# -*- coding: utf-8 -*-
"""工作区医生主逻辑（内嵌 python，便于 JSON 输出与时间解析）。"""

import json
import os
import shutil
import subprocess
import sys
import time
from datetime import datetime, timezone
from pathlib import Path

SCRIPT_ROOT = Path(sys.argv[1]).resolve()
RAW_ARGS = sys.argv[2:]

GIT_ENV_STRIP = (
    "GIT_DIR",
    "GIT_WORK_TREE",
    "GIT_COMMON_DIR",
    "GIT_INDEX_FILE",
    "GIT_PREFIX",
)

USAGE = """用法: bash scripts/governance/gov-workspace-doctor.sh [选项]

选项:
  --fix              自动清理安全项（保守：不确定的只报告不删）
  --json             结构化输出（机器可读）
  --strict           WARN 也返回退出码 1（供 CI / 会话收尾使用）
  --network          额外审计远端分支（会执行 git fetch --prune，较慢）
  --ttl-hours <小时> 认领锁 TTL（默认 24）
  --jobs-days <天>   后台日志保留天数（默认 7）
  --help             显示此帮助
"""


# ---------------------------------------------------------------- 基础工具


class Ctx(object):
    """运行时上下文：仓库根、主工作树、trunk。"""

    def __init__(self):
        self.root = None
        self.main_root = None
        self.trunk = ""
        self.base_ref = ""
        self.repo_name = ""

    def state_roots(self):
        """本地态（锁/缓存）可能分散在当前树与主树，两处都看。"""
        out = []
        for base in (self.root, self.main_root):
            if base is None:
                continue
            p = Path(base).resolve()
            if p not in out:
                out.append(p)
        return out


CTX = Ctx()


def git(args, cwd=None, timeout=60):
    """统一的 git 调用：剥离宿主钩子注入的 GIT_* 变量，显式 -C 定位。"""
    env = {k: v for k, v in os.environ.items() if k not in GIT_ENV_STRIP}
    return subprocess.run(
        ["git", *args],
        cwd=str(cwd or CTX.root),
        capture_output=True,
        text=True,
        env=env,
        timeout=timeout,
    )


def parse_args(argv):
    opts = {
        "fix": False,
        "json": False,
        "strict": False,
        "network": False,
        "ttl_hours": 24.0,
        "jobs_days": 7.0,
        "help": False,
    }
    i = 0
    while i < len(argv):
        a = argv[i]
        if a in ("-h", "--help"):
            opts["help"] = True
            i += 1
        elif a == "--fix":
            opts["fix"] = True
            i += 1
        elif a == "--json":
            opts["json"] = True
            i += 1
        elif a == "--strict":
            opts["strict"] = True
            i += 1
        elif a == "--network":
            opts["network"] = True
            i += 1
        elif a in ("--ttl-hours", "--ttl"):
            opts["ttl_hours"] = float(argv[i + 1])
            i += 2
        elif a in ("--jobs-days",):
            opts["jobs_days"] = float(argv[i + 1])
            i += 2
        else:
            sys.stderr.write("未知参数: %s\n\n%s" % (a, USAGE))
            sys.exit(2)
    return opts


def detect_paths():
    """定位当前工作树根与主工作树根。"""
    r = git(["rev-parse", "--show-toplevel"], cwd=str(SCRIPT_ROOT))
    if r.returncode != 0 or not r.stdout.strip():
        return False
    root = Path(r.stdout.strip()).resolve()
    CTX.root = root
    main_root = root
    listing = git(["worktree", "list", "--porcelain"], cwd=root)
    if listing.returncode == 0:
        for line in listing.stdout.splitlines():
            if line.startswith("worktree "):
                main_root = Path(line[len("worktree "):].strip()).resolve()
                break
    CTX.main_root = main_root
    CTX.repo_name = main_root.name
    return True


def detect_trunk():
    for c in ("master", "main"):
        if git(["show-ref", "--verify", "--quiet", "refs/heads/%s" % c]).returncode == 0:
            CTX.trunk, CTX.base_ref = c, c
            return
    for c in ("master", "main"):
        ref = "refs/remotes/origin/%s" % c
        if git(["show-ref", "--verify", "--quiet", ref]).returncode == 0:
            CTX.trunk, CTX.base_ref = c, "origin/%s" % c
            return


def find_script(rel):
    for base in (CTX.root, CTX.main_root):
        p = Path(base) / rel
        if p.is_file():
            return p
    return None


def parse_ts(value):
    if value is None or isinstance(value, bool):
        return None
    if isinstance(value, (int, float)):
        v = float(value)
        if v > 1e11:  # 毫秒时间戳
            v /= 1000.0
        return v if v > 0 else None
    if isinstance(value, str):
        s = value.strip()
        if not s:
            return None
        try:
            return parse_ts(float(s))
        except ValueError:
            pass
        try:
            return datetime.fromisoformat(s.replace("Z", "+00:00")).timestamp()
        except ValueError:
            return None
    return None


def fmt_ago(seconds):
    """把秒数渲染成「x 分钟前 / x 小时前 / x 天前」。"""
    if seconds < 3600:
        return "%d 分钟前" % max(0, int(seconds // 60))
    if seconds < 86400:
        return "%.1f 小时前" % (seconds / 3600.0)
    return "%.1f 天前" % (seconds / 86400.0)


def new_check(cid, title):
    return {"id": cid, "title": title, "message": "", "items": []}


def add(chk, level, text):
    """追加一条明细并返回引用，便于修复后原地改写成 fix。"""
    item = {"level": level, "text": text}
    chk["items"].append(item)
    return item


def mark_fixed(item, text):
    item["level"] = "fix"
    item["text"] = text


def finalize(chk, message):
    chk["message"] = message
    levels = [i["level"] for i in chk["items"]]
    if "fail" in levels:
        chk["status"] = "fail"
    elif "warn" in levels:
        chk["status"] = "warn"
    else:
        chk["status"] = "ok"
    return chk


def warn_count(chk):
    return sum(1 for i in chk["items"] if i["level"] == "warn")


# ---------------------------------------------------------------- worktree


def worktree_entries():
    r = git(["worktree", "list", "--porcelain"])
    if r.returncode != 0:
        return None
    entries, cur = [], {}
    for line in r.stdout.splitlines():
        if not line.strip():
            if cur:
                entries.append(cur)
                cur = {}
            continue
        key, _, val = line.partition(" ")
        cur[key] = val.strip()
    if cur:
        entries.append(cur)
    for e in entries:
        e["path"] = Path(e.get("worktree", "")).resolve()
        ref = e.get("branch", "")
        e["branch"] = ref[len("refs/heads/"):] if ref.startswith("refs/heads/") else ""
        e["missing"] = ("prunable" in e) or (not e["path"].is_dir())
    return entries


def is_merged(ref):
    if not CTX.base_ref:
        return False
    return git(["merge-base", "--is-ancestor", ref, CTX.base_ref]).returncode == 0


def orphan_candidates(registered, cwd):
    """兄弟目录 / .worktrees 下本仓库命名模式但未注册的残留（排除当前目录自身）。"""
    found = []
    seen = set()

    def consider(p):
        try:
            rp = p.resolve()
        except OSError:
            return
        if not p.is_dir() or rp in registered or rp in seen or rp == cwd:
            return
        seen.add(rp)
        found.append(rp)

    parent = CTX.main_root.parent
    prefix = CTX.repo_name + "-"
    if parent.is_dir():
        for p in sorted(parent.iterdir()):
            if p.name.startswith(prefix):
                consider(p)
    for base in CTX.state_roots():
        wt_dir = base / ".worktrees"
        if wt_dir.is_dir():
            for p in sorted(wt_dir.iterdir()):
                consider(p)
    return found


def orphan_safety(p):
    """判定孤儿目录能否自动删除；返回 (可删, 理由)。"""
    if p == CTX.root or p == CTX.main_root:
        return False, "当前或主工作树"
    try:
        children = list(p.iterdir())
    except OSError as exc:
        return False, "无法读取: %s" % exc
    if not children:
        return True, "空目录"
    dot_git = p / ".git"
    if dot_git.is_dir():
        return False, "独立 git 仓库，不自动删除"
    if dot_git.is_file():
        try:
            content = dot_git.read_text(encoding="utf-8", errors="replace")
        except OSError:
            return False, ".git 文件不可读"
        gitdirs = []
        for base in (CTX.main_root, CTX.root):
            gitdirs.append(str((base / ".git" / "worktrees").resolve()))
        if "gitdir:" in content and any(g in content for g in gitdirs):
            return True, ".git 指向本仓库已失效的 worktree"
        return False, ".git 指向其它仓库，不自动删除"
    # 无 .git：只有「已被 git worktree remove 摘掉注册、但目录里只剩构建缓存」
    # 这一种可以自动删。剩下的内容一旦出现源码/文档，一律交人。
    ignorable = {
        ".gradle", ".gradle-user", ".kotlin", ".idea", ".cxx", "build",
        "local.properties", ".cache", ".pytest_cache", ".ruff_cache",
    }
    leftovers = []
    for child in children:
        if child.name in ignorable:
            continue
        if child.name.startswith(".gradle") or child.name.startswith(".kotlin"):
            continue
        leftovers.append(child.name)
    if not leftovers:
        return True, "无 .git，仅剩构建缓存（worktree remove 后的壳）"
    return False, "无 .git 且含非缓存内容（%s），交人确认" % ", ".join(sorted(leftovers)[:3])


def remove_worktree(path, branch):
    """优先 ses-worktree.sh clean（DEC-065 约定），退化为 remove，再退化为原生 git。"""
    script = find_script("scripts/session/ses-worktree.sh")
    target = branch or str(path)
    if script is not None:
        for sub in ("clean", "remove"):
            r = subprocess.run(
                ["bash", str(script), sub, target],
                capture_output=True,
                text=True,
            )
            if r.returncode == 0:
                return True, "ses-worktree.sh %s" % sub
    r = git(["worktree", "remove", str(path)])
    if r.returncode == 0:
        return True, "git worktree remove"
    return False, (r.stderr.strip().splitlines() or ["移除失败"])[0]


def check_worktrees(opts, fixes):
    chk = new_check("worktree", "worktree 注册与残留")
    entries = worktree_entries()
    if entries is None:
        add(chk, "fail", "git worktree list 执行失败")
        return finalize(chk, "无法读取 worktree 注册表")
    registered = set(e["path"] for e in entries)

    merged, missing = [], []
    for e in entries[1:]:  # 首项为主工作树，永不处理
        if e["missing"]:
            missing.append(e)
        elif e["branch"] and is_merged(e["branch"]):
            merged.append(e)

    for e in missing:
        e["item"] = add(chk, "warn", "目录不存在（注册残留）: %s" % e["path"])
    for e in merged:
        e["item"] = add(
            chk, "warn", "已合入 %s: %s [%s]" % (CTX.trunk, e["path"], e["branch"])
        )

    orphans = []
    for p in orphan_candidates(registered, Path(CTX.root).resolve()):
        ok, why = orphan_safety(p)
        orphans.append((p, ok, why))
        add(chk, "warn", "孤儿残留目录（未注册）: %s（%s）" % (p, why))

    if opts["fix"]:
        if missing:
            r = git(["worktree", "prune"])
            if r.returncode == 0:
                fixes.append("git worktree prune：清理 %d 条失效注册" % len(missing))
                for e in missing:
                    mark_fixed(e["item"], "已剪枝注册: %s" % e["path"])
        for e in merged:
            rp = e["path"]
            if rp in (Path(CTX.root).resolve(), Path(CTX.main_root).resolve()):
                add(chk, "info", "保留当前工作树: %s" % rp)
                continue
            st = git(["status", "--porcelain"], cwd=rp)
            if st.returncode != 0 or st.stdout.strip():
                add(chk, "info", "保留（工作树有改动）: %s" % rp)
                continue
            ok, how = remove_worktree(rp, e["branch"])
            if ok:
                fixes.append("移除已合入 worktree: %s（%s）" % (rp, how))
                mark_fixed(e["item"], "已移除 worktree: %s" % rp)
            else:
                add(chk, "info", "保留（%s）: %s" % (how, rp))
        for p, ok, why in orphans:
            if not ok:
                add(chk, "info", "保留（%s）: %s" % (why, p))
                continue
            try:
                shutil.rmtree(p)
            except OSError as exc:
                add(chk, "info", "删除失败（%s）: %s" % (exc, p))
                continue
            fixes.append("删除孤儿残留目录: %s（%s）" % (p, why))
            for item in chk["items"]:
                if item["level"] == "warn" and str(p) in item["text"]:
                    mark_fixed(item, "已删除孤儿目录: %s" % p)

    remaining = warn_count(chk)
    if remaining == 0:
        return finalize(chk, "%d 个注册 worktree，无冗余" % len(entries))
    return finalize(chk, "%d 项待清理（已合入 / 目录缺失 / 孤儿残留）" % remaining)


# ---------------------------------------------------------------- 分支


def check_local_branches(opts, fixes):
    chk = new_check("branch", "本地分支")
    r = git(["for-each-ref", "--format=%(refname:short)", "refs/heads"])
    if r.returncode != 0:
        add(chk, "fail", "git for-each-ref 执行失败")
        return finalize(chk, "无法读取本地分支")
    branches = [b.strip() for b in r.stdout.splitlines() if b.strip()]
    merged = []
    for b in branches:
        if b in ("master", "main", CTX.trunk):
            continue
        if is_merged(b):
            merged.append(b)
    merged.sort()
    items = {}
    for b in merged:
        items[b] = add(chk, "warn", "已合入 %s: %s" % (CTX.trunk, b))

    if opts["fix"]:
        for b in merged:
            d = git(["branch", "-d", b])
            if d.returncode == 0:
                fixes.append("删除已合入本地分支: %s" % b)
                mark_fixed(items[b], "已删除分支: %s" % b)
            else:
                first = (d.stderr.strip().splitlines() or ["删除失败"])[0]
                add(chk, "info", "保留分支 %s（%s）" % (b, first))

    remaining = warn_count(chk)
    if remaining == 0:
        return finalize(chk, "%d 个本地分支，无已合入残留" % len(branches))
    return finalize(chk, "%d 个已合入 %s 的本地分支" % (remaining, CTX.trunk))


# ---------------------------------------------------------------- 远端分支


def check_remote_branches(opts, fixes):
    chk = new_check("remote", "远端分支")
    if not opts["network"]:
        return finalize(chk, "未启用 --network，跳过（远端检查会 fetch）")
    r = git(["fetch", "--prune", "origin"], timeout=180)
    if r.returncode != 0:
        add(chk, "warn", "git fetch --prune 失败，跳过远端检查")
        return finalize(chk, "远端不可达")
    if not CTX.trunk:
        add(chk, "warn", "找不到 trunk，无法判断远端合入状态")
        return finalize(chk, "缺少远端 trunk")
    base = "refs/remotes/origin/%s" % CTX.trunk
    if git(["show-ref", "--verify", "--quiet", base]).returncode != 0:
        add(chk, "warn", "找不到 origin/%s，无法判断远端合入状态" % CTX.trunk)
        return finalize(chk, "缺少远端 trunk")
    r = git(
        [
            "for-each-ref",
            "--format=%(refname:short)",
            "--merged",
            base,
            "refs/remotes/origin",
        ]
    )
    merged = []
    for line in r.stdout.splitlines():
        name = line.strip()
        if not name or name.endswith("/HEAD"):
            continue
        if name == "origin/%s" % CTX.trunk:
            continue
        merged.append(name)
    merged.sort()
    for b in merged:
        add(chk, "warn", "已合入 %s: %s（人工 git push origin --delete）" % (CTX.trunk, b))
    if not merged:
        return finalize(chk, "远端无已合入残留")
    return finalize(chk, "%d 个已合入 %s 的远端分支" % (len(merged), CTX.trunk))


# ---------------------------------------------------------------- 认领锁


def live_session_ids():
    ids = set()
    for base in CTX.state_roots():
        ai = base / ".ai"
        cur = ai / "current-session.json"
        if cur.is_file():
            try:
                sid = json.loads(cur.read_text(encoding="utf-8")).get("session_id", "")
                if sid:
                    ids.add(sid)
            except (ValueError, OSError):
                pass
        sess_dir = ai / "sessions"
        if sess_dir.is_dir():
            for f in sess_dir.glob("*.json"):
                ids.add(f.stem)
                try:
                    sid = json.loads(f.read_text(encoding="utf-8")).get("session_id", "")
                    if sid:
                        ids.add(sid)
                except (ValueError, OSError):
                    pass
    return ids


def lock_is_expired(data, mtime, now, ttl_seconds):
    expires = parse_ts(
        data.get("expires") or data.get("expire_at") or data.get("expires_at")
    )
    if expires is not None:
        return now > expires, expires
    created = parse_ts(
        data.get("acquired")
        or data.get("created_at")
        or data.get("timestamp")
        or data.get("ts")
    )
    ref = created if created is not None else mtime
    return (now - ref) > ttl_seconds, ref


def check_locks(opts, fixes):
    chk = new_check("locks", "认领锁")
    now = time.time()
    ttl_seconds = opts["ttl_hours"] * 3600.0
    sessions = live_session_ids()
    stale_files = []
    legacy_stale = []

    for base in CTX.state_roots():
        locks_dir = base / ".ai" / "locks"
        if locks_dir.is_dir():
            for f in sorted(locks_dir.iterdir()):
                if not f.is_file() or f.name.startswith("."):
                    continue
                try:
                    data = json.loads(f.read_text(encoding="utf-8")) or {}
                except (ValueError, OSError):
                    add(chk, "warn", "锁文件无法解析（保留，人工确认）: %s" % f)
                    continue
                if not isinstance(data, dict):
                    add(chk, "warn", "锁文件结构异常（保留，人工确认）: %s" % f)
                    continue
                expired, ref = lock_is_expired(data, f.stat().st_mtime, now, ttl_seconds)
                owner = (
                    data.get("owner")
                    or data.get("session")
                    or data.get("session_id")
                    or ""
                )
                if expired:
                    item = add(
                        chk,
                        "warn",
                        "过期锁: %s（%s 前）"
                        % (f, fmt_ago(now - ref) if ref else "时间未知"),
                    )
                    stale_files.append((f, item))
                elif owner and owner not in sessions:
                    item = add(
                        chk, "warn", "锁 owner 无对应 session: %s（owner=%s）" % (f, owner)
                    )
                    stale_files.append((f, item))

        legacy = base / ".ai" / "task-lock.json"
        if legacy.is_file():
            try:
                data = json.loads(legacy.read_text(encoding="utf-8")) or {}
            except (ValueError, OSError):
                add(chk, "warn", "task-lock.json 无法解析: %s" % legacy)
                data = {}
            if isinstance(data, dict):
                for task, holder in sorted(data.items()):
                    if not isinstance(holder, dict):
                        continue
                    expired, ref = lock_is_expired(
                        holder, legacy.stat().st_mtime, now, ttl_seconds
                    )
                    if expired:
                        item = add(
                            chk, "warn", "过期 task 锁: %s（task=%s）" % (legacy, task)
                        )
                        legacy_stale.append((legacy, task, item))

    if opts["fix"] and (stale_files or legacy_stale):
        claim = find_script("scripts/workflow/wf-claim.sh")
        handled = False
        if claim is not None:
            r = subprocess.run(["bash", str(claim), "gc"], capture_output=True, text=True)
            if r.returncode == 0:
                handled = True
                fixes.append("wf-claim.sh gc：清理过期认领锁")
                for _f, item in stale_files:
                    mark_fixed(item, "已由 wf-claim.sh gc 清理")
                for _legacy, _task, item in legacy_stale:
                    mark_fixed(item, "已由 wf-claim.sh gc 清理")
        if not handled:
            for f, item in stale_files:
                try:
                    f.unlink()
                except OSError as exc:
                    add(chk, "info", "删除锁失败（%s）: %s" % (exc, f))
                    continue
                fixes.append("删除失效锁: %s" % f)
                mark_fixed(item, "已删除锁: %s" % f)
            by_file = {}
            for legacy, task, item in legacy_stale:
                by_file.setdefault(legacy, []).append((task, item))
            for legacy, entries in by_file.items():
                try:
                    data = json.loads(legacy.read_text(encoding="utf-8")) or {}
                except (ValueError, OSError):
                    continue
                drop = set(t for t, _i in entries)
                kept = {k: v for k, v in data.items() if k not in drop}
                legacy.write_text(
                    json.dumps(kept, ensure_ascii=False, indent=2) + "\n",
                    encoding="utf-8",
                )
                fixes.append("剪枝过期 task 锁: %s（%d 条）" % (legacy, len(drop)))
                for task, item in entries:
                    mark_fixed(item, "已剪枝 task 锁: %s（%s）" % (legacy, task))

    remaining = warn_count(chk)
    if remaining == 0:
        return finalize(chk, "无过期或孤儿锁")
    return finalize(chk, "%d 个过期/失效锁" % remaining)


# ---------------------------------------------------------------- 后台日志


def check_jobs(opts, fixes):
    chk = new_check("jobs", "后台任务日志")
    now = time.time()
    limit = opts["jobs_days"] * 86400.0
    stale = []
    for base in CTX.state_roots():
        jobs_dir = base / ".cache" / "jobs"
        if not jobs_dir.is_dir():
            continue
        for log in sorted(jobs_dir.glob("*.log")):
            exitf = log.with_suffix(".exit")
            if not exitf.exists():
                continue
            age = now - log.stat().st_mtime
            if age > limit:
                item = add(chk, "warn", "陈旧已完成日志: %s（%s）" % (log, fmt_ago(age)))
                stale.append((log, exitf, item))
    if opts["fix"]:
        for log, exitf, item in stale:
            for p in (log, exitf):
                try:
                    p.unlink()
                except OSError:
                    add(chk, "info", "删除失败: %s" % p)
                    continue
            fixes.append("清理陈旧后台日志: %s" % log)
            mark_fixed(item, "已清理日志: %s" % log)
    remaining = warn_count(chk)
    if remaining == 0:
        return finalize(chk, "无陈旧后台日志")
    return finalize(
        chk, "%d 个超过 %g 天且已完成的日志" % (remaining, opts["jobs_days"])
    )


# ---------------------------------------------------------------- 证据新鲜度


def check_evidence(opts, fixes):
    chk = new_check("evidence", "local-ci 证据")
    now = time.time()
    path = None
    for base in CTX.state_roots():
        cand = base / ".cache" / "local-ci-last.json"
        if cand.is_file():
            path = cand
            break
    if path is None:
        add(chk, "warn", "缺少 .cache/local-ci-last.json — 请运行 gov-local-ci.sh")
        return finalize(chk, "无 local-ci 证据")
    try:
        payload = json.loads(path.read_text(encoding="utf-8"))
    except (ValueError, OSError):
        add(chk, "warn", "证据文件无法解析: %s" % path)
        return finalize(chk, "证据文件损坏")
    finished = parse_ts(
        payload.get("finished_at") or payload.get("finished") or payload.get("timestamp")
    )
    result = payload.get("result", "unknown")
    if finished is None:
        add(chk, "warn", "证据缺少 finished_at: %s" % path)
        return finalize(chk, "证据时间未知")
    age = now - finished
    if age > 86400:
        add(
            chk,
            "warn",
            "证据已过期: %s（%s，result=%s）— 请重跑 gov-local-ci.sh"
            % (path, fmt_ago(age), result),
        )
        return finalize(chk, "local-ci 证据超过 24h")
    return finalize(chk, "证据新鲜（%s，result=%s）" % (fmt_ago(age), result))


# ---------------------------------------------------------------- trunk 同步


def check_sync(opts, fixes):
    chk = new_check("sync", "%s 同步" % (CTX.trunk or "trunk"))
    if not CTX.trunk:
        add(chk, "warn", "找不到 master/main 分支")
        return finalize(chk, "无 trunk")
    origin_ref = "refs/remotes/origin/%s" % CTX.trunk
    if git(["show-ref", "--verify", "--quiet", origin_ref]).returncode != 0:
        return finalize(chk, "无 origin/%s 引用，跳过" % CTX.trunk)
    r = git(["rev-list", "--count", "%s..origin/%s" % (CTX.trunk, CTX.trunk)])
    if r.returncode != 0:
        add(chk, "fail", "git rev-list 执行失败")
        return finalize(chk, "无法计算落后提交数")
    behind = int(r.stdout.strip() or "0")
    if behind > 0:
        add(chk, "warn", "本地 %s 落后 origin/%s %d 个提交" % (CTX.trunk, CTX.trunk, behind))
        return finalize(chk, "落后 origin/%s %d 个提交" % (CTX.trunk, behind))
    return finalize(chk, "与 origin/%s 同步" % CTX.trunk)


# ---------------------------------------------------------------- 主流程


def finish(opts, checks, fixes, error=None):
    summary = {"ok": 0, "warn": 0, "fail": 0}
    for c in checks:
        summary[c["status"]] = summary.get(c["status"], 0) + 1
    if summary["fail"] > 0:
        code = 1
    elif summary["warn"] > 0 and opts["strict"]:
        code = 1
    else:
        code = 0
    payload = {
        "schema_version": 1,
        "source": "gov-workspace-doctor",
        "generated_at": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "root": str(CTX.root) if CTX.root else None,
        "main_root": str(CTX.main_root) if CTX.main_root else None,
        "trunk": CTX.trunk,
        "flags": {
            "fix": opts["fix"],
            "strict": opts["strict"],
            "network": opts["network"],
            "ttl_hours": opts["ttl_hours"],
            "jobs_days": opts["jobs_days"],
        },
        "checks": checks,
        "summary": summary,
        "fixes": fixes,
        "exit_code": code,
    }
    if error:
        payload["error"] = error
    return code, payload


def run(opts):
    checks, fixes = [], []
    if not detect_paths():
        chk = new_check("repo", "git 仓库")
        add(chk, "fail", "当前目录不是 git 仓库")
        checks.append(finalize(chk, "不是 git 仓库"))
        return finish(opts, checks, fixes, error="不是 git 仓库")
    detect_trunk()
    checks.append(check_worktrees(opts, fixes))
    checks.append(check_local_branches(opts, fixes))
    checks.append(check_remote_branches(opts, fixes))
    checks.append(check_locks(opts, fixes))
    checks.append(check_jobs(opts, fixes))
    checks.append(check_evidence(opts, fixes))
    checks.append(check_sync(opts, fixes))
    return finish(opts, checks, fixes)


def print_human(payload):
    root = payload.get("root") or "-"
    main_root = payload.get("main_root") or "-"
    head = "gov-workspace-doctor — 仓库: %s" % main_root
    if root != main_root:
        head += "（当前树: %s）" % root
    lines = [head]
    for c in payload["checks"]:
        lines.append(
            "[%s] %s: %s" % (c["status"].upper().ljust(4), c["id"], c["message"])
        )
        for item in c["items"]:
            prefix = {
                "warn": "  - ",
                "fail": "  - ",
                "fix": "  * ",
                "info": "  . ",
            }.get(item["level"], "  - ")
            lines.append(prefix + item["text"])
    if payload["fixes"]:
        lines.append("已修复 %d 项:" % len(payload["fixes"]))
        for f in payload["fixes"]:
            lines.append("  * " + f)
    elif payload["flags"]["fix"]:
        lines.append("已修复 0 项（无可安全清理项）")
    s = payload["summary"]
    lines.append(
        "=== 汇总: OK %d / WARN %d / FAIL %d → 退出码 %d ==="
        % (s["ok"], s["warn"], s["fail"], payload["exit_code"])
    )
    sys.stdout.write("\n".join(lines) + "\n")


def main():
    opts = parse_args(RAW_ARGS)
    if opts["help"]:
        sys.stdout.write(USAGE)
        sys.exit(0)
    code, payload = run(opts)
    if opts["json"]:
        sys.stdout.write(json.dumps(payload, ensure_ascii=False, indent=2) + "\n")
    else:
        print_human(payload)
    sys.exit(code)


if __name__ == "__main__":
    main()
PY
