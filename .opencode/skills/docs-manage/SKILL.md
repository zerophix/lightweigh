---
name: docs-manage
description: >
  轻衡项目文档（docs/）的创建、修改、归位与校验流程。
  只要在 lightweigh 仓库里新建或改动 .md 文档、调整目录结构、写真源声明、
  处理索引收录，就使用本 skill。触发词：写文档、加文档、docs 目录、文档归属、
  index 没收录、断链、真源声明、文档过期。
---

# 文档管理（docs-manage）

把「这篇文档该放哪、叫什么、写什么标记、怎么算写对」变成一次就做对的流程，
而不是等 `gov-validate` 报错再返工。

**强制力**：`gov-validate` 的 GOV-DOCS-001~005（三层闸门：pre-commit / gov-local-ci / GitHub CI）。
本 skill 负责让你一次写对，脚本负责兜底。两者不是二选一。

## 铁律（不可绕过）

1. **先判归属，再动笔** —— Notion 还是仓库？判错就制造了"两份互相矛盾的最终需求"。
2. **新文档必须进 index** —— 不进索引的文档等于不存在，AI 找不到。
3. **与 Notion 并存的文档必须写「真源声明」** —— 明确哪边是权威。
4. **改了 tasks.json 的计数，同步改 `03_TaskGraph.md`** —— 摘要数字脱节会误导读者。
5. **不手改 `docs/reference/index.md`** —— 它自述由 DEC 晋升写入。

## 流程

```
要写 / 改文档
  │
  ├── 1. 判归属（Notion 还是仓库？）
  │     五问按顺序，第一个"是"的那行决定：
  │     被脚本/钩子/CI 读取 → 仓库    需要非技术角色编辑 → Notion
  │     必须与代码同 PR 变更 → 仓库    需要状态/评论/看板 → Notion
  │     要回溯构建依据哪份文档 → 仓库
  │
  ├── 2. 判目录（仓库侧，按开发流程六阶段）
  │     00-规范 / 01-产品 / 02-设计 / 03-原型
  │     04-研发 / 05-测试 / 06-发布 / 07-运维
  │     新增目录前：先改 DOC-目录与命名规范，再建目录
  │
  ├── 3. 判类型短码（{类型}-{主题}，中文主题用 - 连接）
  │     DOC 规范 · PRD 需求 · DEC 决策 · ROAD 计划 · DES 设计
  │     UX 交互 · UI 原型 · REV 评审 · MET 指标 · QA 测试 · DEF 缺陷
  │
  ├── 4. 写内容（首页三要素）
  │     ① 一句「本文解决什么问题」② 状态（草案/评审中/已定稿）③ 最后更新日期
  │     与 Notion 并存 → 额外写「真源声明」
  │     禁止 emoji、口号式标题、"最终版2" 这类命名
  │
  ├── 5. 收进索引
  │     新增 GC-*    → docs/guides/index.md 加一行
  │     新增 REF-*   → 不能手改，走 wf-promote.sh
  │     新增其他文档 → 对应目录 index.md
  │
  └── 6. 校验
        python3 -m pytest tests/unit/test_docs_governance.py -q
        bash scripts/governance/gov-validate.sh
```

## GOV-DOCS 会拦什么

| 规则 | 级别 | 触发条件 |
|------|:----:|---------|
| GOV-DOCS-001 | ERROR | `docs/guides/GC-*.md` 未被 `guides/index.md` 收录 |
| GOV-DOCS-001 | WARN | `docs/reference/REF-*.md` 未被 `reference/index.md` 收录（晋升待办） |
| GOV-DOCS-002 | ERROR | `docs/**/*.md` 的相对链接指向不存在的路径 |
| GOV-DOCS-003 | ERROR | `docs/index.md` 或 `01_PRD.md` 缺「真源声明」 |
| GOV-DOCS-004 | ERROR | `03_TaskGraph.md` 的 total / pending / 原型数 / wave 数 ≠ `tasks.json` |
| GOV-DOCS-005 | WARN | 入口文档缺 `更新时间：YYYY-MM-DD`，或超期 14 天 |

**ERROR 阻断合入，WARN 只提醒。**

## 新增文档 checklist

- [ ] 归属已判（Notion / 仓库），没制造第二份真相
- [ ] 目录与六阶段对应，没平铺在根下
- [ ] 命名符合 `{类型}-{主题}`，无 emoji、无口号
- [ ] 首页有「解决什么问题 + 状态 + 更新日期」
- [ ] 与 Notion 并存的写了「真源声明」
- [ ] 已收进对应 `index.md`（`REF-*` 除外）
- [ ] 相对链接全部可解析
- [ ] 改了任务计数则同步 `03_TaskGraph.md`
- [ ] `gov-validate` 0 违规

## 常用命令

```bash
# 单跑 docs 治理的负向测试
python3 -m pytest tests/unit/test_docs_governance.py -q

# 全量治理校验（含 GOV-DOCS）
bash scripts/governance/gov-validate.sh

# 只看 docs 相关告警
bash scripts/governance/gov-validate.sh 2>&1 | grep GOV-DOCS

# 全仓库相对链接体检
python3 -c "import os,re
bad=[]
for r,_,fs in os.walk('docs'):
  for f in fs:
    if not f.endswith('.md'): continue
    p=os.path.join(r,f)
    for m in re.finditer(r'\]\(([^)\s]+)\)', open(p,encoding='utf-8').read()):
      t=m.group(1)
      if t.startswith(('http','#','mailto')): continue
      t=t.split('#')[0]
      if t and not os.path.exists(os.path.normpath(os.path.join(r,t))): bad.append(f'{p} -> {t}')
print('断链:', bad or 'none')"
```

## 失败处理

| 报错 | 处理 |
|------|------|
| GOV-DOCS-001 guides | 在 `docs/guides/index.md` 加一行，或确认文件名是否该是 `GC-*` |
| GOV-DOCS-001 reference | 走 `wf-promote.sh` 晋升，**不要手改 index** |
| GOV-DOCS-002 断链 | 相对路径算错了：从所在文件目录起算，跨两级用 `../../` |
| GOV-DOCS-003 真源声明 | 复制标准句式：产品权威在 Notion / 工程权威在仓库 |
| GOV-DOCS-004 计数 | 先读 `tasks.json` 算准，再改摘要；不要反向改 tasks.json 迁就文档 |
| GOV-DOCS-005 过期 | 更新 `更新时间：YYYY-MM-DD`，同时确认内容确实还准确 |

## 能力边界

- 不为了过检查而改 index —— 收录的前提是文档本身该在那个目录。
- 不手改 `docs/reference/index.md` —— 它是晋升产物。
- 不在 Notion 和仓库各留一份不标权威的结论。
- 不为了让 GOV-DOCS-004 变绿而改 `tasks.json` —— 任务板是权威，摘要是跟随方。
- 语义质量（写得对不对、有没有道理）不在本 skill 的脚本能力内，需人工评审。

## 来源

规则 GOV-DOCS-001~005 源自 2026-10-23 手工 docs 巡检发现的 4 类真实问题；
反例测试见 `tests/unit/test_docs_governance.py`；归属规则全文见
`docs/guides/GC-decision-flow.md` 第 0 层。
