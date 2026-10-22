# 项目状态

> 由 generate-status.py 自动生成。最后更新: 2026-10-22T03:51:15Z
> 新 AI 启动时: `cat STATUS.md && bash scripts/session/ses-context-load.sh`

---

## 项目快照

| 字段 | 值 |
|------|-----|
| 名称 | 轻刻健康 Android（qk-health-v2） |
| 版本 | 0.1.0 |
| 文档语言 | zh-CN |
| Git 分支 | feat/design-prototypes |
| 最后提交 | 8ed0d77 docs(governance): sync rule table for removed reflection gate |
| 文档校验 | 运行: `bash scripts/governance/gov-validate.sh` |

## AI 交接

> 上一个 AI session 的上下文。新 AI 启动时**必须阅读此节**。

| 字段 | 值 |
|------|-----|
| 来自 | opencode |
| 时间 | 2026-10-22 |
| 关键文件 | .ai/tasks.json,docs/ai/inbox/ |

**上下文：**

> 完成: 修复 Chipsea_BLE 协议识别错误 (版本 0x10 误识别为 OKOK), 添加名称感知匹配逻辑, 验证 46 条过程数据包, 构建 APK, 创建 DEC-044 和 NOTE-20261021-001 | 决策: Chipsea_BLE 广播版本 0x10 (OKOK 格式) 与 GATT 层 Chipsea 协议不一致. 修改 marker() 检查设备名称含 'Chipsea' 时跳过 OKOK 版本排除, 保持纯 OKOK 设备不受影响. 选择方案 A (名称感知), 评分 5/5.

**下一步：**

> 在 Android 真机安装 APK 验证

## 最近动态

最近 5 次提交：

```
8ed0d77 docs(governance): sync rule table for removed reflection gate
b344d61 refactor(governance): remove forced README reflection, rewrite README
6d67806 docs(design): add design system notes and 154-screen prototypes
aefa7b7 fix: Chipsea_BLE protocol matching + governance fixes
21889fc fix: Chipsea_BLE protocol matching for hybrid broadcast version 0x10
```

## 工程指标

| 指标 | 数量 |
|------|------|
| 决策日志 (DEC) | 47 |
| 设计笔记 (NOTE) | 10 |
| 开发指南 (GC) | 6 |
| 技术参考 (REF) | 1 |

最近决策：

- [DEC-047](docs/ai/decisions/DEC-047.md): DEC-047: 多 AI 并发协作门禁与反思约束退役
- [DEC-046](docs/ai/decisions/DEC-046.md): DEC-046: 本地门禁接入 gov-custom-checks 与 gov-local-ci
- [DEC-045](docs/ai/decisions/DEC-045.md): DEC-045: 文档事实对齐与蒸馏规则统一（授权门禁减负后续）

最近笔记：

- [NOTE-20261022-002](docs/notes/NOTE-20261022-002.md): 轻衡 App 完整页面清单与原型对照
- [NOTE-20261022-001](docs/notes/NOTE-20261022-001.md): 轻衡 App 设计体系分析与登录原型规范
- [NOTE-20261021-001](docs/notes/NOTE-20261021-001.md): Chipsea_BLE 体脂秤协议逆向分析 — 完整技术笔记

## 健康检查

| 检查项 | 状态 | 详情 |
|-------|------|------|
| 构建 | 跳过 | skipped |
| 文档校验 | 就绪 | 运行: bash scripts/governance/gov-validate.sh |
| Git 钩子 | 通过 | 安装: bash scripts/bootstrap/bs-install-hooks.sh |

## 反思

> 上次 AI session 的经验教训。新 AI 启动时必读。

Chipsea_BLE 广播版本 0x10 (OKOK 格式) 与 GATT 层 Chipsea 协议不一致. 修改 marker() 检查设备名称含 'Chipsea' 时跳过 OKOK 版本排除, 保持纯 OKOK 设备不受影响. 选择方案 A (名称感知), 评分 5/5.

