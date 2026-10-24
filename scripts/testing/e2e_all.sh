#!/usr/bin/env bash
# 跑全部用户路径走查（scripts/testing/journeys/*.json）
#
# 需要一台连着 adb 的设备或模拟器；这是唯一能证明"用户真的能点通"的门禁，
# 不进 local-ci（CI 无设备），由人工/预发触发。
#
# 用法:
#   bash scripts/testing/e2e_all.sh              # 构建 + 安装 + 全跑
#   bash scripts/testing/e2e_all.sh --no-build   # 用现有 APK
set -uo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

if [ "${1:-}" != "--no-build" ]; then
  echo "== 构建 debug APK =="
  JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}" \
    ./gradlew :app:assembleDebug --max-workers=4 > /tmp/e2e-build.log 2>&1 \
    || { echo "构建失败，见 /tmp/e2e-build.log"; exit 1; }
  grep -E "^BUILD" /tmp/e2e-build.log | tail -1
fi

fail=0
for j in scripts/testing/journeys/*.json; do
  [ -f "$j" ] || continue
  echo
  echo "== $(basename "$j") =="
  python3 scripts/testing/e2e_journey.py run "$j" || fail=$((fail+1))
done

echo
if [ "$fail" -eq 0 ]; then
  echo "全部路径通过"
else
  echo "${fail} 条路径失败（证据在 .cache/e2e/）"
fi
exit "$fail"
