#!/usr/bin/env python3
"""
Chipsea_BLE 体脂秤 BLE 连接工具 (v2 - 修复版)

修复: 区分 0xFFF1 (过程/非锁定) 和 0x2A9C (结果/锁定) 数据
- 0xFFF1: 实时过程数据, 不稳定, property 字节可能有 bit 7 (测量中)
- 0x2A9C: 最终锁定结果, 稳定

协议参考:
  domain/src/main/java/com/xcwk/scalehub/domain/protocol/chipsea/ChipseaCloudProtocol.java

Chipsea 协议特性:
  0xFFF1 - 非锁定过程 (Notify)   ← 实时数据 (不稳定)
  0xFFF2 - 单位切换 (Write)     ← [0x01, 0x00] = 公斤
  0x2A9C - 锁定结果 (Indicate)  ← 最终稳定结果
  0x2A08 - 时间同步 (Write)     ← 7 字节日期格式
  (注意: 无 0x2A19 电量特征)
"""

import argparse
import asyncio
import sys
import time
from dataclasses import dataclass
from datetime import datetime
from typing import Optional


def uuid16(short_id: int) -> str:
    return f"0000{short_id:04x}-0000-1000-8000-00805f9b34fb"


# Chipsea 协议 UUID
CHAR_PROCESS = uuid16(0xFFF1)     # 非锁定过程 (Notify) - 不稳定数据
CHAR_UNIT = uuid16(0xFFF2)        # 单位切换 (Write)
CHAR_RESULT = uuid16(0x2A9C)      # 锁定结果 (Indicate) - 最终结果
CHAR_TIME = uuid16(0x2A08)        # 时间同步 (Write)
AUTO_SHUTOFF_SECONDS = 10


@dataclass
class ScaleMeasurement:
    weight_kg: float
    unit: str
    weight_raw: int
    impedance_raw: int
    impedance_ohm: Optional[float]
    impedance2_raw: int
    impedance2_ohm: Optional[float]
    locked: bool  # 是否来自 0x2A9C (锁定)
    detail: str
    raw_hex: str


def make_time_packet() -> bytes:
    """Chipsea 时间包 (7 字节): 年(2B LE) + 月 + 日 + 时 + 分 + 秒"""
    now = datetime.now()
    year = now.year
    return bytes([
        year & 0xFF, (year >> 8) & 0xFF,
        now.month, now.day, now.hour, now.minute, now.second,
    ])


def make_unit_packet() -> bytes:
    """Chipsea 单位包: [0x01, 0x00] = 公斤"""
    return bytes([0x01, 0x00])


