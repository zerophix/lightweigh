"""The driver: the ONLY mechanism agentdrive adds.

It keeps an OpenCode session advancing a goal/task-group:
poll -> judge (project policy) -> nudge -> repeat.
Completion is decided by the policy, never by the agent's self-report.

Two modes:
  run(goal, task)            one task
  run_group(goal, role, tasks) a role's task group, reusing ONE session
                               (accumulated understanding across tasks)
"""
import hashlib
import time
from pathlib import Path
from typing import Callable, List, Optional

from .types import Task, Workspace, Outcome
from .policy import ProjectPolicy
from .session import OpenCodeBackend
from .guard import Guard, EventLog


SYSTEM_PROTOCOL = (
    "你是自动执行体。规则：\n"
    "1. 只做达成目标所需的最小必要改动，不要遍历无关目录\n"
    "2. 完成后运行相关校验/测试确认\n"
    "3. 全部达成后，最后一行输出 {\"DONE\": true, \"EVIDENCE\": [...]}\n"
    "4. 若被外部条件真正阻塞，输出 {\"BLOCKED\": true, \"REASON\": \"...\", \"NEED\": \"...\"}\n"
)


class Driver:
    def __init__(self, policy: ProjectPolicy, backend: OpenCodeBackend,
                 guard: Guard, log: Callable[[str], None] = print,
                 pool=None, events: Optional[EventLog] = None,
                 poll_interval: int = 8,
                 token_warn: int = 140_000, token_critical: int = 175_000,
                 max_no_progress: int = 5):
        self.policy = policy
        self.backend = backend
        self.pool = pool
        self.guard = guard
        self.log = log
        self.events = events
        self.poll_interval = poll_interval
        self.token_warn = token_warn
        self.token_critical = token_critical
        self.max_no_progress = max_no_progress

    # ---------- infra ----------

    def _archive(self, task, ws, reason):
        try:
            fn = getattr(self.policy, "archive", None)
            if callable(fn):
                fn(task, ws, reason)
        except Exception:
            pass

    def _emit(self, event: str, **kw):
        if self.events:
            self.events.emit(event, **kw)

    def _memory_text(self, task, ws: Workspace) -> str:
        chunks = []
        for rel in self.policy.memory(task, ws):
            p = Path(ws.location) / rel
            if p.exists() and p.is_file():
                try:
                    txt = p.read_text(encoding="utf-8", errors="replace")
                except Exception:
                    continue
                chunks.append(f"===== {rel} =====\n{txt[:6000]}")
        return "\n\n".join(chunks)

    def _build_initial(self, goal, task, ws):
        fn = getattr(self.policy, "initial_prompt", None)
        if callable(fn):
            try:
                p = fn(goal, task, ws)
                if p:
                    return p
            except Exception:
                pass
        mem = self._memory_text(task, ws)
        targets = self.policy.target_files(task)
        extra = ""
        if task:
            acc = task.acceptance or {}
            then = "; ".join(acc.get("then", []) or [])
            verify = "; ".join(acc.get("verify_commands", []) or [])
            extra = (f"\n## 任务 {task.id}\n- 目标文件：{', '.join(targets) or '(未限定)'}\n"
                     f"- 验收：{then or '(见目标)'}\n- 校验：{verify or '(见目标)'}\n")
        return (f"## 目标\n{goal}\n{extra}\n## 项目记忆（节选）\n{mem or '(无)'}\n\n"
                f"## 执行协议\n{SYSTEM_PROTOCOL}")

    def _nudge_continue(self, task) -> str:
        t = f"（任务 {task.id}）" if task else ""
        return (f"上一轮结束但目标{ t }尚未达成。继续推进最小下一步，"
                f"完成后输出 {{\"DONE\": true}}；确实被阻塞输出 {{\"BLOCKED\": true, \"REASON\": \"...\"}}。")

    def _nudge_no_progress(self, task) -> str:
        targets = self.policy.target_files(task)
        hint = f"直接修改这些文件：{', '.join(targets)}" if targets else "换一个更小的切入点动手"
        return (f"连续多轮没有实质进展。请{hint}；不要重复阅读无关文件。"
                f"若被外部条件阻塞，输出 {{\"BLOCKED\": true, \"REASON\": \"...\"}}。")

    def _nudge_after_compact(self, task) -> str:
        return "上下文已压缩。继续未完成的工作，完成后输出 {\"DONE\": true}。"

    def _file_hash(self, task, ws: Workspace) -> str:
        h = hashlib.md5()
        for rel in self.policy.target_files(task):
            p = Path(ws.location) / rel
            if p.exists():
                try:
                    h.update(p.read_bytes())
                except Exception:
                    pass
        return h.hexdigest()

    # ---------- session ----------

    def _ensure_session(self, role, ws, title, reuse_session_id=None):
        if reuse_session_id:
            self.backend.move(reuse_session_id, ws.location)
            return reuse_session_id
        role_key = getattr(role, "name", "agent")
        if self.pool is not None:
            return self.pool.acquire(role_key, ws.location, role.model,
                                     title=title, agent=role.agent)
        return self.backend.create(ws.location, role.model, title=title, agent=role.agent)

    # ---------- supervise one task ----------

    def _supervise(self, sid, goal, task, ws) -> str:
        """推进一个任务，直到：门禁判定完成 / 越界 / 预算耗尽。

        关键约定：**只在 agent 停手时验收**（turn 结束、空闲 ≥90s、或连续无活动），
        避免在半成品代码上跑门禁造成误判与时间浪费。
        """
        self.backend.prompt(sid, self._build_initial(goal, task, ws), cwd=ws.location)
        last_hash = self._file_hash(task, ws)
        last_tokens = 0
        last_msg_count = 0
        no_progress = 0
        compacted_at = 0.0
        self._rounds = 0

        # max_rounds 语义 = 最少轮询次数；真实上限由 wallclock 决定
        max_polls = max(self.guard.max_rounds, int(self.guard.max_wallclock_s / max(self.poll_interval, 1)))

        for rnd in range(max_polls):
            if self.guard.wallclock_exceeded():
                self.log("[drive] wallclock budget exhausted"); self._archive(task, ws, "wallclock")
                return "BUDGET"
            if self.guard.paused():
                self.log("[drive] paused by human; observing only")
                time.sleep(self.poll_interval); continue

            time.sleep(self.poll_interval)
            state = self.backend.poll(sid)
            if not state:
                continue
            tokens = state.get("tokens", {}).get("input", 0)
            self._tokens = tokens
            outcome = state.get("outcome")
            idle_ms = state.get("time", {}).get("idle", 0)

            if self.guard.budget_exceeded(tokens):
                self.log(f"[drive] token budget exhausted ({tokens})"); self._archive(task, ws, "token-budget")
                return "BUDGET"

            msgs = self.backend.messages(sid)
            signal = OpenCodeBackend.detect_signal(msgs)
            if signal == "BLOCKED":
                self.log("[drive] BLOCKED"); self._archive(task, ws, "blocked")
                return "BLOCKED"

            cur_hash = self._file_hash(task, ws)
            progressed = cur_hash != last_hash
            last_hash = cur_hash
            tokens_grew = tokens > last_tokens
            last_tokens = tokens
            msg_grew = len(msgs) > last_msg_count
            last_msg_count = len(msgs)
            active = tokens_grew or msg_grew or progressed

            if active:
                no_progress = 0
            else:
                no_progress += 1

            turn_ended = outcome in ("succeeded", "failed", "interrupted")
            stalled = idle_ms >= 90_000 and outcome is None
            should_check = signal == "DONE" or turn_ended or stalled or no_progress >= 2

            if should_check:
                res = self.policy.is_done(task, ws)
                if res.done:
                    self.log("[drive] gates passed")
                    self._emit("done", session=sid, task=getattr(task, "id", None))
                    fin = self.policy.finalize(task, ws)
                    self.log(f"[drive] finalize ok={fin.ok}")
                    if fin.ok:
                        try:
                            self.policy.release(task)
                            self.log("[drive] lock released")
                        except Exception:
                            pass
                    if not fin.ok:
                        d = fin.detail if isinstance(fin.detail, dict) else {}
                        if d.get("blocked"):
                            self._archive(task, ws, "finalize-blocked:" + str(d.get("reason"))[:60])
                            return "BLOCKED"
                        return "FAILED"
                    return "DONE"
                rsn = (res.reason or "")
                if rsn.startswith("protected path") or rsn.startswith("out of scope"):
                    self.log(f"[drive] BLOCKED by guard: {rsn}")
                    self._archive(task, ws, "guard:" + rsn[:60])
                    return "BLOCKED"
                self.log(f"[drive] not done yet: {rsn}")
                if signal == "DONE" or turn_ended or stalled:
                    self._rounds += 1
                    self.backend.prompt(sid, self._nudge_continue(task), cwd=ws.location)
                    no_progress = 0
                continue

            if tokens >= self.token_critical and time.time() - compacted_at > 60:
                self.log(f"[drive] tokens {tokens} -> compact"); self.backend.compact(sid)
                compacted_at = time.time(); self._rounds += 1
                self.backend.prompt(sid, self._nudge_after_compact(task), cwd=ws.location); continue
            if tokens >= self.token_warn and time.time() - compacted_at > 300:
                self.log(f"[drive] tokens {tokens} -> compact"); self.backend.compact(sid)
                compacted_at = time.time(); self._rounds += 1
                self.backend.prompt(sid, self._nudge_after_compact(task), cwd=ws.location); continue

            if no_progress >= self.max_no_progress:
                self.log(f"[drive] no progress x{no_progress} -> change strategy")
                self._rounds += 1
                self.backend.prompt(sid, self._nudge_no_progress(task), cwd=ws.location)
                no_progress = 0; continue

            if rnd % 5 == 0:
                self.log(f"[drive] working... tokens={tokens} idle={idle_ms//1000}s no_progress={no_progress}")

        self.log("[drive] max polls reached"); self._archive(task, ws, "max-polls")
        return "BUDGET"

    # ---------- public ----------

    def run(self, goal: str, task: Optional[Task] = None, reuse_session_id: Optional[str] = None) -> Outcome:
        ws = self.policy.prepare(task)
        role = self.policy.role(task)
        sid = self._ensure_session(role, ws, goal[:60], reuse_session_id=reuse_session_id)
        if not sid:
            return Outcome("FAILED", detail="session create failed")
        self.log(f"[drive] session={sid} location={ws.location} role={role.name}")
        status = self._supervise(sid, goal, task, ws)
        return Outcome(status, sid, rounds=getattr(self, "_rounds", 0),
                       tokens=getattr(self, "_tokens", 0), detail={"role": role.name})

    def run_group(self, goal: str, role_id: str, tasks: List[Task]) -> Outcome:
        """One session handles a whole role's task group, task by task."""
        if not tasks:
            self.log(f"[drive] no tasks for role {role_id}")
            return Outcome("DONE", detail="empty group")
        sid = None
        results = []
        for i, task in enumerate(tasks, 1):
            ws = self.policy.prepare(task)
            role = self.policy.role(task)
            sid = self._ensure_session(role, ws, f"{role_id} group", reuse_session_id=sid)
            if not sid:
                results.append((task.id, "FAILED")); break
            self.log(f"[drive] ({i}/{len(tasks)}) task={task.id} session={sid} location={ws.location}")
            g = goal or f"完成 {task.id}"
            status = self._supervise(sid, g, task, ws)
            results.append((task.id, status))
            self.log(f"[drive] ({i}/{len(tasks)}) {task.id} -> {status}")
            if status == "BLOCKED":
                self._emit("group_halt", role=role_id, task=task.id)
                break
        self._emit("group_done", role=role_id, results=results)
        return Outcome("DONE", sid or "", detail={"role": role_id, "results": results})
