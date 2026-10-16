#!/usr/bin/env python3
"""指标本地化测试 (DEC-016): 证明 06_Metrics 由项目钩子驱动, 不再是模板通用指标。

治理设计约束 Q3(可验证性): 没有测试的"本地化"属于 hope-ware —— 钩子被删、
可执行位丢失、或 gov-update-metrics.sh 的覆盖入口被上游升级冲掉时, 无人会发现。
运行: pytest tests/unit/, 或由 gov-validate.sh 的 GOV-TEST-005 执行。
"""
import os
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
HOOK = ROOT / "scripts" / "governance" / "metrics-hooks" / "00-document.sh"
SCRIPT = ROOT / "scripts" / "governance" / "gov-update-metrics.sh"
METRICS = ROOT / "docs" / "ai" / "lifecycle" / "06_Metrics.md"

# 模板内置的通用指标（对 Android 工程是噪音, 本地化后必须消失）
TEMPLATE_MARKERS = ("Shell 脚本行数", "Python 脚本行数", "可执行脚本数")
# 本地化后必须出现的 Android 指标
APP_MARKERS = ("Gradle 模块", "主源码行数", "协议实现", "黄金向量")


def _render_hook() -> str:
    """运行钩子渲染（跳过 gov-validate, 避免 pytest 递归）。"""
    env = dict(os.environ, METRICS_SKIP_VALIDATE="1")
    out = subprocess.run(
        ["bash", str(HOOK)], capture_output=True, text=True, cwd=ROOT, env=env
    )
    assert out.returncode == 0, out.stderr
    return out.stdout


def test_hook_exists_and_is_executable():
    """本地化钩子必须存在且可执行, 否则脚本回退到模板通用指标。"""
    assert HOOK.is_file(), f"缺少指标本地化钩子: {HOOK}"
    assert os.access(HOOK, os.X_OK), "指标钩子必须可执行"


def test_script_keeps_project_override_entry():
    """漂移守卫: 上游升级不得静默移除 00-document.sh 覆盖入口。"""
    assert "00-document.sh" in SCRIPT.read_text(encoding="utf-8")


def test_hook_renders_android_metrics():
    """钩子输出必须是 Android 指标, 且不含模板通用指标。"""
    text = _render_hook()
    for marker in APP_MARKERS:
        assert marker in text, f"钩子输出缺少 Android 指标: {marker}"
    for marker in TEMPLATE_MARKERS:
        assert marker not in text, f"钩子输出残留模板指标: {marker}"


def test_generated_metrics_doc_is_localized():
    """落盘的 06_Metrics.md 必须是本地化结果（防脚本回退后文档未重生成）。"""
    text = METRICS.read_text(encoding="utf-8")
    assert "协议实现" in text, "06_Metrics.md 不是本地化输出"
    for marker in TEMPLATE_MARKERS:
        assert marker not in text, f"06_Metrics.md 残留模板指标: {marker}"
