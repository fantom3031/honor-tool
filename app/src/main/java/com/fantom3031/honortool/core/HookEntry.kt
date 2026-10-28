package com.fantom3031.honortool.core

import com.fantom3031.honortool.BuildConfig
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface

/**
 * Точка входа модуля LibXposed. Указана в `META-INF/xposed/java_init.list`.
 */
class HookEntry : XposedModule() {

    override fun onModuleLoaded(param: XposedModuleInterface.ModuleLoadedParam) {
        XLog.setXposedInterface(this)
        if (param.isSystemServer) XLog.enableSystemServerFile()
        XLog.i("Модуль загружен: process=${param.processName}, systemServer=${param.isSystemServer}")
        modulePath = moduleApplicationInfo.sourceDir
        moduleNativeDir = moduleApplicationInfo.nativeLibraryDir

        runCatching {
            HookPrefs.init(getRemotePreferences(Constants.PREFS_NAME))
            XLog.i("RemotePreferences настроек подключены")
        }.onFailure { t ->
            XLog.e("Не удалось инициализировать RemotePreferences", t)
        }
        runCatching {
            HookImages.init(getRemotePreferences(Constants.IMAGE_PREFS_NAME))
        }.onFailure { XLog.e("Не удалось инициализировать настройки изображений", it) }
    }

    override fun onPackageReady(param: XposedModuleInterface.PackageReadyParam) {
        val packageName = param.packageName
        // "android" can also be loaded by an app process. Its loader does not
        // contain services.jar; framework hooks belong exclusively to the callback below.
        if (packageName == Constants.SYSTEM_FRAMEWORK) return
        val classLoader = param.classLoader

        if (packageName == BuildConfig.APPLICATION_ID) {
            hookSelf(classLoader)
            return
        }

        dispatchHooks(packageName, classLoader)
    }

    override fun onSystemServerStarting(param: XposedModuleInterface.SystemServerStartingParam) {
        // Классы WM/Audio лежат в system_server, а не в пакете "android"
        XLog.i("onSystemServerStarting: loader=${param.classLoader}")
        dispatchHooks(Constants.SYSTEM_FRAMEWORK, param.classLoader)
    }

    private fun dispatchHooks(packageName: String, classLoader: ClassLoader) {
        val hooks = HookRegistry.hooksFor(packageName)
        if (hooks.isEmpty()) return

        XLog.i("Загружены в $packageName, подходящих хуков: ${hooks.size}")

        for (hook in hooks) {
            val enabled = try {
                HookPrefs.isEnabled(hook)
            } catch (t: Throwable) {
                XLog.e("Не удалось прочитать включение '${hook.id}'", t)
                continue
            }
            XLog.i("Проверка хука '${hook.id}': enabled=$enabled")
            when {
                !hook.isSupported() ->
                    XLog.d("Хук '${hook.id}' пропущен: несовместимая версия Android")

                !enabled ->
                    XLog.d("Хук '${hook.id}' пропущен: отключён в настройках")

                else -> hook.applyTo(this, packageName, classLoader)
            }
        }
    }

    /** Подмена [ModuleStatus], чтобы UI приложения знал, что модуль активен. */
    private fun hookSelf(classLoader: ClassLoader) {
        val statusClass = runCatching {
            classLoader.loadClass(ModuleStatus::class.java.name)
        }.getOrNull() ?: return

        statusClass.declaredMethods.forEach { method ->
            when (method.name) {
                "isActive" -> hook(method).intercept { true }
                "xposedApiVersion" -> hook(method).intercept { apiVersion }
                "frameworkName" -> hook(method).intercept { frameworkName }
            }
        }
        XLog.i("hookSelf успешно применен к ModuleStatus")
    }

    companion object {
        /** Путь к APK модуля. */
        @Volatile
        var modulePath: String? = null
            private set

        /** Каталог нативных библиотек модуля (для System.load в целевом процессе). */
        @Volatile
        var moduleNativeDir: String? = null
            private set
    }
}
