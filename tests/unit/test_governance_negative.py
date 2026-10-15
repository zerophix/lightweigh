#!/usr/bin/env python3
"""GOV 规则负向测试: 故意制造违规, 断言对应校验规则确实触发 FAIL。

目的: 证明校验规则真实生效（治理设计约束 Q3 可验证性）。
没有负向测试的规则属于 hope-ware —— 规则被静默删除时无人会发现。
运行: 由 gov-validate.sh 的 GOV-TEST-005 自动执行, 或手动 pytest tests/unit/。
"""
import contextlib
import io
import sys
from datetime import datetime, timedelta, timezone
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parents[2]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from scripts.governance.modules import gov_validate_core as core
from scripts.governance.modules import gov_validate_structure as structure


def _clean_errors_guard():
    """快照-恢复语义 (DEC-007): 只隔离本测试产生的错误, 不吞宿主进程累积的现场。

    进程内 pytest.main (gov_validate_tests.check_pytest) 场景下, validate 主进程
    在 pytest 阶段之前收集的违规必须在测试结束后幸存, 否则摘要计数被清零。
    独立运行时快照为空, 行为等价于清空隔离。
    """
    saved = list(core.errors)
    core.errors.clear()
    yield
    core.errors.clear()
    core.errors.extend(saved)


@pytest.fixture(autouse=True)
def _clean_errors():
    yield from _clean_errors_guard()


def _walk(tmp_path: Path, monkeypatch) -> list:
    """在临时目录上运行结构校验, 返回错误列表（不触碰真实仓库文件）。"""
    monkeypatch.setattr(structure, "ROOT", tmp_path)
    buf = io.StringIO()
    with contextlib.redirect_stdout(buf):
        structure.walk_all()
    return list(core.errors)


def _has(errors, rule_id: str) -> bool:
    return any(rule_id in e for e in errors)


def test_clean_tree_produces_no_errors(tmp_path, monkeypatch):
    """空白对照: 干净目录不应触发任何规则（防误报）。"""
    assert _walk(tmp_path, monkeypatch) == []


def test_struct001_non_ascii_filename(tmp_path, monkeypatch):
    """GOV-STRUCT-001: 非 ASCII 文件名必须 FAIL。"""
    (tmp_path / "docs").mkdir()
    (tmp_path / "docs" / "文件名.md").write_text("# 标题", encoding="utf-8")
    errors = _walk(tmp_path, monkeypatch)
    assert _has(errors, "GOV-STRUCT-001"), errors


def test_struct002_abandoned_path(tmp_path, monkeypatch):
    """GOV-STRUCT-002: old/legacy/vN 遗弃路径必须 FAIL。"""
    (tmp_path / "old").mkdir()
    (tmp_path / "old" / "stray.md").write_text("# 标题", encoding="utf-8")
    errors = _walk(tmp_path, monkeypatch)
    assert _has(errors, "GOV-STRUCT-002"), errors


def test_struct003_rogue_markdown(tmp_path, monkeypatch):
    """GOV-STRUCT-003: docs/ 之外的游离 Markdown 必须 FAIL。"""
    (tmp_path / "STRAY.md").write_text("# 标题", encoding="utf-8")
    errors = _walk(tmp_path, monkeypatch)
    assert _has(errors, "GOV-STRUCT-003"), errors


def test_struct004_disallowed_doc(tmp_path, monkeypatch):
    """GOV-STRUCT-004: 不在允许清单内的 docs 文件必须 FAIL。"""
    (tmp_path / "docs").mkdir()
    (tmp_path / "docs" / "random.md").write_text("# 标题", encoding="utf-8")
    errors = _walk(tmp_path, monkeypatch)
    assert _has(errors, "GOV-STRUCT-004"), errors


def test_struct006_missing_source_annotation(tmp_path, monkeypatch):
    """GOV-STRUCT-006: 黄金标准缺少来源标注必须 FAIL。"""
    guides = tmp_path / "docs" / "guides"
    guides.mkdir(parents=True)
    body = "# 指南\n\n" + "\n\n".join(
        f"第{i}段正文内容用于深度检查。" for i in range(1, 6)
    )
    (guides / "index.md").write_text(body, encoding="utf-8")
    errors = _walk(tmp_path, monkeypatch)
    assert _has(errors, "GOV-STRUCT-006"), errors


