"""任务板不变量（去重与分组整理后固化）。

四条，都是这次整理暴露出来的真实问题：
  - 合并链不能断，也不能链式
  - 有效任务必须有 group_id，且组在 groups 索引里
  - 有效任务之间共享文件超过 50% 必须显式 overlaps_with
  - 审计脚本本身必须报告 0 个硬问题

所有测试使用 tmp_path 沙箱副本，避免依赖真实 .ai/tasks.json 的状态。
"""
import json
import os
import shutil
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
REAL_AI = ROOT / ".ai"
AUDIT_SCRIPT = ROOT / "scripts" / "workflow" / "wf-board-audit.sh"


def _sandbox(tmp_path):
    """Copy real .ai/ to tmp_path/.ai/ for full isolation."""
    ai_dir = tmp_path / ".ai"
    ai_dir.mkdir(parents=True, exist_ok=True)
    shutil.copy(REAL_AI / "tasks.json", ai_dir / "tasks.json")
    archive = REAL_AI / "tasks-archive.json"
    if archive.exists():
        shutil.copy(archive, ai_dir / "tasks-archive.json")
    return tmp_path


def _board(sandbox):
    return json.loads((sandbox / ".ai" / "tasks.json").read_text(encoding="utf-8"))


def _archive(sandbox):
    archive = sandbox / ".ai" / "tasks-archive.json"
    if not archive.exists():
        return {"tasks": []}
    return json.loads(archive.read_text(encoding="utf-8"))


def test_working_board_has_no_merged_or_archived(tmp_path):
    """工作板只留有效任务；重复与归档必须移出。"""
    sandbox = _sandbox(tmp_path)
    d = _board(sandbox)
    dirty = [t["id"] for t in d["tasks"] if t.get("merged_into") or t.get("archived")]
    assert dirty == [], f"工作板仍有重复/归档任务: {dirty}"


def test_merged_chain_is_shallow(tmp_path):
    sandbox = _sandbox(tmp_path)
    everything = _board(sandbox)["tasks"] + _archive(sandbox)["tasks"]
    by_id = {t["id"]: t for t in everything}
    for t in everything:
        target = t.get("merged_into")
        if not target:
            continue
        assert target in by_id, f"{t['id']} → 不存在的 {target}"
        assert not by_id[target].get("merged_into"), f"{t['id']} → {target} 形成链"


def test_active_tasks_have_known_group(tmp_path):
    sandbox = _sandbox(tmp_path)
    d = _board(sandbox)
    groups = {g["id"] for g in d.get("groups", [])}
    for t in d["tasks"]:
        if t.get("merged_into") or t.get("archived"):
            continue
        assert t.get("group_id"), f"{t['id']} 缺 group_id"
        assert t["group_id"] in groups, f"{t['id']} 的组 {t['group_id']} 不在索引"


def test_shared_files_are_declared(tmp_path):
    sandbox = _sandbox(tmp_path)
    d = _board(sandbox)
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


def test_audit_reports_no_hard_problem(tmp_path):
    """审计脚本在沙箱副本上报告 0 个硬问题。"""
    sandbox = _sandbox(tmp_path)
    env = {**os.environ, "WF_BOARD_ROOT": str(sandbox)}
    run = subprocess.run(
        ["bash", str(AUDIT_SCRIPT), "--json"],
        cwd=sandbox, capture_output=True, text=True, env=env,
    )
    data = json.loads(run.stdout)
    assert data["errors"] == [], data["errors"]
    assert data["ok"] is True
