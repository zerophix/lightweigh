# 项目状态

> 由 generate-status.py 自动生成。最后更新: 2026-10-16T04:23:18Z
> 新 AI 启动时: `cat STATUS.md && bash scripts/session/ses-context-load.sh`

---

## 项目快照

| 字段 | 值 |
|------|-----|
| 名称 | 轻刻健康 Android（qk-health-v2） |
| 版本 | 0.1.0 |
| 文档语言 | zh-CN |
| Git 分支 | master |
| 最后提交 | 81a2763 chore: merge health log port slice |
| 文档校验 | 运行: `bash scripts/governance/gov-validate.sh` |

## AI 交接

> 上一个 AI session 的上下文。新 AI 启动时**必须阅读此节**。

| 字段 | 值 |
|------|-----|
| 来自 | opencode |
| 时间 | 2026-10-16 |
| 关键文件 | .ai/tasks.json,docs/ai/inbox/ |

**上下文：**

> 完成: - 零信任对抗评审三轮闭环：R1 红点归因（c1dde64 隔离构建即红）+ 聚合不变量（DEC-017）；R2 计算输入快照 schema v1→v2 + 不变量溯源 + 单位换算四向量（DEC-018）；R3 撤回金丝雀虚报并补齐 4 条可执行断言 + 修复器/检测器 tests/ 豁免分叉修复（fb1fbad）+ 发布门槛 G1-G5（DEC-020）。终审=条件通过、禁止正式放行，报告入 docs/ai/reviews/20261016_harness-zero-trust/
- UI 架构立约（DEC-021）：UDF+屏幕四件套+读模型下沉+设计系统+垂直切片，写入 02_Architecture §6 并晋升黄金标准，ArchUnit UI 守卫标注随 T-006 上锁
- 移植重构启动（DEC-022）：保障矩阵七项（介质/时机/可验证/盲点）+ 任务组 A-D；组 A 健康记录纵切落库（efd4f76→81a2763）：6 源文件 + 15 行为测试，domain+application 33 项全绿
- 配套：README 交付状态表与多轮反思、T-004/005/006 任务登记、收件箱接力信、R1 隔离工作树清理 | 决策: 终审口径落地：条件通过≠放行，放行只认最终 SHA 的纯净构建(G1)+真实迁移测试(G3)+同 SHA 复验(G2/G4)，文档声明/非最终SHA的绿/迁移SQL存在都不算数。UI 架构选 UDF+四件套+读模型下沉，依据是旧库病根有原文可指（UiRefresh 全局总线、千行 Pages、业务散落页面），先立约后上锁——守卫规则如实标注未落地而不是假装已有。移植忠实性原则：行为分界>枚举表象（weight 在旧库写测量表而非健康记录，Pages.kt:537），每条移植规则必带标注出处行号的行为测试。提交纪律全程执行：路径级暂存+审计清单、DEC/DevNotes/反思同车、R0 每提交刷新反思。

**下一步：**

> 人类协调并行会话提交工具链 WIP 收 G1（T-004）；随后开工 DEC-022 组 B 趋势规则下沉

## 最近动态

最近 5 次提交：

```
81a2763 chore: merge health log port slice
efd4f76 feat(application): port health log domain slice from legacy
d6c3f06 chore: merge UI architecture conventions
6a76180 docs(architecture): codify UI layer conventions for M4
11d7a83 chore: merge adversarial review verdict
```

## 工程指标

| 指标 | 数量 |
|------|------|
| 决策日志 (DEC) | 23 |
| 设计笔记 (NOTE) | 4 |
| 开发指南 (GC) | 4 |
| 技术参考 (REF) | 1 |

最近决策：

- [DEC-023](docs/ai/decisions/DEC-023.md): DEC-023: Android 治理本地化收口
- [DEC-022](docs/ai/decisions/DEC-022.md): DEC-022: 移植重构开发计划与落实保障机制 —— 启动健康记录纵切
- [DEC-021](docs/ai/decisions/DEC-021.md): DEC-021: UI 层架构约定 —— UDF + 屏幕四件套 + 读模型下沉

最近笔记：

- [NOTE-20261014-002](docs/notes/NOTE-20261014-002.md): 模板分叉体检：web-ai-bridge 兼容性验证与升级路径缺口
- [NOTE-20261014-001](docs/notes/NOTE-20261014-001.md): 四个潜伏缺陷的同日发现：证据驱动的治理模板实战复盘
- [NOTE-20260711-001](docs/notes/NOTE-20260711-001.md): 模板维护迭代——清理工作区与收件箱处理

## 健康检查

| 检查项 | 状态 | 详情 |
|-------|------|------|
| 构建 | 跳过 | skipped |
| 文档校验 | 就绪 | 运行: bash scripts/governance/gov-validate.sh |
| Git 钩子 | 通过 | 安装: bash scripts/bootstrap/bs-install-hooks.sh |

## 反思

> 上次 AI session 的经验教训。新 AI 启动时必读。

终审口径落地：条件通过≠放行，放行只认最终 SHA 的纯净构建(G1)+真实迁移测试(G3)+同 SHA 复验(G2/G4)，文档声明/非最终SHA的绿/迁移SQL存在都不算数。UI 架构选 UDF+四件套+读模型下沉，依据是旧库病根有原文可指（UiRefresh 全局总线、千行 Pages、业务散落页面），先立约后上锁——守卫规则如实标注未落地而不是假装已有。移植忠实性原则：行为分界>枚举表象（weight 在旧库写测量表而非健康记录，Pages.kt:537），每条移植规则必带标注出处行号的行为测试。提交纪律全程执行：路径级暂存+审计清单、DEC/DevNotes/反思同车、R0 每提交刷新反思。

