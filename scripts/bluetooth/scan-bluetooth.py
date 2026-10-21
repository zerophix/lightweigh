#!/usr/bin/env python3
"""
Bluetooth Device Scanner
扫描周围的蓝牙设备 (BLE + 经典蓝牙)

使用方法:
  python3 scan-bluetooth.py                      # 扫描所有设备
  python3 scan-bluetooth.py --timeout 10         # 设置扫描时间 (默认 8 秒)
  python3 scan-bluetooth.py --ble-only           # 仅扫描 BLE 设备
  python3 scan-bluetooth.py --classic-only       # 仅扫描经典蓝牙设备
  python3 scan-bluetooth.py --json               # JSON 格式输出
  python3 scan-bluetooth.py --filter "Air"       # 按名称过滤
"""

import argparse
import asyncio
import json
import re
import subprocess
import sys
from dataclasses import dataclass, asdict
from datetime import datetime
from typing import List, Optional


@dataclass
class BluetoothDevice:
    address: str
    name: str
    rssi: Optional[int]
    device_type: str  # "ble", "classic"
    extra: dict = None

    def __post_init__(self):
        if self.extra is None:
            self.extra = {}


async def scan_ble_devices(timeout: float = 8.0) -> List[BluetoothDevice]:
    """使用 bleak 扫描 BLE 设备"""
    devices = []
    try:
        from bleak import BleakScanner

        print(f"  正在扫描 BLE 设备 (超时: {timeout}s)...", file=sys.stderr)

        # macOS CoreBluetooth return_adv=True: dict {address: (BLEDevice, AdvData)}
        found = await BleakScanner.discover(timeout=timeout, return_adv=True)

        for addr_or_device, value in found.items():
            # dict values are (BLEDevice, AdvertiseData) tuples
            if isinstance(value, tuple):
                d, adv = value[0], value[1]
            elif hasattr(value, "items"):
                d, adv = value.get("device"), value.get("adv")
            else:
                d, adv = value, None

            # macOS CoreBluetooth 返回 Swift 对象，需要强制转换
            def _s(val):
                """安全转换为字符串"""
                if val is None:
                    return None
                try:
                    return str(val)
                except Exception:
                    return None

            name = _s(getattr(d, "name", None)) or "(无名称)"
            address = _s(getattr(d, "address", None)) or "N/A"
            rssi = None
            if adv is not None:
                rssi = getattr(adv, "rssi", None)
                if rssi is not None:
                    try:
                        rssi = int(rssi)
                    except Exception:
                        rssi = None

            extra = {}
            if adv is not None:
                try:
                    service_uuids = []
                    su = getattr(adv, "service_uuids", None)
                    if su:
                        service_uuids = [str(u) for u in su]

                    mfr_data = {}
                    md = getattr(adv, "manufacturer_data", None)
                    if md:
                        for k, v in md.items():
                            try:
                                mfr_data[str(k)] = bytes(v).hex() if v else ""
                            except Exception:
                                mfr_data[str(k)] = str(v)

                    extra = {
                        "service_uuids": service_uuids,
                        "local_name": _s(getattr(adv, "local_name", None)),
                        "manufacturer_data": mfr_data,
                    }
                except Exception:
                    pass

            devices.append(
                BluetoothDevice(
                    address=address,
                    name=name,
                    rssi=rssi,
                    device_type="ble",
                    extra=extra,
                )
            )
    except ImportError:
        print("  [WARNING] 未安装 bleak 库: pip install bleak", file=sys.stderr)
    except RuntimeError as e:
        msg = str(e)
        if "permission" in msg.lower():
            print(
                "  [ERROR] 蓝牙权限不足，请在「系统设置 > 隐私与安全 > 蓝牙」中授予权限",
                file=sys.stderr,
            )
        elif "off" in msg.lower() or "disabled" in msg.lower():
            print("  [ERROR] 请先打开蓝牙", file=sys.stderr)
        else:
            print(f"  [WARNING] BLE 扫描错误: {e}", file=sys.stderr)
    except Exception as e:
        print(f"  [WARNING] BLE 扫描异常: {e}", file=sys.stderr)

    return devices


