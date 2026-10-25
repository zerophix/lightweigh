#!/usr/bin/env python3
"""可达性静态审计：从应用入口出发，哪些功能屏其实点不到。

背景（真实事故，不是假想）：
  - 12 个屏写完后没有任何导航入口，任务板却标"已完成"；
  - 三个组件（DeviceTips / MineExtensions / MetricDetailScreen）零调用方；
  - 首页八态只接了空态，其余状态在生产路径上不可达。

本脚本用**文件级调用图**给出最低保证：一个屏只要在入口可达闭包里出现过，
就至少存在一条静态调用路径；否则它一定点不到（除非通过反射/动态路由，
本仓库没有这种用法）。

判定口径：
  1. 定义：每个 .kt 文件里 `fun X(` 的顶层与成员函数名，记为该文件的定义。
  2. 边：若文件 A 的正文（去掉自身定义行）出现文件 B 定义的标识符，则 A→B。
  3. 入口集：MainActivity / MainShell / App / ScaleHubApp 等启动文件。
  4. 目标集：名字以 Screen / Flow / Route 结尾且处于 feature/ 目录的 composable。
  5. 不可达 = 目标集中不在入口闭包里的项。

盲点（明确写出来，避免假装完备）：
  - 同名函数不同模块会误连（用「同名即连通」换实现简单）；
  - 完全动态的 when(name) 路由算可达（因为名字出现在宿主文件里）；
  - 只查静态可达性，不查"点进去是否可用"（那需要 ui-test，本仓库暂无）。
"""

from __future__ import annotations

import argparse
import re
import sys
from collections import defaultdict, deque
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SRC = ROOT / "app/src/main/kotlin"

ENTRY_HINTS = (
    "MainActivity.kt",
    "MainShell.kt",
    "ShellChrome.kt",
    "App.kt",
    "ScaleHubApp.kt",
)

FUN_RE = re.compile(r"^\s*(?:@\w+\s*)*fun\s+(?:<[^>]+>\s*)?([A-Za-z_]\w*)\s*\(", re.MULTILINE)
TARGET_RE = re.compile(r"(Screen|Flow|Route|Page|Sheet)$")


def read_files() -> dict[Path, str]:
    out: dict[Path, str] = {}
    for p in sorted(SRC.rglob("*.kt")):
        try:
            out[p] = p.read_text(encoding="utf-8")
        except OSError:
            continue
    return out


def overlay_push_audit(files: dict[Path, str]) -> list[str]:
    """覆盖页的"用户入口"审计。

    静态可达性把「宿主 when 分支里出现过」算可达，于是 Guide 这类
    "有宿主映射、但全库没有任何 Push" 的屏会被误判为可达。这里单独查一件事：
    每个 NavOverlay 是否至少有一个真实的 push 调用点（排除宿主映射与序列化）。
    """
    nav_file = SRC / "ui/shell/NavState.kt"
    text = files.get(nav_file, "")
    overlays = re.findall(r"data (?:object|class)\s+(\w+)\s*(?:\([^)]*\))?\s*:\s*NavOverlay", text)
    ignore = {"MainShell.kt", "NavState.kt"}
    missing = []
    for name in overlays:
        pat = re.compile(r"(?:Push|ReplaceTop)\(\s*NavOverlay\." + re.escape(name) + r"\b")
        if not any(pat.search(t) for f, t in files.items() if f.name not in ignore):
            missing.append(name)
    return missing


def build_graph(files: dict[Path, str]):
    defs: dict[str, set[Path]] = defaultdict(set)
    for path, text in files.items():
        for name in FUN_RE.findall(text):
            defs[name].add(path)

    edges: dict[Path, set[Path]] = defaultdict(set)
    for path, text in files.items():
        own = {n for n, ps in defs.items() if path in ps}
        for name, ps in defs.items():
            if name in own:
                continue
            if re.search(rf"\b{re.escape(name)}\b", text):
                edges[path] |= ps
    return defs, edges


