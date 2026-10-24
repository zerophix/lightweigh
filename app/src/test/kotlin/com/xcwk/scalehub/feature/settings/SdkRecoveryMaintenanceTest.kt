package com.xcwk.scalehub.feature.settings

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.util.regex.Pattern

/**
 * T-092（settings: SDK 清单与数据恢复/维护模式）纯 JVM 单测。
 *
 * 覆盖验收中可自动判定的四类事实：
 * 1. SDK 清单渲染数据完整性：条目顺序、名称、`用途：`/`收集：` 行逐字对齐原型 26；
 * 2. 恢复流程状态推进：确认 → 进行中 → 完成/失败，非法迁移原地不动；
 * 3. 恢复范围三选一互斥、指定日期范围合法性（纯函数）；
 * 4. 维护模式开关幂等与受影响功能禁用判定（纯函数）；
 * 5. 源码守卫：三个目标文件无硬编码颜色/裸 dp、无网络/包管理器/DB 直连。
 *
 * 视觉呈现不在此测（:app 无 compose ui-test 基建），属已声明盲区。
 */
class SdkRecoveryMaintenanceTest {

    // ---- 1. SDK 清单：文案与数据完整性（原型 26 v-sdk-list）----

    @Test
    fun `SDK 清单条目名称与顺序与原型一致`() {
        assertThat(defaultSdkEntries().map { it.name })
            .containsExactly("微信 OpenSDK", "极光推送 JPush", "阿里云 OSS", "Glide")
            .inOrder()
    }

    @Test
    fun `SDK 条目用途与收集文本逐字取自原型`() {
        val entries = defaultSdkEntries().associateBy { it.name }
        assertThat(entries.getValue("微信 OpenSDK").purposeLine).isEqualTo("用途：微信登录与分享")
        assertThat(entries.getValue("微信 OpenSDK").collectedLine).isEqualTo("收集：openid、昵称、头像")
        assertThat(entries.getValue("极光推送 JPush").purposeLine).isEqualTo("用途：测量提醒通知")
        assertThat(entries.getValue("极光推送 JPush").collectedLine).isEqualTo("收集：设备标识（可关闭）")
        assertThat(entries.getValue("阿里云 OSS").purposeLine).isEqualTo("用途：健康数据加密云存储")
        assertThat(entries.getValue("阿里云 OSS").collectedLine).isEqualTo("收集：加密后的测量数据")
        assertThat(entries.getValue("Glide").purposeLine).isEqualTo("用途：图片加载")
        assertThat(entries.getValue("Glide").collectedLine).isEqualTo("收集：无个人数据")
    }

    @Test
    fun `SDK 页文案逐字取自原型 v-sdk-list`() {
        assertThat(SdkListText.TopBarTitle).isEqualTo("第三方 SDK")
        assertThat(SdkListText.BannerLine1).isEqualTo("以下第三方 SDK 用于实现对应功能，")
        assertThat(SdkListText.BannerLine2).isEqualTo("我们要求其遵守本隐私政策。")
        assertThat(SdkListText.FooterNote)
            .isEqualTo("点击可查看各 SDK 的隐私政策链接与数据处理说明。")
        assertThat(SdkListText.BackAction).isEqualTo("返回")
    }

    @Test
    fun `只有极光推送行可关闭开关`() {
        val toggleable = defaultSdkEntries().filter { it.consentToggleable }
        assertThat(toggleable.map { it.name }).containsExactly("极光推送 JPush")
    }

    @Test
    fun `清单数据完全由外部注入`() {
        val injected = listOf(
            SdkEntry(id = "custom", name = "自建 SDK", purpose = "测试用途", collected = "无"),
        )
        val state = SdkListUiState(entries = injected)
        assertThat(state.entries).containsExactlyElementsIn(injected)
        assertThat(SdkListUiState(entries = emptyList()).entries).isEmpty()
    }

    @Test
    fun `SDK 同意开关纯函数幂等且跳过不可关闭条目`() {
        val state = SdkListUiState()
        // 已开启再开启：原样返回
        assertThat(state.withSdkConsent("jpush", true)).isSameInstanceAs(state)
        val closed = state.withSdkConsent("jpush", false)
        assertThat(closed.entries.first { it.id == "jpush" }.consentGranted).isFalse()
        // 已关闭再关闭：原样返回
        assertThat(closed.withSdkConsent("jpush", false)).isSameInstanceAs(closed)
        // 不可关闭 / 未知 id：原样返回
        assertThat(state.withSdkConsent("wechat-opensdk", false)).isSameInstanceAs(state)
        assertThat(state.withSdkConsent("nope", false)).isSameInstanceAs(state)
    }

