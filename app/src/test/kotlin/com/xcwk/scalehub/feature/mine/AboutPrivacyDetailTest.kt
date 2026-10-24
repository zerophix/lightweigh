package com.xcwk.scalehub.feature.mine

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

/**
 * T-090 关于页 + 隐私详情页纯 JVM 单测。
 *
 * 覆盖三类可自动判定事实：
 * 1. 文案逐字取自原型07 v-about / v-privacy（AC then 原文，改一个字即红）；
 * 2. 版本号由传入参数注入（[aboutTagline] 纯函数），界面不写死版本；
 *    删除确认弹层门控为纯函数（[canConfirmDelete]）；
 * 3. 源码守卫：两文件无硬编码颜色、无裸 dp、不触系统敏感 API。
 */
class AboutPrivacyDetailTest {

    // ---- 1. v-about 文案逐字 ----

    @Test
    fun `v-about 文案逐字取自原型`() {
        assertThat(TITLE_ABOUT).isEqualTo("关于轻衡")
        assertThat(APP_NAME_ABOUT).isEqualTo("轻衡")
        assertThat(ROW_USER_AGREEMENT).isEqualTo("用户协议")
        assertThat(ROW_PRIVACY_POLICY).isEqualTo("隐私政策")
        assertThat(ROW_SDK_LIST).isEqualTo("第三方 SDK 清单")
        assertThat(ABOUT_COPYRIGHT).isEqualTo("© 2026 轻衡 · MIT License")
    }

    @Test
    fun `v-about 副标题由传入版本号拼接`() {
        assertThat(aboutTagline("v0.1.0")).isEqualTo("v0.1.0 · 让健康轻一点")
        assertThat(ABOUT_ROWS).containsExactly(
            "用户协议", "隐私政策", "第三方 SDK 清单",
        ).inOrder()
    }

    // ---- 2. v-privacy 文案逐字 ----

    @Test
    fun `v-privacy 文案逐字取自原型`() {
        assertThat(TITLE_PRIVACY_DETAIL).isEqualTo("隐私与数据管理")
        assertThat(ROW_AI_DATA).isEqualTo("AI 数据设置")
        assertThat(ROW_AI_DATA_CAPTION).isEqualTo("撤回授权 · 关闭个性化")
        assertThat(ROW_EXPORT_DATA).isEqualTo("导出健康数据")
        assertThat(ROW_EXPORT_DATA_CAPTION).isEqualTo("CSV · 加密链接 24h 有效")
        assertThat(ROW_SYNC).isEqualTo("数据同步")
        assertThat(ROW_SYNC_CAPTION).isEqualTo("端到端加密")
        assertThat(ROW_PERSONALIZATION).isEqualTo("个性化分析")
        assertThat(ROW_DELETE_ALL).isEqualTo("删除全部健康数据")
        assertThat(ROW_DELETE_ALL_CAPTION).isEqualTo("不可恢复")
        assertThat(HINT_DELETE_VS_CLOSURE)
            .isEqualTo("删除、注销是不同操作，详见「数据删除与保留规则」。")
    }

    @Test
    fun `v-privacy 删除确认弹层文案拼接`() {
        assertThat(CONFIRM_DELETE_TITLE).isEqualTo("删除全部健康数据？")
        assertThat(deleteConfirmBody())
            .isEqualTo("全部测量记录与报告将被删除，不可恢复。建议先导出。")
        assertThat(ACTION_CANCEL_DELETE).isEqualTo("取消")
        assertThat(ACTION_CONFIRM_DELETE).isEqualTo("确认删除")
    }

    @Test
    fun `删除确认仅在弹层打开时可用`() {
        assertThat(canConfirmDelete(true)).isTrue()
        assertThat(canConfirmDelete(false)).isFalse()
    }

    @Test
    fun `隐私页四条目顺序与原型一致`() {
        assertThat(PRIVACY_ROWS.map { it.first })
            .containsExactly("AI 数据设置", "导出健康数据", "数据同步", "个性化分析")
            .inOrder()
    }

    // ---- 3. 源码守卫 ----

    private fun srcOf(name: String): String =
        File("src/main/kotlin/com/xcwk/scalehub/feature/mine/$name").readText()

    @Test
    fun `两文件无硬编码颜色与裸 dp`() {
        listOf("AboutScreen.kt", "PrivacyDetail.kt").forEach { name ->
            val src = srcOf(name)
            assertThat(Regex("""Color\(0x""").containsMatchIn(src)).isFalse()
            assertThat(Regex("""\b\d+\.dp\b""").containsMatchIn(src)).isFalse()
        }
    }

    @Test
    fun `两文件不触系统敏感 API`() {
        listOf("AboutScreen.kt", "PrivacyDetail.kt").forEach { name ->
            val src = srcOf(name)
            listOf("ConnectivityManager", "android.net", "ContentResolver", "Room")
                .forEach { assertThat(src).doesNotContain(it) }
        }
    }

    @Test
    fun `两文件使用 Qk 组件与主题 token`() {
        listOf("AboutScreen.kt", "PrivacyDetail.kt").forEach { name ->
            val src = srcOf(name)
            assertThat(src).contains("QkCard")
            assertThat(src).contains("QkColor.")
        }
    }
}
