package com.fantom3031.honortool.ui.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fantom3031.honortool.R
import com.fantom3031.honortool.hooks.aod.AodDisplayTimeHook
import com.fantom3031.honortool.settings.AppSettings

private const val SLIDER_MAX = 60

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AodDisplayTimeScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { AppSettings(context) }
    var duration by remember {
        mutableIntStateOf(
            settings.getInt(AodDisplayTimeHook.DURATION_KEY, AodDisplayTimeHook.DEFAULT_DURATION)
                .coerceIn(AodDisplayTimeHook.MIN_DURATION, AodDisplayTimeHook.MAX_DURATION)
        )
    }
    var showEditDialog by remember { mutableStateOf(false) }

    if (showEditDialog) {
        var input by remember { mutableStateOf(duration.toString()) }
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Длительность показа, с") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it.filter(Char::isDigit).take(4) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    Text(
                        "От ${AodDisplayTimeHook.MIN_DURATION} до ${AodDisplayTimeHook.MAX_DURATION} с.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    input.toIntOrNull()?.let { v ->
                        val clamped =
                            v.coerceIn(AodDisplayTimeHook.MIN_DURATION, AodDisplayTimeHook.MAX_DURATION)
                        duration = clamped
                        settings.setInt(AodDisplayTimeHook.DURATION_KEY, clamped)
                    }
                    showEditDialog = false
                }) { Text("ОК") }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) { Text("Отмена") }
            }
        )
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text(stringResource(R.string.aod_display_time_title)) }, navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.action_back))
            }
        }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainer))
    }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Экран AOD остаётся включённым: $duration с",
                        modifier = Modifier.fillMaxWidth()
                            .clickable { showEditDialog = true },
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        "Нажми на значение, чтобы ввести его вручную — можно больше 60 с.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = duration.coerceIn(AodDisplayTimeHook.MIN_DURATION, SLIDER_MAX).toFloat(),
                        onValueChange = { duration = it.toInt() },
                        valueRange = AodDisplayTimeHook.MIN_DURATION.toFloat()..SLIDER_MAX.toFloat(),
                        onValueChangeFinished = {
                            settings.setInt(AodDisplayTimeHook.DURATION_KEY, duration)
                        }
                    )
                    if (duration > SLIDER_MAX) {
                        Text(
                            "Значение выше предела ползунка; ползунок установит не больше $SLIDER_MAX с",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                    Text(
                        "Штатное значение — несколько секунд. Изменение применяется при следующем пробуждении экрана, перезапуск не нужен. Дольше — больше расход батареи: экран IPS горит всё это время.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
