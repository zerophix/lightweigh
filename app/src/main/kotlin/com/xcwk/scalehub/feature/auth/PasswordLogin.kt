package com.xcwk.scalehub.feature.auth

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.xcwk.scalehub.ui.components.QkButton
import com.xcwk.scalehub.ui.components.QkButtonStyle
import com.xcwk.scalehub.ui.components.QkCard
import com.xcwk.scalehub.ui.theme.QkColor

/**
 * password-login — 密码登录表单（DEC-043-G，参考 LoginStage.kt:192）。
 */
@Composable
fun PasswordLoginCard(
    onSubmit: (phone: String, password: String) -> Unit,
    onSwitchToCode: () -> Unit = {},
    error: String? = null,
) {
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    QkCard {
        Text("密码登录", style = MaterialTheme.typography.titleMedium, color = QkColor.Fg)
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = phone,
            onValueChange = { phone = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("手机号") },
            singleLine = true,
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("密码") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
        )
        error?.let {
            Text(it, modifier = Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall, color = QkColor.Danger)
        }
        Spacer(modifier = Modifier.height(16.dp))
        QkButton(
            text = "登录",
            modifier = Modifier.fillMaxWidth(),
            enabled = phone.isNotBlank() && password.isNotBlank(),
            onClick = { onSubmit(phone, password) },
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("忘记密码？", style = MaterialTheme.typography.bodySmall, color = QkColor.Primary)
            Spacer(modifier = Modifier.weight(1f))
            QkButton(text = "验证码登录", style = QkButtonStyle.Ghost, onClick = onSwitchToCode)
        }
    }
}

/**
 * set-password — 设置密码表单（DEC-043-G，参考 SettingsPages.kt:215-252）。
 */
@Composable
fun SetPasswordCard(
    onSubmit: (newPassword: String, confirmPassword: String) -> Unit,
    onCancel: () -> Unit = {},
    error: String? = null,
) {
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    QkCard {
        Text("设置登录密码", style = MaterialTheme.typography.titleMedium, color = QkColor.Fg)
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = newPassword,
            onValueChange = { newPassword = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("新密码（至少 6 位）") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = confirmPassword,
            onValueChange = { confirmPassword = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("确认密码") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
        )
        error?.let {
            Text(it, modifier = Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall, color = QkColor.Danger)
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row {
            QkButton(text = "取消", style = QkButtonStyle.Ghost, modifier = Modifier.weight(1f), onClick = onCancel)
            Spacer(modifier = Modifier.padding(horizontal = 8.dp))
            QkButton(
                text = "保存",
                modifier = Modifier.weight(1f),
                enabled = newPassword.length >= 6 && newPassword == confirmPassword,
                onClick = { onSubmit(newPassword, confirmPassword) },
            )
        }
    }
}