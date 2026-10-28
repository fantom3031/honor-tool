package com.fantom3031.honortool.core

import android.util.Log
import io.github.libxposed.api.XposedInterface
import java.io.File

/**
 * Логгер, работающий и внутри процессов с хуками, и в самом приложении.
 *
 * В процессе с хуками сообщения дублируются в журнал Xposed (виден в Vector/LSPosed),
 * в обычном процессе приложения — только в logcat.
 */
object XLog {

    @Volatile
    private var xposed: XposedInterface? = null

    private var diagnosticFile: File? = null

    /** /data/system is available before credential unlock; only system_server writes here. */
    @Synchronized
    fun enableSystemServerFile() {
        diagnosticFile = File("/data/system/honor-tool-volume.log")
        toFile("I", "--- system_server diagnostics started, pid=${android.os.Process.myPid()} ---")
    }

    @Synchronized
    private fun toFile(level: String, message: String, throwable: Throwable? = null) {
        val file = diagnosticFile ?: return
        try {
            if (file.length() > 512 * 1024) {
                val previous = File(file.path + ".previous")
                check(!previous.exists() || previous.delete()) { "Cannot remove old diagnostic log" }
                check(file.renameTo(previous)) { "Cannot rotate diagnostic log" }
            }
            file.appendText(buildString {
                append("${java.util.Date()} uptime=${android.os.SystemClock.elapsedRealtime()} ")
                append("pid=${android.os.Process.myPid()} $level $message\n")
                if (throwable != null) append(Log.getStackTraceString(throwable) + "\n")
            })
        } catch (t: Throwable) {
            Log.e(Constants.TAG, "Cannot write diagnostics to ${file.path}", t)
            toXposed(Log.ERROR, "Cannot write diagnostics to ${file.path}", t)
        }
    }

    fun setXposedInterface(interfaceInstance: XposedInterface?) {
        xposed = interfaceInstance
    }

    private fun toXposed(priority: Int, message: String, throwable: Throwable? = null) {
        try {
            xposed?.log(priority, Constants.TAG, message, throwable)
        } catch (_: Throwable) {
            // Модуль не активирован — XposedInterface недоступен.
        }
    }

    fun d(message: String) {
        Log.d(Constants.TAG, message)
        toFile("D", message)
    }

    fun i(message: String) {
        Log.i(Constants.TAG, message)
        toXposed(Log.INFO, message)
        toFile("I", message)
    }

    fun w(message: String) {
        Log.w(Constants.TAG, message)
        toXposed(Log.WARN, "W: $message")
        toFile("W", message)
    }

    fun e(message: String, throwable: Throwable? = null) {
        Log.e(Constants.TAG, message, throwable)
        toXposed(Log.ERROR, "E: $message", throwable)
        toFile("E", message, throwable)
    }
}
