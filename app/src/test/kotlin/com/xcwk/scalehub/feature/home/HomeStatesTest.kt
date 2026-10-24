package com.xcwk.scalehub.feature.home

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * T-095 首页八态单测。
 *
 * 覆盖验收里可自动判定的两部分：
 * - 原型 `design/prototypes/interactive/11-home-states.html` 八个 view 的文案逐字对齐；
 * - 纯状态机 [homeReduce] 的状态推进（进入各态、返回首页、成员切换、同步完成）。
 *
 * 视觉版式不在此测（需真机/截图）；文案被改一个字或状态转移写错，这里立刻红。
 */
class HomeStatesTest {

    private val prototypeViewIds = listOf(
        "v-home",
        "v-empty",
        "v-unconn",
        "v-syncing",
        "v-error",
        "v-switching",
        "v-brief",
        "v-goaldone",
    )

    // ---- 状态与原型 view 的映射 ----

    @Test
    fun `八个状态与原型 view id 一一对应`() {
        assertThat(HomeStage.entries.map { it.viewId }).containsExactlyElementsIn(prototypeViewIds).inOrder()
        assertThat(HomeStage.entries).hasSize(8)
    }

    @Test
    fun `初始状态为正常首页`() {
        val state = HomeUiState()
        assertThat(state.stage).isEqualTo(HomeStage.Home)
        assertThat(state.memberName).isEqualTo("林悦")
    }

    // ---- v-home 文案 ----

    @Test
    fun `正常首页文案逐字对齐原型`() {
        assertThat(HomeText.Brand).isEqualTo("轻衡")
        assertThat(HomeUiState().greeting).isEqualTo("早上好，林悦。")
        assertThat(HomeText.Slogan).isEqualTo("轻一点，更懂自己。")
        assertThat(HomeText.CurrentWeightHint).isEqualTo("当前体重 · 10月21日 07:30")
        assertThat(HomeText.WeightValue).isEqualTo("62.4")
        assertThat(HomeText.WeightUnit).isEqualTo("kg")
        assertThat(HomeText.WeightDelta).isEqualTo("↓0.3")
        assertThat(HomeUiState().goalHint).isEqualTo("向 58.0 kg 靠近 · 已减 2.8 kg")
        assertThat(HomeText.BodyFatLabel).isEqualTo("体脂率")
        assertThat(HomeText.BodyFatValue).isEqualTo("28.6")
        assertThat(HomeText.BmiLabel).isEqualTo("BMI")
        assertThat(HomeText.BmiValue).isEqualTo("22.1")
    }

    // ---- v-empty 文案 ----

    @Test
    fun `空数据文案逐字对齐原型`() {
        assertThat(HomeText.Brand).isEqualTo("轻衡")
        assertThat(HomeText.EmptyGreeting).isEqualTo("你好，开始记录吧")
        assertThat(HomeText.EmptySlogan).isEqualTo("完成第一次测量，就能看到身体概览。")
        assertThat(HomeText.EmptyTitle).isEqualTo("还没有测量数据")
        assertThat(HomeText.EmptyBodyLine1).isEqualTo("连接体脂秤并完成第一次测量后，")
        assertThat(HomeText.EmptyBodyLine2).isEqualTo("这里会显示你的体重与指标。")
        assertThat(HomeText.EmptyPrimary).isEqualTo("开始第一次测量")
        assertThat(HomeText.BackHome).isEqualTo("← 返回正常首页")
    }

    // ---- v-unconn 文案 ----

    @Test
    fun `设备未连接文案逐字对齐原型`() {
        assertThat(HomeText.Brand).isEqualTo("轻衡")
        assertThat(HomeText.UnconnectedBanner).isEqualTo("体脂秤未连接，暂无法测量。")
        assertThat(HomeText.Reconnect).isEqualTo("重新连接 ›")
        assertThat(HomeText.StaleHint).isEqualTo("上次更新 10月21日 07:30 · 非实时")
        assertThat(HomeText.WeightValue).isEqualTo("62.4")
        assertThat(HomeText.WeightUnit).isEqualTo("kg")
        assertThat(HomeText.DeviceName).isEqualTo("轻衡 S1 · 未连接")
        assertThat(HomeText.DeviceHint).isEqualTo("轻踩秤面唤醒后重连")
        assertThat(HomeText.Connect).isEqualTo("连接")
        assertThat(HomeText.MeasureDisabled).isEqualTo("开始测量（需先连接设备）")
    }