def _proto_check(tmp_path: Path, monkeypatch) -> list:
    """在临时目录上运行协议校验, 返回错误列表（不触碰真实仓库文件）。"""
    from scripts.governance.modules import gov_validate_protocol as protocol
    monkeypatch.setattr(protocol, "DOCS", tmp_path / "docs")
    monkeypatch.setattr(protocol, "ROOT", tmp_path)
    monkeypatch.setattr(protocol, "MEMORY_FILE", tmp_path / "nope.jsonl")
    buf = io.StringIO()
    with contextlib.redirect_stdout(buf):
        protocol.check_protocol()
    return list(core.errors)


def _write_inbox(tmp_path: Path, name: str, status: str, timestamp: str) -> None:
    inbox = tmp_path / "docs" / "ai" / "inbox"
    inbox.mkdir(parents=True, exist_ok=True)
    (inbox / name).write_text(
        f"---\nstatus: {status}\ntimestamp: {timestamp}\n---\n\n# 接力信\n",
        encoding="utf-8",
    )


def test_proto001_fresh_unread_not_flagged(tmp_path, monkeypatch):
    """GOV-PROTO-001 回归: 刚写入的未读接力信不算违规（48h 语义, 防误报）。"""
    now = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    _write_inbox(tmp_path, "20261014-000000.md", "unread", now)
    assert _proto_check(tmp_path, monkeypatch) == []


def test_proto001_stale_unread_fails(tmp_path, monkeypatch):
    """GOV-PROTO-001: 超过 48 小时的未读留言必须 FAIL。"""
    stale = (datetime.now(timezone.utc) - timedelta(hours=72)).strftime(
        "%Y-%m-%dT%H:%M:%SZ")
    _write_inbox(tmp_path, "20261001-000000.md", "unread", stale)
    errors = _proto_check(tmp_path, monkeypatch)
    assert _has(errors, "GOV-PROTO-001"), errors


def test_proto001_stale_but_read_ok(tmp_path, monkeypatch):
    """GOV-PROTO-001: 超 48h 但已读的留言不算违规。"""
    stale = (datetime.now(timezone.utc) - timedelta(hours=72)).strftime(
        "%Y-%m-%dT%H:%M:%SZ")
    _write_inbox(tmp_path, "20261001-000000.md", "read", stale)
    assert _proto_check(tmp_path, monkeypatch) == []


def _r0_check(tmp_path: Path, monkeypatch) -> list:
    """在临时 git 仓库上运行 R0 检查, 仅返回反思相关错误。"""
    from scripts.governance.modules import gov_validate_status as status
    # 提供 handoff 记录, 避免 R0 的 handoff 新鲜度检查干扰本组断言
    mem = tmp_path / "context.jsonl"
    mem.write_text(
        '{"type": "session-start", "ts": "2026-10-14T00:00:00Z", "ai": "t"}\n'
        '{"type": "handoff", "ts": "2026-10-14T01:00:00Z", "ai": "t", '
        '"lessons": "教训"}\n', encoding="utf-8")
    monkeypatch.setattr(status, "ROOT", tmp_path)
    monkeypatch.setattr(status, "MEMORY_FILE", mem)
    buf = io.StringIO()
    with contextlib.redirect_stdout(buf):
        status.check_status()
    return [e for e in core.errors if "反思" in e]


def _git_commit(tmp_path: Path, msg: str, refl: str) -> None:
    """写入带指定反思的 README 并提交（隔离的临时 git 仓库）。"""
    import subprocess
    (tmp_path / "README.md").write_text(
        f"## 当前状态\n\n### 反思\n\n{refl}\n", encoding="utf-8")
    # 同时改动一个附属文件, 保证反思复用场景下提交仍非空
    (tmp_path / "other.txt").write_text(msg, encoding="utf-8")
    subprocess.run(["git", "-C", str(tmp_path), "add", "-A"], check=True,
                   capture_output=True)
    subprocess.run(
        ["git", "-C", str(tmp_path), "-c", "user.name=t", "-c",
         "user.email=t@t", "commit", "-m", msg], check=True, capture_output=True)


def _git_init(tmp_path: Path) -> None:
    import subprocess
    subprocess.run(["git", "-C", str(tmp_path), "init", "-q"], check=True,
                   capture_output=True)


