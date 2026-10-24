"""隐形失效防护（DEC-067）测试：钩子副本漂移 + 任务板回填。

这两项都是"没有报错但已经失效"的典型：
  - 钩子副本过期 → 以为有门禁，实际跑旧版
  - 合并后不回填任务板 → 已交付任务被再次派发（重复实现）
"""
import json
import os
import stat
import subprocess
from pathlib import Path

ROOT_P = Path(__file__).resolve().parents[2]
HOOK_DOCTOR = ROOT_P / "scripts" / "governance" / "gov-hook-doctor.sh"
RECONCILE = ROOT_P / "scripts" / "governance" / "gov-board-reconcile.sh"


def _run(script: Path, *args, root=None, env_extra=None):
    env = dict(os.environ)
    if root is not None:
        env["WF_AUTOPILOT_ROOT"] = str(root)
    if env_extra:
        env.update(env_extra)
    return subprocess.run(["bash", str(script), *args],
                          capture_output=True, text=True, env=env)


def _git(root: Path, *args: str):
    subprocess.run(
        ["git", "-c", "user.email=t@t", "-c", "user.name=t", *args],
        cwd=root, capture_output=True, text=True, check=True)


def test_scripts_syntax():
    for s in (HOOK_DOCTOR, RECONCILE):
        assert subprocess.run(["bash", "-n", str(s)]).returncode == 0


# ---------------------------------------------------------------- 钩子漂移

def _hook_repo(tmp_path: Path) -> Path:
    root = tmp_path / "repo"
    (root / "scripts" / "governance").mkdir(parents=True)
    (root / "scripts" / "bootstrap").mkdir(parents=True)
    (root / "scripts" / "governance" / "gov-pre-push").write_text("#!/bin/sh\necho new\n")
    installer = root / "scripts" / "bootstrap" / "bs-install-hooks.sh"
    installer.write_text("#!/bin/sh\nexit 0\n")
    installer.chmod(installer.stat().st_mode | stat.S_IEXEC)
    _git(root, "init", "-q")
    _git(root, "add", "-A")
    _git(root, "commit", "-qm", "init")
    return root


def _install_copy(root: Path, content: str):
    hooks = root / ".git" / "hooks"
    hooks.mkdir(parents=True, exist_ok=True)
    p = hooks / "pre-push"
    p.write_text(content)
    p.chmod(0o755)


def test_hook_doctor_detects_drift(tmp_path):
    root = _hook_repo(tmp_path)
    _install_copy(root, "#!/bin/sh\necho OLD\n")

    res = _run(HOOK_DOCTOR, root=root)

    assert res.returncode == 1
    assert "DRIFT" in res.stdout


def test_hook_doctor_ok_when_identical(tmp_path):
    root = _hook_repo(tmp_path)
    src = (root / "scripts" / "governance" / "gov-pre-push").read_text()
    _install_copy(root, src)

    res = _run(HOOK_DOCTOR, root=root)

    assert res.returncode == 0, res.stdout + res.stderr
    assert "一致" in res.stdout


def test_hook_doctor_detects_missing(tmp_path):
    root = _hook_repo(tmp_path)

    res = _run(HOOK_DOCTOR, root=root)

    assert res.returncode == 1
    assert "MISSING" in res.stdout


def test_hook_doctor_json(tmp_path):
    root = _hook_repo(tmp_path)
    _install_copy(root, "#!/bin/sh\necho OLD\n")

    res = _run(HOOK_DOCTOR, "--json", root=root)

    data = json.loads(res.stdout)
    assert data["drift"], data
    assert data["drift"][0]["hook"] == "pre-push"


# ---------------------------------------------------------------- 任务板回填

def _board_repo(tmp_path: Path, tasks: list) -> Path:
    root = tmp_path / "repo"
    (root / ".ai").mkdir(parents=True)
    (root / ".ai" / "tasks.json").write_text(
        json.dumps({"tasks": tasks}, ensure_ascii=False), encoding="utf-8")
    _git(root, "init", "-q")
    return root


def _fake_gh(tmp_path: Path, prs: list) -> Path:
    """把 gh 替换成固定输出，避免测试依赖网络/认证。"""
    bindir = tmp_path / "bin"
    bindir.mkdir(exist_ok=True)
    gh = bindir / "gh"
    gh.write_text(
        "#!/bin/sh\ncat <<'JSON'\n" + json.dumps(prs, ensure_ascii=False) + "\nJSON\n")
    gh.chmod(0o755)
    return bindir


def _task(tid: str, status: str, ready: bool = True) -> dict:
    return {
        "id": tid,
        "status": status,
        "acceptance": {"status": "ready" if ready else "draft"},
        "target_files": [],
    }


