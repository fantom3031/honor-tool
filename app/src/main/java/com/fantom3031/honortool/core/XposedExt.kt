package com.fantom3031.honortool.core

import java.lang.reflect.Constructor
import java.lang.reflect.Executable
import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * Параметры вызова перехваченного метода / конструктора для хуков BaseHook.
 */
class HookParam(
    val executable: Executable,
    val thisObject: Any?,
    val args: Array<Any?>,
) {
    val method: Executable get() = executable

    var result: Any? = null
        set(value) {
            field = value
            hasResult = true
        }

    var throwable: Throwable? = null
        set(value) {
            field = value
            hasThrowable = true
        }

    var hasResult = false
        private set

    var hasThrowable = false
        private set

    /** Аргумент хукнутого метода по индексу с приведением типа. */
    @Suppress("UNCHECKED_CAST")
    fun <T> arg(index: Int): T? = args.getOrNull(index) as? T

    /** Заменить аргумент хукнутого метода. */
    fun setArg(index: Int, value: Any?) {
        args[index] = value
    }

    /** Результат хукнутого метода с приведением типа. */
    @Suppress("UNCHECKED_CAST")
    fun <T> resultAs(): T? = result as? T
}

/*
 * Рефлексивные обёртки для работы с приватными полями и методами.
 */

private fun findFieldRecursive(clazz: Class<*>, fieldName: String): Field {
    var c: Class<*>? = clazz
    while (c != null && c != Any::class.java) {
        try {
            val f = c.getDeclaredField(fieldName)
            f.isAccessible = true
            return f
        } catch (_: NoSuchFieldException) {
            c = c.superclass
        }
    }
    throw NoSuchFieldException("Field $fieldName not found in hierarchy of ${clazz.name}")
}

/** Значение поля объекта по имени. */
fun Any.getField(name: String): Any? =
    findFieldRecursive(this.javaClass, name).get(this)

/** Значение поля объекта с приведением типа; null, если тип не совпал или поля нет. */
@Suppress("UNCHECKED_CAST")
fun <T> Any.getFieldAs(name: String): T? = try {
    getField(name) as? T
} catch (_: Throwable) {
    null
}

/** Записать значение в поле объекта. */
fun Any.setField(name: String, value: Any?) {
    findFieldRecursive(this.javaClass, name).set(this, value)
}

/** Значение статического поля класса. */
fun Class<*>.getStaticField(name: String): Any? =
    findFieldRecursive(this, name).get(null)

/** Записать значение в статическое поле класса. */
fun Class<*>.setStaticField(name: String, value: Any?) {
    findFieldRecursive(this, name).set(null, value)
}

/** Вызвать метод объекта с подбором параметров. */
fun Any.callMethod(name: String, vararg args: Any?): Any? {
    val method = findMatchingMethod(this.javaClass, name, args)
    return method.invoke(this, *args)
}

/** Вызвать метод объекта с точным указанием типов параметров. */
fun Any.callMethodExact(name: String, parameterTypes: Array<Class<*>>, vararg args: Any?): Any? {
    var c: Class<*>? = this.javaClass
    while (c != null && c != Any::class.java) {
        try {
            val m = c.getDeclaredMethod(name, *parameterTypes)
            m.isAccessible = true
            return m.invoke(this, *args)
        } catch (_: NoSuchMethodException) {
            c = c.superclass
        }
    }
    throw NoSuchMethodException("Method $name with exact types not found in ${this.javaClass.name}")
}

/** Вызвать статический метод класса. */
fun Class<*>.callStaticMethod(name: String, vararg args: Any?): Any? {
    val method = findMatchingMethod(this, name, args)
    return method.invoke(null, *args)
}

/** Создать экземпляр класса. */
fun Class<*>.newInstance(vararg args: Any?): Any {
    for (ctor in declaredConstructors) {
        if (isParamTypesMatch(ctor.parameterTypes, args)) {
            ctor.isAccessible = true
            return ctor.newInstance(*args)
        }
    }
    throw NoSuchMethodException("Matching constructor not found in $name")
}

private fun findMatchingMethod(clazz: Class<*>, name: String, args: Array<out Any?>): Method {
    var c: Class<*>? = clazz
    while (c != null && c != Any::class.java) {
        for (m in c.declaredMethods) {
            if (m.name == name && isParamTypesMatch(m.parameterTypes, args)) {
                m.isAccessible = true
                return m
            }
        }
        c = c.superclass
    }
    throw NoSuchMethodException("Matching method $name not found in ${clazz.name} hierarchy")
}

private fun isParamTypesMatch(paramTypes: Array<Class<*>>, args: Array<out Any?>): Boolean {
    if (paramTypes.size != args.size) return false
    for (i in paramTypes.indices) {
        val arg = args[i] ?: continue
        val p = paramTypes[i]
        if (!p.isPrimitive && !p.isInstance(arg)) return false
        if (p.isPrimitive && !isPrimitiveBoxCompatible(p, arg)) return false
    }
    return true
}

private fun isPrimitiveBoxCompatible(primitive: Class<*>, obj: Any): Boolean = when (primitive) {
    java.lang.Boolean.TYPE -> obj is Boolean
    java.lang.Integer.TYPE -> obj is Int
    java.lang.Long.TYPE -> obj is Long
    java.lang.Float.TYPE -> obj is Float
    java.lang.Double.TYPE -> obj is Double
    java.lang.Byte.TYPE -> obj is Byte
    java.lang.Short.TYPE -> obj is Short
    java.lang.Character.TYPE -> obj is Char
    else -> false
}
