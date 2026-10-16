#!/usr/bin/env python3
"""GOV-SCRIPT-001~009: 脚本体系完整性校验"""
import os, re, subprocess
from datetime import datetime, timezone
from pathlib import Path
from .gov_validate_core import ROOT, errors, fail

SCRIPTS_DIR = ROOT / "scripts"
REGISTRY = SCRIPTS_DIR / "registry.yaml"

PREFIX_RULES = {
    "governance": "gov-",
    "build": "build-",
    "bootstrap": "bs-",
    "session": "ses-",
    "workflow": "wf-",
    "migration": "mig-",
    "lib": "lib-",
}

SCRIPT_NAUGHTY_PATTERNS = [
    (r'(?i)v\d+$', '版本号后缀'),
    (r'(?i)(_v|\.v|_bak|\.bak|backup|_copy|\.copy)$', '备份/副本标记'),
]


def check_registry_exists() -> None:
    print("\n--- GOV-SCRIPT-001: registry.yaml 存在性 ---")
    if REGISTRY.is_file():
        print("  [PASS] registry.yaml 存在")
    else:
        fail("GOV-SCRIPT-001: registry.yaml 不存在。请在 scripts/ 下创建 registry.yaml 注册所有脚本")
        return


def check_prefix_rules() -> int:
    print("\n--- GOV-SCRIPT-002: 目录前缀规范 ---")
    found_issues = 0
    for subdir, prefix in PREFIX_RULES.items():
        d = SCRIPTS_DIR / subdir
        if not d.is_dir():
            continue
        for f_ in d.iterdir():
            if not f_.is_file() or f_.name.startswith("."):
                continue
            if f_.name.startswith("_"):
                continue
            if not f_.name.startswith(prefix):
                fail(f"GOV-SCRIPT-002: {subdir}/{f_.name}: 必须以 {prefix} 开头")
                found_issues += 1
    if found_issues == 0:
        print("  [PASS] 所有脚本符合前缀规范")
    return found_issues


def check_naughty_names() -> int:
    print("\n--- GOV-SCRIPT-003: 脚本命名坏味道 ---")
    found_issues = 0
    for subdir in PREFIX_RULES:
        d = SCRIPTS_DIR / subdir
        if not d.is_dir():
            continue
        for f_ in d.iterdir():
            if not f_.is_file() or f_.name.startswith("."):
                continue
            stem = Path(f_.name).stem
            for pattern, desc in SCRIPT_NAUGHTY_PATTERNS:
                if re.search(pattern, stem):
                    fail(f"GOV-SCRIPT-003: {subdir}/{f_.name}: 脚本名含{desc}")
                    found_issues += 1
    if found_issues == 0:
        print("  [PASS] 无坏味道脚本名")
    return found_issues


def check_registry_integrity() -> int:
    print("\n--- GOV-SCRIPT-004: registry 完整性校验 ---")
    found_issues = 0
    if not REGISTRY.is_file():
        fail("GOV-SCRIPT-004: registry.yaml 不存在")
        return 1
    try:
        import yaml
        data = yaml.safe_load(REGISTRY.read_text(encoding="utf-8"))
    except Exception as e:
        fail(f"GOV-SCRIPT-004: registry.yaml 解析失败: {e}")
        return 1
    commands = data.get("commands", []) or []
    configs = data.get("configs", []) or []

    def _resolve(path: str):
        # tools/* 相对项目根, 其余相对 scripts/
        return ROOT / path if path.startswith("tools/") else SCRIPTS_DIR / path

    # 正向: 注册的路径必须存在（registry 是命令清单, 只保证注册的命令在磁盘上）
    for entry in list(commands) + list(configs):
        p = entry.get("path", "")
        if not p:
            continue
        if not _resolve(p).is_file():
            fail(f"GOV-SCRIPT-004: 注册表僵尸条目（脚本不存在）: {p}")
            found_issues += 1

    if found_issues == 0:
        print("  [PASS] 所有注册脚本存在, 无僵尸条目")
    return found_issues


def check_root_scripts() -> int:
    print("\n--- GOV-SCRIPT-009: scripts/ 根目录裸脚本 + 项目根脚本 ---")
    found_issues = 0
    for f_ in SCRIPTS_DIR.iterdir():
        if f_.is_file() and f_.suffix in (".sh", ".py", ".js", ".ts") and not f_.name.startswith("."):
            fail(f"GOV-SCRIPT-009: scripts/ 根目录不允许独立脚本: scripts/{f_.name}")
            found_issues += 1
    for f_ in ROOT.iterdir():
        if not f_.is_file() or f_.name.startswith("."):
            continue
        if f_.suffix in (".sh", ".py"):
            if f_.name in ("setup.sh", "validate.sh"):
                continue
            fail(f"GOV-SCRIPT-009: 项目根目录禁止脚本: {f_.name}")
            found_issues += 1
    if found_issues == 0:
        print("  [PASS] 无根目录散落脚本")
    return found_issues


def check_all() -> None:
    check_registry_exists()
    check_registry_integrity()
    check_prefix_rules()
    check_naughty_names()
    check_root_scripts()
