package com.fantom3031.honortool.core

/** Ключи картинок в хранилище, общие для UI приложения и хуков. */
object ImageKeys {

    /** Картинка карточки «О телефоне» в настройках. */
    const val ABOUT_PHONE_ROM = "about_phone_rom15"

    /** PNG-картинка водяного знака для скриншотов. */
    const val SCREENSHOT_WATERMARK = "screenshot_watermark_image"

    /** Метка времени последнего изменения — по ней инвалидируется кэш в хуке. */
    fun updatedKey(key: String): String = "${key}_updated"
}