    // ---- v-syncing 文案 ----

    @Test
    fun `云端同步中文案逐字对齐原型`() {
        assertThat(HomeUiState().syncingBanner).isEqualTo("正在同步 3 条测量记录…")
        assertThat(HomeText.Brand).isEqualTo("轻衡")
        assertThat(HomeText.CurrentWeight).isEqualTo("当前体重")
        assertThat(HomeText.Syncing).isEqualTo("同步中…")
        assertThat(HomeText.WeightValue).isEqualTo("62.4")
        assertThat(HomeText.WeightUnit).isEqualTo("kg")
        assertThat(HomeText.PendingTag).isEqualTo("待同步")
        assertThat(HomeText.PendingHint).isEqualTo("本机记录，稍后上传")
        assertThat(HomeText.SyncDoneDemo).isEqualTo("（演示）点击返回，视为同步完成 →")
    }

    // ---- v-error 文案 ----

    @Test
    fun `刷新失败文案逐字对齐原型`() {
        assertThat(HomeText.Brand).isEqualTo("轻衡")
        assertThat(HomeText.ErrorTitle).isEqualTo("指标加载失败")
        assertThat(HomeText.ErrorBody).isEqualTo("网络开小差了，请检查连接。")
        assertThat(HomeText.Reload).isEqualTo("重新加载")
        assertThat(HomeText.BackHome).isEqualTo("← 返回正常首页")
    }

    // ---- v-switching 文案 ----

    @Test
    fun `成员切换中文案逐字对齐原型`() {
        assertThat(homeSwitchingBanner("妈妈")).isEqualTo("已切换到「妈妈」，正在加载她的数据…")
        assertThat(HomeText.Brand).isEqualTo("轻衡")
        assertThat(HomeText.SwitchDemo).isEqualTo("（演示）点击返回 →")
    }

    // ---- v-brief 文案 ----

    @Test
    fun `AI 简报文案逐字对齐原型`() {
        assertThat(HomeText.BriefTitle).isEqualTo("AI 健康简报")
        assertThat(HomeText.BriefCutoff).isEqualTo("分析截止：2026年10月21日 07:30 · 林悦")
        assertThat(HomeText.BriefHeadline1).isEqualTo("近 30 天趋势稳健，")
        assertThat(HomeText.BriefHeadline2).isEqualTo("继续保持你的节奏。")
        assertThat(HomeText.BriefChanges).isEqualTo("关键变化")
        assertThat(HomeText.BriefWeightDelta).isEqualTo("−1.6")
        assertThat(HomeText.BriefWeightLabel).isEqualTo("体重 kg")
        assertThat(HomeText.BriefFatDelta).isEqualTo("−1.5")
        assertThat(HomeText.BriefFatLabel).isEqualTo("体脂 %")
        assertThat(HomeText.BriefBody).isEqualTo(
            "近 30 天体重下降 1.6 kg、体脂率下降 1.5 个百分点，趋势稳健。单次波动不代表脂肪变化。",
        )
    }

    // ---- v-goaldone 文案 ----

    @Test
    fun `目标达成文案逐字对齐原型`() {
        assertThat(HomeText.GoalDoneTitle).isEqualTo("目标达成")
        assertThat(HomeText.GoalCongrats).isEqualTo("恭喜，达成体重目标！")
        assertThat(HomeText.GoalSummary).isEqualTo("从 65.2 kg 到 58.0 kg，减重 7.2 kg，用时 84 天。")
        assertThat(HomeText.GoalTargetValue).isEqualTo("58.0")
        assertThat(HomeText.GoalTargetLabel).isEqualTo("目标 kg")
        assertThat(HomeText.GoalCurrentValue).isEqualTo("57.9")
        assertThat(HomeText.GoalCurrentLabel).isEqualTo("当前 kg")
        assertThat(HomeText.GoalDaysValue).isEqualTo("84")
        assertThat(HomeText.GoalDaysLabel).isEqualTo("天数")
        assertThat(HomeText.GoalSetNew).isEqualTo("设定新目标")
    }

    @Test
    fun `目标摘要由数值派生且与原型一致`() {
        assertThat(homeGoalSummary(startKg = 65.2, goalKg = 58.0, days = 84))
            .isEqualTo(HomeText.GoalSummary)
    }

    // ---- 状态推进 ----