def test_reconcile_marks_merged_tasks_completed(tmp_path):
    root = _board_repo(tmp_path, [_task("T-072", "in_progress"), _task("T-099", "pending")])
    bindir = _fake_gh(tmp_path, [
        {"number": 59, "title": "feat(auth): login (T-072)",
         "body": "T-074 also", "mergedAt": "2026-10-24T04:00:00Z"},
    ])

    res = _run(RECONCILE, root=root,
               env_extra={"PATH": f"{bindir}:{os.environ['PATH']}"})

    assert res.returncode == 0, res.stdout + res.stderr
    data = json.loads((root / ".ai" / "tasks.json").read_text())
    by_id = {t["id"]: t for t in data["tasks"]}
    assert by_id["T-072"]["status"] == "completed"
    assert "PR #59" in by_id["T-072"]["notes"]
    assert by_id["T-099"]["status"] == "pending"      # 未出现在 PR 中，不动


def test_reconcile_skips_draft_acceptance(tmp_path):
    root = _board_repo(tmp_path, [_task("T-050", "in_progress", ready=False)])
    bindir = _fake_gh(tmp_path, [
        {"number": 1, "title": "chore: x (T-050)", "body": "",
         "mergedAt": "2026-10-24T04:00:00Z"},
    ])

    _run(RECONCILE, root=root, env_extra={"PATH": f"{bindir}:{os.environ['PATH']}"})

    data = json.loads((root / ".ai" / "tasks.json").read_text())
    assert data["tasks"][0]["status"] == "in_progress"


def test_reconcile_dry_run_does_not_write(tmp_path):
    root = _board_repo(tmp_path, [_task("T-072", "in_progress")])
    bindir = _fake_gh(tmp_path, [
        {"number": 59, "title": "T-072", "body": "", "mergedAt": "2026-10-24T04:00:00Z"},
    ])
    before = (root / ".ai" / "tasks.json").read_text()

    _run(RECONCILE, "--dry", root=root,
         env_extra={"PATH": f"{bindir}:{os.environ['PATH']}"})

    assert (root / ".ai" / "tasks.json").read_text() == before


def test_reconcile_respects_since_days(tmp_path):
    root = _board_repo(tmp_path, [_task("T-072", "in_progress")])
    bindir = _fake_gh(tmp_path, [
        {"number": 1, "title": "T-072", "body": "", "mergedAt": "2020-01-01T00:00:00Z"},
    ])
    before = (root / ".ai" / "tasks.json").read_text()

    _run(RECONCILE, "--since-days", "30", root=root,
         env_extra={"PATH": f"{bindir}:{os.environ['PATH']}"})

    assert (root / ".ai" / "tasks.json").read_text() == before


# ---------------------------------------------------------------- 共享状态守护（DEC-067）

PRE_COMMIT = ROOT_P / "scripts" / "governance" / "gov-pre-commit-simplified"


def _precommit_repo(tmp_path: Path) -> Path:
    root = tmp_path / "repo"
    (root / ".ai").mkdir(parents=True)
    (root / ".ai" / "tasks.json").write_text('{"tasks": []}', encoding="utf-8")
    (root / "app").mkdir()
    (root / "app" / "A.kt").write_text("class A\n", encoding="utf-8")
    # 钩子用 ROOT/scripts/governance/gov-custom-checks.sh 的存在性判断是否回落主树
    (root / "scripts" / "governance").mkdir(parents=True)
    (root / "scripts" / "governance" / "gov-custom-checks.sh").write_text("#!/bin/sh\nexit 0\n")
    _git(root, "init", "-q")
    _git(root, "checkout", "-qB", "feat/x")
    _git(root, "add", "-A")
    _git(root, "commit", "-qm", "base")
    return root


def test_precommit_rejects_shared_derived_files(tmp_path):
    """功能分支提交 tasks.json 必须被拒（单一写者，DEC-064/067）。"""
    root = _precommit_repo(tmp_path)
    (root / ".ai" / "tasks.json").write_text('{"tasks": [], "x": 1}', encoding="utf-8")
    _git(root, "add", ".ai/tasks.json")

    res = subprocess.run(["bash", str(PRE_COMMIT)], cwd=root,
                         capture_output=True, text=True)

    assert res.returncode != 0
    assert "禁止在功能分支提交派生数据" in (res.stdout + res.stderr)


def test_precommit_allows_normal_source_commit(tmp_path):
    """普通源码提交不受共享状态守护影响。"""
    root = _precommit_repo(tmp_path)
    (root / "app" / "A.kt").write_text("class A { val x = 1 }\n", encoding="utf-8")
    _git(root, "add", "app/A.kt")

    res = subprocess.run(["bash", str(PRE_COMMIT)], cwd=root,
                         capture_output=True, text=True)

    out = res.stdout + res.stderr
    assert "禁止在功能分支提交派生数据" not in out