    // ---- 2. 恢复范围与日期范围合法性（纯函数）----

    @Test
    fun `恢复页文案逐字取自原型 v-recovery`() {
        assertThat(DataRecoveryText.TopBarTitle).isEqualTo("数据恢复")
        assertThat(DataRecoveryText.Subtitle).isEqualTo("从云端重新拉取历史数据")
        assertThat(DataRecoveryText.CloudAvailable).isEqualTo("云端数据可用")
        assertThat(DataRecoveryText.OptionsLabel).isEqualTo("恢复选项")
        assertThat(RecoveryScopeOption.All.title).isEqualTo("恢复全部数据")
        assertThat(RecoveryScopeOption.All.subtitle).isEqualTo("从云端拉取所有历史记录")
        assertThat(RecoveryScopeOption.Recent30Days.title).isEqualTo("恢复最近 30 天")
        assertThat(RecoveryScopeOption.Recent30Days.subtitle).isEqualTo("仅拉取近 30 天记录")
        assertThat(RecoveryScopeOption.CustomRange.title).isEqualTo("恢复指定日期范围")
        assertThat(RecoveryScopeOption.CustomRange.subtitle).isEqualTo("自定义起止日期")
        assertThat(DataRecoveryText.WarnOverwrite).isEqualTo("覆盖本地现有数据")
        assertThat(DataRecoveryText.StartAction).isEqualTo("开始恢复")
        assertThat(DataRecoveryText.CancelAction).isEqualTo("取消")
        assertThat(DataRecoveryText.CompletedTitle).isEqualTo("数据恢复完成")
    }

    @Test
    fun `恢复范围三选一互斥`() {
        val state = RecoveryUiState()
        assertThat(state.selectedScope).isEqualTo(RecoveryScopeOption.All)
        val recent = state.reduce(RecoveryIntent.SelectScope(RecoveryScopeOption.Recent30Days))
        assertThat(recent.selectedScope).isEqualTo(RecoveryScopeOption.Recent30Days)
        val custom = recent.reduce(RecoveryIntent.SelectScope(RecoveryScopeOption.CustomRange))
        assertThat(custom.selectedScope).isEqualTo(RecoveryScopeOption.CustomRange)
        // 单选：任一时刻只有一个选项被选中
        assertThat(RecoveryScopeOption.entries.count { it == custom.selectedScope }).isEqualTo(1)
    }

    @Test
    fun `日期范围校验：空值与格式`() {
        assertThat(validateRecoveryRange("", "2026-10-21", null))
            .isEqualTo(RecoveryRangeError.Empty)
        assertThat(validateRecoveryRange("2026-10-01", " ", null))
            .isEqualTo(RecoveryRangeError.Empty)
        assertThat(validateRecoveryRange("2026-13-01", "2026-10-21", null))
            .isEqualTo(RecoveryRangeError.InvalidFormat)
        assertThat(validateRecoveryRange("2026-10-01", "21/10/2026", null))
            .isEqualTo(RecoveryRangeError.InvalidFormat)
    }

    @Test
    fun `日期范围校验：起不得晚于止`() {
        assertThat(validateRecoveryRange("2026-10-22", "2026-10-21", null))
            .isEqualTo(RecoveryRangeError.StartAfterEnd)
        // 同日合法
        assertThat(validateRecoveryRange("2026-10-21", "2026-10-21", null)).isNull()
    }

    @Test
    fun `日期范围校验：不得早于最早同步日`() {
        val earliest = LocalDate.of(2026, 1, 1)
        assertThat(validateRecoveryRange("2025-12-31", "2026-10-21", earliest))
            .isEqualTo(RecoveryRangeError.BeforeEarliestSync)
        assertThat(validateRecoveryRange("2026-01-01", "2026-10-21", earliest)).isNull()
        // 云端未返回最早同步日时只校验先后
        assertThat(validateRecoveryRange("2020-01-01", "2026-10-21", null)).isNull()
    }

