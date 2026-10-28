package com.fantom3031.honortool.hooks.settings

import android.content.Context
import android.widget.TextView
import com.fantom3031.honortool.R
import com.fantom3031.honortool.core.BaseHook
import com.fantom3031.honortool.core.HookParam
import com.fantom3031.honortool.core.HookPrefs
import com.fantom3031.honortool.core.XLog

/**
 * Подмена текстовых значений на экране «О телефоне».
 *
 * Контроллеры с `getSummary()` (текст читается через метод):
 * модель, прошивка, модем, ядро, сборка, MagicOS, IMEI, MEID, SELinux, ПЗУ.
 *
 * Контроллеры с другими методами:
 * имя устройства — `DeviceNameController.getDeviceName(Context)`.
 *
 * Ключ настройки: `about_device_<field>_value` (пустая = не подменять).
 */
object AboutDeviceCustomHook : BaseHook() {

    const val ID = "settings_about_device_custom"

    private const val SETTINGS_PACKAGE = "com.android.settings"
    private const val PREF_PREFIX = "about_device_"

    private data class Field(
        val controllerClass: String,
        val key: String,
        val labelRes: Int
    )

    /** Контроллеры с getSummary() — верифицированы по dex. */
    private val summaryFields = listOf(
        Field("com.android.settings.deviceinfo.DeviceModelController", "model", R.string.about_field_model),
        Field("com.android.settings.deviceinfo.FirmwareVersionController", "firmware", R.string.about_field_firmware),
        Field("com.android.settings.deviceinfo.BasebandVersionController", "baseband", R.string.about_field_baseband),
        Field("com.android.settings.deviceinfo.BuildNumberController", "build", R.string.about_field_build),
        Field("com.android.settings.deviceinfo.MagicVersionController", "magic", R.string.about_field_magic),
        Field("com.android.settings.deviceinfo.InternalStorageController", "storage", R.string.about_field_storage),
        Field("com.android.settings.deviceinfo.DeviceImeiController", "imei", R.string.about_field_imei),
        Field("com.android.settings.deviceinfo.DeviceMeidController", "meid", R.string.about_field_meid),
        Field("com.android.settings.deviceinfo.SelinuxStatusController", "selinux", R.string.about_field_selinux),
    )

    override val id: String = ID

    override val titleRes: Int = R.string.hook_about_device_custom_title

    override val descriptionRes: Int = R.string.hook_about_device_custom_description

    override val targetPackages: Set<String> = setOf(SETTINGS_PACKAGE)

    override val enabledByDefault: Boolean = false

    private fun valueFor(key: String): String? =
        HookPrefs.getString("${PREF_PREFIX}${key}_value", "")?.takeIf { it.isNotBlank() }

    /**
     * ПЗУ (InternalStorageController) используется не только на экране «О телефоне» —
     * его getSummary формирует и подписи разделов настроек. Подменяем значение
     * только когда вызов идёт со screens deviceinfo.
     */
    private fun isDeviceInfoCall(): Boolean =
        Thread.currentThread().stackTrace.any {
            it.className.contains("deviceinfo", ignoreCase = true)
        }

    override fun onHook() {
        var hooked = 0

        // 1. Контроллеры с getSummary() — подменяем результат.
        for (field in summaryFields) {
            val controllerClass = findClassOrNull(field.controllerClass) ?: continue
            controllerClass.hookAllAfter("getSummary") { param ->
                val value = valueFor(field.key) ?: return@hookAllAfter
                if (field.key == "storage" && !isDeviceInfoCall()) return@hookAllAfter
                param.result = value
            }
            hooked++
        }

        hookCpuAndRamCard()

        // 2. Стандартные строки — перехват setSummary по ключу preference.
        // Читаем mKey напрямую (HnCardPreference может не иметь getKey()).
        val keyMap = mapOf(
            "kernel_version" to "kernel",
            "device_cpu_and_storage" to "cpu",
            "device_feature_info" to "ram",
            "device_ram" to "ram",
            "device_memory" to "ram",
        )
        val hookAction: (HookParam) -> Unit = hookBefore@{ param ->
            val pref = param.thisObject ?: return@hookBefore
            val key = pref.fieldOrNull("mKey") as? String ?: return@hookBefore
            val fieldKey = keyMap[key] ?: return@hookBefore
            val value = valueFor(fieldKey) ?: return@hookBefore
            param.args[0] = value
        }

        // Стандартный + Honor-овские HwPreference и HnCardPreference.
        findClassOrNull("androidx.preference.Preference")?.hookAllBefore("setSummary", hookAction)
        findClassOrNull("com.hihonor.settingslib.preference.HwPreference")
            ?.hookAllBefore("setSummary", hookAction)
        findClassOrNull("com.hihonor.uikit.hwrecyclerview.card.preference.HnCardPreference")
            ?.hookAllBefore("setSummary", hookAction)

        if (hooked > 0) {
            XLog.i("[$id] перехвачено контроллеров: $hooked + setSummary по ключу")
        } else {
            XLog.w("[$id] контроллеры deviceinfo не найдены")
        }
    }

    private fun hookCpuAndRamCard() {
        val card = findClassOrNull("com.android.settings.deviceinfo.DeviceCpuAndStoragePreference")
            ?: run {
                XLog.w("[$id] карточка CPU/RAM не найдена")
                return
            }

        // Settings 14.0.0.210: C(int, String) формирует CPU для кеша и AsyncTask,
        // F() возвращает отформатированную RAM; обе ветки обходят Preference.summary.
        card.hookAfter("C", Int::class.javaPrimitiveType, String::class.java) { param ->
            valueFor("cpu")?.let { param.result = it }
        }
        card.hookAfter("F") { param ->
            valueFor("ram")?.let { param.result = it }
        }
        // CPU может быть взят из кеша h до завершения AsyncTask. RAM Turbo
        // переписывает поле e в K(), поэтому применяем окончательный текст после него.
        for (method in listOf("onBindViewHolder", "onResume", "K")) {
            card.hookAllAfter(method) { param ->
                val self = param.thisObject ?: return@hookAllAfter
                applyCpuAndRamText(self)
            }
        }
        // L(String) — финальная запись CPU из onPostExecute, не глобальный setText.
        card.hookBefore("L", String::class.java) { param ->
            valueFor("cpu")?.let { param.args[0] = it }
        }
        XLog.i("[$id] CPU/RAM: DeviceCpuAndStoragePreference C/F/bind/K/L установлены")
    }

    private fun applyCpuAndRamText(card: Any) {
        valueFor("cpu")?.let { value ->
            (card.fieldOrNull("d") as? TextView)?.text = value
        }
        valueFor("ram")?.let { value ->
            (card.fieldOrNull("e") as? TextView)?.text = value
        }
    }

    /** Все поля для UI: key → labelRes. */
    val allFields: List<Pair<String, Int>>
        get() = summaryFields.map { it.key to it.labelRes } +
            listOf(
                "kernel" to R.string.about_field_kernel,
                "cpu" to R.string.about_field_cpu,
                "ram" to R.string.about_field_ram,
            )
}
