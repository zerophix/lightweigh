# 开发笔记

> 本迭代（M2 前置硬缺口闭环）的技术笔记、问题与发现。
> 覆盖型文档：每次迭代重写，保留本次迭代的技术细节。

---

## 0. 本迭代概览（DEC-036）

**目标**：完成 20261021_m2-prereq-checklist 核验出的 F-001~F-006 六项硬缺口，为 M2 业务逻辑开发扫清障碍。

**完成任务**：
- T-010（F-001 Critical）：SyncService 补水位线/对账/幂等
- T-011（F-002 Critical）：多成员同步隔离测试
- T-012（F-003 Critical）：全链路端到端测试
- T-014（F-005 High）：AppContainer 端口化 + ArchUnit 负例
- T-015（F-006 High）：文档事实对齐

**测试覆盖增长**：
- `SyncServiceTest`：14 → 22 个单元测试方法（新增 8 个：水位线 6 + 多成员隔离 4，部分合并）
- `FullChainSyncTest`：新增 7 个端到端场景
- `ArchitectureTest`：13 → 14 条规则（新增 `appcontainer公开属性不得暴露infra具体类型`）

**数据库迁移**：v5 → v6，新增 `sync_state` 表（复合主键 `(tableName, memberId)`）

---

## 1. 技术决策记录

- **包坐标一次性收敛（DEC-011）**：脚手架占位包 com.yourco.app → 产品包 com.qingke.health →
  公司统一名 com.xcwk.scalehub。产品名与目录名不随公司名变更，产品身份与公司法人身份解耦。
- **命名空间迁移以 Python 做确定性重放**：见问题 1，用 `os.walk` 逐文件归位而非 shell 字符串替换。
- **纯 JVM 模块作为架构纪律**：`:domain` 与 `:application` 不引入 AGP，Android 依赖直接编译
  失败——比运行期检查更硬的边界。
- **生命周期文档按迭代覆盖重写**：01~06 属覆盖型文档，本次从模板内容改写为真实工程描述。
- **黄金标准经 promote 晋升**：architecture/index.md 不直接编辑，由 wf-promote.sh 从
  02_Architecture 生成并附来源标注。
- **构建可用性属迭代交付物（DEC-015）**："能跑通一次 `./gradlew build`" 与代码同等重要，
  否则依赖组合失效会长期潜伏且无 diff 可查。
- **水位线设计（DEC-036 组 A）**：`sync_state` 表复合主键 `(tableName, memberId)`，
  每表每成员独立水位线。`pushedWatermark`/`pulledWatermark` 分开记录，
  **先推后记**（`upsert` 返回 `true` 才更新），崩溃可重推但不丢。
- **增量推送边界（DEC-036 组 A）**：`between(watermark + 1, MAX)`——`between` 是 inclusive on from，
  `+1` 排除已推的行。幂等性由 PostgREST `on_conflict=merge-duplicates` 兜底，本地水位线只是优化闸门。
- **AppContainer 端口化（DEC-036 组 D）**：所有对外暴露的 `val` 必须是端口类型
  （`MemberProfileReader/Writer`、`DeviceRepository`、`BlePort`），具体实现只在容器内部持有。
  ArchUnit 负例防止回退。

## 2. 遇到的问题与解决方案

### 问题 1：包路径被写成字面反斜杠目录

- 现象：包迁移后源码出现在形如 `com\/xcwk/scalehub/` 的字面反斜杠目录中，且部分文件 package
  声明已改、部分未改，工程无法编译。
- 根因：以 shell 参数替换做包名→路径转换时，转义斜杠被当作字面字符写入目录名；"移动目录"与
  "改 package 声明"两阶段未打成原子操作。
- 解决：用 Python `os.walk` 定位所有含反斜杠的目录，逐文件移动到正斜杠路径并统一 package/import，
  再删除怪目录（9 处）；Room schema 快照删除后按新包名重新导出。构建成功、测试全绿。
- 防止复发：包名→目录路径只允许 `${pkg//.//}` 或等价的正斜杠方案；迁移后立即用一条 grep
  验证旧包名零命中、目录名无非法字符。

### 问题 2：多会话并发写入同一仓库

- 现象：包迁移期间出现同一时间窗的多次提交与文档改动；`gov-next-decision.sh --create` 分配
  的编号被并发会话占用后又被他人提交，留下空模板决策。
- 根因：多个 AI 会话并行操作同一工作区；决策编号锁只能防止 create 竞争，防不住"创建后被他人
  捡走"。
- 解决：迁移与文档对齐分属不同提交；本迭代接管被占用的编号并立即写入真实内容，消除空模板。
- 防止复发：并行会话应约定文件/目录所有权，或串行化对 `docs/ai/decisions/` 的写入。

### 问题 3：字段语义与哨兵的措辞冲突

- 现象：`DomainException` 注释写"禁止吞异常返回魔法值(-1)"，但 `BuiltinComposition` 对无效输入
  返回 -1、`MeasureRecord.fat=-1` 表"未计算"。
- 根因：契约层 -1 是合法领域状态（data-schema 的 fat default 为 -1），与"用 -1 当错误码"是两回事，
  注释未区分。
- 解决：文档与注释区分"未计算哨兵"与"错误魔法值"。
- 防止复发：涉及哨兵/默认值的注释必须显式说明其合法语义来源。

### 问题 4：构建链三处叠加故障，工程在本机无法编译（DEC-015）

- 现象：用户报告 wrapper 无法安装 `gradle-9.4.1-all.zip`，原因 `Read timed out`。
- 根因：三处故障叠加，且后两处被第一处掩盖。
  1. 腾讯镜像对 `-all.zip`（238 MB）返回 `x-cache-status: BYPASS`，叶节点未缓存，
     实测 1~3.5 MB/s 且会中途停滞；Gradle wrapper 下载器**不支持断点续传**，失败即从
     第 0 字节重来，`.part` 长期停在 128/238 MB，故反复超时。
  2. 开发机唯一 JDK 为 Corretto 8，而 Gradle 9.4.1 要求 JVM 17+，报
     `Gradle requires JVM 17 or later to run.`
  3. `room-compiler:2.6.1` 编译期绑定 KSP1 API（`symbol-processing-api:1.9.0-1.0.13`），
     与项目所用 KSP2（`ksp = 2.3.2`）+ Kotlin 2.2.10 不兼容；触发点是
     `MeasurementDao.upsert(po)` 为 `suspend` 且返回 `Unit`，Room 在 KSP2 下解析其
     JVM 签名时抛 `unexpected jvm signature V`（google/ksp#2957）。
- 解决：发行版落盘 `gradle/dists/` 使 wrapper 免网络启动（该目录已 gitignore）；Gradle JVM
  改用 Android Studio 自带 JBR 21（写入开发机 `~/.zshrc`，不入库）；`room` 2.6.1 → 2.8.5。
  `./gradlew build` 全绿：198 tasks，四模块单元测试 + lint + debug/release 打包。
- 防止复发：Kotlin/KSP 主版本升级时必须同步核对 Room 等注解处理器是否支持对应 KSP 引擎；
  "DAO 的 suspend 方法返回 Unit" 记为该缺陷的触发点特征。

### 问题 5：同一条 lint 错误在两个会话之间来回反弹（`@RequiresApi` 是反修复）

- 现象：全量构建在 `:app:lintDebug` 阶段失败，`Lint found 1 error`，指向
  `HistoryRoute.kt:19`：`Call requires API level 26 (current min is 24): HistoryScreen`。
- 根因：`HistoryScreen` 内部用了 `java.time`（平台自 API 26 起才提供），而工程 `minSdk = 24`，
  且当时未启用 core library desugaring。真正的修复本是在构建层开脱糖，让 `java.time` 在
  API 24 上可用。若改在文件上打 `@RequiresApi(Build.VERSION_CODES.O)`，则把「调用方须 ≥26」
  这条约束向上传染，lint 不再报函数体、转而报所有调用点 —— 错误从内部挪到了 `HistoryRoute`，
  构建依旧红点，属于把问题搬家而不是修掉。
