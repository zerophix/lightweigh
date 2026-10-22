## 摘要

<!-- 用 1~3 句说明为何需要此变更 -->

## 关联

- 任务 ID：<!-- T-xxx；无则写 n/a -->
- DEC：<!-- DEC-xxx；无则写 n/a 并说明为何不需要 -->
- AC：<!-- 任务 acceptance.status；或粘贴关键 then/DoD -->

## 合入前门禁（无 GitHub Pro — 必须本地完成）

- [ ] 已在本分支执行：`bash scripts/governance/gov-local-ci.sh`
- [ ] 下方粘贴 **PASS 摘要**（至少含末行 `PASS gov-local-ci` 或等价成功输出）

```
<!-- 粘贴 local-ci 关键摘要 -->
```

## 验收与安全

- [ ] 任务 AC / DoD 已覆盖（见 `docs/guides/GC-task-acceptance.md`）；无任务则说明验证步骤
- [ ] 未静默修改黄金向量（`domain/src/test/resources/golden/`）或 Room schema 快照；若有改动已写 DEC 并在此说明
- [ ] 无密钥 / 令牌 / 真机隐私日志入库
- [ ] 架构或协议变更已走 DEC → lifecycle → promote（如适用）

## 测试说明

<!-- 跑过的 Gradle 任务或手工步骤；真机项标明「待人类」 -->

## 风险 / 回滚

<!-- 已知风险；如何回滚 -->
