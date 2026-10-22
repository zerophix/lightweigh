#!/usr/bin/env python3
"""
Chipsea_BLE 体脂秤广告生命周期监控

持续扫描 Chipsea_BLE 设备，记录它什么时候开始广播和什么时候停止，
以理解 10 秒自动关机的行为，并寻找唤醒方法。
"""

import asyncio
import time
import sys
from datetime import datetime

async def monitor_scale(duration: float = 120.0, target_name: str = "Chipsea_BLE"):
    from bleak import BleakScanner

    print(f"🔍 持续监控 {target_name} 广告生命周期")
    print(f"   监控时长: {duration} 秒")
    print(f"   开始时间: {datetime.now().strftime('%H:%M:%S')}")
    print(f"   {'─'*50}")
    print()

    scan_start = time.time()
    last_seen = None
    first_seen = None
    last_not_seen = None
    total_uptime = 0.0  # 累计广告时间
    total_downtime = 0.0  # 累计未广告时间

    try:
        while time.time() - scan_start < duration:
            found = await BleakScanner.discover(timeout=3.0, return_adv=True)
            
            is_present = False
            rssi = None
            mfr_data = None
            service_uuids = None

            if isinstance(found, dict):
                for addr, value in found.items():
                    d, adv = value
                    name = str(getattr(d, 'name', ''))
                    if target_name.lower() in name.lower():
                        is_present = True
                        rssi = getattr(adv, 'rssi', None)
                        mfr_data = getattr(adv, 'manufacturer_data', None)
                        service_uuids = getattr(adv, 'service_uuids', None)
                        break

            current_time = time.time()
            elapsed = current_time - scan_start

            if is_present:
                if first_seen is None:
                    first_seen = current_time
                    print(f"  [{elapsed:6.1f}s] ✅ 设备上线! RSSI={rssi} dBm")
                    # 打印广告详情
                    if mfr_data:
                        for k, v in mfr_data.items():
                            raw = bytes(v).hex() if v else ""
                            print(f"           厂商 ID: {k} (0x{k:04X}), 数据: {raw}")
                            # 检查标记
                            raw_bytes = bytes(v) if v else b""
                            for i in range(len(raw_bytes) - 1):
                                if raw_bytes[i] == 0xFF and raw_bytes[i+1] == 0xF0:
                                    version = raw_bytes[i+2] if i+2 < len(raw_bytes) else -1
                                    print(f"           -> FF F0 标记 在索引 {i}, 版本: 0x{version:02X} ({version})")
                                    break
                    if service_uuids:
                        print(f"           服务 UUID: {[str(u) for u in service_uuids]}")
                else:
                    # 持续上线
                    if last_seen is None:
                        last_seen = current_time
                    if rssi is not None:
                        print(f"  [{elapsed:6.1f}s] 📶 信号: {rssi} dBm")

                last_seen = current_time
            else:
                if first_seen is not None:
                    # 设备离线
                    offline_time = current_time - (last_seen or first_seen)
                    uptime = (last_seen or current_time) - first_seen
                    if last_seen is not None or first_seen != current_time:
                        print(f"  [{elapsed:6.1f}s] ❌ 设备下线 (连线时长: {uptime:.1f}s)")
                        first_seen = None
                        last_seen = None
                        last_not_seen = current_time

    except KeyboardInterrupt:
        print(f"\n  {'─'*50}")
        print("  ⌨️  用户中断")
    finally:
        elapsed = time.time() - scan_start
        print(f"\n  {'─'*50}")
        print(f"  监控结束 (共 {elapsed:.1f} 秒)")
        print(f"  最后时间: {datetime.now().strftime('%H:%M:%S')}")

async def quick_connect_and_explore(addr: str):
    """快速连接探索设备服务特性"""
    from bleak import BleakClient

    print(f"\n🔗 快速连接: {addr}")
    client = BleakClient(addr, timeout=5.0)
    try:
        await asyncio.wait_for(client.connect(), timeout=5.0)
        print(f"  ✅ 连接成功!")

        # 获取服务
        services = await client.get_services()
        print(f"\n  服务特性 ({len(services)} 个服务):")
        for service in services:
            print(f"    服务 [{service.uuid}]")
            if service.description:
                print(f"      名称: {service.description}")
            for char in service.characteristics:
                props = []
                p = str(char.properties)
                if 'read' in p.lower(): props.append('R')
                if 'write' in p.lower(): props.append('W')
                if 'write-no-response' in p.lower(): props.append('WN')
                if 'notify' in p.lower(): props.append('N')
                if 'indicate' in p.lower(): props.append('I')
                desc = f" ({char.description})" if char.description else ""
                print(f"      特征 {char.uuid}[{'|'.join(props)}]{desc}")

        # 读取电量
        battery_uuid = "00002a19-0000-1000-8000-00805f9b34fb"
        try:
            val = await client.read_gatt_char(battery_uuid)
            print(f"\n  🔋 电量: {val[0] & 0xFF if val else 'N/A'}%")
        except Exception as e:
            print(f"\n  🔋 电量读取失败: {e}")

        # 尝试读取设备信息服务
        try:
            info = await client.read_gatt_char("00002a29-0000-1000-8000-00805f9b34fb")  # Manufacturer Name
            if info:
                print(f"  制造商: {bytes(info).decode('utf-8', errors='replace').strip()}")
        except:
            pass

    except Exception as e:
        print(f"  ❌ 连接失败: {e}")
    finally:
        await client.disconnect()
        print(f"  🔌 已断开")


def hex_str(data) -> str:
    """将数据转换为十六进制字符串"""
    if data is None:
        return "N/A"
    try:
        return bytes(data).hex() if isinstance(data, (bytes, bytearray)) else str(data)
    except:
        return str(data)


async def main():
    import argparse
    parser = argparse.ArgumentParser(description="监控 Chipsea_BLE 体脂秤广告生命周期")
    parser.add_argument("--duration", type=float, default=60.0, help="监控时长 (秒)")
    parser.add_argument("--connect", type=str, default="", help="直接连接指定地址")
    args = parser.parse_args()

    if args.connect:
        await quick_connect_and_explore(args.connect)
    else:
        await monitor_scale(duration=args.duration)


if __name__ == "__main__":
    asyncio.run(main())
