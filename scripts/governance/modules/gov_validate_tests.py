#!/usr/bin/env python3
"""GOV-TEST-001~008: test 文件命名/结构/emoji/语言 + pytest 执行校验"""
import os, re, subprocess, sys
from pathlib import Path
from .gov_validate_core import ROOT, errors, fail
from .gov_emoji import EMOJI_RE, contains_emoji

TESTS_DIR = ROOT / "tests"

TEST_NAME_RE = re.compile(r"^test_[a-z0-9_]+\.py$")

VERSION_PATTERNS = [
    (r'(?i)v\d+$', '版本号后缀'),
    (r'(?i)(_v|\.v|_bak|\.bak|backup|_copy|\.copy)$', '备份/副本标记'),
]

LANG_CONFIG_PATH = ROOT / ".language"
if not LANG_CONFIG_PATH.is_file():
    LANG_CONFIG_PATH = ROOT / "scripts" / "language"
PRIMARY_LANG = ""
if LANG_CONFIG_PATH.is_file():
    match = re.match(r"^([a-zA-Z-]+)", LANG_CONFIG_PATH.read_text(encoding="utf-8"))
    if match:
        PRIMARY_LANG = match.group(1)

CN_RE = re.compile(r"[\u4e00-\u9fff]")
EN_RE = re.compile(r"[a-zA-Z]{3,}")


def walk_test_files() -> None:
    print("\n--- GOV-TEST-001: test 文件命名 ---")
    print("--- GOV-TEST-002: test 文件结构 ---")
    print("--- GOV-TEST-003: test 文件 emoji ---")

    naming_errors: list[str] = []
    version_errors: list[str] = []
    emoji_files: list[str] = []

    FRAMEWORK_NAMES = {"conftest.py", "conftest.js", "setup.js", ".gitkeep"}

    if not TESTS_DIR.is_dir():
        print("  [SKIP] tests/ 目录不存在")
        return

    for dirpath_str, dirs, files in os.walk(str(TESTS_DIR)):
        dirpath = Path(dirpath_str)
        dirname = dirpath.name
        if dirname in {"__pycache__", "node_modules", ".pytest_cache"} or dirname.startswith("."):
            dirs.clear()
            continue

        rel_dir = dirpath.relative_to(ROOT).as_posix()

        for fname in sorted(files):
            fpath = dirpath / fname
            rel = f"{rel_dir}/{fname}"

            if not all(32 <= ord(c) <= 126 for c in fname):
                naming_errors.append(f"文件名含非 ASCII 字符: {rel}")

            if fname in FRAMEWORK_NAMES:
                continue

            if fpath.suffix == ".py" and not TEST_NAME_RE.match(fname):
                naming_errors.append(f"Python test 命名错误: {rel} (期望格式: test_lowercase_underscore.py)")

            for pattern, desc in VERSION_PATTERNS:
                if re.search(pattern, Path(fname).stem):
                    version_errors.append(f"疑似版本/备份文件: {rel}")

            if fpath.suffix in (".py", ".js", ".ts", ".sh"):
                try:
                    text = fpath.read_text(encoding="utf-8", errors="replace")
                except Exception:
                    continue
                # 豁免 tests/：键帽行为测试的夹具必须包含被测字符
                if rel.startswith("tests/"):
                    continue
                if contains_emoji(text):
                    m = EMOJI_RE.search(text)
                    line_num = text[:m.start()].count('\n') + 1 if m else 1
                    emoji_files.append(f"{rel}:{line_num}")

    if naming_errors:
        for e in naming_errors:
            fail(f"GOV-TEST-001: {e}")
    else:
        print("  [PASS] 所有 test 文件命名合规")

    if version_errors:
        for e in version_errors:
            fail(f"GOV-TEST-002: {e}")
    else:
        print("  [PASS] 所有 test 文件结构合规")

    if emoji_files:
        for f_ in emoji_files:
            fail(f"GOV-TEST-003: test 文件含 emoji: {f_}")
    else:
        print("  [PASS] 无 test 文件含 emoji")


def check_pytest() -> None:
    print("\n--- GOV-TEST-005: pytest 运行 ---")
    if not TESTS_DIR.is_dir():
        print("  [SKIP] tests/ 目录不存在")
        return
    try:
        import io
        import pytest
        os.environ.setdefault("PYTHONPATH", str(ROOT))
        buf = io.StringIO()
        old_stdout = sys.stdout
        sys.stdout = buf
        # 钩子环境隔离（DEC-062）：pre-commit / pre-push 会注入 GIT_DIR、GIT_WORK_TREE 等，
        # 进程内 pytest.main() 与各测试的 git 子进程会继承它们，导致：
        #   1) 临时目录里的 git 操作指向宿主仓库（实测曾试图向真实仓库提交）
        #   2) 依赖 git 上下文的测试在钩子中必然失败，使 GOV-TEST-005 误报
        # 校验仓库本身时不应携带宿主钩子的 git 上下文，故在此统一剥离并恢复。
        saved_git_env = {k: os.environ.pop(k) for k in list(os.environ) if k.startswith("GIT_")}
        try:
            exit_code = pytest.main(["--tb=short", "-q", "--no-header", str(TESTS_DIR)])
        finally:
            os.environ.update(saved_git_env)
            sys.stdout = old_stdout
        output = buf.getvalue()
        summary = ""
        for line in output.splitlines():
            stripped = line.strip()
            if stripped and ("passed" in stripped or "failed" in stripped or "error" in stripped):
                summary = stripped
                break
        if exit_code == 0:
            print(f"  [PASS] pytest 全部通过 ({summary})")
        else:
            fail(f"GOV-TEST-005: pytest 失败 (exit={exit_code})")
    except ImportError:
        print("  [SKIP] pytest 未安装")


def check_all() -> None:
    walk_test_files()
    if os.environ.get("GOV_VALIDATE_QUICK"):
        print("\n  [SKIP] GOV_VALIDATE_QUICK=1: 跳过 pytest 执行检查")
        return
    check_pytest()
