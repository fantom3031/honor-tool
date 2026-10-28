package com.fantom3031.honortool.core

import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import androidx.compose.runtime.mutableStateOf
import com.fantom3031.honortool.BuildConfig
import com.fantom3031.honortool.ui.TargetApp
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Состояние проверки области видимости (scope) в менеджере Xposed (Vector / LSPosed).
 */
sealed interface ScopeCheckState {
    data object Checking : ScopeCheckState
    data object RootUnavailable : ScopeCheckState
    data class Success(
        val scopedPackages: Set<String>,
        val missingApps: List<TargetApp>
    ) : ScopeCheckState
}

enum class AppScopeStatus {
    IN_SCOPE,
    NOT_IN_SCOPE,
    UNKNOWN
}

/**
 * Менеджер проверки и управления областью действия (scope) модуля
 * в Xposed менеджерах (Vector, LSPosed).
 */
object XposedScopeManager {

    val isModuleEnabledInDbState = mutableStateOf<Boolean?>(null)

    @Volatile
    var cachedState: ScopeCheckState? = null
        private set

    @Volatile
    private var lastCheckTime: Long = 0L

    @Volatile
    private var shouldForceRefresh: Boolean = false

    private const val AUTO_CHECK_INTERVAL_MS = 25_000L

    private val POSSIBLE_DB_PATHS = listOf(
        "/data/adb/lspd/config/modules_config.db",
        "/data/adb/modules/zygisk_lsposed/config/modules_config.db",
        "/data/adb/modules/vector/config/modules_config.db",
        "/data/system/users/0/lsposed/config/modules_config.db",
        "/data/system/users/0/vector/config/modules_config.db"
    )

    fun markNeedRefresh() {
        shouldForceRefresh = true
    }

    /**
     * Проверяет scope с кешированием, чтобы частые переходы между экранами
     * не вызывали повторные su-команды и не приводили к мерцанию/исчезновению UI.
     */
    fun checkScope(
        context: Context,
        apps: List<TargetApp>,
        force: Boolean = false
    ): ScopeCheckState {
        val now = System.currentTimeMillis()
        val cached = cachedState
        if (!force && !shouldForceRefresh && cached != null && (now - lastCheckTime < AUTO_CHECK_INTERVAL_MS)) {
            if (cached is ScopeCheckState.Success) {
                val missing = findMissingApps(apps, cached.scopedPackages)
                val updated = ScopeCheckState.Success(cached.scopedPackages, missing)
                cachedState = updated
                return updated
            }
            return cached
        }
        shouldForceRefresh = false

        val serviceScope = ServiceBridge.xposedService?.let { service ->
            runCatching { service.scope?.toSet() }.getOrNull()
        }
        val scoped = serviceScope ?: readActiveScopeFromDb(context)
        val newState = if (scoped != null) {
            val missing = findMissingApps(apps, scoped)
            ScopeCheckState.Success(scoped, missing)
        } else {
            ScopeCheckState.RootUnavailable
        }

        cachedState = newState
        lastCheckTime = now
        return newState
    }

    /**
     * Читает множество пакетов, включённых в scope нашего модуля в Vector / LSPosed через root.
     * Возвращает null, если root недоступен или база данных не найдена.
     */
    fun readActiveScopeFromDb(context: Context): Set<String>? {
        val cacheDir = context.cacheDir ?: return null
        val tempDb = File(cacheDir, "scope_inspect.db")
        val tempPath = tempDb.absolutePath

        val copyScript = buildString {
            append("for p in ")
            for (path in POSSIBLE_DB_PATHS) {
                append("\"$path\" ")
            }
            append("; do\n")
            append("  if [ -f \"\$p\" ]; then\n")
            append("    cp \"\$p\" \"$tempPath\" && chmod 666 \"$tempPath\"\n")
            append("    cp \"\${p}-wal\" \"${tempPath}-wal\" 2>/dev/null; chmod 666 \"${tempPath}-wal\" 2>/dev/null\n")
            append("    cp \"\${p}-shm\" \"${tempPath}-shm\" 2>/dev/null; chmod 666 \"${tempPath}-shm\" 2>/dev/null\n")
            append("    exit 0\n")
            append("  fi\n")
            append("done\n")
            append("exit 1\n")
        }

        val copied = try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", copyScript))
            val finished = process.waitFor(4, TimeUnit.SECONDS)
            if (!finished) {
                process.destroy()
                false
            } else {
                process.exitValue() == 0 && tempDb.exists() && tempDb.length() > 0
            }
        } catch (t: Throwable) {
            XLog.w("Не удалось скопировать базу данных Vector/LSPosed: ${t.message}")
            false
        }

        if (!copied) {
            cleanupTempFiles(tempDb)
            return null
        }

