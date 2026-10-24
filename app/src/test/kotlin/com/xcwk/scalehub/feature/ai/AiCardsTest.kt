package com.xcwk.scalehub.feature.ai

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * T-047 AI 结构化卡片行为级验收（DEC-043-B）。
 *
 * 覆盖验收里的三条 then：
 * 1. 四类卡片各自的可见文案：饮食卡（热量 / 三大营养素）、运动卡（时长 / 消耗）、
 *    指标解读卡（指标名 / 解读）、欢迎卡（示例引导）；
 * 2. 解析入库：合法结构化数据推进状态，非法数据被拒绝且状态原样保留；
 * 3. 拍照入口：回调恰好触发一次；未实现（回调为空）时判定为不可用，不渲染可点入口。
 *
 * 视觉布局不在单测范围（需真机 / 截图），故这里只钉死文案、入库语义与回调次数。
 * 卡片文案走 [dietCardTexts] 等纯函数，与 Compose 共用同一批字面量，改一个字就红。
 */
class AiCardsTest {

    // ---- 一、四类卡片的渲染内容 ----

    @Test
    fun `饮食卡渲染日期 热量与三大营养素`() {
        val card = DietCard(
            date = "2026年10月24日",
            meals = listOf(
                DietMeal(name = "早餐", calories = 420, protein = 20.0, carbs = 55.0, fat = 12.0),
                DietMeal(name = "午餐", calories = 630, protein = 35.0, carbs = 70.0, fat = 20.0),
            ),
        )

        val texts = dietCardTexts(card)

        assertThat(texts).containsAtLeast(
            "饮食记录 2026年10月24日",
            "早餐",
            "蛋白质 20.0g · 碳水 55.0g · 脂肪 12.0g",
            "420 千卡",
            "午餐",
            "蛋白质 35.0g · 碳水 70.0g · 脂肪 20.0g",
            "630 千卡",
            "编辑",
            "删除",
            "保存",
        ).inOrder()
    }

    @Test
    fun `饮食卡每餐都带热量与三大营养素`() {
        val card = DietCard(
            date = "2026年10月24日",
            meals = listOf(
                DietMeal(name = "早餐", calories = 420, protein = 20.0, carbs = 55.0, fat = 12.0),
                DietMeal(name = "晚餐", calories = 380, protein = 18.0, carbs = 40.0, fat = 10.0),
            ),
        )

        val texts = dietCardTexts(card)

        assertThat(texts.count { it.endsWith("千卡") }).isEqualTo(card.meals.size)
        assertThat(texts.count { it.startsWith("蛋白质 ") }).isEqualTo(card.meals.size)
    }

    @Test
    fun `运动卡渲染名称 时长与消耗`() {
        val texts = exerciseCardTexts(ExerciseCard(name = "快走", duration = 45, calories = 210))

        assertThat(texts).containsAtLeast("快走", "45 分钟 · 210 千卡").inOrder()
        assertThat(texts).hasSize(2)
    }

    @Test
    fun `运动卡有心率时追加心率 无心率时不渲染该项`() {
        assertThat(exerciseCardTexts(ExerciseCard("慢跑", 30, 260, heartRate = 132)))
            .contains("心率 132 bpm")

        val noHeartRate = exerciseCardTexts(ExerciseCard("慢跑", 30, 260))
        assertThat(noHeartRate.none { it.contains("心率") }).isTrue()
    }

    @Test
    fun `指标解读卡渲染指标名 数值 等级与解读`() {
        val card = MetricCard(
            metricName = "体脂率",
            value = "28.4%",
            level = AiCardText.LevelHigh,
            advice = "略高于同龄区间，建议保持每周三次有氧。",
            tone = 2,
        )

        assertThat(metricCardTexts(card)).containsExactly(
            "体脂率",
            "28.4%",
            "偏高",
            "略高于同龄区间，建议保持每周三次有氧。",
        ).inOrder()
    }

    @Test
    fun `指标解读卡覆盖偏低 标准 偏高三个等级`() {
        val levels = listOf(
            MetricCard("BMI", "17.2", AiCardText.LevelLow, "偏低解读", 0),
            MetricCard("BMI", "22.1", AiCardText.LevelNormal, "标准解读", 1),
            MetricCard("BMI", "29.6", AiCardText.LevelHigh, "偏高解读", 2),
        )

        assertThat(levels.map { metricCardTexts(it)[2] })
            .containsExactly("偏低", "标准", "偏高")
            .inOrder()
        assertThat(levels.map { metricCardTexts(it)[3] })
            .containsExactly("偏低解读", "标准解读", "偏高解读")
            .inOrder()
    }

    @Test
    fun `欢迎卡渲染引导语与四个示例`() {
        val texts = welcomeTexts()

        assertThat(texts).containsAtLeast(
            "你好，我是你的健康助理",
            "我可以帮你分析身体数据、解答健康问题、记录饮食和运动。",
            "试试这些问题：",
            "· 我今天的体脂率正常吗？",
            "· 帮我制定一个减脂计划",
            "· 早餐吃什么有助于减肥？",
            "· 如何提高基础代谢？",
        ).inOrder()
    }

    // ---- 二、示例引导与拍照入口的回调语义 ----

    @Test
    fun `示例引导已接入时点击回调恰好一次且携带问题原文`() {
        val clicked = mutableListOf<String>()
        val callback: (String) -> Unit = { clicked += it }
        val question = AiCardText.WelcomeExamples.first()

        assertThat(aiExampleClickAvailable(callback)).isTrue()
        assertThat(triggerAiExample(callback, question)).isTrue()

        assertThat(clicked).containsExactly(question)
    }

