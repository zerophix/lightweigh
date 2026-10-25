package com.xcwk.scalehub.feature.guide

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File
import java.util.regex.Pattern

/**
 * T-076 引导流程六屏（呈现式组件）纯 JVM 单测。
 *
 * 覆盖验收中可自动判定的三类事实：
 * 1. 六屏文案逐字取自原型 19-onboarding-guide.html（改一个字即红）；
 * 2. 四个新增 view（v-goal / v-prepare / v-connected / v-skip）的状态推进，
 *    以及 v-intro / v-done 的既有推进不被打断；
 * 3. 源码守卫：无硬编码颜色与裸 dp（AC forbidden），且复用 Qk 设计系统。
 *
 * 视觉落位（圆角/间距是否与原图像素一致）不在此测——:app 无 compose ui-test 基建，
 * 属已声明盲区；视觉由原型对照与人工验收关闭。
 */
class GuideFlowTest {

    // ---- 1. 文案逐字（AC-J1-03 then 全部 6 条）----

    @Test
    fun `v-intro 文案逐字取自原型`() {
        assertThat(GuideText.IntroTitle).isEqualTo("完善资料")
        assertThat(GuideText.IntroCounter).isEqualTo("1/3")
        assertThat(GuideText.IntroStepLabel).isEqualTo("第 1 步 · 认识你")
        assertThat(GuideText.IntroHeadline).isEqualTo("认识一下你")
        assertThat(GuideText.IntroDesc).isEqualTo("这些信息用于计算你的身体指标参考区间。")
        assertThat(GuideText.IntroNicknameLabel).isEqualTo("昵称")
        assertThat(GuideText.IntroNicknamePlaceholder).isEqualTo("怎么称呼你？")
        assertThat(GuideText.IntroGenderLabel).isEqualTo("性别")
        assertThat(GuideText.IntroGenderHint).isEqualTo("性别影响体脂率参考区间")
        assertThat(GuideText.IntroBirthDateLabel).isEqualTo("出生日期")
        assertThat(GuideText.IntroBirthDatePlaceholder).isEqualTo("1996-05-12")
        assertThat(GuideText.IntroHeightLabel).isEqualTo("身高")
        assertThat(GuideText.IntroHeightUnit).isEqualTo("cm")
        assertThat(GenderOption.entries.map { it.label }).containsExactly("男", "女", "不告知")
    }

    @Test
    fun `v-goal 文案逐字取自原型`() {
        assertThat(GuideText.GoalTitle).isEqualTo("健康目标")
        assertThat(GuideText.GoalCounter).isEqualTo("2/3")
        assertThat(GuideText.GoalStepLabel).isEqualTo("第 2 步 · 设定健康方向")
        assertThat(GuideText.GoalHeadline).isEqualTo("你想达成什么？")
        assertThat(GuideText.GoalDesc).isEqualTo("目标可以随时调整，\n先选一个方向就好。")
        assertThat(GuideText.GoalWeightLabel).isEqualTo("目标体重")
        assertThat(GuideText.GoalWeightUnit).isEqualTo("kg")
        assertThat(GuideText.GoalWeightHint).isEqualTo("设好目标体重后，这里会显示与当前体重的差值")
        assertThat(GuideText.GoalSkip).isEqualTo("跳过，稍后设置")
        assertThat(GuideText.Next).isEqualTo("下一步")

        assertThat(GuideGoalOption.LoseWeight.title).isEqualTo("减轻体重")
        assertThat(GuideGoalOption.LoseWeight.detail).isEqualTo("设定目标体重，跟踪减重进度")
        assertThat(GuideGoalOption.MaintainWeight.title).isEqualTo("保持体重")
        assertThat(GuideGoalOption.MaintainWeight.detail).isEqualTo("维持当前体重，关注身体成分变化")
        assertThat(GuideGoalOption.BuildMuscle.title).isEqualTo("增加肌肉")
        assertThat(GuideGoalOption.BuildMuscle.detail).isEqualTo("提升肌肉量，改善身体成分比例")
    }

    @Test
    fun `v-prepare 文案逐字取自原型`() {
        assertThat(GuideText.PrepareTitle).isEqualTo("准备测量")
        assertThat(GuideText.PrepareCounter).isEqualTo("3/3")
        assertThat(GuideText.PrepareStepLabel).isEqualTo("第 3 步 · 准备第一次测量")
        assertThat(GuideText.PrepareHeadline).isEqualTo("准备好第一次测量")
        assertThat(GuideText.PrepareDesc).isEqualTo("连接体脂秤，完成第一次测量，\n就能看到你的身体概览。")
        assertThat(GuideText.PrepareRow1Title).isEqualTo("轻踩体脂秤唤醒")
        assertThat(GuideText.PrepareRow1Detail).isEqualTo("屏幕点亮即可连接")
        assertThat(GuideText.PrepareRow2Title).isEqualTo("打开蓝牙，靠近秤")
        assertThat(GuideText.PrepareRow2Detail).isEqualTo("手机距秤 1 米以内")
        assertThat(GuideText.PrepareRow3Title).isEqualTo("赤脚站上秤面")
        assertThat(GuideText.PrepareRow3Detail).isEqualTo("双脚踩住电极，保持静止")
        assertThat(GuideText.PrepareBanner).isEqualTo("测量前请正常饮水，避免剧烈运动后立即测量。")
        assertThat(GuideText.PrepareConnect).isEqualTo("连接体脂秤，开始测量")
        assertThat(GuideText.PrepareSkip).isEqualTo("跳过，稍后连接")
    }

