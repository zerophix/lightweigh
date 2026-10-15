# 任务图

> DEC-002 模版自一致性修复 + DEC-003 协议文件统一更名 AGENTS.md：文档漂移、规则速查表失真、负向测试补齐。
> 覆盖型文档：每次迭代重写。

---

## 1. 优先级队列

### P0（关键路径）

| ID | 任务 | 负责人 | 状态 | 工时 |
|----|------|--------|------|------|
| T-201 | 去数字化：11 处文件删除写死的校验项数 | AI | 已完成 | 0.5h |
| T-202 | AGENTS.md 规则速查表按 8 规则族重写 | AI | 已完成 | 0.4h |
| T-203 | 删除 AGENTS.md 游离围栏（后半文档落入代码块） | AI | 已完成 | 0.1h |
| T-204 | 修 gov-generate-status.py 错误命令路径 + STATUS 同步 | AI | 已完成 | 0.2h |
| T-205 | R0f 白名单补 session-marker.json / session.lock | AI | 已完成 | 0.1h |
| T-211 | scripts/docs/ 旧路径全量修复：gov-sync-standards 3 个功能路径 + .claude 4 个 hook 与 9 个 skill（R0c 必 FAIL） | AI | 已完成 | 0.6h |
| T-212 | 协议文件统一更名为 AGENTS.md + 全仓引用同步与旧名清除（DEC-003） | AI | 已完成 | 0.5h |
| T-213 | R0 反思复用检测上下文感知修复 + 2 个回归用例（DEC-004，首次 push 被拦发现） | AI | 已完成 | 0.3h |
| T-214 | validate 输出契约修复：摘要行恒定输出 + pre-push 改退出码 + 契约测试（DEC-005，同次 push 发现） | AI | 已完成 | 0.3h |

### P1（重要）

| ID | 任务 | 负责人 | 状态 | 工时 |
|----|------|--------|------|------|
| T-206 | 负向测试 tests/unit/test_governance_negative.py（6 用例） | AI | 已完成 | 0.5h |
| T-207 | 原子锁迁入 gov-next-decision.sh + wf- 双实现收敛 wrapper | AI | 已完成 | 0.4h |
| T-208 | setup.sh 步骤编号统一 + .gitignore .cache negation | AI | 已完成 | 0.2h |
| T-209 | lifecycle 文档同步（03/04/05/06 + 索引 + changelog） | AI | 已完成 | 0.3h |
| T-210 | 全量校验 + 提交（本 session 无 shell，需人类执行） | 人类 | 待开始 | 0.3h |

## 2. 依赖关系

```
T-201..T-205(文档漂移 P0) ──┐
T-206(负向测试) ────────────┤
T-207(锁与 wrapper) ────────┼──→ T-209(lifecycle 同步) ──→ T-210(校验+提交)
T-208(杂项) ────────────────┤
T-211(旧路径修复 R0c) ──────┘
```

## 3. 当前状态

- 已完成：T-201 ~ T-209、T-211（静态编写完成）
- 待开始：T-210 全量校验 + 提交（由人类执行 gov-validate.sh / pytest / bash -n）
- 阻塞：T-210 依赖人类环境（本 session 无 shell 工具）

## 4. 已完成

| ID | 任务 | 完成时间 | 交付物 |
|----|------|---------|--------|
| T-201 | 去数字化 | 2026-10-13 | README/AGENTS/.clinerules/.cursorrules/docs 索引等 11 处 |
| T-202 | 速查表重写 | 2026-10-13 | AGENTS.md 校验规则速查 |
| T-203 | 围栏修复 | 2026-10-13 | AGENTS.md CI/CD 章节 |
| T-204 | 生成器修复 | 2026-10-13 | gov-generate-status.py + STATUS.md |
| T-205 | 白名单补齐 | 2026-10-13 | gov_validate_status.py R0f |
| T-206 | 负向测试 | 2026-10-13 | tests/unit/test_governance_negative.py + registry |
| T-207 | 锁与收敛 | 2026-10-13 | gov-next-decision.sh + 2 个 wrapper |
| T-208 | 杂项 | 2026-10-13 | setup.sh + .gitignore |
| T-209 | lifecycle 同步 | 2026-10-13 | 03/04/05/06 + 索引 + changelog |
| T-211 | 旧路径全量修复 | 2026-10-13 | gov-sync-standards.sh、gov-generate-index.sh、.claude/settings.json + 9 个 skill 配置 |

## 5. 版本历史

| 版本 | 日期 | 变更摘要 |
|------|------|---------|
| v3 | 2026-10-13 | DEC-002 模版自一致性修复迭代 |
| v2 | 2026-07-21 | 黄金标准填充迭代：充实架构、创建 REF、刷新指标 |
| v1 | 2026-07-10 | 初始任务图：模板打磨迭代任务分解 |