    @Test
    fun `示例引导未接入时不算可点且不吞点击`() {
        assertThat(aiExampleClickAvailable(null)).isFalse()
        assertThat(triggerAiExample(null, "我今天的体脂率正常吗？")).isFalse()
    }

    @Test
    fun `拍照入口已接入时回调恰好触发一次`() {
        var calls = 0
        val callback: () -> Unit = { calls += 1 }

        assertThat(aiPhotoEntryAvailable(callback)).isTrue()
        assertThat(triggerAiPhotoEntry(callback)).isTrue()

        assertThat(calls).isEqualTo(1)
    }

    @Test
    fun `拍照入口未接入时判定不可用 不渲染可点死入口`() {
        // 回调为空 = 相机/选图尚未接入，Composable 据此直接不渲染入口。
        assertThat(aiPhotoEntryAvailable(null)).isFalse()
        assertThat(triggerAiPhotoEntry(null)).isFalse()
    }

    // ---- 三、解析入库 ----

    @Test
    fun `合法饮食卡入库后状态携带该卡`() {
        val store = AiCardsStore()
        val card = DietCard(
            date = "2026年10月24日",
            meals = listOf(DietMeal("早餐", 420, 20.0, 55.0, 12.0)),
        )

        assertThat(store.submit(AiCardPayload.Diet(card))).isTrue()
        assertThat(store.state.diet).isEqualTo(card)
    }

    @Test
    fun `合法运动卡入库 新卡覆盖同槽位`() {
        val store = AiCardsStore()
        val first = ExerciseCard("快走", 45, 210)
        val second = ExerciseCard("慢跑", 30, 260, heartRate = 132)

        assertThat(store.submit(AiCardPayload.Exercise(first))).isTrue()
        assertThat(store.state.exercise).isEqualTo(first)

        assertThat(store.submit(AiCardPayload.Exercise(second))).isTrue()
        assertThat(store.state.exercise).isEqualTo(second)
    }

    @Test
    fun `合法指标卡入库按顺序累积`() {
        val store = AiCardsStore()
        val fat = MetricCard("体脂率", "28.4%", AiCardText.LevelHigh, "偏高解读", 2)
        val bmi = MetricCard("BMI", "22.1", AiCardText.LevelNormal, "标准解读", 1)

        assertThat(store.submit(AiCardPayload.Metric(fat))).isTrue()
        assertThat(store.submit(AiCardPayload.Metric(bmi))).isTrue()

        assertThat(store.state.metrics).containsExactly(fat, bmi).inOrder()
    }

    @Test
    fun `非法饮食卡被拒绝且不改状态`() {
        val before = AiCardsState()
        val store = AiCardsStore(before)
        val emptyMeals = DietCard(date = "2026年10月24日", meals = emptyList())
        val blankDate = DietCard(
            date = " ",
            meals = listOf(DietMeal("早餐", 420, 20.0, 55.0, 12.0)),
        )
        val negative = DietCard(
            date = "2026年10月24日",
            meals = listOf(DietMeal("早餐", -1, 20.0, 55.0, 12.0)),
        )

        listOf(emptyMeals, blankDate, negative).forEach { card ->
            assertThat(store.submit(AiCardPayload.Diet(card))).isFalse()
            assertThat(store.state).isEqualTo(before)
        }
    }

    @Test
    fun `非法运动卡被拒绝且不改状态`() {
        val before = AiCardsState()
        val store = AiCardsStore(before)

        listOf(
            ExerciseCard("", 30, 200),
            ExerciseCard("慢跑", 0, 200),
            ExerciseCard("慢跑", 30, -5),
            ExerciseCard("慢跑", 30, 200, heartRate = 0),
        ).forEach { card ->
            assertThat(store.submit(AiCardPayload.Exercise(card))).isFalse()
            assertThat(store.state).isEqualTo(before)
        }
    }

    @Test
    fun `非法指标卡被拒绝且不改状态`() {
        val before = AiCardsState()
        val store = AiCardsStore(before)

        listOf(
            MetricCard("", "22.1", AiCardText.LevelNormal, "解读", 1),
            MetricCard("BMI", "  ", AiCardText.LevelNormal, "解读", 1),
            MetricCard("BMI", "22.1", "", "解读", 1),
            MetricCard("BMI", "22.1", AiCardText.LevelNormal, "解读", 3),
        ).forEach { card ->
            assertThat(store.submit(AiCardPayload.Metric(card))).isFalse()
            assertThat(store.state).isEqualTo(before)
        }
    }

    @Test
    fun `非法数据不影响已入库的合法数据`() {
        val store = AiCardsStore()
        val valid = DietCard(
            date = "2026年10月24日",
            meals = listOf(DietMeal("早餐", 420, 20.0, 55.0, 12.0)),
        )
        store.submit(AiCardPayload.Diet(valid))

        assertThat(store.submit(AiCardPayload.Diet(valid.copy(meals = emptyList())))).isFalse()

        assertThat(store.state.diet).isEqualTo(valid)
    }

    @Test
    fun `拒绝时给出具体原因且纯函数不修改入参`() {
        val before = AiCardsState()
        val result = admitAiCard(before, AiCardPayload.Exercise(ExerciseCard("", 0, 0)))

        assertThat(result).isInstanceOf(AiCardAdmission.Rejected::class.java)
        assertThat((result as AiCardAdmission.Rejected).reason).isNotEmpty()
        assertThat(before).isEqualTo(AiCardsState())
    }
}
