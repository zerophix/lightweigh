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


# ---- 源码质量规则（gov-code-check.py）----

CODE_CHECK = SCRIPT.parent / "gov-code-check.py"


def test_layer_purity_rules_present_in_script():
    """分层纯度规则不能被静默删除（内容耦合断言）。"""
    text = SCRIPT.read_text(encoding="utf-8")
    assert "禁止依赖 Android API" in text
    assert "表现层禁止直接 import infrastructure" in text


def test_code_check_script_self_proves_effective():
    """gov-code-check 必须自证有效：守卫负例抓不到该抓的即失败（防规则空转）。"""
    result = subprocess.run(
        ["python3", str(CODE_CHECK)], capture_output=True, text=True
    )
    assert "自证通过" in result.stdout, result.stdout + result.stderr


def test_dead_import_blocks_commit(tmp_path):
    """死 import 必须阻断——规则若空转，复制粘贴的垃圾会一路绿灯。"""
    (tmp_path / "scripts/governance").mkdir(parents=True)
    (tmp_path / "scripts/governance/gov-code-check.py").write_text(
        CODE_CHECK.read_text(encoding="utf-8"), encoding="utf-8"
    )
    src = tmp_path / "app/src/main/kotlin/com/xcwk/scalehub/feature/demo"
    src.mkdir(parents=True)
    (src / "Dead.kt").write_text(
        "package com.xcwk.scalehub.feature.demo\n"
        "import android.graphics.Color\n"
        "fun ok() = 1\n",
        encoding="utf-8",
    )
    result = _run(tmp_path)
    assert result.returncode != 0, "死 import 未被阻断"
    assert "Dead.kt" in result.stdout


def test_duplicate_implementation_blocks_commit(tmp_path):
    """跨文件逐字重复必须阻断——分叉的两份实现迟早改一处忘一处。"""
    (tmp_path / "scripts/governance").mkdir(parents=True)
    (tmp_path / "scripts/governance/gov-code-check.py").write_text(
        CODE_CHECK.read_text(encoding="utf-8"), encoding="utf-8"
    )
    body = (
        "package com.xcwk.scalehub.feature.demo\n"
        "fun twin(input: String): String {\n"
        "    val a = input.trim()\n"
        "    val b = a.lowercase()\n"
        "    val c = b.replace(\"-\", \"_\")\n"
        "    val d = c.ifEmpty { \"none\" }\n"
        "    return d\n"
        "}\n"
    )
    for module, pkg in (("app", "feature/demo"), ("app", "feature/demo2")):
        d = tmp_path / module / "src/main/kotlin/com/xcwk/scalehub" / pkg
        d.mkdir(parents=True)
        (d / "Twin.kt").write_text(body.replace("feature.demo", pkg), encoding="utf-8")
    result = _run(tmp_path)
    assert result.returncode != 0, "逐字重复实现未被阻断"
    assert "重复实现" in result.stdout
