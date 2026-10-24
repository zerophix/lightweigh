package com.xcwk.scalehub.ui.shell

/**
 * 接入波（第二批）的入口登记表 —— 「哪个宿主页上的哪一行，通向哪一屏」。
 *
 * ## 为什么单列一份
 * 本批接入的屏（[com.xcwk.scalehub.feature.ai.AiBoundariesScreen] /
 * [com.xcwk.scalehub.feature.member.ActionPlansScreen] /
 * [com.xcwk.scalehub.feature.measure.ResultShareFlow] /
 * [com.xcwk.scalehub.feature.device.FirmwareManager] /
 * [com.xcwk.scalehub.feature.device.DeviceRenameScreen] /
 * [com.xcwk.scalehub.feature.device.MemberDeviceLinkScreen]）都是呈现式组件：
 * 它们不知道自己在导航里的位置。入口文案、归属宿主页、目标屏三者写在一处，
 * 宿主页只引一行（[WiringEntry.title] / [WiringEntry.hint]），目标屏只在路由装配点出现一次。
 *
 * ## 边界
 * 本文件是纯 Kotlin（零 Compose / 零 Android 依赖）：入口→屏的映射可在 JVM 单测里穷举，
 * 「入口没接上」这类只在手点时才暴露的问题因此能被测试挡住。
 */

/** 入口所属的宿主页（用户从哪一页点进去）。 */
enum class WiringHost {
    /** AI 问答页。 */
    Ai,

    /** 成员管理页（「我的 → 成员管理」）。 */
    Members,

    /** 首页测量结果区。 */
    Measure,

    /** 设备页签。 */
    Device,
}

/** 入口指向的目标屏（与呈现式屏一一对应）。 */
enum class WiringTarget(val screen: String) {
    AiBoundaries("AiBoundariesScreen"),
    ActionPlans("ActionPlansScreen"),
    ResultShare("ResultShareFlow"),
    Firmware("FirmwareManager"),
    DeviceRename("DeviceRenameScreen"),
    MemberDeviceLink("MemberDeviceLinkScreen"),
}

/**
 * 一条入口。
 *
 * @param id 稳定标识（进程重建 / 埋点用，不随文案改动）
 * @param title 入口行标题
 * @param hint 入口行副标题（说明点进去能看到什么）
 */
data class WiringEntry(
    val id: String,
    val host: WiringHost,
    val title: String,
    val hint: String,
    val target: WiringTarget,
)

/** 本批入口清单。文案即宿主页上那一行，改文案只改这里。 */
object WiringEntries {
    val AiBoundaries = WiringEntry(
        id = "ai.boundaries",
        host = WiringHost.Ai,
        title = "AI 边界与降级示例",
        hint = "六种边界状态：不可用、旧报告、分析中、新报告、未授权、成员未授权",
        target = WiringTarget.AiBoundaries,
    )

    val ActionPlans = WiringEntry(
        id = "member.actionPlans",
        host = WiringHost.Members,
        title = "行动计划",
        hint = "本周计划、任务提醒与通知权限状态",
        target = WiringTarget.ActionPlans,
    )

    val ResultShare = WiringEntry(
        id = "measure.resultShare",
        host = WiringHost.Measure,
        title = "分享 / 导出",
        hint = "分享本次测量结果，或导出为 CSV / PDF / 图片",
        target = WiringTarget.ResultShare,
    )

    val Firmware = WiringEntry(
        id = "device.firmware",
        host = WiringHost.Device,
        title = "固件更新",
        hint = "检查设备新版本并升级固件",
        target = WiringTarget.Firmware,
    )

    val DeviceRename = WiringEntry(
        id = "device.rename",
        host = WiringHost.Device,
        title = "设备重命名",
        hint = "给体脂秤一个好记的名字",
        target = WiringTarget.DeviceRename,
    )

    val MemberDeviceLink = WiringEntry(
        id = "device.memberLink",
        host = WiringHost.Device,
        title = "成员设备关联",
        hint = "管理哪些成员使用这台设备",
        target = WiringTarget.MemberDeviceLink,
    )

    /** 全部入口（顺序即宿主页内的呈现顺序）。 */
    val All: List<WiringEntry> = listOf(
        AiBoundaries,
        ActionPlans,
        ResultShare,
        Firmware,
        DeviceRename,
        MemberDeviceLink,
    )

    /** 某宿主页上的入口（设备页三项，其余各一项）。 */
    fun forHost(host: WiringHost): List<WiringEntry> = All.filter { it.host == host }
}

/** 入口 id → 目标屏。宿主按 id 查找唯一目标，避免两条入口指向同一屏却各自写死。 */
fun wiringEntryTargets(): Map<String, WiringTarget> =
    WiringEntries.All.associate { it.id to it.target }

/** 某宿主页上的入口 id 列表（测试与埋点共用）。 */
fun wiringEntryIdsFor(host: WiringHost): List<String> =
    WiringEntries.forHost(host).map { it.id }
