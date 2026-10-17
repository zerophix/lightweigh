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
    private val file: File get() = File(File(appContext.filesDir, "avatars").apply { mkdirs() }, "avatar.jpg")

    fun path(): String? = migrateLegacy()

    /** 一次性迁移：早期版本头像落在 filesDir 根，挪进 avatars/ 子目录。 */
    private fun migrateLegacy(): String? {
        val legacy = File(appContext.filesDir, "avatar.jpg")
        if (!file.exists() && legacy.exists()) {
            legacy.renameTo(file)
        }
        return if (file.exists()) file.absolutePath else null
    }

    /**
     * 相机拍照目标（M4 相机头像）：cache/camera/avatar.jpg 的 content URI。
     * 相机写入此文件后调用方再走 saveFromUri 压缩落盘；授权范围见 file_paths.xml。
     */
    fun newCameraTarget(): String? = runCatching {
        val dir = File(appContext.cacheDir, "camera").apply { mkdirs() }
        val target = File(dir, "avatar.jpg")
        androidx.core.content.FileProvider.getUriForFile(
            appContext, "${appContext.packageName}.fileprovider", target,
        ).toString()
    }.getOrNull()

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
