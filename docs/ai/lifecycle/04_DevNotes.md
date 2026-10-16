# 开发笔记

> 模板打磨迭代中的技术笔记、发现、问题记录。
> 覆盖型文档：每次迭代重写，保留本次迭代的技术细节。

---

## 1. 技术决策记录

- **DEC 采用 next-decision.sh 模板后再编辑**：next-decision.sh 生成的标准模板结构简洁，需要在模板基础上补充方案对比、决策理由、实现组件和已知局限。直接编辑比重新创建更高效。
- **生成决策日志索引**：写完 DEC 后必须执行 `generate-index.sh` 刷新索引，否则新 DEC 不会被索引文件收录。
- **并行编辑多个 lifecycle 文档**：Edit 工具一次调用只传一个 `old_string`，如果旧内容匹配不唯一会报错。需要确保每次编辑的 `old_string` 在当前文件状态中精确匹配。
- **会变的数字不进文档（DEC-002）**：校验项数被写死在 11 个文件里产生 18/20 两套说法。修复方式是全部删除，统一指向 `gov-validate.sh` 实时输出这个单一事实源——数字在 N 处复制就一定会在 N 处漂移。
- **原子锁迁入而非重写（DEC-002）**：wf-next-decision.sh 的锁代码原样迁入 gov-next-decision.sh（含锁内重新扫描），wf- 版本退化为 exec wrapper。双实现各改一半是漂移根源，收敛到文档实际引用的那一个。
- **.cache 里出现外来文件先查来源再加白名单（DEC-002）**：R0f 白名单缺 session-marker.json / session.lock（外部会话机制产生、与已白名单的 session-ledger.json 同族），全仓 grep 无引用后补白名单，而不是删文件。
- **更名走 DEC 而非直接改（DEC-003）**：协议文件统一更名为 AGENTS.md 涉及行为性代码（setup.sh 复制逻辑、mig-migrate.sh 白名单）与 77 处引用，先写 DEC 固化"改哪些、清哪些"，再动手；校验器白名单此前已同时收录两个名字，更名零校验阻力。
- **README 结构规范 = GC 指南 + GOV-README 规则族（DEC-006）**：alpha-studio 实测证明只写在文档里的结构规范必然漂移，因此机读层（H1 唯一开头、当前状态收尾、反思首子节、快速开始顺序）做成 FAIL 规则，手读层（详略、行数、内容质量）用指南 + WARN 引导；每条 FAIL 规则配负向测试，规范↔实现↔测试三者在同一提交内联动修改。
- **升级边界 = 拥有区清单，而非目录（DEC-010）**：gov-config.sh 与 gov-custom-checks.sh 住在 scripts/governance/ 但内容属项目所有，若按目录整同步会被覆盖——边界必须显式写成 gov-owned.list 并配完整性测试；`--check` 先体检、`--apply` 带备份，分叉可以零风险试升级。同次迭代新增文件先踩了 GOV-SCRIPT-002 前缀规范（gov- 开头）被 pre-commit 拦下——规则咬自己的作者是它活着的又一证据，选择改名守规矩而不是给规则开白名单后门。

## 2. 遇到的问题与解决方案

### 问题 1：Edit 工具 old_string 匹配失败

- 现象：编辑 02_Architecture.md 时，因为省略了 `old_string` 参数导致 InputValidationError
- 根因：Edit 工具的 `old_string` 是必填参数，不能省略
- 解决：补全 `old_string` 参数后重试成功
- 防止复发：每次 Edit 调用前确认包含完整的 `old_string`

### 问题 2：STATUS.md 模板内容过期

- 现象：`update-status.sh` 生成的 STATUS.md 中"已知风险"仍显示"密钥管理待评审"（上一个 AI 演示项目的残留）
- 根因：STATUS.md 由 generate-status.py 从 `.ai/tasks.json` 读取 known_risks，旧数据未清理
- 解决：手动清空 `.ai/tasks.json` 中的 known_risks 数组
- 防止复发：新迭代开始时执行 `task-board.sh clear-risks` 清理遗留风险

