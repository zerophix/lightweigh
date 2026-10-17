package com.xcwk.scalehub.infrastructure.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File

/**
 * 头像本地存储（M4）：相册选图 → 降采样压缩到 filesDir/avatar.jpg。
 * 路径随 UI 展示；文件不存在即回退占位头像（旧库 MemberStore.avatarFile 口径）。
 */
class AvatarStore(context: Context) {

    private val appContext = context.applicationContext
    private val file: File get() = File(appContext.filesDir, "avatar.jpg")

    fun path(): String? = if (file.exists()) file.absolutePath else null

    fun saveFromUri(uri: Uri): String? = runCatching {
        val bitmap = appContext.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input)
        } ?: return null
        val scaled = scaleDown(bitmap, MAX_EDGE)
        file.outputStream().use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        scaled.recycle()
        file.absolutePath
    }.getOrNull()

    private fun scaleDown(source: Bitmap, max: Int): Bitmap {
        val longest = source.width.coerceAtLeast(source.height)
        if (longest <= max) return source
        val ratio = max.toFloat() / longest
        return Bitmap.createScaledBitmap(
            source,
            (source.width * ratio).toInt().coerceAtLeast(1),
            (source.height * ratio).toInt().coerceAtLeast(1),
            true,
        )
    }

    companion object {
        private const val MAX_EDGE = 512
    }
}
