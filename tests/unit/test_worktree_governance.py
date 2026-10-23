#!/usr/bin/env python3
"""治理规则的 worktree 感知性测试（DEC-062）。

背景: DEC-050 引入 git worktree 作为多 AI 并发隔离机制，但两条治理检查把
`.git` 当作目录使用：

  - `check_status()` 拼 `ROOT/.git/hooks/<hook>` 判定 hooks 是否安装
  - `check_root_files()` 把根目录每个文件与白名单比对，`.git` 不在其中

而 worktree 下 `.git` 是一个 89 字节的**文件**（内容 `gitdir: <主仓>/.git/worktrees/<名>`），
于是两条检查在任何 worktree 中恒失败：

  [FAIL] Git hooks 未安装: pre-commit, commit-msg, pre-push
  [FAIL] .git: 不在根目录允许清单中

后果是**治理机制否定了协作机制**：agent 建了 worktree 却无法在其中提交，
只能退回共享主树提交（实测：四个 agent 的 worktree 分支均零提交，
而主树出现带他人 session id 的提交）。

本文件锁死修复后的行为，并保留负向用例证明检查未被削弱。

运行: pytest tests/unit/test_worktree_governance.py
"""
import subprocess
import sys
from pathlib import Path

import pytest

ROOT_P = Path(__file__).resolve().parents[2]
if str(ROOT_P) not in sys.path:
    sys.path.insert(0, str(ROOT_P))

from scripts.governance.modules import gov_validate_core as core
from scripts.governance.modules import gov_validate_status as status_mod


def _clean_errors_guard():
    saved = list(core.errors)
    core.errors.clear()
    yield
    core.errors.clear()
    core.errors.extend(saved)


@pytest.fixture(autouse=True)
def _clean_errors():
    yield from _clean_errors_guard()


def _git(cwd: Path, *args: str) -> str:
    r = subprocess.run(
        ["git", "-c", "init.defaultBranch=main", *args],
        cwd=cwd, capture_output=True, text=True, timeout=60,
    )
    assert r.returncode == 0, f"git {' '.join(args)} 失败: {r.stderr}"
    return r.stdout


def _make_root_files(root: Path):
    """补齐根目录治理要求的关键文件。"""
    for name in ("README.md", "LICENSE", ".gitignore", "STATUS.md", "setup.sh", "VERSION"):
        (root / name).write_text(f"# {name}\n", encoding="utf-8")


# ---------------------------------------------------------------- 根文件白名单

def test_git_file_in_root_is_not_flagged(tmp_path, monkeypatch):
    """worktree 形态：.git 是文件，不得被判「不在根目录允许清单中」。"""
    _make_root_files(tmp_path)
    (tmp_path / ".git").write_text("gitdir: /nonexistent/.git/worktrees/x\n", encoding="utf-8")

    monkeypatch.setattr(status_mod, "ROOT", tmp_path)
    status_mod.check_root_files()   # 必须真正执行检查，否则断言的是空列表
    assert not any(".git: 不在根目录允许清单中" in e for e in core.errors), core.errors


def test_root_check_still_flags_unknown_files(tmp_path, monkeypatch):
    """负向对照: 修复不得让白名单检查整体失效。"""
    _make_root_files(tmp_path)
    (tmp_path / ".git").write_text("gitdir: /x\n", encoding="utf-8")
    (tmp_path / "mystery.txt").write_text("不该出现在仓库根目录\n", encoding="utf-8")

    monkeypatch.setattr(status_mod, "ROOT", tmp_path)
    status_mod.check_root_files()
    assert any("mystery.txt" in e for e in core.errors), "未知根文件未被拦截，白名单检查失效"


# ---------------------------------------------------------------- hooks 路径解析

def test_hooks_dir_resolves_in_real_repo():
    """真实仓库中必须解析出真实存在的 hooks 目录（主树与 worktree 均可）。"""
    resolved = status_mod._git_hooks_dir()
    assert resolved.exists(), f"解析出的 hooks 目录不存在: {resolved}"


def test_hooks_dir_degrades_gracefully_when_git_unavailable(tmp_path, monkeypatch):
    """.git 为文件但 git 无法解析（损坏的 worktree）→ 退回原路径，不让校验崩溃。"""
    _make_root_files(tmp_path)
    (tmp_path / ".git").write_text("gitdir: /nonexistent/.git/worktrees/x\n", encoding="utf-8")
    monkeypatch.setattr(status_mod, "ROOT", tmp_path)

    assert status_mod._git_hooks_dir() == tmp_path / ".git" / "hooks", (
        "git 解析失败时应退回 ROOT/.git/hooks（保守降级），否则校验自身会抛异常"
    )


def test_hooks_detected_inside_real_git_worktree(tmp_path, monkeypatch):
    """端到端: 在真实 git worktree 中，安装在主仓 .git/hooks 的钩子必须被识别。"""
    main = tmp_path / "main"
    main.mkdir()
    _git(main, "init", "-q")
    _git(main, "commit", "-q", "--allow-empty", "-m", "init")
    wt = tmp_path / "wt"
    _git(main, "worktree", "add", "-q", str(wt), "-b", "wt-branch")

    # worktree 的 .git 必须是文件（本测试的前提）
    assert (wt / ".git").is_file(), f"worktree 的 .git 不是文件: {wt / '.git'}"

    # 在主仓 hooks 目录安装钩子（worktree 与主树共享）
    hooks_dir = main / ".git" / "hooks"
    for hook in ("pre-commit", "commit-msg", "pre-push"):
        (hooks_dir / hook).write_text("#!/bin/sh\nexit 0\n", encoding="utf-8")
        (hooks_dir / hook).chmod(0o755)

    monkeypatch.setattr(status_mod, "ROOT", wt)
    assert status_mod._git_hooks_dir() == hooks_dir, (
        f"worktree 未解析到主仓 hooks: {status_mod._git_hooks_dir()} != {hooks_dir}"
    )

    status_mod.check_status()
    assert not any("Git hooks 未安装" in e for e in core.errors), (
        f"worktree 中已安装的 hooks 被误判为未安装: {core.errors}"
    )
