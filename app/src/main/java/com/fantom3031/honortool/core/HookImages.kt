package com.fantom3031.honortool.core

import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64

/**
 * Чтение картинок изнутри процессов с хуками.
 *
 * Картинки лежат в виде base64 в RemotePreferences, предоставляемых современным
 * XposedInterface (getRemotePreferences).
 */
internal object HookImages {

    @Volatile
    private var prefs: SharedPreferences? = null

    fun init(imagePrefs: SharedPreferences?) {
        prefs = imagePrefs
    }

    private var cachedKey: String? = null
    private var cachedStamp = 0L
    private var cachedBytes: ByteArray? = null
    private var cachedBitmap: Bitmap? = null

    /** Сырые байты картинки или null, если пользователь ничего не выбрал. */
    fun bytes(key: String): ByteArray? = synchronized(this) {
        val store = prefs ?: return null

        val stamp = store.getLong(ImageKeys.updatedKey(key), 0L)
        if (stamp == 0L) {
            invalidate()
            return null
        }
        if (cachedKey == key && cachedStamp == stamp) return cachedBytes

        val encoded = store.getString(key, null)
        if (encoded.isNullOrEmpty()) {
            invalidate()
            return null
        }

        val decoded = try {
            Base64.decode(encoded, Base64.NO_WRAP)
        } catch (t: Throwable) {
            XLog.e("Битая картинка в хранилище: $key", t)
            invalidate()
            return null
        }

        cachedKey = key
        cachedStamp = stamp
        cachedBytes = decoded
        cachedBitmap = null
        return decoded
    }

    /** Декодированная картинка (кэшируется до следующего изменения). */
    fun bitmap(key: String): Bitmap? = synchronized(this) {
        val data = bytes(key) ?: return null
        cachedBitmap?.let { if (!it.isRecycled) return it }
        val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size)
        if (bitmap == null) {
            XLog.e("Не удалось декодировать картинку $key")
            return null
        }
        cachedBitmap = bitmap
        return bitmap
    }

    private fun invalidate() {
        cachedKey = null
        cachedStamp = 0L
        cachedBytes = null
        cachedBitmap = null
    }
}
