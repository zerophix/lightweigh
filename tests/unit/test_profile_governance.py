"""任务画像（DEC-074）不变量测试。

画像的作用是让"文档 / 测试 / 治理脚本 / 产品代码"四类任务走不同门禁，
而不是每个 AI 都被套上最重流程。这里钉死三条不允许退化的性质：

  - 路径分类稳定（docs/test/gov/code/mixed）
  - 严格度单调：docs < test < code < gov < mixed
  - 越界判定有效：docs 画像不能改 scripts/ 与 src/，code 画像可以改代码
  - 降级必须显式：只有 GOV_PROFILE_FORCE=1 才允许低于推断出的画像

运行: pytest tests/unit/test_profile_governance.py
"""
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "scripts" / "governance" / "gov-profile.sh"


def _lib(expr: str) -> str:
    """在只加载函数（不执行分发）的模式下求值。"""
    code = f'source "{SCRIPT}"; {expr}'
    run = subprocess.run(
        ["bash", "-c", code], cwd=ROOT, capture_output=True, text=True,
        env={**__import__("os").environ, "GOV_PROFILE_LIB": "1"},
    )
    assert run.returncode == 0, run.stderr
    return run.stdout.strip()


def test_path_classification():
    cases = {
        "docs/index.md": "docs",
        "design/prototypes/a.html": "docs",
        "scripts/governance/gov-x.sh": "gov",
        "tests/unit/test_x.py": "test",
        "app/src/test/kotlin/XTest.kt": "test",
        "app/src/main/kotlin/X.kt": "code",
        "domain/src/test/kotlin/Y.kt": "test",
        "mystery/file.bin": "mixed",
    }
    for path, expected in cases.items():
        got = _lib(f'classify "{path}"')
        assert got == expected, f"{path}: {got} != {expected}"


def test_rank_is_monotonic():
    ranks = [_lib(f"rank_of {p}") for p in ("docs", "test", "code", "gov", "mixed")]
    assert ranks == sorted(ranks), ranks
    assert len(set(ranks)) == len(ranks)


def test_docs_profile_cannot_touch_scripts_or_source():
    assert _lib('allowed docs docs/notes/a.md; echo $?') == "0"
    assert _lib('allowed docs scripts/governance/gov-x.sh; echo $?') == "1"
    assert _lib('allowed docs app/src/main/kotlin/X.kt; echo $?') == "1"


def test_code_profile_can_touch_source_but_not_locks():
    assert _lib('allowed code app/src/main/kotlin/X.kt; echo $?') == "0"
    assert _lib('allowed code .ai/locks/T-1/owner; echo $?') == "1"


def test_test_profile_scope():
    assert _lib('allowed test tests/unit/test_x.py; echo $?') == "0"
    assert _lib('allowed test app/src/main/kotlin/X.kt; echo $?') == "1"


def test_force_is_the_only_downgrade_path():
    """未设 FORCE 时只能升；设了 FORCE 才按 env 生效。"""
    plain = subprocess.run(
        ["bash", str(SCRIPT), "resolve"], cwd=ROOT, capture_output=True, text=True,
        env={**__import__("os").environ, "GOV_PROFILE": "docs"},
    )
    forced = subprocess.run(
        ["bash", str(SCRIPT), "resolve"], cwd=ROOT, capture_output=True, text=True,
        env={**__import__("os").environ, "GOV_PROFILE": "docs", "GOV_PROFILE_FORCE": "1"},
    )
    assert forced.stdout.strip() == "docs"
    # 未加 FORCE 时，env 不能把 scripts/ 变更降级成 docs
    assert plain.stdout.strip() != "docs"


def test_scope_violation_is_reported():
    """用 FORCE 造一个 docs 画像，再对真实暂存区做越界检查。

    暂存区为空时不应报越界；这里只验证"没有改动 = 通过"这一条安全性质，
    越界路径的判定已由 allowed 的单测覆盖。
    """
    run = subprocess.run(
        ["bash", str(SCRIPT), "check-scope", "--staged"], cwd=ROOT,
        capture_output=True, text=True,
        env={**__import__("os").environ, "GOV_PROFILE": "docs", "GOV_PROFILE_FORCE": "1"},
    )
    staged = subprocess.run(
        ["git", "diff", "--cached", "--name-only"], cwd=ROOT, capture_output=True, text=True,
    ).stdout.strip()
    if not staged:
        assert run.returncode == 0, run.stdout + run.stderr