### 问题 3：前缀重命名后残留的失效路径（DEC-002 核心发现）

- 现象：gov-sync-standards.sh 的 PROMOTE/VALIDATE/NOTE_SCRIPT 三个变量指向不存在的旧文件；.claude/settings.json 的 4 个 SessionStart hook 仍跑 scripts/docs/session-begin.sh；gov-generate-index.sh 生成的索引写旧命令名
- 根因：scripts/ 前缀重命名（gov-/wf-/ses-）时只改了被直接 grep 到的文档，没改变量赋值、heredoc 模板、.claude/ 隐藏目录和 skill 配置
- 解决：全仓 grep `scripts/(governance|workflow|session|migration)/(旧名)` 逐文件替换；.claude/ 的 hook 权限、skill 执行脚本、契约命令全量对齐新前缀
- 防止复发：验证方式 = 同一条 grep 必须零命中（历史 reviews 除外）；后续重命名脚本时应把该 grep 模式作为收尾检查项

### 问题 4：本 session 无 shell 执行能力

- 现象：无法运行 gov-validate.sh / pytest / bash -n，所有 bash 修改只能静态编写
- 根因：当前 agent 环境未提供终端工具（Blender MCP 亦未连接）
- 解决：DEC-002 记录假设 A3，把全量校验列为 T-210 由人类执行；测试与脚本改动经逐行自审 + 围栏配对计数等静态检查
- 防止复发：含 bash 改动的 session 应优先确认执行环境可用，否则在 tasks.json 的 next_action 里强制挂校验任务

### 问题 5：GOV-PROTO-001 把所有未读信当违规（提交时被 pre-commit 拦截发现）

- 现象：session-end 按铁律写完接力信后，pre-commit 的 GOV-PROTO-001 立即 FAIL，提交被拒；而数分钟前的全量校验该项还是 PASS（当时收件箱恰好无未读信）
- 根因：规则名与文案声称「超 48 小时未读」，实现却是 `inbox_unread > 0` 即 FAIL，从未检查时间——接力机制与校验规则自相矛盾
- 解决：按 frontmatter timestamp → 文件名 YYYYMMDD-HHMMSS → 文件 mtime 三级来源计算信龄，仅超 48h 的未读计入违规；补 3 个测试用例锁住语义
- 防止复发：规则名/文案声称的语义必须逐条对照实现验证；本例再次印证「文档与源码的对齐只能靠审阅 + 测试固化」（见 §4）

### 问题 6：R0 复用检测在干净树上 100% 误报（首次 git push 被 pre-push 拦截发现）

- 现象：首次配置远程执行 push，pre-push 的 validate 报「反思内容与上一次提交完全相同」，但该提交的反思明明是新写的
- 根因：复用检测固定比对工作区与 HEAD——这在 pre-commit（工作区有未提交改动）语义正确，但提交落地后工作区必然等于 HEAD，pre-push/CI/事后校验的所有场景全部误报；本仓库此前无远程，pre-push 从未真正执行，缺陷一直潜伏
- 解决：按 porcelain 状态选择比对基线——脏工作区比 HEAD（保持提交强度），干净树比 HEAD~1（审计语义平移为「最近提交必须刷新反思」）；补 2 个回归用例
- 防止复发：规则的比对基线必须随校验时机（Q1 检测时机）调整；从未在真实场景执行过的钩子/规则不视为已验证

### 问题 7：pre-push 通过条件引用了 validate 永不输出的字符串（DEC-005，同一次首次 push 暴露）

