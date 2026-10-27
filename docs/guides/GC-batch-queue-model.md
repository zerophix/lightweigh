# DOC-批次与队列执行模型

> **版本**：v1.0（对应代码库 `wf-batch.sh` + `wf-queue.sh` + `mode.sh`）
> **来源**：DEC-075（角色/批次/来源三件套）、DEC-068（合并队列 N→1）、DEC-072（三环执行模型）
> **适用仓库**：lightweigh（包名 com.xcwk.scalehub，产品名轻衡）
> **状态**：✅ 已落地脚本，📝 文档同步中

---

## 1. 核心模型：三件套互相决定

```
来源 source  →  决定任务类型与角色 role
  ├─ dec        → 决策拆解的执行项              → dev / gov
  ├─ prototype  → 原型缺口补齐                  → dev
  ├─ incident   → 故障复盘/热修                 → dev / gov
  ├─ gap        → 对照缺口补全                  → dev
  └─ review     → 评审结论落地                  → gov / docs

角色 role    →  决定开发期模式与测试策略
  ├─ dev   → mode.sh dev（关钩子），跑受影响单测 + 增量编译
  ├─ test  → mode.sh dev（关钩子），跑受影响 + 全量 pytest
  ├─ docs  → mode.sh dev（关钩子），只跑 gov-validate
  ├─ gov   → mode.sh full（开钩子），跑全量 pytest / local-ci
  └─ review→ 只读，不跑测试，仅产出评审报告

批次 batch   →  一次开工/收工的最小单位
  ├─ 同一批次内任务共享同一 mode（有 gov 则 full，否则 dev）
  ├─ 批次启动 `wf-batch.sh start` 自动切 mode + 打印测试计划
  └─ 批次收工 `wf-batch.sh finish` 跑对应角色测试计划 → 恢复 full
```

---

## 2. 批次生命周期

### 2.1 创建批次（人类/产品侧）
```bash
# 仅需一次，批次 ID 进入 tasks.json 的 batches 索引
bash scripts/workflow/wf-batch.sh create B-w1-screens "W1 屏交付批次"
```

### 2.2 加任务到批次（结构化，强制字段）
```bash
bash scripts/workflow/wf-task-board.sh add "登录页重构" "DEC-081 拆解" \
  --group auth \
  --source dec \
  --source-ref DEC-081 \
  --batch B-w1-screens \
  --files app/src/main/kotlin/.../LoginScreen.kt \
  --role dev \
  --depends-on T-080 \
  --priority P0
```
**强制字段**：`group_id`、`source`、`batch`、`role`、`target_files`、`depends_on`（可空）、`priority`（P0/P1/P2）

### 2.3 查看可开工任务（依赖已满足）
```bash
bash scripts/workflow/wf-task-board.sh ready
# 输出：按 priority 排序的 pending/in_progress 且无未完成依赖的任务
```

### 2.4 批次开工（自动切 mode + 测试计划）
```bash
bash scripts/workflow/wf-batch.sh start B-w1-screens
# 输出示例：
# 批次 B-w1-screens: 0/5 完成，角色 {dev: 4, test: 1}
# 测试计划（按角色合并去重）:
#   - ./gradlew :app:testDebugUnitTest
#   - bash scripts/governance/gov-affected.sh --run
#   - bash scripts/testing/gov-pytest.sh --all
# 已切换 mode=dev
```

### 2.5 任务级开发（并行 worktree）
```bash
# 单任务认领 + 建 worktree + 打印要求
bash scripts/workflow/wf-autopilot.sh start T-081
# → 自动：任务锁 + 角色 dev + worktree feat/t081-auth

# 进入 worktree
cd ../lightweigh-t081-auth
bash scripts/session/ses-session-begin.sh

# 内环快速反馈（≤5s 预算）
bash scripts/dev/dev-loop.sh --watch
```

