package com.fantom3031.honortool.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import com.fantom3031.honortool.R
import com.fantom3031.honortool.core.Constants
import com.fantom3031.honortool.settings.AppSettings

/** Настройки твика «Прозрачная шторка»: ползунок непрозрачности фона шторки. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HonorShadeTransparentScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { AppSettings(context) }
    var percent by remember {
        mutableIntStateOf(
            settings.getInt(Constants.HONOR_SHADE_ALPHA_KEY, Constants.DEFAULT_HONOR_SHADE_ALPHA)
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.honor_shade_screen_title)) },
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.honor_shade_opacity_label),
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "$percent%",
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                    Slider(
                        value = percent.toFloat(),
                        onValueChange = { value ->
                            percent = value.toInt()
                        },
                        onValueChangeFinished = {
                            settings.setInt(Constants.HONOR_SHADE_ALPHA_KEY, percent)
                        },
                        valueRange = 0f..100f,
                        steps = 99
                    )
                    Text(
                        text = stringResource(R.string.honor_shade_opacity_help),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
