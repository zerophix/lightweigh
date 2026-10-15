package com.qingke.health.domain.model;

/** 一条已保存的测量。算法编号空着时按 builtin-1 读取，不在这里重算。 */
public final class MeasureRecord {
    public final long time;
    public final double weight;
    public final double impedance;
    public final double fat;
    public final String mac;
    public final String algorithm;

    public MeasureRecord(long time, double weight, double impedance, double fat, String mac, String algorithm) {
        this.time = time;
        this.weight = weight;
        this.impedance = impedance;
        this.fat = fat;
        this.mac = mac == null ? "" : mac;
        this.algorithm = algorithm == null || algorithm.isEmpty() ? ScaleDevice.ALGORITHM_BUILTIN_1 : algorithm;
    }
}
