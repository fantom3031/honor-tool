package com.fantom3031.honortool.settings

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Base64
import com.fantom3031.honortool.core.Constants
import com.fantom3031.honortool.core.ImageKeys
import com.fantom3031.honortool.core.XLog
import java.io.ByteArrayOutputStream

/**
 * Хранилище картинок со стороны приложения.
 *
 * Пишем в world-readable `SharedPreferences`, откуда картинку читает
 * `HookImages` внутри процесса целевого приложения.
 */
object ImageStore {

    private const val WEBP_QUALITY = 95

    /** Порог байтов картинки: RemotePreferences не доставляет файлы заметно больше. */
    private const val MAX_REMOTE_BYTES = 700 * 1024

    private fun prefs(context: Context): SharedPreferences = try {
        @Suppress("DEPRECATION", "WorldReadableFiles")
        context.getSharedPreferences(Constants.IMAGE_PREFS_NAME, Context.MODE_WORLD_READABLE)
    } catch (_: Throwable) {
        context.getSharedPreferences(Constants.IMAGE_PREFS_NAME, Context.MODE_PRIVATE)
    }.also {
        WorldReadable.fix(context, Constants.IMAGE_PREFS_NAME)
    }

    fun syncToRemote(context: Context, remote: SharedPreferences) {
        try {
            val localAll = prefs(context).all
            val remoteAll = remote.all
            if (localAll.isNotEmpty()) {
                val editor = remote.edit()
                for ((k, v) in localAll) {
                    when (v) {
                        is String -> editor.putString(k, v)
                        is Long -> editor.putLong(k, v)
                    }
                }
                editor.apply()
                XLog.i("Хранилище картинок синхронизировано с RemotePreferences")
            } else if (remoteAll.isNotEmpty()) {
                val editor = prefs(context).edit()
                for ((k, v) in remoteAll) {
                    when (v) {
                        is String -> editor.putString(k, v)
                        is Long -> editor.putLong(k, v)
                    }
                }
                editor.apply()
                XLog.i("Хранилище картинок восстановлено из RemotePreferences")
            }
        } catch (t: Throwable) {
            XLog.e("Не удалось синхронизировать хранилище картинок с RemotePreferences", t)
        }
    }

    /** Сохраняет картинку. Возвращает false, если не удалось сжать или записать. */
    fun save(context: Context, key: String, bitmap: Bitmap, lossless: Boolean = false): Boolean {
        val bytes = compress(bitmap, lossless) ?: return false
        return try {
            val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            val stamp = System.currentTimeMillis()
            val saved = prefs(context).edit()
                .putString(key, base64)
                .putLong(ImageKeys.updatedKey(key), stamp)
                .commit()
            if (saved) {
                com.fantom3031.honortool.core.ServiceBridge.remoteImagePrefs?.edit()
                    ?.putString(key, base64)
                    ?.putLong(ImageKeys.updatedKey(key), stamp)
                    ?.apply()
                WorldReadable.fix(context, Constants.IMAGE_PREFS_NAME)
            }
            saved
        } catch (t: Throwable) {
            XLog.e("Не удалось сохранить картинку $key", t)
            false
        }
    }

    /** Удаляет картинку — целевое приложение вернётся к оригинальному ресурсу. */
    fun clear(context: Context, key: String) {
        val stamp = System.currentTimeMillis()
        prefs(context).edit()
            .remove(key)
            .putLong(ImageKeys.updatedKey(key), stamp)
            .commit()
        com.fantom3031.honortool.core.ServiceBridge.remoteImagePrefs?.edit()
            ?.remove(key)
            ?.putLong(ImageKeys.updatedKey(key), stamp)
            ?.apply()
        WorldReadable.fix(context, Constants.IMAGE_PREFS_NAME)
    }

    /** Base64-строка сохранённой картинки или null. */
    fun encodedString(context: Context, key: String): String? =
        prefs(context).getString(key, null)

    fun load(context: Context, key: String): Bitmap? {
        val encoded = prefs(context).getString(key, null) ?: return null
        return try {
            val bytes = Base64.decode(encoded, Base64.NO_WRAP)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (t: Throwable) {
            XLog.e("Не удалось прочитать картинку $key", t)
            null
        }
    }

    fun has(context: Context, key: String): Boolean =
        prefs(context).getLong(ImageKeys.updatedKey(key), 0L) != 0L

    /** Размер сохранённой картинки в байтах — показываем в UI. */
    fun sizeBytes(context: Context, key: String): Int =
        prefs(context).getString(key, null)?.length?.let { it / 4 * 3 } ?: 0

    private fun compress(bitmap: Bitmap, lossless: Boolean = false): ByteArray? {
        if (lossless) {
            try {
                ByteArrayOutputStream().use { out ->
                    if (bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) &&
                        out.size() <= MAX_REMOTE_BYTES
                    ) {
                        return out.toByteArray()
                    }
                }
            } catch (t: Throwable) {
                XLog.e("Не удалось сжать картинку в PNG", t)
            }
        }
        val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            @Suppress("DEPRECATION")
            Bitmap.CompressFormat.WEBP
        }
        return try {
            ByteArrayOutputStream().use { out ->
                if (!bitmap.compress(format, WEBP_QUALITY, out)) {
                    ByteArrayOutputStream().use { fallback ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, fallback)
                        fallback.toByteArray()
                    }
                } else {
                    out.toByteArray()
                }
            }
        } catch (t: Throwable) {
            XLog.e("Не удалось сжать картинку", t)
            null
        }
    }
}
