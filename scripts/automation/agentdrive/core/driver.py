"""Referee-driver: run one clean OpenCode session to completion, then judge it.

Unlike the old supervisor (poll -> nudge -> compact), this driver does NOT
interrupt the agent. It only watches for an explicit completion signal and
then runs the project's own acceptance gates. The agent gets a fully
autonomous loop, which the A/B experiment proved produces 4x the output.

  - no nudge:      never inject "keep going" text mid-flight
  - no compact:    opencode's native context compression handles it
  - no idle kill:  the agent may read/think for long stretches
  - judge only:    on DONE / BLOCKED / turn-end, run is_done + finalize
"""
import time
from typing import Optional, List

from .types import Outcome, Task, Workspace
from .policy import ProjectPolicy
from .session import OpenCodeBackend
from .pool import SessionPool


class Driver:
    def __init__(self, policy: ProjectPolicy, backend: OpenCodeBackend = None,
                 poll_interval: float = 15.0,
                 max_wallclock_s: float = 5400.0,
                 token_warn: int = 140_000,
                 max_idle_before_judge: float = 180.0,
                 pool: Optional[SessionPool] = None,
                 log=print):
        self.policy = policy
        self.backend = backend or OpenCodeBackend()
        self.poll_interval = poll_interval
        self.max_wallclock = max_wallclock_s
        self.token_warn = token_warn
        self.max_idle_before_judge = max_idle_before_judge
        self.pool = pool
        self.log = log
        self._rounds = 0
        self._tokens = 0

    # ---------- session ----------

    def _ensure_session(self, role, ws, title, reuse_session_id=None):
        if reuse_session_id:
            self.backend.move(reuse_session_id, ws.location)
            return reuse_session_id
        if self.pool is not None:
            return self.pool.acquire(getattr(role, "name", "agent"), ws.location,
                                     role.model, title=title, agent=role.agent)
        return self.backend.create(ws.location, role.model, title=title, agent=role.agent)

    # ---------- referee loop ----------

    def _referee(self, sid, task, ws) -> str:
        """Watch the session without interrupting. Judge only when the agent
        signals completion, blocks, or goes idle long enough to have stopped."""
        last_msg_count = 0
        last_idle = 0.0
        start = time.time()

        while True:
            if time.time() - start > self.max_wallclock:
                self._archive(task, ws, "wallclock")
                return "BUDGET"

            time.sleep(self.poll_interval)
            state = self.backend.poll(sid)
            if not state:
                continue
            self._tokens = state.get("tokens", {}).get("input", 0)
            outcome = state.get("outcome")
            idle_ms = state.get("time", {}).get("idle", 0)

            # Agent explicitly says DONE
            msgs = self.backend.messages(sid)
            signal = self.backend.detect_signal(msgs)
            if signal == "BLOCKED":
                self._archive(task, ws, "blocked")
                return "BLOCKED"

            # Judge when the agent is finished, interrupted, or idle-long.
            turn_ended = outcome in ("succeeded", "failed", "interrupted")
            idle_too_long = idle_ms >= self.max_idle_before_judge * 1000 and outcome is None
            if signal == "DONE" or turn_ended or idle_too_long:
                res = self.policy.is_done(task, ws)
                if res.done:
                    self.log(f"[referee] gates passed for {task.id}")
                    fin = self.policy.finalize(task, ws)
                    if not fin.ok:
                        d = fin.detail if isinstance(fin.detail, dict) else {}
                        why = str(d.get("reason") or "")
                        if "code changed after verification" in why:
                            # agent is still writing; wait for it to settle
                            self.log(f"[referee] tree moved ({why[:40]}); wait for settle")
                            continue
                        if d.get("blocked"):
                            self._archive(task, ws, "finalize-blocked:" + why[:60])
                            return "BLOCKED"
                        self._archive(task, ws, "finalize-failed:" + why[:60])
                        return "FAILED"
                    try:
                        self.policy.release(task)
                    except Exception:
                        pass
                    return "DONE"
                # Not done yet. Do NOT nudge - let the agent keep working.
                # Only give a nudge if it clearly hit a wall (no new output,
                # not idle, but not making progress for many rounds).
                nmsg = len(msgs)
                if nmsg == last_msg_count and idle_ms < 60_000 and outcome is None:
                    # stalling without idle - maybe it thinks it's done but
                    # hasn't signaled; a gentle hint is OK.
                    self.log(f"[referee] agent stalling ({task.id}); hint once")
                    self.backend.prompt(sid,
                        "If you are blocked, say so. Otherwise keep going. "
                        "When truly done, reply exactly: {\"DONE\": true}.",
                        cwd=ws.location)
                last_msg_count = nmsg
                continue

            # Agent is actively working - never interrupt.
            if self._rounds % 5 == 0:
                self.log(f"[referee] working... {task.id} tokens={self._tokens} idle={int(idle_ms/1000)}s")

        return "BUDGET"

    # ---------- public ----------

    def run(self, goal: str, task: Optional[Task] = None, reuse_session_id: Optional[str] = None) -> Outcome:
        ws = self.policy.prepare(task)
        role = self.policy.role(task)
        sid = self._ensure_session(role, ws, goal[:60], reuse_session_id=reuse_session_id)
        if not sid:
            return Outcome("FAILED", detail="session create failed")
        self.log(f"[referee] session={sid} location={ws.location} role={role.name}")
        status = self._referee(sid, task, ws)
        return Outcome(status, sid, rounds=self._rounds, tokens=self._tokens,
                       detail={"role": role.name})

    def _archive(self, task, ws, reason):
        try:
            self.policy.archive(task, ws, reason)
        except Exception:
            pass
