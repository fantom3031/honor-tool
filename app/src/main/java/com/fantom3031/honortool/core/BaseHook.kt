package com.fantom3031.honortool.core

import android.os.Build
import androidx.annotation.StringRes
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Constructor
import java.lang.reflect.Executable
import java.lang.reflect.Member
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * Базовый класс для всех хуков (LibXposed modern API).
 *
 * Чтобы добавить хук, создайте наследника в пакете `com.fantom3031.honortool.hooks`,
 * зарегистрируйте его в [HookRegistry] и добавьте целевой пакет в `app/module-scope.txt`.
 *
 * Пример:
 * ```
 * object ExampleHook : BaseHook() {
 *     override val id = "example"
 *     override val titleRes = R.string.hook_example_title
 *     override val targetPackages = setOf("com.example.app")
 *
 *     override fun onHook() {
 *         findClass("com.example.app.Foo").hookAfter("bar", Int::class.java) { param ->
 *             param.result = 42
 *         }
 *     }
 * }
 * ```
 */
abstract class BaseHook {

    /** Стабильный идентификатор: используется как ключ настроек, менять нельзя. */
    abstract val id: String

    /** Название хука для списка в приложении. */
    @get:StringRes
    abstract val titleRes: Int

    /** Короткое описание того, что делает хук, или 0, если его нет. */
    @get:StringRes
    open val descriptionRes: Int = 0

    /**
     * Пакеты приложений, в процессах которых нужно применить хук.
     *
     * Используйте [Constants.SYSTEM_FRAMEWORK] для system_server и
     * [Constants.ALL_PACKAGES] — чтобы применять хук во всех процессах.
     */
    abstract val targetPackages: Set<String>

    /** Включён ли хук, пока пользователь не изменил настройку. */
    open val enabledByDefault: Boolean = true

    /** Минимальная поддерживаемая версия Android (API level). */
    open val minSdk: Int = Build.VERSION_CODES.O_MR1

    /** Максимальная поддерживаемая версия Android (API level). */
    open val maxSdk: Int = Int.MAX_VALUE

    internal var xposed: XposedInterface? = null
    internal var classLoaderInternal: ClassLoader? = null
    internal var hookedPackageInternal: String? = null

    /** ClassLoader приложения, в которое внедряется хук. */
    protected val classLoader: ClassLoader
        get() = requireNotNull(classLoaderInternal) { "classLoader доступен только внутри onHook()" }

    /** Пакет приложения, в которое внедряется хук. */
    protected val hookedPackage: String
        get() = requireNotNull(hookedPackageInternal) { "hookedPackage доступен только внутри onHook()" }

    /** Собственно логика хука. Исключения перехватываются и логируются. */
    protected abstract fun onHook()

    /** Подходит ли хук текущей версии Android. */
    open fun isSupported(sdkInt: Int = Build.VERSION.SDK_INT): Boolean = sdkInt in minSdk..maxSdk

    /** Применим ли хук к указанному пакету. */
    fun matches(packageName: String): Boolean =
        Constants.ALL_PACKAGES in targetPackages || packageName in targetPackages

    internal fun applyTo(xposedInstance: XposedInterface, pkgName: String, cl: ClassLoader) {
        this.xposed = xposedInstance
        this.hookedPackageInternal = pkgName
        this.classLoaderInternal = cl
        try {
            onHook()
            XLog.i("Хук '$id' применён к $pkgName")
        } catch (t: Throwable) {
            XLog.e("Хук '$id' упал на $pkgName", t)
        }
    }

    // ---------------------------------------------------------------------
    // Поиск классов
    // ---------------------------------------------------------------------

    /** Находит класс или бросает исключение (будет поймано в [applyTo]). */
    protected fun findClass(className: String, loader: ClassLoader = classLoader): Class<*> =
        Class.forName(className, false, loader)

    /** Находит класс или возвращает null, если его нет. */
    protected fun findClassOrNull(className: String, loader: ClassLoader = classLoader): Class<*>? =
        try {
            Class.forName(className, false, loader)
        } catch (_: Throwable) {
            null
        }

