package com.fantom3031.honortool.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import com.fantom3031.honortool.R
import com.fantom3031.honortool.core.XLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Закрытие целевого приложения, чтобы изменения твика подхватились
 * при его следующем запуске.
 */
object AppControl {

    private const val SU_TIMEOUT_SECONDS = 10L

    /**
     * Останавливает приложение и сообщает результат: если root недоступен,
     * открывает системный экран «О приложении» с кнопкой остановки.
     */
    suspend fun stopAndReport(context: Context, packageName: String) {
        val stopped = withContext(Dispatchers.IO) { forceStop(packageName) }
        if (stopped) {
            Toast.makeText(
                context,
                context.getString(R.string.force_stop_done, packageName),
                Toast.LENGTH_SHORT
            ).show()
        } else {
            Toast.makeText(context, R.string.force_stop_failed, Toast.LENGTH_LONG).show()
            openAppInfo(context, packageName)
        }
    }

    /**
     * Пробует остановить приложение через root. Возвращает `false`, если
     * root недоступен — тогда остаётся открыть экран «О приложении».
     */
    fun forceStop(packageName: String): Boolean = try {
        val command = if (packageName == "com.android.systemui") {
            "pkill -f com.android.systemui || killall com.android.systemui"
        } else {
            "am force-stop $packageName"
        }
        val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
        val finished = process.waitFor(SU_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        if (!finished) {
            process.destroy()
            false
        } else {
            process.exitValue() == 0
        }
    } catch (t: Throwable) {
        XLog.w("Не удалось выполнить force-stop $packageName: ${t.message}")
        false
    }

    /** Системный экран «О приложении», где есть кнопка принудительной остановки. */
    fun openAppInfo(context: Context, packageName: String) {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null)
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (t: Throwable) {
            XLog.w("Не удалось открыть настройки приложения $packageName: ${t.message}")
        }
    }
}
