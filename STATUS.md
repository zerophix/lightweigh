# 项目状态

> 由 generate-status.py 自动生成。最后更新: 2026-10-20T14:07:37Z
> 新 AI 启动时: `cat STATUS.md && bash scripts/session/ses-context-load.sh`

---

## 项目快照

| 字段 | 值 |
|------|-----|
| 名称 | 轻刻健康 Android（qk-health-v2） |
| 版本 | 0.1.0 |
| 文档语言 | zh-CN |
| Git 分支 | refactor/dec-034-architecture-fixes |
| 最后提交 | 98d61b7 Merge branch 'chore/ci-cancel-fix': branch-aware CI cancellation (DEC-033) |
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
98d61b7 Merge branch 'chore/ci-cancel-fix': branch-aware CI cancellation (DEC-033)
abde43b ci(governance): never cancel an in-flight master build
5586a2b Merge branch 'chore/ci-cost-governance': CI cost governance (DEC-033)
bbc26f3 ci(governance): cut metered CI minutes via scope-gated builds
53cb429 fix(governance): sign relay notes with the detected agent
```

## 工程指标

| 指标 | 数量 |
|------|------|
| 决策日志 (DEC) | 34 |
| 设计笔记 (NOTE) | 7 |
| 开发指南 (GC) | 4 |
| 技术参考 (REF) | 1 |

最近决策：

- [DEC-034](docs/ai/decisions/DEC-034.md): DEC-034: DDD 架构合规性修正——领域事件总线、语义耦合下沉与仓储契约诚实化
- [DEC-033](docs/ai/decisions/DEC-033.md): DEC-033: CI 成本治理——并发去重、文档变更跳过重型构建、构建缓存与超时上限
- [DEC-032](docs/ai/decisions/DEC-032.md): DEC-032: CI 环境跳过阿里云镜像（Gradle 遇 5xx 不回退后续仓库）

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

