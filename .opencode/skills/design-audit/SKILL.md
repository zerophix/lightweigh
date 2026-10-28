# Design Audit Skill (Wrapper)

## 来源
原始实现: https://github.com/anthropics/skills/tree/main (Impeccable)
由 Anthropic 维护，本项目仅作为 **本地调用入口包装器**。

## 使用

```
# 在对话中输入:
/design-audit

# 或指定场景:
/design-audit deep       # 深度审查当前页面
/design-audit component  # 审查指定组件
```

## 工作原理

```
用户输入 /design-audit
  → 触发本地 skill (本文件声明)
  → OpenCode 从上下文提取当前 UI 代码
  → 构造 Prompt 发送给 Impeccable
  → 得到布局优化建议
  → 格式化写回对话
```

## 注意事项

- **不复制代码**: 本 skill 不下载 Anthropic 的 repo，只在调用时通过 Prompt 引用其能力。
- **不保存状态**: 每次调用均从远程获取最新建议，无本地缓存。
- **只读建议**: 不会自动修改你的代码，只有人工决定是否采纳。

## 更新方法

无需更新本 skill。Impeccable 的改进由 Anthropic 在 GitHub 上维护，
你只需确保可以访问 https://github.com/anthropics/skills 即可。
