#!/usr/bin/env python3
"""合并队列一条命令 + worktree 自动清理的确定性测试（DEC-064）。

覆盖的两条真实痛点链路：

  - `gov-merge.sh <PR>`：PR 拉平主干 → 派生物同步 → local-ci → 合入 → 清理
  - `ses-worktree.sh clean`：只清理「已合入主干」的 worktree，未合入的一律保留

测试边界（刻意不碰真实环境）：
  - 不调用真实 `gh`（用 PATH 前置的假 gh 脚本，断言错误/计划格式）
  - 不跑 gradle / gov-local-ci（只断言参数与错误路径）
  - worktree 数据用临时 git 仓库里的真实 `git worktree add` 造出来

运行: python3 -m pytest tests/unit/test_merge_queue.py -q
"""
import os
import shutil
import subprocess
import sys
from pathlib import Path

ROOT_P = Path(__file__).resolve().parents[2]
if str(ROOT_P) not in sys.path:
    sys.path.insert(0, str(ROOT_P))

MERGE_SH = ROOT_P / "scripts" / "governance" / "gov-merge.sh"
WT_SH = ROOT_P / "scripts" / "session" / "ses-worktree.sh"

# gov-merge.sh 用「本目录 + ../..」回推 ROOT；临时仓库里放一份副本，
# 保证被测脚本操作的是临时仓库而不是本仓。
_GOV_SCRIPTS = (
    "scripts/governance/gov-merge.sh",
    "scripts/governance/gov-board-sync.sh",
)


def _clean_git_env() -> dict:
    """剥离宿主 git 环境变量（pre-push 会注入 GIT_DIR 等，会把临时仓库指向真实仓库）。"""
    return {k: v for k, v in os.environ.items() if not k.startswith("GIT_")}


def _run(cmd, cwd: Path, env_extra: dict | None = None, timeout: int = 120):
    env = _clean_git_env()
    if env_extra:
        env.update(env_extra)
    return subprocess.run(
        [str(c) for c in cmd], cwd=str(cwd), capture_output=True, text=True,
        timeout=timeout, env=env,
    )


def _git(cwd: Path, *args: str) -> str:
    r = _run(["git", *args], cwd)
    assert r.returncode == 0, f"git {' '.join(args)} 失败: {r.stderr}"
    return r.stdout


def _init_repo(path: Path) -> None:
    path.mkdir(parents=True, exist_ok=True)
    _git(path, "init", "-q", "-b", "master")
    _git(path, "config", "user.email", "test@example.com")
    _git(path, "config", "user.name", "Test")
    _git(path, "config", "commit.gpgsign", "false")
    (path / "README.md").write_text("# temp repo\n", encoding="utf-8")
    (path / ".gitignore").write_text("local.properties\n", encoding="utf-8")
    _git(path, "add", ".")
    _git(path, "commit", "-q", "-m", "init")


def _install_gov_scripts(repo: Path) -> None:
    for rel in _GOV_SCRIPTS:
        dst = repo / rel
        dst.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(ROOT_P / rel, dst)


def _fake_gh(bin_dir: Path, body: str) -> None:
    bin_dir.mkdir(parents=True, exist_ok=True)
    gh = bin_dir / "gh"
    gh.write_text("#!/usr/bin/env bash\n" + body, encoding="utf-8")
    gh.chmod(0o755)


def _path_with(bin_dir: Path) -> dict:
    return {"PATH": f"{bin_dir}{os.pathsep}{os.environ.get('PATH', '')}"}


# ---------------------------------------------------------------- 语法

def test_shell_scripts_parse():
    """两个脚本必须能被 bash 解析（防止提交半成品）。"""
    for script in (MERGE_SH, WT_SH):
        assert script.is_file(), f"脚本缺失: {script}"
        r = _run(["bash", "-n", script], ROOT_P)
        assert r.returncode == 0, f"{script.name} 语法错误: {r.stderr}"


# ---------------------------------------------------------------- gov-merge 用法与错误路径

def test_gov_merge_help_prints_usage():
    r = _run(["bash", MERGE_SH, "--help"], ROOT_P)
    assert r.returncode == 0, r.stderr
    assert "gov-merge.sh <PR号>" in r.stdout
    assert "--dry-run" in r.stdout and "--keep-worktree" in r.stdout


def test_gov_merge_requires_pr_number():
    r = _run(["bash", MERGE_SH], ROOT_P)
    assert r.returncode != 0
    assert "用法" in (r.stderr + r.stdout)


def test_gov_merge_rejects_non_numeric_pr():
    r = _run(["bash", MERGE_SH, "abc"], ROOT_P)
    assert r.returncode != 0
    assert "必须是数字" in (r.stderr + r.stdout)


def test_gov_merge_reports_gh_failure(tmp_path):
    """gh 读不到 PR 时必须给出明确错误 + 可执行排查提示（不静默失败）。"""
    repo = tmp_path / "repo"
    _init_repo(repo)
    _install_gov_scripts(repo)
    bin_dir = tmp_path / "bin"
    _fake_gh(bin_dir, 'echo "gh: Not logged in" >&2\nexit 1\n')

    r = _run(["bash", "scripts/governance/gov-merge.sh", "999"], repo,
             env_extra=_path_with(bin_dir))

    assert r.returncode != 0
    combined = r.stdout + r.stderr
    assert "无法读取 PR #999" in combined, combined
    assert "gh auth status" in combined, "缺少 gh 登录排查提示"
    assert "gh: Not logged in" in combined, "未透传 gh 原始错误信息"


