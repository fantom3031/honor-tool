package com.fantom3031.honortool.core

import android.os.Build

/**
 * Утилиты для безопасного чтения системных свойств Android (SystemProperties).
 * Работает как внутри UI приложения, так и внутри хукнутых процессов.
 */
object SystemProperties {

    private val systemPropertiesClass by lazy {
        runCatching { Class.forName("android.os.SystemProperties") }.getOrNull()
    }

    private val getMethod by lazy {
        systemPropertiesClass?.getMethod("get", String::class.java, String::class.java)
    }

    /**
     * Возвращает значение системного свойства или [def], если свойство не найдено или недоступно.
     */
    fun get(key: String, def: String = ""): String {
        return runCatching {
            getMethod?.invoke(null, key, def) as? String
        }.getOrNull()?.takeIf { it.isNotEmpty() } ?: def
    }

    /**
     * Версия релиза Android из свойства `ro.build.version.release` (fallback на [Build.VERSION.RELEASE]).
     */
    val releaseVersion: String
        get() {
            val prop = get("ro.build.version.release")
            return if (prop.isNotEmpty()) prop else Build.VERSION.RELEASE.orEmpty()
        }

    /**
     * Основная версия Android как целое число (например, 15, 16).
     * Если не удалось распарсить, берётся [Build.VERSION.SDK_INT] с конвертацией:
     * 35 -> 15, 36 -> 16 и т.д.
     */
    val releaseVersionMajor: Int
        get() {
            val release = releaseVersion.trim()
            val majorFromRelease = release.substringBefore('.').toIntOrNull()
            if (majorFromRelease != null && majorFromRelease > 0) {
                return majorFromRelease
            }
            // Fallback по SDK_INT: Android 15 = API 35, Android 16 = API 36
            val sdk = Build.VERSION.SDK_INT
            return if (sdk >= 31) sdk - 20 else sdk
        }
}
