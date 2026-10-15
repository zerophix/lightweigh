# scripts/lib-root.sh — 项目根目录定位（中心化，避免各脚本重复 $0/../..）
#
# 用法: source scripts/lib-root.sh
# 结果: $PROJECT_ROOT 指向项目根目录
#
# 优先级:
#   1. $PROJECT_ROOT 环境变量（显式设置）
#   2. git rev-parse --show-toplevel（git 仓库内）
#   3. 从脚本位置向上找 setup.py（沿目录树遍历）
#   4. 硬编码 fallback（scripts/ 父目录）

if [ -z "${PROJECT_ROOT:-}" ]; then
    _found=""
    # 方法 2: git
    if _root_candidate=$(git rev-parse --show-toplevel 2>/dev/null); then
        _found="$_root_candidate"
    fi
    # 方法 3: 遍历找 setup.py
    if [ -z "$_found" ]; then
        _dir="$(cd "$(dirname "${BASH_SOURCE[0]:-$0}")" && pwd)"
        for _i in 1 2 3 4 5 6 7 8 9 10; do
            if [ -f "$_dir/setup.py" ] || [ -f "$_dir/pyproject.toml" ]; then
                _found="$_dir"
                break
            fi
            _parent="$(dirname "$_dir")"
            [ "$_parent" = "$_dir" ] && break
            _dir="$_parent"
        done
    fi
    # 方法 4: fallback（scripts/ 父目录）
    if [ -z "$_found" ]; then
        _found="$(cd "$(dirname "${BASH_SOURCE[0]:-$0}")/../.." && pwd)"
    fi
    PROJECT_ROOT="$_found"
    unset _found _dir _parent _root_candidate _i
fi
