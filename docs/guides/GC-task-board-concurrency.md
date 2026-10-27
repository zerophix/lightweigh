# DOC-任务看板与并发开发

> **版本**：v2.0（对应代码库 `wf-task-board.sh` + `wf-board-audit.sh` + `wf-lock.sh` + `wf-autopilot.sh`）
> **来源**：DEC-064（任务锁/工作区卫生）、DEC-075（角色/批次/来源）、DEC-066（Autopilot 自动驾驶）
> **状态**：✅ 脚本已落地，📝 文档同步中

---

## 1. 任务数据模型（单一真源：`.ai/tasks.json`）

### 1.1 必填字段（新增任务强制校验）

| 字段 | 类型 | 说明 | 校验规则 |
|------|------|------|----------|
| `id` | string | T-XXX 格式，自动分配 | 唯一、递增 |
| `description` | string | 一句话描述交付物 | 非空、2-gram 去重 ≥0.6 拦截 |
| `why` | string | 为什么做、关联决策/原型 | 可空 |
| `status` | enum | `pending` \| `in_progress` \| `blocked` \| `completed` \| `done` | 必填 |
| `group_id` | string | 归属分组 | **必填**、**必须在 groups 索引** |
| `batch` | string | 批次 ID | **必填**、**必须在 batches 索引** |
| `role` | enum | `dev` \| `test` \| `docs` \| `gov` \| `review` | **必填**、自动推断或显式指定 |
| `source` | enum | `dec` \| `prototype` \| `incident` \| `gap` \| `review` | **必填** |
| `source_ref` | string | 来源引用（如 DEC-081） | `source=dec/review` 时**必填** |
| `target_files` | string[] | 产物文件路径列表 | **必填**（历史任务补齐中） |
| `target_symbols` | string[] | 符号级归属（类/函数名） | 可空，**解决文件重叠误判** |
| `depends_on` | string[] | 依赖任务 ID 列表 | 必须存在、无环 |
| `parent` | string | 父任务 ID | 必须存在、不与 depends_on 重叠 |
| `priority` | enum | `P0` \| `P1` \| `P2` | 默认 P2 |
| `merged_into` | string | 被哪个任务取代（去重用） | 形成链则报错 |
| `overlaps_with` | string[] | 声明 target_files 重叠的任务 | Jaccard ≥0.5 时必声明 |

### 1.2 状态流转

```
pending ──(take/认领)──► in_progress ──(DoD 通过)──► completed
    │                        │
    │                        └─(block 原因)──► blocked
    │
    └─(merged_into)──► [归档，不再参与调度]
```

### 1.3 索引表（必须维护）

```json
{
  "groups": [
    {"id": "auth", "name": "认证授权", "tasks": ["T-080", "T-081"]},
    {"id": "measure", "name": "测量核心", "tasks": ["T-072", "T-073"]}
  ],
  "batches": [
    {"id": "B-w1-screens", "title": "W1 屏交付", "tasks": ["T-080", "T-081", "T-082"]}
  ]
}
```

---

## 2. 任务看板 CLI（`wf-task-board.sh`）

### 2.1 核心命令

```bash
# 列出所有任务（按状态分组）
wf-task-board.sh list

# 查看可开工任务（依赖已满足，按 priority 排序）
wf-task-board.sh ready

# 查看被阻塞任务及阻塞者
wf-task-board.sh blocked

# 记录检查点（进度/决定/下一步，接力必需）
wf-task-board.sh checkpoint T-081 "完成登录页 UI，待接入 AuthPort，下一步写单测"

# 依赖管理
wf-task-board.sh dep add T-082 T-081   # T-082 依赖 T-081
wf-task-board.sh dep cycles            # 检测依赖环（有环即报错）

# 标记完成/阻塞
wf-task-board.sh done T-081
wf-task-board.sh block T-082 "等待 AuthPort 接口定稿"

# 设置下一步（需人类确认）
wf-task-board.sh next "开工 T-082 测量页重构" "T-081 已完成解锁"
wf-task-board.sh confirm              # 人类确认后执行
```

### 2.2 新增任务（强制结构化，拦截重复）

```bash
wf-task-board.sh add "测量页重构" "DEC-082 拆解" \
  --group measure \
  --source dec \
  --source-ref DEC-082 \
  --batch B-w1-screens \
  --files app/src/main/kotlin/.../MeasureScreen.kt \
  --role dev \
  --depends-on T-081 \
  --priority P0
```

