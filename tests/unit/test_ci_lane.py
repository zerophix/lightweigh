#!/usr/bin/env python3
"""风险车道判定测试（DEC-064）。

目的: 证明 `gov-local-ci.sh` 的车道门禁真实生效，且不因「省时间」而降低质量。

三档车道（混合取最高 C > B > A）:
  A  非构建类路径（docs/ design/ .github/ scripts/ tests/ *.md 等）→ 跳过 gradle
  B  仅 app/src/** → 只跑 :app:testDebugUnitTest
  C  源码/构建文件/未知路径/检测失败 → 全量 ./gradlew build

判定逻辑的单一真源是 `scripts/governance/gov-ci-scope.sh --print-lane`，
因此路径集合 → 车道的断言直接打它；车道 → gradle 步骤的映射则用
`gov-local-ci.sh` 的 PLAN 模式（GOV_LOCAL_CI_PLAN=1，不执行任何步骤）验证。

运行: pytest tests/unit/test_ci_lane.py
"""
import subprocess
import sys
from pathlib import Path

ROOT_P = Path(__file__).resolve().parents[2]
if str(ROOT_P) not in sys.path:
    sys.path.insert(0, str(ROOT_P))

SCOPE = ROOT_P / "scripts" / "governance" / "gov-ci-scope.sh"
LOCAL_CI = ROOT_P / "scripts" / "governance" / "gov-local-ci.sh"


def lane_of(files, env=None) -> dict:
    """跑 --print-lane，返回 {'lane':..., 'forced':..., 'reasons':[...], 'paths':[...]}。"""
    proc = subprocess.run(
        ["bash", str(SCOPE), "--print-lane"],
        input="\n".join(files) + ("\n" if files else ""),
        capture_output=True, text=True, cwd=str(ROOT_P), timeout=60,
        env={**__import__("os").environ, "GOV_LANE": "auto", **(env or {})},
    )
    assert proc.returncode == 0, f"--print-lane 退出码非 0: {proc.stderr}"
    out = {"lane": None, "forced": "false", "reasons": [], "paths": []}
    for line in proc.stdout.splitlines():
        if line.startswith("lane="):
            out["lane"] = line[len("lane="):]
        elif line.startswith("forced="):
            out["forced"] = line[len("forced="):]
        elif line.startswith("reason="):
            out["reasons"].append(line[len("reason="):])
        elif line.startswith("path="):
            out["paths"].append(line[len("path="):])
    assert out["lane"] in {"A", "B", "C", "full"}, f"lane 非法: {out['lane']}\n{proc.stdout}"
    return out


def plan(extra_args=(), env=None) -> str:
    """跑 local-ci 的 PLAN 模式：只打印决策与计划步骤，不执行 gradle / 任何检查。"""
    proc = subprocess.run(
        ["bash", str(LOCAL_CI), *extra_args],
        capture_output=True, text=True, cwd=str(ROOT_P), timeout=120,
        env={**__import__("os").environ, "GOV_LOCAL_CI_PLAN": "1", **(env or {})},
    )
    assert proc.returncode == 0, f"PLAN 模式退出码非 0: {proc.stderr}\n{proc.stdout}"
    return proc.stdout


# ------------------------------------------------------- 车道 A：非构建类路径

def test_docs_only_is_lane_a():
    assert lane_of(["docs/index.md", "docs/guides/GC-x.md"])["lane"] == "A"


def test_design_github_metadata_is_lane_a():
    """原型 HTML、.github/、根级 *.md 都不参与 gradle 构建 → A。"""
    assert lane_of(["design/prototypes/interactive/16-x.html"])["lane"] == "A"
    assert lane_of([".github/workflows/ci.yml"])["lane"] == "A"
    assert lane_of(["AGENTS.md"])["lane"] == "A"


def test_governance_scripts_and_tests_are_lane_a():
    """治理脚本与 pytest 用例不参与 gradle 构建 → A。"""
    assert lane_of(["scripts/governance/gov-validate.py"])["lane"] == "A"
    assert lane_of(["tests/unit/test_docs_governance.py"])["lane"] == "A"


