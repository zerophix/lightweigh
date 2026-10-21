# 多 AI 并发协作规则（精简版）

## 互斥机制

```bash
# 领任务（30 分钟 TTL，到期自动释放）
bash scripts/workflow/wf-lock.sh T-043

# 检查任务是否被锁
bash scripts/workflow/wf-lock.sh --check T-043

# 释放任务
bash scripts/workflow/wf-unlock.sh
```

锁文件 `.ai/task-lock.json`，30 分钟过期自动清理。**领锁后才开始写代码**。

## 提交流程

```bash
# 1. 领任务
bash scripts/workflow/wf-lock.sh T-043

# 2. 写代码（在功能分支上）
git checkout -b feat/t043-habit

# 3. 声明文件归属
bash scripts/session/ses-claim-staged.sh

# 4. 提交（pre-commit 自动跑编译检查）
git commit -m "feat(habit): add habit management page"

# 5. 合入 master
git checkout master
git merge --no-ff feat/t043-habit

# 6. 释放任务
bash scripts/workflow/wf-unlock.sh
```

## 治理规则（只保留这些）

| 检查 | 何时 | 失败处理 |
|------|------|---------|
| master 禁直接提交 | pre-commit | 拦截，必须走分支+merge |
| 会话归属 | pre-commit | 拦截，需先 `ses-claim-staged.sh` |
| 编译通过 | pre-commit（有源码变更时） | 拦截，修复后再提交 |
| 测试通过 | 手动/CI | 不拦截提交，但合并前应跑 |
| 文件名 ASCII | 手动 | 不拦截，但应修复 |

## 已移除的规则（不再强制）

- ~~每次提交必须带 DEC 文件~~ → 改为「合并前补 DEC」
- ~~每次提交必须带 DevNotes~~ → 改为「合并前补」
- ~~僵尸任务检测~~ → 由互斥锁的 TTL 替代
- ~~session ledger 追踪~~ → 已加入 .gitignore
- ~~gov-code-check 逐条扫描~~ → 编译器能发现的不再重复检查
- ~~测试注册表强制~~ → 改为 WARN 提示

## 任务看板

```bash
bash scripts/workflow/wf-task-board.sh list    # 查看任务
bash scripts/workflow/wf-task-board.sh next    # 设置下一步
```

任务状态：`pending` → `in_progress`（领锁后） → `done`（合并后）。