def test_r0_clean_tree_changed_reflection_passes(tmp_path, monkeypatch):
    """R0 回归(DEC-004): 干净树上最近提交已刷新反思, 不得误报。"""
    _git_init(tmp_path)
    _git_commit(tmp_path, "c1",
                "第一次教训：教训是不要把会随规则增减变化的数字写死在多个文件里，"
                "数字在几处复制就一定会在几处漂移，只能指向单一事实源。")
    _git_commit(tmp_path, "c2",
                "第二次教训：另一个坑是校验规则的比对基线选错了时机，固定比对工作区"
                "与 HEAD 导致干净树上全部误报，基线必须随校验时机切换。")
    assert _r0_check(tmp_path, monkeypatch) == []


def test_r0_clean_tree_reused_reflection_fails(tmp_path, monkeypatch):
    """R0(DEC-004): 干净树上最近提交复用旧反思必须 FAIL。"""
    _git_init(tmp_path)
    _git_commit(tmp_path, "c1",
                "第一次教训：教训是不要把会随规则增减变化的数字写死在多个文件里，"
                "数字在几处复制就一定会在几处漂移，只能指向单一事实源。")
    _git_commit(tmp_path, "c2",
                "第一次教训：教训是不要把会随规则增减变化的数字写死在多个文件里，"
                "数字在几处复制就一定会在几处漂移，只能指向单一事实源。")
    errors = _r0_check(tmp_path, monkeypatch)
    assert any("上一次提交完全相同" in e for e in errors), errors


def test_validate_output_contract_has_summary_line():
    """DEC-005: 校验输出恒定含「违规: N 项」摘要行。

    pre-push 与 gov-update-metrics.sh 消费该契约（旧缺陷: 成功路径只打印
    「全部通过」, 摘要行缺失导致 pre-push 永远拒绝、违规指标恒为 ?）。
    """
    import os
    import re
    import subprocess
    proc = subprocess.run(
        [sys.executable, str(ROOT / "scripts/governance/gov-validate.py")],
        capture_output=True, text=True, timeout=300,
        env={**os.environ, "GOV_VALIDATE_QUICK": "1"})
    out = proc.stdout + proc.stderr
    assert re.search(r"违规: \d+ 项", out), f"摘要行缺失:\n{out[-2000:]}"
    if proc.returncode == 0:
        assert "违规: 0" in out, f"零违规时应输出「违规: 0」:\n{out[-2000:]}"


def _readme_check(tmp_path: Path, monkeypatch, body: str) -> list:
    """在临时目录上运行 GOV-README 结构检查, 返回该族错误。"""
    from scripts.governance.modules import gov_validate_status as status
    (tmp_path / "README.md").write_text(body, encoding="utf-8")
    monkeypatch.setattr(status, "ROOT", tmp_path)
    buf = io.StringIO()
    with contextlib.redirect_stdout(buf):
        status.check_readme_structure()
    return [e for e in core.errors if "GOV-README" in e]


_COMPLIANT = """# Demo — 演示项目

## 快速开始
python main.py

## 目录结构
src/

## 核心功能
feature-a

## 相关文档
docs/

## 当前状态

### 反思
教训是规范必须配上校验, 否则任何后来的 AI 都会把结构写漂。
"""


def test_readme001_missing_h1(tmp_path, monkeypatch):
    """GOV-README-001: 首个非空行不是 H1 必须 FAIL。"""
    errors = _readme_check(tmp_path, monkeypatch,
                           "## 项目\n\n正文从二级标题开始。\n")
    assert any("GOV-README-001" in e for e in errors), errors


def test_readme001_duplicate_h1(tmp_path, monkeypatch):
    """GOV-README-001: 全文多个 H1 必须 FAIL。"""
    body = "# 主标题\n\n正文\n\n# 又一个一级标题\n"
    errors = _readme_check(tmp_path, monkeypatch, body)
    assert any("GOV-README-001" in e for e in errors), errors


def test_readme002_status_not_last(tmp_path, monkeypatch):
    """GOV-README-002: 「当前状态」之后仍有二级章节必须 FAIL。"""
    body = _COMPLIANT + "\n## AI 协作\n\n接力规则。\n"
    errors = _readme_check(tmp_path, monkeypatch, body)
    assert any("GOV-README-002" in e for e in errors), errors


