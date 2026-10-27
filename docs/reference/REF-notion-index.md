# Notion 与仓库对照索引

> 本文登记轻衡健康空间中的页面，并标出仓库里对应的路径。
> 状态：已定稿。更新时间：2026-10-23。
> 真源声明：页面标题与链接以该空间为准。本文件只做对照，不转写页面正文。

轻衡健康下新增页面时，同一会话或同一合并请求里更新本文件。
路径栏为「仅 Notion」时，表示仓库里没有承载该页正文的文件。相关代码或邻近文档写在备注。

---

## 1. 范围

子页树共 74 个页面，含根页面与 00 至 07 八个层目录。计数口径是 2026-10-23 从该空间逐层导出的子页，不含数据库行、评论或已删除页。
仓库扫描另发现 7 个不重复地址，写在文末。单测夹具里的假地址不录入。

```
轻衡健康
    |
    +-- 00-规范 ---- docs/guides 与 docstandard
    +-- 01-产品 ---- lifecycle/01_PRD 与 tasks.json
    +-- 02-设计 ---- design/prototypes
    +-- 03-原型 ---- design/prototypes/interactive
    +-- 04-研发 ---- GC-coding 与 GC-task-acceptance
    +-- 05-测试 ---- lifecycle/05_Test
    +-- 06-发布 ---- 仅 Notion
    +-- 07-运维 ---- 仅 Notion
```

## 2. 根页面

空间首页。工程文档从 docs/index.md 进入。

| 文档标题 | Notion URL | 仓库对应路径 | 备注 |
| --- | --- | --- | --- |
| 轻衡健康 | https://app.notion.com/p/73241bd50f8a832abddb81320fb0503e | docs/index.md | 工程文档入口。空间全文不在仓库。 |

## 3. 00-规范

本层规定产品文档怎么命名、怎么分工，以及需求怎么落到编码。仓库侧的执行文本在开发指南与文档标准里。

| 文档标题 | Notion URL | 仓库对应路径 | 备注 |
| --- | --- | --- | --- |
| 00-规范 | https://app.notion.com/p/1b141bd50f8a82b7a12981c74a8f33cd | 仅 Notion | 层目录。子页即本表后续各行。 |
| DOC-目录与命名规范 | https://app.notion.com/p/9bd41bd50f8a823aa48a01fa9187eb32 | docs/ai/guides/docstandard.md | 仓库侧命名、语言与黄金标准标注。允许写入的路径另见 AGENTS.md。 |
| DOC-需求到编码链路 | https://app.notion.com/p/a7c41bd50f8a82e182af014345563c3d | docs/guides/GC-decision-flow.md | 同链还有 docs/guides/GC-prototype-driven-dev.md 与 docs/guides/GC-task-acceptance.md。 |
| DOC-开发流程总图 | https://app.notion.com/p/efd41bd50f8a8231b7bd01090d64b89b | docs/ai/guides/workflow.md | 仓库侧生命周期步骤。 |
| DOC-文档权威与工具分工 | https://app.notion.com/p/8ba41bd50f8a82f69e0a818d5bf09017 | docs/guides/GC-decision-flow.md | 第 0 层。docs/index.md 有真源声明。 |
| DOC-变更与评审门禁 | https://app.notion.com/p/4f241bd50f8a83b9a785819cfaca10c7 | .github/PULL_REQUEST_TEMPLATE.md | 本地合入命令 scripts/governance/gov-local-ci.sh。 |
| DOC-术语表 | https://app.notion.com/p/cc141bd50f8a82c78c160179b1578806 | docs/reference/index.md | 第 6 节「术语与口径」。 |

## 4. 01-产品

范围、验收、阶段计划与产品决策。产品口径以这些页面为准。

