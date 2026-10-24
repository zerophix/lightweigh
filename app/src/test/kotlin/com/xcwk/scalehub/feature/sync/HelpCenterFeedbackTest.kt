package com.xcwk.scalehub.feature.sync

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File
import java.util.regex.Pattern

/**
 * T-093 帮助中心与反馈流程的纯 JVM 单测。
 *
 * 覆盖四个可在 JVM 判定的事实面：
 * 1. 七个 view 的文案逐字取自原型08（改一个字即红）；
 * 2. 状态机推进表：FAQ 展开/折叠、表单校验、提交中→成功/失败→重试、草稿保留；
 * 3. 诊断授权判定：未勾选任何项不得上传，健康数据仅在单独勾选时包含；
 * 4. 源码守卫：呈现层不碰系统能力、无硬编码颜色与裸 dp。
 *
 * 视觉呈现不在本测范围（:app 无 compose ui-test 基建，属已声明盲区）。
 */
class HelpCenterFeedbackTest {

    // ---- 1. 文案逐字（AC-N3 then 第 1–7 条） ----

    @Test
    fun `帮助中心文案逐字取自原型 v-help`() {
        assertThat(HelpCenterText.TopBarTitle).isEqualTo("帮助与反馈")
        assertThat(HelpCenterText.FaqSectionTitle).isEqualTo("常见问题")
        assertThat(HelpCenterText.DiagnosticsEntryTitle).isEqualTo("上传诊断日志")
        assertThat(HelpCenterText.DiagnosticsEntryDesc).isEqualTo("帮助排查问题")
        assertThat(HelpCenterText.FeedbackEntry).isEqualTo("意见反馈")
        assertThat(HelpCenterText.SearchHint).isEqualTo("搜索问题，如「测量不准」")
        assertThat(HELP_FAQ_ITEMS.map { it.question }).containsExactly(
            "为什么测量结果有波动？",
            "设备连接失败怎么办？",
            "数据没有同步",
        ).inOrder()
    }

    @Test
    fun `FAQ 详情文案逐字取自原型 v-faq`() {
        assertThat(HelpCenterText.FaqTopBarTitle).isEqualTo("测量结果有波动")
        assertThat(WEIGHT_FLUCTUATION_FAQ.question).isEqualTo("为什么同一天测量结果会有波动？")
        assertThat(WEIGHT_FLUCTUATION_FAQ.body)
            .isEqualTo("体重一天内自然波动 1–2 kg 是正常的，来自水分、进食和排泄。")
        assertThat(WEIGHT_FLUCTUATION_FAQ.tipsTitle).isEqualTo("如何减少波动：")
        assertThat(WEIGHT_FLUCTUATION_FAQ.tips).containsExactly(
            "· 每天同一时段测量",
            "· 测量前避免大量饮水或进食",
            "· 保持测量条件一致",
        ).inOrder()
        assertThat(WEIGHT_FLUCTUATION_FAQ.summary)
            .isEqualTo("建议关注 7 天或 30 天平均趋势，而不是单次数值。")
        assertThat(WEIGHT_FLUCTUATION_FAQ.banner).isEqualTo("体脂秤为估算设备，数据仅供参考。")
        assertThat(FaqVerdict.Unsolved.label).isEqualTo("没解决")
        assertThat(FaqVerdict.Solved.label).isEqualTo("解决了")
    }

    @Test
    fun `反馈填写页文案逐字取自原型 v-feedback`() {
        assertThat(FeedbackText.FormTopBarTitle).isEqualTo("意见反馈")
        assertThat(FeedbackText.TypeLabel).isEqualTo("问题类型")
        assertThat(FeedbackType.entries.map { it.label }).containsExactly("测量", "连接", "其他").inOrder()
        assertThat(FeedbackText.DescriptionLabel).isEqualTo("描述")
        assertThat(FeedbackText.DescriptionHint).isEqualTo("描述你遇到的问题或建议…")
        assertThat(FeedbackText.PrivacyBanner).isEqualTo("默认不收集健康数据，诊断日志需单独授权。")
        assertThat(FeedbackText.SimulateFailure).isEqualTo("模拟失败")
        assertThat(FeedbackText.Submit).isEqualTo("提交")
    }

