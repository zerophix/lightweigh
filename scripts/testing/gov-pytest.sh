#!/usr/bin/env bash
# ============================================================
# scripts/testing/gov-pytest.sh
# pytest 的增量 + 并行执行器（DEC-072）。不依赖 pytest-xdist。
#
# 用法:
#   gov-pytest.sh --all                 # 全量，按 GOV_PYTEST_JOBS 并行
#   gov-pytest.sh tests/unit/a.py ...   # 只跑给定文件
#   gov-pytest.sh --jobs 1 --all        # 串行（排查顺序问题时用）
#
# 环境变量:
#   GOV_PYTEST_JOBS   并行进程数，默认 4；1 = 串行
#   GOV_PYTEST_CACHE  0 = 不用结果缓存，默认 1
#
# 设计:
#   - 分片按"文件个数轮转"，不猜耗时；慢文件少时最多慢一档。
#   - 每个分片独立写日志，聚合时按分片顺序输出，避免输出交错。
#   - 缓存键 = 测试文件内容 + 相关源码/脚本内容 + 调用方传入的 --key 附加串。
#     键不变即复用结果；任何输入变化都会换键，不会误用旧结果。
#   - 缓存命中时仍打印 [cache hit]，不伪装成新执行。
# ============================================================
set -uo pipefail

DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$DIR/../.." && pwd)"
cd "$ROOT" || exit 2

DEFAULT_JOBS="$(getconf _NPROCESSORS_ONLN 2>/dev/null || echo 4)"
[ "$DEFAULT_JOBS" -gt 8 ] && DEFAULT_JOBS=8
[ "$DEFAULT_JOBS" -lt 1 ] && DEFAULT_JOBS=1
JOBS="${GOV_PYTEST_JOBS:-$DEFAULT_JOBS}"
USE_CACHE="${GOV_PYTEST_CACHE:-1}"
EXTRA_KEY=""
FILES=()
ALL=0

while [ $# -gt 0 ]; do
  case "$1" in
    --all) ALL=1; shift ;;
    --jobs) JOBS="${2:-4}"; shift 2 ;;
    --key) EXTRA_KEY="${2:-}"; shift 2 ;;
    --no-cache) USE_CACHE=0; shift ;;
    -h|--help) sed -n '2,24p' "$0"; exit 0 ;;
    *)
      if [ -f "$1" ]; then FILES+=("$1"); shift
      else echo "未知参数或文件不存在: $1" >&2; exit 2
      fi
      ;;
  esac
done

if [ "$ALL" = "1" ]; then
  while IFS= read -r f; do FILES+=("$f"); done < <(find tests -name 'test_*.py' -type f | LC_ALL=C sort)
fi

if [ "${#FILES[@]}" -eq 0 ]; then
  echo "[pytest] 没有要执行的测试文件"
  exit 0
fi

# ---------------- 结果缓存 ----------------
cache_key() {
  local f p
  {
    for f in "${FILES[@]}"; do shasum "$f" 2>/dev/null; done
    # 测试通过子进程调脚本：相关目录内容必须进入键，否则改脚本不会失效
    for p in scripts app/src/main domain/src/main application/src/main infrastructure/src/main docs .ai/tasks.json; do
      if [ -d "$p" ]; then
        find "$p" -type f \( -name '*.kt' -o -name '*.py' -o -name '*.sh' \
          -o -name '*.md' -o -name '*.json' -o -name '*.yaml' -o -name '*.yml' \) \
          -exec shasum {} + 2>/dev/null
      elif [ -f "$p" ]; then
        shasum "$p" 2>/dev/null
      fi
    done
    printf '%s\n' "$EXTRA_KEY" "$JOBS"
  } | shasum | cut -d' ' -f1
}

CACHE_DIR="$ROOT/.cache/pytest"
mkdir -p "$CACHE_DIR"
KEY="$(cache_key)"
STAMP="$CACHE_DIR/run.$KEY"

if [ "$USE_CACHE" = "1" ] && [ -f "$STAMP.out" ]; then
  echo "[cache hit] pytest ($KEY)"
  cat "$STAMP.out"
  exit "$(cat "$STAMP.rc" 2>/dev/null || echo 0)"
fi

# ---------------- 并行分片 ----------------
TMP="$CACHE_DIR/run.$$"
rm -rf "$TMP"
mkdir -p "$TMP"

TOTAL="${#FILES[@]}"
if [ "$JOBS" -gt "$TOTAL" ]; then JOBS="$TOTAL"; fi
[ "$JOBS" -lt 1 ] && JOBS=1

echo "[pytest] $TOTAL 个文件，$JOBS 个并行分片"

i=0
for f in "${FILES[@]}"; do
  printf '%s\n' "$f" >> "$TMP/group.$((i % JOBS))"
  i=$((i + 1))
done

for g in $(seq 0 $((JOBS - 1))); do
  [ -f "$TMP/group.$g" ] || continue
  (
    # shellcheck disable=SC2046
    python3 -m pytest -q $(cat "$TMP/group.$g") > "$TMP/out.$g" 2>&1
    echo $? > "$TMP/rc.$g"
  ) &
done
wait

# ---------------- 聚合 ----------------
RC=0
OUT="$TMP/all.out"
: > "$OUT"
for g in $(seq 0 $((JOBS - 1))); do
  [ -f "$TMP/out.$g" ] || continue
  cat "$TMP/out.$g" >> "$OUT"
  r="$(cat "$TMP/rc.$g" 2>/dev/null || echo 1)"
  [ "$r" != "0" ] && RC=1
done

cat "$OUT"
echo "[pytest] 分片结果: $(grep -hE '^(FAILED|ERROR)' "$OUT" 2>/dev/null | wc -l | tr -d ' ') 条失败项"

if [ "$USE_CACHE" = "1" ]; then
  cp "$OUT" "$STAMP.out" 2>/dev/null || true
  printf '%s\n' "$RC" > "$STAMP.rc"
  ls -t "$CACHE_DIR"/run.*.out 2>/dev/null | tail -n +6 | while IFS= read -r old; do
    rm -f "$old" "${old%.out}.rc"
  done
fi

rm -rf "$TMP"
exit "$RC"
