package com.fantom3031.honortool.hooks

import com.fantom3031.honortool.core.BaseHook
import com.fantom3031.honortool.hooks.settings.AboutDeviceCustomHook
import com.fantom3031.honortool.hooks.settings.AboutPhoneVideoHook
import com.fantom3031.honortool.hooks.settings.ImeiPrivacyHook
import com.fantom3031.honortool.hooks.systemui.HonorShadeTransparentHook
import com.fantom3031.honortool.hooks.systemui.LockScreenChargingHook

/**
 * Список подключённых хуков — единственное место, куда нужно добавить свой хук.
 *
 * Порядок в списке = порядок применения внутри одного процесса.
 *
 * Чек-лист добавления хука:
 *  1. Создать файл в `app/src/main/java/com/fantom3031/honortool/hooks/<приложение>/`.
 *  2. Унаследовать [BaseHook] (проще всего — `object`).
 *  3. Добавить объект в [all].
 *  4. Добавить целевой пакет в `app/module-scope.txt`,
 *     чтобы LSPosed предложил это приложение при выборе области действия.
 */
object HookModules {

    val all: List<BaseHook>
        get() = listOf(
            AboutPhoneVideoHook,
            AboutDeviceCustomHook,
            ImeiPrivacyHook,
            HonorShadeTransparentHook,
            LockScreenChargingHook,
            com.fantom3031.honortool.hooks.systemui.StatusBarSecondsHook,
            com.fantom3031.honortool.hooks.systemui.ScreenshotWatermarkHook,
            com.fantom3031.honortool.hooks.launcher.SelectedFolderSlotsHook,
            com.fantom3031.honortool.hooks.launcher.RecentsGridHook,
            com.fantom3031.honortool.hooks.aod.AodDisplayTimeHook,
            com.fantom3031.honortool.hooks.aod.AodMediaCardHook,
            com.fantom3031.honortool.hooks.aod.AodBrightnessHook,
        )
}