    @Test
    fun `提交中与结果页文案逐字取自原型`() {
        assertThat(FeedbackText.SubmittingTitle).isEqualTo("正在提交反馈…")
        assertThat(FeedbackText.SubmittingHint).isEqualTo("请勿关闭页面")
        assertThat(FeedbackText.SimulateDone).isEqualTo("模拟完成 → 成功")

        assertThat(FeedbackText.SuccessTitle).isEqualTo("反馈已提交")
        assertThat(FeedbackText.SuccessTicketLabel).isEqualTo("工单号")
        assertThat(FeedbackText.SuccessHint).isEqualTo("1–2 个工作日内回复。")
        assertThat(FeedbackText.BackToHelp).isEqualTo("返回帮助中心")
        assertThat(FeedbackText.ContinueFeedback).isEqualTo("继续反馈")
        assertThat(PROTOTYPE_TICKET_NO).isEqualTo("#FB-20261022")

        assertThat(FeedbackText.FailTitle).isEqualTo("反馈提交失败")
        assertThat(FeedbackText.FailBody).isEqualTo("网络开小差了，反馈未发送。")
        assertThat(FeedbackText.FailKept).isEqualTo("填写内容已保留。")
        assertThat(FeedbackText.Retry).isEqualTo("重新提交")
        assertThat(FeedbackText.BackToEdit).isEqualTo("返回修改")
    }

    @Test
    fun `诊断授权页文案逐字取自原型 v-diag`() {
        assertThat(FeedbackText.DiagTopBarTitle).isEqualTo("诊断信息")
        assertThat(FeedbackText.DiagTitle).isEqualTo("上传诊断日志？")
        assertThat(FeedbackText.DiagHint).isEqualTo("用于更快定位问题。")
        assertThat(FeedbackText.DiagAppLog).isEqualTo("应用运行日志")
        assertThat(FeedbackText.DiagAppLogDesc).isEqualTo("不含健康数据与个人信息")
        assertThat(FeedbackText.DiagBluetoothLog).isEqualTo("蓝牙连接日志")
        assertThat(FeedbackText.DiagMeasurements).isEqualTo("测量数值")
        assertThat(FeedbackText.DiagMeasurementsDesc).isEqualTo("可关闭")
        assertThat(FeedbackText.DiagNote).isEqualTo("日志 30 天后自动删除，可随时撤回。")
        assertThat(FeedbackText.DiagConsent).isEqualTo("同意并上传")
    }

    // ---- 2. FAQ 展开 / 折叠 ----

    @Test
    fun `打开某条 FAQ 进入详情并记录展开项`() {
        val state = feedbackReduce(
            HelpCenterUiState(),
            HelpCenterIntent.OpenFaq("device-connection"),
        )
        assertThat(state.step).isEqualTo(HelpCenterStep.Faq)
        assertThat(state.openFaqId).isEqualTo("device-connection")
    }

    @Test
    fun `折叠 FAQ 详情回到列表并清空展开项`() {
        val opened = feedbackReduce(HelpCenterUiState(), HelpCenterIntent.OpenFaq("weight-fluctuation"))
        val closed = feedbackReduce(opened, HelpCenterIntent.CloseFaq)
        assertThat(closed.step).isEqualTo(HelpCenterStep.Help)
        assertThat(closed.openFaqId).isNull()
    }

    @Test
    fun `FAQ 评价被记录并返回列表`() {
        val opened = feedbackReduce(HelpCenterUiState(), HelpCenterIntent.OpenFaq("weight-fluctuation"))
        val judged = feedbackReduce(
            opened,
            HelpCenterIntent.JudgeFaq("weight-fluctuation", FaqVerdict.Solved),
        )
        assertThat(judged.step).isEqualTo(HelpCenterStep.Help)
        assertThat(judged.openFaqId).isNull()
        assertThat(judged.faqVerdicts["weight-fluctuation"]).isEqualTo(FaqVerdict.Solved)
    }

    // ---- 3. 表单校验（空内容 / 超长） ----

    @Test
    fun `空描述不可提交`() {
        assertThat(FeedbackDraft(description = "").validation)
            .isEqualTo(FeedbackValidation.EmptyDescription)
        assertThat(FeedbackDraft(description = "   ").validation)
            .isEqualTo(FeedbackValidation.EmptyDescription)
        assertThat(FeedbackDraft(description = "").canSubmit).isFalse()
    }