### 2.6 任务完成（DoD + 证据随分支提交）
```bash
# 1. DoD 校验（生成 .ai/evidence/T-081.json，ok=true/false 都写）
bash scripts/governance/gov-task-done.sh T-081

# 2. 证据必须随分支提交（关键：队列消费证据）
git add .ai/evidence/T-081.json
git commit -m "feat(auth): deliver T-081" --trailer "Task: T-081"
git push -u origin feat/t081-auth
```

### 2.7 批次收工（跑测试计划 → 恢复 full）
```bash
bash scripts/workflow/wf-batch.sh finish B-w1-screens
# 内部：跑完该批次角色对应的测试计划 → 全绿则 mode.sh full 恢复
```

---

## 3. 合并队列：N 分支 → 1 候选 → 1 次全量验证

### 3.1 为什么不是"每分支跑 CI"
- 每个 worktree 冷构建 5~10 分钟，N 分支 = N 次冷构建
- 分支间冲突 90%+ 来自共享状态（已由 pre-commit 拒绝 + strip 兜底）
- **集成本身才是风险点**：在合并后的候选上验证一次，比 N 个孤立分支各验一次更可信

### 3.2 队列执行流程
```bash
bash scripts/workflow/wf-queue.sh run --lanes full
```

| 阶段 | 动作 | 关键点 |
|------|------|--------|
| **预处理** | 清理已合入 worktree、fetch origin | 串行，秒级 |
| **合并** | 依次 merge 到候选 worktree | 机械冲突自动取候选侧；语义冲突**停机指名分支** |
| **验证** | 候选上跑 `GOV_CI_FULL=1 gov-local-ci.sh` | **唯一全量点** |
| **任务板审计** | `wf-board-audit.sh --strict` | 重复/分组/依赖环/merged_into链 硬拦截 |
| **证据消费** | `gov-evidence-check.sh` (EVIDENCE_MODE=block) | 带 Task: trailer 的分支**必须有 ok=true 证据且 SHA 匹配** |
| **回填** | 候选上回填任务板、同步派生文档 | 随候选一起推送，主树不留脏改动 |
| **推送** | `git push origin HEAD:master` | 单次推送 |
| **清理** | 删 worktree/分支/PR、worktree prune | 已合分支自动清理 |
| **记账** | 产品:治理 文件比例写入 `.cache/queue-runs.jsonl` | 防本末倒置（DEC-071） |

### 3.3 失败处理
| 失败点 | 行为 |
|--------|------|
| 合并冲突 | 报告冲突文件清单，中止本次，已合分支保留在候选 |
| 验证失败 | 候选保留（便于排查），不推送，返回失败码 |
| 证据不足 | 拒绝合入，提示去对应 worktree 跑 `gov-task-done.sh` 补证据 |
| 任务板审计失败 | 拒绝合入，提示修 `.ai/tasks.json` 后重跑 |

### 3.4 并行队列（多 PR 同时验证）
```bash
bash scripts/workflow/wf-queue.sh run --parallel
# 阶段1：所有 PR 串行预处理（同步 master + strip 共享状态 + 推送）
# 阶段2：所有 PR 并发跑 local-ci（wf-run.sh 后台）
# 阶段3：按 PR 号顺序串行等待 + 合入 + 清理
```

---

## 4. 测试计划矩阵（按角色合并去重）

| 角色 | 测试命令 | 触发时机 |
|------|----------|----------|
| `dev` | `./gradlew :app:testDebugUnitTest` + `gov-affected.sh --run` | 批次 start/finish、内环 dev-loop |
| `test` | `gov-pytest.sh --all` | 批次 finish、PR 提测 |
| `docs` | `gov-validate.sh` | 提交环、批次 finish |
| `gov` | `gov-local-ci.sh` | 合并队列、批次 finish（含 gov 角色） |
| `review` | 无自动测试 | 评审节点人工触发 |

> **去重规则**：同一批次内多任务同角色，测试计划合并去重，避免重复跑。

---

## 5. 物理/逻辑/角色三层模式开关

