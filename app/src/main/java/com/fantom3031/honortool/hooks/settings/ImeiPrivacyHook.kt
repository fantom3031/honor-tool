package com.fantom3031.honortool.hooks.settings

import com.fantom3031.honortool.R
import com.fantom3031.honortool.core.BaseHook
import com.fantom3031.honortool.core.XLog

/**
 * Подмена IMEI/MEID/DeviceId нулями в экранах настроек.
 *
 * «О телефоне» и статус SIM читают идентификаторы через TelephonyManager
 * прямо в процессе com.android.settings — хук подменяет возвращаемые
 * значения на 15 нулей, поэтому на экране печатается заглушка.
 *
 * Затрагивает только отображение внутри Настроек: другие приложения
 * и системные службы получают реальные значения.
 */
object ImeiPrivacyHook : BaseHook() {

    const val ID = "settings_imei_privacy"

    private const val SETTINGS_PACKAGE = "com.android.settings"
    private const val TELEPHONY_MANAGER_CLASS = "android.telephony.TelephonyManager"

    /** Заглушка вместо идентификатора. */
    private const val FAKE_IMEI = "000000000000000"

    override val id: String = ID

    override val titleRes: Int = R.string.hook_imei_privacy_title

    override val descriptionRes: Int = R.string.hook_imei_privacy_description

    override val targetPackages: Set<String> = setOf(SETTINGS_PACKAGE)

    override val enabledByDefault: Boolean = true

    override fun onHook() {
        val telephony = findClassOrNull(TELEPHONY_MANAGER_CLASS) ?: run {
            XLog.w("[$id] $TELEPHONY_MANAGER_CLASS не найден")
            return
        }

        // Все перегрузки: getImei(), getImei(slot), getDeviceId(), getDeviceId(slot), getMeid()...
        for (method in listOf("getImei", "getDeviceId", "getMeid")) {
            val unhooks = telephony.replaceAll(method) { FAKE_IMEI }
            if (unhooks.isNotEmpty()) {
                XLog.i("[$id] TelephonyManager.$method -> $FAKE_IMEI (${unhooks.size} перегрузок)")
            } else {
                XLog.d("[$id] метод $method не найден (нормально для этой версии Android)")
            }
        }
    }
}
