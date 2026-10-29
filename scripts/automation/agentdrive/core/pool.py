"""SessionPool: reuse one long-lived session per role instead of per task.

Continuity lives in the session; isolation lives in the worktree (via move).
"""
import json
import time
from pathlib import Path
from typing import Dict, Optional

from .session import OpenCodeBackend


class SessionPool:
    def __init__(self, root: Path, backend: OpenCodeBackend,
                 state_path: Optional[Path] = None,
                 retire_tokens: int = 300_000, retire_age_s: int = 6 * 3600):
        self.root = Path(root)
        self.backend = backend
        self.state_path = state_path or (self.root / ".cache" / "agentdrive" / "sessions.json")
        self.state_path.parent.mkdir(parents=True, exist_ok=True)
        self.retire_tokens = retire_tokens
        self.retire_age_s = retire_age_s
        self._state: Dict[str, dict] = self._load()

    def _load(self) -> Dict[str, dict]:
        if self.state_path.exists():
            try:
                return json.loads(self.state_path.read_text(encoding="utf-8"))
            except Exception:
                return {}
        return {}

    def _save(self):
        self.state_path.write_text(json.dumps(self._state, indent=2, ensure_ascii=False),
                                   encoding="utf-8")

    def _needs_retire(self, rec: dict) -> bool:
        if time.time() - rec.get("created", 0) > self.retire_age_s:
            return True
        st = self.backend.poll(rec["session_id"])
        if st:
            tokens = st.get("tokens", {}).get("input", 0)
            if tokens >= self.retire_tokens:
                return True
        return False

    def acquire(self, role_key: str, location: str, model: Optional[dict],
                title: str = "", agent: str = "build") -> Optional[str]:
        """Return a session for this role, moved to `location`, creating if needed."""
        rec = self._state.get(role_key)
        sid = None
        if rec and rec.get("session_id"):
            st = self.backend.poll(rec["session_id"])
            if st and not self._needs_retire(rec):
                sid = rec["session_id"]
                # move to the current worktree so isolation is preserved
                self.backend.move(sid, location)
            else:
                self._retire(role_key, reason="threshold")
        if not sid:
            sid = self.backend.create(location, model, title=title or role_key, agent=agent)
            if not sid:
                return None
            self._state[role_key] = {"session_id": sid, "created": time.time(),
                                     "tasks": rec.get("tasks", []) if rec else []}
            self._save()
        return sid

    def record_task(self, role_key: str, task_id: str):
        rec = self._state.get(role_key)
        if rec is not None:
            rec.setdefault("tasks", []).append(task_id)
            self._save()

    def _retire(self, role_key: str, reason: str = ""):
        rec = self._state.get(role_key)
        if not rec:
            return
        # write a handoff note slot (agent may append later); keep it simple
        handoff_dir = self.root / ".cache" / "agentdrive" / "handoff"
        handoff_dir.mkdir(parents=True, exist_ok=True)
        note = handoff_dir / f"{role_key.replace('/', '_')}.md"
        if not note.exists():
            note.write_text(f"# Handoff: {role_key}\n(retired: {reason})\n", encoding="utf-8")
        self._state.pop(role_key, None)
        self._save()

    def retire(self, role_key: str, reason: str = "manual"):
        self._retire(role_key, reason)
