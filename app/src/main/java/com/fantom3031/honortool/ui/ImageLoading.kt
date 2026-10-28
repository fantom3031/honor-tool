package com.fantom3031.honortool.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import com.fantom3031.honortool.core.XLog
import kotlin.math.max

/** Загрузка выбранной пользователем картинки с учётом ориентации из EXIF. */
object ImageLoading {

    /** Ограничение на сторону, чтобы не держать в памяти 50-мегапиксельные снимки. */
    private const val MAX_SIDE = 3000

    fun decode(context: Context, uri: Uri, maxSide: Int = MAX_SIDE): Bitmap? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            decodeWithImageDecoder(context, uri, maxSide)
        } else {
            decodeWithBitmapFactory(context, uri, maxSide)
        }
    } catch (t: Throwable) {
        XLog.e("Не удалось открыть картинку $uri", t)
        null
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.P)
    private fun decodeWithImageDecoder(context: Context, uri: Uri, maxSide: Int): Bitmap {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = false
            val longest = max(info.size.width, info.size.height)
            if (longest > maxSide) {
                val ratio = maxSide.toFloat() / longest
                decoder.setTargetSize(
                    (info.size.width * ratio).toInt().coerceAtLeast(1),
                    (info.size.height * ratio).toInt().coerceAtLeast(1)
                )
            }
        }
    }

    private fun decodeWithBitmapFactory(context: Context, uri: Uri, maxSide: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }

        val options = BitmapFactory.Options().apply {
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / sample > maxSide) sample *= 2
            inSampleSize = sample
        }
        val bitmap = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: return null

        val rotation = context.contentResolver.openInputStream(uri)?.use { stream ->
            when (ExifInterface(stream).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f

        if (rotation == 0f) return bitmap
        val matrix = Matrix().apply { postRotate(rotation) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
