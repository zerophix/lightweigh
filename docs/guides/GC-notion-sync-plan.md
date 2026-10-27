# Notion 文档同步计划（代码库治理 v2 → Notion）

> **背景**：代码库治理机制已进化到"批次/队列/角色/证据/物理开关"完整体系，Notion 仍停留在上一代认知。需同步 5 份核心指南文档到对应 Notion 页面。

---

## 1. 文档映射表

| 新建指南文档 | 对应 Notion 路径 | 操作类型 | 优先级 |
|--------------|------------------|----------|--------|
| `GC-batch-queue-model.md` | `轻衡健康 / 04-研发 / DOC-批次与队列执行模型` | **新建** | P0 |
| `GC-task-board-concurrency.md` | `轻衡健康 / 04-研发 / DOC-任务看板与并发开发` | **覆盖更新**（原有 `DOC-并发开发约定`/`DOC-就绪与完成定义`） | P0 |
| `GC-governance-gates.md` | `轻衡健康 / 04-研发 / DOC-治理门禁与模式开关` | **覆盖更新**（原有 `DOC-编码与架构规范`/`DOC-分支与提交规范`） | P0 |
| `GC-role-system.md` | `轻衡健康 / 00-规范 / DOC-角色系统与写入边界` | **新建** | P0 |
| `GC-dod-evidence.md` | `轻衡健康 / 04-研发 / DOC-完成定义与证据闭环` | **覆盖更新**（原有 `DOC-就绪与完成定义`） | P0 |

---

## 2. 需同步修改的现有 Notion 页面

| Notion 页面 | 修改点 | 关联指南 |
|-------------|--------|----------|
| `DOC-需求到编码链路` | 更新任务字段定义（新增 group_id/batch/role/source/depends_on/target_symbols/overlaps_with）、引用 `GC-task-board-concurrency.md` | `GC-task-board-concurrency.md` |
| `DOC-并发开发约定` | 替换为 `GC-task-board-concurrency.md` 精简版，保留 worktree/锁/会话接力部分 | `GC-task-board-concurrency.md` |
| `DOC-就绪与完成定义` | 替换为 `GC-dod-evidence.md` 精简版，保留 DoR 部分 | `GC-dod-evidence.md` |
| `DOC-编码与架构规范` | 替换为 `GC-governance-gates.md` 精简版，保留架构守卫/模块边界部分 | `GC-governance-gates.md` |
| `DOC-分支与提交规范` | 更新三层开关说明、引用 `GC-governance-gates.md` | `GC-governance-gates.md` |
| `DOC-文档权威与工具分工` | 新增角色系统、批次/队列机制作为"仓库可执行真源"的核心组件 | 所有 5 份指南 |
| `DOC-开发流程总图` | 六阶段流程图中"开发"阶段细化为"内环/交付环/提交环/批次环"四环 | `GC-governance-gates.md` |
| `DOC-测试策略` | 新增三环模型测试分层、受影响测试映射、批次/队列测试计划 | `GC-batch-queue-model.md` + `GC-governance-gates.md` |
| `DOC-代码评审清单` | 新增 `review` 角色强制第三方、评审报告署名校验 | `GC-role-system.md` |
| `00-规范 / 术语表` | 新增：角色、画像、批次、队列、证据、物理/逻辑/角色三层开关 | 所有指南 |

---

## 3. 同步执行清单

### 3.1 P0：新建 5 份核心指南（已生成 Markdown，待粘贴）

- [ ] `GC-batch-queue-model.md` → Notion 新建页面
- [ ] `GC-task-board-concurrency.md` → Notion 新建页面
- [ ] `GC-governance-gates.md` → Notion 新建页面
- [ ] `GC-role-system.md` → Notion 新建页面
- [ ] `GC-dod-evidence.md` → Notion 新建页面

### 3.2 P0：更新 9 个现有页面（精简版 + 引用）

- [ ] `DOC-需求到编码链路` — 任务字段表更新
- [ ] `DOC-并发开发约定` — 替换为精简版
- [ ] `DOC-就绪与完成定义` — 替换为精简版
- [ ] `DOC-编码与架构规范` — 替换为精简版
- [ ] `DOC-分支与提交规范` — 更新开关说明
- [ ] `DOC-文档权威与工具分工` — 补充新机制
- [ ] `DOC-开发流程总图` — 四环模型可视化
- [ ] `DOC-测试策略` — 补充测试分层
- [ ] `DOC-代码评审清单` — 补充 review 角色

### 3.3 P1：术语表同步