- 解决：在 `:app` 与 `:infrastructure` 开启 core library desugaring（`desugar_jdk_libs 2.1.5`），
  `HistoryScreen.kt` 保持与 HEAD 逐字节一致（不加任何注解）。全量 `./gradlew build` 通过。
- 反复点：实施期间该注解被另一会话于 16:31 重新写入，构建再次变红，移除后恢复。同一条错误
  在两个会话之间反弹，说明「该修哪一层」没有达成共识，单方面删改无法收敛。
- 防止复发：平台新增 API 优先在构建层解决（脱糖），不要用 `@RequiresApi` 把约束推给调用方；
  判别依据是「这次改动有没有让报错换位置，而不是换个数」。

## 3. 待完成/已知问题

- [ ] M2 SyncGateway 云同步与登录（含 CloudBase 防腐层）
- [ ] M3 ScaleGateway BLE 称重接入（三协议解析器已在 domain 就绪）
- [ ] 档案编辑 writer 与生日月日精度
- [ ] `settings.gradle.kts` 的 rootProject.name 是否随产品统一命名（当前仍为 qk-health-v2）
- [ ] 评估 wrapper 发行版由 `-all` 改为 `-bin`（体积约六成，腾讯镜像 `-all` 叶节点未缓存易超时）

## 4. 关键发现

- **分层纪律靠编译器比靠自觉可靠**：把 domain/application 做成纯 JVM 模块后，"领域层不得依赖
  Android"从约定变成编译约束，无需人工审阅。
- **架构测试需要自检**：ArchUnit 规则可能因类文件解析退化而静默空转，因此配套"解析自检 +
  违规负例"两条元测试，确保守卫真的会红而不是空跑。
- **文档漂移的代价在接力时显现**：生命周期文档若仍描述脚手架模板，下一位 AI 会据错误上下文
  决策——这是本次重写的直接动因。
- **JVM 版本是构建链的第一道闸门**：Gradle 9 在启动阶段就校验 JVM 17+，被它拦下时，
  配置期与编译期的错误全都看不见——本项目的 Room/KSP2 不兼容正是潜伏到闸门放行后才显形。
- **"源码没变"不等于"依赖组合仍然成立"**：`libs.versions.toml` 是版本单一事实源，但当
  Kotlin/KSP 升级而 Room 未同步时，失效的是组合而非文件；这类故障没有 diff 可查，
  只能由一次真实构建暴露。因此 `./gradlew build` 跑通应作为迭代收口的硬动作，而非可选项。

## 5. 变更记录

- 2026-10-17（DEC-033）：CI 成本治理四项——同分支推送并发取消、纯文档变更跳过两个 Gradle 步骤（job 仍上报成功，避免分支保护的必填检查停在 Expected）、开启 `org.gradle.caching` 并把构建缓存落点与条目数打进日志、四个 job 加超时上限。同批结账两笔旧假设：模拟器 connected 测试（run 39035148017，v1→v5 迁移链 + 数据保留）通过，DEC-031 A3 的运行期疑问闭环；连续三轮 master 推送全绿，DEC-032 A1 的分流生效得到验证。并行会话遗留的 qk-verify worktree 经证据确认无独有提交后归档并移除。
- 2026-10-17（收尾）：交接信署名失真修复——wf-inbox.sh 把 from 硬编码为 claude，而身份检测只存在于 ses-session-end.sh，于是任何平台发出的接力信都署 claude；两者改用 lib-platform.sh 的 detect_agent 统一检测（DEC-009 不冒充），并订正本批已发出的那封接力信署名。
- 2026-10-17（DEC-032）：CI 插件解析真因定位与修复——runner 访问阿里云三个镜像全部 502，而 Gradle 解析遇 5xx 直接终止、不回退后续仓库，排首位的镜像令插件解析在第一个仓库即失败；settings.gradle.kts 两个 repositories 块改为按 `CI` 环境变量分流，CI 只用官方源、本地保留阿里云优先。同批更正 DEC-031 的根因判断（版本非主因），并撤掉临时探针步骤。
- 2026-10-17（DEC-031）：构建链依赖可获取性修复——catalog 的 KSP 由公开仓库不存在的 2.3.2 钉到 2.3.9；本地在把 2.3.9 缓存一并移走、停掉 daemon 的前提下全量 build + assembleAndroidTest 通过（254 任务，247 真实执行）。该版本的可解析性以 PR #1 的 CI 复跑结果为最终判定。
- 2026-10-17（DEC-030）：构建层启用 core library desugaring，java.time 在 minSdk 24 可用；撤回 HistoryScreen 上的 @RequiresApi 注解，该注解在两会话间反弹两次，本批落盘「平台新增 API 只用脱糖、不把注解推给调用方」的共识。
- 2026-10-17（DEC-027）：云同步上传断链修复——SyncService.push() 实装且注入容器但生产零调用，新增 SyncTrigger 点火并接入三个写用例，本地写成功后 fire-and-forget 上云。
- 2026-10-17（DEC-026 组 L）：导航两处修正与称重未绑定出口打通——首页历史入口直达历史覆盖页、称重未绑定态经 needBind 标志在 WAIT 相渲染「去绑定设备」，FeatureScreen 包名归位 features。
- 2026-10-17（DEC-028）：网络配置集中化（BackendConfig 收敛 TCB 域名，网关/会话/云函数三处引用改指）与首页页面级间距 token 化（MeasureScreen 映射 QkSpacing）。
- 2026-10-17（DEC-029）：本地存储三处收口落地——备份规则排除会话凭据、档案键收敛为 ALL_KEYS 单一事实源并加反射守卫、迁移测试补上执行工作流；附带修好 DEC-028 未入索引导致的校验失败。

- 2026-10-17（DEC-029）：本地存储三处收口，各封一个面。A（P0 安全）凭据不入备份：
  `SessionStore` 把 access/refresh 明文放在 `scalehub_session`，而 `allowBackup` 为真且原先
  没有任何备份规则 → 新增 `backup_rules.xml`（API 30 及以下）与 `data_extraction_rules.xml`
  （API 31+），cloud-backup 与 device-transfer 两条通道分别排除 `scalehub_session.xml`，
  测量与健康记录作为用户数据保留备份。B（P1 静默缺陷）键表单一事实源：
  `ProfileStore.clearMember` 原实现在方法体内就地枚举键名，与 companion 的 `KEY_*` 常量构成
  两份各自维护的副本，加字段漏登记即静默留孤儿键 → companion 增 `internal ALL_KEYS`，
  `clearMember` 遍历之，新增 `ProfileStoreKeyTest` 反射断言两者相等（正向通过 + 负向探针失败，
  两轮验证）。C（P1 验证缺口）迁移测试接执行：`ci.yml` 只跑 `assembleAndroidTest`（只编译不执行），
  `MigrationTest` 覆盖 v1 到 v5 却不在任何门禁上 → 新增 `.github/workflows/android-test.yml`
  （workflow_dispatch 手动触发，刻意不挂 push 与 pull_request）执行
  `:infrastructure:connectedDebugAndroidTest`，注册表描述同步订正。附带修正：`DEC-028` 未登记进
  决策索引，致 `gov-validate.sh` 的 pytest 项失败，重建索引后恢复为 64 passed。
- 2026-10-17（DEC-029 附带，环境事故）：仓库 `.git/config` 于 17:09 被写入 `bare = true`，
  所有工作树命令失效（`git status` 报 must be run in a work tree）。本轮以 `GIT_WORK_TREE=.`
  前缀绕过完成全部校验，未改动该共享配置，待会诊是否属并行会话的刻意隔离手段。

