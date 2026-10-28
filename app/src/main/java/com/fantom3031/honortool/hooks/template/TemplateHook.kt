package com.fantom3031.honortool.hooks.template

import com.fantom3031.honortool.R
import com.fantom3031.honortool.core.BaseHook
import com.fantom3031.honortool.core.XLog
import com.fantom3031.honortool.core.callMethod
import com.fantom3031.honortool.core.getField

/**
 * Шаблон хука — скопируйте этот файл, переименуйте и правьте под своё приложение.
 *
 * Этот объект НЕ зарегистрирован в `HookModules.all`, поэтому ничего не делает.
 *
 * После копирования:
 *  1. Смените [id] (он же ключ настроек).
 *  2. Заведите строки названия и описания в `res/values/strings.xml`
 *     и `res/values-ru/strings.xml`, укажите их в [titleRes] и [descriptionRes].
 *  3. Укажите реальные пакеты в [targetPackages].
 *  4. Добавьте эти пакеты в `app/module-scope.txt`.
 *  5. Добавьте объект в `HookModules.all`.
 */
object TemplateHook : BaseHook() {

    override val id: String = "template"

    override val titleRes: Int = R.string.app_name

    override val targetPackages: Set<String> = setOf("com.example.app")

    override val enabledByDefault: Boolean = false

    override fun onHook() {
        // Класс может отсутствовать в другой версии приложения — проверяем.
        val target = findClassOrNull("com.example.app.SomeClass") ?: run {
            XLog.w("[$id] класс com.example.app.SomeClass не найден в $hookedPackage")
            return
        }

        // Прочитать/подменить результат метода.
        target.hookAfter("isPremium") { param ->
            param.result = true
        }

        // Изменить аргумент до вызова метода.
        target.hookBefore("setLimit", Int::class.java) { param ->
            param.args[0] = 100
        }

        // Полная замена реализации.
        target.replace("buildAdRequest") { null }

        // Все перегрузки метода сразу.
        target.hookAllAfter("onConfigLoaded") { param ->
            val config = param.thisObject?.getField("config")
            XLog.d("[$id] config = $config")
        }

        // Доступ к полям и методам через рефлексию.
        target.hookConstructorAfter { param ->
            param.thisObject?.callMethod("init")
        }

        // Код, которому нужен готовый Context приложения.
        afterApplicationCreated { app ->
            XLog.d("[$id] Application создан: ${app.packageName}")
        }
    }
}
