package com.fantom3031.honortool.ui

import android.app.ActivityManager
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

@Composable
fun MemoryCleanupCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var running by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf<String?>(null) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Очистка ОЗУ", style = MaterialTheme.typography.titleMedium)
            Text(
                "Останавливает сторонние и предустановленные приложения. VPN, загрузки и уведомления будут прерваны.",
                style = MaterialTheme.typography.bodySmall
            )
            Button(enabled = !running, onClick = {
                running = true
                scope.launch {
                    try {
                        report = withContext(Dispatchers.IO) {
                            val manager = context.getSystemService(ActivityManager::class.java)
                            fun availableMb(): Long {
                                val info = ActivityManager.MemoryInfo()
                                manager.getMemoryInfo(info)
                                return info.availMem / (1024 * 1024)
                            }
                            val before = availableMb()
                            val packages = context.packageManager
                            val protected = mutableSetOf(
                                context.packageName,
                                "com.android.systemui",
                                "com.hihonor.android.launcher",
                                "com.google.android.permissioncontroller",
                                "com.google.android.packageinstaller",
                                "com.android.intentresolver",
                                "com.hihonor.android.internal.app",
                                "com.android.externalstorage",
                                "com.google.android.providers.media.module",
                                "com.android.providers.media",
                                "com.android.providers.telephony",
                                "com.android.keychain",
                                "com.android.networkstack",
                                "com.google.android.networkstack",
                                "com.google.android.networkstack.tethering",
                                "com.google.android.cellbroadcastservice",
                                "com.android.bluetooth",
                                "com.android.nfc",
                                "com.android.se",
                                "com.rosan.dhizuku",
                                "moe.shizuku.privileged.api",
                                "org.lsposed.manager",
                                "org.lsposed.lspatch",
                                "com.topjohnwu.magisk",
                                "me.weishu.kernelsu",
                                "me.bmax.apatch"
                            )
                            Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
                                ?.substringBefore('/')?.let { protected.add(it) }
                            @Suppress("DEPRECATION")
                            packages.resolveActivity(
                                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
                                android.content.pm.PackageManager.MATCH_DEFAULT_ONLY
                            )?.activityInfo?.packageName?.let { protected.add(it) }
                            @Suppress("DEPRECATION")
                            val installed = packages.getInstalledApplications(0)
                            // Shared UID: stopping another package must not stop Honor Tool or the keyboard.
                            val protectedUids = installed.filter { it.packageName in protected }
                                .map { it.uid }.toSet()
                            val targets = installed.filter {
                                it.uid / 100000 == android.os.Process.myUid() / 100000 &&
                                    it.uid % 100000 in 10000..19999 &&
                                    it.packageName !in protected && it.uid !in protectedUids &&
                                    it.packageName.matches(Regex("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+"))
                            }.map { it.packageName }.distinct().sortedWith(
                                compareBy<String> { it != "io.github.guys222.processkeepalive" }
                                    .thenBy { it }
                            )
                            val user = android.os.Process.myUid() / 100000
                            val command = buildString {
                                append("id -u | grep -qx 0 || exit 1\n")
                                append("failed=0\n")
                                targets.forEach { pkg ->
                                    append("am force-stop --user $user '$pkg' >/dev/null 2>&1 || failed=1\n")
                                }
                                append("am kill-all >/dev/null 2>&1 || failed=1\n")
                                append("exit \$failed")
                            }
                            val process = ProcessBuilder("su", "-c", command)
                                .redirectErrorStream(true)
                                .redirectOutput(ProcessBuilder.Redirect.to(java.io.File("/dev/null")))
                                .start()
                            try {
                                if (!process.waitFor(180, TimeUnit.SECONDS)) {
                                    process.destroyForcibly()
                                    "Время очистки истекло. Часть приложений могла быть остановлена."
                                } else if (process.exitValue() != 0) {
                                    "Очистка выполнена не полностью либо root не предоставлен. Доступно: ${availableMb()} МБ."
                                } else {
                                    "Обработано пакетов (включая предустановленные): ${targets.size}. Доступно ОЗУ: $before → ${availableMb()} МБ."
                                }
                            } finally {
                                process.outputStream.close()
                                if (process.isAlive) process.destroyForcibly()
                            }
                        }
                    } catch (e: Exception) {
                        report = "Ошибка очистки: ${e.message ?: e.javaClass.simpleName}"
                    } finally {
                        running = false
                    }
                }
            }) { Text(if (running) "Очистка…" else "Очистить (root)") }
            report?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}
