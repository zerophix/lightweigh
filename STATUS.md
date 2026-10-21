# 项目状态

> 由 generate-status.py 自动生成。最后更新: 2026-10-21T12:52:26Z
> 新 AI 启动时: `cat STATUS.md && bash scripts/session/ses-context-load.sh`

---

## 项目快照

| 字段 | 值 |
|------|-----|
| 名称 | 轻刻健康 Android（qk-health-v2） |
| 版本 | 0.1.0 |
| 文档语言 | zh-CN |
| Git 分支 | master |
| 最后提交 | 03d5374 chore: merge pre-push fix |
| 文档校验 | 运行: `bash scripts/governance/gov-validate.sh` |

## AI 交接

> 上一个 AI session 的上下文。新 AI 启动时**必须阅读此节**。

| 字段 | 值 |
|------|-----|
| 来自 | opencode |
| 时间 | 2026-10-20 |
| 关键文件 | .ai/tasks.json,docs/ai/inbox/ |

**上下文：**

> 完成: DEC-034 五组修正落地：A 事件类型化（DataChange 替代 Unit 信号、四个 VM 精确订阅、写用例剥离 SyncTrigger 改由 SyncOnLocalWrites 订阅点火、SettingsViewModel 发布下沉）、B 算法映射收敛进 ScaleDevice.algorithmFor、C 三个聚合根补 create 工厂 + Clock 抽象（写用例 currentTimeMillis 清零）、D 仓储 save 显式携带 memberId 并删除聚合里的死字段、G 评审报告自身修正（统计口径与 F-010 撤回）。另完成蓝牙连接修复：芯海 SCANNED_DEVICE 路径、Manifest 旧版权限、前台 Activity 自跟踪、权限门与蓝牙状态广播化。新增 gov-code-check.py 五条源码规则。413 测试全绿，已切分支 refactor/dec-034-architecture-fixes 并提交组 A-D 代码与治理脚本。 | 决策: 评审 F-001 原案「DataRefreshBus 下沉 domain」经评估否决：会给纯模型层引入协程依赖、多一层无收益的间接、并让表现层依赖领域层的总线概念，故改为事件类型化达成同等目标。F-005 未采用评审推荐的方案 A 复合主键，选方案 B：前者牵动 Room 实体、DAO、查询与 schema v3 四处，而真正的问题只是不变量没在接口签名上。F-010 因所述目录不存在予以撤回。

**下一步：**

> review 本分支 refactor/dec-034-architecture-fixes 后决定是否合入 master。

## 最近动态

最近 5 次提交：

```
03d5374 chore: merge pre-push fix
37fc5e6 chore: fix pre-push audit - remove lock file, fix task status
6156b22 feat(auth): merge email/weChat login and feature screens
5dd9510 feat(auth): add email/weChat login, fix habit compile, add screens
29aaf97 Merge T-028 report analysis
```

## 工程指标

| 指标 | 数量 |
|------|------|
| 决策日志 (DEC) | 43 |
| 设计笔记 (NOTE) | 7 |
| 开发指南 (GC) | 4 |
| 技术参考 (REF) | 1 |

最近决策：

- [DEC-043](docs/ai/decisions/DEC-043.md): DEC-043: 对齐参考项目 13 项缺失功能
- [DEC-042](docs/ai/decisions/DEC-042.md): DEC-042: 测量会话生命周期锚定 overlay
- [DEC-041](docs/ai/decisions/DEC-041.md): DEC-041: 统一错误模型与跨模块元数据可见性修复

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

评审 F-001 原案「DataRefreshBus 下沉 domain」经评估否决：会给纯模型层引入协程依赖、多一层无收益的间接、并让表现层依赖领域层的总线概念，故改为事件类型化达成同等目标。F-005 未采用评审推荐的方案 A 复合主键，选方案 B：前者牵动 Room 实体、DAO、查询与 schema v3 四处，而真正的问题只是不变量没在接口签名上。F-010 因所述目录不存在予以撤回。

