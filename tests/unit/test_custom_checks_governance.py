"""gov-custom-checks.sh 反射收口规则的可执行性验证（DEC-021 执行记录 2026-10-17）。

证明三点（防 hope-ware）：规则确实写进脚本、干净树通过、违例树被阻断。
"""
import pathlib
import subprocess

SCRIPT = pathlib.Path(__file__).resolve().parents[2] / "scripts" / "governance" / "gov-custom-checks.sh"


def _run(cwd: pathlib.Path) -> subprocess.CompletedProcess:
    return subprocess.run(["bash", str(SCRIPT)], cwd=cwd, capture_output=True, text=True)


def test_reflection_rule_present_in_script():
    """规则本体不能被静默删除（内容耦合断言）。"""
    text = SCRIPT.read_text(encoding="utf-8")
    assert "Class\\.forName" in text
    assert "kotlin\\.reflect\\." in text


def test_clean_tree_passes(tmp_path):
    feature = tmp_path / "app/src/main/kotlin/com/xcwk/scalehub/feature/demo"
    feature.mkdir(parents=True)
    (feature / "Clean.kt").write_text("package com.xcwk.scalehub.feature.demo\nfun ok() = 1\n", encoding="utf-8")
    result = _run(tmp_path)
    assert result.returncode == 0, result.stdout + result.stderr


def test_reflection_violation_blocks_commit(tmp_path):
    feature = tmp_path / "app/src/main/kotlin/com/xcwk/scalehub/feature/demo"
    feature.mkdir(parents=True)
    (feature / "Bad.kt").write_text(
        'package com.xcwk.scalehub.feature.demo\nfun sneaky(): Class<*> = Class.forName("java.lang.String")\n',
        encoding="utf-8",
    )
    result = _run(tmp_path)
    assert result.returncode != 0, "反射违例未被阻断"
    assert "FAIL" in result.stdout
    assert "Bad.kt" in result.stdout


def test_ui_layer_also_covered(tmp_path):
    ui = tmp_path / "app/src/main/kotlin/com/xcwk/scalehub/ui/shell"
    ui.mkdir(parents=True)
    (ui / "Sneak.kt").write_text(
        "package com.xcwk.scalehub.ui.shell\nval r = java.lang.reflect.Method::class\n",
        encoding="utf-8",
    )
    result = _run(tmp_path)
    assert result.returncode != 0, "ui 层反射违例未被阻断"
