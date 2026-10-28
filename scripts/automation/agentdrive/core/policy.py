"""ProjectPolicy: the only seam between the generic engine and a specific project."""
from abc import ABC
from typing import Any, List, Optional

from .types import Task, Workspace, Role, DoneResult, FinalizeResult


class ProjectPolicy(ABC):
    """Override any subset. Defaults make agentdrive usable on a bare repo."""

    # 1) Work source (optional)
    def next_task(self) -> Optional[Task]:
        return None

    # 2) Prepare workspace (optional: locks, worktrees, mode switches...)
    def prepare(self, task: Optional[Task]) -> Workspace:
        return Workspace(location=str(self.root))

    # 3) Completion check (REQUIRED for real use; weak default = never auto-done)
    def is_done(self, task: Optional[Task], ws: Workspace) -> DoneResult:
        return DoneResult(done=False, reason="no done-checker configured")

    # 4) Finalize (optional: commit/push/PR). Default = leave changes in place.
    def finalize(self, task: Optional[Task], ws: Workspace) -> FinalizeResult:
        return FinalizeResult(ok=True, detail="changes left in workspace")

    # 5) Memory to load into a fresh session (paths, relative to ws.location)
    def memory(self, task: Optional[Task], ws: Workspace) -> List[str]:
        return ["AGENTS.md", "README.md"]

    # 6) Release task ownership after successful delivery (optional)
    def release(self, task: Optional[Task]) -> None:
        return None

    # 7) Archive evidence on abort (optional; never delete silently)
    def archive(self, task: Optional[Task], ws: Workspace, reason: str = "") -> None:
        return None

    # 8) Role / model
    def role(self, task: Optional[Task]) -> Role:
        return Role()

    # ---- helpers ----
    def __init__(self, root: str = "."):
        import os
        self.root = os.path.abspath(root)

    def target_files(self, task: Optional[Task]) -> List[str]:
        return list(task.target_files) if task else []
