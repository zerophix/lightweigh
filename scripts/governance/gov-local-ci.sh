#!/usr/bin/env bash
# scripts/governance/gov-local-ci.sh
# 合并前本地门禁（项目自有）：Gradle build + gov-validate + gov-custom-checks + secrets-scan + schema-guard
# DEC-049 secrets；DEC-052 schema-guard；DEC-051 写入 .cache/local-ci-last.json 供 STATUS 取证
#
# 用法:
#   bash scripts/governance/gov-local-ci.sh
#
# 任一步失败即非 0 退出。等价于「本地版 build-and-guard + docs-validate + code-check」。
set -euo pipefail

ROOT="$(git rev-parse --show-toplevel 2>/dev/null || true)"
if [ -z "${ROOT:-}" ] || [ ! -f "$ROOT/scripts/governance/gov-validate.sh" ]; then
  ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
fi
cd "$ROOT"

CACHE_DIR="$ROOT/.cache"
RESULT_FILE="$CACHE_DIR/local-ci-last.json"
mkdir -p "$CACHE_DIR"

PASS=0
FAIL=0
STARTED_AT="$(date '+%Y-%m-%dT%H:%M:%S%z')"
# ISO-ish with colon in offset for JSON consumers
STARTED_AT_ISO="$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
BRANCH="$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo unknown)"
GIT_SHA="$(git rev-parse HEAD 2>/dev/null || echo unknown)"
# step results as name=code pairs
STEPS=()

run_step() {
  local name="$1"
  shift
  echo ""
  echo "=== [$name] ==="
  if "$@"; then
    echo "  PASS: $name"
    PASS=$((PASS + 1))
    STEPS+=("${name}=0")
    return 0
  else
    local rc=$?
    echo "  FAIL: $name (exit $rc)"
    FAIL=$((FAIL + 1))
    STEPS+=("${name}=$rc")
    return 1
  fi
}

write_result() {
  local overall="$1"  # pass | fail
  local finished_iso
  finished_iso="$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
  # Export env for python
  OVERALL="$overall" \
  STARTED_AT_ISO="$STARTED_AT_ISO" \
  FINISHED_ISO="$finished_iso" \
  BRANCH="$BRANCH" \
  GIT_SHA="$GIT_SHA" \
  PASS_N="$PASS" \
  FAIL_N="$FAIL" \
  RESULT_FILE="$RESULT_FILE" \
  STEPS_CSV="$(IFS=,; echo "${STEPS[*]}")" \
  python3 - <<'PY'
import json, os
from pathlib import Path

steps = []
for part in filter(None, os.environ.get("STEPS_CSV", "").split(",")):
    if "=" not in part:
        continue
    name, code = part.rsplit("=", 1)
    try:
        code_i = int(code)
    except ValueError:
        code_i = 1
    steps.append({"name": name, "exit_code": code_i, "ok": code_i == 0})

payload = {
    "schema_version": 1,
    "source": "gov-local-ci",
    "result": os.environ["OVERALL"],
    "started_at": os.environ["STARTED_AT_ISO"],
    "finished_at": os.environ["FINISHED_ISO"],
    "branch": os.environ["BRANCH"],
    "git_sha": os.environ["GIT_SHA"],
    "pass_count": int(os.environ["PASS_N"]),
    "fail_count": int(os.environ["FAIL_N"]),
    "steps": steps,
}
path = Path(os.environ["RESULT_FILE"])
path.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(f"  wrote evidence: {path}")
PY
}

echo "gov-local-ci — 仓库: $ROOT"
echo "开始: $(date '+%Y-%m-%d %H:%M:%S %z')  sha=${GIT_SHA:0:7}  branch=$BRANCH"

set +e
run_step "gradlew build" ./gradlew build --stacktrace
S1=$?
run_step "gov-validate" bash scripts/governance/gov-validate.sh
S2=$?
run_step "gov-custom-checks" bash scripts/governance/gov-custom-checks.sh
S3=$?
run_step "gov-secrets-scan" bash scripts/governance/gov-secrets-scan.sh
S4=$?
run_step "gov-schema-guard" bash scripts/governance/gov-schema-guard.sh
S5=$?
set -e

echo ""
echo "=== 汇总 ==="
echo "  通过: $PASS  失败: $FAIL"
if [ "$FAIL" -ne 0 ] || [ "$S1$S2$S3$S4$S5" != "00000" ]; then
  write_result fail
  bash scripts/governance/gov-update-status.sh 2>/dev/null || true
  echo "FAIL gov-local-ci"
  exit 1
fi
write_result pass
bash scripts/governance/gov-update-status.sh 2>/dev/null || true
echo "PASS gov-local-ci"
exit 0