def test_gov_merge_rejects_non_open_pr(tmp_path):
    """已合并 / 已关闭的 PR 不得重复走合并流程，并提示用 clean 清理残留。"""
    repo = tmp_path / "repo"
    _init_repo(repo)
    _install_gov_scripts(repo)
    bin_dir = tmp_path / "bin"
    _fake_gh(
        bin_dir,
        "cat <<'JSON'\n"
        '{"headRefName":"feat/demo","state":"MERGED","mergeable":"UNKNOWN"}\n'
        "JSON\n",
    )

    r = _run(["bash", "scripts/governance/gov-merge.sh", "42"], repo,
             env_extra=_path_with(bin_dir))

    assert r.returncode != 0
    combined = r.stdout + r.stderr
    assert "非 OPEN" in combined, combined
    assert "ses-worktree.sh clean" in combined, "缺少清理残留的下一步提示"


def test_gov_merge_dry_run_only_prints_plan(tmp_path):
    """--dry-run 只打印计划：不得建 worktree、不得改分支。"""
    repo = tmp_path / "repo"
    _init_repo(repo)
    _install_gov_scripts(repo)
    bin_dir = tmp_path / "bin"
    _fake_gh(
        bin_dir,
        "cat <<'JSON'\n"
        '{"headRefName":"feat/demo","state":"OPEN","mergeable":"MERGEABLE"}\n'
        "JSON\n",
    )

    r = _run(["bash", "scripts/governance/gov-merge.sh", "7", "--dry-run"], repo,
             env_extra=_path_with(bin_dir))

    assert r.returncode == 0, r.stderr
    assert "[dry-run]" in r.stdout
    assert "feat/demo" in r.stdout
    assert "未执行任何变更" in r.stdout
    # 计划里的临时 worktree 不得真被创建
    assert not (tmp_path / "repo-demo").exists()
    wt_list = _git(repo, "worktree", "list", "--porcelain")
    assert wt_list.count("worktree ") == 1, f"--dry-run 建了 worktree: {wt_list}"


# ---------------------------------------------------------------- ses-worktree clean

def _make_clean_fixture(tmp_path: Path):
    """造三份数据：主树 + 已合入分支的 worktree + 未合入分支的 worktree。"""
    repo = tmp_path / "repo"
    _init_repo(repo)

    merged_wt = tmp_path / "repo-merged"
    _git(repo, "branch", "feat/merged", "master")
    _git(repo, "worktree", "add", "-q", str(merged_wt), "feat/merged")

    active_wt = tmp_path / "repo-active"
    _git(repo, "worktree", "add", "-q", "-b", "feat/active", str(active_wt), "master")
    (active_wt / "only-active.txt").write_text("active work\n", encoding="utf-8")
    _git(active_wt, "add", ".")
    _git(active_wt, "commit", "-q", "-m", "active work")

    return repo, merged_wt, active_wt


def test_worktree_clean_dry_run_lists_only_merged(tmp_path):
    repo, merged_wt, active_wt = _make_clean_fixture(tmp_path)

    r = _run(["bash", WT_SH, "clean", "--dry-run"], repo)

    assert r.returncode == 0, r.stderr
    out = r.stdout
    assert f"[dry-run] 将移除 worktree: {merged_wt}" in out, out
    assert f"将移除 worktree: {active_wt}" not in out, "未合入的 worktree 被列入清理"
    assert "保留（未合入" in out and str(active_wt) in out, out
    # dry-run 不得真删
    assert merged_wt.is_dir() and active_wt.is_dir()


def test_worktree_clean_removes_merged_keeps_active(tmp_path):
    repo, merged_wt, active_wt = _make_clean_fixture(tmp_path)

    r = _run(["bash", WT_SH, "clean"], repo)

    assert r.returncode == 0, r.stderr
    assert not merged_wt.exists(), "已合入分支的 worktree 未被清理"
    assert active_wt.is_dir(), "未合入分支的 worktree 被误删"
    listed = _git(repo, "worktree", "list", "--porcelain")
    assert f"worktree {merged_wt}" not in listed
    assert f"worktree {active_wt}" in listed
    # clean 只清 worktree，不删分支（分支删除由 gov-merge 第 9 步负责）
    assert _run(["git", "show-ref", "--verify", "--quiet", "refs/heads/feat/merged"], repo).returncode == 0


def test_worktree_clean_reports_baseline_missing(tmp_path):
    """没有主干时不能瞎删，必须报错退出。"""
    repo = tmp_path / "repo"
    repo.mkdir()
    _git(repo, "init", "-q", "-b", "topic-only")
    _git(repo, "config", "user.email", "test@example.com")
    _git(repo, "config", "user.name", "Test")
    (repo / "a.txt").write_text("a\n", encoding="utf-8")
    _git(repo, "add", ".")
    _git(repo, "commit", "-q", "-m", "init")

    r = _run(["bash", WT_SH, "clean", "--dry-run"], repo)
    assert r.returncode != 0
    assert "找不到基线分支" in r.stderr


# ---------------------------------------------------------------- ses-worktree add 防回归

def test_worktree_add_copies_local_properties(tmp_path):
    """local.properties 被 gitignore，不会随 checkout 带出；缺它 gradle 报 SDK 缺失。"""
    repo = tmp_path / "repo"
    _init_repo(repo)
    (repo / "local.properties").write_text("sdk.dir=/tmp/android-sdk\n", encoding="utf-8")

    r = _run(["bash", WT_SH, "add", "feat/demo"], repo)

    assert r.returncode == 0, r.stderr
    dst = tmp_path / "repo-demo"
    assert dst.is_dir(), f"worktree 未创建: {dst}"
    copy = dst / "local.properties"
    assert copy.is_file(), "worktree 内缺少 local.properties（gradle 会报 SDK location not found）"
    assert copy.read_text(encoding="utf-8") == "sdk.dir=/tmp/android-sdk\n"
    assert "已复制 local.properties" in r.stdout
