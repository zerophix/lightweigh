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

- 2026-10-16（DEC-011）：包坐标整体迁移 com.qingke.health → com.xcwk.scalehub，46 文件归位、
  9 处怪目录清除，Room schema 按新包名重导出，构建与测试全绿。
- 2026-10-16（DEC-012）：生命周期 01~06 与黄金标准 architecture/index.md 改写为真实工程描述。
- 2026-10-16（DEC-013）：空模板事故根治。根因是校验盲区——唯一查占位符的
  `gov-validate-decisions.sh` 从未被 pre-commit/gov-validate 调用，而 GOV-CONTENT-004 只看编号。
  新增 GOV-CONTENT-012 用模板 token 白名单判定，不用通用尖括号正则，因为 DEC-010 正文的
  `<源>` 会被通用正则误报。
- 2026-10-16（DEC-015）：构建链三处叠加故障修复。`gradle-9.4.1-all` 落盘使 wrapper 免网络启动；
  Gradle JVM 改用 JBR 21（开发机 `~/.zshrc`，不入库）；`room` 2.6.1 → 2.8.5 修复与 KSP2 的
  不兼容（`unexpected jvm signature V`）。`./gradlew build` 全绿：198 tasks，四模块单元测试
  + lint + debug/release 打包。

## 6. 版本历史

| 版本 | 日期 | 变更摘要 |
|------|------|---------|
| v7 | 2026-10-16 | DEC-015 构建链修复（发行版落盘 + JBR 21 + room 2.8.5） |
| v6 | 2026-10-16 | DEC-013 决策空模板校验（GOV-CONTENT-012） |
| v6 | 2026-10-16 | DEC-014 R2：输入快照/不变量溯源/单位向量/交付状态表 |
| v5 | 2026-10-16 | M1 收口与文档对齐（DEC-012）：01~06 重写为真实工程描述 |
| v5 | 2026-10-16 | DEC-013 聚合不变量加固 + 对抗评审 R1 归因实验 |
| v4 | 2026-10-16 | DEC-011 公司全限定名迁移 com.xcwk.scalehub |
| v3 | 2026-10-14 | DEC-003 协议文件统一更名 AGENTS.md |
| v2 | 2026-10-13 | DEC-002 模版自一致性修复 |
| v1 | 2026-07-10 | 初始开发笔记 |