def parse_chipsea_frame(data: bytes, locked: bool = False) -> Optional[ScaleMeasurement]:
    """
    解析 Chipsea GATT 测量帧 (20 字节)
    
    布局:
      [0-1]   flags (2B LE): 0x04C4=体脂秤
      [2-8]   时间回传 (年2B LE + 月 + 日 + 时 + 分 + 秒) - 7 字节
      [9-10]  阻抗1 (2B LE)
      [11-12] 体重 (2B LE)
      [13-14] 阻抗2 (2B LE)
      [15]    property: bit7=测量中, bit6-5=单位, bit4-3=小数
      [16-19] 扩展字段
    """
    if data is None or len(data) < 13:
        return None

    raw_hex = data.hex().upper()

    flags = (data[0] & 0xFF) | ((data[1] & 0xFF) << 8)
    weight_raw = (data[11] & 0xFF) | ((data[12] & 0xFF) << 8)
    impedance_raw = (data[9] & 0xFF) | ((data[10] & 0xFF) << 8) if len(data) >= 11 else 0
    impedance2_raw = (data[13] & 0xFF) | ((data[14] & 0xFF) << 8) if len(data) >= 15 else 0
    property_byte = data[15] & 0xFF if len(data) > 15 else 0

    # property 解析 (Chipsea 表一)
    measuring = (property_byte & 0x80) != 0  # bit7: 测量中
    unit_code = (property_byte >> 3) & 0x03   # bit6-5: 单位
    decimal_code = (property_byte >> 1) & 0x03 # bit4-3: 小数位数

    unit_labels = {0: "kg", 1: "斤", 2: "lb", 3: "st:lb"}
    unit = unit_labels.get(unit_code, "kg")

    # Chipsea GATT 小数位: 0x01=0位, 0x02=1位, 0x00=2位
    decimal_places = {0x01: 0, 0x02: 1, 0x00: 2}.get(decimal_code, 2)
    divisor = 10 ** decimal_places

    weight_value = weight_raw / divisor

    # 转换为公斤
    if unit_code == 0:  # kg
        weight_kg = round(weight_value * 10) / 10
    elif unit_code == 1:  # 斤
        weight_kg = round(weight_value / 2.0 * 10) / 10
    elif unit_code == 2:  # lb
        weight_kg = round(weight_value * 0.45359237 * 10) / 10
    else:  # st:lb
        stones = (weight_raw >> 8) & 0xFF
        pounds = (weight_raw & 0xFF) / 10.0
        weight_kg = round((stones * 14.0 + pounds) * 0.45359237 * 10) / 10

    # 阻抗 (1/10 Ω)
    impedance_ohm = round(impedance_raw / 10.0, 1) if impedance_raw > 0 else None
    impedance2_ohm = round(impedance2_raw / 10.0, 1) if impedance2_raw > 0 else None

    # 详情
    status = "🔒" if locked else ("📏" if measuring else "🔄")
    detail_parts = [status]
    detail_parts.append(f"flags=0x{flags:04X}")
    detail_parts.append(f"prop=0x{property_byte:02X}")
    if measuring:
        detail_parts.append("测量中")
    if locked:
        detail_parts.append("已锁定")
    detail_parts.append(f"Z1={impedance_ohm}Ω" if impedance_ohm else "Z1=N/A")
    if impedance2_ohm:
        detail_parts.append(f"Z2={impedance2_ohm}Ω")
    detail_parts.append(f"bytes[16-19]={data[16:20].hex().upper() if len(data) > 19 else 'short'}")

    return ScaleMeasurement(
        weight_kg=weight_kg,
        unit=unit,
        weight_raw=weight_raw,
        impedance_raw=impedance_raw,
        impedance_ohm=impedance_ohm,
        impedance2_raw=impedance2_raw,
        impedance2_ohm=impedance2_ohm,
        locked=locked,
        detail=" ".join(detail_parts),
        raw_hex=raw_hex,
    )


async def scan_for_scale(timeout: float = 5.0) -> Optional[str]:
    """扫描并返回 Chipsea_BLE 设备地址 (UUID)"""
    from bleak import BleakScanner

    found = await BleakScanner.discover(timeout=timeout, return_adv=True)

    for addr, value in found.items():
        d, adv = value[0], value[1] if isinstance(value, tuple) else value
        name = str(getattr(d, 'name', ''))
        if 'chipsea' in name.lower():
            rssi = getattr(adv, 'rssi', None)
            print(f'✅ 找到: {name} @ {addr} (RSSI={rssi})', flush=True)
            return addr

    return None


