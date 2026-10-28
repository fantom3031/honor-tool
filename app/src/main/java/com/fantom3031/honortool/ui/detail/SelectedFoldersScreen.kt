package com.fantom3031.honortool.ui.detail

import android.content.*
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.fantom3031.honortool.R
import com.fantom3031.honortool.hooks.launcher.SelectedFolderSlotsHook as FolderHook
import com.fantom3031.honortool.settings.AppSettings
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectedFoldersScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { AppSettings(context) }
    val lifecycleOwner = LocalLifecycleOwner.current
    var folders by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var selected by remember { mutableStateOf(settings.getStringSet(FolderHook.IDS_KEY)) }
    var all by remember { mutableStateOf(settings.getBoolean(FolderHook.ALL_KEY, false)) }
    var received by remember { mutableStateOf(false) }
    fun refresh() {
        context.sendBroadcast(Intent(FolderHook.QUERY).setPackage(FolderHook.LAUNCHER)
            .putExtra("replyPackage", context.packageName))
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                val ids = intent.getStringArrayExtra("ids") ?: return
                val names = intent.getStringArrayExtra("names") ?: return
                folders = ids.zip(names)
                received = true
            }
        }
        if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(receiver, IntentFilter(FolderHook.RESPONSE), Context.RECEIVER_EXPORTED)
        else context.registerReceiver(receiver, IntentFilter(FolderHook.RESPONSE))
        refresh()
        onDispose { context.unregisterReceiver(receiver) }
    }
    // Also delivers changes after the asynchronous RemotePreferences synchronization.
    LaunchedEffect(selected, all) { delay(600); refresh() }
    Scaffold(topBar = {
        TopAppBar(title = { Text(stringResource(R.string.selected_folders_title)) }, navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.action_back))
            }
        }, actions = {
            IconButton(onClick = { refresh() }) {
                Icon(Icons.Rounded.Refresh, contentDescription = "Обновить список и названия папок")
            }
        }, colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ))
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text("Изменения применяются автоматически. Выбор сохраняется по ID и остаётся после переименования. Хук с full работает независимо.",
                        modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Все карточные папки", modifier = Modifier.weight(1f))
                        Switch(all, onCheckedChange = {
                            all = it; settings.setBoolean(FolderHook.ALL_KEY, it)
                        })
                    }
                }
                if (!received) Text("Ожидание ответа лаунчера. После первого включения хука перезапустите лаунчер.",
                    modifier = Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                else if (folders.isEmpty()) Text("Карточные папки пока не обнаружены. Откройте рабочий стол и вернитесь сюда.",
                    modifier = Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(folders, key = { it.first }) { (id, name) ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(name.ifBlank { "Без названия" })
                            Text("ID: $id", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = all || id in selected, enabled = !all, onCheckedChange = { enabled ->
                            selected = if (enabled) selected + id else selected - id
                            settings.setStringSet(FolderHook.IDS_KEY, selected)
                        })
                    }
                }
            }
            if (selected.any { id -> folders.none { it.first == id } }) item {
                Text("Сохранён выбор и для папок, которых нет в текущем списке. Их ID не удаляются автоматически.")
            }
        }
    }
}