- 现象：R0 修复后校验全绿，pre-push 仍拒绝推送，且不打印任何 FAIL 明细
- 根因：pre-push 通过条件是 `grep -q "违规: 0"`，但 Python 校验引擎成功路径只输出「全部通过」——摘要行仅失败时才打印，该字符串在成功输出中不存在，**pre-push 在任何情况下都不会放行**；同族缺陷：gov-update-metrics.sh 按 `^  违规:` 提取违规数也匹配不到，「校验违规」指标长期恒为 `?`。两处消费者依赖旧版 scripts/docs/validate.sh 的文案，Python 引擎重构时未同步输出契约；仓库此前无远程、指标从未核对，缺陷潜伏
- 解决：摘要行恒定输出「  违规: N 项」（两空格缩进匹配 metrics 既有锚点）+ pre-push 通过判定改用退出码（与 pre-commit、CI 一致，解除文案耦合）+ 子进程契约测试钉住格式
- 防止复发：跨进程/跨脚本的输出契约必须显式定义并用测试锁定；重构生产者时先 grep 全部消费者（本例与问题 6 同一次推送暴露，再次印证「未在真实执行路径跑过的钩子不算验证」）

### 问题 8：进程内 pytest 夹具吞掉校验现场——FAIL 打印但违规计 0（DEC-007，实现 DEC-006 时追查发现）

- 现象：GOV-README-001 打印了 `[FAIL]`，摘要却是「违规: 0 项」、退出码 0；直接单测调用该函数 errors len=1，全量运行却为 0
- 根因：GOV-TEST-005 用 `pytest.main()` 在 validate 进程内跑测试，负向测试的 autouse 夹具 `_clean_errors` 每个测试前后 `core.errors.clear()`，清掉了宿主进程在 pytest 阶段之前累积的全部违规——**全量模式下早期规则（R0/STRUCT/CONTENT/PROTO/SCRIPT/README）全部假阴性**，CI docs-validate 正是全量模式；pre-commit 走 QUICK 跳过 pytest 才侥幸未受影响
- 解决：夹具改快照-恢复语义（进入时快照+清空保持隔离，结束时恢复现场）+ 元测试锁住「宿主现场错误必须幸存」
- 防止复发：进程内嵌套执行测试时，任何"清理共享状态"的夹具都必须快照-恢复而非无脑清零；**自相矛盾的输出（FAIL 与 违规: 0 并存）是状态被中途篡改的直接证据，见到就追**

### 问题 9：R0 两处检查在干净 checkout 上必然失败（DEC-008，T-215 干净克隆模拟 CI 发现）

- 现象：`git clone` 后跑 validate，报「未找到 handoff 反思记录」；追查发现该 `fail; return` 还掩盖了第二处必然失败「Git hooks 未安装」——即使 Actions 账户问题解决，CI 每次也会红
- 根因：`.cache/context.jsonl` 被 gitignore（不入库）、`.git/hooks` 不随 clone 走（本机状态），两者在任何干净 checkout 天然缺失；R0 对执行环境不做区分地强制。GOV-CONTENT-008 对同一文件缺失时是 `[SKIP]`，同族检查两种行为
- 解决：handoff 检查加 MEMORY_FILE 存在性守卫（对齐 008 先例，文件存在无 handoff 仍 FAIL）；hooks 检查加 `CI` 环境变量守卫（CI 跳过，本机仍强制安装）；4 个分流测试锁双向语义
- 防止复发：**校验上线前先在干净克隆上跑一遍**——本地全绿只证明它认识你的机器；`fail; return` 提前返回会掩盖后续检查，修第一处时必须手动排查同函数内的连带失败

### 问题 10：session-end 上下文缓存静默失败——PROTO-002 永远无法被自己刷新（DEC-009，人类批准执行 session-end 时实测暴露）

