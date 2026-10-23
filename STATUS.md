# 项目状态

> 由 generate-status.py 自动生成。最后更新: 2026-10-23T14:20:27Z
> 新 AI 启动时: `cat STATUS.md && bash scripts/session/ses-context-load.sh`

---

## 项目快照

| 字段 | 值 |
|------|-----|
| 名称 | 轻衡健康 Android（lightweigh） |
| 版本 | 0.1.0 |
| 文档语言 | zh-CN |
| Git 分支 | feat/settings-language |
| 最后提交 | 1b3b146 docs(lifecycle): sync task graph counts with tasks board (56 pending/23 done) |
| 文档校验 | 运行: `bash scripts/governance/gov-validate.sh` |

## AI 交接

> 上一个 AI session 的上下文。新 AI 启动时**必须阅读此节**。

| 字段 | 值 |
|------|-----|
| 来自 | opencode |
| 时间 | 2026-10-23 |
| 关键文件 | .ai/tasks.json,docs/ai/inbox/ |

**上下文：**

> 完成: T-091 语言与外观设置完成（本地 2 提交，未推送）: 5d50376 feat(settings) 两个呈现式 Screen（LanguageSettings.kt/AppearanceSettings.kt）+ 14 例 JVM 单测 + QkTheme 补 QkColor.Sub/QkRadius.Tile/QkStroke token; 958e16e chore(tasks) 测试注册表登记 + 03_TaskGraph pending 58→57 + tasks.json T-091 in_progress。验证全绿: :app:testDebugUnitTest（新 14 例 0 失败）; gov-prototype-verify T-091 --strict 通过（文案匹配率 48%/57%）; local-ci 6 步中 5 步 PASS（gradlew build / custom-checks / secrets-scan / schema-guard / test-guard），gov-validate 仅剩 2 项 worktree 假阳性（见风险） | 决策: 1) 语言页保留原型全部 4 项含日本語: 原型 view 是文案单一真源，AC 点名的 3 语是其子集，删反而违背 DoD 与原型一致; 2) 原型 emoji 图形（日/月/齿轮）改 Material 线性图标: 全局规则禁源码 emoji，LightMode/DarkMode 直接 import（同 MembersScreen 先例），齿轮复用 QkIcons.Setting，KDoc 已注明该偏差; 3) token 缺失按任务授权补进 QkTheme.kt: Sub=#5D756D（原型 --sub）/ Tile=14（原型外观卡圆角）/ QkStroke 1与2（细边/选中边），两新文件零字面量 dp 与零硬编码色; 4) 两页共用 SettingsNoticeBanner 声明在 LanguageSettings.kt 同包直用，不新建第三个文件（任务限定只建 2 个 target_file）; 5) 原型 data-toast 不在纯渲染层实现，随 Save 意图交宿主 ViewModel; 6) 宁可交付受阻分支也不 --no-verify 绕门禁

**下一步：**

> 等人类解封 worktree 门禁（合并 DEC-062 3811606 + GOV-TEST-005 清理 GIT_* env）后，从本 worktree push feat/settings-language 并按模板 gh pr create，勿 merge 留人审

## 最近动态

最近 5 次提交：

```
1b3b146 docs(lifecycle): sync task graph counts with tasks board (56 pending/23 done)
7af0c2b Merge branch 'fix/worktree-aware-governance' into feat/settings-language (DEC-062 worktree gate fix)
90c8d0d chore(session): handoff after t-091 delivery (push blocked)
23facbd fix(governance): strip hook git env from the test runner (DEC-062)
e061a97 test(governance): sanitize git env in worktree tests
```

## 工程指标

| 指标 | 数量 |
|------|------|
| 决策日志 (DEC) | 62 |
| 设计笔记 (NOTE) | 10 |
| 开发指南 (GC) | 9 |
| 技术参考 (REF) | 1 |

最近决策：

- [DEC-062](docs/ai/decisions/DEC-062.md): DEC-062: 治理检查改为 worktree 感知，使 DEC-050 的并发隔离真正可用
- [DEC-061](docs/ai/decisions/DEC-061.md): DEC-061: Wave 1 新屏以呈现式组件交付，静态文案与运行时读数分离
- [DEC-060](docs/ai/decisions/DEC-060.md): DEC-060: 僵尸任务规则改为按停滞时长判定，不再拦截认领中的任务

最近笔记：

- [NOTE-20261022-002](docs/notes/NOTE-20261022-002.md): 轻衡 App 完整页面清单与原型对照
- [NOTE-20261022-001](docs/notes/NOTE-20261022-001.md): 轻衡 App 设计体系分析与登录原型规范
- [NOTE-20261021-001](docs/notes/NOTE-20261021-001.md): Chipsea_BLE 体脂秤协议逆向分析 — 完整技术笔记

## 健康检查

| 检查项 | 状态 | 详情 |
|-------|------|------|
| 本地门禁 (gov-local-ci) | 通过 | local-ci PASS @ 2026-10-23T14:20:27Z sha=1b3b146 branch=feat/settings-language [gradlew build:ok,gov-validate:ok,gov-custom-checks:ok,gov-secrets-scan:ok,gov-schema-guard:ok,gov-test-guard:ok] |
| GitHub Actions | 无数据 | gh 不可用或无最近 run |
| 文档校验 | 通过 | 来自 local-ci @ 2026-10-23T14:20:27Z sha=1b3b146 |
| Git 钩子 | 未安装 | 安装: bash scripts/bootstrap/bs-install-hooks.sh |

## 反思

> 上次 AI session 的经验教训。新 AI 启动时必读。

1) 语言页保留原型全部 4 项含日本語: 原型 view 是文案单一真源，AC 点名的 3 语是其子集，删反而违背 DoD 与原型一致; 2) 原型 emoji 图形（日/月/齿轮）改 Material 线性图标: 全局规则禁源码 emoji，LightMode/DarkMode 直接 import（同 MembersScreen 先例），齿轮复用 QkIcons.Setting，KDoc 已注明该偏差; 3) token 缺失按任务授权补进 QkTheme.kt: Sub=#5D756D（原型 --sub）/ Tile=14（原型外观卡圆角）/ QkStroke 1与2（细边/选中边），两新文件零字面量 dp 与零硬编码色; 4) 两页共用 SettingsNoticeBanner 声明在 LanguageSettings.kt 同包直用，不新建第三个文件（任务限定只建 2 个 target_file）; 5) 原型 data-toast 不在纯渲染层实现，随 Save 意图交宿主 ViewModel; 6) 宁可交付受阻分支也不 --no-verify 绕门禁