    @Test
    fun `超长描述不可提交且边界值可提交`() {
        val tooLong = FeedbackDraft(description = "测".repeat(FEEDBACK_DESCRIPTION_MAX + 1))
        assertThat(tooLong.validation).isEqualTo(FeedbackValidation.TooLong)
        assertThat(tooLong.canSubmit).isFalse()

        val atLimit = FeedbackDraft(description = "测".repeat(FEEDBACK_DESCRIPTION_MAX))
        assertThat(atLimit.validation).isEqualTo(FeedbackValidation.Ready)
        assertThat(atLimit.canSubmit).isTrue()
    }

    @Test
    fun `未选类型不可提交`() {
        assertThat(FeedbackDraft(type = null, description = "连接总是失败").canSubmit).isFalse()
    }

    @Test
    fun `草稿不合法时提交事件不推进`() {
        val state = HelpCenterUiState(step = HelpCenterStep.Feedback, draft = FeedbackDraft(description = ""))
        assertThat(feedbackReduce(state, HelpCenterIntent.SubmitFeedback).step)
            .isEqualTo(HelpCenterStep.Feedback)
    }

    // ---- 4. 提交中 → 成功 / 失败 → 重试 ----

    @Test
    fun `合法草稿提交进入提交中`() {
        val state = HelpCenterUiState(
            step = HelpCenterStep.Feedback,
            draft = FeedbackDraft(FeedbackType.Connect, "连接总是失败"),
        )
        assertThat(feedbackReduce(state, HelpCenterIntent.SubmitFeedback).step)
            .isEqualTo(HelpCenterStep.Submitting)
    }

    @Test
    fun `提交成功带上注入的工单号`() {
        val state = HelpCenterUiState(
            step = HelpCenterStep.Submitting,
            draft = FeedbackDraft(FeedbackType.Measure, "测量不准"),
        )
        val success = feedbackReduce(state, HelpCenterIntent.SubmitSucceeded("#FB-20261022"))
        assertThat(success.step).isEqualTo(HelpCenterStep.Success)
        assertThat(success.ticketNo).isEqualTo("#FB-20261022")
    }

    @Test
    fun `乱序的成功回包不会越过提交中`() {
        val state = HelpCenterUiState(
            step = HelpCenterStep.Feedback,
            draft = FeedbackDraft(FeedbackType.Measure, "测量不准"),
        )
        val after = feedbackReduce(state, HelpCenterIntent.SubmitSucceeded("#FB-20261022"))
        assertThat(after.step).isEqualTo(HelpCenterStep.Feedback)
        assertThat(after.ticketNo).isEmpty()
    }

    @Test
    fun `提交失败保留已填内容并可重试`() {
        val draft = FeedbackDraft(FeedbackType.Other, "希望支持周报导出")
        val submitting = HelpCenterUiState(step = HelpCenterStep.Submitting, draft = draft)
        val failed = feedbackReduce(submitting, HelpCenterIntent.SubmitFailed)
        assertThat(failed.step).isEqualTo(HelpCenterStep.Failed)
        assertThat(failed.draft).isEqualTo(draft)
        assertThat(failed.draftRetainedOnFailure).isTrue()

        val retried = feedbackReduce(failed, HelpCenterIntent.RetrySubmit)
        assertThat(retried.step).isEqualTo(HelpCenterStep.Submitting)
        assertThat(retried.draft).isEqualTo(draft)
    }

    @Test
    fun `失败页返回修改后草稿仍在`() {
        val draft = FeedbackDraft(FeedbackType.Measure, "测量不准")
        val failed = HelpCenterUiState(step = HelpCenterStep.Failed, draft = draft)
        val editing = feedbackReduce(failed, HelpCenterIntent.BackToEdit)
        assertThat(editing.step).isEqualTo(HelpCenterStep.Feedback)
        assertThat(editing.draft).isEqualTo(draft)
        assertThat(editing.draftRetainedOnFailure).isFalse()
    }

