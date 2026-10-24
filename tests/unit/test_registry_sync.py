"""注册表自动同步（DEC-065）测试：派生数据由磁盘真源生成，功能分支不手写。"""
import subprocess
import sys
from pathlib import Path

ROOT_P = Path(__file__).resolve().parents[2]
SCRIPT = ROOT_P / "scripts" / "governance" / "gov-registry-sync.py"

HEADER = "commands:\n"
TAIL = "configs: []\norphans: []\n"


def _entry(path: str, desc: str = "人工描述") -> str:
    return (
        f"- description: {desc}\n"
        f"  path: {path}\n"
        "  status: stable\n"
        "  type: gradle-test\n"
    )


def _run(root: Path, *args: str) -> subprocess.CompletedProcess:
    return subprocess.run(
        [sys.executable, str(SCRIPT), "--root", str(root), *args],
        capture_output=True, text=True,
    )


def _make(root: Path, registry_entries: list[str], disk: list[str]) -> Path:
    reg = root / "tests" / "android-test-registry.yaml"
    reg.parent.mkdir(parents=True, exist_ok=True)
    reg.write_text(HEADER + "".join(registry_entries) + TAIL, encoding="utf-8")
    for rel in disk:
        p = root / rel
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text("// test\n", encoding="utf-8")
    return reg


def test_adds_missing_and_keeps_existing_verbatim(tmp_path):
    rel = "app/src/test/kotlin/NewTest.kt"
    reg = _make(
        tmp_path,
        [_entry("app/src/test/kotlin/OldTest.kt", "保留我原来的描述")],
        ["app/src/test/kotlin/OldTest.kt", rel],
    )

    res = _run(tmp_path)

    assert res.returncode == 0, res.stderr
    text = reg.read_text(encoding="utf-8")
    assert "保留我原来的描述" in text          # 已有条目原样保留
    assert rel in text                          # 新增条目
    assert "configs: []" in text and "orphans: []" in text  # 尾部段保留


def test_removes_zombie_entries(tmp_path):
    gone = "app/src/test/kotlin/GoneTest.kt"
    kept = "app/src/test/kotlin/KeptTest.kt"
    reg = _make(
        tmp_path,
        [_entry(gone), _entry(kept)],
        [kept],
    )

    res = _run(tmp_path)

    assert res.returncode == 0, res.stderr
    text = reg.read_text(encoding="utf-8")
    assert gone not in text
    assert kept in text


def test_android_test_sources_are_scanned(tmp_path):
    rel = "infrastructure/src/androidTest/kotlin/com/x/MigrationTest.kt"
    reg = _make(tmp_path, [], [rel])

    res = _run(tmp_path)

    assert res.returncode == 0, res.stderr
    assert rel in reg.read_text(encoding="utf-8")


def test_idempotent_second_run_is_noop(tmp_path):
    rel = "app/src/test/kotlin/OnlyTest.kt"
    _make(tmp_path, [], [rel])

    assert _run(tmp_path).returncode == 0
    first = (tmp_path / "tests" / "android-test-registry.yaml").read_text(encoding="utf-8")
    assert _run(tmp_path).returncode == 0
    second = (tmp_path / "tests" / "android-test-registry.yaml").read_text(encoding="utf-8")

    assert first == second


def test_dry_run_does_not_write(tmp_path):
    rel = "app/src/test/kotlin/DryTest.kt"
    reg = _make(tmp_path, [], [rel])
    before = reg.read_text(encoding="utf-8")

    res = _run(tmp_path, "--dry")

    assert res.returncode == 0, res.stderr
    assert reg.read_text(encoding="utf-8") == before


def test_fixture_files_are_not_registered(tmp_path):
    rel = "app/src/test/kotlin/SampleFixture.kt"
    reg = _make(tmp_path, [], [rel])

    assert _run(tmp_path).returncode == 0
    assert rel not in reg.read_text(encoding="utf-8")
