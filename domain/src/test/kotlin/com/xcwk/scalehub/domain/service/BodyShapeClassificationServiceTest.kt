package com.xcwk.scalehub.domain.service

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * BodyShapeClassificationService 黄金向量测试
 *
 * 验证 3x3 体型分类矩阵与原始 BodyAnalysis.kt SHAPES 完全一致。
 */
class BodyShapeClassificationServiceTest {

    private val shapeService = BodyShapeClassificationService()
    private val bodyCompService = BodyCompositionService()
    private val maleFatStd = bodyCompService.fatStandard(true)
    private val femaleFatStd = bodyCompService.fatStandard(false)

    @Test
    fun `体脂等级判定_男性_边界值`() {
        assertEquals(BodyShapeClassificationService.FatBand.LOW, shapeService.determineFatBand(9.9, maleFatStd))
        assertEquals(BodyShapeClassificationService.FatBand.NORMAL, shapeService.determineFatBand(10.0, maleFatStd))
        assertEquals(BodyShapeClassificationService.FatBand.NORMAL, shapeService.determineFatBand(15.0, maleFatStd))
        assertEquals(BodyShapeClassificationService.FatBand.NORMAL, shapeService.determineFatBand(20.0, maleFatStd))
        assertEquals(BodyShapeClassificationService.FatBand.HIGH, shapeService.determineFatBand(20.1, maleFatStd))
    }

    @Test
    fun `体脂等级判定_女性_边界值`() {
        assertEquals(BodyShapeClassificationService.FatBand.LOW, shapeService.determineFatBand(17.9, femaleFatStd))
        assertEquals(BodyShapeClassificationService.FatBand.NORMAL, shapeService.determineFatBand(18.0, femaleFatStd))
        assertEquals(BodyShapeClassificationService.FatBand.NORMAL, shapeService.determineFatBand(23.0, femaleFatStd))
        assertEquals(BodyShapeClassificationService.FatBand.NORMAL, shapeService.determineFatBand(28.0, femaleFatStd))
        assertEquals(BodyShapeClassificationService.FatBand.HIGH, shapeService.determineFatBand(28.1, femaleFatStd))
    }

    @Test
    fun `BMI等级判定_边界值`() {
        assertEquals(BodyShapeClassificationService.BmiBand.UNDERWEIGHT, shapeService.determineBmiBand(18.4))
        assertEquals(BodyShapeClassificationService.BmiBand.NORMAL, shapeService.determineBmiBand(18.5))
        assertEquals(BodyShapeClassificationService.BmiBand.NORMAL, shapeService.determineBmiBand(22.0))
        assertEquals(BodyShapeClassificationService.BmiBand.NORMAL, shapeService.determineBmiBand(23.9))
        assertEquals(BodyShapeClassificationService.BmiBand.OVERWEIGHT, shapeService.determineBmiBand(24.0))
        assertEquals(BodyShapeClassificationService.BmiBand.OVERWEIGHT, shapeService.determineBmiBand(27.9))
        assertEquals(BodyShapeClassificationService.BmiBand.OVERWEIGHT, shapeService.determineBmiBand(28.0))
        assertEquals(BodyShapeClassificationService.BmiBand.OVERWEIGHT, shapeService.determineBmiBand(35.0))
    }

    @Test
    fun `3x3矩阵完整验证_男性`() {
        val matrix = shapeService.getShapeMatrix()
        assertEquals(3, matrix.size)
        assertEquals(3, matrix[0].size)

        // 第1行：体脂偏低
        assertEquals("消瘦型", matrix[0][0])
        assertEquals("低脂肪型", matrix[0][1])
        assertEquals("运动员型", matrix[0][2])

        // 第2行：体脂标准
        assertEquals("苗条肌肉型", matrix[1][0])
        assertEquals("苗条型", matrix[1][1])
        assertEquals("健康型", matrix[1][2])

        // 第3行：体脂偏高
        assertEquals("隐形肥胖型", matrix[2][0])
        assertEquals("偏胖型", matrix[2][1])
        assertEquals("肥胖型", matrix[2][2])
    }

    @Test
    fun `分类完整流程_男性_9大类全覆盖`() {
        // 消瘦型
        assertEquals("消瘦型", shapeService.classify(8.0, 17.0, maleFatStd)?.shapeName)
        // 低脂肪型
        assertEquals("低脂肪型", shapeService.classify(8.0, 22.0, maleFatStd)?.shapeName)
        // 运动员型
        assertEquals("运动员型", shapeService.classify(8.0, 26.0, maleFatStd)?.shapeName)

        // 苗条肌肉型
        assertEquals("苗条肌肉型", shapeService.classify(15.0, 17.0, maleFatStd)?.shapeName)
        // 苗条型
        assertEquals("苗条型", shapeService.classify(15.0, 22.0, maleFatStd)?.shapeName)
        // 健康型
        assertEquals("健康型", shapeService.classify(15.0, 26.0, maleFatStd)?.shapeName)

        // 隐形肥胖型
        assertEquals("隐形肥胖型", shapeService.classify(25.0, 17.0, maleFatStd)?.shapeName)
        // 偏胖型
        assertEquals("偏胖型", shapeService.classify(25.0, 22.0, maleFatStd)?.shapeName)
        // 肥胖型
        assertEquals("肥胖型", shapeService.classify(25.0, 26.0, maleFatStd)?.shapeName)
    }

    @Test
    fun `分类完整流程_女性_9大类全覆盖`() {
        // 消瘦型
        assertEquals("消瘦型", shapeService.classify(16.0, 17.0, femaleFatStd)?.shapeName)
        // 低脂肪型
        assertEquals("低脂肪型", shapeService.classify(16.0, 22.0, femaleFatStd)?.shapeName)
        // 运动员型
        assertEquals("运动员型", shapeService.classify(16.0, 26.0, femaleFatStd)?.shapeName)

        // 苗条肌肉型
        assertEquals("苗条肌肉型", shapeService.classify(22.0, 17.0, femaleFatStd)?.shapeName)
        // 苗条型
        assertEquals("苗条型", shapeService.classify(22.0, 22.0, femaleFatStd)?.shapeName)
        // 健康型
        assertEquals("健康型", shapeService.classify(22.0, 26.0, femaleFatStd)?.shapeName)

        // 隐形肥胖型
        assertEquals("隐形肥胖型", shapeService.classify(30.0, 17.0, femaleFatStd)?.shapeName)
        // 偏胖型
        assertEquals("偏胖型", shapeService.classify(30.0, 22.0, femaleFatStd)?.shapeName)
        // 肥胖型
        assertEquals("肥胖型", shapeService.classify(30.0, 26.0, femaleFatStd)?.shapeName)
    }

    @Test
    fun `输入不足返回null`() {
        assertNull(shapeService.classify(null, 22.0, maleFatStd))
        assertNull(shapeService.classify(15.0, null, maleFatStd))
    }

    @Test
    fun `描述文案非空`() {
        val result = shapeService.classify(15.0, 22.0, maleFatStd)
        assertNotNull(result)
        assertNotNull(result?.description)
        assertTrue(result!!.description.isNotEmpty())
    }

    @Test
    fun `所有体型名称列表_共9种`() {
        assertEquals(9, BodyShapeClassificationService.ALL_SHAPES.size)
        assertTrue("消瘦型" in BodyShapeClassificationService.ALL_SHAPES)
        assertTrue("肥胖型" in BodyShapeClassificationService.ALL_SHAPES)
    }
}