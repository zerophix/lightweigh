#!/usr/bin/env bash
#
# setup.sh — 一键安装 AI Project Collaboration Blueprint
#
# 用法:
#   bash /path/to/ai-project-template/setup.sh            # 全新安装
#   bash /path/to/ai-project-template/setup.sh --migrate  # 已有项目迁移
#
# --migrate 模式：安装模板后，对已存在项目中的散落 .md 文件
# 执行扫描并按类型归档到 docs/archive/。
# ============================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$SCRIPT_DIR/scripts/lib/lib-platform.sh" 2>/dev/null || true
TEMPLATE_DIR="$SCRIPT_DIR"

echo "=== AI Project Collaboration Blueprint Installer ==="
echo "  Template: $TEMPLATE_DIR"
echo "  Target:   $(pwd)"
echo ""

# 复制脚本
echo "  [1/5] Installing scripts..."
mkdir -p scripts/governance scripts/session scripts/workflow scripts/migration scripts/bootstrap scripts/lib scripts/registry
for subdir in governance session workflow migration bootstrap lib; do
  for f in "$TEMPLATE_DIR/scripts/$subdir/"*; do
    [ -f "$f" ] || continue
    base=$(basename "$f")
    [ "$base" = "__pycache__" ] && continue
    [ "$base" = "__init__.py" ] && continue
    # 跳过 Python 缓存文件
    case "$base" in *.pyc|*.pyo) continue ;; esac
    if [ ! -f "scripts/$subdir/$base" ]; then
      cp "$f" "scripts/$subdir/$base"
      chmod +x "scripts/$subdir/$base" 2>/dev/null || true
      echo "    + scripts/$subdir/$base"
    fi
  done
done

# 复制文档骨架
echo "  [2/5] Installing docs skeleton..."
cp -r "$TEMPLATE_DIR/docs/." docs/

# 复制配置
echo "  [3/5] Installing config..."
if [ ! -f "AGENTS.md" ]; then
  cp "$TEMPLATE_DIR/AGENTS.md" .
  echo "    + AGENTS.md"
else
  echo "    ~ AGENTS.md (exists, skipped)"
fi

if [ ! -f "mkdocs.yml" ]; then
  cp "$TEMPLATE_DIR/mkdocs.yml" .
  echo "    + mkdocs.yml"
else
  echo "    ~ mkdocs.yml (exists, skipped)"
fi

if [ ! -f ".gitignore" ]; then
  cp "$TEMPLATE_DIR/.gitignore" .
  echo "    + .gitignore"
fi

mkdir -p .cache
touch .cache/.gitkeep

# 复制额外的配置文件
for extra in .editorconfig .gitattributes .clinerules .opencode/rules.md .cursorrules CONTRIBUTING.md LICENSE environment.yml .language; do
  if [ -f "$TEMPLATE_DIR/$extra" ] && [ ! -f "$(basename $extra)" ]; then
    mkdir -p "$(dirname "$extra")" 2>/dev/null || true
    cp "$TEMPLATE_DIR/$extra" "$extra" 2>/dev/null || true
    echo "    + $extra"
  fi
done

# 安装钩子
if [ -f "scripts/bootstrap/bs-install-hooks.sh" ]; then
  echo "  Installing Git hooks..."
  bash scripts/bootstrap/bs-install-hooks.sh 2>&1 | tail -1
fi

# 初始化上下文
echo "  Initializing AI context..."
bash scripts/session/ses-context-cache.sh init 2>&1 | tail -1

# 填充模板变量
echo "  [4/5] Filling template variables..."
echo "    Enter project info (press Enter to keep template placeholder):"
read -r -p "    Project Name [{{Project Name}}]: " PROJ_NAME
read -r -p "    Project Description: " PROJ_DESC
read -r -p "    Author: " PROJ_AUTHOR
read -r -p "    GitHub URL: " PROJ_GITHUB
read -r -p "    Source dirs (space separated, for pre-commit monitoring) [src app lib packages]: " PROJ_SRC
echo ""

# 替换所有模板文件中的变量
CURRENT_YEAR=$(date +%Y)
TODAY=$(date +%Y-%m-%d)

# 1. 全局变量替换
for f in AGENTS.md README.md CONTRIBUTING.md; do
  [ -f "$f" ] || continue
  [ -n "$PROJ_NAME" ] && sed_i "s|{{Project Name}}|$PROJ_NAME|g" "$f" 2>/dev/null
  [ -n "$PROJ_DESC" ] && sed_i "s|{{Project Description}}|$PROJ_DESC|g" "$f" 2>/dev/null
done

# 2. mkdocs.yml + docs/index.md（含站点配置）
for f in mkdocs.yml docs/index.md; do
  [ -f "$f" ] || continue
  [ -n "$PROJ_NAME" ] && sed_i "s|{{Project Name}}|$PROJ_NAME|g" "$f" 2>/dev/null
  [ -n "$PROJ_DESC" ] && sed_i "s|{{Project Description}}|$PROJ_DESC|g" "$f" 2>/dev/null
  [ -n "$PROJ_AUTHOR" ] && sed_i "s|{{Author}}|$PROJ_AUTHOR|g" "$f" 2>/dev/null
  [ -n "$PROJ_GITHUB" ] && sed_i "s|{{GitHub URL}}|$PROJ_GITHUB|g" "$f" 2>/dev/null
  [ -n "$PROJ_DESC" ] && sed_i "s|{{Current status}}|$PROJ_DESC|g" "$f" 2>/dev/null
