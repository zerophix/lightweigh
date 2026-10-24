#!/usr/bin/env python3
"""端到端走查：在真机/模拟器上按用户路径点击，并留下截图与断言。

为什么需要它：其余门禁证明的是"屏存在且可达"，没有一个证明"点进去能用"。
108 个屏全部有 @Preview，但预览不会告诉你点「开始测量」会不会崩。

用法：
  python3 scripts/testing/e2e_journey.py probe              # 启动并列出可点项
  python3 scripts/testing/e2e_journey.py run <journey.json>  # 跑一条用户路径
  python3 scripts/testing/e2e_journey.py run <journey.json> --no-install

journey.json 结构：
  {
    "name": "首次启动到首页",
    "steps": [
      {"name": "启动", "expect": ["登录"]},
      {"name": "选游客", "tap": "游客", "expect": ["完善资料"]},
      {"name": "截图点", "shot": true}
    ]
  }

判定口径：
  - expect 里的每个字符串都必须出现在当前屏幕的控件文本里（子串匹配）
  - tap 找不到目标即判失败（不静默跳过，否则走查会假通过）
  - 每步默认截图；失败时额外保存 dump.xml，便于事后定位
退出码：0 全部通过 / 1 有失败 / 2 环境问题（无设备、未安装等）
"""

from __future__ import annotations

import json
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ADB = Path.home() / "Library/Android/sdk/platform-tools/adb"
if not ADB.exists():
    from shutil import which

    found = which("adb")
    ADB = Path(found) if found else ADB

PKG = "com.xcwk.scalehub"
ACTIVITY = f"{PKG}/{PKG}.MainActivity"
OUT_ROOT = ROOT / ".cache" / "e2e"


def adb(*args: str, timeout: int | str | float = 60) -> subprocess.CompletedProcess:
    return subprocess.run([str(ADB), *args], capture_output=True, text=True, timeout=timeout)


def require_device() -> None:
    r = adb("devices")
    if "device" not in [ln.split("\t")[0].strip() for ln in r.stdout.splitlines()[1:]] and "device" not in r.stdout:
        print("❌ 没有可用设备（adb devices 为空）", file=sys.stderr)
        raise SystemExit(2)


def install(apk: Path | None) -> None:
    if apk is None:
        apk = ROOT / "app/build/outputs/apk/debug/app-debug.apk"
    if not apk.exists():
        print(f"❌ APK 不存在: {apk}（先跑 ./gradlew :app:assembleDebug）", file=sys.stderr)
        raise SystemExit(2)
    r = adb("install", "-r", "-t", str(apk), timeout=300)
    if "Success" not in r.stdout:
        print(f"❌ 安装失败: {r.stdout} {r.stderr}", file=sys.stderr)
        raise SystemExit(2)
    print(f"已安装 {apk.name}")


def dump_xml() -> str:
    adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
    return adb("shell", "cat", "/sdcard/ui.xml").stdout


def nodes(xml: str):
    """返回 [(text, desc, clickable, bounds)]。控件文本包含 text 与 content-desc。"""
    out = []
    try:
        root = ET.fromstring(xml)
    except ET.ParseError:
        return out
    for n in root.iter("node"):
        text = n.get("text") or ""
        desc = n.get("content-desc") or ""
        clickable = n.get("clickable") == "true"
        bounds = n.get("bounds") or ""
        if text or desc:
            out.append((text, desc, clickable, bounds))
    return out


def center_of(bounds: str) -> tuple[int, int] | None:
    m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", bounds)
    if not m:
        return None
    x1, y1, x2, y2 = map(int, m.groups())
    return (x1 + x2) // 2, (y1 + y2) // 2


def visible_texts(xml: str) -> str:
    return "\n".join(f"{t} {d}" for t, d, _, _ in nodes(xml))


def tap_text(xml: str, label: str) -> bool:
    """优先精确匹配，其次子串匹配；短标签（如「保存」）最容易误点邻项。"""
    # 顺序很重要：可点且精确 > 可点且包含 > 精确 > 包含。
    # 反例：权限弹窗的说明文字以 "Allow ..." 开头，先匹配到它就会点空。
    items = [(t, d, c, b) for t, d, c, b in nodes(xml)]
    rules = (
        (lambda t, d: t == label or d == label, True),
        (lambda t, d: label in t or label in d, True),
        (lambda t, d: t == label or d == label, False),
        (lambda t, d: label in t or label in d, False),
    )
    for matcher, need_clickable in rules:
        for text, desc, clickable, bounds in items:
            if need_clickable and not clickable:
                continue
            if matcher(text, desc):
                pt = center_of(bounds)
                if pt:
                    adb("shell", "input", "tap", str(pt[0]), str(pt[1]))
                    return True
    return False


