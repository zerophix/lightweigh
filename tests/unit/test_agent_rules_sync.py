"""AI 规则单一真源（DEC-076）不变量测试。

三家工具读三个不同文件，过去各写一份并已漂移（曾残留"反思≥50字"这种
早已退役的硬约束）。这里钉死：三个文件必须由同一个生成器产出，且内容
必须包含角色/画像/硬规则三个入口。
"""
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SYNC = ROOT / "scripts" / "governance" / "gov-sync-agent-rules.sh"
TARGETS = [ROOT / ".cursorrules", ROOT / ".clinerules", ROOT / ".opencode" / "rules.md"]


def test_generator_check_passes():
    run = subprocess.run(["bash", str(SYNC), "--check"], cwd=ROOT,
                         capture_output=True, text=True)
    assert run.returncode == 0, run.stdout + run.stderr


def test_all_rule_files_are_generated_pointers():
    for path in TARGETS:
        text = path.read_text(encoding="utf-8")
        assert "生成物，勿手改" in text, path
        assert "AGENTS.md" in text, path
        assert "wf-role.sh claim" in text, path
        assert "wf-autopilot.sh status" in text, path


def test_no_stale_hard_rules_in_generated_files():
    """退役规则不得回流到蒸馏文件（历史上复发过一次）。"""
    for path in TARGETS:
        text = path.read_text(encoding="utf-8")
        assert "50 字" not in text, path
        assert "不写无法提交" not in text, path


def test_pre_commit_requires_role_for_code_and_scripts():
    hook = (ROOT / "scripts" / "governance" / "gov-pre-commit-simplified").read_text(encoding="utf-8")
    assert "GOV_ROLE_REQUIRED" in hook
    assert "wf-role.sh claim dev" in hook


def test_session_begin_shows_compliance_card():
    script = (ROOT / "scripts" / "session" / "ses-session-begin.sh").read_text(encoding="utf-8")
    assert "合规卡" in script
    assert "wf-role.sh claim dev|gov|docs|review" in script
