package com.fantom3031.honortool.core

import android.content.SharedPreferences

/**
 * Чтение настроек изнутри процессов с хуками.
 *
 * В современном LibXposed настройки читаются через RemotePreferences от XposedInterface.
 */
internal object HookPrefs {

    @Volatile
    private var remotePrefs: SharedPreferences? = null

    fun init(prefs: SharedPreferences?) {
        remotePrefs = prefs
    }

    private fun snapshot(): SharedPreferences? = remotePrefs

    fun isEnabled(hook: BaseHook): Boolean =
        snapshot()?.getBoolean(Constants.enabledKey(hook.id), hook.enabledByDefault)
            ?: hook.enabledByDefault

    fun getBoolean(key: String, default: Boolean): Boolean =
        snapshot()?.getBoolean(key, default) ?: default

    fun getInt(key: String, default: Int): Int =
        snapshot()?.getInt(key, default) ?: default

    fun getString(key: String, default: String): String =
        snapshot()?.getString(key, default) ?: default

    fun getStringSet(key: String, default: Set<String> = emptySet()): Set<String> =
        snapshot()?.getStringSet(key, default) ?: default

    /** Все настройки с указанным префиксом — для хуков со списком значений. */
    fun entriesWithPrefix(prefix: String): Map<String, Any?> {
        val store = snapshot() ?: return emptyMap()
        return try {
            store.all.filterKeys { it.startsWith(prefix) }
        } catch (t: Throwable) {
            XLog.e("Не удалось перечислить настройки с префиксом '$prefix'", t)
            emptyMap()
        }
    }
}
