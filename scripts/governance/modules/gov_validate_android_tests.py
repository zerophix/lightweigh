#!/usr/bin/env python3
"""GOV-ATEST-001~005: Android 单元测试治理 — 命名/版本/emoji/注册表

镜像 tests/ 下 pytest 治理（gov_validate_tests.py / gov_validate_tests_gov.py）
的规则，治理的是 Android 工程各 Gradle 模块 `src/test` 与 `src/androidTest` 下
的 Kotlin/Java 测试文件。两者互补不重叠：

- 治理测试用例（pytest, `tests/`）→ GOV-TEST-* / GOV-TEST-GOV-*
- 单元测试方法数（`@Test` 计数）→ metrics-hooks/00-document.sh
- Android 单元测试文件（本模块）→ GOV-ATEST-001~005

源码目录单一事实源取 gov-config.sh 的 SRC_DIRS（与 pre-commit 同源，防漂移）。
"""
import os, re, yaml
from pathlib import Path
from .gov_validate_core import ROOT, errors, fail
from .gov_emoji import EMOJI_RE, contains_emoji

GOV_CONFIG = ROOT / "scripts" / "governance" / "gov-config.sh"
REGISTRY_PATH = ROOT / "tests" / "android-test-registry.yaml"

VERSION_PATTERNS = [
    (r'(?i)v\d+$', '版本号后缀'),
    (r'(?i)(_v|\.v|_bak|\.bak|backup|_copy|\.copy)$', '备份/副本标记'),
]

# PascalCase 且以 Test 或 Fixture 结尾。
# Fixture 后缀放行: RuleViolationFixture.kt 是架构守卫引用的夹具, 与 pytest 侧
# conftest.py 豁免同思路（用后缀而非清单, 免维护）。
TEST_STEM_RE = re.compile(r"^[A-Z][A-Za-z0-9]*(?:Test|Fixture)$")


def _parse_src_dirs(root: Path) -> list:
    if not (root / GOV_CONFIG.relative_to(ROOT)).is_file():
        return []
    text = (root / GOV_CONFIG.relative_to(ROOT)).read_text(encoding="utf-8")
    m = re.search(r'^SRC_DIRS="([^"]*)"', text, re.MULTILINE)
    return m.group(1).split() if m else []


def _walk_test_files(root: Path, src_dirs: list) -> list:
    """返回 [(相对路径, Path)]；跳过 build/ 产物。"""
    found = []
    for mod in src_dirs:
        for kind in ("test", "androidTest"):
            d = root / mod / "src" / kind
            if not d.is_dir():
                continue
            for dirpath, dirs, files in os.walk(str(d)):
                dp = Path(dirpath)
                if "build" in dp.parts:
                    dirs.clear()
                    continue
                for fname in sorted(files):
                    if fname.endswith((".kt", ".java")):
                        found.append((dp, fname))
    return found


def _load_registry(root: Path) -> list:
    rp = root / REGISTRY_PATH.relative_to(ROOT)
    if not rp.is_file():
        return []
    data = yaml.safe_load(rp.read_text(encoding="utf-8")) or {}
    return [e.get("path", "") for e in (data.get("commands") or []) if e.get("path")]


def check_all() -> None:
    src_dirs = _parse_src_dirs(ROOT) or ["domain", "application", "infrastructure", "app"]
    files = _walk_test_files(ROOT, src_dirs)

    nonascii, naming, version, emoji = [], [], [], []

    for dp, fname in files:
        rel = (dp / fname).relative_to(ROOT).as_posix()
        if not all(32 <= ord(c) <= 126 for c in fname):
            nonascii.append(rel)
        stem = Path(fname).stem
        if not TEST_STEM_RE.match(stem):
            naming.append(f"{rel} (期望: PascalCase 且以 Test 或 Fixture 结尾)")
        for pat, desc in VERSION_PATTERNS:
            if re.search(pat, stem):
                version.append(f"{rel} ({desc})")
        try:
            text = (dp / fname).read_text(encoding="utf-8", errors="replace")
        except Exception:
            continue
        if contains_emoji(text):
            m = EMOJI_RE.search(text)
            line = text[:m.start()].count("\n") + 1 if m else 1
            emoji.append(f"{rel}:{line}")

    print("\n--- GOV-ATEST-001: Android 测试文件名非 ASCII ---")
    for f_ in nonascii:
        fail(f"GOV-ATEST-001: 文件名含非 ASCII 字符: {f_}")
    if not nonascii:
        print("  [PASS] 所有 Android 测试文件名使用 ASCII")

    print("\n--- GOV-ATEST-002: Android 测试命名规范 ---")
    for e in naming:
        fail(f"GOV-ATEST-002: {e}")
    if not naming:
        print("  [PASS] 所有 Android 测试文件命名合规")

    print("\n--- GOV-ATEST-003: Android 测试版本/备份命名 ---")
    for f_ in version:
        fail(f"GOV-ATEST-003: {f_}")
    if not version:
        print("  [PASS] 无版本/备份命名")

    print("\n--- GOV-ATEST-004: Android 测试文件 emoji ---")
    for f_ in emoji:
        fail(f"GOV-ATEST-004: {f_}")
    if not emoji:
        print("  [PASS] 无 Android 测试文件含 emoji")

    print("\n--- GOV-ATEST-005: Android 测试注册表完整性 ---")
    reg = _load_registry(ROOT)
    if not REGISTRY_PATH.is_file():
        fail("GOV-ATEST-005: tests/android-test-registry.yaml 不存在")
        reg = []
    # 反向: Test 后缀文件必须已注册（Fixture 是夹具, 豁免注册）
    unregistered = [
        (dp / fname).relative_to(ROOT).as_posix()
        for dp, fname in files
        if Path(fname).stem.endswith("Test")
        and (dp / fname).relative_to(ROOT).as_posix() not in reg
    ]
    # 正向: 已注册但磁盘不存在 = 僵尸
    registered_paths = {(dp / fname).relative_to(ROOT).as_posix() for dp, fname in files}
    zombies = [p for p in reg if p not in registered_paths]
    if unregistered:
        for p in unregistered:
            fail(f"GOV-ATEST-005: 磁盘测试未在 android-test-registry.yaml 注册: {p}")
    if zombies:
        for p in zombies:
            fail(f"GOV-ATEST-005: 注册表僵尸条目（测试文件不存在）: {p}")
    if not unregistered and not zombies:
        print("  [PASS] 所有 Android 测试文件已在注册表登记，无僵尸条目")