package com.xcwk.scalehub.feature.features

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.xcwk.scalehub.ui.components.QkCard
import com.xcwk.scalehub.ui.theme.QkTheme

/** 路由契约。 */
const val FEATURE_ROUTE_NAME = "feature/{name}"

/** 旧库 featureNames 映射（Pages.kt:430-451 逐条移植）。 */
private val featureNames = mapOf(
    "notifications" to "通知提醒",
    "card-manage" to "卡片管理",
    "fasting" to "轻断食",
    "period" to "生理期记录",
    "buddy" to "减肥搭子",
    "baby" to "亲亲宝贝",
    "food-compare" to "食物对比",
    "assessment" to "健康测评",
    "service" to "客服服务",
    "recipes" to "健康食谱",
    "coins" to "OK币",
    "weekly-report" to "健康周报",
    "care" to "亲情关爱",
    "badges" to "我的徽章",
    "tools" to "健康工具",
    "widgets" to "桌面小组件",
    "help" to "帮助反馈",
    "user-home" to "个人主页",
    "theme" to "主题皮肤",
    "scan" to "扫一扫",
)

/**
 * feature — 占位页（旧库 FeatureScreen 移植，Pages.kt:454-458）：
 * 旧库此页即为占位文案（"对应业务还没接入"），v2 原样保留口径——如实告知。
 */
@Composable
fun FeatureRoute(name: String, onBack: () -> Unit) {
    FeatureScreen(name = name, onBack = onBack)
}

@Composable
fun FeatureScreen(name: String, onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.ArrowBack,
                contentDescription = "返回",
                modifier = Modifier.padding(end = 12.dp),
            )
            Text(featureNames[name] ?: "功能详情", style = MaterialTheme.typography.headlineSmall)
        }
        QkCard(modifier = Modifier.padding(top = 16.dp)) {
            Text(
                "这个入口的界面已经按参考项目放好。对应业务还没接入，体脂秤测量、档案和日常记录可以继续使用。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun FeatureScreenPreview() {
    QkTheme { FeatureScreen(name = "fasting", onBack = {}) }
}
