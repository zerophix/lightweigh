# 项目状态

> 由 generate-status.py 自动生成。最后更新: 2026-10-23T14:17:38Z
> 新 AI 启动时: `cat STATUS.md && bash scripts/session/ses-context-load.sh`

---

## 项目快照

| 字段 | 值 |
|------|-----|
| 名称 | 轻衡健康 Android（lightweigh） |
| 版本 | 0.1.0 |
| 文档语言 | zh-CN |
| Git 分支 | feat/ai-weekly-summary |
| 最后提交 | ecd8609 docs(lifecycle): sync task graph counts with tasks board (56 pending/23 done) |
| 文档校验 | 运行: `bash scripts/governance/gov-validate.sh` |

## AI 交接

> 上一个 AI session 的上下文。新 AI 启动时**必须阅读此节**。

| 字段 | 值 |
|------|-----|
| 来自 | opencode |
| 时间 | 2026-10-23 |
| 关键文件 | .ai/tasks.json,docs/ai/inbox/ |

**上下文：**

> 完成: 1) T-084 新增两个呈现式 Screen（只新建文件，未碰 AiScreen/AiViewModel 等 Wave 2 T-083 归属文件）:
   - app/src/main/kotlin/com/xcwk/scalehub/feature/ai/AiWeeklySummary.kt（原型20 v-weekly: 成员+日期区间 hint / 总结句卡 / 关键变化三格 / 本周亮点 / 下周建议 / 免责 note / 生成下周计划+返回；周数据为空走 Wave 0 的 ui/states QkEmptyState，不渲染假指标）
   - app/src/main/kotlin/com/xcwk/scalehub/feature/ai/MemberPermissionDetail.kt（v-member-detail: 身份行+当前标签 / 权限管理五项开关与说明 / 数据概览（goalProgressPercent 目标进度 39%，区间<=0 返回 0 不除零）/ 关联设备 / note / 查看 AI 周总结+保存权限）
2) 新增 app/src/test/kotlin/com/xcwk/scalehub/feature/Wave1AiWeeklyTest.kt：12 条 JVM 单测（空态分支判定、除零兜底、不补假行），:app:testDebugUnitTest 共 138 全绿；pytest tests/unit 155 全绿
3) gov-prototype-verify.py T-084 --strict 通过（文案匹配率 55% / 54%，无硬编码颜色与裸 dp）
4) 治理侧两处既有文件必改已登记: tests/android-test-registry.yaml 注册新测试（GOV-ATEST-005）；gov-validate --fix 同步 docs/ai/lifecycle/03_TaskGraph.md 计数为 57 pending，并随 .ai/tasks.json（T-084 → in_progress）一起提交
5) 提交: 14c9258 feat(ai) / a146a9b chore(tasks)，均经 ses-claim-staged 声明归属、无 --no-verify | 决策: 1) 呈现式约束: Screen 纯渲染，状态由独立 data class（AiWeeklySummaryUiState / MemberPermissionDetailUiState）整体传入，不读 repository、不建 ViewModel，避免与 T-083 的四件套抢文件（GC-prototype-driven-dev 同模块串行）。
2) 空态与除零: AC 明确「周数据为空走通用空态、不出现除零或假数据」，故把判定抽成可测纯函数（hasData / goalProgressPercent / lostKg / overview*Text），单测覆盖起始<=目标、反向增长、超额达成等边界——这正是「写错了没人会发现」的部分。
3) 视觉: 颜色/圆角/间距全走 QkColor/QkRadius/QkSpacing（含派生 QkSpacing.Xl*2 做 48dp 头像），文案逐字取自原型；原型 .card.soft/.rows/.lbl 等 QkCard 无法直接表达的版式用 token 自组容器，不新造 token（QkTheme 属既有文件，禁改）。
4) 拒绝绕过: gov-validate 两项失败用 CI=1 或 --no-verify 都能「过」，但 DEC-062 已把 CI=1 绕过评为 1/5 分（伪造环境掩盖真实问题），本任务也明令禁止 --no-verify，故选择等待既定修复。
5) tasks.json / 03_TaskGraph.md 一起提交而非只改本地: 计数校验要求两者自洽，只改一边会在合并后让 master 变红。

**下一步：**

> 等 DEC-062（fix/worktree-aware-governance）合入 master 后，完成 T-084 的 local-ci → push → PR（不 merge），随后把 T-084 标 done

## 最近动态

最近 5 次提交：

```
ecd8609 docs(lifecycle): sync task graph counts with tasks board (56 pending/23 done)
13ac9ed Merge branch 'fix/worktree-aware-governance' into feat/ai-weekly-summary (DEC-062 worktree gate fix)
eef269b chore(session): handoff after t-084 delivery pending dec-062
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
| 本地门禁 (gov-local-ci) | 通过 | local-ci PASS @ 2026-10-23T14:17:38Z sha=ecd8609 branch=feat/ai-weekly-summary [gradlew build:ok,gov-validate:ok,gov-custom-checks:ok,gov-secrets-scan:ok,gov-schema-guard:ok,gov-test-guard:ok] |
| GitHub Actions | 无数据 | gh 不可用或无最近 run |
| 文档校验 | 通过 | 来自 local-ci @ 2026-10-23T14:17:38Z sha=ecd8609 |
| Git 钩子 | 未安装 | 安装: bash scripts/bootstrap/bs-install-hooks.sh |

## 反思

> 上次 AI session 的经验教训。新 AI 启动时必读。

1) 呈现式约束: Screen 纯渲染，状态由独立 data class（AiWeeklySummaryUiState / MemberPermissionDetailUiState）整体传入，不读 repository、不建 ViewModel，避免与 T-083 的四件套抢文件（GC-prototype-driven-dev 同模块串行）。
> 2) 空态与除零: AC 明确「周数据为空走通用空态、不出现除零或假数据」，故把判定抽成可测纯函数（hasData / goalProgressPercent / lostKg / overview*Text），单测覆盖起始<=目标、反向增长、超额达成等边界——这正是「写错了没人会发现」的部分。
> 3) 视觉: 颜色/圆角/间距全走 QkColor/QkRadius/QkSpacing（含派生 QkSpacing.Xl*2 做 48dp 头像），文案逐字取自原型；原型 .card.soft/.rows/.lbl 等 QkCard 无法直接表达的版式用 token 自组容器，不新造 token（QkTheme 属既有文件，禁改）。
> 4) 拒绝绕过: gov-validate 两项失败用 CI=1 或 --no-verify 都能「过」，但 DEC-062 已把 CI=1 绕过评为 1/5 分（伪造环境掩盖真实问题），本任务也明令禁止 --no-verify，故选择等待既定修复。
> 5) tasks.json / 03_TaskGraph.md 一起提交而非只改本地: 计数校验要求两者自洽，只改一边会在合并后让 master 变红。