| 文档标题 | Notion URL | 仓库对应路径 | 备注 |
| --- | --- | --- | --- |
| 01-产品 | https://app.notion.com/p/17441bd50f8a8315bed58149f012a27a | 仅 Notion | 层目录。工程镜像入口 docs/ai/lifecycle/01_PRD.md。 |
| DOC-决策与范围索引 | https://app.notion.com/p/e7741bd50f8a8397a0ef81336dc821a8 | docs/ai/decisions/index.md | 工程决策目录。产品决策正文在本页。 |
| PRD-原型板块清单 | https://app.notion.com/p/f9f41bd50f8a83a6851c81b4ccacf64f | design/page-list-TODO.md | 交互稿目录 design/prototypes/interactive/。 |
| PRD-产品需求 | https://app.notion.com/p/72141bd50f8a829ebb8b81442486b288 | docs/ai/lifecycle/01_PRD.md | 该文件写明产品范围与验收以本页为准。 |
| ROAD-MVP阶段计划 | https://app.notion.com/p/92641bd50f8a82339df281a382f00d29 | .ai/tasks.json | 执行工单。阶段摘要 docs/ai/lifecycle/03_TaskGraph.md。 |
| PRD-账号与建档 | https://app.notion.com/p/12341bd50f8a820489fb8162756d0b95 | design/prototypes/interactive/01-login.html | 另见 14-account-lifecycle.html、19-onboarding-guide.html、21-account-recovery.html。代码 feature/auth、feature/guide。 |
| PRD-首页与空态 | https://app.notion.com/p/f2a41bd50f8a827bbbfb01c92e9dba12 | design/prototypes/interactive/11-home-states.html |  |
| PRD-测量与报告 | https://app.notion.com/p/5a641bd50f8a830b82b8010d0d839534 | design/prototypes/interactive/03-measure.html | 另见 12-measure-full.html、16-measure-result.html。代码 feature/measure、feature/report。 |
| PRD-历史与趋势 | https://app.notion.com/p/e7041bd50f8a82c684ff012cb3985880 | design/prototypes/interactive/05-history-trend.html | 另见 17-metric-details.html。代码 feature/history、feature/trend。 |
| PRD-设备管理 | https://app.notion.com/p/adf41bd50f8a820f8a99812bf1c58194 | design/prototypes/interactive/04-device.html | 另见 18-device-setup.html。代码 feature/device。 |
| PRD-目标与提醒 | https://app.notion.com/p/94641bd50f8a83f79934018c8de8d073 | design/prototypes/interactive/13-members-plans.html | 代码 feature/habit。 |
| PRD-帮助与反馈 | https://app.notion.com/p/4f441bd50f8a82c0bbbb019fa3d72ab7 | design/prototypes/interactive/08-sync-network-help.html |  |
| PRD-主路径验收标准 | https://app.notion.com/p/39341bd50f8a8326962a81a73b4f10ef | docs/ai/lifecycle/01_PRD.md | 验收口径在第 4 节。任务级 AC 在 .ai/tasks.json 的 ac_refs。 |
| PRD-BLE权限与异常 | https://app.notion.com/p/6d341bd50f8a82e1a94881441de8a71e | design/prototypes/interactive/18-device-setup.html | 协议笔记 docs/notes/NOTE-20261021-001.md。 |
| PRD-CloudBase同步策略 | https://app.notion.com/p/ba241bd50f8a82c3914e015889a88278 | docs/notes/NOTE-20261016-001.md | 实现 infrastructure/cloud/CloudBaseGateway.kt。相关决策 DEC-028、DEC-040。 |
| MET-主路径埋点 | https://app.notion.com/p/76941bd50f8a8347a310012c50b4602c | 仅 Notion | 仓库无独立埋点说明。 |
| PRD-弱网与离线 | https://app.notion.com/p/82f41bd50f8a82a4bf9e8154686777d1 | design/prototypes/interactive/22-data-conflict.html | 另见 08-sync-network-help.html。 |
| PRD-隐私与首次同意 | https://app.notion.com/p/c5941bd50f8a83d390f601dd02f12fca | design/prototypes/interactive/02-onboarding-permission.html | 授权后状态见 10-privacy-authorization.html。 |
| DEC-品牌与包名口径 | https://app.notion.com/p/88341bd50f8a82c5b211013ed6d7a340 | docs/ai/decisions/DEC-055.md | 工程侧品牌对齐记录。产品口径以本页为准。 |
| DEC-注销与导出SLA | https://app.notion.com/p/daa41bd50f8a8236a85a81775b04e810 | 仅 Notion | 注销流程屏 design/prototypes/interactive/07-mine-settings.html。时限以本页为准。 |
| PRD-崩溃观测与放量 | https://app.notion.com/p/c9841bd50f8a834e90dc8165934e2d17 | 仅 Notion |  |
| PRD-Release与上架包 | https://app.notion.com/p/26b41bd50f8a83d5a9a10168dbc14b02 | 仅 Notion | 持续集成在 .github/workflows/ci.yml，不是上架说明。 |
| PRD-商店上架物料 | https://app.notion.com/p/a4141bd50f8a8233ac57013cd2427b37 | 仅 Notion |  |
| DOC-品牌字符串清查表 | https://app.notion.com/p/20541bd50f8a837da01c814ffe2c0d22 | 仅 Notion | 相关工程决策 docs/ai/decisions/DEC-055.md。 |
| DOC-硬件SKU白名单 | https://app.notion.com/p/17841bd50f8a8202a4b581f58004d137 | 仅 Notion | 已接入协议在 domain/protocol。 |
| DOC-真机会签清单 | https://app.notion.com/p/06b41bd50f8a827b87bc81e4c87f8651 | 仅 Notion |  |
| DOC-商店物料定稿清单 | https://app.notion.com/p/9f141bd50f8a823db9c3015023c1f44a | 仅 Notion |  |
| PRD-数据导出 | https://app.notion.com/p/33841bd50f8a838a9998011f81f6a60d | design/prototypes/interactive/16-measure-result.html |  |
| PRD-个人中心与设置 | https://app.notion.com/p/a8641bd50f8a8294bdb2811e8f1425ca | design/prototypes/interactive/07-mine-settings.html | 另见 24-remaining-gaps.html。代码 feature/mine、feature/settings。 |
| PRD-隐私中心 | https://app.notion.com/p/0cd41bd50f8a8261bcda018c55491b37 | design/prototypes/interactive/10-privacy-authorization.html |  |
| DEC-Phase1范围边界 | https://app.notion.com/p/11741bd50f8a827bab7981a7ddde66ba | 仅 Notion | 工程侧非目标见 docs/ai/lifecycle/01_PRD.md 第 5 节。 |
| PRD-客服与反馈闭环 | https://app.notion.com/p/2c541bd50f8a839b865e0125fb2d41fc | design/prototypes/interactive/08-sync-network-help.html | 闭环时限与责任以本页为准。 |
| DOC-库表设计 | https://app.notion.com/p/27f41bd50f8a8272859f81fa001f9cfb | infrastructure/schemas/com.xcwk.scalehub.infrastructure.persistent.AppDatabase/ | 当前 Room 快照。设计说明以本页为准。变更守卫 DEC-052。 |
| DEC-库表目标态默认 | https://app.notion.com/p/18241bd50f8a82d294cd010ce5dd102c | 仅 Notion | 已入库快照至 7.json。怎么改见 docs/ai/decisions/DEC-052.md。 |

