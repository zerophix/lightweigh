# 原型驱动开发指南（并发 AI 必读）

> 本文件回答三个问题：任务按什么顺序做、原型怎么参考、代码按什么架构写。
> 任何领取 T-072~T-105 任务的 AI 会话，开工前必须读完本文件。DEC-048 口径。

---

## 0. 强制力机制（不是纯文档约定）

本规范由三层机制强制执行，AI 不能跳过：

| 层 | 介质 | 作用 |
|----|------|------|
| 引导 | `.opencode/skills/prototype-dev/SKILL.md` | AI 认领任务时自动加载流程与映射表 |
| 元数据 | `.ai/tasks.json` 的 `prototype_ref` + `module`/`ac_refs`/`prd_ref` 字段 | 任务携带原型引用、模块编号、AC 追溯、上层文档链接 |
| Schema 校验 | `gov-validate.py` 的 GOV-PTASK-001~007 检查 | 每次 validate 校验 34 个任务 schema |
| 闭环验证 | `gov-prototype-verify.py --strict` | 检查目标文件存在/无硬编码颜色/文案匹配率 |

### 快速命令

```bash
# 认领任务并读元数据
bash scripts/workflow/wf-task-board.sh take T-XXX
python3 scripts/governance/gov-prototype-verify.py T-XXX

# 闭环验证（提交前必须过）
python3 scripts/governance/gov-prototype-verify.py T-XXX --strict

# 全量闭环检查（CI / 收尾）
python3 scripts/governance/gov-prototype-verify.py --all --strict

# 只校验 schema（任务制定阶段）
python3 scripts/governance/gov-prototype-verify.py --schema
```

---

## 1. 任务波次（执行顺序）

任务板 `.ai/tasks.json` 中 T-072~T-105 共 34 个原型映射任务，按依赖关系分 **3 个执行波次**。
波次是任务的**顶层 `wave` 字段**（数值 0/1/2），由 GOV-PTASK-004 校验，不是写在 notes 里。

> 另有一批 T-059~T-071（13 条功能缺口）编排上称"Wave 3"，但它们不带 `prototype_ref`
> 也没有 `wave` 字段——它们依赖 Wave 1 完成，属于另一批次，不计入下表 34 条。

### Wave 0：基础组件（必须最先做）

其他任务会复用这些组件。Wave 0 未完成前，不要开 Wave 1/2。

| 任务 | 产出 | 被谁复用 |
|------|------|----------|
| T-098 | `ui/components/QkPullRefresh.kt` `QkSwipeDelete.kt` `QkInfiniteScroll.kt` `QkSearchBar.kt` | 历史、趋势、成员列表 |
| T-099 | `ui/components/QkSkeleton.kt` `QkLongPressMenu.kt` | 各模块加载态、长按操作 |
| T-097 | `states/ErrorStates.kt`（16 种通用状态页） | 所有模块的空/错/加载态 |

### Wave 1：独立新模块（全并行，只新建文件）

每个任务只**新建** feature 目录下的文件，不改现有文件。不同任务文件不重叠，可安全并行。
模块间无依赖，领取任意一个即可开工。

任务清单（按模块）：
- trend: T-082
- history: T-081
- member: T-087, T-088
- ai: T-084, T-085
- privacy: T-096
- sync: T-093, T-094, T-102
- settings: T-091, T-092
- mine: T-089, T-090, T-101
- auth: T-073, T-074
- guide: T-075, T-100
- measure: T-078
- device: T-079, T-080, T-103, T-105

### Wave 2：改现有文件（同模块内必须串行）

这些任务修改现有 Screen/ViewModel，同模块的任务**不能并行**（会冲突）。
不同模块之间仍可并行。

| 任务 | 改动文件 | 串行约束 |
|------|----------|----------|
| T-072 auth | `AuthScreen.kt` `AuthUiState.kt` | auth 模块内串行 |
| T-076 guide | `GuideScreen.kt` | guide 模块内串行 |
| T-077 measure | `MeasureScreen.kt` `MeasureViewModel.kt` | measure 模块内串行 |
| T-083 ai | `AiScreen.kt` `AiUiState.kt` `AiViewModel.kt` | ai 模块内串行 |
| T-086 member | `MembersScreen.kt` | member 模块内串行 |
| T-104 device | `DeviceScreen.kt` `DeviceUiState.kt` `DeviceViewModel.kt` | device 模块内串行 |
| T-095 home | `MainShell.kt` `home/HomeStates.kt` | 单任务，无冲突 |