- 2026-10-17（DEC-025）：视觉体系换轨。以交互原型为唯一视觉事实源，三层收口：令牌
  （`QkTheme.kt` 整体替换，主绿 `#2D8B56`→`#159B72`，卡片 17dp、按钮 12dp、图标块 13dp，
  新增 `QkRadius`/`QkSpacing.Page`/`QkTypography` 显式字重阶梯与组件内部色 token）、组件库
  （`QkCard` 改 1px 细边、`QkButton` 三态、`QkTextField` 用 `BasicTextField` 自绘把标签移到
  框外、新增 `QkGlyph`/`QkPill`/`QkSegmented`/`QkListRow`/`QkProgress`/`QkMetric`）、壳层
  （`ShellChrome.kt` 品牌顶栏 + 四页签底栏 + 抬高 15dp 中央 + 按钮）。调用点全部迁移：21 处
  `Button`/`OutlinedButton` → `QkButton`，10 处 `OutlinedTextField` → `QkTextField`。两处
  非视觉修正：详情页压栈时顶栏让位（否则与页面自带「← 标题」双标题）；快捷记录面板改
  `skipPartiallyExpanded` + `navigationBarsPadding`（半展态会把最后一行记录项切在屏幕边缘）。
  全模块 295 项 0 失败，`gov-validate.sh` 0 违规。附带把未入库的原型目录 `docs/protorype/`
  更名为 `docs/prototype/`、展示板 PNG 改 ASCII 名（原目录触发 GOV-STRUCT-001）。
- 2026-10-17（DEC-024 组 E 前置）：补齐状态机的链路时序测试网，为 sealed 状态替换做前置。
  新增四项：`reconnect` 后首次断连被 `suppressDisconnect` 吞掉（判据取「扫描计数不多一次」）、
  已连接未上秤时断连延迟 700ms 才弹提示、已上秤时订阅失败走 `holdStepOff` 回到「请光脚站上秤」、
  密封后断连不再发起静默重连。`MeasureSessionTest` 14 → 18 项，全模块 272 项 0 失败（强制重跑）。
  过程记录：首版 suppressDisconnect 测试断言「不弹重连提示」，去掉早退后仍然通过——因为漏吞时
  走的是 `scheduleRetry` 而非弹提示，两条路径在该断言上同值，属无效断言；改判据后才红。
- 2026-10-17（DEC-024 组 C）：`MeasureSession` 状态机化第一步。抽 `isSettling()`
  收敛 `onConnectFail` / `onDisConnected` / `onIndicateFailure` 三处重复守卫；`confirmSave()`
  补 `closed` 守卫，修掉「stop() 只取消定时器、不清 awaitingConfirm，导致会话结束后迟到的确认
  仍写库」；删除与 `cancelZero()` 完全重复的 `cancelSettle()`。补三项路径测试
  （stop 后迟到确认 / reconnect 清空重扫 / retry 重连），`MeasureSessionTest` 11 → 14 项。
  验证方式：先去掉守卫复跑，确认目标用例变红（14 中 1 失败），恢复后 `:application:test`
  93 项全绿——「断言已加」必须有一次失败即红的执行记录。
- 2026-10-17（DEC-024）：导航状态收口。`MainShell` 的 7 个并列导航状态（tab / showQuick /
  showAi / showScale / reportTime / featureName / recordType）收敛为 `NavState`（页签 +
  模态记录表单 + 覆盖页栈）+ 纯函数 `reduce`；记录表单保持模态替换层、不进栈（下层
  `MeasureScreen` 若继续组合会持有蓝牙会话）；新增 `BackHandler` 单点处理系统返回键；
  `listSaver` 保存栈序，进程重建后顶层页与离开时一致。P1 把 `TrendRoute`（历史/报告）与
  `MineRoute`（设置/成员）的局部布尔并入同一栈。有意修正两处语义：切页签现在会关闭功能页与
  报告页（旧实现漏清 featureName/reportTime），返回键不再退出 Activity。新增 `NavStateTest`
  12 项（含 1000 次随机事件序列不变量），app 模块 77 项全绿。
- 2026-10-17（DEC-024 组 D/E）：测量会话状态机收口。新增 `SessionPhase.kt`：`Idle` /
  `Linking(picked, suppressNextDisconnect)` / `Linked(MeasureProgress)` / `Sealed(SealedResult)` /
  `Closed` 五个相位 + 15 个事件 + 纯 `reduce`，非法转移一律 no-op（蓝牙回调本来就会乱序到达，
  静默忽略比崩溃更贴合现场）。`MeasureSession` 的可变字段 22 → 5：`phase`、`binding`
  （mac/协议/算法 id 的值对象）、`secondsLeft`、`tipIndex`、`lastWeightRaw`；6 个 Job 句柄按
  资源不计。删掉 `closed`/`linkReady`/`sealed`/`saved`/`seenWeight`/`steppingOff`/`awaitingConfirm`/
  `heldWeightOnly`/`suppressDisconnect`/`heldWeight`/`heldImpedance`/`pendingImpedance`/`heldHeart`/
  `scaleKind`，改为相位派生只读视图——写入口只剩 `reduce`。结构替换前后 `MeasureSessionTest`
  20 项全绿且未改用例，是行为等价的证据；新增 `SessionPhaseTest` 21 项（状态×事件逐格 +
  2000 步随机四条不变量）。组 D 的机械守卫落在 `gov-custom-checks.sh`：application 模块单文件
  非 Job 可变字段 > 8 即 FAIL，以阈值 3 试跑确认规则会咬住。全模块 295 项 0 失败。
- 2026-10-16（DEC-023）：Android 治理本地化收口。评估确认治理可按语言耦合度分四层落地：
  协议层/门禁层零耦合无需改，指标层已本地化，需收口 CI 对齐、Android 测试治理、analyzer 修整。
  新增 GOV-ATEST-001~005 治理各模块 `src/test` 的 Kotlin/Java 测试（命名/版本/emoji/注册表），
  配 9 个负向测试；新增 gov-lifecycle-audit.sh 供本地与 CI 共用"源码变更必须含 DEC+DevNotes"；
  AGENTS.md CI/CD 段与校验速查表对齐真实实现。
- 2026-10-16（DEC-023 组C）：analyzer 工具链收口。补 GOV-SCRIPT-004 注册表完整性校验（只做正向
  "注册命令必须存在", 不做反向"磁盘脚本必须注册"—— scripts/registry 是命令清单而非测试清单,
  反向会误报 bs-install-hooks/lib-platform 等 helper, 与 GOV-TEST-GOV 的测试注册表语义不同）;
  删除 6 个不存在的 tools/analyze 与 tools/metrics 僵尸条目; 清 AGENTS.md 对 ana-similarity.py 的引用。
  配 2 个负向测试。
- 2026-10-16（DEC-023 组D）：R0d 依赖目录白名单补 Gradle 产物边界。build 目录名太通用（会命中
  Gradle 发行版/AAR 元数据内的包目录 org.gradle.build）, 故不守护 build 位置, 仅将 .gradle/
  .gradle-user/.kotlin/.idea/.cxx 定为"仅允许项目根"并跳过其内部遍历; 下载缓存 gradle/ 与构建产物
  build/ 一并跳过不深挖。配 2 个负向测试。
- 2026-10-16（DEC-011）：包坐标整体迁移 com.qingke.health → com.xcwk.scalehub，46 文件归位、
  9 处怪目录清除，Room schema 按新包名重导出，构建与测试全绿。
- 2026-10-16（DEC-012）：生命周期 01~06 与黄金标准 architecture/index.md 改写为真实工程描述。
- 2026-10-16（DEC-013）：空模板事故根治。根因是校验盲区——唯一查占位符的
  `gov-validate-decisions.sh` 从未被 pre-commit/gov-validate 调用，而 GOV-CONTENT-004 只看编号。
  新增 GOV-CONTENT-012 用模板 token 白名单判定，不用通用尖括号正则，因为 DEC-010 正文的
  `<源>` 会被通用正则误报。
