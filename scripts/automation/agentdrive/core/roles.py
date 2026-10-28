"""Role registry + dispatcher. Project provides .agentdrive/roles.yaml.

Dispatch order: verify_kind -> module_map -> path globs -> default.
"""
import fnmatch
from dataclasses import dataclass, field
from pathlib import Path
from typing import Dict, List, Optional

from .types import Task


def _verify_kind(task: Task) -> str:
    cmds = " ".join((task.acceptance or {}).get("verify_commands", []) or [])
    if "gov-prototype-verify" in cmds:
        return "prototype"
    if "pytest" in cmds:
        return "pytest"
    if "gradlew" in cmds or "gradle" in cmds:
        return "gradle"
    if "gov-local-ci" in cmds or "gov-validate" in cmds:
        return "local-ci"
    return "other"


@dataclass
class RoleSpec:
    id: str
    responsibilities: str = ""
    verify_kind: str = "other"
    profile: str = "code"
    allow_paths: List[str] = field(default_factory=list)
    model: Optional[dict] = None


class RoleRegistry:
    def __init__(self, path: str):
        self.path = Path(path)
        self.roles: Dict[str, RoleSpec] = {}
        self.routing: dict = {}
        self._load()

    def _load(self):
        try:
            import yaml
            data = yaml.safe_load(self.path.read_text(encoding="utf-8")) or {}
        except Exception:
            return
        for rid, spec in (data.get("roles") or {}).items():
            m = spec.get("model")
            model = None
            if m:
                model = {"id": m["id"], "providerID": m["provider"],
                         "variant": m.get("variant", "default")}
            self.roles[rid] = RoleSpec(
                id=rid,
                responsibilities=spec.get("responsibilities", ""),
                verify_kind=spec.get("verify_kind", "other"),
                profile=spec.get("profile", "code"),
                allow_paths=spec.get("allow_paths", []) or [],
                model=model,
            )
        self.routing = data.get("routing") or {}

    def _by_verify(self, kind: str) -> Optional[str]:
        for rid, spec in self.roles.items():
            if spec.verify_kind == kind:
                return rid
        return None

    def _by_path(self, task: Task) -> Optional[str]:
        files = task.target_files or []
        for rid, spec in self.roles.items():
            for pattern in spec.allow_paths:
                for f in files:
                    if fnmatch.fnmatch(f, pattern):
                        return rid
        return None

    def dispatch(self, task: Task) -> str:
        kind = _verify_kind(task)
        rid = self._by_verify(kind)
        if rid:
            return rid
        module = (task.meta or {}).get("module") or ""
        rid = (self.routing.get("module_map") or {}).get(module)
        if rid:
            return rid
        rid = self._by_path(task)
        if rid:
            return rid
        return self.routing.get("default", "backend-dev")

    def spec(self, rid: str) -> Optional[RoleSpec]:
        return self.roles.get(rid)
