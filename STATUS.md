# 项目状态

> 由 generate-status.py 自动生成。最后更新: 2026-10-16T03:11:44Z
> 新 AI 启动时: `cat STATUS.md && bash scripts/session/ses-context-load.sh`

---

## 项目快照

| 字段 | 值 |
|------|-----|
| 名称 | 轻刻健康 Android（qk-health-v2） |
| 版本 | 0.1.0 |
| 文档语言 | zh-CN |
| Git 分支 | master |
| 最后提交 | d39b722 chore: merge doc terminology unification |
| 文档校验 | 运行: `bash scripts/governance/gov-validate.sh` |

## AI 交接

> 上一个 AI session 的上下文。新 AI 启动时**必须阅读此节**。

| 字段 | 值 |
|------|-----|
| 来自 | local-ai |
| 时间 | 2026-10-15 |
| 关键文件 | README.md,AGENTS.md,STATUS.md |

**上下文：**

> 安装 ai-project-template 并完成 Android 工程适配，gov-validate 归零

**下一步：**

> M2 云同步与登录（参考旧库 qk-health-android 的 auth/cloud/sync 行为）

## 最近动态

最近 5 次提交：

```
d39b722 chore: merge doc terminology unification
5e945ec docs(governance): unify quantity terminology and fix DEC numbering
89c37b0 chore: merge R2 review fixes
81fea81 feat(domain): persist calc inputs, cap impedance, unit vectors
6fc9105 chore: merge aggregate invariants hardening
```

## 工程指标

| 指标 | 数量 |
|------|------|
| 决策日志 (DEC) | 19 |
| 设计笔记 (NOTE) | 4 |
| 开发指南 (GC) | 4 |
| 技术参考 (REF) | 1 |

最近决策：

- [DEC-019](docs/ai/decisions/DEC-019.md): DEC-019: 统一文档术语与数量口径
- [DEC-018](docs/ai/decisions/DEC-018.md): DEC-018: 对抗评审 R2 —— 不变量溯源、计算输入快照与交付状态标记
- [DEC-017](docs/ai/decisions/DEC-017.md): DEC-017: 测量聚合不变量加固

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

三个坑: 模板setup.sh复制循环不递归丢了modules校验包(装完必须立刻validate); 自己改白名单丢闭括号且修复断言写太巧没修上(修语法用最笨的sed先看现场); 非交互管道下setup.sh问答与conda分支行为不同(先定位卡点再猜)