def test_readme003_reflection_not_first(tmp_path, monkeypatch):
    """GOV-README-003: 「### 反思」不在状态节首位必须 FAIL。"""
    body = _COMPLIANT.replace(
        "## 当前状态\n\n### 反思",
        "## 当前状态\n\n### 项目快照\n\n快照数据\n\n### 反思")
    errors = _readme_check(tmp_path, monkeypatch, body)
    assert any("GOV-README-003" in e for e in errors), errors


def test_readme004_quickstart_after_architecture(tmp_path, monkeypatch):
    """GOV-README-004: 「快速开始」排在架构之后必须 FAIL。"""
    body = """# Demo — 演示项目

## 架构
分层说明。

## 快速开始
python main.py

## 当前状态

### 反思
教训是顺序规则必须用负向测试钉住。
"""
    errors = _readme_check(tmp_path, monkeypatch, body)
    assert any("GOV-README-004" in e for e in errors), errors


def test_readme_structure_compliant_passes(tmp_path, monkeypatch):
    """正向: 完全符合 GC-readme-structure 机读层的 README 零错误。"""
    errors = _readme_check(tmp_path, monkeypatch, _COMPLIANT)
    assert errors == [], errors


def test_readme001_ignores_code_fence_comments(tmp_path, monkeypatch):
    """GOV-README-001: 代码围栏内的 # 注释不计为 H1（DEC-006 执行中发现）。"""
    body = """# Demo — 演示项目

## 快速开始

```bash
# 1. 安装依赖
# 2. 启动服务
```

## 当前状态

### 反思
教训是标题识别必须跳过代码围栏, 否则 shell 注释会被当成标题。
"""
    errors = _readme_check(tmp_path, monkeypatch, body)
    assert errors == [], errors


def test_clean_errors_fixture_preserves_outer_errors():
    """DEC-007: 夹具只清理本测试的错误, 不吞宿主进程已累积的现场错误。

    进程内 pytest.main 场景下, validate 主进程在 pytest 阶段之前收集的
    违规必须幸存, 否则全量校验把 FAIL 打印成违规: 0。
    """
    outer = "DEC-007 outer sentinel"
    core.errors.append(outer)
    gen = _clean_errors_guard()
    next(gen)  # 进入测试阶段
    core.errors.append("inner test error")
    with pytest.raises(StopIteration):
        next(gen)  # 测试结束: 内部错误清理, 外部现场恢复
    assert outer in core.errors, "夹具吞掉了宿主进程累积的错误"
    core.errors.remove(outer)


_R0_README = (
    "## 当前状态\n\n### 反思\n\n"
    "教训是任何校验规则都必须在干净检出上真实跑通过一次, "
    "否则它在本地全绿而 CI 永久红灯, 规则越多这种暗坑越多。\n")


def _r0_env_check(tmp_path: Path, monkeypatch, memory=None, ci: bool = False) -> list:
    """运行 R0 的 handoff/hooks 检查（DEC-008 分流语义）, 返回相关错误。"""
    from scripts.governance.modules import gov_validate_status as status
    (tmp_path / "README.md").write_text(_R0_README, encoding="utf-8")
    monkeypatch.setattr(status, "ROOT", tmp_path)
    monkeypatch.setattr(status, "MEMORY_FILE", tmp_path / "context.jsonl")
    if memory is not None:
        (tmp_path / "context.jsonl").write_text(memory, encoding="utf-8")
    if ci:
        monkeypatch.setenv("CI", "true")
    else:
        monkeypatch.delenv("CI", raising=False)
    buf = io.StringIO()
    with contextlib.redirect_stdout(buf):
        status.check_status()
    return [e for e in core.errors if "handoff" in e or "hooks" in e]


def test_r0_missing_memory_file_skips_handoff(tmp_path, monkeypatch):
    """DEC-008 组A: 记忆文件缺失（干净 checkout）跳过 handoff 检查。"""
    errors = _r0_env_check(tmp_path, monkeypatch, memory=None, ci=True)
    assert errors == [], errors


def test_r0_memory_without_handoff_fails(tmp_path, monkeypatch):
    """DEC-008 组A: 文件存在但无 handoff 记录必须仍 FAIL。"""
    memory = '{"type": "session-start", "ts": "2026-10-14T00:00:00Z", "ai": "t"}\n'
    errors = _r0_env_check(tmp_path, monkeypatch, memory=memory, ci=True)
    assert any("未找到 handoff" in e for e in errors), errors


