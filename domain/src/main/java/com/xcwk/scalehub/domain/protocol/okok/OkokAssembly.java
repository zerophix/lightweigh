package com.xcwk.scalehub.domain.protocol.okok;

import com.xcwk.scalehub.domain.protocol.packet.MeasureFrame;

/** 把 C9 两包、CA 三包收齐。其它帧头立刻解析。 */
final class OkokAssembly {

    private static final int HEADER_EIGHT_HR = 0xC9;
    private static final int HEADER_EIGHT_MULTI = 0xCA;

    private int header = -1;
    private int total;
    private int next;
    private byte[] first;
    private byte[] second;
    private boolean pending;

    void reset() {
        header = -1;
        total = 0;
        next = 0;
        first = null;
        second = null;
        pending = false;
    }

    boolean isPending() {
        return pending;
    }

    MeasureFrame offer(byte[] data) {
        pending = false;
        if (data == null || data.length == 0) {
            return null;
        }
        int kind = data[0] & 0xFF;
        if (kind != HEADER_EIGHT_HR && kind != HEADER_EIGHT_MULTI) {
            reset();
            return MeasureFrame.parse(data);
        }
        if (data.length < 20) {
            reset();
            return null;
        }
        int info = data[1] & 0xFF;
        int index = info & 0x0F;
        int count = (info >> 4) & 0x0F;
        int expected = kind == HEADER_EIGHT_HR ? 2 : 3;
        if (count != expected || index >= count) {
            reset();
            return null;
        }
        if (index == 0) {
            header = kind;
            total = count;
            next = 1;
            first = data.clone();
            second = null;
            pending = true;
            return null;
        }
        if (header != kind || index != next || first == null) {
            reset();
            return null;
        }
        if (index == 1 && total == 3) {
            second = data.clone();
            next = 2;
            pending = true;
            return null;
        }
        byte[] packet1 = first;
        byte[] packet2 = total == 2 ? data : second;
        byte[] packet3 = total == 3 ? data : null;
        reset();
        if (packet2 == null || (total == 3 && packet3 == null)) {
            return null;
        }
        if (kind == HEADER_EIGHT_HR) {
            return MeasureFrame.eightHeart(packet1, packet2);
        }
        return MeasureFrame.eightMulti(packet1, packet2, packet3);
    }
}
