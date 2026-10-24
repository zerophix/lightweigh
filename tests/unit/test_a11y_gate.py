#!/usr/bin/env python3
"""无障碍静态门禁的负向测试（T-070 · gov-a11y-check.py）。

目的与 `test_governance_negative.py` 一致：证明规则真实生效（治理设计约束 Q3）。
没有负例的规则是 hope-ware —— 规则被删掉或写坏时，全库检查依然「通过」，没人会发现。

每个用例在临时目录里造一小段 Compose 代码，断言：
  - 该抓的（图标按钮无 contentDescription / 固定高度文本容器）必须抓到
  - 不该抓的（有标签的图标按钮、heightIn(min)、装饰性分隔线）必须不误报

运行：pytest tests/unit/test_a11y_gate.py
"""
import importlib.util
import sys
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "scripts" / "governance" / "gov-a11y-check.py"


def _load_module():
    spec = importlib.util.spec_from_file_location("gov_a11y_check", SCRIPT)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


@pytest.fixture()
def gate(tmp_path, monkeypatch):
    """把扫描根指向临时目录，避免用例依赖真实源码现状。"""
    module = _load_module()
    src = tmp_path / "app" / "src" / "main" / "kotlin"
    src.mkdir(parents=True)
    monkeypatch.setattr(module, "ROOT", tmp_path)
    monkeypatch.setattr(module, "SRC_DIR", src)
    return module, src


def _write(src: Path, name: str, body: str) -> None:
    path = src / name
    path.write_text(body, encoding="utf-8")


def _rules(findings) -> list[str]:
    return sorted(f.rule for f in findings)


HEADER = "package demo\n\nimport androidx.compose.material3.Icon\nimport androidx.compose.material3.Text\n"


# ---- A11Y-001: 图标按钮必须带 contentDescription ----


def test_icon_button_without_content_description_is_reported(gate):
    module, src = gate
    _write(
        src,
        "Bad.kt",
        HEADER
        + """
fun BadIconButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(Icons.Filled.Close, contentDescription = null)
    }
}
""",
    )
    assert "A11Y-001" in _rules(module.scan())


def test_icon_button_with_content_description_passes(gate):
    module, src = gate
    _write(
        src,
        "Good.kt",
        HEADER
        + """
fun GoodIconButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(Icons.Filled.Close, contentDescription = "关闭")
    }
}
""",
    )
    assert _rules(module.scan()) == []


# ---- A11Y-002: 可点击容器必须可朗读 ----


def test_clickable_icon_only_box_is_reported(gate):
    module, src = gate
    _write(
        src,
        "IconOnly.kt",
        HEADER
        + """
fun Bell(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clickable(onClick = onClick),
    ) {
        Icon(Icons.Filled.Notifications, contentDescription = null)
    }
}
""",
    )
    assert "A11Y-002" in _rules(module.scan())


def test_clickable_with_visible_text_passes(gate):
    module, src = gate
    _write(
        src,
        "WithText.kt",
        HEADER
        + """
fun SaveRow(onClick: () -> Unit) {
    Box(modifier = Modifier.clickable(onClick = onClick)) {
        Text("保存")
    }
}
""",
    )
    assert _rules(module.scan()) == []


def test_clickable_with_semantics_description_passes(gate):
    module, src = gate
    _write(
        src,
        "Semantics.kt",
        HEADER
        + """
fun CloseButton(contentDescription: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clickable(onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
    ) {
        Text("x")
    }
}
""",
    )
    assert _rules(module.scan()) == []


# ---- A11Y-003: 文本容器不得固定高度 ----


def test_fixed_height_text_container_is_reported(gate):
    module, src = gate
    _write(
        src,
        "Fixed.kt",
        HEADER
        + """
fun Segment(label: String) {
    Box(
        modifier = Modifier
            .height(34.dp)
            .padding(4.dp),
    ) {
        Text(label)
    }
}
""",
    )
    assert "A11Y-003" in _rules(module.scan())


def test_height_in_min_passes(gate):
    module, src = gate
    _write(
        src,
        "HeightIn.kt",
        HEADER
        + """
fun Segment(label: String) {
    Box(
        modifier = Modifier
            .heightIn(min = 34.dp)
            .padding(4.dp),
    ) {
        Text(label)
    }
}
""",
    )
    assert _rules(module.scan()) == []


def test_decorative_divider_is_not_reported(gate):
    module, src = gate
    _write(
        src,
        "Divider.kt",
        HEADER
        + """
fun Divider() {
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(QkColor.LineSoft))
}
""",
    )
    assert _rules(module.scan()) == []


# ---- 退出码契约：CI 只认 0 / 1 ----


def test_exit_code_zero_when_clean(gate, capsys):
    module, _ = gate
    _write(gate[1], "Clean.kt", HEADER + "\nfun Noop() = Unit\n")
    assert module.main([]) == 0
    assert "通过" in capsys.readouterr().out


def test_exit_code_one_when_violated(gate, capsys):
    module, src = gate
    _write(
        src,
        "Bad.kt",
        HEADER
        + """
fun BadIconButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(Icons.Filled.Close, contentDescription = null)
    }
}
""",
    )
    assert module.main([]) == 1
    out = capsys.readouterr().out
    assert "A11Y-001" in out
    assert "Bad.kt:" in out


if __name__ == "__main__":
    sys.exit(pytest.main([__file__, "-v"]))