## 5. 02-设计

设计分工与缺屏。交互稿在 design/prototypes/。

| 文档标题 | Notion URL | 仓库对应路径 | 备注 |
| --- | --- | --- | --- |
| 02-设计 | https://app.notion.com/p/38041bd50f8a82c3b5e081ea77477c39 | 仅 Notion | 层目录。稿件在 design/prototypes/。 |
| DES-Figma与HTML分工 | https://app.notion.com/p/f5041bd50f8a820fb4f881c276208bac | design/prototypes/interactive/index.html | 静态视觉稿 design/prototypes/index.html。 |
| DES-Figma缺屏清单 | https://app.notion.com/p/eac41bd50f8a823a9cbe816f74293a51 | design/page-list-TODO.md |  |
| DES-设计评审清单 | https://app.notion.com/p/09041bd50f8a8287ae7a817ae11fde1c | 仅 Notion |  |
| DES-Token与组件引用 | https://app.notion.com/p/92041bd50f8a837d85f781be2e813b14 | design/prototypes/theme.css | 代码 token app/src/main/kotlin/com/xcwk/scalehub/ui/theme/QkTheme.kt。 |

## 6. 03-原型

各流程的界面说明。可点原型在 design/prototypes/interactive/。

| 文档标题 | Notion URL | 仓库对应路径 | 备注 |
| --- | --- | --- | --- |
| 03-原型 | https://app.notion.com/p/ded41bd50f8a83c18b9781ce5a6302fb | design/prototypes/interactive/index.html | 26 个交互流程、192 可点屏。 |
| UI-启动引导 | https://app.notion.com/p/f4641bd50f8a826193bb0186db03dad7 | design/prototypes/interactive/02-onboarding-permission.html | 另见 19-onboarding-guide.html。代码 feature/guide。 |
| UI-测量 | https://app.notion.com/p/c3141bd50f8a83958f6801283f8124ab | design/prototypes/interactive/03-measure.html | 另见 12-measure-full.html、16-measure-result.html。代码 feature/measure。 |
| UI-记录 | https://app.notion.com/p/e7141bd50f8a83a3a18181551cf459a9 | design/prototypes/interactive/05-history-trend.html | 代码 feature/history、feature/record。 |
| UI-身体指标 | https://app.notion.com/p/43e41bd50f8a83c4ab18019ce48e4e07 | design/prototypes/interactive/17-metric-details.html | 代码 feature/trend。 |
| UI-行动计划 | https://app.notion.com/p/8a141bd50f8a838a9ced81ada6177356 | design/prototypes/interactive/13-members-plans.html |  |
| UI-个人中心 | https://app.notion.com/p/82d41bd50f8a82c79f9f81ca3fcf196f | design/prototypes/interactive/07-mine-settings.html | 代码 feature/mine。 |
| UI-家庭成员 | https://app.notion.com/p/46b41bd50f8a83de8a4e01c8cc26e20c | design/prototypes/interactive/23-family-invite.html | 另见 13-members-plans.html。代码 feature/member。 |
| UI-智能健康 | https://app.notion.com/p/f7f41bd50f8a822fac6881d8de4c90e0 | design/prototypes/interactive/06-ai.html | 另见 20-ai-members.html、25-ai-boundaries.html。代码 feature/ai。 |

