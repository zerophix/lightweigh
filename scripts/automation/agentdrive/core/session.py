"""OpenCode backend. The only place that talks to `opencode api`."""
import json
import subprocess
from pathlib import Path
from typing import Any, Dict, List, Optional


class OpenCodeBackend:
    """Thin client over `opencode api` for session create/prompt/poll/compact."""

    def __init__(self, home: Path = None):
        # home = directory from which API calls are made (usually project root)
        self.home = Path(home) if home else Path.cwd()

    def _api(self, method: str, path: str, body: Optional[dict] = None,
             cwd: Optional[Path] = None, timeout: int = 60) -> Optional[Any]:
        cmd = ["opencode", "api", method, path]
        if body is not None:
            cmd += ["--data", json.dumps(body, ensure_ascii=False)]
        try:
            r = subprocess.run(cmd, cwd=str(cwd or self.home),
                               capture_output=True, text=True, timeout=timeout)
        except subprocess.TimeoutExpired:
            return None
        if r.returncode != 0:
            return None
        try:
            return json.loads(r.stdout)
        except json.JSONDecodeError:
            return None

    # ---------- lifecycle ----------

    def create(self, location: str, model: Optional[dict] = None,
               title: str = "", agent: str = "build") -> Optional[str]:
        body: Dict[str, Any] = {"title": title or "agentdrive", "agent": agent,
                                "location": {"directory": location}}
        if model:
            body["model"] = model
        res = self._api("post", "/api/session", body, cwd=Path(location), timeout=60)
        if res and "data" in res:
            return res["data"]["id"]
        return None

    def prompt(self, session_id: str, text: str, cwd: Optional[str] = None) -> bool:
        res = self._api("post", f"/api/session/{session_id}/prompt",
                        {"text": text}, cwd=Path(cwd) if cwd else None, timeout=30)
        return bool(res and "data" in res)

    def poll(self, session_id: str) -> Optional[dict]:
        res = self._api("get", f"/api/session/{session_id}", timeout=15)
        if res and isinstance(res, dict):
            return res.get("data")
        return None

    def messages(self, session_id: str) -> List[dict]:
        res = self._api("get", f"/api/session/{session_id}/message", timeout=15)
        if res and isinstance(res, dict):
            return res.get("data", []) or []
        return []

    def compact(self, session_id: str) -> bool:
        res = self._api("post", f"/api/session/{session_id}/compact", {}, timeout=120)
        return res is not None

    def move(self, session_id: str, directory: str) -> bool:
        res = self._api("post", f"/api/session/{session_id}/move",
                        {"directory": directory}, timeout=15)
        return res is not None

    def delete(self, session_id: str) -> bool:
        self._api("delete", f"/api/session/{session_id}", timeout=15)
        return True

    # ---------- signals ----------

    @staticmethod
    def latest_agent_text(messages: List[dict]) -> str:
        parts = []
        for m in messages:
            if m.get("type") != "assistant":
                continue
            for c in m.get("content", []) or []:
                if c.get("type") == "text" and c.get("text"):
                    parts.append(c["text"])
        return "\n".join(parts)

    @classmethod
    def detect_signal(cls, messages: List[dict]) -> str:
        text = cls.latest_agent_text(messages).upper().replace(" ", "")
        if '"DONE":TRUE' in text or '"DONE":TRUE,' in text:
            return "DONE"
        if '"BLOCKED":TRUE' in text:
            return "BLOCKED"
        tail = cls.latest_agent_text(messages).strip().splitlines()[-3:] if messages else []
        if any(l.strip().strip('`"').strip().upper() == "DONE" for l in tail):
            return "DONE"
        return "WORKING"