    /** Первый существующий класс из списка — удобно для разных версий прошивки. */
    protected fun findFirstClass(vararg classNames: String, loader: ClassLoader = classLoader): Class<*>? =
        classNames.firstNotNullOfOrNull { findClassOrNull(it, loader) }

    /**
     * Все неабстрактные реализации метода во всей иерархии класса.
     */
    protected fun Class<*>.methodsInHierarchy(
        name: String,
        returnType: Class<*>? = null
    ): List<Method> {
        val found = mutableListOf<Method>()
        var next: Class<*>? = this
        while (true) {
            val clazz = next ?: break
            if (clazz == Any::class.java) break

            val declared = try {
                clazz.declaredMethods
            } catch (t: Throwable) {
                XLog.e("[$id] не удалось прочитать методы ${clazz.name}", t)
                emptyArray()
            }
            declared.filterTo(found) { method ->
                method.name == name &&
                    (returnType == null || method.returnType == returnType) &&
                    !Modifier.isAbstract(method.modifiers)
            }
            next = try {
                clazz.superclass
            } catch (t: Throwable) {
                null
            }
        }
        return found
    }

    // ---------------------------------------------------------------------
    // Хуки методов
    // ---------------------------------------------------------------------

    fun interface Unhook {
        fun unhook()
    }

    /** Хук перед выполнением метода. */
    protected fun Class<*>.hookBefore(
        methodName: String,
        vararg parameterTypes: Any?,
        action: (HookParam) -> Unit
    ): Unhook? = hookMethod(this, methodName, parameterTypes) { exec ->
        hookBeforeExecutable(exec, action)
    }

    /** Хук после выполнения метода. */
    protected fun Class<*>.hookAfter(
        methodName: String,
        vararg parameterTypes: Any?,
        action: (HookParam) -> Unit
    ): Unhook? = hookMethod(this, methodName, parameterTypes) { exec ->
        hookAfterExecutable(exec, action)
    }

    /** Полная замена тела метода; результат лямбды становится результатом метода. */
    protected fun Class<*>.replace(
        methodName: String,
        vararg parameterTypes: Any?,
        action: (HookParam) -> Any?
    ): Unhook? = hookMethod(this, methodName, parameterTypes) { exec ->
        replaceExecutable(exec, action)
    }

    /** Метод всегда возвращает указанное значение. */
    protected fun Class<*>.returnConstant(
        methodName: String,
        value: Any?,
        vararg parameterTypes: Any?
    ): Unhook? = hookMethod(this, methodName, parameterTypes) { exec ->
        returnConstantExecutable(exec, value)
    }

    /** Метод становится пустым (ничего не делает). */
    protected fun Class<*>.doNothing(
        methodName: String,
        vararg parameterTypes: Any?
    ): Unhook? = hookMethod(this, methodName, parameterTypes) { exec ->
        doNothingExecutable(exec)
    }

    /**
     * Метод не выполняется вообще: возвращается нейтральное значение под его
     * тип (`null`, `false`, `0`).
     */
    protected fun Class<*>.skipAll(methodName: String): Set<Unhook> =
        hookAllBefore(methodName) { param -> param.result = neutralResult(param.method) }

    /** Хук всех перегрузок метода — до выполнения. */
    protected fun Class<*>.hookAllBefore(
        methodName: String,
        action: (HookParam) -> Unit
    ): Set<Unhook> = hookAllMethods(this, methodName) { exec ->
        hookBeforeExecutable(exec, action)
    }

    /** Хук всех перегрузок метода — после выполнения. */
    protected fun Class<*>.hookAllAfter(
        methodName: String,
        action: (HookParam) -> Unit
    ): Set<Unhook> = hookAllMethods(this, methodName) { exec ->
        hookAfterExecutable(exec, action)
    }

    /** Замена всех перегрузок метода. */
    protected fun Class<*>.replaceAll(
        methodName: String,
        action: (HookParam) -> Any?
    ): Set<Unhook> = hookAllMethods(this, methodName) { exec ->
        replaceExecutable(exec, action)
    }

    /** Хук конструктора после выполнения. */
    protected fun Class<*>.hookConstructorAfter(
        vararg parameterTypes: Any?,
        action: (HookParam) -> Unit
    ): Unhook? = hookConstructor(this, parameterTypes) { exec ->
        hookAfterExecutable(exec, action)
    }

