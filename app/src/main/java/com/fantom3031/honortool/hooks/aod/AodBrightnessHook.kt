package com.fantom3031.honortool.hooks.aod

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.fantom3031.honortool.R
import com.fantom3031.honortool.core.BaseHook
import com.fantom3031.honortool.core.HookPrefs
import com.fantom3031.honortool.core.XLog

/**
 * Яркость Always-On Display.
 *
 * DozeService.updateDozeScreenBrightness вычисляет уровень (штатные значения
 * в процессе — около 3–7) и передаёт в ApSolutionUtil.setDozeScreenBrightness(int)
 * → PowerManagerEx.setAodBrightness. Процентные масштабы на такой шкале
 * незаметны, поэтому твик задаёт абсолютный уровень: 0 = авто (штатная),
 * иначе фиксированная яркость 1–100.
 *
 * Динамика: приложение рассылает broadcast при смене ползунка — яркость
 * применяется сразу.
 */
object AodBrightnessHook : BaseHook() {

    const val ID = "aod_brightness"
    const val BRIGHTNESS_KEY = "aod_brightness_level"
    const val SYNC_ACTION = "com.fantom3031.honortool.AOD_BRIGHTNESS_SYNC"
    const val EXTRA_LEVEL = "level"

    const val AUTO = 0
    const val DEFAULT_LEVEL = 0
    const val MIN_LEVEL = 1
    const val MAX_LEVEL = 10

    private const val AOD_PACKAGE = "com.hihonor.aod"

    @Volatile private var lastLevel = 0
    @Volatile private var lastApplied = -1
    @Volatile private var receiverRegistered = false

    override val id = ID
    override val titleRes = R.string.aod_brightness_title
    override val descriptionRes = R.string.aod_brightness_description
    override val targetPackages = setOf(AOD_PACKAGE)
    override val enabledByDefault = false

    override fun onHook() {
        findClass("com.hihonor.aod.doze.ApSolutionUtil").hookBefore(
            "setDozeScreenBrightness",
            Int::class.javaPrimitiveType
        ) { p ->
            val level = p.args[0] as? Int ?: return@hookBefore
            if (level > 0) lastLevel = level
            val override = HookPrefs.getInt(BRIGHTNESS_KEY, DEFAULT_LEVEL)
            if (override <= AUTO) return@hookBefore
            val fixed = override.coerceIn(MIN_LEVEL, MAX_LEVEL)
            if (p.args[0] != fixed) {
                p.args[0] = fixed
                lastApplied = fixed
                if (lastLevel != lastApplied) {
                    XLog.d("[$id] яркость: штатно $level → задано $fixed")
                }
            }
        } ?: XLog.w("[$id] setDozeScreenBrightness не найден")
        registerSyncReceiver()
        XLog.i("[$id] яркость AOD: хук установлен")
    }

    private fun registerSyncReceiver() {
        if (receiverRegistered) return
        val context = currentContext() ?: return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val level = intent.getIntExtra(EXTRA_LEVEL, DEFAULT_LEVEL)
                applyNow(level)
            }
        }
        try {
            context.registerReceiver(
                receiver, IntentFilter(SYNC_ACTION), Context.RECEIVER_EXPORTED
            )
            receiverRegistered = true
            XLog.i("[$id] приёмник динамической яркости готов")
        } catch (t: Throwable) {
            XLog.e("[$id] не удалось зарегистрировать приёмник яркости", t)
        }
    }

    /** Уровень > 0 — применить немедленно; авто (0) — яркость вернётся сама со следующим циклом прошивки. */
    private fun applyNow(level: Int) {
        if (level <= AUTO) {
            XLog.d("[$id] выбран авто — вернётся штатная яркость со следующим обновлением")
            return
        }
        runCatching {
            Class.forName("com.hihonor.aod.doze.ApSolutionUtil")
                .getMethod("setDozeScreenBrightness", Int::class.javaPrimitiveType)
                .invoke(null, level)
            lastApplied = level
            XLog.d("[$id] яркость применена сразу: $level")
        }.onFailure { XLog.e("[$id] не удалось применить яркость сразу", it) }
    }

    private fun currentContext(): Context? = try {
        val cls = Class.forName("com.hihonor.aod.utils.GlobalContext")
        cls.getMethod("getContext").invoke(null) as? Context
    } catch (_: Throwable) {
        null
    }
}