def test_repo_config_files_are_lane_a():
    assert lane_of([".gitignore", ".editorconfig"])["lane"] == "A"


# ------------------------------------------------------- 车道 B：仅 app 源码

def test_app_src_only_is_lane_b():
    files = [
        "app/src/main/kotlin/com/xcwk/scalehub/feature/home/HomeScreen.kt",
        "app/src/main/res/values/strings.xml",
        "app/src/test/kotlin/com/xcwk/scalehub/ExampleTest.kt",
    ]
    assert lane_of(files)["lane"] == "B"


def test_app_build_file_is_not_lane_b():
    """app 模块的构建脚本会影响整个构建 → 必须 C，不能只跑单测。"""
    assert lane_of(["app/build.gradle.kts"])["lane"] == "C"


# ------------------------------------------------------- 车道 C：源码与构建文件

def test_core_modules_are_lane_c():
    for f in (
        "domain/src/main/java/com/xcwk/scalehub/domain/X.java",
        "application/src/main/kotlin/A.kt",
        "infrastructure/src/main/kotlin/B.kt",
        "infrastructure/schemas/local-db/1.json",
        "golden/vectors/baseline.json",
    ):
        assert lane_of([f])["lane"] == "C", f


def test_gradle_build_files_are_lane_c():
    for f in (
        "build.gradle.kts",
        "settings.gradle.kts",
        "gradle.properties",
        "gradle/libs.versions.toml",
        "gradle/wrapper/gradle-wrapper.properties",
    ):
        assert lane_of([f])["lane"] == "C", f


def test_unknown_path_is_lane_c():
    """白名单外的未知路径一律 C（黑名单式默认放行是禁区）。"""
    assert lane_of(["some_new_dir/whatever.bin"])["lane"] == "C"


def test_source_file_hidden_in_docs_is_lane_c():
    """源码扩展名闸门：即使藏在 docs/ 下也必须升到 C。"""
    assert lane_of(["docs/snippets/Real.kt"])["lane"] == "C"


def test_empty_input_is_lane_c():
    """变更清单为空 / 检测失败 → 保守按 C，不留门禁空洞。"""
    out = lane_of([])
    assert out["lane"] == "C"
    assert out["reasons"], "C 的保守分支应给出原因"


# ------------------------------------------------------- 混合取最高

def test_mixed_docs_and_app_src_takes_b():
    assert lane_of(["docs/index.md", "app/src/main/kotlin/App.kt"])["lane"] == "B"


def test_mixed_app_src_and_domain_takes_c():
    assert lane_of(["app/src/main/kotlin/App.kt", "domain/src/main/java/X.java"])["lane"] == "C"


def test_mixed_docs_and_domain_takes_c():
    assert lane_of(["docs/index.md", "domain/src/main/java/X.java"])["lane"] == "C"


def test_mixed_docs_and_design_stays_a():
    assert lane_of(["docs/index.md", "design/a.html", ".github/workflows/ci.yml"])["lane"] == "A"


def test_lane_reasons_name_the_deciding_files():
    """输出必须能回答「由哪些文件决定」—— 决定因素要包含升级车道的文件。"""
    out = lane_of(["docs/index.md", "app/src/main/kotlin/App.kt"])
    assert "app/src/main/kotlin/App.kt" in out["reasons"], out
    assert "docs/index.md" not in out["reasons"], "低车道的文件不应列为决定因素"


# ------------------------------------------------------- GOV_LANE 强制指定

def test_force_full_equals_legacy_behaviour():
    out = lane_of(["docs/index.md"], env={"GOV_LANE": "full"})
    assert out["lane"] == "full"
    assert out["forced"] == "true"


def test_force_a_overrides_source_paths():
    out = lane_of(["domain/src/main/java/X.java"], env={"GOV_LANE": "A"})
    assert out["lane"] == "A"
    assert out["forced"] == "true"