    @Test
    fun `v-connected 文案逐字取自原型`() {
        assertThat(GuideText.ConnectedTitle).isEqualTo("连接设备")
        assertThat(GuideText.ConnectedHeadline).isEqualTo("正在搜索设备…")
        assertThat(GuideText.ConnectedDesc).isEqualTo("请轻踩体脂秤唤醒，保持手机靠近")
        assertThat(GuideText.ConnectedSkip).isEqualTo("跳过")
        assertThat(GuideText.ConnectedSimulate).isEqualTo("模拟连接成功")
    }

    @Test
    fun `v-done 文案逐字取自原型`() {
        assertThat(GuideText.DoneHeadline).isEqualTo("一切就绪")
        assertThat(GuideText.DoneDesc).isEqualTo("资料已完善，体脂秤已连接。\n现在就可以开始第一次测量了。")
        assertThat(GuideText.DoneStart).isEqualTo("开始第一次测量")
        assertThat(GuideText.DoneRestart).isEqualTo("重新走一遍引导")
    }

    @Test
    fun `v-skip 文案逐字取自原型`() {
        assertThat(GuideText.SkipHeadline).isEqualTo("跳过连接")
        assertThat(GuideText.SkipDesc).isEqualTo("你可以随时在「设备管理」中连接体脂秤。\n引导已完成。")
        assertThat(GuideText.SkipFinish).isEqualTo("完成引导")
        assertThat(GuideText.PrototypeFooter)
            .isEqualTo("轻衡 · 首次引导交互原型 · 5 屏（补 Figma A11–A13 引导三部曲缺口）")
    }

    // ---- 2. 状态推进（六屏） ----

    @Test
    fun `主按钮沿 intro goal prepare connected done 前进`() {
        var state = GuideUiState()
        assertThat(state.step).isEqualTo(GuideStep.Intro)

        state = guideReduce(state, GuideIntent.Next)
        assertThat(state.step).isEqualTo(GuideStep.Goal)

        state = guideReduce(state, GuideIntent.Next)
        assertThat(state.step).isEqualTo(GuideStep.Prepare)

        // v-prepare 的主按钮是「连接体脂秤，开始测量」——语义与 Next 等价的专门意图
        state = guideReduce(state, GuideIntent.StartConnecting)
        assertThat(state.step).isEqualTo(GuideStep.Connected)

        state = guideReduce(state, GuideIntent.SimulatedConnected)
        assertThat(state.step).isEqualTo(GuideStep.Done)

        // 终态不再前进
        assertThat(guideReduce(state, GuideIntent.Next).step).isEqualTo(GuideStep.Done)
    }

    @Test
    fun `v-goal 跳过稍后设置直接进准备页`() {
        val goal = GuideUiState(step = GuideStep.Goal)
        assertThat(guideReduce(goal, GuideIntent.SkipGoal).step).isEqualTo(GuideStep.Prepare)
    }

    @Test
    fun `v-prepare 与 v-connected 跳过都进 v-skip 且是终态`() {
        val prepare = guideReduce(GuideUiState(step = GuideStep.Prepare), GuideIntent.SkipConnection)
        assertThat(prepare.step).isEqualTo(GuideStep.Skipped)

        val connected = guideReduce(GuideUiState(step = GuideStep.Connected), GuideIntent.SkipConnection)
        assertThat(connected.step).isEqualTo(GuideStep.Skipped)

        // 跳过页不再前进，Finish 不改状态（出栈由宿主回调决定）
        assertThat(guideReduce(prepare, GuideIntent.Next).step).isEqualTo(GuideStep.Skipped)
        assertThat(guideReduce(prepare, GuideIntent.Finish).step).isEqualTo(GuideStep.Skipped)
    }

    @Test
    fun `返回键逐步回退且首页与终态不回退`() {
        assertThat(guideReduce(GuideUiState(step = GuideStep.Goal), GuideIntent.Back).step)
            .isEqualTo(GuideStep.Intro)
        assertThat(guideReduce(GuideUiState(step = GuideStep.Prepare), GuideIntent.Back).step)
            .isEqualTo(GuideStep.Goal)
        assertThat(guideReduce(GuideUiState(step = GuideStep.Connected), GuideIntent.Back).step)
            .isEqualTo(GuideStep.Prepare)
        assertThat(guideReduce(GuideUiState(step = GuideStep.Intro), GuideIntent.Back).step)
            .isEqualTo(GuideStep.Intro)
        assertThat(guideReduce(GuideUiState(step = GuideStep.Done), GuideIntent.Back).step)
            .isEqualTo(GuideStep.Done)
        assertThat(guideReduce(GuideUiState(step = GuideStep.Skipped), GuideIntent.Back).step)
            .isEqualTo(GuideStep.Skipped)
    }