def reachable(edges: dict[Path, set[Path]], roots: set[Path]) -> set[Path]:
    seen = set(roots)
    q = deque(roots)
    while q:
        cur = q.popleft()
        for nxt in edges.get(cur, ()):  # 正向：调用者 → 被调用者
            if nxt not in seen:
                seen.add(nxt)
                q.append(nxt)
    return seen


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--json", action="store_true")
    ap.add_argument("--all", action="store_true", help="列出全部目标屏状态")
    ap.add_argument("--baseline", type=Path, default=ROOT / "scripts/governance/gov-reach-baseline.txt",
                    help="存量不可达清单；新出现即失败（棘轮）")
    ap.add_argument("--update-baseline", action="store_true", help="把当前不可达写入基线（只能变小）")
    args = ap.parse_args()

    files = read_files()
    if not files:
        print(f"[reach] 未找到源码：{SRC}")
        return 2

    defs, edges = build_graph(files)
    roots = {p for p in files if p.name in ENTRY_HINTS}
    if not roots:
        print("[reach] 未找到入口文件，请先更新 ENTRY_HINTS")
        return 2

    seen = reachable(edges, roots)

    targets = []
    for path, text in files.items():
        if "/feature/" not in path.as_posix():
            continue
        for name in FUN_RE.findall(text):
            if TARGET_RE.search(name):
                targets.append((name, path))

    unreachable = sorted((name, p) for name, p in targets if p not in seen)

    # 覆盖页必须真的有 push 点：宿主映射不算用户入口
    missing_overlays = overlay_push_audit(files)

    if args.json:
        import json

        print(
            json.dumps(
                {
                    "targets": len(targets),
                    "unreachable": [{"name": n, "file": p.as_posix()} for n, p in unreachable],
                    "roots": sorted(p.as_posix() for p in roots),
                },
                ensure_ascii=False,
                indent=2,
            )
        )
        return 0 if not unreachable else 1

    print(f"[reach] 入口 {len(roots)} 个，目标屏 {len(targets)} 个，可达 {len(targets) - len(unreachable)} 个")
    if missing_overlays:
        print(f"[reach] 覆盖页缺 push 入口 {len(missing_overlays)} 个：")
        for name in missing_overlays:
            print(f"  ✗ NavOverlay.{name} — 宿主有映射，但全库没有 Push/ReplaceTop 调用点")
    if args.all:
        for name, p in sorted(targets):
            mark = "可达" if (name, p) not in set(unreachable) else "不可达"
            print(f"  [{mark}] {p.relative_to(ROOT)} :: {name}")

    current = {f"{p.relative_to(ROOT)} :: {name}" for name, p in unreachable}
    current |= {f"NavOverlay.{name} :: 无 push 入口" for name in missing_overlays}

    if args.update_baseline:
        previous = set()
        if args.baseline.exists():
            previous = {
                ln.strip()
                for ln in args.baseline.read_text(encoding="utf-8").splitlines()
                if ln.strip() and not ln.startswith("#")
            }
        kept = current & previous  # 棘轮：只允许变小，已修复的不写回
        args.baseline.write_text(
            "# 存量不可达屏（棘轮基线，只减不增）\n"
            "# 由 gov-reachability-check.py --update-baseline 生成\n"
            + "".join(f"{ln}\n" for ln in sorted(kept or current)),
            encoding="utf-8",
        )
        print(f"[reach] 基线已更新：{len(kept or current)} 项 -> {args.baseline}")
        return 0

    baseline = set()
    if args.baseline.exists():
        baseline = {
            ln.strip()
            for ln in args.baseline.read_text(encoding="utf-8").splitlines()
            if ln.strip() and not ln.startswith("#")
        }

    new_ones = sorted(current - baseline)
    fixed = sorted(baseline - current)

    if fixed:
        print(f"[reach] 已修复 {len(fixed)} 项（基线可收紧）：")
        for ln in fixed:
            print(f"  ✓ {ln}")

    if new_ones:
        print(f"[reach] 新增不可达 {len(new_ones)} 项（点不到，且不在基线内）：")
        for ln in new_ones:
            print(f"  ✗ {ln}")
        print("[reach] 修复方式：接进入口，或确认为废弃屏后从基线移除并删代码")
        return 1

    print(f"[reach] 无新增不可达（存量 {len(current)} 项，基线 {len(baseline)} 项）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
