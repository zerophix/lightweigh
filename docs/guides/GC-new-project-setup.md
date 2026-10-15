# 新项目接入指南

> 从头开始的新项目，一键安装本模版。

---

## 前置条件

| 条件 | 说明 |
|------|------|
| Git | 仓库已初始化或准备初始化 |
| Python 3 | ≥ 3.8，校验引擎所需 |
| MkDocs (可选) | 文档站点，`conda env create -f environment.yml` |

---

## 安装步骤

```bash
# 1. 克隆模版
git clone https://github.com/neking/ai-project-template.git my-project
cd my-project
rm -rf .git          # 断开模版 Git 历史
git init             # 初始化自己的仓库

# 2. 运行安装脚本
bash setup.sh

# 3. 交互式填写项目信息
#    Project Name [{{Project Name}}]: My App
#    Project Description: ...
#    Author: Alice
#    GitHub URL: https://github.com/alice/my-app
#    Source dirs (space separated) [src app lib packages]: src

# 4. 安装完成，提交初始代码
git add -A
git commit -m "feat(init): bootstrap with AI Project Collaboration Blueprint"
```

---

## setup.sh 自动完成

| 模块 | 说明 |
|------|------|
| 脚本 | `scripts/` 下所有治理/生命周期/Git 钩子脚本 |
| 文档骨架 | `docs/` 完整文档结构（全部是模板，等待填充） |
| 配置文件 | `AGENTS.md`、`mkdocs.yml`、`.language`、`.gitignore` |
| Git 钩子 | pre-commit / commit-msg / pre-push 自动安装 |
| 上下文 | `.cache/context.jsonl` 初始化，AI 记忆就绪 |
| 变量填充 | 项目名/描述/作者/GitHub 替换所有模板中的 `{{}}` |
| 源码目录 | 写入 `scripts/governance/gov-config.sh` |
| 校验 | 首次 `validate.sh` 确认一切合规 |

---

## 安装后验证

```bash
bash scripts/governance/gov-validate.sh                    # 0 违规
bash scripts/governance/gov-update-status.sh && cat STATUS.md  # 仪表盘
bash scripts/session/ses-context-cache.sh status        # 上下文已就绪
```

---

## 首次迭代

```bash
# 1. 写 PRD
vim docs/ai/lifecycle/01_PRD.md

# 2. 创建设计笔记
bash scripts/governance/gov-next-note.sh create "主题"

# 3. 创建决策日志
bash scripts/governance/gov-next-decision.sh --create
vim docs/ai/decisions/DEC-001.md

# 4. 写代码 → 更新 lifecycle 文档

# 5. 晋升到黄金标准
bash scripts/governance/gov-sync-standards.sh --dec DEC-001

# 6. 校验 + 提交
bash scripts/governance/gov-validate.sh
git add -A
git commit -m "feat(xxx): description"

# 7. Session 结束 → 接力交接
bash scripts/session/ses-session-end.sh
```

---

## 参考文档

- [决策流转规范](GC-decision-flow.md) — NOTE→DEC→lifecycle→promote→golden 完整链路
- [AI 工作流程](../ai/guides/workflow.md) — 10 步生命周期详解
- [文档标准](../ai/guides/docstandard.md) — 文档格式和质量规范
- [约束规则](../ai/constraints/index.md) — 文件命名和行为边界
