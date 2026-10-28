package com.fantom3031.honortool.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.fantom3031.honortool.R
import com.fantom3031.honortool.core.ImageKeys
import com.fantom3031.honortool.hooks.settings.AboutDeviceCustomHook
import com.fantom3031.honortool.hooks.settings.AboutPhoneVideoHook
import com.fantom3031.honortool.hooks.systemui.HonorShadeTransparentHook
import com.fantom3031.honortool.settings.ImageStore
import com.fantom3031.honortool.ui.detail.HonorShadeTransparentScreen
import com.fantom3031.honortool.ui.detail.AboutPhoneVideoScreen

/** Экраны приложения. */
private sealed interface Route {
    data object Apps : Route
    data class AppTweaks(val app: TargetApp) : Route
    data class HookSettings(val hookId: String) : Route
    data class BannerCrop(val uri: Uri) : Route
}

/**
 * Навигация: список приложений → твики приложения → настройки твика.
 * Стек простой, поэтому обходимся без navigation-compose.
 */
@Composable
fun AppRoot() {
    val context = LocalContext.current
    val stack = remember { mutableStateListOf<Route>(Route.Apps) }

    fun push(route: Route) = stack.add(route)
    fun pop() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
    }

    val bannerPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) push(Route.BannerCrop(uri))
    }

    BackHandler(enabled = stack.size > 1) { pop() }

    when (val route = stack.last()) {
        Route.Apps -> AppsScreen(onOpenApp = { push(Route.AppTweaks(it)) })

        is Route.AppTweaks -> AppHooksScreen(
            app = route.app,
            onBack = ::pop,
            onOpenHook = { hook -> push(Route.HookSettings(hook.id)) }
        )

        is Route.HookSettings -> when (route.hookId) {
            com.fantom3031.honortool.hooks.launcher.SelectedFolderSlotsHook.ID ->
                com.fantom3031.honortool.ui.detail.SelectedFoldersScreen(onBack = ::pop)
            com.fantom3031.honortool.hooks.aod.AodDisplayTimeHook.ID ->
                com.fantom3031.honortool.ui.detail.AodDisplayTimeScreen(onBack = ::pop)
            com.fantom3031.honortool.hooks.aod.AodBrightnessHook.ID ->
                com.fantom3031.honortool.ui.detail.AodBrightnessScreen(onBack = ::pop)
            HonorShadeTransparentHook.ID -> HonorShadeTransparentScreen(onBack = ::pop)
            com.fantom3031.honortool.hooks.systemui.ScreenshotWatermarkHook.ID ->
                com.fantom3031.honortool.ui.detail.ScreenshotWatermarkScreen(onBack = ::pop)

            AboutDeviceCustomHook.ID -> com.fantom3031.honortool.ui.detail.AboutDeviceCustomScreen(
                onBack = ::pop
            )

            AboutPhoneVideoHook.ID -> AboutPhoneVideoScreen(
                onBack = ::pop,
                onPickPhoto = {
                    bannerPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                }
            )

            else -> LaunchedEffect(route.hookId) { pop() }
        }

        is Route.BannerCrop -> CropScreen(
            uri = route.uri,
            targetWidth = AboutPhoneVideoHook.BANNER_WIDTH,
            targetHeight = AboutPhoneVideoHook.BANNER_HEIGHT,
            onCancel = ::pop,
            onCropped = { bitmap ->
                val saved = ImageStore.save(context, ImageKeys.ABOUT_PHONE_ROM, bitmap)
                pop()
                Toast.makeText(
                    context,
                    if (saved) R.string.image_saved else R.string.image_save_failed,
                    Toast.LENGTH_LONG
                ).show()
            }
        )
    }
}
