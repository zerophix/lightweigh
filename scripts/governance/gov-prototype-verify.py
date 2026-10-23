#!/usr/bin/env python3
"""
gov-prototype-verify.py — 原型→任务→代码 闭环验证

验证链路：
  原型 HTML (design/prototypes/interactive/*.html)
    → 任务 (tasks.json 的 prototype_ref 字段)
      → 代码 (target_files)
        → 反向检查：代码是否真的用了原型的文案/结构

用法：
  python3 scripts/governance/gov-prototype-verify.py <task-id> [--strict]
  python3 scripts/governance/gov-prototype-verify.py --all [--strict]
  python3 scripts/governance/gov-prototype-verify.py --schema   # 只校验 schema

--strict: 缺 target_files 或代码不匹配时返回非零（用于门禁）
默认模式：只报告，不失败（用于检查）
"""
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent.parent
TASKS_FILE = ROOT / ".ai" / "tasks.json"
PROTOTYPE_DIR = ROOT / "design" / "prototypes" / "interactive"

# 硬编码颜色检测（Compose 里禁止 Color(0x...)，必须用 QkColor）
HARDCODED_COLOR = re.compile(r"Color\(0x[0-9A-Fa-f]{6,8}\)")
# 硬编码 dp 检测（禁止裸 14.dp 等，必须用 QkSpacing）
HARDCODED_DP = re.compile(r"(?<!Qk)\b\d+\.dp\b")


def load_tasks():
    with open(TASKS_FILE, encoding="utf-8") as f:
        return json.load(f)["tasks"]


def find_task(tasks, task_id):
    for t in tasks:
        if t["id"] == task_id:
            return t
    return None


def extract_views_from_html(html_path):
    """从原型 HTML 提取所有 view id 及其文本内容。"""
    if not html_path.exists():
        return {}
    text = html_path.read_text(encoding="utf-8")
    views = {}
    # 匹配 <div class="view" id="v-xxx"> ... </div> (简化：按 id 切分)
    pattern = re.compile(r'id="(v-[^"]+)"')
    ids = pattern.findall(text)
    # 提取每个 view 块的文本（粗略：从 id 位置到下一个 view id 或文件尾）
    positions = [(m.start(), m.group(1)) for m in pattern.finditer(text)]
    for i, (pos, vid) in enumerate(positions):
        end = positions[i + 1][0] if i + 1 < len(positions) else len(text)
        block = text[pos:end]
        # 提取中文文案（去掉 HTML 标签）
        clean = re.sub(r"<[^>]+>", " ", block)
        # 提取中文短语（2字以上）
        zh_phrases = re.findall(r"[一-鿿]{2,}", clean)
        views[vid] = {"phrases": list(set(zh_phrases)), "raw_len": len(block)}
    return views


def check_schema(task):
    """校验任务的 prototype_ref schema。返回 (errors, warnings)。"""
    errors = []
    warnings = []
    pref = task.get("prototype_ref")
    if not pref:
        errors.append(f"{task['id']}: 缺 prototype_ref 字段")
        return errors, warnings

    # file 字段
    proto_file = pref.get("file", "")
    if not proto_file:
        errors.append(f"{task['id']}: prototype_ref.file 缺失")
    else:
        proto_path = ROOT / proto_file
        if not proto_path.exists():
            errors.append(f"{task['id']}: 原型文件不存在 {proto_file}")

    # views 字段
    views = pref.get("views", [])
    if not views:
        warnings.append(f"{task['id']}: prototype_ref.views 为空")

    # target_files 字段
    targets = task.get("target_files", [])
    if not targets:
        warnings.append(f"{task['id']}: target_files 为空（Wave 1 应有目标文件）")

    # wave 字段
    wave = task.get("wave")
    if wave is None:
        warnings.append(f"{task['id']}: 缺 wave 字段")

    # module 字段（DOC-CHAIN §3 M# 追溯）
    module = task.get("module", "")
    if not module:
        warnings.append(f"{task['id']}: 缺 module 字段（DOC-CHAIN §3 要求 M# 追溯）")

    # ac_refs 字段（DOC-CHAIN §3 AC 原文追溯）
    ac_refs = task.get("ac_refs", [])
    if not ac_refs:
        warnings.append(f"{task['id']}: 缺 ac_refs（DOC-CHAIN §3 要求至少一条 AC）")

    # prd_ref 字段（DOC-CHAIN §4 上层文档链接）
    prd_ref = task.get("prd_ref", "")
    if not prd_ref:
        warnings.append(f"{task['id']}: 缺 prd_ref（DOC-CHAIN §4 要求上层引用）")

    return errors, warnings


