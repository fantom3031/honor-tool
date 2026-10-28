package com.fantom3031.honortool.core

import android.content.Context
import com.fantom3031.honortool.R

/**
 * Область действия модуля — список приложений, которые LSPosed предлагает выбрать.
 *
 * Массив `@array/module_scope` генерируется при сборке из `app/module-scope.txt`
 * задачей `generateXposedMetadata`.
 */
object ModuleScope {

    fun packages(context: Context): List<String> = try {
        context.resources.getStringArray(R.array.module_scope).toList()
    } catch (t: Throwable) {
        XLog.e("Не удалось прочитать @array/module_scope", t)
        emptyList()
    }
}