### Wave 3：功能缺口（P0 → P1 → P2）

T-059~T-071，是产品功能缺口而非原型映射。依赖 Wave 1 的基础页面。
按 P0 → P1 → P2 顺序做。

---

## 2. 原型怎么参考

### 2.1 原型文件位置

```
design/prototypes/interactive/    # 26 个交互原型 HTML（主要参考）
design/prototypes/html-screens/   # 97 个单屏 HTML（按模块前缀命名，补充细节屏）
design/prototypes/theme.css       # 设计系统 token（单一来源）
design/prototypes/interactive/shell.css
design/prototypes/interactive/app.js
design/prototypes/figma-renders/  # Figma 官方 60 屏渲染图（视觉对照）
```

### 2.2 任务描述里的原型引用格式

任务 `notes` 中形如：
```
原型01 v-email/v-wechat/v-guest · 新增 auth/EmailLogin.kt
```
含义：
- `原型01` → 文件 `design/prototypes/interactive/01-login.html`
- `v-email` → 该 HTML 里 `id="v-email"` 的 `<div class="view">` 块
- `新增 auth/EmailLogin.kt` → 目标文件

### 2.3 读原型的步骤

1. 打开任务描述指定的 HTML 文件
2. 用 `grep -n 'id="v-<view-id>"' <file>` 定位 view 块的起始行
3. 读该 view 块的 HTML，理解：布局结构、文案、颜色类名、交互元素
4. 颜色类名对照 `theme.css` 里的 CSS 变量（如 `--green` → `QkColor.Primary`）
5. 布局对照 Compose 组件（如 `.card` → `QkCard`，`.row` → `QkListRow`）

### 2.4 原型 → Compose 映射速查

| 原型 CSS 类/结构 | Compose 组件 | 位置 |
|------------------|--------------|------|
| `.card` | `QkCard` | `ui/components/QkComponents.kt` |
| `.row`（列表行） | `QkListRow` | 同上 |
| `.btn.primary` | `QkButton(style=Primary)` | 同上 |
| `.btn.secondary` | `QkButton(style=Secondary)` | 同上 |
| `.btn.ghost` | `QkButton(style=Ghost)` | 同上 |
| `.field input` | `QkTextField` | 同上 |
| `.sw`（开关） | `QkPill` 或 `Switch` | 同上 |
| `.tag` / `.pill` | `QkChip` / `QkPill` | 同上 |
| `.overlay` + `.dialog` | `QkDialog`（或 `AlertDialog`） | 同上 |
| `.statepage`（空态） | `QkEmptyHint` | 同上 |
| `.skeleton` | `QkSkeleton`（T-099 产出） | 待建 |
| `.ic`（图标块） | `QkIconBubble` | `ui/components/` |
| `.progress` | `QkProgress` | `QkComponents.kt` |
| `.avatar` | `QkAvatar` | `ui/components/QkAvatar.kt` |
| 顶部导航 | `QkTopBar` | `QkComponents.kt` |
| 底部导航 | `QkBottomNav` | `QkComponents.kt` |
| 图表 | `QkAreaChart` / `TrendLineChart` | `ui/components/` / `feature/trend/` |
| 颜色 `--green` | `QkColor.Primary` | `ui/theme/QkTheme.kt` |
| 颜色 `--ink` | `QkColor.Fg` | 同上 |
| 颜色 `--muted` | `QkColor.Muted` | 同上 |
| 颜色 `--bg` | `QkColor.Bg` | 同上 |
| 颜色 `--orange` | `QkColor.Accent` | 同上 |
| 圆角 17px | `QkRadius.Card` | 同上 |
| 边距 14px | `QkSpacing.Page` | 同上 |

**原则**：原型里出现的颜色/圆角/间距，一律用 `QkTheme.kt` 里的 token，禁止硬编码 `Color(0x...)` 或 `14.dp`。
如果 token 里没有，先在 `QkTheme.kt` 补一个，再用。

---

## 3. 代码架构规范

### 3.1 模块分层（不可违反）

```
app → application → domain ← infrastructure
```

| 模块 | 放什么 | 禁止 |
|------|--------|------|
| `:domain` | 实体、端口（interface）、领域规则 | 零 Android / Room / UI 依赖 |
| `:application` | 用例 / 应用服务，依赖 domain 端口 | 零 UI |
| `:infrastructure` | 端口实现：Room、网络、蓝牙、Preference | 零 UI |
| `:app` | 组合根、`feature/*` UI、ArchUnit | 不直接访问 Room/SDK |

