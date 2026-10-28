package com.fantom3031.honortool.ui.detail

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fantom3031.honortool.R
import com.fantom3031.honortool.core.Constants
import com.fantom3031.honortool.settings.AppSettings
import com.fantom3031.honortool.settings.VideoStore
import java.io.File

/** Настройки анимации «О телефоне»: видео, фото на весь баннер или DOOM. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutPhoneVideoScreen(
    onBack: () -> Unit,
    onPickPhoto: () -> Unit
) {
    val context = LocalContext.current
    val settings = remember { AppSettings(context) }
    var mode by remember {
        mutableStateOf(
            settings.getString(Constants.ABOUT_VIDEO_MODE_KEY, Constants.ABOUT_VIDEO_MODE_VIDEO)
        )
    }
    var videoPath by remember {
        mutableStateOf(
            settings.getString(Constants.ABOUT_VIDEO_PATH_KEY, Constants.DEFAULT_ABOUT_VIDEO_PATH)
        )
    }

    fun setMode(value: String) {
        mode = value
        settings.setString(Constants.ABOUT_VIDEO_MODE_KEY, value)
    }

    val videoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val saved = VideoStore.copyToDownloads(context, uri)
        if (saved != null) {
            settings.setString(Constants.ABOUT_VIDEO_PATH_KEY, saved)
            videoPath = saved
            setMode(Constants.ABOUT_VIDEO_MODE_VIDEO)
            Toast.makeText(context, R.string.about_video_saved, Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, R.string.about_video_save_failed, Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.hook_about_phone_video_title)) },
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
                    ModeRow(
                        selected = mode == Constants.ABOUT_VIDEO_MODE_VIDEO,
                        label = stringResource(R.string.about_video_mode_video),
                        onClick = { setMode(Constants.ABOUT_VIDEO_MODE_VIDEO) }
                    )
                    ModeRow(
                        selected = mode == Constants.ABOUT_VIDEO_MODE_PHOTO,
                        label = stringResource(R.string.about_video_mode_photo),
                        onClick = { setMode(Constants.ABOUT_VIDEO_MODE_PHOTO) }
                    )
                    ModeRow(
                        selected = mode == Constants.ABOUT_VIDEO_MODE_DOOM,
                        label = stringResource(R.string.about_video_mode_doom),
                        onClick = { setMode(Constants.ABOUT_VIDEO_MODE_DOOM) }
                    )
                }
            }

            when (mode) {
                Constants.ABOUT_VIDEO_MODE_DOOM -> Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.about_doom_title),
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.about_doom_help),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.about_doom_controls_title),
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(Modifier.height(6.dp))
                        ControlRow(stringResource(R.string.about_doom_ctl_move))
                        ControlRow(stringResource(R.string.about_doom_ctl_turn))
                        ControlRow(stringResource(R.string.about_doom_ctl_fire))
                        ControlRow(stringResource(R.string.about_doom_ctl_use))
                        ControlRow(stringResource(R.string.about_doom_ctl_weapon))
                        ControlRow(stringResource(R.string.about_doom_ctl_volume))
                    }
                }

                Constants.ABOUT_VIDEO_MODE_PHOTO -> Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.about_photo_banner_title),
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.about_photo_banner_help),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = onPickPhoto,
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Text(stringResource(R.string.about_photo_banner_pick))
                        }
                    }
                }

                else -> {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                text = stringResource(R.string.about_video_current),
                                style = MaterialTheme.typography.titleSmall
                            )
                            Spacer(Modifier.height(6.dp))
                            val file = File(videoPath)
                            if (file.isFile) {
                                Text(
                                    text = videoPath,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = stringResource(
                                        R.string.about_video_size_kb,
                                        file.length() / 1024
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                Text(
                                    text = stringResource(R.string.about_video_none),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = videoPath,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { videoPicker.launch(arrayOf("video/mp4")) },
                                    shape = RoundedCornerShape(18.dp)
                                ) {
                                    Text(stringResource(R.string.about_video_pick))
                                }
                                if (File(videoPath).isFile) {
                                    TextButton(
                                        onClick = {
                                            VideoStore.clear(context)
                                            settings.setString(
                                                Constants.ABOUT_VIDEO_PATH_KEY,
                                                Constants.DEFAULT_ABOUT_VIDEO_PATH
                                            )
                                            videoPath = Constants.DEFAULT_ABOUT_VIDEO_PATH
                                        },
                                        shape = RoundedCornerShape(18.dp)
                                    ) {
                                        Text(stringResource(R.string.action_reset))
                                    }
                                }
                            }
                        }
                    }
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                text = stringResource(R.string.how_it_works),
                                style = MaterialTheme.typography.titleSmall
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = stringResource(R.string.about_video_help),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ControlRow(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
    ) {
        Text(
            text = "•",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(end = 8.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ModeRow(selected: Boolean, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.height(0.dp))
        Text(text = label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 4.dp))
    }
}
