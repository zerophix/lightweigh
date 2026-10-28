"""Lightweigh policy: map the generic engine onto this repo's canonical workflow.

It owns NO lifecycle logic. It only:
  - dispatches tasks to roles (.agentdrive/roles.yaml)
  - delegates start/gates/finish to wf-autopilot.sh (single legal path)
  - enforces scope (only target_files) and archives evidence on abort
  - gives the session the SAME bootstrap a human uses: read AGENTS.md, take the task
"""
import json
import shutil
import subprocess
from pathlib import Path
from typing import Dict, List, Optional

from ..core.policy import ProjectPolicy
from ..core.roles import RoleRegistry
from ..core.types import Task, Workspace, Role, DoneResult, FinalizeResult


def _run_json(cmd: List[str], cwd: Path, timeout: int = 900) -> Optional[dict]:
    try:
        r = subprocess.run(cmd, cwd=str(cwd), capture_output=True, text=True, timeout=timeout)
    except subprocess.TimeoutExpired:
        return None
    try:
        return json.loads(r.stdout.strip().splitlines()[-1])
    except Exception:
        try:
            return json.loads(r.stdout)
        except Exception:
            return None


class LightweighPolicy(ProjectPolicy):
    def __init__(self, root: str = ".", task_id: Optional[str] = None,
                 wf_script: Optional[str] = None):
        super().__init__(root)
        self.task_id = task_id
        self.wf = wf_script or str(Path(self.root) / "scripts" / "workflow" / "wf-autopilot.sh")
        self.agents = self._load_agents()
        self.routing = self._load_routing()
        self.registry = RoleRegistry(str(Path(self.root) / ".agentdrive" / "roles.yaml"))

    # ---------- config ----------

    def _load_agents(self) -> Dict[str, dict]:
        try:
            import yaml
            p = Path(self.root) / ".opencode" / "agents.yaml"
            if p.exists():
                data = yaml.safe_load(p.read_text(encoding="utf-8")) or {}
                return {a["id"]: a for a in data.get("agents", [])}
        except Exception:
            pass
        return {}

    def _load_routing(self) -> dict:
        try:
            import yaml
            p = Path(self.root) / ".opencode" / "agents.yaml"
            if p.exists():
                data = yaml.safe_load(p.read_text(encoding="utf-8")) or {}
                return data.get("routing", {}) or {}
        except Exception:
            pass
        return {}

    # ---------- task source ----------

    def _tasks_file(self) -> Path:
        return Path(self.root) / ".ai" / "tasks.json"

    def _load_task(self, tid: str) -> Optional[Task]:
        try:
            data = json.loads(self._tasks_file().read_text(encoding="utf-8"))
        except Exception:
            return None
        for t in data.get("tasks", []):
            if t.get("id") == tid:
                return Task(
                    id=t["id"],
                    title=t.get("title") or t.get("description", ""),
                    target_files=t.get("target_files") or [],
                    acceptance=t.get("acceptance") or {},
                    meta={"module": t.get("module") or "misc",
                          "prototype_ref": t.get("prototype_ref")},
                )
        return None

    def next_task(self) -> Optional[Task]:
        if self.task_id:
            return self._load_task(self.task_id)
        try:
            data = json.loads(self._tasks_file().read_text(encoding="utf-8"))
        except Exception:
            return None
        for t in data.get("tasks", []):
            if t.get("status") == "pending" and (t.get("acceptance") or {}).get("status") == "ready":
                if t.get("target_files"):
                    return self._load_task(t["id"])
        return None

    # ---------- workspace ----------

    def _valid_worktree(self, path: str) -> bool:
        p = Path(path)
        if not (p / ".git").exists():
            return False
        r = subprocess.run(["git", "rev-parse", "--is-inside-work-tree"], cwd=str(p),
                           capture_output=True, text=True)
        return r.returncode == 0 and r.stdout.strip() == "true"

    def prepare(self, task: Optional[Task]) -> Workspace:
        if not task:
            return Workspace(location=self.root)

        def _start():
            return _run_json(["bash", self.wf, "start", task.id, "--json"],
                             Path(self.root), timeout=180)

        contract = _start()
        if not contract or not contract.get("ok"):
            raise RuntimeError(f"start failed for {task.id}: {contract}")
        wt = contract["worktree"]
        if not self._valid_worktree(wt):
            shutil.rmtree(wt, ignore_errors=True)
            contract = _start()
            if not contract or not contract.get("ok") or not self._valid_worktree(contract["worktree"]):
                raise RuntimeError(f"worktree invalid after recreate: {contract}")
            wt = contract["worktree"]
        task.meta.update({"branch": contract.get("branch"), "mode": contract.get("mode")})
        return Workspace(location=wt, meta=contract)

    # ---------- bootstrap:接手项目，而不是塞简报 ----------

    def initial_prompt(self, goal: str, task: Optional[Task], ws: Workspace) -> Optional[str]:
        """最小注入：只给 AGENTS.md 推不出来的三样（任务身份/本轮状态/机器边界）。

        规则、工作流、任务板查找方式已由项目 AGENTS.md 自动注入，不再复述。
        """
        if not task:
            return None
        try:
            role_id = self.role(task).name
        except Exception:
            role_id = "dev"
        targets = ", ".join(task.target_files or []) or "(未声明)"
        verify = "; ".join((task.acceptance or {}).get("verify_commands") or []) or "(见 gov-task-done)"
        state = (goal or "").strip() or "未开始"
        return (
            f"你是 {role_id}，任务 {task.id}（{task.title}）。\n"
            f"上一轮状态：{state}\n"
            f"工作区：{ws.location}（隔离 worktree，git 已就绪）\n"
            f"只允许改：{targets}\n"
            f"完成后自检：{verify}\n"
            f"按 AGENTS.md 流程自行开工：认领角色、从任务板取该任务、实现、自检、提交。"
        )

    def memory(self, task: Optional[Task], ws: Workspace) -> List[str]:
        # AGENTS.md is auto-loaded by opencode; nothing extra to inject.
        return []


    # ---------- evidence + fail-closed guards ----------

    # 项目脚本（session-begin / gov-task-done / 合并队列）写出的共享派生数据：
    # AGENTS.md §5 禁止功能分支携带它们，合并队列是唯一写者。
    DERIVED_PATTERNS = [
        ".ai/tasks.json", ".ai/tasks-archive.json", ".ai/staged-claims.json",
        ".ai/current-mode.json", ".ai/current-session.json", ".ai/roles/**", ".ai/locks/**",
        "STATUS.md", "android-test-registry.yaml", "docs/index.md",
        "docs/ai/lifecycle/**", "docs/ai/inbox/**",
    ]

    PROTECTED_DEFAULT = [
        "tests/**", "pytest.ini", "pyproject.toml", "tox.ini",
        "build.gradle", "build.gradle.kts", "settings.gradle", "settings.gradle.kts",
        "gradlew", "scripts/governance/**", ".github/workflows/**",
    ]

    def _protected(self) -> list:
        try:
            import yaml
            data = yaml.safe_load((Path(self.root) / ".agentdrive" / "roles.yaml").read_text(encoding="utf-8")) or {}
            return data.get("protected_paths") or self.PROTECTED_DEFAULT
        except Exception:
            return self.PROTECTED_DEFAULT

    def _match(self, path: str, patterns: list) -> bool:
        import fnmatch
        for pat in patterns:
            if pat.endswith("/**"):
                if path.startswith(pat[:-3]):
                    return True
            elif fnmatch.fnmatch(path, pat):
                return True
        return False

    def violations(self, task, ws: Workspace) -> dict:
        """Return {'scope': [...], 'protected': [...]} — empty lists mean OK."""
        if task is None:
            return {"scope": [], "protected": []}
        targets = list(task.target_files or [])
        evidence = f".ai/evidence/{task.id}.json"
        scope, protected = [], []
        prot_pats = self._protected()
        for f in self._changed_files(ws):
            if self._match(f, self.DERIVED_PATTERNS):
                continue  # 派生数据由 _sweep_derived 让路，不计入越界
            allowed = f in targets or f == evidence or (f.startswith(".ai/evidence/") and f.endswith(".json"))
            if not allowed:
                scope.append(f)
            # protected = 受保护路径，且不是本任务声明的目标文件
            if f not in targets and self._match(f, prot_pats):
                protected.append(f)
        return {"scope": scope, "protected": protected}

    def _sweep_derived(self, task, ws: Workspace) -> list:
        """把脚本写出的派生数据移出候选交付：已跟踪的回滚，未跟踪的移入归档。"""
        import subprocess
        import shutil
        moved = []
        root = Path(ws.location)
        try:
            r = subprocess.run(["git", "status", "--porcelain"], cwd=ws.location,
                               capture_output=True, text=True, timeout=60)
            lines = [l for l in (r.stdout or "").splitlines() if l.strip()]
        except Exception:
            return moved
        dest_root = Path(self.root) / ".agentdrive" / "derived" / (getattr(task, "id", "unknown") or "unknown")
        for line in lines:
            code, path = line[:2].strip(), line[3:].strip()
            if path.startswith('"') and path.endswith('"'):
                path = path[1:-1]
            if not self._match(path, self.DERIVED_PATTERNS):
                continue
            if "D" in code:
                continue
            if "??" in line or code == "??":
                src = root / path
                if src.exists():
                    dst = dest_root / path
                    dst.parent.mkdir(parents=True, exist_ok=True)
                    try:
                        shutil.move(str(src), str(dst)); moved.append(path)
                    except Exception:
                        pass
            else:
                subprocess.run(["git", "checkout", "--", path], cwd=ws.location,
                               capture_output=True, text=True, timeout=60)
                moved.append(path)
        return moved

    def _candidate_tree(self, ws: Workspace) -> str:
        """Write a tree object for the current worktree state (no commit)."""
        import subprocess
        try:
            subprocess.run(["git", "add", "-A"], cwd=ws.location, capture_output=True, text=True, timeout=60)
            r = subprocess.run(["git", "write-tree"], cwd=ws.location, capture_output=True, text=True, timeout=60)
            return (r.stdout or "").strip() if r.returncode == 0 else ""
        except Exception:
            return ""

    def _head(self, ws: Workspace) -> str:
        import subprocess
        try:
            r = subprocess.run(["git", "rev-parse", "HEAD"], cwd=ws.location, capture_output=True, text=True)
            return (r.stdout or "").strip()
        except Exception:
            return ""

    # ---------- gates / finalize ----------

    def is_done(self, task: Optional[Task], ws: Workspace) -> DoneResult:
        if not task:
            return DoneResult(done=False, reason="no task")

        swept = self._sweep_derived(task, ws)
        if swept:
            self._last_swept = swept
        v = self.violations(task, ws)
        if v["protected"]:
            return DoneResult(done=False, reason="protected path modified: " + ", ".join(v["protected"][:5]))
        if v["scope"]:
            return DoneResult(done=False, reason="out of scope: " + ", ".join(v["scope"][:5]))

        res = _run_json(["bash", self.wf, "gates", task.id, "--json", "--quick",
                         "--workdir", ws.location], Path(self.root), timeout=900)
        ok = bool(res and res.get("ok"))
        tree = self._candidate_tree(ws) if ok else ""
        if ok:
            self._evidence = getattr(self, "_evidence", {})
            self._evidence[task.id] = {"tree": tree, "gates": res.get("gates"), "ts": __import__("time").time()}
        return DoneResult(done=ok, reason="" if ok else "gates failed",
                          evidence={"tree": tree, "gates": (res or {}).get("gates")})

    def finalize(self, task: Optional[Task], ws: Workspace) -> FinalizeResult:
        if not task:
            return FinalizeResult(ok=False, detail="no task")

        self._sweep_derived(task, ws)

        # fail-closed：越界/受保护路径 → 拒绝交付，不回滚后假装通过
        v = self.violations(task, ws)
        if v["protected"] or v["scope"]:
            return FinalizeResult(ok=False, detail={
                "blocked": True,
                "reason": "scope/protected violation",
                "violations": v,
            })

        # 证据必须对应当前候选 tree（防止用旧证据证明新代码）
        tree = self._candidate_tree(ws)
        ev = getattr(self, "_evidence", {}).get(task.id)
        if not ev or ev.get("tree") != tree:
            return FinalizeResult(ok=False, detail={
                "blocked": True,
                "reason": "stale evidence: candidate tree changed since verification",
                "evidence_tree": (ev or {}).get("tree"),
                "current_tree": tree,
            })

        res = _run_json(["bash", self.wf, "finish", task.id, "--json",
                         "--workdir", ws.location], Path(self.root), timeout=900)
        detail = res or {}
        if isinstance(detail, dict):
            detail["_verified_tree"] = tree
            detail["_commit"] = self._head(ws)
        return FinalizeResult(ok=bool(res and res.get("ok")), detail=detail)

    # ---------- scope guard + archive ----------

    def _changed_files(self, ws: Workspace) -> List[str]:
        try:
            r = subprocess.run(["git", "status", "--porcelain"], cwd=ws.location,
                               capture_output=True, text=True, timeout=30)
        except Exception:
            return []
        files = []
        for line in (r.stdout or "").splitlines():
            if not line.strip():
                continue
            path = line[3:].strip()
            if " -> " in path:
                path = path.split(" -> ", 1)[1]
            files.append(path)
        return files

    def sanitize_scope(self, task, ws: Workspace) -> List[str]:
        """Revert changes outside target_files (and task evidence). Returns removed paths."""
        if task is None:
            return []
        allowed = set(task.target_files or [])
        allowed.add(f".ai/evidence/{task.id}.json")
        removed = []
        for f in self._changed_files(ws):
            if f in allowed:
                continue
            if f.startswith(".ai/evidence/") and f.endswith(".json"):
                continue
            p = Path(ws.location) / f
            tracked = subprocess.run(["git", "ls-files", "--error-unmatch", f],
                                     cwd=ws.location, capture_output=True, text=True).returncode == 0
            if tracked:
                subprocess.run(["git", "checkout", "--", f], cwd=ws.location,
                               capture_output=True, text=True)
            else:
                try:
                    if p.is_file():
                        p.unlink()
                    elif p.is_dir():
                        shutil.rmtree(p, ignore_errors=True)
                except Exception:
                    pass
            removed.append(f)
        return removed

    def archive(self, task: Optional[Task], ws: Workspace, reason: str = "") -> None:
        if task is None:
            return
        out = Path(self.root) / ".cache" / "agentdrive" / "archive" / task.id
        out.mkdir(parents=True, exist_ok=True)
        try:
            diff = subprocess.run(["git", "diff", "HEAD"], cwd=ws.location,
                                  capture_output=True, text=True, timeout=60).stdout
            status = subprocess.run(["git", "status", "--porcelain"], cwd=ws.location,
                                    capture_output=True, text=True, timeout=30).stdout
            (out / "changes.diff").write_text(diff, encoding="utf-8")
            (out / "status.txt").write_text(status, encoding="utf-8")
            (out / "reason.txt").write_text(reason, encoding="utf-8")
        except Exception:
            pass


    # ---------- role task group ----------

    def task_group(self, role_id: str) -> list:
        """All ready tasks that dispatch to this role (the role's own backlog)."""
        try:
            data = json.loads(self._tasks_file().read_text(encoding="utf-8"))
        except Exception:
            return []
        out = []
        for t in data.get("tasks", []):
            if t.get("status") not in ("pending", "ready"):
                continue
            task = self._load_task(t["id"])
            if not task:
                continue
            if self.registry.dispatch(task) == role_id:
                out.append(task)
        return out

    def roles(self) -> list:
        return list(self.registry.roles.keys())

    # ---------- role ----------

    def _agent_id(self, task: Optional[Task]) -> str:
        proto = (task.meta.get("prototype_ref") if task else None)
        if proto:
            return "prototype-mapper"
        module = (task.meta.get("module") if task else "") or ""
        return (self.routing.get("module_map", {}) or {}).get(module,
                self.routing.get("default", "backend-dev"))

    def role(self, task: Optional[Task]) -> Role:
        rid = self.registry.dispatch(task) if (task and self.registry.roles) else self._agent_id(task)
        spec = self.registry.spec(rid)
        model = spec.model if spec and spec.model else None
        if model is None:
            m = self.agents.get(rid, {}).get("model")
            if m:
                model = {"id": m["id"], "providerID": m["provider"], "variant": m.get("variant", "default")}
        return Role(name=rid, model=model, agent="build")
