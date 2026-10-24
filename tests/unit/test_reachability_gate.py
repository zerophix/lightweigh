"""可达性门禁的契约测试（棘轮语义）。

背景：`gov-reachability-check.py` 用静态调用图证明「屏点不到」。如果它自己
坏了（永远返回 0），门禁就成了装饰。这里钉住三点：

1. 能识别出目标屏（解析没崩、也没把一切当可达）；
2. 基线棘轮：当前不可达 ⊆ 基线 → 通过；基线缺失时视为新增 → 失败；
3. `--update-baseline` 只写当前仍然不可达的项（已修复的不会回填）。
"""

from __future__ import annotations

import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "scripts/governance/gov-reachability-check.py"


def run(*args: str) -> subprocess.CompletedProcess:
    return subprocess.run(
        [sys.executable, str(SCRIPT), *args],
        cwd=ROOT,
        capture_output=True,
        text=True,
        timeout=120,
    )


def test_script_finds_targets():
    res = run()
    assert "目标屏" in res.stdout, res.stdout + res.stderr
    assert "目标屏 0 个" not in res.stdout


def test_baseline_ratchet_passes_with_current_baseline():
    res = run()
    assert res.returncode == 0, res.stdout
    assert "无新增不可达" in res.stdout


def test_empty_baseline_blocks_existing_unreachable(tmp_path: Path):
    """存量不可达清空后，这个阻塞场景就不适用了（说明债务已还完）。"""
    import pytest

    if "存量 0 项" in run().stdout:
        pytest.skip("当前无存量不可达，棘轮阻塞场景不再适用")
    empty = tmp_path / "empty-baseline.txt"
    empty.write_text("", encoding="utf-8")
    res = run("--baseline", str(empty))
    assert res.returncode == 1, res.stdout
    assert "新增不可达" in res.stdout


def test_update_baseline_ratchets_down(tmp_path: Path):
    baseline = tmp_path / "baseline.txt"
    # 先写入一条不存在的旧项，模拟「已修复但还在基线里」
    baseline.write_text("app/src/.../Gone.kt :: GoneScreen\n", encoding="utf-8")
    upd = run("--baseline", str(baseline), "--update-baseline")
    assert upd.returncode == 0, upd.stdout
    kept = baseline.read_text(encoding="utf-8")
    assert "GoneScreen" not in kept, kept
    assert "只减不增" in kept
