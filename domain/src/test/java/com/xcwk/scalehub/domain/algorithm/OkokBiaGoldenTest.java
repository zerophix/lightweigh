package com.xcwk.scalehub.domain.algorithm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 黄金向量测试 — 体脂算法的跨端一致性守卫。
 *
 * 向量由 spec/tools/gen_vectors.py 生成（单一事实源），同时被
 * qk-health-ios 的 XCTest 加载，任何一端算法漂移都会被本测试捕获。
 *
 * fixtures: spec/fixtures/algorithm/body-composition-vectors.tsv
 *           （运行前同步到 app/src/test/resources/golden/）
 * 系数出处: proto-okok/docs/notes/NOTE-20261014-001.md
 */
public class OkokBiaGoldenTest {

    private static final double TOLERANCE = 0.05;

    private static final class Vector {
        String id;
        String algorithm;
        String status;
        boolean male;
        int age;
        int heightCm;
        double weightKg;
        double impedance;
        // expected
        Double fat, muscleKg, musclePct, waterPct, waterKg, proteinPct, boneKg, visceral, bmr;
        Double bodyAge;
    }

    private List<Vector> load() throws Exception {
        List<Vector> out = new ArrayList<>();
        InputStream in = getClass().getResourceAsStream("/golden/body-composition-vectors.tsv");
        assertNotNull("缺少黄金向量资源 /golden/body-composition-vectors.tsv", in);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String header = reader.readLine();
            assertNotNull(header);
            String line;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                String[] c = line.split("\t", -1);
                Vector v = new Vector();
                v.id = c[0];
                v.algorithm = c[1];
                v.status = c[3];
                v.male = "1".equals(c[4]);
                v.age = Integer.parseInt(c[5]);
                v.heightCm = Integer.parseInt(c[6]);
                v.weightKg = Double.parseDouble(c[7]);
                v.impedance = Double.parseDouble(c[8]);
                v.fat = num(c[11]);
                v.muscleKg = num(c[12]);
                v.musclePct = num(c[13]);
                v.waterPct = num(c[14]);
                v.waterKg = num(c[15]);
                v.proteinPct = num(c[16]);
                v.boneKg = num(c[17]);
                v.visceral = num(c[18]);
                v.bmr = num(c[19]);
                v.bodyAge = num(c[20]);
                out.add(v);
            }
        }
        return out;
    }

    private static Double num(String s) {
        return s == null || s.isEmpty() ? null : Double.parseDouble(s);
    }

    private static void assertClose(String label, Double expected, double actual) {
        if (expected == null) {
            return; // 该向量此字段无期望值（如未成年无内脏脂肪）
        }
        assertEquals(label, expected, actual, TOLERANCE);
    }

    @Test
    public void allActiveVectorsPass() throws Exception {
        int checked = 0;
        for (Vector v : load()) {
            if (!"active".equals(v.status)) {
                continue; // pending_implementation 的向量等待 8 电极分支落地后启用
            }
            if ("okok-bia-1".equals(v.algorithm)) {
                OkokBia.Analysis a = OkokBia.analyze(v.male, v.age, v.heightCm, v.weightKg, v.impedance);
                assertNotNull(v.id + " 未产生分析结果", a);
                assertClose(v.id + ".fat", v.fat, a.fatPercent);
                assertClose(v.id + ".muscleKg", v.muscleKg, a.muscleKg);
                assertClose(v.id + ".musclePct", v.musclePct, a.musclePercent);
                assertClose(v.id + ".waterPct", v.waterPct, a.waterPercent == null ? 0 : a.waterPercent);
                assertClose(v.id + ".waterKg", v.waterKg, a.waterKg == null ? 0 : a.waterKg);
                assertClose(v.id + ".proteinPct", v.proteinPct, a.proteinPercent == null ? 0 : a.proteinPercent);
                assertClose(v.id + ".boneKg", v.boneKg, a.boneKg == null ? 0 : a.boneKg);
                assertClose(v.id + ".visceral", v.visceral, a.visceral == null ? 0 : a.visceral);
                assertClose(v.id + ".bmr", v.bmr, a.bmr == null ? 0 : a.bmr);
                if (v.bodyAge != null) {
                    assertEquals(v.id + ".bodyAge", v.bodyAge.intValue(), a.bodyAge);
                }
                checked++;
            } else if ("builtin-1".equals(v.algorithm)) {
                double fat = BuiltinComposition.INSTANCE.fatPercent(
                        v.male, v.age, v.heightCm, v.weightKg, v.impedance);
                assertClose(v.id + ".builtin.fat", v.fat, fat);
                checked++;
            }
        }
        assertTrue("没有消费任何向量", checked > 0);
    }

    @Test
    public void eightElectrodeVectorsReserved() throws Exception {
        // 8 电极分支未实现前：确认向量已在 fixture 中就位（防止 fixture 被误删）
        boolean found = false;
        for (Vector v : load()) {
            if ("pending_implementation".equals(v.status)) {
                found = true;
            }
        }
        assertTrue("缺少 pending 的 8 电极向量", found);
    }
}