- 2026-10-16（DEC-014）：剩余黄金标准去模板化。`reference/index.md` 改写为项目技术参考
  （协议 / 算法 / 存储 / 术语口径），`guides/index.md` 去掉模板品牌，`mkdocs.yml` 站点身份改为
  项目；两份黄金标准均经 `wf-promote.sh` 晋升并标注来源。
- 2026-10-16（DEC-015）：构建链三处叠加故障修复。`gradle-9.4.1-all` 落盘使 wrapper 免网络启动；
  Gradle JVM 改用 JBR 21（开发机 `~/.zshrc`，不入库）；`room` 2.6.1 → 2.8.5 修复与 KSP2 的
  不兼容（`unexpected jvm signature V`）。`./gradlew build` 全绿：198 tasks，四模块单元测试
  + lint + debug/release 打包。
- 2026-10-16（DEC-016）：指标文档本地化为 Android 应用指标。新增项目钩子
  `metrics-hooks/00-document.sh` 整篇覆盖重写 `06_Metrics.md`，`gov-update-metrics.sh` 增加
  通用覆盖入口；漂移守卫测试锁住钩子与覆盖入口，防止回落到统计治理工具链行数的通用模板。
- 2026-10-16（DEC-017）：测量聚合不变量加固。`MeasureRecord` 构造期强制 time>0、
  weight∈[1,400]、impedance≥0、fat==-1 哨兵或∈[0,100]，违规抛 `DomainException`；
  配套 6 项不变量测试（每条守卫一个反例）。
- 2026-10-16（DEC-018）：对抗评审 R2 收口——不变量来源分层标注（契约级 / 产品级）、
  impedance 补上界 5000Ω、计算输入快照随记录持久化（measurements schema v1→v2）、
  单位换算四向量落地、README 增「交付状态」表如实标注各提交链可交付性。
- 2026-10-16（DEC-019）：文档术语与口径统一。新增术语表（`reference/index.md` §6）作为数量
  口径唯一事实源；修正 README 与生命周期文档里裸「N 项测试」等不写口径的表述；修复决策编号
  冲突——把复用了已占编号的「聚合不变量」「对抗评审 R2」分别改号为 DEC-017 / DEC-018，
  恢复 DEC-013（空模板校验）与 DEC-014（黄金标准去模板化），并修好决策索引生成器的标题解析。
- 2026-10-16（M2b）：认证与网关实现落地（SessionStore 端点逐位移植 + CloudBaseGateway
  分块 upsert + AuthService 登录后自动恢复）；游客门上壳（先同意后进入/60s 冷却/
  法务全文对话框）。HTTP 联调与 JWT 仪器测试随部署回填。四作用域 151 全绿。
- 2026-10-16（M2a）：云同步语义核心落地——CloudPorts 三端口 + SyncService（游客门/
  推送顺序/条件字段/快照列映射/坏行跳过/注销三步），11 项行为测试全程假端口无网可测。
  偏差声明：Mutex 串行替代 single-flight 折叠、devices 随 M3、拉取上限沿旧库。
  M2b（infra Gateway/Session 实现 + 登录 UI + AI 问答）待续。
- 2026-10-16（报告保真轮）：BodyMetrics.java 全量移植 BodyAnalysis（十八项指标/评分/
  体型九宫格/七项风险/建议文/控制量/双算法路径，行号对应）；报告 UI 全面升级
  （评分卡/风险评估/指标 tone 上色/控制建议）。四作用域 127 全绿，注册表 +1。
- 2026-10-16（阶段 1 契约）：spec v2（measurements 快照四列 + 云端↔本地列名映射；
  health_logs todo 关闭+unique 补齐）；云端 DDL 入 NOTE（未部署，待 M2 权限）。
- 2026-10-16（刷新信号实体化）：实机演示暴露跨屏刷新缺口（加号记饮食后首页须重启才见）——
  按 DEC-021 支柱2 落 DataRefreshBus（写用例 notify、查询 VM 订阅重读、无订阅即弃、
  bus 可空保伪造），+3 发射测试，四作用域 118 全绿。旧库 UiRefresh 正是此问题的反面教材。
- 2026-10-16（加号面板+8类记录切片）：底栏中央加号→ModalBottomSheet 快捷面板
  （AI 记录/绑定体脂秤置灰待 M2/M3）→ feature/record 四件套（8 类字段语义逐条随旧库
  RecordScreen：预设标签/副值/喝水快捷量/习惯即名称/「请输入有效数字」）；
  weight 双路径增 manualFat 体脂直录（旧库 saveManualWeight 口径，-1 哨兵、越界 fail-fast）；
  首页「健康记录」区块（HealthLogQuery 七类中文名+单位）。四作用域 115 全绿，注册表 +2。
- 2026-10-16（体测报告切片）：ReportQuery（BMI 五级阈值逐位/缺档/比上次/六格指标）
  + feature/report 四件套 + 历史行→报告三级导航态；体成分只认记录输入快照（R2 兑现，
  旧式行诚实空指标）。延后：评分建议体型、色阶条、AI 解读（M2）、去完善（nav 栈）。
  四作用域执行 103 全绿，注册表 +2。
- 2026-10-16（历史记录切片）：HistoryQuery（行序/previous/时段/按日/月汇总单源）+
  DeleteMeasurementService + 端口 delete（四实现同步）+ feature/history 四件套 +
  趋势页二级入口（Route 层导航态）。延后：报告跳转/对比/导出。测试四作用域 93 全绿，
  注册表 +3。
- 2026-10-16（M4 我的切片）：档案编辑纵切——修复"体脂以 height=0 计算"的生产功能洞。
  MemberProfile（守卫+周岁，出处行号）/MemberProfileWriter/UpdateProfileService/
  ProfileQuery；Reader 扩 4 字段；ProfileStore 关闭 TODO（writer+月日精度），昵称归位；
  feature/mine 四件套上壳。测试 73 执行全绿（domain 10/应用 4/VM 4），
  注册表 +3。叙事详见 DEC-022 执行记录。
- 2026-10-16（第 4 轮复裁落盘）：四轮零信任评审收敛——构建产物交付通过
  （SHA a7bfc89）、算法验收待 G5、整体条件通过；conclusion.md 入会审目录，
  README 交付表与终审段转终态，DEC-020 复裁记录追加。剩余闭环仅 G5（任务板
  T-007）与 T-003（人类裁决）。
- 2026-10-16（G3 关闭）：真机迁移链测试（androidTest 新源集 + instrumentation
  runner + schema 入 assets）。首跑抓获 R2 潜伏 bug：MIGRATION_1_2 蛇形
  age_years/height_cm vs 实体驼峰——升级库会读崩；修复为驼峰并抽 MIGRATION_1_2_SQL
  常量作单一事实源。静态对拍补盲 1→2 段（更名 MigrationSchemaAgreementTest、
  可空列解析修正——原 shapeOf 要求 NOT NULL 漏抓可空列，v3 段恰全 NOT NULL 未暴露）。
  真机证据：emulator-5554 android-35，tests=1 failures=0 errors=0
  （2026-10-16T06:10:14），日志 /tmp/g3_final.log。G1/G3 双关闭。
- 2026-10-16（G1 证据）：干净树构建溯源——SHA=fea604fc6e7d10ceea7aabb98da48801743a305e，
  TREE=0 非 cache 改动，CMD=`./gradlew test assembleDebug --console=plain` →
  BUILD SUCCESSFUL，91 任务（9 执行/82 复用），EXIT=0，UTC 2026-10-16T05:59:37Z；
  冷跑 `clean test assembleDebug` → 95 任务 89 实执行，EXIT=0，0 失败，
  UTC 2026-10-16T05:59:58Z。全量日志 /tmp/g1_build.log、/tmp/g1_cold.log。
  G1（DEC-020 门禁）就此关闭；G2/G4 于本 SHA 全绿（pytest 56 + 钩子 0 违规）。
