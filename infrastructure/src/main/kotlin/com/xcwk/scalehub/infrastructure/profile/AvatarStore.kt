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
    private fun fileFor(memberId: String): File =
        File(File(appContext.filesDir, "avatars").apply { mkdirs() }, "avatar_$memberId.jpg")

    /** 按成员读头像路径；member 1 回退旧单文件名（升级前存量）。 */
    fun path(memberId: String): String? {
        val scoped = fileFor(memberId)
        if (!scoped.exists() && memberId == "1") {
            val legacySingle = File(File(appContext.filesDir, "avatars").apply { mkdirs() }, "avatar.jpg")
            if (legacySingle.exists()) legacySingle.renameTo(scoped)
        }
        return if (scoped.exists()) scoped.absolutePath else null
    }

    /** 删除某成员头像文件（成员移除联动）。 */
    fun removeFor(memberId: String) {
        fileFor(memberId).delete()
    }

    fun saveFromUri(uri: Uri, memberId: String): String? = runCatching {
        val file = fileFor(memberId)
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
