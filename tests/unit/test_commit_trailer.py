"""提交 trailer 注入契约（DEC-073）。

任务归属的第一权威是提交上的 `Task: T-XXX`，不是分支名。钩子在两种情况
下必须做出正确判断：

  1. 本会话持有该任务锁，且暂存文件命中该任务的 target_files → 写入 trailer；
  2. 锁属于别的会话（历史遗留）→ 绝不写入，否则每次提交都会被贴上别人的任务号。
"""

from __future__ import annotations

import json
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
HOOK = ROOT / "scripts/governance/gov-prepare-commit-msg"


def _git(root: Path, *args: str) -> None:
    subprocess.run(
        ["git", "-c", "user.email=t@t", "-c", "user.name=t", *args],
        cwd=root, capture_output=True, text=True, check=True,
    )


def _repo(tmp_path: Path, owner: str, session: str, target: str) -> Path:
    root = tmp_path / "repo"
    (root / ".ai" / "locks" / "T-999").mkdir(parents=True)
    (root / "src").mkdir()
    (root / ".ai" / "tasks.json").write_text(
        json.dumps({"tasks": [{"id": "T-999", "status": "pending", "target_files": [target]}]}),
        encoding="utf-8",
    )
    (root / ".ai" / "current-session.json").write_text(
        json.dumps({"session_id": session}), encoding="utf-8"
    )
    (root / ".ai" / "locks" / "T-999" / "owner").write_text(owner + "\n", encoding="utf-8")
    (root / target).parent.mkdir(parents=True, exist_ok=True)
    (root / target).write_text("// probe\n", encoding="utf-8")
    _git(root, "init", "-q")
    _git(root, "add", "-A")
    return root


def _run(root: Path, msg: Path) -> None:
    # 钩子按自身路径推断仓库根，所以必须在被测仓库内放一份副本
    hook = root / "scripts" / "governance" / "gov-prepare-commit-msg"
    hook.parent.mkdir(parents=True, exist_ok=True)
    hook.write_text(HOOK.read_text(encoding="utf-8"), encoding="utf-8")
    subprocess.run(["bash", str(hook), str(msg), ""], cwd=root,
                   capture_output=True, text=True, check=True)


def test_trailer_written_for_own_lock_and_matching_file(tmp_path: Path):
    root = _repo(tmp_path, owner="ses-mine", session="ses-mine", target="src/A.kt")
    msg = tmp_path / "msg.txt"
    msg.write_text("feat(x): subject\n\nbody\n", encoding="utf-8")

    _run(root, msg)

    assert "Task: T-999" in msg.read_text(encoding="utf-8")


def test_no_trailer_for_foreign_lock(tmp_path: Path):
    root = _repo(tmp_path, owner="ses-other", session="ses-mine", target="src/A.kt")
    msg = tmp_path / "msg.txt"
    msg.write_text("feat(x): subject\n\nbody\n", encoding="utf-8")

    _run(root, msg)

    assert "Task:" not in msg.read_text(encoding="utf-8")


def test_no_trailer_without_matching_staged_file(tmp_path: Path):
    root = _repo(tmp_path, owner="ses-mine", session="ses-mine", target="src/A.kt")
    (root / "src" / "Other.kt").write_text("// other\n", encoding="utf-8")
    _git(root, "add", "src/Other.kt")
    _git(root, "rm", "-q", "--cached", "src/A.kt")
    msg = tmp_path / "msg.txt"
    msg.write_text("feat(x): subject\n\nbody\n", encoding="utf-8")

    _run(root, msg)

    assert "Task:" not in msg.read_text(encoding="utf-8")
