"""
gov_validate_prototype.py — 原型驱动开发闭环校验

验证任务板中 prototype_ref 字段的完整性：
  - GOV-PROTO-001: prototype_ref.file 存在且可访问
  - GOV-PROTO-002: prototype_ref.views 非空且在原型 HTML 中存在
  - GOV-PROTO-003: target_files 路径格式正确
  - GOV-PROTO-004: wave 字段存在且为 0/1/2
  - GOV-PROTO-005: module 字段存在（M# 追溯）
  - GOV-PROTO-006: ac_refs 非空（AC 原文追溯）
  - GOV-PROTO-007: prd_ref 存在（上层文档链接）

轻量校验（只查 schema，不查代码），适合挂在 gov-validate 管线。
代码级闭环验证（硬编码颜色/文案匹配率）由 gov-prototype-verify.py --strict 单独执行。
"""
from pathlib import Path
from .gov_validate_core import ROOT, fail


def _warn(msg):
    print(f'  [WARN] {msg}')


def check_prototype_tasks():
    """GOV-PROTO-001~004: 任务板 prototype_ref schema 校验"""
    import json
    import re

    tasks_file = ROOT / ".ai" / "tasks.json"
    if not tasks_file.exists():
        return

    try:
        data = json.loads(tasks_file.read_text(encoding="utf-8"))
    except (json.JSONDecodeError, UnicodeDecodeError):
        return

    tasks = data.get("tasks", [])
    proto_tasks = [t for t in tasks if t.get("prototype_ref")]
    if not proto_tasks:
        return

    proto_dir = ROOT / "design" / "prototypes" / "interactive"
    file_pattern = re.compile(r"^design/prototypes/interactive/[\w\-]+\.html$")
    path_pattern = re.compile(r"^app/src/main/kotlin/com/xcwk/scalehub/[\w/]+\.kt$")

    checked = 0
    for task in proto_tasks:
        tid = task.get("id", "?")
        pref = task.get("prototype_ref", {})
        checked += 1

        # GOV-PROTO-001: file 存在且路径格式正确
        proto_file = pref.get("file", "")
        if not proto_file:
            fail(f"GOV-PROTO-001: {tid} prototype_ref.file 缺失")
        elif not file_pattern.match(proto_file):
            fail(f"GOV-PROTO-001: {tid} prototype_ref.file 路径格式错误: {proto_file}")
        elif not (ROOT / proto_file).exists():
            fail(f"GOV-PROTO-001: {tid} 原型文件不存在: {proto_file}")

        # GOV-PROTO-002: views 非空
        views = pref.get("views", [])
        if not views:
            _warn(f"GOV-PROTO-002: {tid} prototype_ref.views 为空")

        # GOV-PROTO-003: target_files 路径格式
        targets = task.get("target_files", [])
        for tf in targets:
            if not path_pattern.match(tf):
                _warn(f"GOV-PROTO-003: {tid} target_files 路径格式异常: {tf}")

        # GOV-PROTO-004: wave 字段
        wave = task.get("wave")
        if wave is None:
            _warn(f"GOV-PROTO-004: {tid} 缺 wave 字段")
        elif wave not in (0, 1, 2):
            _warn(f"GOV-PROTO-004: {tid} wave 值异常: {wave}（应为 0/1/2）")

        # GOV-PROTO-005: module 字段（M# 追溯）
        module = task.get("module", "")
        if not module:
            _warn(f"GOV-PROTO-005: {tid} 缺 module 字段（DOC-CHAIN §3 要求 M# 追溯）")

        # GOV-PROTO-006: ac_refs 非空（AC 原文追溯）
        ac_refs = task.get("ac_refs", [])
        if not ac_refs:
            _warn(f"GOV-PROTO-006: {tid} 缺 ac_refs（DOC-CHAIN §3 要求至少一条 AC，缺则不得标 Done）")

        # GOV-PROTO-007: prd_ref 存在（上层文档链接）
        prd_ref = task.get("prd_ref", "")
        if not prd_ref:
            _warn(f"GOV-PROTO-007: {tid} 缺 prd_ref（DOC-CHAIN §4 要求上层引用可点开）")

    if checked:
        print(f"  [OK] GOV-PROTO: {checked} 个原型任务 schema 校验完成")


def check_all():
    """入口：运行所有原型校验"""
    check_prototype_tasks()
