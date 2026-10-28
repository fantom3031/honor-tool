package com.fantom3031.honortool.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fantom3031.honortool.R
import com.fantom3031.honortool.core.Constants
import com.fantom3031.honortool.hooks.settings.AboutDeviceCustomHook
import com.fantom3031.honortool.settings.AppSettings

/** Настройки подмены значений «О телефоне». */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutDeviceCustomScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { AppSettings(context) }
    val fields = remember { AboutDeviceCustomHook.allFields }
    val values = remember {
        mutableStateMapOf<String, String>().apply {
            for ((key, _) in fields) {
                put(key, settings.getString("about_device_${key}_value", ""))
            }
        }
    }

    fun save(key: String, value: String) {
        values[key] = value
        settings.setString("about_device_${key}_value", value)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.hook_about_device_custom_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(PaddingValues(16.dp)),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.about_device_custom_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    for ((key, labelRes) in fields) {
                        OutlinedTextField(
                            value = values[key] ?: "",
                            onValueChange = { save(key, it) },
                            label = { Text(stringResource(labelRes)) },
                            singleLine = false,
                            minLines = 1,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}
