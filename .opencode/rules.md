# OpenCode Rules — AI 协作协议（继承 AGENTS.md）

## 启动

- `cat STATUS.md` — 查看项目全景、AI 交接、反思
- `bash scripts/session/ses-context-load.sh` — 加载 AI 上下文
- 阅读 `docs/ai/constraints/index.md` — 行为边界

## 决策流转

```
发现 → NOTE（未定型） → DEC（追加写） → lifecycle（覆盖）
  → wf-promote.sh → 黄金标准 → gov-validate.sh → commit
```

创建决策：
```bash
bash scripts/governance/gov-next-decision.sh --create
# 编辑 docs/ai/decisions/DEC-XXX.md
# DEC 只追加，不覆盖
```

晋升：
```bash
bash scripts/governance/gov-sync-standards.sh --dec DEC-XXX
```

## 文件规范

**允许写入的目录：**

| 目录 | 命名模式 |
|------|---------|
| `docs/guides/` | `GC-{slug}.md` |
| `docs/reference/` | `REF-{slug}.md` |
| `docs/archive/` | `ARCH-{slug}.md` |
| `docs/notes/` | `NOTE-YYYYMMDD-NNN.md` |
| `docs/ai/decisions/` | `DEC-XXX.md` |

**禁止：** old/legacy/vN 路径，v1/v2/bak/backup/copy 后缀

## 校验

```bash
bash scripts/governance/gov-validate.sh       # 严格模式
bash scripts/governance/gov-validate.sh --fix # 自动修复
```

提交前 0 违规，所有 FAIL 必须修复。

## 反思

session 结束时写入经验教训到 context.jsonl：

```bash
bash scripts/session/ses-context-cache.sh save handoff \
  from="opencode" to="*" \
  context="做了什么" \
  next="下一步该做什么" \
  key_files="改了哪些文件" \
  lessons="具体反思：哪里做错了、什么坑要避免"
```

> 反思必须 ≥50 字，含信号词（错/坑/教训/避免/本应），不能复用旧内容。
> 这是硬约束（R0），不写无法提交。

## 参考

- `AGENTS.md` — 完整协议
- `docs/guides/GC-decision-flow.md` — 决策流转
- `docs/ai/guides/consensus.md` — 多 AI 共识