    /** Хук конструктора до выполнения. */
    protected fun Class<*>.hookConstructorBefore(
        vararg parameterTypes: Any?,
        action: (HookParam) -> Unit
    ): Unhook? = hookConstructor(this, parameterTypes) { exec ->
        hookBeforeExecutable(exec, action)
    }

    /** Хук всех конструкторов до выполнения. */
    protected fun Class<*>.hookAllConstructorsBefore(
        action: (HookParam) -> Unit
    ): Set<Unhook> {
        val unhooks = mutableSetOf<Unhook>()
        for (ctor in declaredConstructors) {
            ctor.isAccessible = true
            hookBeforeExecutable(ctor, action)?.let { unhooks.add(it) }
        }
        return unhooks
    }

    /** Хук всех конструкторов после выполнения. */
    protected fun Class<*>.hookAllConstructorsAfter(
        action: (HookParam) -> Unit
    ): Set<Unhook> {
        val unhooks = mutableSetOf<Unhook>()
        for (ctor in declaredConstructors) {
            ctor.isAccessible = true
            hookAfterExecutable(ctor, action)?.let { unhooks.add(it) }
        }
        return unhooks
    }

    /**
     * Выполняет действие после создания Application целевого приложения —
     * удобно, когда нужен готовый Context.
     */
    protected fun afterApplicationCreated(action: (android.app.Application) -> Unit) {
        val appClass = findClassOrNull("android.app.Application") ?: return
        appClass.hookAfter("onCreate") { hookParam ->
            (hookParam.thisObject as? android.app.Application)?.let(action)
        }
    }

    /**
     * Подменяет уже найденный через рефлексию метод: он не выполняется,
     * а сразу возвращает [value].
     */
    protected fun Method.replaceWithConstant(value: Any?): Unhook? =
        returnConstantExecutable(this, value)

    // ---------------------------------------------------------------------
    // Чтение объектов целевого приложения
    // ---------------------------------------------------------------------

    /** Значение поля объекта или null, если поля нет. */
    protected fun Any.fieldOrNull(name: String): Any? = try {
        getField(name)
    } catch (t: Throwable) {
        XLog.d("[$id] нет поля $name в ${javaClass.name}: ${t.message}")
        null
    }

    /**
     * Результат вызова метода объекта или null, если метода нет или он упал.
     * Приватные методы тоже вызываются.
     */
    protected fun Any.callOrNull(name: String, vararg args: Any?): Any? = try {
        callMethod(name, *args)
    } catch (t: Throwable) {
        XLog.d("[$id] вызов $name у ${javaClass.name} не удался: ${t.message}")
        null
    }

    /** Значение, которое можно вернуть вместо вызова метода, не сломав вызывающий код. */
    protected fun neutralResult(member: Member?): Any? =
        when ((member as? Method)?.returnType) {
            null, Void.TYPE -> null
            Boolean::class.javaPrimitiveType -> false
            Int::class.javaPrimitiveType -> 0
            Long::class.javaPrimitiveType -> 0L
            Float::class.javaPrimitiveType -> 0f
            Double::class.javaPrimitiveType -> 0.0
            Short::class.javaPrimitiveType -> 0.toShort()
            Byte::class.javaPrimitiveType -> 0.toByte()
            Char::class.javaPrimitiveType -> '\u0000'
            else -> null
        }

    // ---------------------------------------------------------------------
    // Внутренняя реализация на базе XposedInterface
    // ---------------------------------------------------------------------

    private fun resolveTypes(types: Array<out Any?>, loader: ClassLoader): Array<Class<*>> {
        return types.map { t ->
            when (t) {
                is Class<*> -> t
                is String -> findClass(t, loader)
                else -> throw IllegalArgumentException("Неподдерживаемый тип параметра: $t")
            }
        }.toTypedArray()
    }