def scan_system_bluetooth() -> List[BluetoothDevice]:
    """使用 system_profiler 扫描已配对的蓝牙设备"""
    devices = []
    try:
        result = subprocess.run(
            ["system_profiler", "SPBluetoothDataType"],
            capture_output=True,
            text=True,
            timeout=20,
        )
        output = result.stdout

        # 解析 system_profiler 输出
        # 格式示例:
        #      Not Connected:
        #          DeviceName:
        #              Address: xx:xx:xx:xx:xx:xx
        #              RSSI: -50

        mac_pattern = re.compile(
            r"([0-9A-Fa-f]{2}:[0-9A-Fa-f]{2}:[0-9A-Fa-f]{2}:[0-9A-Fa-f]{2}:[0-9A-Fa-f]{2}:[0-9A-Fa-f]{2})"
        )

        lines = output.split("\n")
        current_name = None
        current_addr = None
        current_rssi = None
        in_device_section = False

        for line in lines:
            stripped = line.strip()

            # 检测设备名称行 (以名称+冒号结尾，且缩进 10 个空格)
            if re.match(r"^\s{10}\S+:\s*$", line) and not stripped.startswith(("Address", "RSSI", "Services", "Manufacturer", "Supported", "Connected", "Paired")):
                if current_name and current_addr:
                    devices.append(
                        BluetoothDevice(
                            address=current_addr,
                            name=current_name.rstrip(":"),
                            rssi=current_rssi,
                            device_type="classic",
                            extra={"paired": True, "source": "system_profiler"},
                        )
                    )
                current_name = stripped
                current_addr = None
                current_rssi = None
                in_device_section = True
                continue

            if in_device_section:
                if "Address:" in stripped:
                    mac_match = mac_pattern.search(stripped)
                    if mac_match:
                        current_addr = mac_match.group(1)
                elif "RSSI:" in stripped:
                    rssi_str = stripped.split("RSSI:")[-1].strip()
                    try:
                        current_rssi = int(rssi_str)
                    except ValueError:
                        pass

        # 保存最后一个设备
        if current_name and current_addr:
            devices.append(
                BluetoothDevice(
                    address=current_addr,
                    name=current_name.rstrip(":"),
                    rssi=current_rssi,
                    device_type="classic",
                    extra={"paired": True, "source": "system_profiler"},
                )
            )

    except Exception as e:
        print(f"  [WARNING] system_profiler 解析出错: {e}", file=sys.stderr)

    return devices


def format_rssi(rssi: Optional[int]) -> str:
    """格式化 RSSI 显示"""
    if rssi is None:
        return "  N/A"
    if rssi >= -50:
        return "🔥 {:>4} dBm (强)".format(rssi)
    elif rssi >= -70:
        return "📶 {:>4} dBm (中)".format(rssi)
    elif rssi >= -90:
        return "📶 {:>4} dBm (弱)".format(rssi)
    else:
        return "🔸 {:>4} dBm (极弱)".format(rssi)


def deduplicate(devices: List[BluetoothDevice]) -> List[BluetoothDevice]:
    """去重: 如果同一个地址同时出现在 BLE 和 Classic 中，保留 RSSI 高的"""
    seen = {}
    for dev in devices:
        key = dev.address.lower()
        if key in seen:
            existing = seen[key]
            if (dev.rssi or -100) > (existing.rssi or -100):
                seen[key] = dev
        else:
            seen[key] = dev
    return list(seen.values())


