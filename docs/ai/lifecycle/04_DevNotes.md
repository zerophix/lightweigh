# 开发笔记

> 本迭代（M1 收口与文档对齐）的技术笔记、问题与发现。
> 覆盖型文档：每次迭代重写，保留本次迭代的技术细节。

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
