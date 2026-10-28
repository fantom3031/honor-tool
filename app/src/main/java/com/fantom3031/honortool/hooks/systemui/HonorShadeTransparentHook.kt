package com.fantom3031.honortool.hooks.systemui

import com.fantom3031.honortool.R
import com.fantom3031.honortool.core.BaseHook
import com.fantom3031.honortool.core.Constants
import com.fantom3031.honortool.core.HookPrefs
import com.fantom3031.honortool.core.XLog

/**
 * Светлая шторка уведомлений для Honor (MagicOS 8, Android 14).
 *
 * Серый фон шторки рисует `com.android.magic.blur.WindowBlurView` через
 * `setBackgroundColor(0xff333333)`. Хук подменяет цвет на светлую полупрозрачную тонировку.
 *
 * Прочее не трогаем: `setBackground` с общим `ColorDrawable` темнил плитки (drawable
 * общий), а размытие окна через `ViewRootImpl` в этой прошивке не сработало.
 */
object HonorShadeTransparentHook : BaseHook() {

    const val ID = "systemui_honor_transparent_shade"

    override val id: String = ID
    override val titleRes: Int = R.string.hook_honor_transparent_shade_title
    override val descriptionRes: Int = R.string.hook_honor_transparent_shade_description

    override val targetPackages: Set<String> = setOf("com.android.systemui")

    override val enabledByDefault: Boolean = false

    private const val BLUR_VIEW = "com.android.magic.blur.WindowBlurView"

    /**
     * Цвет тонировки фона шторки: оригинальный серый `0x333333` из прошивки.
     * Альфа берётся из настроек (0..100 %), поэтому ползунок меняет только прозрачность.
     * Значение читается при каждом вызове, применяется без перезагрузки.
     */
    private const val TINT_RGB = 0x00333333

    private fun tintColor(): Int {
        val percent = HookPrefs.getInt(Constants.HONOR_SHADE_ALPHA_KEY, Constants.DEFAULT_HONOR_SHADE_ALPHA)
            .coerceIn(0, 100)
        val alpha = percent * 255 / 100
        return (alpha shl 24) or TINT_RGB
    }

    override fun onHook() {
        val blurView = findClassOrNull(BLUR_VIEW) ?: run {
            XLog.w("[$id] класс $BLUR_VIEW не найден")
            return
        }

        blurView.hookBefore("setBackgroundColor", Int::class.javaPrimitiveType) { param ->
            param.args[0] = tintColor()
        }
        XLog.i("[$id] тонировка WindowBlurView установлена")
    }
}
