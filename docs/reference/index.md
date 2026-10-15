# 技术参考

> 接口、协议、规范和自动化工具的技术标准。
> 不可直接编辑。由生命周期文档或 DEC 晋升后写入。

---

## 1. 项目技术栈

| 层 | 技术 | 说明 |
|----|------|------|
| 文档站点 | MkDocs + Material | Python 静态站点生成器，用于浏览全部文档 |
| 校验引擎 | Python 3 + Bash | 文档校验规则（gov-validate.sh，规则族见 AGENTS.md 速查表） |
| 自动化脚本 | Bash Shell | 生命周期管理、接力协作、晋升、指标跟踪 |
| 版本控制 | Git + GitHub | 代码托管和协作 |
| CI/CD | GitHub Actions | 文档校验 + 站点构建 + 部署到 GitHub Pages |

---

## 2. 脚本接口清单

| 脚本 | 路径 | 功能 | 调用方 |
|------|------|------|--------|
| validate.sh | `scripts/governance/gov-validate.sh` | 全量文档校验 | 手动 / pre-commit hook |
| lifecycle.sh | `scripts/workflow/wf-lifecycle.sh` | 10 步生命周期编排 | AI 手动调用 |
| session-begin.sh | `scripts/session/ses-session-begin.sh` | AI 启动上下文加载 | AI 新 session 启动 |
| session-end.sh | `scripts/session/ses-session-end.sh` | AI 收尾接力交接 | AI session 结束 |
| promote.sh | `scripts/workflow/wf-promote.sh` | 晋升到黄金标准 | lifecycle end |
| update-status.sh | `scripts/governance/gov-update-status.sh` | STATUS.md 自动生成 | lifecycle 后 / 手动 |
| update-metrics.sh | `scripts/governance/gov-update-metrics.sh` | 指标数据更新 | lifecycle 后 / 手动 |
| task-board.sh | `scripts/workflow/wf-task-board.sh` | AI 任务看板管理 | AI 协作 |
| inbox.sh | `scripts/workflow/wf-inbox.sh` | AI 收件箱管理 | AI 接力 |
| next-decision.sh | `scripts/governance/gov-next-decision.sh` | 创建 DEC 决策模板 | AI 决策时 |

---

## 3. 参考文档索引

| 参考文档 | 内容概述 |
|---------|---------|
| [脚本接口规范](REF-api-spec.md) | 项目自动化脚本的标准命令行接口 |

预留分类：

- **数据格式**（REF-data-model.md）：项目文档的元数据模型
- **部署配置**（REF-deployment.md）：MkDocs 站点构建和 GitHub Pages 发布

---

## 来源: docs/ai/decisions/DEC-001

从 DEC-001 和项目架构文档晋升而来。
