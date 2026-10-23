# 指标

> 轻衡健康 Android（lightweigh）的量化指标，数值实时计算、随代码自动更新。
> 覆盖型文档：由 gov-update-metrics.sh 重写（本地化钩子 metrics-hooks/00-document.sh）。

---

## 1. 代码规模

| 指标 | 当前值 | 说明 |
|------|--------|------|
| Gradle 模块 | 4 | domain / application / infrastructure / app |
| 源文件 | 201 | Kotlin + Java（各模块 src/main） |
| 源码行数 | 22602 | 四模块合计 |
| 测试文件 | 57 | 各模块 src/test |
| 测试代码行数 | 8377 | |
| 测试 / 源码行比 | 37% | 测试行 ÷ 源码行 |

## 2. 领域资产

| 指标 | 当前值 | 说明 |
|------|--------|------|
| 协议实现 | 3 | okok-1 / okok-broadcast-1 / chipsea-cloud-1 |
| 体成分算法 | 2 | builtin-1 / okok-bia-1 |
| 黄金向量 | 6 | 体成分跨端一致性向量（domain/src/test/resources/golden） |
| 领域层纯度 | 100% | :domain 不引入 Android 依赖，编译期强制 |

## 3. 质量门禁

| 指标 | 当前值 | 说明 |
|------|--------|------|
| 文档校验违规 | 2 | gov-validate.sh |
| 单元测试方法 | 444 | @Test（debug / release 变体各执行一遍） |

## 4. 文档健康

| 指标 | 当前值 | 说明 |
|------|--------|------|
| 决策日志 (DEC) | 61 | 已记录的工程决策 |
| 设计笔记 (NOTE) | 10 | 讨论 / 设计笔记 |
| 开发指南 (GC) | 9 | 已发布的规范指南 |
| 技术参考 (REF) | 1 | 已发布的技术参考 |
| 生命周期文档 | 6/6 | PRD / 架构 / 任务图 / 开发笔记 / 测试 / 指标 |
| 黄金标准 | 5/5 | architecture / guides / reference / changelog / notes |

## 5. 迭代进度

| 指标 | 当前值 |
|------|--------|
| 当前迭代 | — |
| 任务板条目 | 83 |

---

> 自动更新于 2026-10-23