    @Test
    fun `跨屏意图不生效`() {
        // 未到 goal 就点跳过设置 / 未到 prepare 就连接 / 未到 connected 就模拟成功
        assertThat(guideReduce(GuideUiState(step = GuideStep.Intro), GuideIntent.SkipGoal).step)
            .isEqualTo(GuideStep.Intro)
        assertThat(guideReduce(GuideUiState(step = GuideStep.Goal), GuideIntent.StartConnecting).step)
            .isEqualTo(GuideStep.Goal)
        assertThat(guideReduce(GuideUiState(step = GuideStep.Prepare), GuideIntent.SimulatedConnected).step)
            .isEqualTo(GuideStep.Prepare)
        assertThat(guideReduce(GuideUiState(step = GuideStep.Intro), GuideIntent.SkipConnection).step)
            .isEqualTo(GuideStep.Intro)
    }

    @Test
    fun `输入与选项保持并随重启清空`() {
        var state = GuideUiState()
        state = guideReduce(state, GuideIntent.NicknameChanged("林悦"))
        state = guideReduce(state, GuideIntent.GenderSelected(GenderOption.Undisclosed))
        state = guideReduce(state, GuideIntent.BirthDateChanged("1996-05-12"))
        state = guideReduce(state, GuideIntent.HeightChanged("168"))
        state = guideReduce(state, GuideIntent.GoalSelected(GuideGoalOption.BuildMuscle))
        state = guideReduce(state, GuideIntent.TargetWeightChanged("58.0"))
        state = guideReduce(state, GuideIntent.Next)

        assertThat(state.step).isEqualTo(GuideStep.Goal)
        assertThat(state.nickname).isEqualTo("林悦")
        assertThat(state.gender).isEqualTo(GenderOption.Undisclosed)
        assertThat(state.birthDate).isEqualTo("1996-05-12")
        assertThat(state.heightText).isEqualTo("168")
        assertThat(state.goal).isEqualTo(GuideGoalOption.BuildMuscle)
        assertThat(state.targetWeightText).isEqualTo("58.0")

        val restarted = guideReduce(state, GuideIntent.Restart)
        assertThat(restarted).isEqualTo(GuideUiState())
        assertThat(restarted.step).isEqualTo(GuideStep.Intro)
    }

    @Test
    fun `默认目标与原型选中项一致`() {
        assertThat(GuideUiState().goal).isEqualTo(GuideGoalOption.LoseWeight)
        assertThat(GuideUiState().step).isEqualTo(GuideStep.Intro)
    }

    // ---- 3. 源码守卫（AC forbidden：硬编码颜色 / 裸 dp；且复用 Qk） ----

    private val targetFile = "GuideScreen.kt"

    private fun sourceOf(fileName: String): String {
        var dir: File? = File(System.getProperty("user.dir"))
        while (dir != null) {
            val f = File(dir, "app/src/main/kotlin/com/xcwk/scalehub/feature/guide/$fileName")
            if (f.exists()) return f.readText(Charsets.UTF_8)
            dir = dir.parentFile
        }
        error("$fileName 未找到（从 ${System.getProperty("user.dir")} 向上探测失败）")
    }

    /** 去掉注释后再断言：文档里允许出现约束原文，代码本身不许出现违规符号。 */
    private fun codeOnly(source: String): String =
        source
            .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("""(?m)//.*$"""), "")

    @Test
    fun `目标文件无硬编码颜色与裸 dp`() {
        val code = codeOnly(sourceOf(targetFile))
        assertThat(code).doesNotMatch(Pattern.compile("""Color\(0x[0-9A-Fa-f]{6,8}\)"""))
        // 与 gov-prototype-verify 的 HARDCODED_DP 同口径：Qk token 之外不许写 NN.dp
        assertThat(code).doesNotMatch(Pattern.compile("""(?<!Qk)\b\d+\.dp\b"""))
    }

    @Test
    fun `目标文件复用 Qk 设计系统且不越界碰蓝牙`() {
        val source = sourceOf(targetFile)
        assertThat(source).contains("Qk")
        val code = codeOnly(source)
        assertThat(code).doesNotContain("BluetoothAdapter")
        assertThat(code).doesNotContain("android.bluetooth")
        assertThat(code).doesNotContain("BluetoothLeScanner")
    }

    @Test
    fun `目标文件覆盖六屏文案常量`() {
        val source = sourceOf(targetFile)
        listOf(
            GuideText.IntroHeadline,
            GuideText.GoalHeadline,
            GuideText.PrepareHeadline,
            GuideText.ConnectedHeadline,
            GuideText.DoneHeadline,
            GuideText.SkipHeadline,
        ).forEach { headline ->
            assertThat(source).contains(headline)
        }
    }
}