## 7. 04-研发

编码、完成定义、分支与并发。仓库指南是执行文本。

| 文档标题 | Notion URL | 仓库对应路径 | 备注 |
| --- | --- | --- | --- |
| 04-研发 | https://app.notion.com/p/2ec41bd50f8a8242b9b70112ad21fc69 | 仅 Notion | 层目录。 |
| DOC-编码与架构规范 | https://app.notion.com/p/c1a41bd50f8a83979a4701b6f0063c16 | docs/guides/GC-coding-conventions.md | 架构事实 docs/architecture/index.md。 |
| DOC-就绪与完成定义 | https://app.notion.com/p/1d141bd50f8a837795cd019cb5adc969 | docs/guides/GC-task-acceptance.md |  |
| DOC-分支与提交规范 | https://app.notion.com/p/7e341bd50f8a83eab980818b1db0c4b4 | AGENTS.md | 推送前检查 scripts/governance/gov-pre-push。 |
| DOC-代码评审清单 | https://app.notion.com/p/f4041bd50f8a8304975d81888798eb9d | .github/PULL_REQUEST_TEMPLATE.md |  |
| DOC-并发开发约定 | https://app.notion.com/p/0b941bd50f8a8293926081b93874f350 | docs/guides/GC-multi-agent-collab.md |  |

## 8. 05-测试

测试策略的工程摘要在生命周期文档。缺陷与会签仍在本层。

| 文档标题 | Notion URL | 仓库对应路径 | 备注 |
| --- | --- | --- | --- |
| 05-测试 | https://app.notion.com/p/59b41bd50f8a834daa670184befb43a4 | 仅 Notion | 层目录。 |
| DOC-测试策略 | https://app.notion.com/p/36e41bd50f8a83e684df01bc66a4cbab | docs/ai/lifecycle/05_Test.md |  |
| DOC-缺陷管理 | https://app.notion.com/p/8b941bd50f8a8345b59381fb2b328ced | 仅 Notion |  |
| QA-主路径测试矩阵 | https://app.notion.com/p/d5341bd50f8a828fbc01813a3ecdb571 | 仅 Notion | 自动化用例不按此矩阵分文件。 |
| DOC-准出与会签入口 | https://app.notion.com/p/14641bd50f8a82b4ad3201aa0729c025 | 仅 Notion |  |

