package com.xcwk.scalehub.feature.ai

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * T-083 AI 五态单元测试（原型 `06-ai.html`）。
 *
 * 覆盖验收里可自动判定的两部分：
 * 1. 文案：v-generating / v-failed / v-unauth / v-plan / v-auth 五屏逐字断言
 *    （走 [aiStageVisibleTexts]，与 Compose 共用同一批常量，改一个字就红）；
 * 2. 状态推进：生成中 → 成功 / 失败 / 停止、失败 → 重发、未授权 → 授权引导、
 *    授权设置 → 撤回，以及非法事件原地不动（[reduceAiStage]）。
 *
 * 视觉布局不在单测范围（需真机 / 截图），故这里只钉死文案与状态机。
 */
class AiStatesTest {

    private fun state(stage: AiStage) = AiUiState(loading = false, formal = true, stage = stage)

    // ---- 一、五个 view 的逐字文案 ----

    @Test
    fun `生成中逐字文案`() {
        val texts = aiStageVisibleTexts(state(AiStage.Generating))
        assertThat(texts).containsAtLeast(
            "AI 健康助手",
            "怎样测量更可比？",
            "轻衡 AI",
            "正在分析…",
            "生成中…",
            "停止生成",
            "（或等待自动完成 → ）",
        ).inOrder()
    }

    @Test
    fun `发送失败逐字文案`() {
        val texts = aiStageVisibleTexts(state(AiStage.Failed))
        assertThat(texts).containsAtLeast(
            "AI 健康助手",
            "发送失败",
            "重发",
            "睡眠不好会影响体脂吗？",
            "网络连接已断开，恢复后可重发。",
            "继续聊聊你的健康问题…",
        ).inOrder()
    }

    @Test
    fun `未授权逐字文案`() {
        val texts = aiStageVisibleTexts(state(AiStage.Unauthenticated))
        assertThat(texts).containsAtLeast(
            "AI 健康助手",
            "设置",
            "未开启个性化分析，AI 仅提供通用健康知识。",
            "BMI 22.1 正常吗？",
            "体脂率怎么看？",
            "通用健康问题都可以问我。要获得针对你数据的解读，请先开启个性化分析。",
            "去开启个性化",
        ).inOrder()
    }

    @Test
    fun `本周行动计划逐字文案`() {
        val texts = aiStageVisibleTexts(state(AiStage.Plan))
        assertThat(texts).containsAtLeast(
            "本周行动计划",
            "已采纳",
            "10月21日生效",
            "本周 1/3 已完成，继续保持。",
            "轻松散步 20 分钟",
            "已完成 · 10月21日",
            "固定时段测量体重",
            "建议任选 3 晚",
            "记录睡眠感受",
            "长按跳过示例",
        ).inOrder()
    }

    @Test
    fun `AI 数据设置逐字文案`() {
        val texts = aiStageVisibleTexts(state(AiStage.AuthSettings))
        assertThat(texts).containsAtLeast(
            "AI 数据设置",
            "个性化分析已开启",
            "2026年10月21日授权",
            "使用测量记录",
            "使用基本资料",
            "使用个人目标",
            "删除全部 AI 对话",
            "撤回全部授权",
            "关闭后仍可一般咨询",
            "关闭后 AI 不使用你的个人健康数据。",
        ).inOrder()
    }

    @Test
    fun `对话态展示真实消息而不是原型示例`() {
        val texts = aiStageVisibleTexts(
            AiUiState(messages = listOf(AiMessage(true, "我该吃多少？"), AiMessage(false, "按体重算。"))),
        )
        assertThat(texts).containsExactly("AI 健康助手", "我该吃多少？", "按体重算。").inOrder()
    }

    // ---- 二、生成链路状态推进 ----

    @Test
    fun `对话进入生成中`() {
        assertThat(reduceAiStage(AiStage.Chat, AiStageEvent.StartGeneration))
            .isEqualTo(AiStage.Generating)
    }

    @Test
    fun `生成中成功后回到对话`() {
        assertThat(reduceAiStage(AiStage.Generating, AiStageEvent.GenerationSucceeded))
            .isEqualTo(AiStage.Chat)
    }

    @Test
    fun `生成中失败进入发送失败`() {
        assertThat(reduceAiStage(AiStage.Generating, AiStageEvent.GenerationFailed))
            .isEqualTo(AiStage.Failed)
    }