    private fun hookMethod(
        clazz: Class<*>,
        methodName: String,
        parameterTypes: Array<out Any?>,
        hookFunc: (Executable) -> Unhook?
    ): Unhook? = try {
        val resolved = resolveTypes(parameterTypes, clazz.classLoader ?: classLoader)
        var c: Class<*>? = clazz
        var method: Method? = null
        while (c != null && c != Any::class.java) {
            try {
                method = c.getDeclaredMethod(methodName, *resolved)
                break
            } catch (_: NoSuchMethodException) {
                c = c.superclass
            }
        }
        if (method == null) {
            throw NoSuchMethodException("Метод $methodName не найден в ${clazz.name}")
        }
        method.isAccessible = true
        hookFunc(method)
    } catch (t: Throwable) {
        XLog.e("[$id] не удалось хукнуть ${clazz.name}.$methodName", t)
        null
    }

    private fun hookConstructor(
        clazz: Class<*>,
        parameterTypes: Array<out Any?>,
        hookFunc: (Executable) -> Unhook?
    ): Unhook? = try {
        val resolved = resolveTypes(parameterTypes, clazz.classLoader ?: classLoader)
        val ctor = clazz.getDeclaredConstructor(*resolved)
        ctor.isAccessible = true
        hookFunc(ctor)
    } catch (t: Throwable) {
        XLog.e("[$id] не удалось хукнуть конструктор ${clazz.name}", t)
        null
    }

    private fun hookAllMethods(
        clazz: Class<*>,
        methodName: String,
        hookFunc: (Executable) -> Unhook?
    ): Set<Unhook> {
        val unhooks = mutableSetOf<Unhook>()
        var c: Class<*>? = clazz
        while (c != null && c != Any::class.java) {
            try {
                for (m in c.declaredMethods) {
                    if (m.name == methodName) {
                        m.isAccessible = true
                        hookFunc(m)?.let { unhooks.add(it) }
                    }
                }
            } catch (t: Throwable) {
                XLog.e("[$id] ошибка перебора методов ${c.name}", t)
            }
            c = c.superclass
        }
        return unhooks
    }

    private fun hookExecutable(
        executable: Executable,
        interceptor: (XposedInterface.Chain) -> Any?
    ): Unhook? {
        val x = xposed ?: run {
            XLog.e("[$id] XposedInterface не инициализирован для ${executable.name}")
            return null
        }
        return try {
            val handle = x.hook(executable)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept { chain -> interceptor(chain) }
            Unhook { handle.unhook() }
        } catch (t: Throwable) {
            XLog.e("[$id] Ошибка при установке хука на $executable", t)
            null
        }
    }

    private fun hookBeforeExecutable(
        executable: Executable,
        action: (HookParam) -> Unit
    ): Unhook? = hookExecutable(executable) { chain ->
        val args = chain.args.toTypedArray()
        val param = HookParam(chain.executable, chain.thisObject, args)
        try {
            action(param)
        } catch (t: Throwable) {
            XLog.e("[$id] Ошибка в hookBefore для $executable", t)
        }
        if (param.hasThrowable) {
            throw requireNotNull(param.throwable)
        }
        if (param.hasResult) {
            param.result
        } else if (chain.thisObject != null) {
            chain.proceedWith(chain.thisObject, param.args)
        } else {
            chain.proceed(param.args)
        }
    }

    private fun hookAfterExecutable(
        executable: Executable,
        action: (HookParam) -> Unit
    ): Unhook? = hookExecutable(executable) { chain ->
        val result = chain.proceed()
        val args = chain.args.toTypedArray()
        val param = HookParam(chain.executable, chain.thisObject, args)
        param.result = result
        try {
            action(param)
        } catch (t: Throwable) {
            XLog.e("[$id] Ошибка в hookAfter для $executable", t)
        }
        if (param.hasThrowable) {
            throw requireNotNull(param.throwable)
        }
        param.result
    }

    private fun replaceExecutable(
        executable: Executable,
        action: (HookParam) -> Any?
    ): Unhook? = hookExecutable(executable) { chain ->
        val args = chain.args.toTypedArray()
        val param = HookParam(chain.executable, chain.thisObject, args)
        try {
            action(param)
        } catch (t: Throwable) {
            XLog.e("[$id] Ошибка в replace для $executable", t)
            neutralResult(executable)
        }
    }

    private fun returnConstantExecutable(
        executable: Executable,
        value: Any?
    ): Unhook? = hookExecutable(executable) { value }

    private fun doNothingExecutable(
        executable: Executable
    ): Unhook? = hookExecutable(executable) { null }
}