done

# 3. 带 {{Title}} 的章节索引页 → 固定中文标题 + 项目名
for f in docs/guides/index.md; do
  [ -f "$f" ] || continue
  sed_i "s|{{Title}}|开发指南|g" "$f" 2>/dev/null
  [ -n "$PROJ_NAME" ] && sed_i "s|{{Project Name}}|$PROJ_NAME|g" "$f" 2>/dev/null
  [ -n "$PROJ_DESC" ] && sed_i "s|{{Description}}|$PROJ_DESC|g" "$f" 2>/dev/null
done

for f in docs/reference/index.md; do
  [ -f "$f" ] || continue
  sed_i "s|{{Title}}|技术参考|g" "$f" 2>/dev/null
  [ -n "$PROJ_NAME" ] && sed_i "s|{{Project Name}}|$PROJ_NAME|g" "$f" 2>/dev/null
  [ -n "$PROJ_DESC" ] && sed_i "s|{{Description}}|$PROJ_DESC|g" "$f" 2>/dev/null
done

for f in docs/architecture/index.md; do
  [ -f "$f" ] || continue
  sed_i "s|{{Title}}|架构概览|g" "$f" 2>/dev/null
  [ -n "$PROJ_NAME" ] && sed_i "s|{{Project Name}}|$PROJ_NAME|g" "$f" 2>/dev/null
  [ -n "$PROJ_DESC" ] && sed_i "s|{{Description}}|$PROJ_DESC|g" "$f" 2>/dev/null
done

# 4. mkdocs.yml 导航中的 {{Title}} 占位
[ -n "$PROJ_NAME" ] && sed_i "s|{{Title}}|$PROJ_NAME|g" mkdocs.yml 2>/dev/null

# 5. 自动填充固定值（日期、年份等）
for f in LICENSE docs/ai/lifecycle/01_PRD.md docs/ai/lifecycle/02_Architecture.md \
         docs/ai/lifecycle/03_TaskGraph.md docs/ai/lifecycle/04_DevNotes.md \
         docs/ai/lifecycle/05_Test.md docs/ai/lifecycle/06_Metrics.md; do
  [ -f "$f" ] || continue
  sed_i "s|{{Year}}|$CURRENT_YEAR|g" "$f" 2>/dev/null
  sed_i "s|{{date}}|$TODAY|g" "$f" 2>/dev/null
done

# 6. 更新 update-status.sh 项目名 + 重新生成 STATUS.md
if [ -n "$PROJ_NAME" ]; then
  sed_i "s|{{Project Name}}|$PROJ_NAME|g" scripts/governance/gov-update-status.sh 2>/dev/null
  bash scripts/governance/gov-update-status.sh 2>&1 | tail -1
fi

# 7. AGENTS.md 的 {{占位符}} 提示
[ -n "$PROJ_NAME" ] && sed_i "s|{{占位符}}|$PROJ_NAME|g" AGENTS.md 2>/dev/null

# 8. config.sh 源码目录配置
CONFIG_FILE="scripts/governance/gov-config.sh"
if [ -f "$CONFIG_FILE" ]; then
  [ -n "$PROJ_SRC" ] && sed_i "s|^SRC_DIRS=.*|SRC_DIRS=\"$PROJ_SRC\"|g" "$CONFIG_FILE" 2>/dev/null
  # 重构 SRC_PREFIXES
  if [ -n "$PROJ_SRC" ]; then
    SRC_PREFIXES=""
    for dir in $PROJ_SRC; do
      SRC_PREFIXES="$SRC_PREFIXES ^$dir/"
    done
    SRC_PREFIXES="${SRC_PREFIXES# }"
    sed_i "s|^SRC_PREFIXES=.*|SRC_PREFIXES=\"$SRC_PREFIXES\"|g" "$CONFIG_FILE" 2>/dev/null
  fi
  echo "    + config.sh (SRC_DIRS=${PROJ_SRC:-src app lib packages})"
fi

echo "    Variables filled."

# 设置 Conda/MkDocs 环境
if [ -f "environment.yml" ] && command -v conda &>/dev/null; then
  echo "  Setting up Conda mkdocs environment..."
  if conda env list 2>/dev/null | grep -q "^mkdocs "; then
    echo "    (conda env 'mkdocs' already exists)"
  else
    conda env create -f environment.yml 2>&1 | tail -1
    echo "    + conda env 'mkdocs' created"
  fi
fi

# 迁移模式：归档散落文件
if [ "${1:-}" = "--migrate" ]; then
  echo "  Running migration for existing files..."
  bash scripts/migration/mig-migrate.sh --archive 2>&1
fi

# 生成初始状态
echo "  Generating initial STATUS.md..."
bash scripts/governance/gov-update-status.sh 2>&1 | tail -1

# 验证
echo "  [5/5] Running initial validation..."
bash scripts/governance/gov-validate.sh 2>&1 | grep -E "违规|警告|结论"

echo ""
echo "=== Installation complete ==="
echo ""
echo "  AI handoff is ready:"
echo "    cat STATUS.md                      # project state"
echo "    bash scripts/session/ses-context-load.sh   # AI memory"
echo "    bash scripts/migration/mig-migrate.sh --scan   # 扫描剩余散落文件"
echo ""
