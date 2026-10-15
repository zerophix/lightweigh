# 测试

> 模板迭代的验证策略：文档校验替代代码测试。AI 提交代码前必须更新。
> 覆盖型文档：每次迭代重写。

---

## 1. 验证策略

本模板项目无运行时代码（纯 Shell/Python 脚本 + Markdown 文档），测试等价于**文档校验**和**脚本正确性验证**。

| 层级 | 覆盖范围 | 工具/框架 | 最低通过率 |
|------|---------|----------|-----------|
| 文档校验 | 所有 docs/ 文件结构、命名、语言、内容 | validate.sh (Python + Bash) | 100% (0 违规) |
| 脚本语法 | 所有 scripts/ 脚本 | bash -n / python -m py_compile | 100% |
| 脚本可执行 | migrate.sh/task-board.sh/promote.sh 等 | 实际运行验证 | 100% |
| mkdocs 构建 | 文档站点可正常构建 | mkdocs build | 构建成功 |
| git hook | pre-commit/commit-msg/pre-push | commit 测试 | 通过 |

## 2. 验证结果

本次迭代（DEC-002）在无 shell 执行能力的 session 中完成，bash / Python 修改均为静态编写，
以下命令在提交前由人类执行，结果回填本节：

```bash
# 本次迭代验证清单（DEC-002 假设 A3）
$ bash scripts/governance/gov-validate.sh
结果: 待运行（必须 0 违规才能提交）

$ find scripts/ -name "*.sh" -exec bash -n {} \;
结果: 待运行（本次新增/修改了 4 个 bash 文件）

$ python3 -m py_compile scripts/governance/gov-generate-status.py scripts/governance/modules/gov_validate_status.py tests/unit/test_governance_negative.py
结果: 待运行

$ python3 -m pytest tests/ -q
结果: 待运行（应含 6 个负向用例全部通过）
```

## 3. 新增验证用例

| 文件 | 验证内容 | 覆盖场景 |
|------|---------|---------|
| validate.sh | R0 系列 + GOV-* 全量校验 | 文档结构/命名/语言/emoji/记忆时效/索引一致性 |
| gov-validate.py | Python 编排引擎 + 8 个校验模块 | status/structure/content/protocol/scripts/tests/skills |
| tests/unit/test_governance_negative.py | 规则负向测试（DEC-002 新增） | GOV-STRUCT-001/002/003/004/006 故意违规必须 FAIL + 空白对照防误报 |
| pre-commit | 提交前自动修复+校验 | 缩进/尾空格/emoji/language/校验通过门控 |
| pre-push | 推送前分支校验 | 主干分支限制/DEC+DevNotes 审计/mkdocs 构建 |
| generate-index.py | 决策日志索引生成 | 索引完整性/编号连续性 |
| task-board.sh | 任务看板 CRUD + confirm | 添加/完成/确认/下一步设置 |
| session-end.sh | 接力交接输出 | 4 项必填/收件箱/任务板状态 |

## 4. 未覆盖边界

- **跨 session 接力完整性**：无法自动化验证 Session B 是否真的完成了 Session A 交接的任务。当前依赖 human-in-the-loop 确认。
- **--no-verify 绕过**：pre-commit hook 可以被 git 的 `--no-verify` 完全绕过。这是设计上的安全阀，无法通过钩子自身防御，只能通过审计痕迹事后发现。
- **散落文件清理验证**：migrate.sh 只扫描不自动清理，30 个待归档文件需要人工确认后再归档。

## 5. 版本历史

| 版本 | 日期 | 变更摘要 |
|------|------|---------|
| v2 | 2026-10-13 | DEC-002：新增治理负向测试，验证清单改为诚实的待运行状态 |
| v1 | 2026-07-10 | 初始验证计划：模板项目的文档校验体系 |