    @Test
    fun `自定义范围非法时不能开始恢复`() {
        val state = RecoveryUiState(
            selectedScope = RecoveryScopeOption.CustomRange,
            customStartText = "2026-10-22",
            customEndText = "2026-10-21",
        )
        assertThat(state.rangeError).isEqualTo(RecoveryRangeError.StartAfterEnd)
        assertThat(state.canStart).isFalse()
        assertThat(state.reduce(RecoveryIntent.Start).phase).isEqualTo(RecoveryPhase.Selecting)
        // 非自定义范围不受空日期影响
        assertThat(RecoveryUiState().rangeError).isNull()
        assertThat(RecoveryUiState().canStart).isTrue()
    }

    // ---- 3. 恢复流程状态推进：确认 → 进行中 → 完成 / 失败 ----

    @Test
    fun `确认阶段点开始进入进行中`() {
        val running = RecoveryUiState().reduce(RecoveryIntent.Start)
        assertThat(running.phase).isEqualTo(RecoveryPhase.Running)
        assertThat(running.progressPercent).isEqualTo(0)
    }

    @Test
    fun `进行中重复开始不产生新副本`() {
        val running = RecoveryUiState().reduce(RecoveryIntent.Start)
        assertThat(running.reduce(RecoveryIntent.Start)).isSameInstanceAs(running)
    }

    @Test
    fun `进行中不可改范围`() {
        val running = RecoveryUiState().reduce(RecoveryIntent.Start)
        val after = running.reduce(RecoveryIntent.SelectScope(RecoveryScopeOption.Recent30Days))
        assertThat(after).isSameInstanceAs(running)
        assertThat(running.reduce(RecoveryIntent.ChangeCustomRange("2026-10-01", "2026-10-21")))
            .isSameInstanceAs(running)
    }

    @Test
    fun `进度只在进行中生效并夹取到 0 到 100`() {
        val running = RecoveryUiState().reduce(RecoveryIntent.Start)
        assertThat(running.reduce(RecoveryIntent.Progress(42)).progressPercent).isEqualTo(42)
        assertThat(running.reduce(RecoveryIntent.Progress(180)).progressPercent).isEqualTo(100)
        assertThat(running.reduce(RecoveryIntent.Progress(-3)).progressPercent).isEqualTo(0)
        // 确认阶段忽略进度
        val selecting = RecoveryUiState()
        assertThat(selecting.reduce(RecoveryIntent.Progress(50))).isSameInstanceAs(selecting)
    }

    @Test
    fun `进行中完成后进入完成态并带恢复条数`() {
        val completed = RecoveryUiState()
            .reduce(RecoveryIntent.Start)
            .reduce(RecoveryIntent.Progress(60))
            .reduce(RecoveryIntent.Complete(128))
        assertThat(completed.phase).isEqualTo(RecoveryPhase.Completed)
        assertThat(completed.progressPercent).isEqualTo(100)
        assertThat(completed.restoredCount).isEqualTo(128)
        assertThat(DataRecoveryText.CompletedCountTemplate.format(completed.restoredCount))
            .isEqualTo("已从云端恢复 128 条记录。本地数据已更新。")
        // 完成态不可再完成
        assertThat(completed.reduce(RecoveryIntent.Complete(1))).isSameInstanceAs(completed)
    }

    @Test
    fun `进行中失败后进入失败态并保留原因`() {
        val failed = RecoveryUiState()
            .reduce(RecoveryIntent.Start)
            .reduce(RecoveryIntent.Fail("云端连接超时"))
        assertThat(failed.phase).isEqualTo(RecoveryPhase.Failed)
        assertThat(failed.failureReason).isEqualTo("云端连接超时")
        // 失败态不可直接完成
        assertThat(failed.reduce(RecoveryIntent.Complete(1))).isSameInstanceAs(failed)
        // 重置回到确认阶段且保留已选范围
        val reset = failed.reduce(RecoveryIntent.Reset)
        assertThat(reset.phase).isEqualTo(RecoveryPhase.Selecting)
        assertThat(reset.selectedScope).isEqualTo(failed.selectedScope)
        assertThat(reset.failureReason).isEmpty()
    }

    // ---- 4. 维护模式：文案、禁用判定、开关幂等 ----

    @Test
    fun `维护页文案逐字取自原型 v-maintenance`() {
        assertThat(MaintenanceText.Title).isEqualTo("服务维护中")
        assertThat(MaintenanceText.DescriptionPrefix).isEqualTo("我们正在升级服务，预计")
        assertThat(MaintenanceText.DurationSuffix).isEqualTo("内恢复。")
        assertThat(MaintenanceText.DescriptionLine2).isEqualTo("期间测量和查看数据不受影响，")
        assertThat(MaintenanceText.DescriptionLine3).isEqualTo("仅云同步和 AI 功能暂停。")
        assertThat(MaintenanceText.EtaPrefix).isEqualTo("预计恢复时间：")
        assertThat(MaintenanceText.RetryAction).isEqualTo("稍后再试")
        assertThat(MaintenanceText.AnnouncementAction).isEqualTo("查看状态公告")
    }

