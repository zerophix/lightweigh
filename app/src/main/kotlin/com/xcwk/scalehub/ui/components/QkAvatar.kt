package com.xcwk.scalehub.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import java.io.File

/**
 * 头像（旧库 Avatar :559-570 移植，M4）：本地路径有图即圆形裁剪展示，
 * 无图/文件缺失回退人形占位（主题 token 取色）。
 */
@Composable
fun QkAvatar(path: String?, size: Dp) {
    val bitmap = remember(path) {
        if (path.isNullOrBlank() || !File(path).exists()) {
            null
        } else {
            BitmapFactory.decodeFile(path)?.asImageBitmap()
        }
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = "头像",
            modifier = Modifier.size(size).clip(CircleShape),
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(
            Modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(size * 0.55f),
            )
        }
    }
}
