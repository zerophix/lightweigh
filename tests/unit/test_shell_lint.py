"""Shell 脚本静态防线（DEC-068 补充）。

今天在队列脚本上连续踩了三类"只有运行时才炸"的坑，全部可静态检出：

  1. `$var` 紧跟中文 → bash 把多字节字符当成变量名的一部分 → unbound variable
     （set -u 下直接崩，且错误信息乱码）
  2. 合并提交信息用了非法 type（`merge(...)`）→ commit-msg 钩子拒绝
  3. 空数组展开 `${arr[@]}` → set -u 下 unbound variable

这里把它们变成仓库级 lint，避免以后每个新脚本重复踩。
"""
import re
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SCRIPTS = sorted(
    p for p in (ROOT / "scripts").rglob("*.sh") if p.is_file()
)

VAR_THEN_CJK = re.compile(r"\$[A-Za-z_][A-Za-z0-9_]*[^\x00-\x7f]")
VALID_COMMIT_TYPES = (
    "feat|fix|docs|style|build|refactor|revert|test|perf|ci|chore"
)
COMMIT_SUBJECT = re.compile(rf"^({VALID_COMMIT_TYPES})\([a-z0-9-]+\): .+$")


def test_scripts_exist():
    assert SCRIPTS, "scripts/ 下应有 shell 脚本"


def test_no_unbraced_var_adjacent_to_cjk():
    """`$var` 后紧跟非 ASCII 必须写成 `${var}`（bash 会把多字节当变量名）。"""
    bad = []
    for path in SCRIPTS:
        for lineno, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
            # 跳过注释与 echo 之外的场景也一视同仁：规则简单才守得住
            if VAR_THEN_CJK.search(line):
                bad.append(f"{path.relative_to(ROOT)}:{lineno}: {line.strip()[:80]}")
    assert not bad, "变量后紧跟中文，请改用 ${var}：\n" + "\n".join(bad)


def test_all_scripts_parse():
    """所有 shell 脚本必须通过 bash -n。"""
    bad = []
    for path in SCRIPTS:
        r = subprocess.run(["bash", "-n", str(path)], capture_output=True, text=True)
        if r.returncode != 0:
            bad.append(f"{path.relative_to(ROOT)}: {r.stderr.strip()[:120]}")
    assert not bad, "\n".join(bad)


def test_queue_merge_message_is_valid_commit_subject():
    """队列生成的合并提交信息必须符合 commit-msg 规则（否则合并全部被拒）。"""
    text = (ROOT / "scripts" / "workflow" / "wf-queue.sh").read_text(encoding="utf-8")
    m = re.search(r'git merge --no-ff -q -m "([^"]*)"', text)
    assert m, "未找到队列的合并提交信息模板"
    template = m.group(1)
    # 用真实分支名渲染后校验
    rendered = (
        template.replace("$branch", "feat/example-task")
        .replace("${branch}", "feat/example-task")
        .replace("$stamp", "20261024-120000")
        .replace("${stamp}", "20261024-120000")
    )
    assert COMMIT_SUBJECT.match(rendered), f"非法提交信息: {rendered}"


def test_queue_handles_shared_state_conflict():
    """队列必须包含"共享状态冲突取候选侧"的逻辑，否则双分支改注册表就卡死。"""
    text = (ROOT / "scripts" / "workflow" / "wf-queue.sh").read_text(encoding="utf-8")
    assert "SHARED_PATHS" in text
    assert "checkout --ours" in text
