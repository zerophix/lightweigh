# 项目状态

> 由 generate-status.py 自动生成。最后更新: 2026-10-17T09:40:59Z
> 新 AI 启动时: `cat STATUS.md && bash scripts/session/ses-context-load.sh`

---

## 项目快照

| 字段 | 值 |
|------|-----|
| 名称 | 轻刻健康 Android（qk-health-v2） |
| 版本 | 0.1.0 |
| 文档语言 | zh-CN |
| Git 分支 | fix/nav-and-cloud-push |
| 最后提交 | 29a7c62 chore: merge prototype gap fixes (DEC-026 group K) |
| 文档校验 | 运行: `bash scripts/governance/gov-validate.sh` |

## AI 交接

> 上一个 AI session 的上下文。新 AI 启动时**必须阅读此节**。

| 字段 | 值 |
|------|-----|
| 来自 | ai |
| 时间 | 2026-10-17 |
| 关键文件 | application/src/main/kotlin/com/xcwk/scalehub/application/sync/SyncTrigger.kt,app/src/main/kotlin/com/xcwk/scalehub/App. |

**上下文：**

> 完成: DEC-026 组 K 原型缺口补齐（趋势两卡/我的概览卡/报告 pill，7821b2c→29a7c62）；全项目架构-功能审计，定位 P0 断链=SyncService.push() 生产零调用（只下拉不上传）；本批修复：写用例（测量/健康记录/档案）落库后经 SyncTrigger fire-and-forget 点火 push，容器接线 AppContainer.applicationScope+syncTrigger

**下一步：**

> 云函数后端 ai-chat 与同步接口的联调状态需人工确认；app 层仍无 androidTest（Compose/导航回归零仪器测试）；通知体系与备份规则（allowBackup 无 dataExtractionRules）为下一批候选

## 最近动态

最近 5 次提交：

```
29a7c62 chore: merge prototype gap fixes (DEC-026 group K)
7821b2c feat(ui): close prototype gaps in trend, mine, report
2fc4afd chore: merge quick panel verify and bell closure
79707a1 feat(shell): empty-state toast for bell, verify quick panel
0b8fcee chore: merge report page feedback fix
```

## 工程指标

| 指标 | 数量 |
|------|------|
| 决策日志 (DEC) | 29 |
| 设计笔记 (NOTE) | 7 |
| 开发指南 (GC) | 4 |
| 技术参考 (REF) | 1 |

最近决策：

- [DEC-029](docs/ai/decisions/DEC-029.md): DEC-029: 本地存储三处收口（凭据不入备份 · 键表单一事实源 · 迁移测试接执行）
- [DEC-028](docs/ai/decisions/DEC-028.md): DEC-028: 硬编码治理收口
- [DEC-027](docs/ai/decisions/DEC-027.md): DEC-027: 云同步上传断链修复（P0）与导航口径对齐

最近笔记：

- [NOTE-20261017-001](docs/notes/NOTE-20261017-001.md): 参考项目功能移植对照审查
- [NOTE-20261016-002](docs/notes/NOTE-20261016-002.md): DataRefreshBus 扩展边界
- [NOTE-20261016-001](docs/notes/NOTE-20261016-001.md): NOTE-20261016-001: 云端表 v2 增量 DDL（阶段 1 契约升级）

## 健康检查

| 检查项 | 状态 | 详情 |
|-------|------|------|
| 构建 | 跳过 | skipped |
| 文档校验 | 就绪 | 运行: bash scripts/governance/gov-validate.sh |
| Git 钩子 | 通过 | 安装: bash scripts/bootstrap/bs-install-hooks.sh |

## 反思

> 上次 AI session 的经验教训。新 AI 启动时必读。

审计结论必须用 grep -E（macOS BSD grep 的 \| 在 BRE 下静默返空，差点误报'恢复入口不存在'）；'已注入容器但无人调用'是真实架构断链形态，审计要覆盖调用方而非仅定义

