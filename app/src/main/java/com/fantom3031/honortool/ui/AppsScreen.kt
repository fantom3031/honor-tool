package com.fantom3031.honortool.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PriorityHigh
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.fantom3031.honortool.BuildConfig
import com.fantom3031.honortool.R
import com.fantom3031.honortool.core.AppScopeStatus
import com.fantom3031.honortool.core.HookRegistry
import com.fantom3031.honortool.core.ModuleScope
import com.fantom3031.honortool.core.ModuleStatus
import com.fantom3031.honortool.core.ScopeCheckState
import com.fantom3031.honortool.core.ServiceBridge
import com.fantom3031.honortool.core.XposedScopeManager
import com.fantom3031.honortool.settings.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Главный экран: статус модуля, проверка scope и список приложений, для которых есть твики. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsScreen(onOpenApp: (TargetApp) -> Unit) {
    val context = LocalContext.current
    val settings = remember { AppSettings(context) }
    val scopePackages = remember { ModuleScope.packages(context) }
    val missingScope = remember { HookRegistry.packagesMissingFromScope(scopePackages) }

    // Используем закешированное состояние сразу, чтобы плашка не пропадала при переходе
    var scopeCheckState by remember {
        mutableStateOf(XposedScopeManager.cachedState ?: ScopeCheckState.Checking)
    }
    var refreshTrigger by remember { mutableIntStateOf(0) }

    val apps by produceState(initialValue = emptyList<TargetApp>()) {
        value = withContext(Dispatchers.IO) { TargetApps.load(context) }
    }

    // Автоматическое обновление статуса scope при возвращении в приложение (с дебаунсом внутри XposedScopeManager)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshTrigger++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(refreshTrigger, apps) {
        if (apps.isEmpty()) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            val state = XposedScopeManager.checkScope(context, apps, force = false)
            scopeCheckState = state
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = {
                        XposedScopeManager.markNeedRefresh()
                        refreshTrigger++
                    }) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = stringResource(R.string.action_check_scope)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { StatusCard() }

            // Карточка проверки области действия (scope) в менеджере Vector / LSPosed
            when (val state = scopeCheckState) {
                is ScopeCheckState.Checking -> {
                    // Пока нет данных (первый запуск), плашку не показываем
                }
                is ScopeCheckState.Success -> {
                    if (state.missingApps.isNotEmpty()) {
                        item {
                            MissingScopeCard(
                                missingApps = state.missingApps,
                                onOpenManager = {
                                    val opened = XposedScopeManager.openManager(context)
                                    if (!opened) {
                                        Toast.makeText(
                                            context,
                                            R.string.xposed_manager_not_found,
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                },
                                onRefresh = {
                                    XposedScopeManager.markNeedRefresh()
                                    refreshTrigger++
                                }
                            )
                        }
                    } else {
                        item {
                            AllScopeActiveCard(totalApps = apps.count { it.installed })
                        }
                    }
                }
                is ScopeCheckState.RootUnavailable -> {
                    if (ModuleStatus.isActive()) {
                        item {
                            RootRequiredScopeCard(
                                onCheck = {
                                    XposedScopeManager.markNeedRefresh()
                                    refreshTrigger++
                                },
                                onOpenManager = {
                                    val opened = XposedScopeManager.openManager(context)
                                    if (!opened) {
                                        Toast.makeText(
                                            context,
                                            R.string.xposed_manager_not_found,
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            )
                        }
                    }
                }
            }

            if (!settings.isSharedWithHooks) {
                item {
                    NoticeCard(
                        icon = Icons.Rounded.WarningAmber,
                        title = stringResource(R.string.prefs_unavailable_title),
                        text = stringResource(R.string.prefs_unavailable_text)
                    )
                }
            }

            if (missingScope.isNotEmpty()) {
                item {
                    NoticeCard(
                        icon = Icons.Rounded.ErrorOutline,
                        title = stringResource(R.string.scope_missing_title),
                        text = stringResource(
                            R.string.scope_missing_text,
                            missingScope.joinToString("\n") { "• $it" }
                        )
                    )
                }
            }

            item {
                Text(
                    text = stringResource(R.string.apps_section),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                )
            }

            if (HookRegistry.hooks.isEmpty()) {
                item {
                    NoticeCard(
                        icon = Icons.Rounded.Info,
                        title = stringResource(R.string.no_tweaks_title),
                        text = stringResource(R.string.no_tweaks_text)
                    )
                }
            } else if (apps.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            } else {
                items(apps, key = { it.packageName }) { app ->
                    val scopeStatus = when (val state = scopeCheckState) {
                        is ScopeCheckState.Success -> {
                            if (!app.installed) AppScopeStatus.UNKNOWN
                            else if (XposedScopeManager.isAppInScope(app, state.scopedPackages)) AppScopeStatus.IN_SCOPE
                            else AppScopeStatus.NOT_IN_SCOPE
                        }
                        else -> AppScopeStatus.UNKNOWN
                    }

                    AppRow(
                        app = app,
                        scopeStatus = scopeStatus,
                        onClick = { onOpenApp(app) }
                    )
                }
            }

            item { MemoryCleanupCard() }
            item { Footer() }

            item {
                val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                    androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
                ) { granted ->
                    if (granted) postTestNotification(context)
                }
                OutlinedButton(
                    onClick = {
                        val granted = if (android.os.Build.VERSION.SDK_INT >= 33) {
                            android.content.pm.PackageManager.PERMISSION_GRANTED == context.checkSelfPermission(
                                android.Manifest.permission.POST_NOTIFICATIONS
                            )
                        } else true
                        if (granted) postTestNotification(context)
                        else permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Тестовое уведомление")
                }
            }
        }
    }
}

private const val TEST_CHANNEL_ID = "honortool_test"
private const val TEST_NOTIFICATION_ID = 1001

private fun postTestNotification(context: android.content.Context) {
    val manager = context.getSystemService(android.app.NotificationManager::class.java) ?: return
    val channel = android.app.NotificationChannel(
        TEST_CHANNEL_ID,
        "Тестовые уведомления",
        android.app.NotificationManager.IMPORTANCE_HIGH
    )
    manager.createNotificationChannel(channel)
    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
    val pending = android.app.PendingIntent.getActivity(
        context, 0, intent,
        android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
    )
    val notification = android.app.Notification.Builder(context, TEST_CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle("Honor Tool")
        .setContentText("Тестовое уведомление")
        .setContentIntent(pending)
        .setAutoCancel(true)
        .build()
    manager.notify(TEST_NOTIFICATION_ID, notification)
}

/**
 * Сворачиваемая плашка со списком приложений, отсутствующих в scope Xposed менеджера.
 */
@Composable
private fun MissingScopeCard(
    missingApps: List<TargetApp>,
    onOpenManager: () -> Unit,
    onRefresh: () -> Unit
) {
    var isCollapsed by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        if (isCollapsed) {
            // Свёрнутая компактная полоска с заголовком и значком слева
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isCollapsed = false }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.WarningAmber,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.scope_missing_collapsed_title, missingApps.size),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(18.dp)
                )
            }
        } else {
            // Развёрнутый вид с подробным описанием и кнопками
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isCollapsed = true },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.WarningAmber,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.scope_missing_in_manager_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { isCollapsed = true },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ExpandLess,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                val missingList = missingApps.joinToString("\n") { "• ${it.label} (${it.packageName})" }
                Text(
                    text = stringResource(R.string.scope_missing_in_manager_text, missingList),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.9f)
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onOpenManager,
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.action_open_xposed_manager),
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1
                        )
                    }
                    IconButton(
                        onClick = onRefresh,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AllScopeActiveCard(totalApps: Int) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    text = stringResource(R.string.scope_all_active_title),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.scope_all_active_text, totalApps),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun RootRequiredScopeCard(onCheck: () -> Unit, onOpenManager: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.scope_root_required_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.scope_root_required_text),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onCheck,
                    shape = RoundedCornerShape(18.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.action_check_scope),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                TextButton(
                    onClick = onOpenManager,
                    shape = RoundedCornerShape(18.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.action_open_xposed_manager),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

@Composable
private fun AppRow(
    app: TargetApp,
    scopeStatus: AppScopeStatus,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (app.icon != null) {
                Image(
                    bitmap = app.icon,
                    contentDescription = null,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
            } else {
                Box(
                    modifier = Modifier.size(40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Android,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    text = app.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(1.dp))
                val tweaks = pluralStringResource(
                    R.plurals.tweak_count,
                    app.hooks.size,
                    app.hooks.size
                )
                Text(
                    text = if (app.installed) {
                        tweaks
                    } else {
                        stringResource(R.string.app_not_installed, tweaks)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Компактный индикатор scope справа, не зависящий от длины названия приложения
            when (scopeStatus) {
                AppScopeStatus.IN_SCOPE -> {
                    Spacer(Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                modifier = Modifier.size(10.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = stringResource(R.string.scope_badge_in_scope),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
                AppScopeStatus.NOT_IN_SCOPE -> {
                    Spacer(Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PriorityHigh,
                                contentDescription = null,
                                modifier = Modifier.size(10.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = stringResource(R.string.scope_badge_not_in_scope),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
                AppScopeStatus.UNKNOWN -> {}
            }

            Spacer(Modifier.width(6.dp))

            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun StatusCard() {
    val serviceConnected by ServiceBridge.isConnectedState
    val dbEnabled by XposedScopeManager.isModuleEnabledInDbState
    val active = ModuleStatus.isActive() || serviceConnected || (dbEnabled == true)
    val container =
        if (active) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.errorContainer
    val content =
        if (active) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onErrorContainer

    val fwName = if (ModuleStatus.frameworkName() != "unknown") {
        ModuleStatus.frameworkName()
    } else if (ServiceBridge.frameworkName != null) {
        ServiceBridge.frameworkName!!
    } else {
        "Vector"
    }

    val apiVer = if (ModuleStatus.xposedApiVersion() > 0) {
        ModuleStatus.xposedApiVersion()
    } else if (ServiceBridge.xposedService?.apiVersion != null) {
        ServiceBridge.xposedService!!.apiVersion
    } else {
        102
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = container),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (active) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(32.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = stringResource(
                        if (active) R.string.module_active else R.string.module_inactive
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = content
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (active) {
                        stringResource(
                            R.string.module_active_details,
                            fwName,
                            apiVer
                        )
                    } else {
                        stringResource(R.string.module_inactive_hint)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = content.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
fun NoticeCard(icon: ImageVector, title: String, text: String) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        ),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(Modifier.padding(16.dp)) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.9f)
                )
            }
        }
    }
}

@Composable
private fun Footer() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Rounded.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(14.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = stringResource(
                R.string.footer_summary,
                BuildConfig.VERSION_NAME,
                HookRegistry.hooks.size
            ),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.outline
        )
    }
}
