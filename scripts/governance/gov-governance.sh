#!/usr/bin/env bash
# scripts/governance/gov-governance.sh — 文件治理桥接
# 桥接 tools/analyze/ana-fs-governance.py 和 ana-fs-filetypes.py 到治理/状态管线。
set -euo pipefail
_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$_DIR/../lib/lib-root.sh"
ROOT="$PROJECT_ROOT"

GOV_PY="$ROOT/tools/analyze/ana-fs-governance.py"
FILE_PY="$ROOT/tools/analyze/ana-fs-filetypes.py"

case "${1:-}" in
    check)
        python3 "$GOV_PY" "$ROOT" --json 2>/dev/null || echo '{"health_score":0,"issues":99,"violations":[]}'
        ;;
    score)
        python3 "$GOV_PY" "$ROOT" --json 2>/dev/null | python3 -c "
import sys,json
d=json.load(sys.stdin)
print(d.get('health_score',0))
" 2>/dev/null || echo "0"
        ;;
    filetypes)
        python3 "$FILE_PY" "$ROOT" --json 2>/dev/null || echo '{"total":0,"categories":{}}'
        ;;
    violations)
        python3 "$GOV_PY" "$ROOT" --json 2>/dev/null | python3 -c "
import sys,json
d=json.load(sys.stdin)
for v in d.get('violations', []):
    print(f\"  [{v['rule']}] {v['msg']}\")
" 2>/dev/null || true
        ;;
    watch)
        python3 "$GOV_PY" "$ROOT" --watch 2>/dev/null || true
        ;;
    *)
        echo "Usage: $0 {check|score|filetypes|violations|watch}"
        exit 1
        ;;
esac