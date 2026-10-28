"""agentdrive CLI - generic auto-driver for OpenCode sessions.

Examples:
  # Any repo, no project knowledge:
  agentdrive --goal "add install section to README" --done "grep -q Install README.md"

  # This project, using its own governance as the done-check:
  agentdrive --policy lightweigh --task T-138 --root .
"""
import argparse
import json
import sys
from pathlib import Path

from .core.session import OpenCodeBackend
from .core.driver import Driver
from .core.guard import Guard, EventLog
from .policies.default_policy import DefaultPolicy
from .policies.lightweigh_policy import LightweighPolicy


def main(argv=None):
    ap = argparse.ArgumentParser(prog="agentdrive",
                                 description="Auto-drive an OpenCode session toward a goal.")
    ap.add_argument("--goal", required=False, help="Natural-language goal")
    ap.add_argument("--done", dest="done_command", help="Shell command; exit 0 = done")
    ap.add_argument("--policy", choices=["default", "lightweigh"], default="default")
    ap.add_argument("--task", help="Task id (for lightweigh policy)")
    ap.add_argument("--role", help="Role id: run that role's whole task group")
    ap.add_argument("--max-tasks", type=int, default=0, help="Limit group size (0=all)")
    ap.add_argument("--root", default=".", help="Project root")
    ap.add_argument("--model", help="provider/model#variant (default policy)")
    ap.add_argument("--max-rounds", type=int, default=60)
    ap.add_argument("--budget-tokens", type=int, default=1_000_000)
    ap.add_argument("--wallclock", type=int, default=5400)
    ap.add_argument("--poll", type=int, default=8)
    ap.add_argument("--pause-token", help="Path to pause token file")
    ap.add_argument("--session", help="Pin an existing opencode session id (reuse its context)")
    ap.add_argument("--reuse-sessions", action="store_true",
                    help="Reuse one long-lived session per role (move between worktrees)")
    ap.add_argument("--json", action="store_true", help="Emit final outcome as JSON")
    args = ap.parse_args(argv)

    root = Path(args.root).resolve()

    import os
    model_spec = args.model or os.environ.get("AGENTDRIVE_MODEL")
    model = None
    if model_spec:
        parts = model_spec.split("#")
        spec = parts[0]
        variant = parts[1] if len(parts) > 1 else "default"
        if "/" in spec:
            provider, mid = spec.split("/", 1)
            model = {"id": mid, "providerID": provider, "variant": variant}

    if args.policy == "lightweigh":
        policy = LightweighPolicy(root=str(root), task_id=args.task)
        task = policy.next_task() if not args.task else policy._load_task(args.task)
        if not task:
            print("no task found", file=sys.stderr); return 2
        goal = args.goal or (task.title or f"完成 {task.id}")
    else:
        if not model:
            print("default policy needs a model: pass --model provider/model#variant "
                  "or set AGENTDRIVE_MODEL", file=sys.stderr)
            return 2
        policy = DefaultPolicy(root=str(root), done_command=args.done_command, model=model)
        task = None
        if not args.goal:
            print("--goal required for default policy", file=sys.stderr); return 2
        goal = args.goal

    backend = OpenCodeBackend(home=root)
    guard = Guard(max_rounds=args.max_rounds, budget_tokens=args.budget_tokens,
                  max_wallclock_s=args.wallclock, pause_token=args.pause_token)
    events = EventLog(str(root / ".cache" / "agentdrive" / "events.jsonl"))

    pool = None
    if args.reuse_sessions:
        from .core.pool import SessionPool
        pool = SessionPool(root=root, backend=backend)

    driver = Driver(policy=policy, backend=backend, guard=guard,
                    events=events, poll_interval=args.poll, pool=pool)

    if args.role:
        group = policy.task_group(args.role) if hasattr(policy, "task_group") else []
        if args.max_tasks:
            group = group[:args.max_tasks]
        outcome = driver.run_group(args.goal or "", args.role, group)
    else:
        outcome = driver.run(goal, task, reuse_session_id=args.session)
    if args.json:
        print(json.dumps({"status": outcome.status, "session": outcome.session_id,
                          "rounds": outcome.rounds, "tokens": outcome.tokens,
                          "detail": outcome.detail}, ensure_ascii=False, default=str))
    else:
        print(f"[agentdrive] status={outcome.status} session={outcome.session_id} "
              f"rounds={outcome.rounds} tokens={outcome.tokens}")
    return 0 if outcome.status == "DONE" else 1


if __name__ == "__main__":
    sys.exit(main())