    @Test
    fun `生成中可停止回到对话`() {
        assertThat(reduceAiStage(AiStage.Generating, AiStageEvent.StopGeneration))
            .isEqualTo(AiStage.Chat)
    }

    @Test
    fun `失败后重发回到生成中`() {
        assertThat(reduceAiStage(AiStage.Failed, AiStageEvent.Retry))
            .isEqualTo(AiStage.Generating)
    }

    @Test
    fun `完整链路 发送到失败到重发到成功`() {
        var stage = AiStage.Chat
        stage = reduceAiStage(stage, AiStageEvent.StartGeneration)
        assertThat(stage).isEqualTo(AiStage.Generating)
        stage = reduceAiStage(stage, AiStageEvent.GenerationFailed)
        assertThat(stage).isEqualTo(AiStage.Failed)
        stage = reduceAiStage(stage, AiStageEvent.Retry)
        assertThat(stage).isEqualTo(AiStage.Generating)
        stage = reduceAiStage(stage, AiStageEvent.GenerationSucceeded)
        assertThat(stage).isEqualTo(AiStage.Chat)
    }

    // ---- 三、授权引导链路 ----

    @Test
    fun `未授权去开启个性化进入授权设置`() {
        assertThat(reduceAiStage(AiStage.Unauthenticated, AiStageEvent.EnablePersonalization))
            .isEqualTo(AiStage.AuthSettings)
    }

    @Test
    fun `授权设置撤回后回到未授权`() {
        assertThat(reduceAiStage(AiStage.AuthSettings, AiStageEvent.WithdrawAuthorization))
            .isEqualTo(AiStage.Unauthenticated)
    }

    @Test
    fun `授权链路可来回往返`() {
        var stage = AiStage.Unauthenticated
        stage = reduceAiStage(stage, AiStageEvent.EnablePersonalization)
        stage = reduceAiStage(stage, AiStageEvent.WithdrawAuthorization)
        assertThat(stage).isEqualTo(AiStage.Unauthenticated)
    }

    // ---- 四、入口与非法事件 ----

    @Test
    fun `对话可进入本周计划与授权设置`() {
        assertThat(reduceAiStage(AiStage.Chat, AiStageEvent.OpenPlan)).isEqualTo(AiStage.Plan)
        assertThat(reduceAiStage(AiStage.Chat, AiStageEvent.OpenAuthSettings))
            .isEqualTo(AiStage.AuthSettings)
    }

    @Test
    fun `计划与设置可返回对话`() {
        assertThat(reduceAiStage(AiStage.Plan, AiStageEvent.BackToChat)).isEqualTo(AiStage.Chat)
        assertThat(reduceAiStage(AiStage.AuthSettings, AiStageEvent.BackToChat))
            .isEqualTo(AiStage.Chat)
    }

    @Test
    fun `非法事件原地不动`() {
        // 生成中不允许直接撤回授权，也不会被「打开计划」打断
        assertThat(reduceAiStage(AiStage.Generating, AiStageEvent.WithdrawAuthorization))
            .isEqualTo(AiStage.Generating)
        assertThat(reduceAiStage(AiStage.Generating, AiStageEvent.OpenPlan))
            .isEqualTo(AiStage.Generating)
        // 对话态不能重发（没有失败上下文）
        assertThat(reduceAiStage(AiStage.Chat, AiStageEvent.Retry)).isEqualTo(AiStage.Chat)
    }

    @Test
    fun `默认状态停在对话不误入五态`() {
        val initial = AiUiState()
        assertThat(initial.stage).isEqualTo(AiStage.Chat)
        assertThat(initial.plan.items).hasSize(3)
    }

    // ---- 五、默认数据与原型一致 ----

    @Test
    fun `计划默认三条任务与原型一致`() {
        val items = defaultAiPlanItems()
        assertThat(items.map { it.title })
            .containsExactly("轻松散步 20 分钟", "固定时段测量体重", "记录睡眠感受").inOrder()
        assertThat(items.map { it.done }).containsExactly(true, false, false).inOrder()
    }

    @Test
    fun `授权默认开启`() {
        val auth = AiAuthorization()
        assertThat(auth.enabled).isTrue()
        assertThat(listOf(auth.useMeasurements, auth.useProfile, auth.useGoals))
            .containsExactly(true, true, true)
    }
}
