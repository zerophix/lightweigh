# Project Collaboration Blueprint — AI 协作协议

> 任何 AI 在此项目中工作时遵循本文件。细节见 `docs/guides/GC-*.md`。DEC-048 起本文件保持精炼。

---

## 新 AI 启动（必须）

```bash
cat STATUS.md
bash scripts/session/ses-context-load.sh
bash scripts/workflow/wf-inbox.sh list
bash scripts/workflow/wf-task-board.sh list
ls -t docs/notes/ | head -3
ls -t docs/ai/decisions/ | head -3
```

1. 有未读 inbox → 先读再干活。
2. `next_action` 未确认 → 问人类；确认后 `wf-task-board.sh confirm`。
3. 编码前读任务 `acceptance`（见 [GC-task-acceptance](docs/guides/GC-task-acceptance.md)）；无 ready AC 不开工。
4. 多会话：`ses-worktree.sh add feat/<topic>` → `cd` 到新树 → `ses-session-begin.sh` → `ses-claim-staged.sh` → commit → `gov-local-ci.sh` → PR → `ses-session-end.sh`。详见 [GC-multi-agent-collab](docs/guides/GC-multi-agent-collab.md)（DEC-050）。

### 接力铁律

- 必须查收件箱；必须问人类确认方向；结束必须 `ses-session-end.sh`（完成/未完成/决策理由/风险）。

---

## 硬规则

1. **禁止**在 `master`/`main` 直提交；分支 `feat|fix|docs|refactor|chore|test|ci/<topic>`。
2. **合入权威（无 GitHub Pro）**：`bash scripts/governance/gov-local-ci.sh` 全绿 + PR 模板勾选；勿习惯性 `--no-verify`。
3. 决策：`NOTE` → `DEC` → lifecycle → `wf-promote.sh` → 黄金标准。详见 [GC-decision-flow](docs/guides/GC-decision-flow.md)。
4. 编码约定：[GC-coding-conventions](docs/guides/GC-coding-conventions.md)（模块、四件套、Room、黄金禁区）。
5. 文档语言：跟 `.language`（本仓 `zh-CN` → 中文文档）。
6. 工程反思**自愿**：[GC-engineering-reflections](docs/guides/GC-engineering-reflections.md)；REFLECT 门禁已退役（DEC-047）。
7. 提交：`<type>(<scope>): <subject>`（英文，标题 ≤72）；type 含 feat/fix/docs/test/chore/ci/refactor/build/perf/revert/style。

---

## 允许写入的文档位置

只能在下列位置新增/更新约定名的 `.md`（勿造 `old/`/`legacy/`/`vN/`）：

- `docs/guides/GC-{slug}.md`、`docs/reference/REF-{slug}.md`、`docs/archive/ARCH-{slug}.md`
- `docs/notes/NOTE-YYYYMMDD-NNN.md`、`docs/ai/decisions/DEC-XXX.md`
- `docs/ai/lifecycle/{01..06}_*.md`（覆盖型）、`docs/ai/reviews/...`、`docs/ai/inbox/...`
- 各目录 `index.md` 可更新导航；**不可**直改黄金标准正文（须 promote）

完整树与命名表见历史版本 git；以本清单 + validate 为准。

---

## 命令入口

```bash
bash scripts/governance/gov-validate.sh
bash scripts/governance/gov-custom-checks.sh
bash scripts/governance/gov-local-ci.sh
bash scripts/governance/gov-install-gitleaks.sh  # 可选：安装本地 gitleaks
bash scripts/governance/gov-next-decision.sh --create
bash scripts/workflow/wf-promote.sh <target> --decision DEC-XXX
bash scripts/bootstrap/bs-install-hooks.sh   # 安装 simplified pre-commit（DEC-047）
./gradlew :domain:test   # 更多见 GC-coding-conventions
```

规则族清单以 `gov-validate.sh` 实时输出为准（勿在文档写死条数）。生命周期 10 步见 `docs/ai/guides/workflow.md`。

---

## 本机钩子与 CI（事实）

| 层 | 行为 |
|----|------|
| pre-commit | `gov-pre-commit-simplified`：禁 master、可选会话归属、源码时 compile + custom-checks |
| pre-push | 分支名 + 全量 validate（可被 `--no-verify` 绕过） |
| Actions | `ci.yml`：push 到 master/main/**特性分支通配**（feat/fix/chore/…）与 **PR**（DEC-049）；文档变更仍可跳过重型构建（DEC-033） |
| Dependabot | `.github/dependabot.yml`：gradle + github-actions 周更（DEC-049） |
| 密钥扫描 | `gov-secrets-scan.sh`（gitleaks）接入 local-ci / pre-push；缺二进制软警告，有则硬失败 |
| STATUS 证据 | `gov-local-ci` 写入 `.cache/local-ci-last.json`，`STATUS.md` 健康检查读取（DEC-051）；可选 `gh run` |
| 分支保护 | **无**（用户不做 Pro）；靠 local-ci + PR 模板纪律补偿 |

---

## 平台蒸馏

| 平台 | 文件 |
|------|------|
| 通用 | `AGENTS.md`（本文件） |
| Cursor / Cline | `.cursorrules` / `.clinerules` |

任务板权威：`.ai/tasks.json`。
