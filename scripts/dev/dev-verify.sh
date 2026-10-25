#!/usr/bin/env bash
# scripts/dev/dev-verify.sh
# 一条命令完成"改完就看"：编译 → 装到设备 → 启动 →（可选）跑一条用户路径。
#
# 用法:
#   scripts/dev/dev-verify.sh                 # 编译 + 安装 + 启动
#   scripts/dev/dev-verify.sh --no-build      # 只安装现有 APK
#   scripts/dev/dev-verify.sh --journey browse  # 额外跑 scripts/testing/journeys/browse.json
set -uo pipefail

cd "$(git rev-parse --show-toplevel)" || exit 2
BUILD=1
JOURNEY=""
while [ $# -gt 0 ]; do
  case "$1" in
    --no-build) BUILD=0; shift ;;
    --journey) JOURNEY="$2"; shift 2 ;;
    *) echo "未知参数: $1" >&2; exit 2 ;;
  esac
done

ADB="${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}"
APK="app/build/outputs/apk/debug/app-debug.apk"
PKG="com.xcwk.scalehub"

if [ "$BUILD" -eq 1 ]; then
  echo "[1/3] 编译"
  JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}" \
    ./gradlew :app:assembleDebug --max-workers=4 -q || exit 2
else
  echo "[1/3] 跳过编译"
fi

[ -f "$APK" ] || { echo "APK 不存在: $APK" >&2; exit 2; }
[ -x "$ADB" ] || { echo "找不到 adb: $ADB" >&2; exit 2; }

echo "[2/3] 安装"
"$ADB" install -r -t "$APK" | tail -1

echo "[3/3] 启动"
"$ADB" shell am start -n "$PKG/.MainActivity" >/dev/null

if [ -n "$JOURNEY" ]; then
  f="scripts/testing/journeys/$JOURNEY.json"
  [ -f "$f" ] || f="$JOURNEY"
  echo "[e2e] $f"
  python3 scripts/testing/e2e_journey.py run "$f" --no-install
fi

echo "完成。设备上现在就是这版代码。"
