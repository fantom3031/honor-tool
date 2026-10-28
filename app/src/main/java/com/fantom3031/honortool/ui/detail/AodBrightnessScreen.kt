package com.fantom3031.honortool.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import android.content.Context
import android.content.Intent
import com.fantom3031.honortool.R
import com.fantom3031.honortool.hooks.aod.AodBrightnessHook
import com.fantom3031.honortool.settings.AppSettings

private data class AodBrightnessMode(val level: Int, val label: String)

private val BRIGHTNESS_MODES = listOf(
    AodBrightnessMode(AodBrightnessHook.AUTO, "Авто"),
    AodBrightnessMode(1, "Максимум"),
    AodBrightnessMode(3, "Штатная"),
    AodBrightnessMode(7, "Тускл.")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AodBrightnessScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { AppSettings(context) }
    var level by remember {
        mutableIntStateOf(
            settings.getInt(AodBrightnessHook.BRIGHTNESS_KEY, AodBrightnessHook.DEFAULT_LEVEL)
        )
    }
    Scaffold(topBar = {
        TopAppBar(title = { Text(stringResource(R.string.aod_brightness_title)) }, navigationIcon = {
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
                        when (level) {
                            AodBrightnessHook.AUTO -> "Яркость AOD: авто (штатная)"
                            1 -> "Яркость AOD: максимум"
                            3 -> "Яркость AOD: штатная"
                            7 -> "Яркость AOD: тусклая"
                            else -> "Яркость AOD: уровень $level"
                        }
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BRIGHTNESS_MODES.forEach { mode ->
                            FilterChip(
                                selected = level == mode.level,
                                onClick = {
                                    level = mode.level
                                    settings.setInt(AodBrightnessHook.BRIGHTNESS_KEY, mode.level)
                                    applyBrightness(context, mode.level)
                                },
                                label = { Text(mode.label) }
                            )
                        }
                    }
                    Text(
                        "Панель поддерживает только эти режимы: «максимум» (уровень 1), «штатная» (3) и «тусклая» (7). «Авто» — прошивка сама регулирует (ночью тусклее). Применяется сразу; если AOD скрыт — при следующем пробуждении.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun applyBrightness(context: Context, level: Int) {
    context.sendBroadcast(
        Intent(AodBrightnessHook.SYNC_ACTION)
            .setPackage("com.hihonor.aod")
            .putExtra(AodBrightnessHook.EXTRA_LEVEL, level)
    )
}
