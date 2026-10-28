package com.fantom3031.honortool.hooks.aod

import com.fantom3031.honortool.R
import com.fantom3031.honortool.core.BaseHook
import com.fantom3031.honortool.core.HookPrefs
import com.fantom3031.honortool.core.XLog

/**
 * Длительность показа Always-On Display.
 *
 * AodApHost.scheduleStandbyTimeoutTask гасит экран через
 * SwingConfig.getNudgeDisplayTime() секунд. Хук подменяет геттер,
 * значение читается при каждом пробуждении — изменение применяется
 * без перезапуска процесса AOD.
 */
object AodDisplayTimeHook : BaseHook() {

    const val ID = "aod_display_time"
    const val DURATION_KEY = "aod_display_duration"
    const val DEFAULT_DURATION = 10
    const val MIN_DURATION = 5

    /** Верхний предел при ручном вводе; ползунок в UI ограничен 60 с. */
    const val MAX_DURATION = 600

    override val id = ID
    override val titleRes = R.string.aod_display_time_title
    override val descriptionRes = R.string.aod_display_time_description
    override val targetPackages = setOf("com.hihonor.aod")
    override val enabledByDefault = false

    override fun onHook() {
        val config = findClass("com.hihonor.aod.utils.SwingConfig")
        config.replace("getNudgeDisplayTime") {
            HookPrefs.getInt(DURATION_KEY, DEFAULT_DURATION).coerceIn(MIN_DURATION, MAX_DURATION)
        } ?: XLog.w("[$id] getNudgeDisplayTime не найден")
        XLog.i("[$id] длительность показа AOD переопределена")
    }
}
