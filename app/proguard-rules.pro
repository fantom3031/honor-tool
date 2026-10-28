# Модуль обращается к части своих классов по имени: из LSPosed (точка входа)
# и из собственных хуков через рефлексию. Такие классы переименовывать нельзя.

# Точка входа: имя записано в META-INF/xposed/java_init.list и assets/xposed_init.
-keep class com.fantom3031.honortool.core.HookEntry { *; }

# DOOM: JNI-методы зовутся по нативным именам (Java_com_fantom3031_honortool_doom_...).
-keep class com.fantom3031.honortool.doom.** { *; }

# Методы подменяются по имени в HookEntry.hookSelf.
-keep class com.fantom3031.honortool.core.ModuleStatus { *; }

# Хуки создаются как object и вызываются из HookModules, но onHook()
# дёргается через рефлексию Xposed — оставляем целиком.
-keep class * extends com.fantom3031.honortool.core.BaseHook { *; }

# LibXposed API есть только в рантайме (compileOnly), поэтому R8 не должен
# ругаться на отсутствующие классы интерфейсов и хелперов.
-dontwarn io.github.libxposed.annotation.**
-keep class io.github.libxposed.api.** { *; }
-dontwarn io.github.libxposed.api.**

# LibXposed Service (IPC и RemotePreferences)
-keep class io.github.libxposed.service.** { *; }
-dontwarn io.github.libxposed.service.**
-keep class com.fantom3031.honortool.core.ServiceBridge { *; }

# Читаемые стектрейсы в журнале LSPosed.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
