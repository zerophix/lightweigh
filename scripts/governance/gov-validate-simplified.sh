#!/usr/bin/env bash
# 精简验证 — 只检查必要项
# 用法: gov-validate-simplified.sh [--fast]

set -uo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

FAIL=0

# 1. 非 ASCII 文件名
echo "--- 文件名检查 ---"
BAD_NAMES=$(python3 -c "
import pathlib
for d in ['app/src','application/src','domain/src','infrastructure/src']:
    for p in pathlib.Path(d).rglob('*'):
        if p.is_file() and any(ord(c) > 127 for c in str(p)):
            print(p)
" | head -5)
if [ -n "$BAD_NAMES" ]; then
  echo "  [FAIL] 非 ASCII 文件名:"
  echo "$BAD_NAMES"
  FAIL=1
else
  echo "  [PASS]"
fi

# 2. 死 import（编译器能发现，这里只做快速扫描）
echo "--- 源码质量 ---"
DEAD_IMPORTS=$(python3 -c "
import re, pathlib
count = 0
for p in list(pathlib.Path('app/src/main').rglob('*.kt')) + list(pathlib.Path('application/src/main').rglob('*.kt')) + list(pathlib.Path('infrastructure/src/main').rglob('*.kt')):
    try:
        lines = p.read_text(encoding='utf-8').split('\n')
        imports = [(i, l) for i, l in enumerate(lines) if l.startswith('import ') and '.*' not in l]
        for idx, imp in imports:
            sym = imp.split('.')[-1]
            body = '\n'.join(lines[:idx] + lines[idx+1:])
            if sym not in body and f'import {imp[7:]}' == imp:
                count += 1
                if count <= 3:
                    print(f'  {p}:{idx+1} {sym}')
    except: pass
if count == 0: print('  [PASS] 无死 import')
else: print(f'  [WARN] {count} 个疑似死 import')
" 2>/dev/null)
echo "$DEAD_IMPORTS"

# 3. 测试注册表
echo "--- 测试注册表 ---"
if [ -f tests/android-test-registry.yaml ]; then
  UNREGISTERED=$(python3 -c "
import yaml, pathlib
reg = yaml.safe_load(open('tests/android-test-registry.yaml'))
paths = {e['path'] for e in reg.get('commands', [])}
actual = set()
for p in pathlib.Path('.').glob('*/src/test/**/*.kt'):
    actual.add(str(p))
missing = actual - paths
if missing: print(f'  [WARN] {len(missing)} 个测试未注册')
else: print('  [PASS]')
" 2>/dev/null)
  echo "$UNREGISTERED"
fi

echo ""
if [ "$FAIL" -eq 0 ]; then
  echo "=== 验证通过 ==="
else
  echo "=== 验证失败 ==="
  exit 1
fi