    @Test
    fun `维护状态默认值对齐原型`() {
        val state = MaintenanceUiState()
        assertThat(state.active).isTrue()
        assertThat(state.durationLabel).isEqualTo("30 分钟")
        assertThat(state.estimatedRecoveryTime).isEqualTo("14:30")
        assertThat(state.affectedLine).isEqualTo("受影响功能：云同步 · AI 解读 · 周总结")
    }

    @Test
    fun `维护模式下按受影响清单禁用入口`() {
        val state = MaintenanceUiState()
        MaintenanceFeature.entries.forEach { feature ->
            assertThat(isFeatureBlocked(feature, state)).isTrue()
            assertThat(isEntryEnabled(feature, state)).isFalse()
        }
        assertThat(enabledFeatures(MaintenanceFeature.entries, state)).isEmpty()
        // 非维护态全部放行
        val normal = MaintenanceUiState(active = false)
        assertThat(enabledFeatures(MaintenanceFeature.entries, normal))
            .containsExactlyElementsIn(MaintenanceFeature.entries)
        // 清单可由外部收窄：未列入的功能不受影响
        val narrowed = MaintenanceUiState(affectedFeatures = listOf(MaintenanceFeature.CloudSync))
        assertThat(isEntryEnabled(MaintenanceFeature.AiInterpretation, narrowed)).isTrue()
        assertThat(isEntryEnabled(MaintenanceFeature.CloudSync, narrowed)).isFalse()
    }

    @Test
    fun `维护模式开关幂等`() {
        val active = MaintenanceUiState(active = true)
        // 已在目标态：原地返回，且不重复通知宿主
        assertThat(active.applyMaintenanceRequest(MaintenanceRequest.Enter)).isSameInstanceAs(active)
        assertThat(active.requiresHostNotification(MaintenanceRequest.Enter)).isFalse()
        // 需要切换：产生新状态并通知
        val exited = active.applyMaintenanceRequest(MaintenanceRequest.Exit)
        assertThat(exited.active).isFalse()
        assertThat(active.requiresHostNotification(MaintenanceRequest.Exit)).isTrue()
        // 反向同理
        assertThat(exited.applyMaintenanceRequest(MaintenanceRequest.Exit)).isSameInstanceAs(exited)
        assertThat(exited.requiresHostNotification(MaintenanceRequest.Exit)).isFalse()
        assertThat(exited.applyMaintenanceRequest(MaintenanceRequest.Enter).active).isTrue()
    }

    // ---- 5. 源码守卫（AC-S5 forbidden）----

    private fun sourceOf(fileName: String): String {
        var dir: File? = File(System.getProperty("user.dir") ?: ".")
        while (dir != null) {
            val f = File(dir, "app/src/main/kotlin/com/xcwk/scalehub/feature/settings/$fileName")
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

    private val targets = listOf("SdkListScreen.kt", "DataRecovery.kt", "MaintenanceScreen.kt")

    @Test
    fun `目标文件不直连网络云端DB与包管理器`() {
        val forbidden = listOf(
            "android.net", "ConnectivityManager", "PackageManager", "getInstalledPackages",
            "okhttp3", "HttpURLConnection", "java.sql", "android.database",
            "openConnection", "Retrofit",
        )
        targets.forEach { fileName ->
            val code = codeOnly(sourceOf(fileName))
            forbidden.forEach { symbol ->
                assertThat(code).doesNotContain(symbol)
            }
        }
    }

    @Test
    fun `目标文件无硬编码颜色与裸 dp`() {
        targets.forEach { fileName ->
            val code = codeOnly(sourceOf(fileName))
            assertThat(code).doesNotMatch(Pattern.compile("""Color\(0x[0-9A-Fa-f]{6,8}\)"""))
            // 与 gov-prototype-verify 的 HARDCODED_DP 同口径
            assertThat(code).doesNotMatch(Pattern.compile("""(?<!Qk)\b\d+\.dp\b"""))
        }
    }

    @Test
    fun `目标文件复用 Qk 设计系统组件`() {
        targets.forEach { fileName ->
            assertThat(sourceOf(fileName)).contains("Qk")
        }
    }
}