        val scopedPackages = mutableSetOf<String>()
        var db: SQLiteDatabase? = null
        try {
            db = SQLiteDatabase.openDatabase(
                tempPath,
                null,
                SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
            )
            val modulePkg = BuildConfig.APPLICATION_ID

            // 1. Vector & современная схема LSPosed (таблица modules: mid, module_pkg_name; scope: mid, app_pkg_name)
            var mid: Long? = null
            var moduleActive = false
            try {
                db.rawQuery(
                    "SELECT mid, enabled FROM modules WHERE module_pkg_name = ?",
                    arrayOf(modulePkg)
                ).use { cursor ->
                    if (cursor.moveToFirst()) {
                        mid = cursor.getLong(0)
                        val enabled = if (cursor.columnCount > 1) cursor.getInt(1) == 1 else true
                        moduleActive = enabled
                    }
                }
            } catch (_: Throwable) {
                try {
                    db.rawQuery(
                        "SELECT mid FROM modules WHERE module_pkg_name = ?",
                        arrayOf(modulePkg)
                    ).use { cursor ->
                        if (cursor.moveToFirst()) {
                            mid = cursor.getLong(0)
                            moduleActive = true
                        }
                    }
                } catch (t: Throwable) {
                    XLog.d("Поиск mid в таблице modules: ${t.message}")
                }
            }
            if (moduleActive) {
                isModuleEnabledInDbState.value = true
            }

            if (mid != null) {
                try {
                    db.rawQuery(
                        "SELECT app_pkg_name FROM scope WHERE mid = ?",
                        arrayOf(mid.toString())
                    ).use { cursor ->
                        while (cursor.moveToNext()) {
                            val pkg = cursor.getString(0)
                            if (!pkg.isNullOrBlank()) scopedPackages.add(pkg)
                        }
                    }
                } catch (t: Throwable) {
                    XLog.w("Ошибка чтения scope по mid: ${t.message}")
                }
            }

            // 2. Старая схема LSPosed (таблица scope содержит module_pkg_name напрямую)
            if (scopedPackages.isEmpty()) {
                try {
                    db.rawQuery(
                        "SELECT app_pkg_name FROM scope WHERE module_pkg_name = ?",
                        arrayOf(modulePkg)
                    ).use { cursor ->
                        while (cursor.moveToNext()) {
                            val pkg = cursor.getString(0)
                            if (!pkg.isNullOrBlank()) scopedPackages.add(pkg)
                        }
                    }
                } catch (_: Throwable) {}
            }

            // 3. Таблица lsp_scope (в случае миграции или даунгрейда)
            if (scopedPackages.isEmpty()) {
                try {
                    db.rawQuery(
                        "SELECT app_pkg_name FROM lsp_scope WHERE module_pkg_name = ?",
                        arrayOf(modulePkg)
                    ).use { cursor ->
                        while (cursor.moveToNext()) {
                            val pkg = cursor.getString(0)
                            if (!pkg.isNullOrBlank()) scopedPackages.add(pkg)
                        }
                    }
                } catch (_: Throwable) {}
            }
        } catch (t: Throwable) {
            XLog.e("Ошибка при чтении scope из sqlite", t)
        } finally {
            try { db?.close() } catch (_: Throwable) {}
            cleanupTempFiles(tempDb)
        }

        return scopedPackages
    }

    private fun cleanupTempFiles(file: File) {
        try { file.delete() } catch (_: Throwable) {}
        try { File("${file.absolutePath}-wal").delete() } catch (_: Throwable) {}
        try { File("${file.absolutePath}-shm").delete() } catch (_: Throwable) {}
    }

    /**
     * Проверяет, включено ли приложение [TargetApp] в множество пакетов scope.
     */
    fun isAppInScope(app: TargetApp, scopedPackages: Set<String>): Boolean {
        if ("*" in scopedPackages) return true

        val pkg = app.packageName
        if (pkg == Constants.SYSTEM_FRAMEWORK || pkg == "android" || pkg == "system_server") {
            return "system" in scopedPackages || "android" in scopedPackages || "system_server" in scopedPackages
        }

        if (pkg in scopedPackages) return true

        // Проверяем целевые пакеты хуков этого приложения
        for (hook in app.hooks) {
            for (targetPkg in hook.targetPackages) {
                if (targetPkg == Constants.SYSTEM_FRAMEWORK || targetPkg == "android" || targetPkg == "system_server") {
                    if ("system" in scopedPackages || "android" in scopedPackages || "system_server" in scopedPackages) {
                        return true
                    }
                } else if (targetPkg in scopedPackages) {
                    return true
                }
            }
        }

        return false
    }

    /**
     * Находит приложения, которые установлены на устройстве, но отсутствуют в scope Xposed менеджера.
     */
    fun findMissingApps(apps: List<TargetApp>, scopedPackages: Set<String>): List<TargetApp> {
        return apps.filter { it.installed && !isAppInScope(it, scopedPackages) }
    }

    /**
     * Открывает установленный менеджер Xposed (Vector или LSPosed).
     * Возвращает true, если менеджер успешно запущен.
     */
    fun openManager(context: Context): Boolean {
        markNeedRefresh()

        // 1. Категория менеджера Vector (как для отдельного, так и паразитического APK)
        val vectorIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory("org.matrix.vector.manager.LAUNCH_MANAGER")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            if (context.packageManager.queryIntentActivities(vectorIntent, 0).isNotEmpty()) {
                context.startActivity(vectorIntent)
                return true
            }
        } catch (_: Throwable) {}

        // 2. Категория менеджера LSPosed
        val lsposedIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory("org.lsposed.manager.LAUNCH_MANAGER")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            if (context.packageManager.queryIntentActivities(lsposedIntent, 0).isNotEmpty()) {
                context.startActivity(lsposedIntent)
                return true
            }
        } catch (_: Throwable) {}

        // 3. Стандартный запуск через пакет
        val managers = listOf(
            "org.matrix.vector",
            "org.lsposed.manager",
            "org.meowcat.edxposed.manager",
            "de.robv.android.xposed.installer"
        )
        for (pkg in managers) {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(pkg)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try {
                    context.startActivity(launchIntent)
                    return true
                } catch (_: Throwable) {}
            }
        }
        return false
    }
}