def print_results(devices: List[BluetoothDevice], show_json: bool = False):
    """打印扫描结果"""
    if not devices:
        print("\n📭 未找到任何蓝牙设备")
        print("   提示: 请确保蓝牙已打开，并且设备在广播模式下")
        return

    if show_json:
        output = {
            "timestamp": datetime.now().isoformat(),
            "count": len(devices),
            "devices": [asdict(d) for d in devices],
        }
        print(json.dumps(output, indent=2, ensure_ascii=False))
        return

    # 按设备类型分组
    ble_devices = [d for d in devices if d.device_type == "ble"]
    classic_devices = [d for d in devices if d.device_type == "classic"]

    ble_devices.sort(key=lambda d: d.rssi or -100, reverse=True)
    classic_devices.sort(key=lambda d: d.name or "")

    print(f"\n{'='*60}")
    print(f"  Bluetooth 设备扫描结果")
    print(f"  时间: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    print(f"  合计: {len(devices)} 个设备")
    print(f"  - BLE 设备: {len(ble_devices)} 个")
    print(f"  - 经典蓝牙: {len(classic_devices)} 个")
    print(f"{'='*60}\n")

    if classic_devices:
        print("🔊 经典蓝牙设备 (已配对):")
        print(f"  {'地址':<22} {'信号':<14} 名称")
        print(f"  {'-'*22} {'-'*14} {'-'*24}")
        for dev in classic_devices:
            rssi_str = format_rssi(dev.rssi) if dev.rssi else "  --"
            print(f"  {dev.address:<22} {rssi_str:>10}  {dev.name}")
        print()

    if ble_devices:
        print("📱 BLE 设备 (按信号强度排序):")
        print(f"  {'地址':<40} {'信号':<18} 名称")
        print(f"  {'-'*40} {'-'*18} {'-'*30}")
        for dev in ble_devices:
            rssi_str = format_rssi(dev.rssi)
            # 截断过长的地址显示
            addr_display = dev.address[:38] if len(dev.address) > 38 else dev.address
            print(f"  {addr_display:<40} {rssi_str:>10}  {dev.name}")
        print()

    print(f"{'='*60}")


async def main():
    parser = argparse.ArgumentParser(
        description="扫描周围的蓝牙设备 (BLE + 经典)",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
示例:
  %(prog)s                         # 扫描所有设备
  %(prog)s --timeout 10            # 设置扫描 10 秒
  %(prog)s --ble-only              # 仅扫描 BLE 设备
  %(prog)s --classic-only          # 仅扫描经典蓝牙
  %(prog)s --json                  # 输出 JSON 格式
  %(prog)s --filter "AirPods"    # 按名称过滤
        """,
    )
    parser.add_argument(
        "--timeout", type=float, default=8.0, help="BLE 扫描超时时间 (秒, 默认 8)"
    )
    parser.add_argument(
        "--ble-only", action="store_true", help="仅扫描 BLE 设备"
    )
    parser.add_argument(
        "--classic-only", action="store_true", help="仅扫描经典蓝牙设备"
    )
    parser.add_argument(
        "--json", action="store_true", help="输出 JSON 格式"
    )
    parser.add_argument(
        "--filter", type=str, default="", help="按名称过滤 (模糊匹配)"
    )
    args = parser.parse_args()

    print(f"🔍 正在扫描蓝牙设备...")

    all_devices: List[BluetoothDevice] = []

    # 扫描 BLE 设备
    if not args.classic_only:
        ble_devices = await scan_ble_devices(timeout=args.timeout)
        all_devices.extend(ble_devices)

    # 扫描经典蓝牙设备 (来自 system_profiler)
    if not args.ble_only:
        print("  正在扫描经典蓝牙设备...", file=sys.stderr)
        classic_devices = scan_system_bluetooth()
        all_devices.extend(classic_devices)

    # 过滤
    if args.filter:
        filter_lower = args.filter.lower()
        all_devices = [
            d for d in all_devices if filter_lower in (d.name or "").lower()
        ]

    # 去重
    all_devices = deduplicate(all_devices)

    # 输出结果
    if args.json:
        print_results(all_devices, show_json=True)
    else:
        print_results(all_devices)


if __name__ == "__main__":
    asyncio.run(main())
