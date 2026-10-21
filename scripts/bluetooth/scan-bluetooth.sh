#!/usr/bin/env bash
#
# Bluetooth Device Scanner
# 扫描周围的蓝牙设备 (BLE + 经典蓝牙)
#
# 用法:
#   ./scan-bluetooth.sh                     # 扫描所有设备
#   ./scan-bluetooth.sh --timeout 10        # 设置扫描时间
#   ./scan-bluetooth.sh --ble-only           # 仅扫描 BLE
#   ./scan-bluetooth.sh --classic-only       # 仅扫描经典蓝牙
#   ./scan-bluetooth.sh --json               # JSON 输出
#   ./scan-bluetooth.sh --filter "AirPods"  # 按名称过滤
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PYTHON_SCRIPT="${SCRIPT_DIR}/scan-bluetooth.py"

# 检查 Python 脚本是否存在
if [ ! -f "$PYTHON_SCRIPT" ]; then
    echo "ERROR: Python 脚本不存在: $PYTHON_SCRIPT" >&2
    exit 1
fi

# 检查蓝牙是否开启
check_bluetooth_status() {
    local bt_status
    bt_status=$(/usr/sbin/system_profiler SPBluetoothDataType 2>/dev/null \
        | grep -E "State|Bluetooth" | head -5 || true)
    if echo "$bt_status" | grep -qi "off\|disabled\|not available" 2>/dev/null; then
        echo "⚠️  蓝牙可能未打开，请先开启蓝牙 (系统设置 → 蓝牙)"
    fi
}

# 检查必需的 Python 库
check_python_deps() {
    if ! python3 -c "import bleak" 2>/dev/null; then
        echo "⚠️  建议安装 bleak 库以获得更好的 BLE 扫描效果:"
        echo "   pip3 install bleak"
        echo ""
    fi
}

# 检查蓝牙状态
check_bluetooth_status
check_python_deps

# 执行扫描
python3 "$PYTHON_SCRIPT" "$@"