- 2026-10-16（G1 执行·接管）：工具链迁移 WIP 接管提交。作者为并行 session（DEC-015：
  kotlinOptions→compilerOptions ×2 + Room 2.8.5），本 session 经人类暂停协调后接棒；
  内容逐行核对与 DEC-015 一致后以本 session 提交发出，归因以本条与提交信息声明为准。
  紧随干净工作树 G1 构建，溯源证据见 v24。
- 2026-10-16（归因事故补记）：e0d6bcf 在提交 M4 壳时连带扫入并行会话已暂存的
  DEC-023 全套 13 文件（ci.yml/AGENTS/治理模块/注册表/DEC-023 正文与索引/06_Metrics）——
  我的提交前暂存区检查无条件打印"(空=安全)"而未对输出做非空断言。文件零丢失、
  校验 0 违规，但提交信息归因混同；正式归属经收件箱通报（20261016-054805）与本条
  记录声明：该 13 文件属并行会话 DEC-023「Android 治理本地化收口」工作。
  整改：审计步骤改为非空即中止，不依赖人眼毫秒拦截；不回写共享历史（多 session
  禁 reset，且对方随即检出自身分支 docs/android-gov-localize，未受历史影响）。

- 2026-10-16（M4 启动）：导航壳 ui/shell/MainShell——四页签 IA（首页挂现有记录功能、
  趋势经 TrendRoute 首次可达、设备/我的占位）、Scaffold+NavigationBar、页签状态
  rememberSaveable 自持（宿主导航状态域，业务状态仍归 VM）、图标用 icons-core 子集；
  MainActivity 改挂壳。未引 navigation 库（A3 假设留详情栈验证）。四模块 gradle 与
  治理 56 项全绿。

- 2026-10-16（DEC-021 task B）：设计系统落地。QkTheme/QkColor 品牌色板（移植旧库
  QingkeTheme：轻刻绿 2D8B56/Bg FAFAFA/Accent E85D04 等 9 色）+ QkSpacing 五档刻度；
  Qk 组件库首批 5 个（Chip/Card/SectionTitle/StatRow/EmptyHint）；TrendScreen 组件化、
  MainActivity 切 QkTheme；ArchUnit UI 层选择器 feature.. 扩为 feature.. + ui..
  （6 处规则与负例同口径）。app 测试执行数 12 全绿。

- 2026-10-16（T-006 / DEC-021 task A）：首个 UI 垂直切片 feature/trend 四件套落地
  （UiState/ViewModel/Screen/Route 分文件，Screen 无状态 + @Preview，样式仅用
  MaterialTheme 槽位），ViewModel 消费组 B 的 TrendQuery 并按注入时区格式化；
  ArchUnit 上锁两条存储 SDK 规则（androidx.room/java.sql 包级 + SharedPreferences
  类名谓词，1.3.0 无 haveNameMatching 故手写 DescribedPredicate）+ 随附负例；
  守卫合计 6 规则 9 方法，TrendViewModelTest 3 方法（初始化/意图重查/空态文案）。
  Route 以常量契约先行（导航壳待 M4）；图表 Canvas 与设计 token（task B）待后续切片。

- 2026-10-16（DEC-022 组 C）：Room health_logs 表。schema v2→v3：HealthLogPo 复合主键
  (loggedAt, type) 对齐云表唯一约束，MIGRATION_2_3 建表且 DDL 抽为可测常量；
  RoomHealthLogRepository 六字段往返 + 脏行 fail-fast 测试；迁移 DDL 与导出 3.json
  的静态对拍测试（列名/类型/主键逐列一致）；组合根接线 healthLogRepository 与
  recordHealthLogService。验证边界如实声明：静态对拍只钉拼写漂移，真实迁移执行与
  数据保留待 T-005 androidTest（G3）关闭。infra-debug 测试执行数 9，全模块 Gradle
  测试 0 失败（schema 3.json 入库）。

- 2026-10-16（DEC-022 组 B）：趋势查询规则下沉。MeasurementRepository 增区间读
  between（闭开区间升序，InMemory/Room DAO/伪造方四方同步）；TrendQuery 落地五类规则
  （范围/时段/日末/统计/月汇总，逐条标注 TrendPages 行号），TrendQueryTest 10 个单元
  测试方法。同车修复 R2 遗留回归：RoomMeasurementRepository.toDomain 快照透传因替换锚点
  不匹配静默 no-op 未入库，读路径丢 male/age/height——补 RoomMappingTest 2 项（经公共
  API 走写读往返），并现场演示回退即红/还原即绿。三模块本轮 Gradle 测试运行全部通过（执行数以
  build/test-results 事实源为准）。

- 2026-10-16（DEC-021 执行）：trigger/ui → feature/measure 命名迁移修复。3 文件 git mv
  （Screen/ViewModel/夹具）、包名与 import 更新、ArchUnit 四条规则禁区加 feature.. 并保留
  trigger..（BLE/后台预留）、负例测试随迁改名、文档四处同步（02/05_Test/golden）。
  app 守卫测试 12 次执行全绿。

- 2026-10-16（DEC-022）：移植重构启动，组 A「健康记录纵切」落地。计划与保障机制入 DEC
  （七项保障各绑定介质/时机/可执行验证/盲点）；代码侧新增 HealthLogType（7 类枚举 +
  weight 委托语义）、HealthLog 构造守卫（时间戳/类型/主值/名称/副值五条，出处标注
  Pages.kt 行号）、HealthLogRepository 端口、RecordHealthLogService（习惯归一 1.0、
  盖时间戳）、InMemory 实现（(time,type) 唯一对齐云表）。行为测试 15 项（domain 9 +
  application 6），domain+application 合计 33 项全绿。体重入口写测量表的分界以
  weight 拒绝测试钉住。
- 2026-10-16（DEC-021）：UI 架构立约。基于旧库 qk-health-v2 的 UI 病根拆解（UiRefresh
  全局总线、千行页面文件、业务散落页面），确立 UDF + 屏幕四件套 + 读模型下沉 + 设计系统 +
  垂直切片交付五条约定，写入 02_Architecture §6 并晋升黄金标准；ArchUnit UI 守卫规则
  标注为随 M4 首切片落地（不假装已有）。
- 2026-10-16（DEC-020）：对抗评审 R3 收口。撤回"金丝雀已加"的文字级声称（全历史无可执行
  断言），补齐 4 条金丝雀（转义构造夹具，对 strip 免疫）；发现并修复修复器/检测器分歧——
  pre-commit Stage 0a 补 `grep -v '^tests/'`，与 GOV-TEST 的 tests/ 豁免对齐；溯源构建
  （SHA=89c37b0 + WIP 三文件声明）全绿 34 次执行 0 失败、schemas/2.json 导出；定义发布门槛
  G1-G5 与任务阻断/披露分诊。

## 6. 版本历史

