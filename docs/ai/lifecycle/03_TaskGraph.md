# 任务图

> **权威任务板：仓库根目录 `.ai/tasks.json`**（含 next_action、known_risks、各 T-xxx 状态）。
> 本文件是覆盖型摘要，便于人类扫一眼；**详细工单、认领与完成状态以 tasks.json 为准**。
> 更新时间：2026-10-27（DEC-064：移除手写计数，任务板为唯一真源）。

---

## 1. 当前阶段

| 项 | 值 |
|----|-----|
| 已合入主线 | Wave 0/1/2 原型映射 (T-024~T-117 已完成)；DEC-034~044 架构债收口 |
| Room | version 7，schema 1~7 入库 |
| 真机 BLE | 用户确认真机秤可用；Chipsea 混合广播误识别已修（DEC-044） |
| 交互原型 | `design/prototypes/interactive/` 26 个 HTML、192 屏，为 UI/UX 唯一真相源 |
| 任务板 next_action | 17 个原型视图任务 ready，4 个接入任务 blocked |

## 2. 优先级摘要（勿当完整列表）

任务板计数（总数 / completed / pending / 各 wave 条数）**不在本文件手写**，一律以
`.ai/tasks.json` 为准（DEC-064：派生数据不入库，冲突源归零）。

当前状态摘要（snapshot）：completed=73, done=1, pending: 21 — 详情见 tasks.json。

### 已完成（节选）

T-024~T-117 一带多已标记 completed/done（基础组件 / Wave 0 / Wave 1 / Wave 2 / 习惯 / 调试 / 架构债等）。

### Pending（节选，完整列表见 tasks.json）

| 批次 | 任务 | 数量 | 说明 |
|------|------|------|------|
| B-prototype-views | T-118~T-131 | 14 | 原型视图接线 | Wave 0 基础组件已落地 |
| B-integration-1 | T-132 | 1 | 纯接线（DataRecovery/ProfileSetup/PermissionFlow/GuideScreen/AiBoundaries） |
| B-integration-2 | T-133 | 1 | 补 DDD 能力后接线（DeviceRename/MemberDeviceLink/FirmwareManager/FeedbackFlow/HelpCenter/PrivacyAuth） |
| B-integration-3 | T-134 | 1 | 复用复杂能力（ActionPlans/ResultShare/DataConflict/Maintenance/SdkList） |
| B-integration-4 | T-135 | 1 | 死代码裁决（AiCards/MeasureHelp/AccountLifecycle/HealthConnectPlatform） |
| B-ddd-refactor | T-136~T-138 | 3 | DDD 重构 + 功能验收测试 + Evidence 验证 |

**执行波次**：原型视图任务 (T-118~T-131) 和 DDD 重构 (T-136~T-138) 为 `ready` 状态，可直接认领。
接入波任务 (T-132~T-135) 为 `blocked` 状态，等待 DEC-081 补充验收标准。

### 波次说明

| 阶段 | 内容 |
|------|------|
| Wave 0 | 基础组件 — 已完成 |
| Wave 1 | 独立新模块 — 已完成 |
| Wave 2 | 改现有文件 — 已完成 |
| 接入波 | 视图接线 / DDD 重构 / 死代码裁决 — 进行中 |

---

## 3. 依赖与协作注意

```
真机验收（人类） ──► 协议/称重回归（AI + 黄金向量）
文档事实（DEC-045） ──► 新会话上下文可靠
分支保护 / 开源（人类） ──► CI 才成为合入硬门禁
```

- 多会话共享工作树仍有碰撞风险；本机精简 pre-commit 含可选会话归属（需 `current-session.json`）。
- **T-118~T-131**：开工前读 `docs/guides/GC-prototype-driven-dev.md`，skill `prototype-dev` 会自动加载流程；提交前 `gov-prototype-verify.py <任务号> --strict` 必须通过。
- **T-132~T-135**：阻塞中，等待 DEC-081 补充验收标准。

## 4. 如何更新本文件

改任务状态时：**先改 `.ai/tasks.json`**，再视需要重写本节摘要。不要只改本文件导致双真相。
