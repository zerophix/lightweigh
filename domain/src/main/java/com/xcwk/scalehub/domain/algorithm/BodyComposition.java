package com.xcwk.scalehub.domain.algorithm;

/** 由体重、阻抗和身体资料计算体脂。缺资料时返回负数，不填默认身高或年龄。 */
public interface BodyComposition {
    String id();

    double fatPercent(boolean male, int age, int heightCm, double weightKg, double impedanceOhm);
}
