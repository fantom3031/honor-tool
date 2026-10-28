package com.fantom3031.honortool.ui

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.graphics.drawable.toBitmap
import com.fantom3031.honortool.R
import com.fantom3031.honortool.core.BaseHook
import com.fantom3031.honortool.core.Constants
import com.fantom3031.honortool.core.HookRegistry
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/** Приложение с твиками — элемент главного экрана. */
data class TargetApp(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap?,
    val hooks: List<BaseHook>,
    val installed: Boolean
)

object TargetApps {

    private const val ICON_SIZE_PX = 144

    /** Понятные названия для пакетов, у которых системное имя ничего не говорит. */
    private val labelOverrides = mapOf(
        Constants.SYSTEM_FRAMEWORK to R.string.package_system_framework,
        Constants.ALL_PACKAGES to R.string.package_all_apps,
    )

    /** Пакеты-плагины, которые в UI объединяются с родительским приложением. */
    private val packageAliases = mapOf(
        "system_server" to Constants.SYSTEM_FRAMEWORK
    )

    fun load(context: Context): List<TargetApp> {
        val byPackage = linkedMapOf<String, MutableList<BaseHook>>()
        HookRegistry.hooks.forEach { hook ->
            hook.targetPackages.forEach { rawPackage ->
                val packageName = packageAliases[rawPackage] ?: rawPackage
                val list = byPackage.getOrPut(packageName) { mutableListOf() }
                if (hook !in list) list.add(hook)
            }
        }

        return byPackage.map { (packageName, hooks) ->
            val info = applicationInfo(context, packageName)
            TargetApp(
                packageName = packageName,
                label = labelOverrides[packageName]?.let { context.getString(it) }
                    ?: info?.let { context.packageManager.getApplicationLabel(it).toString() }
                    ?: packageName,
                icon = loadIcon(context, packageName),
                hooks = hooks.sortedBy { context.getString(it.titleRes) },
                installed = info != null || packageName == Constants.ALL_PACKAGES
            )
        }.sortedBy { it.label.lowercase() }
    }

    private fun applicationInfo(context: Context, packageName: String) = try {
        context.packageManager.getApplicationInfo(packageName, 0)
    } catch (_: PackageManager.NameNotFoundException) {
        null
    } catch (_: Throwable) {
        null
    }

    private fun loadIcon(context: Context, packageName: String): ImageBitmap? = try {
        val drawable = context.packageManager.getApplicationIcon(packageName)
        drawable.toBitmap(ICON_SIZE_PX, ICON_SIZE_PX).asImageBitmap()
    } catch (_: Throwable) {
        null
    }
}
