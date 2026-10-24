"""后台任务执行器 wf-run.sh 的行为测试。

核心契约（AGENTS.md 引用）:
  - start 立即返回（<2s），任务在后台继续，stdout 只回显 job id
  - 任务结束把退出码原子写入 .exit；wait 回传同一退出码并输出日志尾部
  - 同前缀并发可用；list/status/tail 可观测；gc 只清理已完成 job
  - 参数错误必须给出明确用法并在 stderr 说明

测试通过 WF_RUN_JOBS_DIR 指向 tmp_path，避免污染仓库 .cache。

运行: python3 -m pytest tests/unit/test_job_runner.py -q
"""
from __future__ import annotations

import os
import subprocess
import time
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "scripts" / "workflow" / "wf-run.sh"


@pytest.fixture()
def jobs_dir(tmp_path: Path) -> Path:
    """每个用例独立的 job 目录。"""
    return tmp_path / "jobs"


def _run(args, jobs_dir: Path, timeout: int = 30) -> subprocess.CompletedProcess:
    env = os.environ.copy()
    env["WF_RUN_JOBS_DIR"] = str(jobs_dir)
    return subprocess.run(
        ["bash", str(SCRIPT), *args],
        capture_output=True,
        text=True,
        env=env,
        timeout=timeout,
    )


def _start(jobs_dir: Path, name: str, *command: str) -> str:
    proc = _run(["start", name, "--", *command], jobs_dir)
    assert proc.returncode == 0, proc.stderr
    job_id = proc.stdout.strip()
    assert job_id, "start 必须回显 job id"
    return job_id


def _wait_for_file(path: Path, timeout: float = 5.0) -> None:
    deadline = time.monotonic() + timeout
    while not path.is_file():
        if time.monotonic() > deadline:
            raise AssertionError(f"等待文件超时: {path}")
        time.sleep(0.02)


def test_start_returns_immediately_and_job_continues(jobs_dir: Path) -> None:
    begin = time.monotonic()
    job_id = _start(jobs_dir, "quick", "bash", "-c", "sleep 1; echo done")
    elapsed = time.monotonic() - begin

    assert elapsed < 2.0, f"start 阻塞了 {elapsed:.2f}s，应在 2s 内返回"
    log = jobs_dir / f"{job_id}.log"
    exitf = jobs_dir / f"{job_id}.exit"
    _wait_for_file(log)
    assert not exitf.exists(), "任务尚未结束，不应出现 .exit"

    wait = _run(["wait", job_id], jobs_dir)
    assert wait.returncode == 0, wait.stderr
    assert "done" in wait.stdout
    assert "exit=0" in wait.stdout
    assert exitf.is_file()


def test_wait_returns_exit_code_and_log_content(jobs_dir: Path) -> None:
    job_id = _start(
        jobs_dir, "echoes", "bash", "-c", "echo hello-job; echo second-line"
    )
    wait = _run(["wait", job_id, "--timeout", "15"], jobs_dir)

    assert wait.returncode == 0
    assert "hello-job" in wait.stdout
    assert "second-line" in wait.stdout
    assert (jobs_dir / f"{job_id}.exit").read_text().strip() == "0"

    tail = _run(["tail", job_id], jobs_dir)
    assert tail.returncode == 0
    assert "hello-job" in tail.stdout


def test_failure_command_records_exit_3(jobs_dir: Path) -> None:
    job_id = _start(jobs_dir, "fail", "bash", "-c", "echo boom; exit 3")

    wait = _run(["wait", job_id], jobs_dir)

    assert wait.returncode == 3, f"wait 应回传被等待命令退出码，实际 {wait.returncode}"
    assert (jobs_dir / f"{job_id}.exit").read_text().strip() == "3"
    assert "boom" in wait.stdout
    assert _run(["status", job_id], jobs_dir).stdout.strip() == "exit=3"


