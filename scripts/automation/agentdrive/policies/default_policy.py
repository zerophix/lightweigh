"""Default policy: works on any repo with zero project knowledge.

Completion is a shell command's exit code. Changes are left in place.
"""
import subprocess
from pathlib import Path
from typing import Dict, List, Optional

from ..core.policy import ProjectPolicy
from ..core.types import Task, Workspace, Role, DoneResult, FinalizeResult


class DefaultPolicy(ProjectPolicy):
    def __init__(self, root: str = ".", done_command: Optional[str] = None,
                 model: Optional[Dict] = None, agent: str = "build"):
        super().__init__(root)
        self.done_command = done_command
        self.model = model
        self.agent = agent

    def prepare(self, task: Optional[Task]) -> Workspace:
        return Workspace(location=self.root)

    def role(self, task: Optional[Task]) -> Role:
        return Role(name="default", model=self.model, agent=self.agent)

    def is_done(self, task: Optional[Task], ws: Workspace) -> DoneResult:
        if not self.done_command:
            return DoneResult(done=False, reason="no --done command supplied")
        try:
            r = subprocess.run(self.done_command, shell=True, cwd=ws.location,
                               capture_output=True, text=True, timeout=600)
        except subprocess.TimeoutExpired:
            return DoneResult(done=False, reason="done command timeout")
        return DoneResult(
            done=(r.returncode == 0),
            reason=f"exit={r.returncode}",
            evidence={"stdout": (r.stdout or "")[-800:], "stderr": (r.stderr or "")[-800:]},
        )

    def finalize(self, task: Optional[Task], ws: Workspace) -> FinalizeResult:
        return FinalizeResult(ok=True, detail="changes left in workspace")
