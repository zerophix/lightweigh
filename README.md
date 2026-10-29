# lightweigh

lightweigh is an opinionated, **local-first AI collaboration governance harness**
that lives *inside* a git repository. It enforces a multi-agent coding workflow
through git hooks, shell/Python validators, and a pytest suite — with **no
GitHub Actions budget and no branch protection** required.

It is currently layered on top of an Android/Kotlin `ScaleHub` device-management
application, but the governance layer is portable to any git monorepo.

## Why (the problem it solves)

Most AI coding repos run one agent at a time under human review. lightweigh
targets the harder case: **multiple AI agents (and humans) sharing one worktree**,
where accidental overlap, stale task state, and unguarded merges are the norm.

It reconstructs the controls a normal team gets from GitHub Enterprise:

- **branch / scope lock** — who may edit which path, by role
- **single-writer derived state** — `.ai/tasks.json`, `STATUS.md`, `docs/index.md`
  are only mutated by the merge queue
- **lane-scoped CI** — `gov-local-ci.sh` picks static / unit / full per profile
- **evidence binding** — every CI conclusion carries `git_sha`,
  `rule_version`, and `check_set_hash` so rule changes invalidate stale results
- **merge-then-crash recovery** — `gov-board-reconcile.sh` is idempotent

## Layout

```
AGENTS.md              # governance index (rules → executors)
scripts/hooks/         # core.hooksPath = scripts/hooks
scripts/governance/    # validate.sh, local-ci, merge, queue, validators
scripts/workflow/      # wf-autopilot, wf-role, wf-claim, wf-mode, wf-queue
scripts/session/       # worktree lifecycle + staged ownership
scripts/testing/       # pytest entrypoint (gov-pytest.sh)
tests/unit/            # 80+ invariant tests
docs/ai/               # decisions, reviews, inbox, lifecycle (golden)
```

## 快速开始

```bash
bash scripts/session/ses-session-begin.sh      # session + compliance card
bash scripts/workflow/wf-role.sh claim dev      # declare role
bash scripts/workflow/wf-autopilot.sh status    # one-screen truth + NEXT cmd
```

See [CONTRIBUTING.md](CONTRIBUTING.md) for the full role + merge workflow.

## Work modes

| Mode      | Skips            | TTL   |
|-----------|------------------|-------|
| `explore` | compile, push     | 2h    |
| `docs`    | compile, push     | 2h    |
| `test`    | compile (pytest)  | 4h    |
| `code`    | full standard     | dev   |
| `gov`     | most strict       | dev   |

```bash
bash scripts/workflow/wf-mode.sh switch explore --duration 2h
bash scripts/workflow/wf-mode.sh switch code
```

Modes are HMAC-signed in `.ai/current-mode.json` and auto-expire back to `dev`.

## Governance decisions

Architecture decision records live in `docs/ai/decisions/DEC-*.md`. The
rationale and the Q1–Q5 self-checks (timing / medium / coverage / blind spot /
demo gap) that every rule must answer are in `AGENTS.md`.

## 相关文档

- [Architecture decision records](docs/ai/decisions/) — `DEC-*.md`
- [Governance index](AGENTS.md) — rules → executors mapping
- [Contribution guide](CONTRIBUTING.md)

## License

Apache-2.0. See [LICENSE](LICENSE).
