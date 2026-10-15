#!/usr/bin/env python3
"""GOV-TEST-GOV-001~012: 测试体系治理 (镜像 GOV-SCRIPT-001~012)"""
import os, re, subprocess, yaml
from datetime import datetime, timezone
from pathlib import Path
from .gov_validate_core import ROOT, errors, fail

TESTS_DIR = ROOT / "tests"

TEST_CATEGORIES = {
    # 以下目录按项目需要创建后自动校验:
    # "unit": "test_", "integration": "test_", "e2e": "test_e2e_",
    "experiments": "exp_",
    "archive": "archive_",
}

TEST_NAUGHTY_PATTERNS = [
    (r'(?i)_v\d+$', '版本号后缀'),
    (r'(?i)(_v|\.v|_bak|\.bak|backup|_copy|\.copy)$', '备份/副本标记'),
    (r'(?i)(_old|\.old|_legacy|\.legacy)$', '旧版标记'),
]

TEST_REGISTRY = TESTS_DIR / "registry.yaml"

TEST_SMOKE_SKIP = {}  # Add test paths here to skip smoke tests

TEST_FRAMEWORK_FILES = {
    "conftest.py", "conftest.js", "setup.js", ".gitkeep",
    "pytest.ini", "jest.config.js", "tsconfig.json", "test_sanity.py",
}


def check_registry_integrity() -> int:
    print("\n--- GOV-TEST-GOV-001: 测试注册表完整性 ---")
    found_issues = 0
    if not TEST_REGISTRY.is_file():
        fail("GOV-TEST-GOV-001: tests/registry.yaml 不存在")
        return 1
    try:
        data = yaml.safe_load(TEST_REGISTRY.read_text(encoding="utf-8"))
    except Exception as e:
        fail(f"GOV-TEST-GOV-001: registry.yaml 解析失败: {e}")
        return 1

    configs = data.get("configs", [])
    for cfg in configs:
        path = cfg.get("path", "")
        critical = cfg.get("critical", False)
        if not path:
            continue
        fpath = ROOT / path
        if not fpath.is_file():
            if critical:
                fail(f"GOV-TEST-GOV-001: 关键配置文件缺失: {path}")
            else:
                fail(f"GOV-TEST-GOV-001: registry 僵尸条目（配置文件不存在）: {path}")
            found_issues += 1

    commands = data.get("commands", [])
    for entry in commands:
        path = entry.get("path", "")
        critical = entry.get("critical", False)
        ttype = entry.get("type", "pytest")
        if not path:
            continue
        fpath = ROOT / path
        if not fpath.is_file():
            if critical:
                fail(f"GOV-TEST-GOV-001: 关键测试缺失: {path}")
            else:
                fail(f"GOV-TEST-GOV-001: registry 僵尸条目（测试文件不存在）: {path}")
            found_issues += 1
            continue
        if ttype in ("pytest", "unit", "integration", "e2e") and path.endswith(".py"):
            try:
                compile(fpath.read_text(encoding="utf-8"), path, "exec")
            except SyntaxError as e:
                fail(f"GOV-TEST-GOV-001: {path}: Python 语法错误: {e}")
                found_issues += 1
        elif ttype == "jest" and path.endswith((".js", ".ts")):
            r = subprocess.run(["node", "--check", str(fpath)], capture_output=True, text=True)
            if r.returncode != 0:
                fail(f"GOV-TEST-GOV-001: {path}: JS/TS 语法错误: {r.stderr.strip()[:120]}")
                found_issues += 1
        if path.endswith(".sh") and not os.access(str(fpath), os.X_OK):
            fail(f"GOV-TEST-GOV-001: {path}: 无可执行权限")
            found_issues += 1

    if found_issues == 0:
        print("  [PASS] 所有注册测试/配置存在、语法正确、权限完整")
    return found_issues


def check_prefix_rules() -> int:
    print("\n--- GOV-TEST-GOV-002: 测试目录前缀规范 ---")
    found_issues = 0
    for subdir, prefix in TEST_CATEGORIES.items():
        d = TESTS_DIR / subdir
        if not d.is_dir():
            continue
        for f in d.iterdir():
            if not f.is_file() or f.name.startswith(".") or f.name.startswith("_"):
                continue
            if f.name in TEST_FRAMEWORK_FILES:
                continue
            if not f.name.startswith(prefix):
                fail(f"GOV-TEST-GOV-002: {subdir}/{f.name}: 必须以 {prefix} 开头")
                found_issues += 1

    if found_issues == 0:
        print("  [PASS] 所有测试文件符合前缀规范")
    return found_issues