    @Test
    fun `成功页继续反馈清空草稿`() {
        val success = HelpCenterUiState(
            step = HelpCenterStep.Success,
            draft = FeedbackDraft(FeedbackType.Measure, "测量不准"),
            ticketNo = "#FB-20261022",
        )
        val next = feedbackReduce(success, HelpCenterIntent.ContinueFeedback)
        assertThat(next.step).isEqualTo(HelpCenterStep.Feedback)
        assertThat(next.draft.description).isEmpty()
        assertThat(next.ticketNo).isEmpty()
    }

    @Test
    fun `模拟失败回调后草稿保留`() {
        val state = HelpCenterUiState(
            step = HelpCenterStep.Feedback,
            draft = FeedbackDraft(FeedbackType.Measure, "测量不准"),
        )
        val failed = feedbackReduce(state, HelpCenterIntent.SimulateFailure)
        assertThat(failed.step).isEqualTo(HelpCenterStep.Failed)
        assertThat(failed.draft.description).isEqualTo("测量不准")
    }

    // ---- 5. 诊断授权 ----

    @Test
    fun `诊断项默认与原型一致`() {
        val consent = DiagnosticConsent()
        assertThat(consent.appLog).isTrue()
        assertThat(consent.bluetoothLog).isTrue()
        assertThat(consent.measurements).isFalse()
        assertThat(consent.includesHealthData).isFalse()
    }

    @Test
    fun `未勾选任何诊断项时不上传`() {
        val none = DiagnosticConsent(appLog = false, bluetoothLog = false, measurements = false)
        assertThat(none.canUpload).isFalse()
        val state = HelpCenterUiState(step = HelpCenterStep.Diagnostics, diagnostics = none)
        assertThat(feedbackReduce(state, HelpCenterIntent.ConsentAndUpload).step)
            .isEqualTo(HelpCenterStep.Diagnostics)
    }

    @Test
    fun `勾选测量数值才算包含健康数据`() {
        val consent = feedbackReduce(
            HelpCenterUiState(step = HelpCenterStep.Diagnostics),
            HelpCenterIntent.ToggleDiagnosticItem(DiagnosticItemKind.Measurements),
        ).diagnostics
        assertThat(consent.measurements).isTrue()
        assertThat(consent.includesHealthData).isTrue()
        assertThat(consent.canUpload).isTrue()
    }

    @Test
    fun `勾选后同意上传回到帮助中心`() {
        val state = HelpCenterUiState(step = HelpCenterStep.Diagnostics)
        assertThat(feedbackReduce(state, HelpCenterIntent.ConsentAndUpload).step)
            .isEqualTo(HelpCenterStep.Help)
    }

    // ---- 6. 源码守卫（AC forbidden：系统能力直连 / 硬编码颜色间距） ----

    private fun sourceOf(fileName: String): String {
        var dir: File? = File(System.getProperty("user.dir").orEmpty())
        while (dir != null) {
            val f = File(dir, "app/src/main/kotlin/com/xcwk/scalehub/feature/sync/$fileName")
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

    private val targets = listOf("HelpCenter.kt", "FeedbackFlow.kt", "HelpCenterFlow.kt", "HelpCenterRoute.kt")

    @Test
    fun `目标文件不接触系统日志网络蓝牙与数据库`() {
        targets.forEach { fileName ->
            val code = codeOnly(sourceOf(fileName))
            listOf(
                "android.util.Log",
                "android.net",
                "android.bluetooth",
                "ConnectivityManager",
                "BluetoothAdapter",
                "RoomDatabase",
                "okhttp3",
            ).forEach { symbol -> assertThat(code).doesNotContain(symbol) }
        }
    }

    @Test
    fun `目标文件无硬编码颜色与裸 dp`() {
        targets.forEach { fileName ->
            val code = codeOnly(sourceOf(fileName))
            assertThat(code).doesNotMatch(Pattern.compile("""Color\(0x[0-9A-Fa-f]{6,8}\)"""))
            assertThat(code).doesNotMatch(Pattern.compile("""(?<!Qk)\b\d+\.dp\b"""))
        }
    }

    @Test
    fun `目标文件复用 Qk 设计系统组件`() {
        // 只有视图文件渲染 UI；Flow/Route 是纯路由，不含任何绘制代码。
        listOf("HelpCenter.kt", "FeedbackFlow.kt").forEach { fileName ->
            assertThat(sourceOf(fileName)).contains("Qk")
        }
    }
}
