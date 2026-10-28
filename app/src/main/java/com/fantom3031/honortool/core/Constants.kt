package com.fantom3031.honortool.core

object Constants {
    /** Имя файла настроек, общего для UI приложения и процессов с хуками. */
    const val PREFS_NAME = "honor_tool_prefs"

    /**
     * Отдельный файл для картинок (base64), чтобы основной файл настроек
     * оставался маленьким и быстро перечитывался внутри хуков.
     */
    const val IMAGE_PREFS_NAME = "honor_tool_images"

    /** Тег для logcat и журнала Xposed. */
    const val TAG = "HonorTool"

    /** Ключ настройки «хук включён». */
    fun enabledKey(hookId: String): String = "hook_${hookId}_enabled"

    /** Специальное значение в [BaseHook.targetPackages] — применять хук во всех процессах. */
    const val ALL_PACKAGES = "*"

    /** Пакет системного фреймворка (system_server). */
    const val SYSTEM_FRAMEWORK = "android"

    /** Непрозрачность фона шторки Honor, 0..100 (процент, 0 = полностью прозрачно). */
    const val HONOR_SHADE_ALPHA_KEY = "hook_systemui_honor_transparent_shade_alpha"
    const val DEFAULT_HONOR_SHADE_ALPHA = 85

    /** Режим анимации карточки «О телефоне»: [ABOUT_VIDEO_MODE_VIDEO], [ABOUT_VIDEO_MODE_PHOTO] или [ABOUT_VIDEO_MODE_DOOM]. */
    const val ABOUT_VIDEO_MODE_KEY = "hook_about_phone_video_mode"
    const val ABOUT_VIDEO_MODE_VIDEO = "video"
    const val ABOUT_VIDEO_MODE_PHOTO = "photo"

    /** DOOM в карточке «О телефоне»: нативный PureDOOM, кадры вместо видео. */
    const val ABOUT_VIDEO_MODE_DOOM = "doom"

    /** Путь файла замены видео «О телефоне» (копия из приложения или adb push). */
    const val ABOUT_VIDEO_PATH_KEY = "hook_about_phone_video_path"
    const val DEFAULT_ABOUT_VIDEO_PATH = "/sdcard/Download/honortool/banner.mp4"

}