def test_force_c_overrides_docs():
    out = lane_of(["docs/index.md"], env={"GOV_LANE": "C"})
    assert out["lane"] == "C"
    assert out["forced"] == "true"


def test_force_lane_is_case_insensitive():
    assert lane_of(["docs/index.md"], env={"GOV_LANE": "b"})["lane"] == "B"


def test_unknown_force_value_falls_back_to_auto():
    """非法值不得静默放行：按 auto 判定（docs → A），并在 stderr 告警。"""
    proc = subprocess.run(
        ["bash", str(SCOPE), "--print-lane"], input="docs/index.md\n",
        capture_output=True, text=True, timeout=60,
        env={**__import__("os").environ, "GOV_LANE": "zzz"},
    )
    assert proc.returncode == 0
    assert "lane=A" in proc.stdout.splitlines()
    assert "GOV_LANE" in proc.stderr, "非法值应有告警"


# ------------------------------------------------------- stdout 契约

def test_stdout_is_machine_readable():
    proc = subprocess.run(
        ["bash", str(SCOPE), "--print-lane"], input="docs/index.md\n",
        capture_output=True, text=True, timeout=60,
        env={**__import__("os").environ, "GOV_LANE": "auto"},
    )
    lines = [ln for ln in proc.stdout.splitlines() if ln.strip()]
    assert lines[0].startswith("lane="), lines
    for ln in lines:
        assert ln.split("=", 1)[0] in {"lane", "forced", "reason", "path"}, ln
    assert proc.stderr.strip(), "人读诊断应走 stderr"


def test_scope_mode_unchanged_by_lane_support():
    """默认模式契约不变：stdout 仍只有一行 code=。"""
    proc = subprocess.run(
        ["bash", str(SCOPE)], input="docs/index.md\napp/src/main/kotlin/App.kt\n",
        capture_output=True, text=True, timeout=60,
    )
    assert proc.stdout.strip() == "code=true"


# ------------------------------------------------------- local-ci 车道 → 步骤

def test_plan_lane_a_skips_gradle_but_keeps_guards():
    """车道 A 必须仍跑 validate/custom-checks/secrets/schema/test-guard。"""
    out = plan(env={"GOV_LANE": "A"})
    assert "PLAN lane=A gradle=skip" in out, out
    assert "gradlew build" not in out, "车道 A 不得调用 gradle build"
    for step in (
        "gov-validate", "gov-custom-checks", "gov-secrets-scan",
        "gov-schema-guard", "gov-test-guard",
    ):
        assert step in out, f"车道 A 缺少步骤 {step}"


def test_plan_lane_b_runs_app_unit_tests_only():
    out = plan(env={"GOV_LANE": "B"})
    assert "PLAN lane=B gradle=:app:testDebugUnitTest" in out, out
    assert "gradlew build" not in out
    assert "gov-secrets-scan" in out and "gov-schema-guard" in out


def test_plan_lane_c_runs_full_build():
    out = plan(env={"GOV_LANE": "C"})
    assert "PLAN lane=C gradle=build" in out, out
    for step in ("gov-validate", "gov-custom-checks", "gov-secrets-scan",
                 "gov-schema-guard", "gov-test-guard"):
        assert step in out


def test_plan_force_full_runs_full_build():
    out = plan(env={"GOV_LANE": "full"})
    assert "PLAN lane=full gradle=build" in out, out


def test_plan_strict_flag_means_full():
    """--strict 等价全量（历史习惯），不得被车道优化降级。"""
    out = plan(extra_args=("--strict",), env={"GOV_LANE": "auto"})
    assert "PLAN lane=full gradle=build" in out, out


def test_plan_reports_deciding_files():
    """输出必须明确打印选了哪条车道、由哪些文件决定。"""
    out = plan(env={"GOV_LANE": "auto"})
    assert "车道决策" in out
    assert "决定因素" in out or "变更基线" in out