    @Test
    fun `进入每个状态`() {
        val home = HomeUiState()
        val cases = mapOf(
            HomeEvent.OpenEmpty to HomeStage.Empty,
            HomeEvent.OpenUnconnected to HomeStage.Unconnected,
            HomeEvent.OpenSyncing to HomeStage.Syncing,
            HomeEvent.OpenError to HomeStage.Error,
            HomeEvent.OpenBrief to HomeStage.Brief,
            HomeEvent.OpenGoalDone to HomeStage.GoalDone,
        )
        cases.forEach { (event, stage) ->
            assertThat(homeReduce(home, event).stage).isEqualTo(stage)
        }
    }

    @Test
    fun `任意状态返回都回到正常首页`() {
        HomeStage.entries.forEach { stage ->
            val reduced = homeReduce(HomeUiState(stage = stage), HomeEvent.Back)
            assertThat(reduced.stage).isEqualTo(HomeStage.Home)
        }
    }

    @Test
    fun `空态开始第一次测量后回到首页`() {
        val empty = HomeUiState(stage = HomeStage.Empty)
        assertThat(homeReduce(empty, HomeEvent.StartFirstMeasure).stage).isEqualTo(HomeStage.Home)
    }

    @Test
    fun `未连接态重连后回到首页`() {
        val unconn = HomeUiState(stage = HomeStage.Unconnected)
        assertThat(homeReduce(unconn, HomeEvent.ReconnectDevice).stage).isEqualTo(HomeStage.Home)
    }

    @Test
    fun `错误态重新加载后回到首页`() {
        val error = HomeUiState(stage = HomeStage.Error)
        assertThat(homeReduce(error, HomeEvent.ReloadMetrics).stage).isEqualTo(HomeStage.Home)
    }

    @Test
    fun `同步中返回视为同步完成并清空待同步条数`() {
        val syncing = HomeUiState(stage = HomeStage.Syncing, pendingSyncCount = 3)
        val done = homeReduce(syncing, HomeEvent.SyncCompleted)
        assertThat(done.stage).isEqualTo(HomeStage.Home)
        assertThat(done.pendingSyncCount).isEqualTo(0)
    }

    @Test
    fun `切换到其他成员进入切换态并提交成员名`() {
        val switched = homeReduce(HomeUiState(), HomeEvent.SwitchMember("妈妈"))
        assertThat(switched.stage).isEqualTo(HomeStage.Switching)
        assertThat(switched.memberName).isEqualTo("妈妈")
        assertThat(switched.switchingBanner).isEqualTo("已切换到「妈妈」，正在加载她的数据…")
        assertThat(switched.memberInitial).isEqualTo("妈")
    }

    @Test
    fun `切换回当前成员停留在首页`() {
        val state = HomeUiState(stage = HomeStage.Switching).copy(memberName = "林悦")
        val reduced = homeReduce(state, HomeEvent.SwitchMember("林悦"))
        assertThat(reduced.stage).isEqualTo(HomeStage.Home)
        assertThat(reduced.memberName).isEqualTo("林悦")
    }

    @Test
    fun `空成员名不进入切换态`() {
        assertThat(homeReduce(HomeUiState(), HomeEvent.SwitchMember("  ")).stage)
            .isEqualTo(HomeStage.Home)
    }

    @Test
    fun `目标达成两个动作都回到首页`() {
        val goalDone = HomeUiState(stage = HomeStage.GoalDone)
        assertThat(homeReduce(goalDone, HomeEvent.SetNewGoal).stage).isEqualTo(HomeStage.Home)
        assertThat(homeReduce(goalDone, HomeEvent.KeepCurrentWeight).stage).isEqualTo(HomeStage.Home)
    }

    // ---- 纯函数边界 ----

    @Test
    fun `目标进度为三十九且夹在零到一百之间`() {
        assertThat(HomeUiState().goalProgressPercent).isEqualTo(39)
        assertThat(homeGoalProgressPercent(65.2, 70.0, 58.0)).isEqualTo(0)
        assertThat(homeGoalProgressPercent(65.2, 50.0, 58.0)).isEqualTo(100)
        // 起点与目标重合：不算除零，视为已完成
        assertThat(homeGoalProgressPercent(58.0, 58.0, 58.0)).isEqualTo(100)
    }

    @Test
    fun `待同步条数为负时按零条呈现`() {
        assertThat(homeSyncingBanner(-1)).isEqualTo("正在同步 0 条测量记录…")
    }
}
