"""gov-profile.sh 行为测试（DEC-074, DEC-081）：classify/rank_of/allowed/paths/resolve。

不变点的不变量测试在 test_profile_governance.py；本文件聚焦接口输出格式与边界。
"""
import os
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "scripts" / "governance" / "gov-profile.sh"


def _lib(expr: str) -> str:
    """Source the script in lib mode and evaluate expr (asserts rc=0)."""
    code = f'source "{SCRIPT}"; {expr}'
    run = subprocess.run(
        ["bash", "-c", code], cwd=ROOT, capture_output=True, text=True,
        env={**os.environ, "GOV_PROFILE_LIB": "1"},
    )
    assert run.returncode == 0, f"lib eval failed: {run.stderr}"
    return run.stdout.strip()


def _lib_raw(expr: str) -> subprocess.CompletedProcess:
    """Source the script in lib mode and evaluate expr (returns full result)."""
    code = f'source "{SCRIPT}"; {expr}'
    return subprocess.run(
        ["bash", "-c", code], cwd=ROOT, capture_output=True, text=True,
        env={**os.environ, "GOV_PROFILE_LIB": "1"},
    )


def _run(*args, **env) -> subprocess.CompletedProcess:
    e = {**os.environ, **env}
    return subprocess.run(
        ["bash", str(SCRIPT), *args], cwd=ROOT, capture_output=True, text=True, env=e,
    )


# -- classify ------------------------------------------------------------------

def test_classify_docs():
    """classify should return 'docs' for docs/design/*.md/.opencode paths."""
    assert _lib('classify "docs/index.md"') == "docs"
    assert _lib('classify "design/prototypes/a.html"') == "docs"
    assert _lib('classify "AGENTS.md"') == "docs"
    assert _lib('classify ".opencode/rules.md"') == "docs"


def test_classify_gov():
    """classify should return 'gov' for scripts/.github paths."""
    assert _lib('classify "scripts/governance/gov-validate.py"') == "gov"
    assert _lib('classify "scripts/dev/ship.sh"') == "gov"
    assert _lib('classify ".github/hooks/pre-commit"') == "gov"


def test_classify_test():
    """classify should return 'test' for test paths."""
    assert _lib('classify "tests/unit/test_x.py"') == "test"
    assert _lib('classify "app/src/test/kotlin/XTest.kt"') == "test"
    assert _lib('classify "domain/src/test/kotlin/Y.kt"') == "test"
    assert _lib('classify "application/src/test/kotlin/Z.kt"') == "test"
    assert _lib('classify "infrastructure/src/test/kotlin/W.kt"') == "test"


def test_classify_code():
    """classify should return 'code' for source/build paths."""
    assert _lib('classify "app/src/main/kotlin/X.kt"') == "code"
    assert _lib('classify "domain/src/main/kotlin/Y.kt"') == "code"
    assert _lib('classify "build.gradle.kts"') == "code"
    assert _lib('classify "gradle.properties"') == "code"


def test_classify_mixed():
    """classify should return 'mixed' for unknown paths."""
    assert _lib('classify "mystery/file.bin"') == "mixed"


def test_classify_ai_is_docs():
    """.ai/* should classify as docs (derived data, not code)."""
    assert _lib('classify ".ai/roles.json"') == "docs"


# -- rank_of -------------------------------------------------------------------

def test_rank_of_monotonic():
    """rank_of should return increasing values: docs < test < code < gov < mixed."""
    ranks = [_lib(f'rank_of {p}') for p in ("docs", "test", "code", "gov", "mixed")]
    assert ranks == sorted(ranks), f"Not monotonic: {ranks}"
    assert len(set(ranks)) == len(ranks), f"Duplicates: {ranks}"


# -- paths ---------------------------------------------------------------------

def test_paths_per_profile():
    """paths should output correct paths for each profile."""
    profiles = {
        "docs": ["docs/", "design/", "*.md"],
        "test": ["tests/", "app/src/test/"],
        "gov": ["scripts/", "tests/", "docs/", ".github/"],
        "code": ["app/", "domain/", "application/", "infrastructure/"],
    }
    for prof, expected_parts in profiles.items():
        result = _run("paths", prof)
        assert result.returncode == 0
        for part in expected_parts:
            assert part in result.stdout, f"paths {prof}: missing '{part}'"


def test_paths_mixed_is_wildcard():
    """paths mixed should return wildcard."""
    result = _run("paths", "mixed")
    assert result.returncode == 0
    assert "*" in result.stdout


# -- resolve -------------------------------------------------------------------

def test_resolve_syntax():
    """resolve should pass bash -n syntax check."""
    run = subprocess.run(["bash", "-n", str(SCRIPT)], capture_output=True, text=True)
    assert run.returncode == 0, run.stderr


def test_resolve_help():
    """resolve should be documented in usage."""
    run = _run("--help")
    assert "resolve" in run.stdout or "resolve" in run.stderr


# -- allowed (returns exit code, not stdout) -----------------------------------

def test_allowed_gov_path_for_gov_profile():
    """gov profile should allow scripts/ and docs/ paths (exit 0)."""
    r1 = _lib_raw('allowed gov scripts/governance/gov-validate.py')
    assert r1.returncode == 0, f"gov should allow scripts/: {r1.stderr}"
    r2 = _lib_raw('allowed gov docs/ai/decisions/DEC-001.md')
    assert r2.returncode == 0, f"gov should allow docs/: {r2.stderr}"


def test_allowed_app_source_for_code_profile():
    """code profile should allow app/src/main/ but not .ai/locks (exit 0/1)."""
    r1 = _lib_raw('allowed code app/src/main/kotlin/X.kt')
    assert r1.returncode == 0, f"code should allow app/src/main/: {r1.stderr}"
    r2 = _lib_raw('allowed code .ai/locks/T-1/owner')
    assert r2.returncode == 1, f"code should deny .ai/locks/: {r2.stderr}"


def test_allowed_rejects_docs_profile_for_scripts():
    """docs profile should not allow scripts/ paths (exit 1)."""
    r1 = _lib_raw('allowed docs scripts/governance/gov-validate.py')
    assert r1.returncode == 1, f"docs should deny scripts/: {r1.stderr}"
    r2 = _lib_raw('allowed docs app/src/main/kotlin/X.kt')
    assert r2.returncode == 1, f"docs should deny app/src/main/: {r2.stderr}"


def test_allowed_rejects_code_profile_for_tests():
    """code profile should not allow tests/ paths (exit 1)."""
    r = _lib_raw('allowed code tests/unit/test_x.py')
    assert r.returncode == 1, f"code should deny tests/: {r.stderr}"