def check_naughty_names() -> int:
    print("\n--- GOV-TEST-GOV-003: 测试命名坏味道 ---")
    found_issues = 0
    for subdir in TEST_CATEGORIES:
        d = TESTS_DIR / subdir
        if not d.is_dir():
            continue
        for f in d.iterdir():
            if not f.is_file() or f.name.startswith(".") or f.name.startswith("_"):
                continue
            if f.name in TEST_FRAMEWORK_FILES:
                continue
            stem = Path(f.name).stem
            for pattern, desc in TEST_NAUGHTY_PATTERNS:
                if re.search(pattern, stem):
                    fail(f"GOV-TEST-GOV-003: {subdir}/{f.name}: 脚本名含{desc}")
                    found_issues += 1
    if found_issues == 0:
        print("  [PASS] 无坏味道测试名")
    return found_issues


def check_reverse_registry() -> int:
    print("\n--- GOV-TEST-GOV-005: 注册表反向校验 ---")
    found_issues = 0
    SKIP_EXTENSIONS = {".md", ".txt", ".pyc", ".json", ".yaml", ".yml", ".toml", ".ini", ".cfg"}
    if not TEST_REGISTRY.is_file():
        fail("GOV-TEST-GOV-005: registry.yaml 不存在")
        return 1
    try:
        data = yaml.safe_load(TEST_REGISTRY.read_text(encoding="utf-8"))
        registered_paths = {entry.get("path", "") for entry in data.get("commands", []) if entry.get("path")}
    except Exception as e:
        fail(f"GOV-TEST-GOV-005: registry.yaml 解析失败: {e}")
        return 1

    for subdir in TEST_CATEGORIES:
        d = TESTS_DIR / subdir
        if not d.is_dir():
            continue
        for f in d.rglob("*"):
            if not f.is_file() or f.name.startswith(".") or f.name.startswith("_"):
                continue
            if f.suffix in SKIP_EXTENSIONS:
                continue
            if f.name in TEST_FRAMEWORK_FILES:
                continue
            rel = f.relative_to(ROOT).as_posix()
            if rel not in registered_paths:
                entry = next((e for e in data.get("commands", []) if e.get("path") == rel), None)
                status = entry.get("status") if entry else "unknown"
                if status not in ("archived", "experiments"):
                    fail(f"GOV-TEST-GOV-005: 磁盘测试未在 registry.yaml 注册: {rel}")
                    found_issues += 1

    for f in TESTS_DIR.iterdir():
        if f.is_file() and f.suffix in (".py", ".js", ".ts") and f.name not in TEST_FRAMEWORK_FILES:
            fail(f"GOV-TEST-GOV-005: tests/ 根目录不允许放独立测试文件: tests/{f.name}")
            found_issues += 1

    if found_issues == 0:
        print("  [PASS] 所有磁盘测试已在 registry.yaml 注册")
    return found_issues


def check_empty_dirs() -> int:
    print("\n--- GOV-TEST-GOV-010: 测试空目录检测 ---")
    found_issues = 0
    WHITELIST = {"__pycache__", "node_modules", ".pytest_cache", ".jest_cache"}
    TEST_EMPTY_WHITELIST = {"archive", "experiments"}
    for dirpath, dirnames, filenames in os.walk(str(TESTS_DIR)):
        rel = Path(dirpath).relative_to(TESTS_DIR)
        if rel == Path("."):
            continue
        parts = rel.parts
        if any(p in WHITELIST or p.startswith(".") for p in parts):
            continue
        rp = Path(dirpath)
        has_content = any(
            (rp / entry).is_file() or (rp / entry).is_dir()
            for entry in os.listdir(dirpath)
            if not entry.startswith(".") and entry not in WHITELIST
        )
        if not has_content:
            leaf_dir = parts[-1] if parts else ""
            if leaf_dir in TEST_EMPTY_WHITELIST:
                continue
            fail(f"GOV-TEST-GOV-010: tests 空目录: {rel}，请放入文件或删除目录")
            found_issues += 1
    if found_issues == 0:
        print("  [PASS] 无空目录")
    return found_issues


def check_all() -> None:
    check_registry_integrity()
    check_prefix_rules()
    check_naughty_names()
    check_reverse_registry()
    check_empty_dirs()