- [ ] 术语表新增 15+ 术语定义

### 3.4 P1：索引页面更新

- [ ] `04-研发` 目录页：新增 5 份指南入口
- [ ] `00-规范` 目录页：新增角色系统入口

---

## 4. 同步操作指南（粘贴到 Notion 的最佳实践）

### 4.1 Markdown → Notion 页面

```bash
# 1. 打开对应 Notion 页面（或新建）
# 2. 全选现有内容（Cmd+A）删除
# 3. 粘贴 Markdown 内容（Notion 自动渲染标题/代码块/表格/列表）
# 4. 检查：
#    - 代码块语言标识（bash/python/json）是否识别
#    - 表格对齐是否正确
#    - 交叉引用链接（如 `GC-xxx.md`）需手动改为 Notion 内部链接
# 5. 设置页面图标/封面（建议：📋 指南、🔧 规范、📊 流程）
```

### 4.2 交叉引用链接转换

| Markdown 写法 | Notion 操作 |
|---------------|-------------|
| `[GC-batch-queue-model.md]` | 选中文字 → `[[` 搜索 `DOC-批次与队列执行模型` 回车 |
| `scripts/workflow/wf-batch.sh` | 保留为代码块或加粗，不加链接 |
| `AGENTS.md §2` | 保留文本，不加链接 |

### 4.3 表格处理

- 简单 Markdown 表格 → Notion 原生表格（粘贴后自动转换）
- 复杂表格（含换行/代码块）→ 保留为代码块，或拆分为多个简单表格

---

## 5. 验收标准（同步完成后自检）

| 检查项 | 通过标准 |
|--------|----------|
| **覆盖完整性** | 代码库 `scripts/workflow/` `scripts/governance/` `scripts/dev/` 所有新机制在指南中有对应章节 |
| **术语一致性** | "批次/队列/角色/画像/证据/物理开关/逻辑开关/内环/交付环/提交环/批次环" 全文统一 |
| **命令可运行性** | 文档中所有 `bash scripts/...` 命令在仓库中真实存在且帮助文本一致 |
| **交叉引用可达** | 文档间引用在 Notion 内部链接可跳转，无死链 |
| **与 AGENTS.md 对齐** | §2 三环模型、§3 角色、§10 映射表、§13 钩子与 CI 完全一致 |
| **Notion 结构规范** | 页面在正确目录下、有图标/封面、父子层级正确 |

---

## 6. 同步后的维护规则

| 触发条件 | 必须同步动作 |
|----------|--------------|
| 修改 `.ai/roles.json` | 同步更新 `GC-role-system.md` 角色定义表 |
| 新增/修改 `wf-batch.sh` `wf-queue.sh` `wf-task-board.sh` | 同步更新 `GC-batch-queue-model.md` / `GC-task-board-concurrency.md` |
| 修改 `mode.sh` `gov-mode.sh` `gov-local-ci.sh` `dev-loop.sh` | 同步更新 `GC-governance-gates.md` |
| 修改 `gov-task-done.sh` `gov-evidence-check.sh` `gov-prototype-verify.py` | 同步更新 `GC-dod-evidence.md` |
| 修改 `AGENTS.md` 核心规则 | 同步更新对应指南 + 术语表 |

> **原则**：Notion 是"结论/拍板"层，仓库是"可执行真源"层。**代码库变更先行，文档跟进同步**。每次治理脚本变更 PR 必须包含对应指南文档更新。

---

## 7. 预估工时

| 阶段 | 工时 | 备注 |
|------|------|------|
| 新建 5 份指南粘贴 | 2h | 纯粘贴+格式微调 |
| 更新 9 个现有页面 | 3h | 精简替换+引用修正 |
| 术语表同步 | 1h | 批量新增 |
| 交叉引用链接修正 | 2h | 手工点链接 |
| 自检验收 | 1h | 对照清单 |
| **合计** | **~9h** | 可分 2-3 次完成 |

---

## 8. 责任人与时间线

| 角色 | 负责范围 | 目标完成 |
|------|----------|----------|
| 文档 Owner | 5 份指南新建、9 页面更新 | T+1 天 |
| 术语 Owner | 术语表同步 | T+1 天 |
| 审核人 | 全量自检验收、Notion 结构确认 | T+2 天 |

---

## 9. 关联资源

- 代码库指南源文件：`docs/guides/GC-*.md`（5 份）
- Notion 工作区：`轻衡健康` → `00-规范` / `04-研发`
- 权威规则本体：`AGENTS.md`（根目录）
- 术语表页面：`DOC-术语表`