包根：`com.xcwk.scalehub`

### 3.2 屏幕四件套（DEC-021）

新页面 = 新目录 + 四件套：

```
app/src/main/kotlin/com/xcwk/scalehub/feature/<name>/
  <Name>Screen.kt      # Compose UI，零业务逻辑
  <Name>ViewModel.kt   # 编排用例 / 读模型
  <Name>UiState.kt     # 不可变状态 data class
  <Name>Route.kt       # 入口，收集 StateFlow → 传给 Screen
```

Route 模式（照抄 HabitRoute.kt）：

```kotlin
@Composable
fun XxxRoute(
    onBack: () -> Unit = {},
    vm: XxxViewModel = viewModel(factory = XxxViewModel.Factory),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    XxxScreen(state = state, onIntent = vm::onIntent, onBack = onBack)
}
```

Screen 模式：`@Composable fun XxxScreen(state: XxxUiState, onIntent: (XxxIntent) -> Unit, ...)`，
纯渲染，不创建 ViewModel，不访问 repository。

ViewModel 模式：`object Factory : ViewModelProvider.Factory`，通过 `App.container` 拿端口。

### 3.3 UI 禁令

1. Screen/ViewModel **禁止**直接访问 Room、SharedPreferences、蓝牙 SDK
2. 数据经 `application` 用例或 `domain` 端口获取
3. 颜色/圆角/间距**禁止**硬编码，用 `QkColor` / `QkRadius` / `QkSpacing`
4. 新组件优先复用 `ui/components/Qk*`，没有再新建
5. ArchUnit 在 `:app` 测试中守分层，违规会红

### 3.4 测试期望

- ViewModel：JVM 单测（`./gradlew :app:testDebugUnitTest`）
- 领域规则：`:domain:test`
- 改某模块 `src/main` 时，同 PR 宜含该模块 `src/test`（DEC-053，CI 警告不失败）
- 黄金向量 / Room schema 变更必须有 DEC + 显式说明

### 3.5 合入流程

```bash
# 1. 认领任务
bash scripts/workflow/wf-task-board.sh take T-XXX

# 2. 多会话：开 worktree
bash scripts/session/ses-worktree.sh add feat/<topic>
cd <worktree>
bash scripts/session/ses-session-begin.sh

# 3. 编码 + 测试
./gradlew :app:testDebugUnitTest

# 4. 提交（先声明归属）
git add <files>
bash scripts/session/ses-claim-staged.sh
git commit -m "feat(<scope>): <subject>"

# 5. 合入门禁
bash scripts/governance/gov-local-ci.sh   # 全绿才算过

# 6. PR + 会话结束
# ... push, PR ...
bash scripts/session/ses-session-end.sh
```

### 3.6 提交规范

```
<type>(<scope>): <subject>     # 英文，标题 ≤72 字符
```
- type: feat / fix / docs / test / chore / ci / refactor
- scope: 模块名（auth / measure / device / ai / member / trend / history / settings / mine / sync / home / states / components）
- pre-commit 要求标题纯英文、正文每行 ≤72 字符

---

## 4. 开工前检查清单

领取任务后，按顺序确认：

1. [ ] 读完本文件
2. [ ] 读任务 `acceptance` 字段（若有）
3. [ ] `python3 scripts/governance/gov-prototype-verify.py T-XXX` 读元数据
4. [ ] `grep -n 'id="v-<view-id>"' <原型文件>` 定位原型 view
5. [ ] 读原型 view 块，理解布局/文案/颜色
6. [ ] 确认目标文件不与其他 in_progress 任务冲突
7. [ ] Wave 0 组件若被本任务依赖，确认已存在
8. [ ] 开工编码
9. [ ] 跑 `./gradlew :app:testDebugUnitTest`
10. [ ] 跑 `python3 scripts/governance/gov-prototype-verify.py T-XXX --strict`
11. [ ] 跑 `bash scripts/governance/gov-local-ci.sh`
12. [ ] 提交 + push

---

## 来源

DEC-021（四件套）、DEC-048（精炼 AGENTS）、DEC-053（测试期望）、DEC-054（多会话守卫）；
原型映射与波次划分来自 2026-10-23 任务制定会话。
