"""Core value objects for agentdrive. No project-specific words allowed here."""
from dataclasses import dataclass, field
from typing import Any, Dict, List, Optional


@dataclass
class Task:
    """A unit of work. For project-less usage this may be synthesized from the goal."""
    id: str
    title: str = ""
    target_files: List[str] = field(default_factory=list)
    acceptance: Dict[str, Any] = field(default_factory=dict)
    meta: Dict[str, Any] = field(default_factory=dict)


@dataclass
class Workspace:
    """Where the agent should work."""
    location: str
    meta: Dict[str, Any] = field(default_factory=dict)


@dataclass
class Role:
    """Which agent/model should execute."""
    name: str = "agent"
    model: Optional[Dict[str, Any]] = None  # {id, providerID, variant}
    agent: str = "build"


@dataclass
class DoneResult:
    done: bool
    reason: str = ""
    evidence: Any = None


@dataclass
class FinalizeResult:
    ok: bool
    detail: Any = None


@dataclass
class Outcome:
    status: str            # DONE | BLOCKED | BUDGET | FAILED
    session_id: str = ""
    rounds: int = 0
    tokens: int = 0
    detail: Any = None
