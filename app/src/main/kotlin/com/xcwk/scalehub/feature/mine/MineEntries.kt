package com.xcwk.scalehub.feature.mine

/**
 * mine — 我的页二级入口登记表（原型 07 接入波）。
 *
 * 账号与安全 / 隐私与数据管理 / 健康数据说明 / 关于四屏在此之前只有呈现层，
 * 我的页上没有入口，用户点不到。入口文案、所属宿主页、目标屏三者写在一处：
 * 宿主页只引一行（[MineEntriesCard]），目标屏只在装配点（[MineMoreHost]）出现一次。
 *
 * 纯 Kotlin（零 Compose / 零 Android 依赖），"入口指向的屏是否已交付"可在 JVM 单测里穷举。
 */
enum class MineEntry(
    /** 入口行标题（即我的页上那一行）。 */
    val title: String,
    /** 入口行副标题。 */
    val hint: String,
    /** 目标屏名（与 feature 层的 Screen 函数同名，单测据此查存在性）。 */
    val screen: String,
) {
    AccountSecurity(
        title = "账号与安全",
        hint = "手机号、邮箱与登录密码",
        screen = "AccountSecurityScreen",
    ),
    PrivacyDetail(
        title = "隐私与数据管理",
        hint = "AI 数据设置、导出与删除全部数据",
        screen = "PrivacyDetailScreen",
    ),
    HealthInfo(
        title = "健康数据说明",
        hint = "收集范围、加密方式与保留期",
        screen = "HealthInfoScreen",
    ),
    About(
        title = "关于轻衡",
        hint = "版本、用户协议与第三方 SDK 清单",
        screen = "AboutScreen",
    ),
}

/** 入口清单（顺序即我的页「账号与信息」卡内的呈现顺序）。 */
val MINE_ENTRIES: List<MineEntry> = MineEntry.entries

/** 我的页「账号与信息」卡的组标题。 */
const val MINE_ENTRIES_SECTION = "账号与信息"

/** 入口标题 → 目标屏名；未登记的标题返回 null（单测用来钉「每行都有去处」）。 */
fun mineEntryScreenName(title: String): String? =
    MINE_ENTRIES.firstOrNull { it.title == title }?.screen
