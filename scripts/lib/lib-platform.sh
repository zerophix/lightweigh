#!/usr/bin/env bash
# ============================================================
# scripts/lib/lib-platform.sh
# 跨平台工具函数 — BSD (macOS) / GNU (Linux) 兼容层
#
# 用法:
#   source scripts/lib/lib-platform.sh
#   sed_i "s/old/new/g" file
#   file_mod_time /path/to/file
# ============================================================

# 兼容 sed -i（BSD: sed -i '' / GNU: sed -i）
# 用法: sed_i "s/old/new/g" file
sed_i() {
    local expr="$1"; shift
    local file="$1"
    if sed --version 2>/dev/null | grep -q GNU; then
        sed -i "$expr" "$file"
    else
        sed -i '' "$expr" "$file"
    fi
}

# 兼容 stat（BSD: stat -f "%m" / GNU: stat -c "%Y"）
file_mod_time() {
    local path="$1"
    if stat --version 2>/dev/null | grep -q GNU; then
        stat -c "%Y" "$path" 2>/dev/null || echo "0"
    else
        stat -f "%m" "$path" 2>/dev/null || echo "0"
    fi
}

# 兼容 mktemp
safe_tempfile() {
    mktemp 2>/dev/null || mktemp -t "tmp" 2>/dev/null || echo "/tmp/tmp-$$"
}

# 兼容 realpath
portable_realpath() {
    local path="$1"
    if command -v realpath &>/dev/null; then
        realpath "$path"
    else
        python3 -c "import os; print(os.path.realpath('$path'))"
    fi
}

# 检测当前 AI 平台身份（DEC-009: 按环境检测, 不冒充）
# 注意: 交接信的署名(wf-inbox.sh)与上下文缓存的身份(ses-session-end.sh)必须
# 共用本实现 — 两处各写一份时, 其中一处漏改会让署名静默失真。
# 返回: opencode | claude | unknown-ai
detect_agent() {
    if [ -n "${OPENCODE:-}" ]; then
        echo "opencode"
    elif [ -n "${CLAUDECODE:-}" ] || [ -n "${CLAUDE_CODE:-}" ]; then
        echo "claude"
    else
        echo "unknown-ai"
    fi
}
