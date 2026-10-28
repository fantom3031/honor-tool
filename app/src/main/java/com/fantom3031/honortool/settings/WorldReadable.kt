package com.fantom3031.honortool.settings

import android.content.Context
import com.fantom3031.honortool.core.XLog
import java.io.File

/**
 * Доступность файлов настроек чужим процессам.
 *
 * Хуки читают наши настройки из процесса целевого приложения через
 * `XSharedPreferences`, поэтому каталоги над файлом должны быть доступны
 * для входа (x) и чтения (r), а сам файл настроек — для чтения (r).
 */
internal object WorldReadable {

    fun fix(context: Context, prefsName: String? = null) {
        try {
            val dataDir = File(context.applicationInfo.dataDir)
            makeTraversable(dataDir)

            val prefsDir = File(dataDir, "shared_prefs")
            makeTraversable(prefsDir)

            if (prefsName != null) {
                makeReadable(File(prefsDir, "$prefsName.xml"))
            }

            prefsDir.listFiles()?.forEach { file ->
                if (file.isFile) makeReadable(file)
            }
        } catch (t: Throwable) {
            XLog.e("Не удалось обновить права доступа к $prefsName", t)
        }
    }

    fun isReadable(context: Context, prefsName: String): Boolean = try {
        val dataDir = File(context.applicationInfo.dataDir)
        val prefsFile = File(dataDir, "shared_prefs/$prefsName.xml")
        !prefsFile.exists() || prefsFile.canRead()
    } catch (_: Throwable) {
        true
    }

    private fun makeTraversable(dir: File) {
        if (!dir.exists()) return
        dir.setReadable(true, false)
        dir.setExecutable(true, false)
    }

    private fun makeReadable(file: File) {
        if (!file.exists()) return
        file.setReadable(true, false)
    }
}