| 层级 | 命令 | 作用域 | 典型场景 |
|------|------|--------|----------|
| **物理层** | `mode.sh dev \| full` | `core.hooksPath` → 空目录/正式钩子 | 纯开发期**彻底关钩子**，零开销 |
| **逻辑层** | `gov-mode.sh off 2h` | 单条规则跳过、有审计日志、8h 上限 | 临时放宽、留痕 |
| **角色层** | `wf-role.sh claim dev\|test\|docs\|gov\|review` | 写入边界 + 测试策略 | `wf-batch.sh start` 自动按角色切 mode |

> **批次模式切换逻辑**：
> ```bash
> # wf-batch.sh start 内部
> if "gov" in roles: mode.sh full
> else: mode.sh dev
> ```

---

## 6. 关键约束与最佳实践

1. **批次是模式切换单元**：不要在批次中混用需要不同 mode 的任务；如需 gov 角色任务，单独建批次或接受全批次 full mode。
2. **证据不进分支 = 没做**：`gov-task-done.sh` 生成的 `.ai/evidence/T-xxx.json` 必须 `git add` 并随分支提交，队列只消费分支里的证据。
3. **Task: trailer 是证据锚点**：提交信息必须带 `--trailer "Task: T-081"`，队列据此找对应证据文件。
4. **共享文件单一写者**：`AGENTS.md`、`.ai/tasks.json`、STATUS.md 等只由队列在候选上回填，功能分支**绝不碰**。
5. **依赖图无环**：`wf-task-board.sh dep cycles` 会在加任务、批次审计时自动检测。
6. **重复任务源头拦截**：`wf-task-board.sh add` 时 2-gram 相似度 ≥ 0.6 自动拒，除非 `--distinct` 或 `--supersedes`。

---

## 7. 常用命令速查

```bash
# 批次
wf-batch.sh list                    # 所有批次进度
wf-batch.sh status B-xxx            # 批次明细 + 下一步
wf-batch.sh start B-xxx             # 开工（切 mode + 测试计划）
wf-batch.sh finish B-xxx            # 收工（跑测试计划 → 恢复 full）

# 队列
wf-queue.sh status                  # 可入队分支
wf-queue.sh run [--dry] [--lanes auto|full] [--parallel]  # 批量合入
wf-queue.sh run --pr 123,124        # 仅指定 PR

# 任务看板
wf-task-board.sh ready              # 可开工任务
wf-task-board.sh blocked            # 被谁阻塞
wf-task-board.sh checkpoint T-xxx "进度/决定/下一步"
wf-task-board.sh dep add T-1 T-2    # 加依赖
wf-task-board.sh dep cycles         # 检测环

# 模式开关
mode.sh dev       # 物理关钩子（纯开发）
mode.sh full      # 恢复钩子（交付/合入前）
mode.sh status    # 当前模式

# 角色
wf-role.sh claim dev|test|docs|gov|review [--task T-xxx]
wf-role.sh check-scope --staged     # 提交前越界检查
```

---

## 8. 已知缺口与演进方向

| 缺口 | 现状 | 目标 |
|------|------|------|
| 批次依赖可视化 | 仅 CLI 输出 | Notion 甘特/依赖图自动同步 |
| 队列调度策略 | 简单串行/并行 | 优先级/资源感知调度 |
| 证据可视化 | 仅 JSON 文件 | Notion 证据看板（任务-证据-状态） |
| 角色切换审计 | 仅本地日志 | 审计日志入库可查 |

---

## 9. 关联文档

- `DOC-需求到编码链路` — 来源 source 定义与追溯
- `DOC-任务看板与并发开发` — 任务字段、认领、依赖、checkpoint
- `DOC-治理门禁与模式开关` — 三层开关详细机制
- `DOC-角色系统与写入边界` — 角色定义、must_produce、边界校验
- `DOC-完成定义与证据闭环` — DoD 三重校验、证据随分支提交、队列消费
- `AGENTS.md` §2-§6 — 执行模型、角色、worktree、共享文件、合入规则