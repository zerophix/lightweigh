#!/usr/bin/env bash
# 从模板生成新项目（对应 Java 版 mvn archetype:generate 的效果）
# 用法: bash tools/new-project.sh <包名> <应用名> <目标目录>
# 例:   bash tools/new-project.sh com.acme.health "健康助手" ../health-app
set -euo pipefail
# macOS/BSD 与 GNU 工具链差异处理（生成脚本需可在 Linux CI 上运行）
case "$(uname -s)" in
  Darwin) export LC_ALL=en_US.UTF-8 ;;
  *)      export LC_ALL=C.UTF-8 ;;
esac
# sed -i 形态：BSD(macOS) 要空串参数，GNU 跟版本号直接 -i
if sed --version >/dev/null 2>&1; then SED_INPLACE=(-i); else SED_INPLACE=(-i ''); fi

OLD_PKG="com.yourco.app"
NEW_PKG="${1:?用法: new-project.sh <包名> <应用名> <目标目录>}"
APP_NAME="${2:?缺少应用名}"
TARGET="${3:?缺少目标目录}"

if [[ -e "$TARGET" ]]; then echo "错误: 目标已存在 $TARGET" >&2; exit 1; fi
if [[ ! "$NEW_PKG" =~ ^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+$ ]]; then
  echo "错误: 包名不合法 $NEW_PKG" >&2; exit 1
fi

SRC="$(cd "$(dirname "$0")/.." && pwd)"
mkdir -p "$TARGET"

# 排除构建产物、缓存与版本库（.gradle-user 是 gradlew 内置的项目级缓存，含二进制）
rsync -a \
  --exclude '.git/' \
  --exclude '.gradle/' \
  --exclude '.gradle-user/' \
  --exclude '.cache/' \
  --exclude 'build/' \
  --exclude '.idea/' \
  "$SRC/" "$TARGET/"

# 包目录重命名：把旧包叶子目录整体 mv 成新包叶子目录（每个模块的 src 根各自计算）
#   .../kotlin/com/yourco/app  →  .../kotlin/com/acme/demo
NEW_LEAF="$(echo "$NEW_PKG" | tr '.' '/')"
OLD_PATH_SEG="$(echo "$OLD_PKG" | tr '.' '/')"
for SRC_ROOT in "$TARGET"/{domain,application,infrastructure,app}/src/{main,test}/kotlin; do
  OLD_DIR="$SRC_ROOT/$OLD_PATH_SEG"
  if [[ -d "$OLD_DIR" ]]; then
    mkdir -p "$SRC_ROOT/$(dirname "$NEW_LEAF")"
    mv "$OLD_DIR" "$SRC_ROOT/$NEW_LEAF"
  fi
done

# 文件内容替换（只扫文本文件，跳过一切缓存/产物目录）
grep -rl --include='*.kt' --include='*.kts' --include='*.xml' --include='*.toml' \
     --include='*.md' --include='*.properties' --include='*.tsv' --include='*.json' \
     --exclude-dir=.git --exclude-dir=.gradle --exclude-dir=.gradle-user \
     --exclude-dir=build --exclude-dir=.idea --exclude-dir=.cache \
     -e "$OLD_PKG" -e "Your App" -e "your-android-scaffold" "$TARGET" 2>/dev/null |
while read -r f; do
  sed "${SED_INPLACE[@]}" \
    -e "s/$OLD_PKG/$NEW_PKG/g" \
    -e "s/Your App/$APP_NAME/g" \
    -e "s/your-android-scaffold/$(basename "$TARGET")/g" "$f"
done

echo "✓ 已生成 $TARGET"
echo "  包名: $NEW_PKG"
echo "  应用: $APP_NAME"
echo "下一步: cd $TARGET && ./gradlew build"