async def connect_and_monitor(addr: str):
    """连接 Chipsea_BLE 并接收测量数据 (区分过程/结果)"""
    from bleak import BleakClient

    print(f'\n🔗 连接: {addr}', flush=True)
    client = BleakClient(addr, timeout=12.0)

    process_packets = []  # 0xFFF1 数据
    result_packets = []   # 0x2A9C 数据
    latest_result = None

    def handler(sender, data):
        """处理通知数据"""
        nonlocal latest_result
        raw = bytes(data)
        char_uuid = str(sender).lower()
        is_locked = "2a9c" in char_uuid
        is_process = "fff1" in char_uuid

        m = parse_chipsea_frame(raw, locked=is_locked)
        if m:
            if is_process:
                process_packets.append(m)
            elif is_locked:
                result_packets.append(m)
                latest_result = m

            label = "🔒 结果" if is_locked else ("📏 过程" if is_process else "❓ 未知")
            z1_str = f"Z1={m.impedance_ohm:.1f}Ω" if m.impedance_ohm else "Z1=N/A"
            z2_str = f"Z2={m.impedance2_ohm:.1f}Ω" if m.impedance2_ohm else ""
            print(f"  [{time.time()-t0:.1f}s] {label}: {m.weight_kg:.1f} {m.unit} {z1_str} {z2_str}", flush=True)
            print(f"       {m.detail} | raw={raw.hex().upper()}", flush=True)

    t0 = time.time()

    try:
        await asyncio.wait_for(client.connect(), timeout=12.0)
        print(f'✅ 连接成功 ({time.time()-t0:.1f}s)', flush=True)

        # 订阅
        await client.start_notify(CHAR_PROCESS, handler)
        print(f'  ✅ 订阅 0xFFF1 (过程)', flush=True)
        await client.start_notify(CHAR_RESULT, handler)
        print(f'  ✅ 订阅 0x2A9C (结果)', flush=True)

        # 同步时间
        await asyncio.wait_for(
            client.write_gatt_char(CHAR_TIME, make_time_packet(), response=True), timeout=2.0)
        print(f'  ⏱️  时间同步: {datetime.now().strftime("%H:%M:%S")}', flush=True)

        # 设置公斤
        await asyncio.wait_for(
            client.write_gatt_char(CHAR_UNIT, make_unit_packet(), response=True), timeout=2.0)
        print(f'  👤 单位: 公斤', flush=True)

        print(f'\n  ⏱️  等待测量 (15 秒)... 请踏秤', flush=True)
        print(f'  {"━"*40}', flush=True)

        await asyncio.sleep(15)

        print(f'\n{"━"*40}', flush=True)
        print(f'  📊 统计:', flush=True)
        print(f'    过程数据 (0xFFF1): {len(process_packets)} 条', flush=True)
        print(f'    结果数据 (0x2A9C): {len(result_packets)} 条', flush=True)

        if latest_result:
            print(f'\n  🎯 最终结果 (0x2A9C):', flush=True)
            print(f'     体重: {latest_result.weight_kg:.1f} {latest_result.unit}', flush=True)
            if latest_result.impedance_ohm:
                print(f'     阻抗: {latest_result.impedance_ohm:.1f} Ω', flush=True)
            print(f'     详情: {latest_result.detail}', flush=True)
            print(f'     原始: {latest_result.raw_hex}', flush=True)
        elif process_packets:
            # 如果没有 0x2A9C 数据, 使用最后一条 0xFFF1 数据
            last = process_packets[-1]
            print(f'\n  🎯 最后过程数据 (0xFFF1):', flush=True)
            print(f'     体重: {last.weight_kg:.1f} {last.unit}', flush=True)
            print(f'     详情: {last.detail}', flush=True)
            print(f'     原始: {last.raw_hex}', flush=True)

    except Exception as e:
        print(f'\n❌ 错误 ({time.time()-t0:.1f}s): {e}', flush=True)
    finally:
        await client.disconnect()
        print(f'\n🔌 断开 ({time.time()-t0:.1f}s)', flush=True)


async def main():
    parser = argparse.ArgumentParser(description="Chipsea_BLE 体脂秤连接工具 (v2)")
    parser.add_argument("--addr", type=str, default="", help="指定 UUID 地址")
    parser.add_argument("--scan-only", action="store_true", help="仅扫描")
    parser.add_argument("--wait", type=float, default=3.0, help="等待唤醒秒数")
    args = parser.parse_args()

    if args.scan_only:
        result = await scan_for_scale(timeout=8.0)
        if result:
            print(f'💡 下次: python3 {sys.argv[0]} --addr "{result}"', flush=True)
        return

    if args.wait > 0:
        print(f'⏳ 等待 {args.wait}s...', flush=True)
        await asyncio.sleep(args.wait)

    addr = args.addr
    if not addr:
        # 轮询扫描直到找到设备
        print('🔍 轮询扫描 (最多 60s)...', flush=True)
        start = time.time()
        while time.time() - start < 60:
            addr = await scan_for_scale(timeout=3.0)
            if addr:
                break
            await asyncio.sleep(0.3)
        if not addr:
            print('\n❌ 未找到设备，请电池重连后重试', flush=True)
            return

    await connect_and_monitor(addr)


if __name__ == "__main__":
    asyncio.run(main())
