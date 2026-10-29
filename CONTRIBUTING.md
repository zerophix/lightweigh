# Contributing to lightweigh

Thanks for your interest. lightweigh is a **local-first governance harness**:
it does not depend on GitHub Actions (which is paywalled here) or branch
protection. To keep the system self-consistent, follow the workflow below.

## 1. Declare a role

lightweigh enforces *role-scoped* edits. Claim one before changing code.

```bash
bash scripts/session/ses-session-begin.sh          # session + compliance card
bash scripts/workflow/wf-role.sh claim <role>       # dev | test | gov | docs
```

| Role     | May edit                                | Must produce        |
|----------|-----------------------------------------|---------------------|
| `dev`    | `app/` / `domain/` / `application/` / `infrastructure/` + build | task evidence + affected tests pass |
| `test`   | `tests/` + module `src/test/`           | failing test or coverage evidence |
| `gov`    | `scripts/**`, `tests/**`, `docs/**`, `.github/**`, root rules | full pytest pass |
| `docs`   | `docs/**`, `design/**`, `*.md`          | index entry + reachable links |

A role expires after 8 hours. You cannot hold more than one role at a time.

## 2. Work on a branch

```bash
bash scripts/workflow/wf-mode.sh switch explore --duration 2h   # optional, skip compile
```

Branch naming: `<type>/<description>` (e.g. `feat/auth`, `chore/docs`).

## 3. Claim a task (if one exists)

```bash
bash scripts/workflow/wf-claim.sh claim T-xxx
```

## 4. Ship

```bash
bash scripts/dev/ship.sh -m "feat(scope): subject" <path>
```

`ship.sh` runs pre-commit + pre-push checks, commits, pushes, and opens a PR.
If you see `❌ pre-push 审计未通过`, read the message; `--no-verify` is audited
(`GOV_BYPASS_POLICY=block` can make it hard-fail).

## 5. Review & merge

Merge is done by the merge queue (single trunk writer):

```bash
bash scripts/governance/gov-local-ci.sh            # full lane-C gate
bash scripts/governance/gov-merge.sh <PR-number>   # queue merge + reconcile
```

## Writing tests

lightweigh is unusual in that **tests also test the governance system**:

```bash
bash scripts/testing/gov-pytest.sh                # all governance + unit tests
bash scripts/testing/gov-pytest.sh -k evidence    # a subset
```

New governance rules should come with a test that proves the rule fires
(negative case) and that it can be retired (the rule lists its retirement
condition: two false positives or 30 days of zero hits → review).

## Pull request checklist

- [ ] Role claimed; change is within the role's allow list
- [ ] Branch `<type>/<description>`, commit `<type>(<scope>): <subject>`
- [ ] `Task: T-xxx` trailer present where a task exists
- [ ] `gov-local-ci.sh` (lane C) is green
- [ ] `docs/index.md` updated if you added a doc

## License

By contributing you agree your changes are licensed under Apache-2.0 (see
`LICENSE`).
