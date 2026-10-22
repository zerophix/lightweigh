#!/usr/bin/env bash
# scripts/governance/gov-local-ci.sh
# 合并前本地门禁（项目自有）：Gradle build + gov-validate + gov-custom-checks + secrets-scan (DEC-049)
#
# 用法:
#   bash scripts/governance/gov-local-ci.sh
#
# 任一步失败即非 0 退出。等价于「本地版 build-and-guard + docs-validate + code-check」。
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

PASS=0
FAIL=0

run_step() {
  local name="$1"
  shift
  echo ""
  echo "=== [$name] ==="
  if "$@"; then
    echo "  PASS: $name"
    PASS=$((PASS + 1))
  else
    echo "  FAIL: $name"
    FAIL=$((FAIL + 1))
    return 1
  fi
}

echo "gov-local-ci — 仓库: $ROOT"
echo "开始: $(date '+%Y-%m-%d %H:%M:%S %z')"

set +e
run_step "gradlew build" ./gradlew build --stacktrace
S1=$?
run_step "gov-validate" bash scripts/governance/gov-validate.sh
S2=$?
run_step "gov-custom-checks" bash scripts/governance/gov-custom-checks.sh
S3=$?
run_step "gov-secrets-scan" bash scripts/governance/gov-secrets-scan.sh
S4=$?
set -e

echo ""
echo "=== 汇总 ==="
echo "  通过: $PASS  失败: $FAIL"
if [ "$FAIL" -ne 0 ] || [ "$S1$S2$S3$S4" != "0000" ]; then
  echo "FAIL gov-local-ci"
  exit 1
fi
echo "PASS gov-local-ci"
exit 0
