"""任务板不变量（去重与分组整理后固化）。

四条，都是这次整理暴露出来的真实问题：
  - 合并链不能断，也不能链式
  - 有效任务必须有 group_id，且组在 groups 索引里
  - 有效任务之间共享文件超过 50% 必须显式 overlaps_with
  - 审计脚本本身必须报告 0 个硬问题
"""
import json
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
BOARD = ROOT / ".ai" / "tasks.json"
ARCHIVE = ROOT / ".ai" / "tasks-archive.json"


def _board():
    return json.loads(BOARD.read_text(encoding="utf-8"))


def _archive():
    if not ARCHIVE.exists():
        return {"tasks": []}
    return json.loads(ARCHIVE.read_text(encoding="utf-8"))


def test_working_board_has_no_merged_or_archived():
    """工作板只留有效任务；重复与归档必须移出。"""
    d = _board()
    dirty = [t["id"] for t in d["tasks"] if t.get("merged_into") or t.get("archived")]
    assert dirty == [], f"工作板仍有重复/归档任务: {dirty}"


def test_merged_chain_is_shallow():
    everything = _board()["tasks"] + _archive()["tasks"]
    by_id = {t["id"]: t for t in everything}
    for t in everything:
        target = t.get("merged_into")
        if not target:
            continue
        assert target in by_id, f"{t['id']} → 不存在的 {target}"
        assert not by_id[target].get("merged_into"), f"{t['id']} → {target} 形成链"


def test_active_tasks_have_known_group():
    d = _board()
    groups = {g["id"] for g in d.get("groups", [])}
    for t in d["tasks"]:
        if t.get("merged_into") or t.get("archived"):
            continue
        assert t.get("group_id"), f"{t['id']} 缺 group_id"
        assert t["group_id"] in groups, f"{t['id']} 的组 {t['group_id']} 不在索引"


def test_shared_files_are_declared():
    d = _board()
    by_id = {t["id"]: t for t in d["tasks"]}
    active = [t for t in d["tasks"] if not t.get("merged_into") and not t.get("archived")]
    for i, a in enumerate(active):
        for b in active[i + 1:]:
            fa = set(a.get("target_files") or [])
            fb = set(b.get("target_files") or [])
            if not fa or not fb:
                continue
            jac = len(fa & fb) / len(fa | fb)
            if jac < 0.5:
                continue
            declared = b["id"] in (a.get("overlaps_with") or []) or a["id"] in (b.get("overlaps_with") or [])
            assert declared, f"{a['id']} 与 {b['id']} 共享 {jac:.0%} 文件但未声明 overlaps_with"


def test_audit_reports_no_hard_problem():
    run = subprocess.run(
        ["bash", "scripts/workflow/wf-board-audit.sh", "--json"],
        cwd=ROOT, capture_output=True, text=True,
    )
    data = json.loads(run.stdout)
    assert data["errors"] == [], data["errors"]
    assert data["ok"] is True