def foreground_package() -> str:
    r = adb("shell", "dumpsys", "activity", "activities")
    m = re.search(r"mResumedActivity.*?\s([\w.]+)/", r.stdout)
    return m.group(1) if m else ""


def shot(out: Path, name: str) -> Path:
    path = out / f"{name}.png"
    adb("shell", "screencap", "-p", "/sdcard/shot.png")
    data = subprocess.run([str(ADB), "exec-out", "screencap", "-p"], capture_output=True).stdout
    path.write_bytes(data)
    return path


def slug(name: str) -> str:
    return re.sub(r"[^0-9a-zA-Z_-]+", "-", name)[:40]


def cmd_probe(no_install: bool) -> int:
    require_device()
    if not no_install:
        install(None)
    adb("shell", "am", "force-stop", PKG)
    adb("shell", "am", "start", "-n", ACTIVITY)
    time.sleep(2.5)
    xml = dump_xml()
    out = OUT_ROOT / time.strftime("probe-%Y%m%d-%H%M%S")
    out.mkdir(parents=True, exist_ok=True)
    shot(out, "launch")
    print(f"截图: {out}/launch.png")
    print("可点控件：")
    for text, desc, clickable, bounds in nodes(xml):
        if clickable:
            print(f"  - {text or desc!r} {bounds}")
    print("\n全部文本：")
    print(visible_texts(xml))
    return 0


def cmd_run(journey_path: Path, no_install: bool, keep_data: bool) -> int:
    require_device()
    if not no_install:
        install(None)
    spec = json.loads(journey_path.read_text(encoding="utf-8"))
    out = OUT_ROOT / time.strftime(f"{slug(spec.get('name', 'journey'))}-%Y%m%d-%H%M%S")
    out.mkdir(parents=True, exist_ok=True)

    adb("shell", "am", "force-stop", PKG)
    if not keep_data:
        adb("shell", "pm", "clear", PKG)
    adb("shell", "am", "start", "-n", ACTIVITY)
    time.sleep(2.5)

    results = []
    failed = 0
    for idx, step in enumerate(spec.get("steps") or [], 1):
        name = step.get("name") or f"step{idx}"
        xml = dump_xml()
        ok = True
        detail = []

        if step.get("back"):
            adb("shell", "input", "keyevent", "4")
            time.sleep(step.get("wait", 1.0))
            xml = dump_xml()
            detail.append("back")

        label = step.get("tap")
        if label:
            if tap_text(xml, label):
                detail.append(f"tap {label!r}")
                time.sleep(step.get("wait", 1.2))
                xml = dump_xml()
                fg = foreground_package()
                if fg and fg != PKG:
                    ok = False
                    detail.append(f"点击后离开了 App（前台={fg}）")
            else:
                ok = False
                detail.append(f"tap {label!r} 未找到")

        expected = step.get("expect") or []
        missing = [e for e in expected if e not in visible_texts(xml)]
        if missing:
            ok = False
            detail.append(f"缺少文案 {missing}")

        if step.get("shot", True):
            shot(out, f"{idx:02d}-{slug(name)}")
        if not ok:
            (out / f"{idx:02d}-{slug(name)}.xml").write_text(xml, encoding="utf-8")
            failed += 1

        results.append({"step": idx, "name": name, "ok": ok, "detail": detail})
        print(f"  {'OK ' if ok else 'FAIL'} {idx:02d} {name}  {'; '.join(detail)}")

    report = {"journey": spec.get("name"), "device": adb("devices").stdout.strip(),
              "failed": failed, "steps": results}
    (out / "report.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n",
                                     encoding="utf-8")
    print(f"\n结果: {'通过' if failed == 0 else f'{failed} 步失败'}  证据: {out}")
    return 0 if failed == 0 else 1


def main(argv: list[str]) -> int:
    if not argv or argv[0] in ("-h", "--help"):
        print(__doc__)
        return 0
    cmd = argv[0]
    no_install = "--no-install" in argv
    if cmd == "probe":
        return cmd_probe(no_install)
    if cmd == "run":
        if len(argv) < 2:
            print("用法: e2e_journey.py run <journey.json>", file=sys.stderr)
            return 2
        return cmd_run(Path(argv[1]), no_install, "--keep-data" in argv)
    print(f"未知命令: {cmd}", file=sys.stderr)
    return 2


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