**拦截规则**：
- 2-gram 相似度 ≥ 0.6 → 拒绝，提示相似任务
- 除非 `--distinct "差异说明"` 或 `--supersedes T-XXX`

---

## 3. 任务板严格审计（`wf-board-audit.sh --strict`）

**合并队列强制执行，硬问题即拒绝合入**

| 硬问题类型 | 检测逻辑 | 处理方式 |
|------------|----------|----------|
| `merged_into` 链 | A→B→C 形式的传递引用 | 扁平化：直接指向最终任务 |
| 有效任务缺 `target_files` | `active` 任务无产物声明 | 补齐或标记 `archived` |
| 文件高度重叠未声明 | Jaccard ≥ 0.5 且无 `overlaps_with` | 加 `overlaps_with` 或拆分符号归属 |
| 符号级归属互不重叠 | 同一文件不同 `target_symbols` | 合法，不报错 |
| 缺 `group_id` 或不在索引 | 必填 + 必须在 `groups` | 补齐索引或加 group |
| 缺 `batch` 或不在索引 | 必填 + 必须在 `batches` | 补齐索引或加 batch |
| `role`/`source` 非法值 | 不在枚举集合 | 修正枚举值 |
| 依赖不存在/成环 | `depends_on` 引用检查 + DFS 环检测 | 修正依赖或打破环 |
| `parent` 与 `depends_on` 冲突 | 同时依赖父任务 | 去掉冲突依赖 |

**软债务（只报告不拦截）**：
- 历史任务缺 `acceptance`
- 无 `verify_commands`

```bash
# 本地自检
bash scripts/workflow/wf-board-audit.sh --strict

# CI/队列内部自动执行
# 失败即拒绝合入，需修 .ai/tasks.json 后重跑
```

---

## 4. 并发开发三件套

### 4.1 任务互斥锁（`wf-lock.sh`，JSON + TTL）

```bash
# 认领锁（30 分钟 TTL，自动过期清理）
wf-lock.sh T-081

# 检查锁状态
wf-lock.sh --check T-081
# 输出：LOCKED by ses-xxx (剩余 25 分钟) / UNLOCKED

# 释放当前会话所有锁
wf-unlock.sh
```

> **设计**：单文件 `.ai/task-lock.json`，TTL 自动 gc，无残留目录问题。

### 4.2 Worktree 隔离（`ses-worktree.sh`）

```bash
# 创建 worktree（自动复制 local.properties、自检钩子）
bash scripts/session/ses-worktree.sh add feat/t081-auth

# 列出/清理
bash scripts/session/ses-worktree.sh list
bash scripts/session/ses-worktree.sh clean [--dry-run]
```

**上限**：默认 3 个任务 worktree（`WF_WORKTREE_LIMIT`），超限拒绝创建。

### 4.3 谁在干什么（`who-is-working.sh`）

```bash
bash scripts/dev/who-is-working.sh
# 输出：
# 会话          worktree                    分支              模块      锁
# ses-abc       ../lightweigh-t081-auth     feat/t081-auth    auth      T-081
# ses-def       ../lightweigh-t082-measure  feat/t082-measure measure   T-082
```

---

## 5. Autopilot 自动驾驶（`wf-autopilot.sh`）

**任何 AI 进入仓库第一步：**

```bash
bash scripts/workflow/wf-autopilot.sh status
```

### 5.1 一屏真相输出

```
============================================================
 工作区状态 (14:32)  分支: feat/t081-auth
============================================================

【任务板下一批】
批次1: 3 条任务
  - T-081 | 模块=auth | 分支=feat/t081-auth | 子代理=sub-t081 | 文件4个
  - T-082 | 模块=measure | 分支=feat/t082-measure | 子代理=sub-t082 | 文件2个

【在途 PR】
  PR    分支                                     状态         worktree
  #124  feat/t071-sync                           MERGEABLE    ../lightweigh-t071-sync

【worktree 审计】
  已合入但未清理（可安全删除）:
    ../lightweigh-t069|feat/t069-profile

【教训库】
  共 12 条，未强制 3 条：LEARN-004, LEARN-011, LEARN-019

【下一步】
  bash scripts/workflow/wf-autopilot.sh advance     # 自动推进所有在途 PR
```

### 5.2 自动推进在途 PR（`advance`）

```bash
# 串行推进（冲突即停）
wf-autopilot.sh advance

# 并行推进（预处理串行 → CI 并发 → 合入串行）
wf-autopilot.sh advance --parallel
```

