#!/usr/bin/env python3
"""GOV-HYG-001: 仓库内禁止备份/快照残留文件。

## 为什么单独立一条规则

`GOV-ATEST-003`（Android 测试版本/备份命名）名义上管「备份命名」，但它的候选集由
`_walk_test_files` 产出，而该函数只收 `.kt` / `.java` 结尾的文件。`FooTest.kt.bak`
的扩展名是 `.bak`，**根本进不了候选集**，于是这条规则在结构上永远看不见 `.bak`。

2026-10-27 实测：4 个测试文件被改名成 `.bak`（31 个 auth 屏测试 + 9 个健康/扫码端口
测试随之静默失效），`GOV-ATEST-003` 仍报 `[PASS] 无版本/备份命名`。有规则、无执行者，
等于没有。

本模块补上执行者，并且作用域是**整个仓库**而非测试目录——备份残留不分目录。

## 与 GOV-ATEST-003 的分工

- 本规则：按扩展名/后缀识别备份产物（`.bak`、`.orig`、`~` …），全仓生效
- ATEST-003：识别 Kotlin/Java 测试**文件名主干**里的版本后缀（`FooTestV2.kt`）

两者不重叠：`.bak` 归本规则，`V2` 归 ATEST-003。
"""
import os
import re
from pathlib import Path

from .gov_validate_core import ROOT as _CORE_ROOT
from .gov_validate_core import fail

# 允许在测试里 monkeypatch 到临时目录（与其它治理模块保持同一套写法）
ROOT = _CORE_ROOT

# 构建产物与工具缓存：里面的临时/备份文件不算仓库内容
IGNORED_DIRS = {
    ".gradle",
    ".gradle-user",
    ".idea",
    ".kotlin",
    ".cxx",
    ".cache",
    ".workbuddy-ai",
    ".worktrees",
    ".venv",
    "venv",
    "build",
    "dist",
    "node_modules",
    "out",
    "__pycache__",
}

# 点目录默认全部剪掉（它们是工具缓存，不是仓库内容），只放行这几个可能有仓库文件的。
# 教训（2026-10-27）：最初只按名字黑名单剪，漏了 `.gradle-user`（5.1 GB / 46474 文件），
# 一个模块就走了 29857 步，把整个 gov-validate 从 3s 拖到 17.5s。
DOT_DIRS_TO_SCAN = {".github", ".ai", ".opencode", ".claude"}

# (正则, 说明) —— 全部作用于「文件名」，不是 stem
BACKUP_PATTERNS: list[tuple[str, str]] = [
    (r"\.bak\d*$", ".bak 备份文件"),
    (r"\.bak\.\d+$", ".bak.N 备份文件"),
    (r"\.backup$", ".backup 备份文件"),
    (r"\.orig$", ".orig 合并残留"),
    (r"\.rej$", ".rej 合并残留"),
    (r"\.old$", ".old 旧版本"),
    (r"\.save$", ".save 临时保存"),
    (r"\.copy$", ".copy 副本"),
    (r"\.swp$|\.swo$", "编辑器交换文件"),
    (r"~$", "编辑器备份（~ 结尾）"),
    (r"副本|备份", "文件名含「副本/备份」"),
]

_COMPILED = [(re.compile(p), d) for p, d in BACKUP_PATTERNS]


def _iter_files(root: Path):
    """遍历仓库文件，跳过忽略目录与符号链接。

    剪枝口径：显式忽略名单 + 「点目录默认不扫，只放行 DOT_DIRS_TO_SCAN」。
    后者是兜底——任何新出现的工具缓存目录都不会再拖慢遍历。
    """
    for dirpath, dirnames, filenames in os.walk(root):
        # 原地裁剪，避免 os.walk 进入忽略目录
        dirnames[:] = [
            d for d in dirnames
            if d not in IGNORED_DIRS
            and (not d.startswith(".") or d in DOT_DIRS_TO_SCAN)
        ]
        for fname in filenames:
            p = Path(dirpath) / fname
            if p.is_symlink():
                continue
            yield p


def scan(root: Path | None = None) -> list[str]:
    """返回违规描述列表（不打印）。便于单测直接断言。"""
    base = root or ROOT
    hits: list[str] = []
    for p in _iter_files(base):
        name = p.name
        # worktree 下 .git 是文件而非目录，属 git 内部结构
        if name == ".git":
            continue
        for rx, desc in _COMPILED:
            if rx.search(name):
                try:
                    rel = p.relative_to(base).as_posix()
                except ValueError:
                    rel = p.as_posix()
                hits.append(f"{rel} ({desc})")
                break
    return sorted(hits)


def check_all() -> None:
    print("\n--- GOV-HYG-001: 禁止备份/快照残留文件 ---")
    hits = scan()
    for h in hits[:20]:
        fail(f"GOV-HYG-001: {h}")
    if len(hits) > 20:
        fail(f"GOV-HYG-001: 另有 {len(hits) - 20} 项未列出（共 {len(hits)} 项）")
    if not hits:
        print("  [PASS] 无备份/快照残留文件")