## 9. 06-发布

上架与发布清单。仓库里的工作流只覆盖持续集成。

| 文档标题 | Notion URL | 仓库对应路径 | 备注 |
| --- | --- | --- | --- |
| 06-发布 | https://app.notion.com/p/4bc41bd50f8a838bbc2581bfba64e6d4 | 仅 Notion | 层目录。 |
| DOC-发布流程 | https://app.notion.com/p/7dd41bd50f8a82e2a25401de09b568a7 | 仅 Notion | 持续集成见 .github/workflows/ci.yml。 |
| DOC-上线完备清单 | https://app.notion.com/p/a1d41bd50f8a838ab6ae01ed402f2008 | 仅 Notion |  |

## 10. 07-运维

监控、告警与反馈回流的运行手册放在本层。仓库侧没有单独的运维手册。

| 文档标题 | Notion URL | 仓库对应路径 | 备注 |
| --- | --- | --- | --- |
| 07-运维 | https://app.notion.com/p/d4f41bd50f8a83cb82d38124b53fcf38 | 仅 Notion | 层目录。 |
| DOC-监控与反馈闭环 | https://app.notion.com/p/77741bd50f8a837b9d6701a9a9ad3186 | 仅 Notion |  |
| DOC-SLO与告警响应 | https://app.notion.com/p/76f41bd50f8a834792cc8157f5ee562f | 仅 Notion |  |
| DOC-反馈回流模板 | https://app.notion.com/p/ba241bd50f8a82dbb75081469151f4e4 | 仅 Notion |  |

## 11. 仓库扫描补充

下列地址出现在 `.ai/tasks.json` 的 `prd_ref`，没有出现在上面的子页树。标题未随链接入库，故不猜测页面名称。
单测 `tests/unit/test_prototype_governance.py` 使用 `https://app.notion.com/p/abc` 作为夹具，不是真实页面，不列入。

| 文档标题 | Notion URL | 仓库对应路径 | 备注 |
| --- | --- | --- | --- |
| 待确认标题 | https://app.notion.com/p/40141bd50f8a8131aad6f95b74dcf436 | .ai/tasks.json | 子页树未收录。引用任务：T-072、T-073、T-076、T-081、T-082、T-083、T-084、T-085、T-086、T-087、T-088、T-100。 |
| 待确认标题 | https://app.notion.com/p/40241bd50f8a81978bd4c99ada828be1 | .ai/tasks.json | 子页树未收录。引用任务：T-074、T-089、T-090、T-091、T-092、T-101。 |
| 待确认标题 | https://app.notion.com/p/40141bd50f8a81cdbbedcb4fb9228eca | .ai/tasks.json | 子页树未收录。引用任务：T-075、T-096。 |
| 待确认标题 | https://app.notion.com/p/40141bd50f8a818fbdbcc0396b31763c | .ai/tasks.json | 子页树未收录。引用任务：T-077、T-078、T-097、T-098、T-099。 |
| 待确认标题 | https://app.notion.com/p/40141bd50f8a815baa74ee7f26890aba | .ai/tasks.json | 子页树未收录。引用任务：T-079、T-080、T-103、T-104、T-105。 |
| 待确认标题 | https://app.notion.com/p/40141bd50f8a8134bec9f5290e1bd659 | .ai/tasks.json | 子页树未收录。引用任务：T-093、T-094、T-102。 |
| 待确认标题 | https://app.notion.com/p/40141bd50f8a811b8d8dc68bc9b789e7 | .ai/tasks.json | 子页树未收录。引用任务：T-095。 |

## 来源: docs/index.md

对照依据为 2026-10-23 导出的轻衡健康子页树，以及当日仓库内 `app.notion.com` 扫描。