def test_r0_hooks_check_fails_outside_ci(tmp_path, monkeypatch):
    """DEC-008 组B: 非 CI 环境 hooks 缺失必须 FAIL（本机 onboarding 强制）。"""
    errors = _r0_env_check(tmp_path, monkeypatch, memory=None, ci=False)
    assert any("Git hooks 未安装" in e for e in errors), errors


def test_r0_hooks_check_skipped_in_ci(tmp_path, monkeypatch):
    """DEC-008 组B: CI 环境跳过 hooks 检查（本机状态不随 clone 走）。"""
    errors = _r0_env_check(tmp_path, monkeypatch, memory=None, ci=True)
    assert not any("Git hooks" in e for e in errors), errors


def test_session_end_handoff_call_covers_required_fields():
    """DEC-009: session-end 的 save handoff 调用必须覆盖缓存脚本全部必填字段。

    真实事故: 漏传必填字段 to 被 save 拒写, 错误又被 2>/dev/null + || true
    双重吞掉, session-end 永远无法刷新它自己负责的 PROTO-002 时效时钟。
    """
    import re
    end_sh = (ROOT / "scripts/session/ses-session-end.sh").read_text(encoding="utf-8")
    cache_sh = (ROOT / "scripts/session/ses-context-cache.sh").read_text(encoding="utf-8")
    m = re.search(r'REQUIRED_FIELDS_handoff="([^"]+)"', cache_sh)
    assert m, "找不到 handoff 必填字段声明"
    required = m.group(1).split()
    block_m = re.search(r"save handoff\s*\\(.*?)2>/dev/null", end_sh, re.DOTALL)
    assert block_m, "找不到 session-end 的 save handoff 调用块"
    block = block_m.group(1)
    missing = [f for f in required if not re.search(rf"(?:^|\s|\\n\s*){f}=", block)]
    assert not missing, f"session-end save handoff 缺少必填字段: {missing}"


def test_upgrade_manifest_entries_exist():
    """DEC-010: 拥有区清单的每个条目必须在仓库中真实存在。"""
    manifest = ROOT / "scripts/governance/gov-owned.list"
    for line in manifest.read_text(encoding="utf-8").splitlines():
        entry = line.split("#")[0].strip()
        if not entry:
            continue
        if entry.endswith("/*"):
            assert (ROOT / entry[:-2]).is_dir(), f"清单目录不存在: {entry}"
        else:
            assert (ROOT / entry).is_file(), f"清单文件不存在: {entry}"


def test_template_version_format():
    """DEC-010: gov-template-version 必须是合法 semver（单一事实源）。"""
    import re
    v = (ROOT / "scripts/governance/gov-template-version").read_text().strip()
    assert re.fullmatch(r"\d+\.\d+\.\d+", v), f"模板版本号格式非法: {v!r}"


def test_upgrade_check_and_apply_on_fork(tmp_path):
    """DEC-010: 分叉上 --check 报差异不改文件, --apply 备份并同步。"""
    import shutil
    import subprocess
    fork = tmp_path / "fork"
    (fork / "scripts/governance").mkdir(parents=True)
    for f in ("gov-upgrade.sh", "gov-owned.list"):
        shutil.copy(ROOT / "scripts/governance" / f, fork / "scripts/governance" / f)
    (fork / "scripts/governance/gov-template-version").write_text("1.0.0\n")
    (fork / "scripts/governance/gov-validate.sh").write_text("# forked old version\n")
    upg = fork / "scripts/governance/gov-upgrade.sh"

    proc = subprocess.run(["bash", str(upg), "--check", str(ROOT)],
                          capture_output=True, text=True, timeout=120)
    assert proc.returncode == 0, proc.stderr
    assert "本地 1.0.0" in proc.stdout and "[差异]" in proc.stdout, proc.stdout
    assert "forked old version" in (fork / "scripts/governance/gov-validate.sh").read_text(), \
        "--check 不得修改文件"

    proc2 = subprocess.run(["bash", str(upg), "--apply", str(ROOT)],
                           capture_output=True, text=True, timeout=120)
    assert proc2.returncode == 0, proc2.stderr
    assert (fork / "scripts/governance/gov-validate.sh").read_text() == \
        (ROOT / "scripts/governance/gov-validate.sh").read_text(), "--apply 应同步到源内容"
    baks = list((fork / ".cache/upgrade-backup").rglob("gov-validate.sh"))
    assert baks and "forked old version" in baks[0].read_text(), "--apply 应先备份旧文件"