def verify_task(task, strict=False):
    """验证单个任务的完整闭环。返回 (errors, warnings, info)。"""
    errors, warnings = check_schema(task)
    info = []

    pref = task.get("prototype_ref", {})
    proto_file = pref.get("file", "")
    views = pref.get("views", [])
    targets = task.get("target_files", [])

    if not proto_file:
        return errors, warnings, info

    proto_path = ROOT / proto_file
    if not proto_path.exists():
        return errors, warnings, info

    # 提取原型 view 内容
    all_views = extract_views_from_html(proto_path)

    # 检查 views 是否存在于原型
    for v in views:
        if v not in all_views:
            errors.append(f"{task['id']}: 原型 {proto_file} 中无 view {v}")
        else:
            info.append(f"{task['id']}: view {v} 存在（{all_views[v]['raw_len']} 字符）")

    # 检查 target_files
    existing_targets = []
    for tf in targets:
        tf_path = ROOT / tf
        if tf_path.exists():
            existing_targets.append(tf_path)
            info.append(f"{task['id']}: 目标文件存在 {tf}")
        else:
            if strict:
                warnings.append(f"{task['id']}: 目标文件不存在 {tf}（可能未实现）")
            else:
                info.append(f"{task['id']}: 目标文件待建 {tf}")

    # 如果目标文件存在，检查代码质量
    for tf_path in existing_targets:
        code = tf_path.read_text(encoding="utf-8")

        # 检查硬编码颜色
        color_matches = HARDCODED_COLOR.findall(code)
        if color_matches:
            errors.append(
                f"{task['id']}: {tf_path.name} 硬编码颜色 {color_matches[:3]}，"
                f"必须用 QkColor token"
            )

        # 检查是否用了 Qk 组件（至少 import 了 Qk*）
        if "Qk" not in code and "@Composable" in code:
            warnings.append(
                f"{task['id']}: {tf_path.name} 未引用任何 Qk* 组件，"
                f"检查是否复用了设计系统"
            )

        # 反向检查：原型文案是否出现在代码中
        all_phrases = set()
        for v in views:
            if v in all_views:
                all_phrases.update(all_views[v]["phrases"])

        if all_phrases and existing_targets:
            # 检查至少 30% 的原型文案出现在代码中
            code_has = sum(1 for p in all_phrases if p in code)
            ratio = code_has / len(all_phrases) if all_phrases else 0
            if ratio < 0.3 and all_phrases:
                warnings.append(
                    f"{task['id']}: {tf_path.name} 原型文案匹配率 "
                    f"{ratio:.0%}（{code_has}/{len(all_phrases)}），"
                    f"可能未参考原型"
                )
            else:
                info.append(
                    f"{task['id']}: {tf_path.name} 原型文案匹配率 {ratio:.0%}"
                )

    return errors, warnings, info


def main():
    args = sys.argv[1:]
    strict = "--strict" in args
    schema_only = "--schema" in args
    all_mode = "--all" in args
    args = [a for a in args if not a.startswith("--")]

    tasks = load_tasks()

    if schema_only:
        # 只校验所有有 prototype_ref 的任务的 schema
        all_errors = []
        all_warnings = []
        for t in tasks:
            if t.get("prototype_ref"):
                e, w = check_schema(t)
                all_errors.extend(e)
                all_warnings.extend(w)
        for w in all_warnings:
            print(f"  [WARN] {w}")
        for e in all_errors:
            print(f"  [ERROR] {e}")
        print(f"\n  schema 校验: {len(all_errors)} 错误, {len(all_warnings)} 警告")
        return 1 if all_errors else 0

    if all_mode:
        # 验证所有有 prototype_ref 的任务
        total_errors = []
        total_warnings = []
        verified = 0
        for t in tasks:
            if not t.get("prototype_ref"):
                continue
            e, w, info = verify_task(t, strict=strict)
            total_errors.extend(e)
            total_warnings.extend(w)
            verified += 1
            for i in info:
                print(f"  [INFO] {i}")
        for w in total_warnings:
            print(f"  [WARN] {w}")
        for e in total_errors:
            print(f"  [ERROR] {e}")
        print(f"\n  闭环验证: {verified} 任务, "
              f"{len(total_errors)} 错误, {len(total_warnings)} 警告")
        return 1 if (strict and total_errors) else 0

    if not args:
        print("用法: gov-prototype-verify.py <task-id> | --all | --schema [--strict]")
        return 1

    task_id = args[0]
    task = find_task(tasks, task_id)
    if not task:
        print(f"  [ERROR] 任务 {task_id} 不存在")
        return 1

    errors, warnings, info = verify_task(task, strict=strict)
    for i in info:
        print(f"  [INFO] {i}")
    for w in warnings:
        print(f"  [WARN] {w}")
    for e in errors:
        print(f"  [ERROR] {e}")

    if errors:
        print(f"\n  {task_id}: 闭环验证失败（{len(errors)} 错误）")
        return 1
    print(f"\n  {task_id}: 闭环验证通过")
    return 0


if __name__ == "__main__":
    sys.exit(main())