| 版本 | 日期 | 变更摘要 |
|------|------|---------|
| v83 | 2026-10-22 | DEC-053：main 缺同模块测试 PR 警告（test-guard，默认 warn） |
| v82 | 2026-10-22 | DEC-052：schema/golden 守卫（CI PR 评论+默认 fail，接入 local-ci） |
| v81 | 2026-10-22 | DEC-051：local-ci 落盘证据 + STATUS 健康检查取证（禁 npm 伪构建） |
| v80 | 2026-10-22 | DEC-050：ses-worktree.sh + hooks 优先 show-toplevel（worktree ROOT） |
| v79 | 2026-10-22 | DEC-049：特性分支 CI 通配、Dependabot、gov-secrets-scan（gitleaks）接入 local-ci/pre-push |
| v78 | 2026-10-22 | DEC-048：任务 AC schema + GC-task-acceptance；AGENTS 瘦身 + GC-coding-conventions；PR 合入模板；GOV-PROTO-004 |
| v80 | 2026-10-22 | DEC-047 多 AI 并发协议 + 钉死 simplified hook 安装 + 退役 REFLECT；gov-local-ci 已验证 PASS |
| v79 | 2026-10-22 | 清理 17 条 GOV-CODE-001 死 import（gov-code-check --fix），使 custom-checks 可绿 |
| v78 | 2026-10-22 | DEC-046 精简 pre-commit 接入 custom-checks；新增 gov-local-ci.sh；修复 custom-checks 退出码空转 |
| v77 | 2026-10-22 | DEC-045 文档事实对齐（BLE/云已落地）+ 蒸馏规则取消强制反思 + 治理脚本/AI适配度评审入库；02_Architecture→promote architecture |
| v75 | 2026-10-21 | DEC-043-A T-028 报告分析 tab：ReportScreen 改双 tab（身体指标/报告分析）；新增 ReportAnalysisTab.kt（BodyFigure Canvas 小人插画/BmiScaleBar BMI 14-38 映射色带/HistoryLine 120dp 折线图/体型 3x3 矩阵/目标达成行/体重控制卡）；ReportViewModel 注入 MeasurementQueryPort 取近期序列（最多 11 条）+ 计算目标达成行（体重 stdWeight×0.9-1.1/BMI 18.5-24/体脂男 10-20 女 18-28/肌肉率男 32-56 女 28-52）；app 测试全绿 |
| v74 | 2026-10-21 | DEC-038 第 4 条根因：连接地址移植缺口——参考全部连接点用扫描活地址（linkAddress=deviceMac），我们误用绑定表存档地址，芯海随机地址轮换后 scannedDevices 查不到即「没有扫到这台芯海秤」失败；MeasureSession 增 linkAddress，首连/quietReconnect/retryConnect 统一活地址优先，start() 清空；协议解析层与参考逐字节对拍一致（非解析问题） |
| v73 | 2026-10-21 | DEC-038 真机追因轮：协议标记穷举证实秤广播缺席（47 条 0 命中）后四路收口——链路防固件静默死锁（订阅 CCCD/写队列 3s 超时+身份校验）、扫描门两静默守卫补去重证据日志、扫描模式对齐参考 LOW_LATENCY（原 BALANCED 半占空比错过唤醒脉冲）、悬浮日志防洪（ble 层抽样+5s 汇总+停扫总账，session 判决层不抽样）+ 8s 扫描看门狗定论行 + start 守卫日志；修 DeviceScreen heroText 硬编码「已连接」UI 谎言（绑定页从不建连，该字样误导真机排查数轮，改「使用中」）；5 条新回归测试 |
| v74 | 2026-10-21 | DEC-042 测量会话生命周期锚定 overlay：ScaleRoute 离开即 DisposableEffect onDispose 调 vm.stop()（停扫描/断连接/取消定时器），修复「退出测量页会话后台空转」；ScaleViewModel session 改 var + newSession() 工厂，start() 检测 closed 终态即重建新会话并重挂 state 收集（旧 collect 先 cancel 防泄漏），VM 自持 MutableStateFlow 转发，重进测量页可用；MeasureSession.closed 提升公开只读；顺手修 DebugPanel 函数内非法 typealias/const val；374 测试全绿 |
| v73 | 2026-10-21 | DEC-041 统一错误模型与元数据修复：domain 改 Android Library（com.android.library + kotlin.android）根治 Kotlin 元数据跨模块不可见（密封类/伴生对象/扩展函数在应用层不可见）；AppError.Codes 提升为顶层 AppErrorCodes；引入 AppError 密封类（Network/Server/Validation/Business/Unknown）+ AppResult 密封类（Ok/Err + map/flatMap/fold/recover/onFailure + kotlin.Result 互操作）；基建层 CloudBaseGateway/CloudFunctionAi/SessionStore 返回 AppResult（网络异常/HTTP 非 2xx/解析失败显式建模，不再被 runCatching 吞成 null）；应用层 SyncService/AuthService/AiService/RestoreAccountService/DeleteAccountService 签名改 AppResult，错误按类型分流（Network 重试、Server 401 跳登录、Business 直达文案）；敏感信息过滤：auth 只记 path 不记 query；374 测试+ArchUnit 14 条全绿 |
| v72 | 2026-10-21 | DEC-040 全链路调试日志：BleDebugLog 泛化改名 application.debug.DebugLog（语义覆盖 ble/session/sync/cloud/ai/auth，测试类同步更名 DebugLogTest）；四链路插桩约 30 处——SyncService 记 push/restore/deleteAccount 成败+四表条数水位线+拉取失败定位+坏行跳过，CloudBaseGateway/CloudFunctionAi/SessionStore 的 request() 统一记 http method/path/code 与网络异常（runCatching 吞掉的失败从此可见）；敏感信息硬约束：auth 只记 path 不记含 verify_code 的 query，token/验证码/手机号禁入日志；面板标题改「调试日志」；374 测试+ArchUnit 14 条全绿 |
| v71 | 2026-10-21 | DEC-039 蓝牙调试悬浮球：application 层新增 BleDebugLog（纯 JVM 环形缓冲 2 万行，synchronized，logcat 双写不破坏原行为）；AndroidBleLink blog() 收敛 12 处日志点并补齐 TX/RX hex、订阅逐特征进度、读失败分支；MeasureSession 编排日志 25 处（协议重绑/连接回调/帧解析/封存/落库分支）；app 新增 ui.debug.DebugOverlay 悬浮球+面板（拖动吸附/一键复制/清空/自动贴底），面板经真机反馈由全屏改为底部 62% 小窗（顶部圆角、点外遮罩关闭）；MainActivity Box 叠加，BuildConfig.DEBUG 门控（buildFeatures 开 buildConfig）；374 测试+ArchUnit 14 条全绿 |
| v70 | 2026-10-21 | DEC-037 T-016 仓储读写分离：domain 仓储纯化（Measurement=save/delete、HealthLog=save），查询端口 MeasurementQueryPort/HealthLogQueryPort 定义在 application（先例 SyncStatePort/BlePort 五端口同模式），infrastructure 新增 RoomMeasurementQuery/RoomHealthLogQuery 实现、InMemory 双实现写口+端口；读模型类 MeasurementQuery/HealthLogQuery（HealthLogItem）恢复原名只换构造参数，MeasureViewModel 零改动。中途方案（domain 塞回 QuerySupport + 4 层委托链）经先例对照整体回退；修复 SyncService 测试 9 处写口/查询端口分实例问题（save 写入 push 读不到，编译不拦）；362 测试+ArchUnit 14 条全绿 |
| v69 | 2026-10-21 | DEC-035 组合根只暴露端口：先实证 ArchUnit 盲区——javap 显示 ScaleViewModel 字节码调用 getBleLink:()AndroidBleLink（另有 getDeviceRepository/getProfileStore），但 ArchUnit 统计的 106 个直接依赖里 infrastructure 包一个都没有，它不追踪方法体内的调用返回类型，据此否决评审的方案 B（返回类型规则会静默放行）；改用方案 A：AppContainer 四个具体类型属性改私有 XxxImpl，对外只给 blePort/deviceRepository/profileReader+profileWriter/authPort+cloudSessionPort，表现层 6 处取值点改端口；复验 infra 引用 3→0，413 项全绿 |
| v68 | 2026-10-20 | DEC-034 组 F 评估后不采纳并收官（七组全部完成）：F-007 值对象的三条论据中两条已被 A/C/D 组顺带解决（Clock 收口时间语义、聚合与 String 混用会编译失败），第三条「参数顺序错」在 save(record, memberId) 上无相邻同类参数可换，memberId 传递链仅三段；ddd-architecture 9 项有效发现全关闭（6 修 + 3 评估关闭），余 ai-dev-readiness 11 项 |
| v67 | 2026-10-20 | DEC-034 组 E 评估后部分不采纳：F-008 的「查询服务直访 DAO」被 ArchUnit 禁止（application 不依赖 infrastructure），15 处 between/recent 调用中 14 处本就在查询层或 SyncService，仅修 ScaleViewModel 一处绕过 MeasurementQuery 直捅仓储；F-009 的「Member 贫血」诊断不成立（nextId/nextSlot/normalizeNickname 与 MAX=8 均在实体内，MemberStore.add 只做编排）；两条软约束均记录不采纳理由入 DEC-034 与评审报告 |
| v66 | 2026-10-20 | DEC-034 DDD 架构合规性修正（评审 10 项发现，A/B/C/D/G 五组落地）：A 事件类型化——DataChange sealed 替代 Unit 信号、四个 ViewModel 精确订阅、写用例剥离 SyncTrigger 改由 SyncOnLocalWrites 订阅点火、SettingsViewModel 发布动作下沉 RestoreAccountService/DeleteAccountService（评审原案「下沉 domain」经评估否决，理由入 DEC）；B 语义耦合下沉——ScaleDevice.algorithmFor 收敛三处重复的协议→算法映射，infrastructure 不再 import domain.algorithm；C 聚合根工厂——MeasureRecord/HealthLog/MemberProfile 各补 create 入口（体脂编排/习惯归一/昵称归一）+ Clock 抽象（写用例 currentTimeMillis 清零）；D 仓储契约——save 显式携带 memberId、聚合删除从未持有有效值的 memberId 死字段；G 评审报告自身修正（统计口径 2→1 Critical、F-010 撤回、F-006 数字更正）；新增 gov-code-check.py 五条源码规则（守卫负例自证 + 死 import/逐字重复/相似度/两处语义耦合），413 项全绿 |
| v65 | 2026-10-17 | DEC-029 本地存储三处收口（凭据不入备份 / 档案键表单一事实源 / 迁移测试接执行）+ 反修复注解回弹与 git core.bare 事故记录 |
| v64 | 2026-10-17 | DEC-028 硬编码治理（任务 A/B 落地）：A 网络配置集中化——新建 BackendConfig 收敛 TCB 域名与网络参数，CloudBaseGateway/SessionStore/CloudFunctionAi 改为引用，域名 2 处重复收敛为 1 处（服务去云绑定）；B 保守语义间距 token 化——MeasureScreen 10 处页面级间距映射到 QkSpacing（8dp→Sm、12dp→Md），图标/圆角/行内微间距保留裸 dp；任务 C 文案抽取评估暂缓（动态值占位符风险高、无多语言需求、不可测试验证） |
| v59 | 2026-10-17 | DEC-026 组 H 修订（用户反馈「体测报告做得不好」）：头部卡恒在（无评分记录也有日期+大数字体重+增减，修复缺测量身份）、删重复的 BMI 独立卡（体型折进 BMI 行副标题）、身高/实际年龄移出关键指标表改「分析依据」脚注、指标图标映射补全（肥胖度/代谢/年龄等，新增 QkIcons.Age）；模拟器复验 + 295 项全绿 |
| v60 | 2026-10-17 | DEC-026 组 J 收口：设置页与加号快捷面板截图核对通过（快捷面板 8 项为真实记录类型集，原型演示的「睡眠」在产品范围外未做）；顶栏通知铃 `onBell` 空实现闭环——无通知后端时给 Toast「暂无新通知」诚实空态（MainShell）；README 补「坐标先 dump 再 tap / 瞬时 UI 用窗口 dump 证据」反思；295 项全绿 |
| v61 | 2026-10-17 | DEC-026 组 K 补缺（对照原型逐页审计后修复）：趋势页补「数据解读」卡（TrendQuery.trendReadingOf 按首末差判方向，单点不硬读趋势→「记录还太少」）与「趋势对比」卡（comparison 取前一等长窗均值，ALL 范围无上周期给 "--"）；我的页补「健康数据概览」三格卡（原型 profile metric-grid，复用 HomeGoalQuery 与首页同源，MineViewModel 增 refreshBus 订阅）；报告页标题改原型文案「身体报告」+ 评分卡补「较上次 ±N 分」pill（ReportQuery 对 prev 复跑 analysis 取上期评分）；新增单测 12 例，307 项全绿；模拟器复验三页（pill 分支待阻抗数据） |
| v62 | 2026-10-17 | 全项目架构-功能审计后的导航修正（遗留会话在途批次，取证后采纳）：称重未绑定态按钮从「返回」改为直达设备页绑定（ScaleScreen 增 onGoBind，壳层 SelectTab 清覆盖栈落设备页）；首页历史入口从切趋势页改为直达历史覆盖页（对齐旧库 QingkeApp.kt:339 与「我的」页口径） |
| v63 | 2026-10-17 | 组 L 代码落地：v62 所记两处导航修正提交入库并二次修正分支——首页历史入口直达历史覆盖页；称重「无绑定设备」走 WAIT 相而非 NOT_BOUND 结果态（首版按钮改对了文案却因分支不对而不显示），MeasureState 增 needBind 标志 + WAIT 相据标志渲染「去绑定设备」+ 单测两态断言；DEC-026 补执行记录；worktree 隔离验证 + 模拟器实拍复验（历史按钮/称重按钮/跳转落点三证） |
| v63 | 2026-10-17 | **P0 断链修复：云同步只下拉不上传**。审计发现 SyncService.push() 实装并注入容器但生产零调用（仅测试调）——新测量/记录/档案变更永不上云，「恢复账号」只拉旧快照。修复：新增 SyncTrigger（fire-and-forget + runCatching 吞异常，写用例不等网络），三个写用例（RecordMeasurement/RecordHealthLog/UpdateProfile）落库成功后点火；AppContainer 增 applicationScope 与 syncTrigger（游客在 push 首行天然 no-op）；恢复账号降级为兜底路径。新增单测 8 例（SyncTrigger 4 + 三服务各 1~2），315 项全绿；GOV-ATEST-005 抓出未注册新测试已补登记 |
| v58 | 2026-10-17 | DEC-026 逐页按原型重建（F/G 批）：QkIcons 图标表 + extended 依赖；新增 QkPageTitle/QkBackButton/QkScoreRing/QkDeviceHero/QkDateChip/QkRiskRow 六个页面级原语；首页六区块重排（8 个原型外模块编入卡片语法）；趋势/设备/我的/历史/报告/设置六页按原型重建（设备页补 .device-hero 蓝牙环，报告页评分环按分画弧）；我的页接历史/设备两个导航回调（295 项全绿，gov-validate 0 违规） |
| v57 | 2026-10-17 | DEC-025 视觉体系换轨：令牌整体替换（主绿 #159B72 / 17dp 卡片 / 字重阶梯）+ 组件库按原型原语重建（QkButton 三态、QkTextField 标签外置）+ 壳层品牌顶栏与抬高式底栏 + 21 处按钮与 10 处输入框迁移（295 项全绿） |
| v56 | 2026-10-17 | DEC-024 组 D/E 收口：SessionPhase sealed 相位 + 纯 reduce 替换 MeasureSession 的 13 个并列字段（可变字段 22→5），相位矩阵 21 项，可变字段阈值守卫入 gov-custom-checks（295 项全绿） |
| v55 | 2026-10-17 | DEC-024 组 E 前置：补 4 项链路时序测试，MeasureSessionTest 18 项，全模块 272 项 |
| v54 | 2026-10-17 | DEC-024 组 C：MeasureSession 抽 isSettling + confirmSave 补 closed 守卫，补 3 项路径测试（93 项 application 单测） |
| v53 | 2026-10-17 | DEC-024 导航状态收口：NavState 单一栈 + BackHandler，Trend/Mine 局部导航并入（77 项 app 单测） |
| v53 | 2026-10-17 | T-005 关闭：v1→v5 真机全链迁移测试（模拟器实跑通过）+ CI androidTest 编译门启用 |
| v52 | 2026-10-17 | 首页全家桶·H2：旧库 HomeScreen 全量重建 + feature 占位页族（253 测试） |
| v51 | 2026-10-17 | 首页全家桶·H1：GoalStore 目标存储 + HomeGoalQuery 今日汇总 + 目标云推送（253 测试） |
| v50 | 2026-10-17 | 多成员·刀B：成员页四件套 + 我的页入口 + 切换/增删信号广播（247 测试） |
| v49 | 2026-10-17 | 多成员维·刀A：member_id 进两表（v5 迁移）+ MemberStore + 档案/头像按成员 + 槽位下发（243 测试） |
| v48 | 2026-10-17 | 移植补齐批：扫码绑定（zxing+ScaleQr）+ 设置四分支（关于/两清单/清缓存，238 测试） |
| v47 | 2026-10-17 | M4-相机头像：FileProvider + EXTRA_OUTPUT 全分辨率落盘（233 测试） |
| v46 | 2026-10-17 | M4-视觉轮收尾：QkAreaChart 面积趋势图 + 首页卡片化（并行草稿接管入库，233 测试） |
| v45 | 2026-10-17 | M4-视觉轮：QkWheelPicker（出生/身高）+ QkAvatar（相册选图，233 测试） |
| v44 | 2026-10-17 | M4-历史增强：按日筛选（DatePicker）+ 导出分享（227 测试） |
| v43 | 2026-10-17 | 治理切片：表现层桥面白名单 + 组合根白名单 + 反射 grep 门 + trigger 占位（守卫 9 规则） |
| v42 | 2026-10-17 | M4-报告入口闭环：查看报告跳转 + AI 解读登录态联动 + 覆盖页叠放（224 测试） |
| v41 | 2026-10-16 | M3d：设备页签绑定 UI + 蓝牙测量覆盖页（MeasureSession 接壳，222 测试） |
| v40 | 2026-10-16 | M3c：AndroidBleLink + Room 设备仓储（devices 表 v4 迁移）+ devices 同步闭环（217 测试） |
| v39 | 2026-10-16 | M3b：BlePort 端口 + MeasureSession 测量编排（MeasureActivity 状态机移植，206 测试） |
| v38 | 2026-10-16 | M3a：协议层黄金向量测试（okok/芯海/广播/组包/下发帧，195 测试） |
| v37 | 2026-10-16 | M2c：设置账号三流程 UI + AI 问答客户端（AiService/CloudFunctionAi，163 测试） |
| v36 | 2026-10-16 | M2b：SessionStore/CloudBaseGateway/AuthService + 游客门四件套（151 测试） |
| v35 | 2026-10-16 | M2a 同步语义核心：三端口 + SyncService（138 测试，无网可测） |
| v34 | 2026-10-16 | 报告保真轮：BodyMetrics 全套移植（BodyAnalysis，127 测试） |
| v33 | 2026-10-16 | 阶段1契约升级：spec v2 快照四列 + NOTE-20261016-001 云端 DDL |
| v32 | 2026-10-16 | DataRefreshBus：跨屏刷新信号订阅实体化（演示暴露的首页不刷新缺口） |
| v31 | 2026-10-16 | 加号面板+8类记录切片（manualFat 直录 + 首页可见闭环，115 测试） |
| v30 | 2026-10-16 | DEC-023 组 C/D：GOV-SCRIPT-004 注册表完整性 + 清僵尸 analyzer + R0d Gradle 边界 |
| v28 | 2026-10-16 | 历史记录切片：delete 端口 + HistoryQuery + history 四件套（93 测试） |
| v27 | 2026-10-16 | M4 我的切片：档案编辑纵切（关闭 height=0 功能洞，73 测试） |
| v26 | 2026-10-16 | 第 4 轮复裁落盘：构建交付通过，算法验收待 G5，评审闭环收敛 |
| v25 | 2026-10-16 | G3 关闭：真机迁移链通过，抓获并修复 1→2 蛇形/驼峰缺陷，静态对拍补盲 |
| v24 | 2026-10-16 | G1 收口：fea604f 干净树冷暖双跑全绿，溯源三要素在案（T-004 done） |
| v23 | 2026-10-16 | G1 执行：接管工具链迁移 WIP（归因 DEC-015 作者=并行 session） |
| v22 | 2026-10-16 | 归因事故补记：e0d6bcf 扫入 DEC-023（13 文件归属声明，收件箱通报） |
| v21 | 2026-10-16 | M4 启动：四页签导航壳 MainShell（trend 首次可达） |
| v20 | 2026-10-16 | DEC-023 Android 治理本地化：GOV-ATEST 测试治理 + lifecycle-audit 脚本 + CI 对齐 |
| v20 | 2026-10-16 | DEC-021 task B：QkTheme 设计 token + 首批 5 组件 + 守卫扩口 ui.. |
| v19 | 2026-10-16 | T-006 首个 UI 切片 feature/trend 四件套 + ArchUnit 存储 SDK 守卫上锁 |
| v18 | 2026-10-16 | DEC-022 组 C：Room health_logs 表 + 迁移静态对拍 |
| v17 | 2026-10-16 | DEC-022 组 B 趋势规则下沉 + R2 读映射回归修复 |
| v16 | 2026-10-16 | DEC-021 执行：trigger/ui → feature/measure 命名迁移 + 守卫规则扩展 |
| v15 | 2026-10-16 | DEC-022 移植计划 + 组 A 健康记录纵切（15 项行为测试） |
| v14 | 2026-10-16 | DEC-021 UI 架构立约（02_Architecture §6 + 晋升） |
| v13 | 2026-10-16 | DEC-020 对抗评审 R3：金丝雀补齐、修复器分歧修复、发布门槛 G1-G5 |
| v12 | 2026-10-16 | DEC-019 术语与口径统一（术语表）+ 决策编号冲突修复 |
| v11 | 2026-10-16 | DEC-018 对抗评审 R2：输入快照 / 不变量溯源 / 单位向量 / 交付状态表 |
| v10 | 2026-10-16 | DEC-017 聚合不变量加固 + 对抗评审 R1 归因实验 |
| v9 | 2026-10-16 | DEC-016 指标文档本地化为 Android 应用指标 |
| v8 | 2026-10-16 | DEC-015 构建链修复（发行版落盘 + JBR 21 + room 2.8.5） |
| v7 | 2026-10-16 | DEC-014 剩余黄金标准去模板化 |
| v6 | 2026-10-16 | DEC-013 决策空模板校验（GOV-CONTENT-012） |
| v5 | 2026-10-16 | DEC-012 M1 收口与文档对齐：01~06 重写为真实工程描述 |
| v4 | 2026-10-16 | DEC-011 公司全限定名迁移 com.xcwk.scalehub |
| v3 | 2026-10-14 | DEC-003 协议文件统一更名 AGENTS.md |
| v2 | 2026-10-13 | DEC-002 模版自一致性修复 |
| v1 | 2026-07-10 | 初始开发笔记 |
| v76 | 2026-10-21 | DEC-043-D 习惯管理页落地：HabitViewModel/HabitUiState/HabitRoute + HabitQuery/HabitRepository/RoomHabitRepository 读写分离 + HabitDao（Flow+HabitPo）+ AppDatabase v7 habits 表 + RecordHabitService + WorkManagerHabitReminderScheduler + 9 项 ViewModel 测试 + 迁移静态对拍补齐 |
