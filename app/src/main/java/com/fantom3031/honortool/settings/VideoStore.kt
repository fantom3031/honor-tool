package com.fantom3031.honortool.settings

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.fantom3031.honortool.core.XLog

/**
 * Хранилище видео-замены анимации «О телефоне».
 *
 * Файл копируется в `Download/honortool/` через MediaStore (доступно процессу
 * настроек через FUSE) и возвращает фактический путь, который хук читает из prefs.
 */
object VideoStore {

    private const val DIR = "Download/honortool"
    private const val FILE_NAME = "banner.mp4"

    /** Копирует выбранное видео и возвращает фактический путь файла. */
    fun copyToDownloads(context: Context, source: Uri): String? {
        return try {
            deleteOwn(context)
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, FILE_NAME)
                put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                put(MediaStore.MediaColumns.RELATIVE_PATH, DIR)
            }
            val target = context.contentResolver
                .insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
            context.contentResolver.openOutputStream(target)?.use { out ->
                context.contentResolver.openInputStream(source)?.use { input ->
                    input.copyTo(out)
                } ?: return null
            } ?: return null
            queryDataPath(context, target)
        } catch (t: Throwable) {
            XLog.e("Не удалось скопировать видео", t)
            null
        }
    }

    /** Удаляет файлы замены: свои через MediaStore и adb-pushed напрямую. */
    fun clear(context: Context) {
        try {
            deleteOwn(context)
            val file = java.io.File(defaultPath())
            if (file.exists()) file.delete()
        } catch (t: Throwable) {
            XLog.e("Не удалось удалить видео", t)
        }
    }

    fun defaultPath(): String = "/sdcard/$DIR/$FILE_NAME"

    private fun deleteOwn(context: Context) {
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        resolver.query(
            collection,
            arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.DISPLAY_NAME} = ?",
            arrayOf(FILE_NAME),
            null
        )?.use { cursor ->
            val ids = mutableListOf<Long>()
            while (cursor.moveToNext()) ids.add(cursor.getLong(0))
            ids.forEach { resolver.delete(ContentUris.withAppendedId(collection, it), null, null) }
        }
    }

    private fun queryDataPath(context: Context, uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
}