def test_concurrent_jobs_all_listed_with_correct_status(jobs_dir: Path) -> None:
    started = {}
    for name in ("alpha", "beta", "gamma"):
        job_id = _start(
            jobs_dir, name, "bash", "-c", f"sleep 0.6; echo job-{name}"
        )
        started[name] = job_id

    assert len(set(started.values())) == 3, "并发同前缀 job 的 id 必须互不相同"

    listing = _run(["list"], jobs_dir)
    assert listing.returncode == 0
    for job_id in started.values():
        assert job_id in listing.stdout, f"list 缺少 {job_id}"
    assert "running" in listing.stdout or "done" in listing.stdout

    for name, job_id in started.items():
        wait = _run(["wait", job_id], jobs_dir)
        assert wait.returncode == 0, wait.stderr
        assert f"job-{name}" in wait.stdout

    after = _run(["list"], jobs_dir)
    for job_id in started.values():
        assert f"{job_id}" in after.stdout
        assert "running" not in after.stdout
        assert "exit=0" not in after.stdout  # list 用 EXIT 列，不是 status 格式
    assert after.stdout.count("done") == 3


def test_gc_removes_old_and_zero_day_jobs(jobs_dir: Path) -> None:
    # 构造一个 10 天前的已完成 job
    old_id = _start(jobs_dir, "old", "bash", "-c", "echo old")
    assert _run(["wait", old_id], jobs_dir).returncode == 0
    old_exit = jobs_dir / f"{old_id}.exit"
    ten_days_ago = time.time() - 10 * 86400
    os.utime(old_exit, (ten_days_ago, ten_days_ago))

    # 一个刚完成的 job，默认 7 天阈值下应保留
    fresh_id = _start(jobs_dir, "fresh", "bash", "-c", "echo fresh")
    assert _run(["wait", fresh_id], jobs_dir).returncode == 0

    gc = _run(["gc", "--days", "7"], jobs_dir)
    assert gc.returncode == 0
    assert not (jobs_dir / f"{old_id}.exit").exists()
    assert not (jobs_dir / f"{old_id}.log").exists()
    assert (jobs_dir / f"{fresh_id}.exit").exists(), "新 job 不应被 7 天阈值清理"

    gc_zero = _run(["gc", "--days", "0"], jobs_dir)
    assert gc_zero.returncode == 0
    assert not (jobs_dir / f"{fresh_id}.exit").exists()
    assert not (jobs_dir / f"{fresh_id}.log").exists()


def test_exit_file_is_atomic_and_only_visible_after_completion(jobs_dir: Path) -> None:
    job_id = _start(jobs_dir, "atomic", "bash", "-c", "sleep 0.4; exit 0")
    log = jobs_dir / f"{job_id}.log"
    exitf = jobs_dir / f"{job_id}.exit"
    _wait_for_file(log)

    assert not exitf.exists(), "运行期间 .exit 不可见"
    assert _run(["status", job_id], jobs_dir).stdout.strip() == "running"

    wait = _run(["wait", job_id], jobs_dir)
    assert wait.returncode == 0
    assert exitf.is_file()
    assert exitf.read_text().strip().isdigit()
    # 原子写不应残留临时文件
    assert list(jobs_dir.glob("*.tmp*")) == []


def test_wait_timeout_returns_124_without_killing_job(jobs_dir: Path) -> None:
    job_id = _start(jobs_dir, "slow", "bash", "-c", "sleep 0.8; echo late")
    early = _run(["wait", job_id, "--timeout", "0"], jobs_dir)

    assert early.returncode == 124
    assert "超时" in early.stderr or "timeout" in early.stderr.lower()

    # 任务并没有被杀掉，最终仍能正常完成
    later = _run(["wait", job_id, "--timeout", "15"], jobs_dir)
    assert later.returncode == 0
    assert "late" in later.stdout


@pytest.mark.parametrize(
    "args",
    [
        [],
        ["start"],
        ["start", "foo"],
        ["start", "--", "echo", "hi"],
        ["wait", "../../etc/passwd"],
        ["status", "does-not-exist"],
        ["bogus"],
        ["gc", "--days", "abc"],
    ],
)
def test_bad_arguments_give_usage_error(jobs_dir: Path, args) -> None:
    proc = _run(args, jobs_dir)

    assert proc.returncode == 2, f"{args} 应以 2 退出，实际 {proc.returncode}"
    assert "wf-run:" in proc.stderr
    if args and args[0] in {"start", "", "bogus"}:
        assert "用法" in proc.stderr