- 现象：完整跑完 session-end 六步、显示「交接完成」，但 PROTO-002 仍报 36 小时，context.jsonl 无新 handoff 条目
- 根因：save handoff 漏传必填字段 `to`（REQUIRED_FIELDS_handoff 含5字段），save 拒写报错到 stderr，调用处 `2>/dev/null || true` 把错误和失败双重吞掉——**收尾链路的时钟永远无法自我刷新**；同处 `from="claude"` 硬编码导致 opencode session 一律署名 claude
- 解决：补 `to="next-ai"`；from 按 OPENCODE/CLAUDECODE 环境变量检测（本机实测 OPENCODE=1 存在），兜底 unknown-ai；静态契约测试解析两个脚本的字段声明与调用块，断言必填字段全覆盖
- 防止复发：**「显示完成」和「实际完成」之间隔着所有被 2>/dev/null 吞掉的错误**——链路型脚本每一步的成败必须可被下游观测（本例由 PROTO-002 这个下游规则观测到）；跨脚本的参数契约要么用测试钉住，要么让被调方校验时把错误裸露给调用方决策

## 3. 待完成/已知问题

- [x] 全量校验未运行：本 session 无 shell 工具，`gov-validate.sh`、`pytest`、`bash -n` 需由人类在提交前执行（DEC-002 假设 A3）——已随后续 session 实测 shell 可用并全部跑通
- [x] Claude Code 兼容性（DEC-003 假设 A2）——官方文档确认 v2.1.233 直接读取 AGENTS.md，仅 /memory 不列出（v2.1.280 起列出），软链为纯兜底
- [ ] GOV-SCRIPT 族缺 `bash -n` 语法检查，与旧文档声称的 R7 能力不符——按实标注后，增加语法检查列为后续任务
- [ ] gov_validate_skills.py 引用他项目资产（.claude/skills/ 阅读、data/learning-log.db），模板应剥离该模块或改为可选插件
- [ ] mkdocs.yml 同步后需手动确认导航结构完整

## 4. 关键发现

- 模板项目的特点是无运行时依赖，所有工作产出都是文档和脚本。这意味着"测试"的对象是校验规则和脚本的正确性，而不是代码逻辑。
- DEC-001 中记录的"人类枢纽闭环"模式在本 session 中得到了完整验证：用户确认方向后才继续工作，没有 AI 自主决策的偏差。
- lifecycle 文档的填充顺序很重要：PRD 定义目标 → Architecture 描述设计 → TaskGraph 分解任务 → DevNotes 记录过程 → Test 验证 → Metrics 量化。这个顺序就是生命周期 10 步中 "A"（执行）阶段的自然展开。
- **规则表比规则腐化得更快（DEC-002）**：modules 源码早已从 R1~R20 重命名为 GOV-* 族，但 AGENTS.md 速查表、docstandard 门控表、多处注释仍引用旧编号，且没有任何检查盯着"文档编号是否存在"。负向测试只能证明规则活着，证明不了文档写对——文档与源码的对齐目前只能靠定期静态审阅。
- **协议文件的游离围栏是最隐蔽的一类漂移**：一个多余的 ``` 让校验规则速查表整节渲染成代码块，目录结构、grep 关键词都正常，肉眼扫视发现不了，只有逐行读围栏配对才能发现。

## 4.5 变更记录（v4）

- 2026-10-16（DEC-011）：包坐标整体迁移 com.qingke.health → com.xcwk.scalehub。
  过程记录：目录迁移中 zsh 参数替换的转义斜杠产生了字面反斜杠目录链，改用
  Python 做确定性重放修复（46 文件归位、9 处怪目录清除）。Room schema 快照
  删除后按新包名重新导出。构建成功、21 测试全绿。

## 5. 版本历史

| 版本 | 日期 | 变更摘要 |
|------|------|---------|
| v4 | 2026-10-16 | DEC-011 公司全限定名迁移 com.xcwk.scalehub |
| v3 | 2026-10-14 | DEC-003 协议文件统一更名 AGENTS.md：全仓操作性引用同步，历史文档保留旧名 |
| v2 | 2026-10-13 | DEC-002 模版自一致性修复：文档漂移、速查表重写、负向测试、锁收敛 |
| v1 | 2026-07-10 | 初始开发笔记：模板打磨过程记录 |
