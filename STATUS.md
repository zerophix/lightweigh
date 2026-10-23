# 项目状态

> 由 generate-status.py 自动生成。最后更新: 2026-10-23T08:28:25Z
> 新 AI 启动时: `cat STATUS.md && bash scripts/session/ses-context-load.sh`

---

## 项目快照

| 字段 | 值 |
|------|-----|
| 名称 | 轻衡健康 Android（lightweigh） |
| 版本 | 0.1.0 |
| 文档语言 | zh-CN |
| Git 分支 | fix/gov-proto-rule-id-collision |
| 最后提交 | 905bffa chore(session): handoff after PR 31 governance merge |
| 文档校验 | 运行: `bash scripts/governance/gov-validate.sh` |

## AI 交接

> 上一个 AI session 的上下文。新 AI 启动时**必须阅读此节**。

| 字段 | 值 |
|------|-----|
| 来自 | opencode |
| 时间 | 2026-10-23 |
| 关键文件 | .ai/tasks.json,docs/ai/inbox/ |

**上下文：**

> 完成: 原型驱动开发与文档治理闭环全部落地并合入 master（PR #31, merge 3df489a）:
1) 原型闭环: gov-prototype-verify.py + GOV-PROTO 校验 + 34 任务补 prototype_ref/target_files/module/ac_refs/prd_ref/wave + skill prototype-dev
2) 文档闭环: GOV-DOCS-001~005 + gov-validate --fix 自愈 + skill docs-manage + 29 条测试
3) PR 模板: 补上规范强制但缺失的 .github/PULL_REQUEST_TEMPLATE.md + 7 条 guard-safe 测试
4) DEC-057 (治理从文档约束升级为脚本门禁)、DEC-058 (CI 变更范围白名单)
5) CI 慢因修复: gov-ci-scope.sh 单一真源 + 15 条负向测试，非构建类路径不再白跑全量 gradle
6) Notion 六阶段文档体系: 新增 04-研发/05-测试/06-发布/07-运维 + 9 篇专业文档 + 真源归属规则
7) 本地已切回 master 并 ff，feature 分支本地已删，gov-validate 0 违规 / 125 测试通过 | 决策: 1) 强制力介质选 validator 脚本而非 AGENTS.md —— 治理设计约束 Q2: 后者最易写也最易被忽略，本次全部规则挂进 gov-validate 三层闸门 (pre-commit / gov-local-ci / GitHub CI)
2) 自愈准入判据: 自愈安全性 = 改错了会不会被同一条规则立刻抓到。GOV-DOCS-004 计数同步是自洽的 (改错下次仍红) 故允许 --fix；005 时间戳是自噬的 (无脑改今日则规则永久失效) 故必须绑 git diff 判定内容真有变更；统一边界为只重写已有锚点、不新增结构
3) PR 模板刻意不含 allow-no-tests / allow-schema-change 字面量，也不含 DEC 加三位数字 —— 两个 guard 靠匹配 PR body 放行，模板自带即永久静默放行；首次写时我自己踩中 DEC-048，靠变异测试抓回
4) CI 范围判定用白名单而非黑名单: 白名单外默认 code=true，新增构建目录也不会漏；.github/ 故意不入白名单，改 CI 本身仍保守构建
5) 把内联在 ci.yml 的正则抽为可测脚本，否则改白名单时无人能回答会不会漏源码
6) 三个工具定位: Notion 管为什么做什么谁定的 (结构化真源)、仓库 docs/ 管怎么做与机器校验 (承重、可重建性差)、幕布管还在想的事 (迁完即弃)

**下一步：**

> 修 GOV-PROTO 撞号: 把 gov_validate_prototype.py 的规则改名为不与 gov_validate_protocol.py 冲突的前缀 (如 GOV-PTASK-001~007)，同步 skill/binding/GC 文档/测试中的引用

## 最近动态

最近 5 次提交：

```
905bffa chore(session): handoff after PR 31 governance merge
3df489a Merge pull request #31 from zerophix/feat/gap-closure-prototypes
fa3555e ci(governance): route ci scope check through gov-ci-scope (DEC-058)
4b142b2 docs(decisions): add DEC-057 for prototype and docs gates
7074b2d ci(governance): add guard-safe pull request template and checks
```

## 工程指标

| 指标 | 数量 |
|------|------|
| 决策日志 (DEC) | 59 |
| 设计笔记 (NOTE) | 10 |
| 开发指南 (GC) | 9 |
| 技术参考 (REF) | 1 |

最近决策：

- [DEC-059](docs/ai/decisions/DEC-059.md): DEC-059: 原型任务规则改名 GOV-PTASK，消除与 AI 协议规则的编号碰撞
- [DEC-058](docs/ai/decisions/DEC-058.md): DEC-058: CI 变更范围白名单扩展，非构建类路径不再触发重型构建
- [DEC-057](docs/ai/decisions/DEC-057.md): DEC-057: 原型与文档治理从文档约束升级为脚本门禁

最近笔记：

- [NOTE-20261022-002](docs/notes/NOTE-20261022-002.md): 轻衡 App 完整页面清单与原型对照
- [NOTE-20261022-001](docs/notes/NOTE-20261022-001.md): 轻衡 App 设计体系分析与登录原型规范
- [NOTE-20261021-001](docs/notes/NOTE-20261021-001.md): Chipsea_BLE 体脂秤协议逆向分析 — 完整技术笔记

## 健康检查

| 检查项 | 状态 | 详情 |
|-------|------|------|
| 本地门禁 (gov-local-ci) | 通过 | local-ci PASS @ 2026-10-23T06:18:33Z sha=39a2c7d branch=feat/gap-closure-prototypes [gradlew build:ok,gov-validate:ok,gov-custom-checks:ok,gov-secrets-scan:ok,gov-schema-guard:ok,gov-test-guard:ok] |
| GitHub Actions | 无数据 | gh 不可用或无最近 run |
| 文档校验 | 通过 | 来自 local-ci @ 2026-10-23T06:18:33Z sha=39a2c7d |
| Git 钩子 | 通过 | 安装: bash scripts/bootstrap/bs-install-hooks.sh |

## 反思

> 上次 AI session 的经验教训。新 AI 启动时必读。

1) 强制力介质选 validator 脚本而非 AGENTS.md —— 治理设计约束 Q2: 后者最易写也最易被忽略，本次全部规则挂进 gov-validate 三层闸门 (pre-commit / gov-local-ci / GitHub CI)
> 2) 自愈准入判据: 自愈安全性 = 改错了会不会被同一条规则立刻抓到。GOV-DOCS-004 计数同步是自洽的 (改错下次仍红) 故允许 --fix；005 时间戳是自噬的 (无脑改今日则规则永久失效) 故必须绑 git diff 判定内容真有变更；统一边界为只重写已有锚点、不新增结构
> 3) PR 模板刻意不含 allow-no-tests / allow-schema-change 字面量，也不含 DEC 加三位数字 —— 两个 guard 靠匹配 PR body 放行，模板自带即永久静默放行；首次写时我自己踩中 DEC-048，靠变异测试抓回
> 4) CI 范围判定用白名单而非黑名单: 白名单外默认 code=true，新增构建目录也不会漏；.github/ 故意不入白名单，改 CI 本身仍保守构建
> 5) 把内联在 ci.yml 的正则抽为可测脚本，否则改白名单时无人能回答会不会漏源码
> 6) 三个工具定位: Notion 管为什么做什么谁定的 (结构化真源)、仓库 docs/ 管怎么做与机器校验 (承重、可重建性差)、幕布管还在想的事 (迁完即弃)

