package com.fantom3031.honortool.ui.detail

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.absoluteOffset
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Context
import android.content.Intent
import com.fantom3031.honortool.R
import com.fantom3031.honortool.core.ImageKeys
import com.fantom3031.honortool.hooks.systemui.ScreenshotWatermarkHook
import com.fantom3031.honortool.settings.AppSettings
import com.fantom3031.honortool.settings.ImageStore
import com.fantom3031.honortool.ui.ImageLoading

private const val SYNC_IMAGE_CHUNK = 256 * 1024

/** Рассылает настройки знака в SystemUI; с картинкой — чанками по 256 КБ. */
private fun syncWatermark(context: Context, settings: AppSettings, withImage: Boolean) {
    fun baseIntent() = Intent(ScreenshotWatermarkHook.SYNC_ACTION)
        .setPackage("com.android.systemui")
        .apply {
            putExtra(
                ScreenshotWatermarkHook.EXTRA_MODE,
                settings.getString(ScreenshotWatermarkHook.MODE_KEY, ScreenshotWatermarkHook.MODE_TEXT)
            )
            putExtra(
                ScreenshotWatermarkHook.EXTRA_TEXT,
                settings.getString(ScreenshotWatermarkHook.TEXT_KEY, "")
            )
            putExtra(
                ScreenshotWatermarkHook.EXTRA_TEXT_SIZE,
                settings.getInt(ScreenshotWatermarkHook.TEXT_SIZE_KEY, ScreenshotWatermarkHook.DEFAULT_TEXT_SIZE)
            )
            putExtra(
                ScreenshotWatermarkHook.EXTRA_OPACITY,
                settings.getInt(ScreenshotWatermarkHook.OPACITY_KEY, ScreenshotWatermarkHook.DEFAULT_OPACITY)
            )
            putExtra(
                ScreenshotWatermarkHook.EXTRA_MARGIN,
                settings.getInt(ScreenshotWatermarkHook.MARGIN_KEY, ScreenshotWatermarkHook.DEFAULT_MARGIN)
            )
            putExtra(
                ScreenshotWatermarkHook.EXTRA_POSITION,
                settings.getString(ScreenshotWatermarkHook.POSITION_KEY, ScreenshotWatermarkHook.DEFAULT_POSITION)
            )
            putExtra(
                ScreenshotWatermarkHook.EXTRA_IMAGE_SCALE,
                settings.getInt(ScreenshotWatermarkHook.IMAGE_SCALE_KEY, ScreenshotWatermarkHook.DEFAULT_IMAGE_SCALE)
            )
        }

    if (!withImage) {
        context.sendBroadcast(baseIntent())
        return
    }
    val encoded = ImageStore.encodedString(context, ImageKeys.SCREENSHOT_WATERMARK)
    if (encoded == null) {
        context.sendBroadcast(baseIntent().putExtra(ScreenshotWatermarkHook.EXTRA_IMAGE_CLEAR, true))
        return
    }
    val revision = System.currentTimeMillis()
    val chunks = encoded.chunked(SYNC_IMAGE_CHUNK)
    chunks.forEachIndexed { index, chunk ->
        context.sendBroadcast(baseIntent().apply {
            putExtra(ScreenshotWatermarkHook.EXTRA_IMAGE_REVISION, revision)
            putExtra(ScreenshotWatermarkHook.EXTRA_CHUNK_INDEX, index)
            putExtra(ScreenshotWatermarkHook.EXTRA_CHUNK_TOTAL, chunks.size)
            putExtra(ScreenshotWatermarkHook.EXTRA_CHUNK, chunk)
        })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenshotWatermarkScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { AppSettings(context) }
    var mode by remember {
        mutableStateOf(settings.getString(ScreenshotWatermarkHook.MODE_KEY, ScreenshotWatermarkHook.MODE_TEXT))
    }
    var text by remember {
        mutableStateOf(settings.getString(ScreenshotWatermarkHook.TEXT_KEY, ""))
    }
    var textSize by remember {
        mutableIntStateOf(
            settings.getInt(ScreenshotWatermarkHook.TEXT_SIZE_KEY, ScreenshotWatermarkHook.DEFAULT_TEXT_SIZE)
                .coerceIn(8, 64))
    }
    var opacity by remember {
        mutableIntStateOf(
            settings.getInt(ScreenshotWatermarkHook.OPACITY_KEY, ScreenshotWatermarkHook.DEFAULT_OPACITY)
                .coerceIn(10, 100))
    }
    var margin by remember {
        mutableIntStateOf(
            settings.getInt(ScreenshotWatermarkHook.MARGIN_KEY, ScreenshotWatermarkHook.DEFAULT_MARGIN)
                .coerceIn(0, 64))
    }
    var position by remember {
        mutableStateOf(settings.getString(ScreenshotWatermarkHook.POSITION_KEY, ScreenshotWatermarkHook.DEFAULT_POSITION))
    }
    var imageScale by remember {
        mutableIntStateOf(
            settings.getInt(ScreenshotWatermarkHook.IMAGE_SCALE_KEY, ScreenshotWatermarkHook.DEFAULT_IMAGE_SCALE)
                .coerceIn(5, 100))
    }
    var hasImage by remember { mutableStateOf(ImageStore.has(context, ImageKeys.SCREENSHOT_WATERMARK)) }
    var imageVersion by remember { mutableIntStateOf(0) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val bitmap = ImageLoading.decode(context, uri, maxSide = 1080)
            if (bitmap != null) {
                hasImage = ImageStore.save(context, ImageKeys.SCREENSHOT_WATERMARK, bitmap, lossless = true)
                imageVersion++
                syncWatermark(context, settings, withImage = true)
            }
        }
    }

    LaunchedEffect(Unit) { syncWatermark(context, settings, withImage = true) }

    Scaffold(topBar = {
        TopAppBar(title = { Text(stringResource(R.string.screenshot_watermark_title)) }, navigationIcon = {
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
                    Box(
                        Modifier.fillMaxWidth().height(180.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF232A36), Color(0xFF10141C))
                                )
                            )
                    ) {
                        val alignment = when (position) {
                            "top_left" -> Alignment.TopStart
                            "top_right" -> Alignment.TopEnd
                            "bottom_left" -> Alignment.BottomStart
                            else -> Alignment.BottomEnd
                        }
                        if (mode == ScreenshotWatermarkHook.MODE_IMAGE) {
                            val preview = remember(hasImage, imageVersion) {
                                if (hasImage) ImageStore.load(context, ImageKeys.SCREENSHOT_WATERMARK) else null
                            }
                            preview?.let { bmp ->
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .fillMaxWidth(imageScale / 100f)
                                        .align(alignment)
                                        .absoluteOffset(
                                            x = if (position.endsWith("left")) margin.dp else (-margin).dp,
                                            y = if (position.startsWith("top")) margin.dp else (-margin).dp
                                        )
                                        .alpha(opacity / 100f)
                                )
                            } ?: Text(
                                "Картинка не выбрана",
                                modifier = Modifier.align(Alignment.Center),
                                color = Color.White.copy(alpha = 0.6f)
                            )
                        } else {
                            Column(Modifier.align(alignment).padding(margin.dp)) {
                                text.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
                                    .forEach { line ->
                                        val lineColor = ScreenshotWatermarkHook.lineColor(line)
                                        Text(
                                            ScreenshotWatermarkHook.lineText(line),
                                            style = TextStyle(
                                                fontSize = textSize.sp,
                                                shadow = Shadow(
                                                    Color.Black.copy(alpha = 0.5f),
                                                    androidx.compose.ui.geometry.Offset(1f, 1f),
                                                    2f
                                                )
                                            ),
                                            color = (lineColor?.let { Color(it) } ?: Color.White)
                                                .copy(alpha = opacity / 100f)
                                        )
                                    }
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = mode == ScreenshotWatermarkHook.MODE_TEXT,
                            onClick = {
                                mode = ScreenshotWatermarkHook.MODE_TEXT
                                settings.setString(ScreenshotWatermarkHook.MODE_KEY, mode)
                                syncWatermark(context, settings, withImage = false)
                            },
                            label = { Text("Текст") }
                        )
                        FilterChip(
                            selected = mode == ScreenshotWatermarkHook.MODE_IMAGE,
                            onClick = {
                                mode = ScreenshotWatermarkHook.MODE_IMAGE
                                settings.setString(ScreenshotWatermarkHook.MODE_KEY, mode)
                                syncWatermark(context, settings, withImage = false)
                            },
                            label = { Text("Картинка") }
                        )
                    }

                    if (mode == ScreenshotWatermarkHook.MODE_TEXT) {
                        OutlinedTextField(
                            value = text,
                            onValueChange = {
                                text = it
                                settings.setString(ScreenshotWatermarkHook.TEXT_KEY, it)
                                syncWatermark(context, settings, withImage = false)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            label = { Text("Текст знака") }
                        )
                        Text(
                            "Цвет строки — hex-код в начале: «#FF5722 Красная строка» (6 или 8 цифр). Строки без кода — белые. Текст уменьшается, если не влезает по ширине.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text("Размер: $textSize")
                        Slider(
                            value = textSize.toFloat(),
                            onValueChange = { textSize = it.toInt() },
                            valueRange = 8f..64f,
                            onValueChangeFinished = {
                                settings.setInt(ScreenshotWatermarkHook.TEXT_SIZE_KEY, textSize)
                                syncWatermark(context, settings, withImage = false)
                            }
                        )
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = {
                                picker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }) { Text("Выбрать PNG") }
                            if (hasImage) {
                                OutlinedButton(onClick = {
                                    ImageStore.clear(context, ImageKeys.SCREENSHOT_WATERMARK)
                                    hasImage = false
                                    syncWatermark(context, settings, withImage = true)
                                }) { Text("Убрать") }
                            }
                        }
                        if (!hasImage) {
                            Text(
                                "Картинка не выбрана — скриншоты остаются без знака",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text("Ширина: $imageScale% экрана")
                        Slider(
                            value = imageScale.toFloat(),
                            onValueChange = { imageScale = it.toInt() },
                            valueRange = 5f..100f,
                            onValueChangeFinished = {
                                settings.setInt(ScreenshotWatermarkHook.IMAGE_SCALE_KEY, imageScale)
                                syncWatermark(context, settings, withImage = false)
                            }
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            cornerChip("top_left", "Левый верх", position, settings) { position = it }
                            cornerChip("top_right", "Правый верх", position, settings) { position = it }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            cornerChip("bottom_left", "Левый низ", position, settings) { position = it }
                            cornerChip("bottom_right", "Правый низ", position, settings) { position = it }
                        }
                    }

                    Text("Непрозрачность: $opacity%")
                    Slider(
                        value = opacity.toFloat(),
                        onValueChange = { opacity = it.toInt() },
                        valueRange = 10f..100f,
                        onValueChangeFinished = {
                            settings.setInt(ScreenshotWatermarkHook.OPACITY_KEY, opacity)
                            syncWatermark(context, settings, withImage = false)
                        }
                    )
                    Text("Отступ от края: $margin dp")
                    Slider(
                        value = margin.toFloat(),
                        onValueChange = { margin = it.toInt() },
                        valueRange = 0f..64f,
                        onValueChangeFinished = {
                            settings.setInt(ScreenshotWatermarkHook.MARGIN_KEY, margin)
                            syncWatermark(context, settings, withImage = false)
                        }
                    )
                }
            }
            Text(
                "Знак наносится на системные скриншоты: кнопками, тремя пальцами и плиткой в шторке — он попадает и в сохранённый файл, и в «Поделиться». Изменения настроек применяются сразу, без перезапуска. Скриншоты сторонних приложений и, возможно, длинные прокручиваемые снимки не затрагиваются. Для первого включения хука нужен SystemUI в области действия LSPosed и один перезапуск.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun cornerChip(
    value: String,
    label: String,
    current: String,
    settings: AppSettings,
    onSelect: (String) -> Unit
) {
    val context = LocalContext.current
    FilterChip(
        selected = current == value,
        onClick = {
            onSelect(value)
            settings.setString(ScreenshotWatermarkHook.POSITION_KEY, value)
            syncWatermark(context, settings, withImage = false)
        },
        label = { Text(label) }
    )
}
