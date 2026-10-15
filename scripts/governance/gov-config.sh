#!/usr/bin/env bash
# scripts/governance/gov-config.sh
# 项目共享配置 — 被 pre-commit 等引用
# 模版使用此默认值，setup.sh 安装时交互式配置

# 源码目录（git diff 检测用，空格分隔多个目录）
SRC_DIRS="domain application infrastructure app"

# 默认源码前缀（用于 grep 匹配，多个用空格分隔 grep 模式）
SRC_PREFIXES="^domain/ ^application/ ^infrastructure/ ^app/"
