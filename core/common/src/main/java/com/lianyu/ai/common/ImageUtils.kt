package com.lianyu.ai.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

object ImageUtils {

    /**
     * 保存封面图（"我"页面顶部大图框）：仅压缩尺寸，不降低画质。
     * 长边超过 [maxDimension] 时按比例缩小，再以高质量 JPEG（95）写盘，
     * 避免用户上传的原图占用过大存储空间。
     */
    suspend fun saveBannerToInternalStorage(
        context: Context,
        uri: String,
        maxDimension: Int = 1440
    ): String? = withContext(Dispatchers.IO) {
        try {
            val inputUri = Uri.parse(uri)
            val original = context.contentResolver.openInputStream(inputUri)?.use { input ->
                BitmapFactory.decodeStream(input)
            } ?: return@withContext null

            val scaled = if (original.width > maxDimension || original.height > maxDimension) {
                val ratio = minOf(
                    maxDimension.toFloat() / original.width,
                    maxDimension.toFloat() / original.height
                )
                val targetWidth = (original.width * ratio).toInt().coerceAtLeast(1)
                val targetHeight = (original.height * ratio).toInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(original, targetWidth, targetHeight, true)
            } else {
                original
            }

            val bannersDir = File(context.filesDir, "banners")
            if (!bannersDir.exists()) {
                bannersDir.mkdirs()
            }
            val file = File(bannersDir, "banner_${UUID.randomUUID()}.jpg")
            file.outputStream().use { output ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 95, output)
            }

            if (scaled !== original) {
                original.recycle()
            }
            scaled.recycle()

            file.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    fun deleteBannerFile(path: String?) {
        path?.let {
            try {
                File(it).delete()
            } catch (_: Exception) { }
        }
    }

    suspend fun saveUriToInternalStorage(context: Context, uri: String): String? = withContext(Dispatchers.IO) {
        try {
            if (!uri.startsWith("content://") && File(uri).exists()) {
                return@withContext uri
            }

            val inputUri = Uri.parse(uri)
            val fileName = "avatar_${UUID.randomUUID()}.jpg"
            val avatarsDir = File(context.filesDir, "avatars")
            if (!avatarsDir.exists()) {
                avatarsDir.mkdirs()
            }
            val file = File(avatarsDir, fileName)

            context.contentResolver.openInputStream(inputUri)?.use { input ->
                file.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: run {
                return@withContext null
            }
            file.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    fun deleteAvatarFile(path: String?) {
        path?.let {
            try {
                File(it).delete()
            } catch (_: Exception) { }
        }
    }
}
