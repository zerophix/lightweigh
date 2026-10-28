"""Event log + guardrails (budget, pause). Project-agnostic."""
import json
import time
from pathlib import Path
from typing import Optional


class EventLog:
    def __init__(self, path: str):
        self.path = Path(path)
        self.path.parent.mkdir(parents=True, exist_ok=True)

    def emit(self, event: str, **kw):
        rec = {"ts": time.time(), "event": event, **kw}
        with open(self.path, "a", encoding="utf-8") as f:
            f.write(json.dumps(rec, ensure_ascii=False) + "\n")


class Guard:
    def __init__(self, max_rounds=60, budget_tokens=1_000_000, max_wallclock_s=5400,
                 pause_token: Optional[str] = None):
        self.max_rounds = max_rounds
        self.budget_tokens = budget_tokens
        self.max_wallclock_s = max_wallclock_s
        self.pause_token = pause_token
        self.start = time.time()

    def rounds_exceeded(self, rnd: int) -> bool:
        return rnd >= self.max_rounds

    def budget_exceeded(self, tokens: int) -> bool:
        return tokens >= self.budget_tokens

    def wallclock_exceeded(self) -> bool:
        return (time.time() - self.start) >= self.max_wallclock_s

    def paused(self) -> bool:
        if not self.pause_token:
            return False
        p = Path(self.pause_token)
        if not p.exists():
            return False
        try:
            return bool(json.loads(p.read_text(encoding="utf-8")).get("paused"))
        except Exception:
            return True
