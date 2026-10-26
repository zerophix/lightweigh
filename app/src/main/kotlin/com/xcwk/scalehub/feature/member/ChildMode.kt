package com.xcwk.scalehub.feature.member

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.xcwk.scalehub.ui.components.QkCard
import com.xcwk.scalehub.ui.theme.QkColor
import com.xcwk.scalehub.ui.theme.QkSpacing
import java.time.LocalDate
import java.time.Period

/**
 * 儿童模式（T-071）。
 *
 * 口径（隐私优先，默认收紧）：
 * - 未成年人（< 18 岁）默认关闭 AI 解读、报告导出与跨成员分享；
 * - 只有监护人明确授权后才开放，授权状态由调用方持有并持久化；
 * - 年龄未知一律按未成年人处理（宁可多要一次授权）。
 */
enum class MemberFeature { AiInsight, ShareReport, CrossMemberView }

object ChildModePolicy {

    const val ADULT_AGE = 18

    /** 未知年龄按儿童处理。 */
    fun isMinor(age: Int?): Boolean = age == null || age < ADULT_AGE

    /**
     * 某项功能对当前成员是否可用。
     *
     * @param age 成员年龄；null 表示未知
     * @param guardianConsent 监护人是否已授权（仅未成年人需要）
     */
    fun allows(feature: MemberFeature, age: Int?, guardianConsent: Boolean): Boolean {
        if (!isMinor(age)) return true
        if (!guardianConsent) return false
        // 即使授权，跨成员数据仍不允许在儿童模式下暴露
        return feature != MemberFeature.CrossMemberView
    }

    /** 需要提示"已开启儿童模式"的场景：未成年人且未授权。 */
    fun needsGuardianPrompt(age: Int?, guardianConsent: Boolean): Boolean =
        isMinor(age) && !guardianConsent
}


/** 从出生日期文本算周岁；解析失败或日期在未来返回 null（不猜年龄）。 */
fun ageFromBirthDate(raw: String, today: LocalDate = LocalDate.now()): Int? {
    val (y, m, d) = parseBirthDate(raw) ?: return null
    val birth = runCatching { LocalDate.of(y, m, d) }.getOrNull() ?: return null
    if (birth.isAfter(today)) return null
    return Period.between(birth, today).years
}

/**
 * 未成年人提示卡：只在年龄可判定且小于 18 时出现。
 * 授权开关由调用方持有（本组件不持久化），避免"组件自己记住同意过"。
 */
@Composable
fun ChildModeNotice(
    age: Int?,
    guardianConsent: Boolean,
    onConsentChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!ChildModePolicy.isMinor(age)) return
    QkCard(modifier = modifier) {
        Text(
            text = "未成年人模式",
            style = MaterialTheme.typography.titleSmall,
            color = QkColor.Fg,
        )
        Text(
            text = "AI 解读与报告分享默认关闭；监护人同意后开放，跨成员数据始终不可见。",
            modifier = Modifier.padding(top = QkSpacing.Xs),
            style = MaterialTheme.typography.bodySmall,
            color = QkColor.Muted,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = QkSpacing.Sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "监护人已同意",
                style = MaterialTheme.typography.bodyMedium,
                color = QkColor.Fg,
            )
            Switch(checked = guardianConsent, onCheckedChange = onConsentChange)
        }
    }
}