**内部步骤**：同步 master → 机械冲突自动解决 → 派生物同步 → local-ci → gh pr merge → 清理 worktree/分支

### 5.3 单任务启动/收工（`start`/`finish`）

```bash
# 认领 + 建 worktree + 打印任务要求
wf-autopilot.sh start T-081

# DoD 校验 → 提交 → 推送 → 开 PR
wf-autopilot.sh finish T-081
```

---

## 6. 会话接力协议（`ses-session-begin.sh` / `ses-session-end.sh`）

### 6.1 启动时自动加载

```bash
bash scripts/session/ses-session-begin.sh
# 输出：
# 🆔 会话标识: ses-20261027-143200-a1b2
#   上一个 AI 的交接: 上下文: 完成 T-081 登录页 | 下一步: 开工 T-082
#   AI 收件箱有 1 条未读消息
#   === AI 接力状态 ===
#     下一步待人类确认: 开工 T-082 测量页重构
#     进行中任务: T-081 登录页重构
```

### 6.2 收尾强制交接（四项缺一不可）

```bash
bash scripts/session/ses-session-end.sh
# 交互式填写：
# 1. 本次完成的工作
# 2. 未完成/留给下一 Session 的工作
# 3. 关键决策与理由（为什么这么做、考察过哪些方案）
# 4. 已知风险/陷阱/未验证假设
# 5. 下一个 AI 应该做什么 + 理由
```

**自动执行**：
1. 校验文档（`gov-validate.sh`）
2. 更新任务板（`next` + `risk`）
3. 写入收件箱结构化接力信
4. 写入上下文缓存（含推理过程）
5. 刷新 STATUS.md、更新指标
6. **工作区审计**（`gov-workspace-doctor.sh --fix`：清 worktree/分支/锁/证据）
7. 清理会话标识、本会话 claims

---

## 7. 常见场景操作指南

### 7.1 并行开发两个无依赖任务

```bash
# 1. 看可并行批次
wf-parallel.sh plan --limit 3
# → 批次1: T-081(auth), T-082(measure)  文件无交集

# 2. 任务 A 认领
wf-lock.sh T-081
wf-role.sh claim dev --task T-081
bash scripts/session/ses-worktree.sh add feat/t081-auth
cd ../lightweigh-t081-auth
bash scripts/session/ses-session-begin.sh
# ... 开发 T-081 ...

# 3. 任务 B 另开终端/会话并行
wf-lock.sh T-082
wf-role.sh claim dev --task T-082
bash scripts/session/ses-worktree.sh add feat/t082-measure
cd ../lightweigh-t082-measure
bash scripts/session/ses-session-begin.sh
# ... 开发 T-082 ...
```

### 7.2 处理被阻塞任务

```bash
# 查看阻塞链
wf-task-board.sh blocked
# T-083  等待 T-081, T-082

# 标记阻塞（自动记录原因）
wf-task-board.sh block T-083 "等待 T-081/T-082 完成 AuthPort 接口"

# 依赖完成后，T-083 自动出现在 ready 列表
wf-task-board.sh ready
```

### 7.3 任务去重/替代

```bash
# 发现重复：新任务替代旧任务
wf-task-board.sh add "新版登录页" "DEC-083 重构" \
  --group auth --source dec --source-ref DEC-083 \
  --batch B-w1-screens --supersedes T-080
# → T-080.merged_into = T-083，T-080 归档不再调度
```

---

## 8. 关联文档

- `DOC-批次与队列执行模型` — 批次创建、角色、队列合入
- `DOC-角色系统与写入边界` — 角色定义、边界校验、must_produce
- `DOC-治理门禁与模式开关` — 三层开关、mode.sh 物理关钩子
- `DOC-完成定义与证据闭环` — DoD 校验、证据随分支提交、队列消费
- `AGENTS.md` §3-§5 — 任务画像、worktree 生命周期、共享文件单一写者

---

## 9. 已知缺口

| 缺口 | 影响 | 计划 |
|------|------|------|
| 历史 23 条任务无 `target_files` | 无法跑 DoD、证据链断裂 | 分批补齐或标记 archived |
| 8 条任务 `acceptance.status=draft` | 无法机械验收 | 补齐 AC 或标记 blocked |
| Notion 任务视图未同步新字段 | 人类看旧视图 | Notion 数据库字段同步 |
| 依赖图可视化缺失 | 仅 CLI 文本 | Notion 甘特/依赖图自动生成 |