package com.fantom3031.honortool.core

/**
 * Определение того, активирован ли модуль.
 *
 * Методы этого объекта подменяются самим модулем ([HookEntry.hookSelf]) в процессе
 * приложения. Если LSPosed не активировал модуль, подмены не происходит и методы
 * возвращают значения по умолчанию.
 */
object ModuleStatus {

    @JvmStatic
    fun isActive(): Boolean = ServiceBridge.isConnected

    @JvmStatic
    fun xposedApiVersion(): Int = ServiceBridge.xposedService?.apiVersion ?: -1

    @JvmStatic
    fun frameworkName(): String = ServiceBridge.frameworkName ?: "unknown"
}
