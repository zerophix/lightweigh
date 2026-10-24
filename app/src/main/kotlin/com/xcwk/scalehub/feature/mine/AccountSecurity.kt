package com.xcwk.scalehub.feature.mine

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.xcwk.scalehub.ui.components.QkCard
import com.xcwk.scalehub.ui.components.QkListRow
import com.xcwk.scalehub.ui.components.QkSectionHead
import com.xcwk.scalehub.ui.components.QkVSpace
import com.xcwk.scalehub.ui.theme.QkColor
import com.xcwk.scalehub.ui.theme.QkSpacing

/**
 * mine — 账号与安全页（原型07 v-account）。
 *
 * 三个绑定行（手机号/邮箱/登录密码）+ 注销入口（接 T-089 注销四态流程）。
 * 文案逐字取自原型；绑定态（掩码/已设置/未绑定）与行点击回调由调用方注入，
 * 组件零系统依赖（呈现式，DEC-061 口径）。
 */

// ---- 静态文案（逐字取自原型 07 v-account） ----

const val TITLE_ACCOUNT_SECURITY = "账号与安全"
const val ROW_PHONE = "手机号"
const val PHONE_MASKED_DEFAULT = "138****0000"
const val ACTION_REPLACE_PHONE = "更换"
const val ROW_EMAIL = "邮箱"
const val EMAIL_UNBOUND = "未绑定"
const val ACTION_BIND = "绑定"
const val ROW_PASSWORD = "登录密码"
const val PASSWORD_SET = "已设置"
const val ACTION_MODIFY = "修改"
const val ROW_ACCOUNT_CLOSURE = "注销账号"
const val ACCOUNT_CLOSURE_CAPTION = "注销后数据永久删除"
const val ACCOUNT_CLOSURE_HINT = "注销前建议先在「隐私与数据管理」导出数据。"

/** 三个绑定行标题（顺序即原型顺序）。 */
val ACCOUNT_ROWS = listOf(ROW_PHONE, ROW_EMAIL, ROW_PASSWORD)

/**
 * 账号与安全页（无状态）：三绑定行 + 注销入口。
 *
 * [phoneMasked] / [emailState] / [passwordState] 为绑定态文案（原型仅定义
 * 未绑定/已设置两种形态，其余由调用方注入，不编造）；行点击与注销点击
 * 回调注入，本组件不触网络/存储。
 */
@Composable
fun AccountSecurityScreen(
    phoneMasked: String,
    emailState: String,
    passwordState: String,
    onRowClick: (String) -> Unit,
    onClosureClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text(TITLE_ACCOUNT_SECURITY, style = MaterialTheme.typography.titleLarge)
        QkVSpace(QkSpacing.Sm)
        QkCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                QkListRow(
                    title = ROW_PHONE,
                    subtitle = phoneMasked,
                    trailing = { Text(ACTION_REPLACE_PHONE, color = QkColor.Primary) },
                    modifier = Modifier.fillMaxWidth().clickable { onRowClick(ROW_PHONE) },
                )
                QkListRow(
                    title = ROW_EMAIL,
                    subtitle = emailState,
                    trailing = { Text(ACTION_BIND, color = QkColor.Primary) },
                    modifier = Modifier.fillMaxWidth().clickable { onRowClick(ROW_EMAIL) },
                )
                QkListRow(
                    title = ROW_PASSWORD,
                    subtitle = passwordState,
                    trailing = { Text(ACTION_MODIFY, color = QkColor.Primary) },
                    modifier = Modifier.fillMaxWidth().clickable { onRowClick(ROW_PASSWORD) },
                )
            }
        }
        QkVSpace(QkSpacing.Md)
        QkSectionHead(ROW_ACCOUNT_CLOSURE)
        QkVSpace(QkSpacing.Xs)
        Text(
            ACCOUNT_CLOSURE_CAPTION,
            style = MaterialTheme.typography.bodySmall,
            color = QkColor.Danger,
        )
        QkVSpace(QkSpacing.Xs)
        QkCard(modifier = Modifier.fillMaxWidth()) {
            QkListRow(
                title = ROW_ACCOUNT_CLOSURE,
                subtitle = ACCOUNT_CLOSURE_HINT,
                modifier = Modifier.fillMaxWidth().clickable { onClosureClick() },
            )
        }
    }
}
