package com.fantom3031.honortool.ui

import com.fantom3031.honortool.hooks.settings.AboutDeviceCustomHook
import com.fantom3031.honortool.hooks.settings.AboutPhoneVideoHook
import com.fantom3031.honortool.hooks.systemui.HonorShadeTransparentHook

/**
 * Твики, у которых есть свой экран настроек.
 *
 * Добавляя такой твик, допишите его id сюда и добавьте ветку в
 * `AppRoot` → `Route.HookSettings`.
 */
object HookDetails {

    private val withDetails = setOf(
        AboutPhoneVideoHook.ID,
        AboutDeviceCustomHook.ID,
        HonorShadeTransparentHook.ID,
        com.fantom3031.honortool.hooks.systemui.ScreenshotWatermarkHook.ID,
        com.fantom3031.honortool.hooks.launcher.SelectedFolderSlotsHook.ID,
        com.fantom3031.honortool.hooks.aod.AodDisplayTimeHook.ID,
        com.fantom3031.honortool.hooks.aod.AodBrightnessHook.ID,
    )

    fun hasDetails(hookId: String): Boolean = hookId in withDetails
}
