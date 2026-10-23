#!/usr/bin/env python3
"""CI 变更范围判定的负向测试（DEC-058）。

目的: 证明 `scripts/governance/gov-ci-scope.sh` 真实生效，且**不会漏掉源码**。

背景: 原 ci.yml 内联正则只认 `docs/` 与 `*.md`，导致 `scripts/`、`tests/`、
      `design/`、`.opencode/` 这些不参与 gradle 构建的变更被判为 code=true，
      白跑全量构建；pull_request 事件以 master 为基线（整条分支），开 PR 几乎
      必然触发重型构建 —— 这是 2026-10-23 实测到的 CI 慢因。

两类必须同时成立的约束:
  - 不漏: 只要动到 Kotlin / gradle 源码 → 必须 code=true（门禁不可空缺）
  - 不冤: 只动非构建类路径 → code=false（消除假阳性）
任何一条被破坏都属于治理设计约束 Q4「盲点」。

运行: pytest tests/unit/test_ci_change_scope.py
"""
import subprocess
import sys
from pathlib import Path

ROOT_P = Path(__file__).resolve().parents[2]
if str(ROOT_P) not in sys.path:
    sys.path.insert(0, str(ROOT_P))

SCRIPT = ROOT_P / "scripts" / "governance" / "gov-ci-scope.sh"


def classify(files: list) -> str:
    """返回脚本 stdout 中的 code=true / code=false。"""
    proc = subprocess.run(
        ["bash", str(SCRIPT)],
        input="\n".join(files) + ("\n" if files else ""),
        capture_output=True, text=True, cwd=str(ROOT_P), timeout=30,
    )
    assert proc.returncode == 0, f"脚本退出码非 0: {proc.stderr}"
    return proc.stdout.strip()


# ------------------------------------------------- 不漏：源码必须触发构建

def test_kotlin_source_triggers_build():
    """动到 Kotlin 源码必须 code=true —— 漏了就是门禁空缺。"""
    assert classify(["app/src/main/kotlin/com/xcwk/scalehub/App.kt"]) == "code=true"


def test_gradle_build_file_triggers_build():
    """动到构建脚本必须 code=true。"""
    assert classify(["app/build.gradle.kts"]) == "code=true"
    assert classify(["settings.gradle.kts"]) == "code=true"
    assert classify(["gradle/libs.versions.toml"]) == "code=true"


def test_gradle_module_any_file_triggers_build():
    """四个 gradle 模块内任一文件都必须触发构建（含资源与测试）。"""
    for f in (
        "domain/src/main/java/com/xcwk/scalehub/domain/X.java",
        "application/src/main/kotlin/A.kt",
        "infrastructure/src/main/kotlin/B.kt",
        "app/src/test/kotlin/C.kt",
        "app/src/main/res/values/strings.xml",
    ):
        assert classify([f]) == "code=true", f


def test_mixed_changes_always_trigger_build():
    """文档 + 源码混合时必须 code=true（白名单是「全部命中才跳过」）。"""
    files = ["docs/index.md", "app/src/main/kotlin/com/xcwk/scalehub/App.kt"]
    assert classify(files) == "code=true"


# ------------------------------------------------- 不冤：非构建类路径跳过

def test_docs_only_skips_build():
    assert classify(["docs/index.md", "docs/guides/GC-x.md"]) == "code=false"


def test_prototype_html_skips_build():
    """design/ 下的原型 HTML 不参与 gradle 构建（DEC-058 直接诱因）。"""
    assert classify(["design/prototypes/interactive/16-measure-result.html"]) == "code=false"


def test_governance_scripts_skip_build():
    """scripts/ 是治理工具，不参与 gradle 构建（DEC-058 直接诱因）。"""
    assert classify(["scripts/governance/gov-validate.py"]) == "code=false"


def test_pytest_files_skip_build():
    """仓库根 tests/ 仅被 pytest 收集（pytest.ini testpaths），gradle 不编译。"""
    assert classify(["tests/unit/test_docs_governance.py"]) == "code=false"


def test_opencode_skills_skip_build():
    """.opencode/ 下的 skill 元数据不参与 gradle 构建（DEC-058 直接诱因）。"""
    assert classify([".opencode/skills/docs-manage/skill.yaml"]) == "code=false"


def test_ai_and_cache_metadata_skip_build():
    assert classify([".ai/tasks.json", ".cache/context.jsonl"]) == "code=false"


# ------------------------------------------------- 真实回归用例

def test_regression_pr31_file_list_skips_build():
    """用 2026-10-23 PR #31 的真实清单验证：原判定 code=true 是假阳性。

    该清单零 Kotlin / gradle 文件，本不应触发全量构建。
    """
    files = [
        ".ai/tasks.json",
        ".github/PULL_REQUEST_TEMPLATE.md",
        ".opencode/skills/docs-manage/binding.yaml",
        ".opencode/skills/prototype-dev/skill.yaml",
        "AGENTS.md",
        "design/prototypes/interactive/26-final-gaps.html",
        "docs/ai/decisions/DEC-057.md",
        "scripts/governance/gov_validate_docs.py",
        "tests/unit/test_docs_autofix.py",
    ]
    assert classify(files) == "code=false", "非构建类清单仍被误判为需构建"


def test_regression_flips_when_source_added():
    """同一清单加入任一源码必须立刻翻转为 code=true（防白名单扩得过宽）。"""
    files = [
        "design/prototypes/interactive/26-final-gaps.html",
        "scripts/governance/gov_validate_docs.py",
        "app/src/main/kotlin/com/xcwk/scalehub/feature/home/HomeScreen.kt",
    ]
    assert classify(files) == "code=true"


# ------------------------------------------------- 保守分支

def test_empty_input_is_conservative_true():
    """diff 为空或失败时必须 code=true —— 不能让门禁因静默失败而空缺。"""
    assert classify([]) == "code=true"


def test_unknow_path_is_conservative_true():
    """白名单外的未知路径一律按 code=true（黑名单式的「默认放行」是禁区）。"""
    assert classify(["some_new_dir/whatever.bin"]) == "code=true"


def test_script_stdout_only_contains_code_line():
    """stdout 必须只有 code= 行 —— 它被直接追加进 $GITHUB_OUTPUT。"""
    proc = subprocess.run(
        ["bash", str(SCRIPT)],
        input="docs/index.md\n", capture_output=True, text=True, timeout=30,
    )
    lines = [ln for ln in proc.stdout.splitlines() if ln.strip()]
    assert len(lines) == 1 and lines[0] in ("code=true", "code=false"), lines
    assert proc.stderr.strip(), "诊断信息应在 stderr（CI 日志可见）"
