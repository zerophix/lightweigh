"""合并队列端到端烟测（DEC-068/069）。

背景：队列脚本今天连续失败 6 次（trap 变量、提交信息 type、中文变量、缺
local.properties、共享状态冲突、JAVA_HOME）。单元测试只覆盖片段，抓不到
"整条链走不通"。本文件用临时仓库 + 桩脚本跑完整链路：
  建候选 → 合并分支 → 验证 → 推送 master → 删分支/worktree → 回填计时
"""
import json
import os
import subprocess
from pathlib import Path

ROOT_P = Path(__file__).resolve().parents[2]
QUEUE = ROOT_P / "scripts" / "workflow" / "wf-queue.sh"


def _git(root: Path, *args: str, check: bool = True):
    return subprocess.run(
        ["git", "-c", "user.email=t@t", "-c", "user.name=t", *args],
        cwd=root, capture_output=True, text=True, check=check)


def _write(path: Path, text: str, mode: int = 0o644):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")
    path.chmod(mode)


def _env(root: Path) -> Path:
    """构造最小可用仓库：远端 + 队列脚本 + 桩 local-ci/board/reconcile。"""
    remote = root / "remote.git"
    subprocess.run(["git", "init", "-q", "--bare", str(remote)], check=True)
    repo = root / "repo"
    repo.mkdir()
    _git(repo, "init", "-q")
    _git(repo, "checkout", "-qB", "master")
    _git(repo, "remote", "add", "origin", str(remote))

    _write(repo / "README.md", "x\n")
    _write(repo / "local.properties", "sdk.dir=/tmp/fake\n")

    # 队列本体
    _write(repo / "scripts/workflow/wf-queue.sh", QUEUE.read_text(encoding="utf-8"), 0o755)

    # 桩：local-ci 恒通过（真实 gradle 不在本测试范围）
    _write(repo / "scripts/governance/gov-local-ci.sh",
           "#!/usr/bin/env bash\necho '通过: 6  失败: 0'\necho 'PASS gov-local-ci'\nexit 0\n", 0o755)
    # 桩：回填与同步（真实实现依赖 gh）
    _write(repo / "scripts/governance/gov-board-reconcile.sh", "#!/usr/bin/env bash\nexit 0\n", 0o755)
    _write(repo / "scripts/governance/gov-board-sync.sh", "#!/usr/bin/env bash\nexit 0\n", 0o755)
    _write(repo / "scripts/governance/gov-validate.sh", "#!/usr/bin/env bash\nexit 0\n", 0o755)
    # 证据消费（DEC-078）：队列会调用它；本 fixture 的提交无 Task trailer，应直接放行
    _write(repo / "scripts/governance/gov-evidence-check.sh",
           (ROOT_P / "scripts" / "governance" / "gov-evidence-check.sh").read_text(encoding="utf-8"),
           0o755)
    _write(repo / "scripts/session/ses-worktree.sh", "#!/usr/bin/env bash\nexit 0\n", 0o755)
    _write(repo / "scripts/session/ses-claim-staged.sh", "#!/usr/bin/env bash\nexit 0\n", 0o755)

    # 任务板（通过严格审计需要：batches、groups、tasks 均含必填字段）
    _write(repo / ".ai/tasks.json", json.dumps({
        "version": 1,
        "batches": [{"id": "B-demo", "title": "演示批次", "tasks": ["T-001", "T-002", "T-003"]}],
        "groups": [{"id": "demo", "title": "演示组", "tasks": ["T-001", "T-002", "T-003"]}],
        "tasks": [
            {"id": "T-001", "status": "completed", "description": "底座", "group_id": "demo",
             "target_files": ["a.kt"], "acceptance": {"status": "ready", "verify_commands": ["true"]},
             "role": "dev", "batch": "B-demo", "source": "gap", "source_ref": "", "priority": "P0"},
            {"id": "T-002", "status": "pending", "description": "依赖底座", "group_id": "demo",
             "depends_on": ["T-001"], "target_files": ["b.kt"],
             "acceptance": {"status": "ready", "verify_commands": ["true"]},
             "role": "dev", "batch": "B-demo", "source": "gap", "source_ref": "", "priority": "P0"},
            {"id": "T-003", "status": "pending", "description": "依赖第二步", "group_id": "demo",
             "depends_on": ["T-001"], "target_files": ["c.kt"],
             "acceptance": {"status": "ready", "verify_commands": ["true"]},
             "role": "dev", "batch": "B-demo", "source": "gap", "source_ref": "", "priority": "P0"},
        ],
    }, ensure_ascii=False), 0o644)
    # 审计脚本
    _write(repo / "scripts/workflow/wf-board-audit.sh",
           (ROOT_P / "scripts" / "workflow" / "wf-board-audit.sh").read_text(encoding="utf-8"), 0o755)
    # validate 依赖的模块
    _write(repo / "scripts/governance/modules/gov_validate_status.py",
           (ROOT_P / "scripts" / "governance" / "modules" / "gov_validate_status.py").read_text(encoding="utf-8"), 0o644)

    _git(repo, "add", "-A")
    _git(repo, "commit", "-qm", "init")
    _git(repo, "push", "-q", "-u", "origin", "master")

    # 假 gh：队列只用 pr list/close
    bindir = root / "bin"
    bindir.mkdir(exist_ok=True)
    _write(bindir / "gh",
           "#!/usr/bin/env bash\n"
           "case \"$1 $2\" in\n"
           "  'pr list') exit 0 ;;\n"
           "  'pr close') exit 0 ;;\n"
           "esac\nexit 0\n", 0o755)
    return repo


def test_queue_end_to_end(tmp_path):
    repo = _env(tmp_path)

    # 两个文件不相交的分支
    for name in ("feat/a", "feat/b"):
        _git(repo, "checkout", "-qB", name, "master")
        _write(repo / f"{name.split('/')[1]}.txt", name)
        _git(repo, "add", "-A")
        _git(repo, "commit", "-qm", f"feat: {name}")
        _git(repo, "push", "-q", "-u", "origin", name)
    _git(repo, "checkout", "-q", "master")

    # 为分支建 worktree（队列要求本地存在 worktree）
    for name in ("feat/a", "feat/b"):
        wt = tmp_path / name.replace("/", "-")
        _git(repo, "worktree", "add", "-q", str(wt), name)

    env = {**os.environ, "PATH": f"{tmp_path / 'bin'}:{os.environ['PATH']}",
           "WF_QUEUE_ROOT": str(repo), "WF_BOARD_ROOT": str(repo)}
    res = subprocess.run(["bash", str(repo / "scripts/workflow/wf-queue.sh"),
                          "run", "--lanes", "A", "feat/a", "feat/b"],
                         cwd=repo, capture_output=True, text=True, env=env, timeout=300)

    assert res.returncode == 0, res.stdout + res.stderr
    # master 前进并包含两个分支的内容
    files = _git(repo, "ls-tree", "-r", "--name-only", "origin/master").stdout
    assert "a.txt" in files and "b.txt" in files
    # 候选目录被清理（trap 生效）
    assert not list(tmp_path.glob("repo-queue-*"))
    # 分支与 worktree 被清理
    branches = _git(repo, "branch", "--format=%(refname:short)").stdout
    assert "feat/a" not in branches and "feat/b" not in branches
    # 计时记账
    log = repo / ".cache" / "queue-runs.jsonl"
    assert log.is_file()
    rec = json.loads(log.read_text(encoding="utf-8").strip().splitlines()[-1])
    assert rec["branches"] == 2 and rec["result"] == "ok"
