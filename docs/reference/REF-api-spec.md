# 脚本接口规范

> 项目自动化脚本的标准命令行接口规范。
> 所有 `scripts/` 下的可执行脚本遵循此约定。

---

## 1. 通用约定

| 规范项 | 标准 |
|--------|------|
| shebang | `#!/usr/bin/env bash` |
| 错误处理 | `set -euo pipefail` |
| 帮助 | `--help` 或 `-h` 参数 |
| 退出码 | 0=成功, 1=一般错误, 2=参数错误 |
| 日志 | stdout 输出结果, stderr 输出错误 |

## 2. 核心脚本 CLI

### validate.sh
全量文档校验（规则清单与数量以 `gov-validate.sh` 实时输出为准）。

```
用法: bash scripts/governance/gov-validate.sh [--fix]
  --fix    自动修复可修复项（emoji、空白符等）
```

### lifecycle.sh
10 步生命周期编排。

```
用法: bash scripts/workflow/wf-lifecycle.sh <step>
  step   D|R|E|T|A|V|M|L|D|P
```

### promote.sh
晋升内容到黄金标准文件。

```
用法: cat content | bash scripts/workflow/wf-promote.sh <target> --decision DEC-XXX

参数:
  --decision DEC-XXX  来源决策编号（必填）
  --verify             写入后自动运行 validate.sh
  --append             追加模式（不覆盖，用于 changelog）
  --auto <source>      从生命周期文档自动提取摘要

允许目标:
  docs/architecture/index.md
  docs/guides/index.md
  docs/reference/index.md
  docs/changelog/index.md
  docs/notes/index.md
  docs/reference/REF-{slug}.md
```

### session-begin.sh
AI 启动上下文加载。

```
用法: bash scripts/session/ses-session-begin.sh
```

### session-end.sh
AI 收尾接力交接。

```
用法: bash scripts/session/ses-session-end.sh
```

### task-board.sh
AI 任务看板管理。

```
用法: bash scripts/workflow/wf-task-board.sh <command> [args]

命令:
  list             查看任务看板
  add "描述"      新增任务
  done T-001      标记完成
  next "描述"     设置下一步
  confirm          确认下一步方向
```

### inbox.sh
AI 收件箱管理。

```
用法: bash scripts/workflow/wf-inbox.sh <command> [args]

命令:
  list             查看所有消息
  read <id>        读取消息
  send "主题" "内容"  留信
  mark <id>        标记已读
```

### update-status.sh
STATUS.md 自动生成。

```
用法: bash scripts/governance/gov-update-status.sh
```

### update-metrics.sh
指标数据更新。

```
用法: bash scripts/governance/gov-update-metrics.sh [--custom <path>]
  --custom <path>  运行自定义指标脚本
```

## 3. 钩子系统

指标钩子位于 `scripts/governance/metrics-hooks/` 目录，被 `gov-update-metrics.sh` 自动扫描执行。

| 钩子 | 用途 |
|------|------|
| 10-git-stats.sh | Git 基础统计（提交数、分支数、贡献者） |
| 20-doc-counts.sh | 文档目录计数（architecture、guides、reference、notes） |

钩子输出格式：管道分隔的 `| key | value | source |`

## 来源: ai/decisions/DEC-001
