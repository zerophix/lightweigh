---
name: prototype-dev
description: >
  原型驱动开发闭环。认领 T-072~T-105 任务、或需要"照着交互原型写 Compose 代码"时使用。
  从任务的 prototype_ref 读原型 view → 映射到 Qk 组件与 token → 编码 → 闭环验证。
  触发词：原型开发、照原型写、prototype task、T-072 等原型映射任务。
---

# 原型驱动开发（Prototype-Driven Dev）

把「原型 HTML → 任务 → Compose 代码 → 验证」串成一条**可被脚本验证的闭环**。
本 skill 是流程入口，真正强制力来自 `scripts/governance/gov-prototype-verify.py`。

## 铁律（不可绕过）

1. **先读任务元数据，再写代码** —— `prototype_ref` 已经告诉你参考哪个原型、哪些 view、写到哪些文件。
2. **不硬编码颜色/间距** —— 原型的值必须映射到 `QkTheme` token（见映射表）。
3. **闭环验证不过就不算完成** —— `gov-prototype-verify.py <task> --strict` 必须通过。
4. **不直访数据层** —— Screen/ViewModel 经 domain 端口，不碰 Room/SDK。

## 完整流程

```
原型驱动开发
  │
  ├── 1. 认领任务
  │     bash scripts/workflow/wf-task-board.sh take T-XXX
  │
  ├── 2. 读任务元数据（原型引用 + 目标文件 + 波次 + 追溯）
  │     python3 scripts/governance/gov-prototype-verify.py T-XXX
  │     → prototype_ref.file / .views / target_files / wave
  │     → module / ac_refs / prd_ref（DOC-CHAIN §3 追溯）
  │
  ├── 3. 确认波次依赖就绪
  │     Wave 0（T-097/098/099 基础组件）必须先完成
  │     否则 Wave 1/2 缺 QkSkeleton / QkPullRefresh / QkSwipeDelete
  │
  ├── 4. 定位原型 view
  │     grep -n 'id="v-<view>"' design/prototypes/interactive/<file>.html
  │     读该 view 块 HTML → 提取布局结构、文案、颜色类名、交互元素
  │
  ├── 5. 映射到 Compose（对照下方映射表）
  │     .card → QkCard / .btn.primary → QkButton / --green → QkColor.Primary
  │     圆角 17px → QkRadius.Card / 边距 14px → QkSpacing.Page
  │
  ├── 6. 编码（四件套）
  │     feature/<name>/{Screen,ViewModel,UiState,Route}.kt
  │     Screen 纯渲染零业务；ViewModel 经 application/domain 端口取数
  │
  ├── 7. 测试
  │     ./gradlew :app:testDebugUnitTest
  │
  ├── 8. 闭环验证（关键）
  │     python3 scripts/governance/gov-prototype-verify.py T-XXX --strict
  │     → 检查：目标文件存在 / 无硬编码颜色 / 原型文案匹配率 ≥30%
  │
  └── 9. 提交
        git add <files> && bash scripts/session/ses-claim-staged.sh
        && git commit -m "feat(<scope>): <subject>"
        && bash scripts/governance/gov-local-ci.sh
        && bash scripts/workflow/wf-task-board.sh done T-XXX
```

## 常用命令

```bash
# 只看任务元数据（原型引用 + 目标文件 + 波次）
python3 scripts/governance/gov-prototype-verify.py T-072

# 闭环验证单任务（提交前必须过）
python3 scripts/governance/gov-prototype-verify.py T-072 --strict

# 全量闭环检查（CI / 收尾用）
python3 scripts/governance/gov-prototype-verify.py --all --strict

# 只校验 schema（不检查代码，用于任务制定阶段）
python3 scripts/governance/gov-prototype-verify.py --schema
```

## 原型 → Compose 映射表

| 原型 CSS 类/结构 | Compose 组件 | 位置 |
|------------------|--------------|------|
| `.card` | `QkCard` | `ui/components/QkComponents.kt` |
| `.row`（列表行） | `QkListRow` | 同上 |
| `.btn.primary` | `QkButton(style=Primary)` | 同上 |
| `.btn.secondary` | `QkButton(style=Secondary)` | 同上 |
| `.btn.ghost` | `QkButton(style=Ghost)` | 同上 |
| `.field input` | `QkTextField` | 同上 |
| `.tag` / `.pill` | `QkChip` / `QkPill` | 同上 |
| `.overlay` + `.dialog` | `QkDialog` | 同上 |
| `.statepage`（空态） | `QkEmptyHint` | 同上 |
| `.skeleton` | `QkSkeleton` | T-099 待建 |
| `.ic`（图标块） | `QkIconBubble` | `ui/components/` |
| `.progress` | `QkProgress` | `QkComponents.kt` |
| `.avatar` | `QkAvatar` | `ui/components/QkAvatar.kt` |
| 顶部导航 | `QkTopBar` | `QkComponents.kt` |
| 底部导航 | `QkBottomNav` | `QkComponents.kt` |
| 图表 | `QkAreaChart` | `ui/components/QkAreaChart.kt` |

## 颜色 token 对照

| 原型 CSS 变量 | Compose token | 值 |
|---------------|---------------|-----|
| `--green` | `QkColor.Primary` | 0xFF159B72 |
| `--green-dark` | `QkColor.PrimaryDark` | 0xFF087B59 |
| `--mint` | `QkColor.Mint` | 0xFFE7F7F0 |
| `--ink` | `QkColor.Fg` | 0xFF20312D |
| `--muted` | `QkColor.Muted` | 0xFF87948F |
| `--bg` | `QkColor.Bg` | 0xFFF4F7F6 |
| `--white` | `QkColor.Card` | 0xFFFFFFFF |
| `--line` | `QkColor.Border` | 0xFFEDF1EF |
| `--orange` | `QkColor.Accent` | 0xFFF3A64A |
| （危险） | `QkColor.Danger` | 0xFFDC4C4C |
| （蓝） | `QkColor.Blue` | 0xFF58A9E8 |
| （紫） | `QkColor.Purple` | 0xFF9C83EC |

## 间距 / 圆角 token

| 用途 | token | 值 |
|------|-------|-----|
| 页面边距 | `QkSpacing.Page` | 14.dp |
| 卡片内边距 | `QkSpacing.CardPad` | 15.dp |
| 卡片间距 | `QkSpacing.CardGap` | 11.dp |
| 卡片圆角 | `QkRadius.Card` | 17.dp |
| 按钮圆角 | `QkRadius.Button` | 12.dp |
| 输入框圆角 | `QkRadius.Field` | 11.dp |

## 闭环验证失败处理

| 失败项 | 处理方式 |
|--------|---------|
| 原型文件不存在 | 检查 `prototype_ref.file` 路径 |
| view 不存在 | 检查 view id 拼写，或原型已改需同步任务 |
| 目标文件不存在 | 实现未完成，继续编码 |
| 硬编码颜色 `Color(0x...)` | 替换为对应 `QkColor` token |
| 原型文案匹配率 <30% | 检查是否参考了原型、文案是否照抄 |

## 能力边界

- 不跳过闭环验证：`--strict` 失败就必须修，不能绕过。
- 不硬编码颜色/间距：原型值必须映射到 `QkTheme` token。
- 不直访数据层：Screen/ViewModel 不碰 Room/SDK，经 domain 端口。
- 不删测试凑绿：测试失败修代码，不删断言。

## 来源

DEC-021（四件套）、DEC-048（精炼 AGENTS）、DEC-053（测试期望）；
原型映射来自 2026-10-23 任务制定会话；闭环验证由 `gov-prototype-verify.py` 强制。
