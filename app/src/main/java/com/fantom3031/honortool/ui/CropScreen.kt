package com.fantom3031.honortool.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.fantom3031.honortool.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Обрезка выбранной картинки строго под размер целевого ресурса.
 *
 * Кадр всегда имеет пропорции [targetWidth]:[targetHeight]; жестами задаётся
 * область исходника, которая затем масштабируется в точный размер ресурса.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CropScreen(
    uri: Uri,
    targetWidth: Int,
    targetHeight: Int,
    onCancel: () -> Unit,
    onCropped: (Bitmap) -> Unit
) {
    val context = LocalContext.current
    val source by produceState<Bitmap?>(initialValue = null, uri) {
        value = withContext(Dispatchers.IO) { ImageLoading.decode(context, uri) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.crop_title, targetWidth, targetHeight)) },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.action_cancel)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        val bitmap = source
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            when {
                bitmap == null -> CircularProgressIndicator()
                bitmap.width < 8 || bitmap.height < 8 ->
                    Text(
                        text = stringResource(R.string.crop_failed),
                        style = MaterialTheme.typography.bodyLarge
                    )

                else -> CropContent(
                    source = bitmap,
                    targetWidth = targetWidth,
                    targetHeight = targetHeight,
                    onCancel = onCancel,
                    onCropped = onCropped
                )
            }
        }
    }
}

@Composable
private fun CropContent(
    source: Bitmap,
    targetWidth: Int,
    targetHeight: Int,
    onCancel: () -> Unit,
    onCropped: (Bitmap) -> Unit
) {
    val scope = rememberCoroutineScope()
    val aspect = targetWidth.toFloat() / targetHeight.toFloat()

    // Самый большой кадр нужных пропорций, который влезает в исходник.
    val baseWidth: Float
    val baseHeight: Float
    if (source.width.toFloat() / source.height.toFloat() > aspect) {
        baseHeight = source.height.toFloat()
        baseWidth = baseHeight * aspect
    } else {
        baseWidth = source.width.toFloat()
        baseHeight = baseWidth / aspect
    }

    var zoom by remember(source) { mutableFloatStateOf(1f) }
    var center by remember(source) {
        mutableStateOf(Offset(source.width / 2f, source.height / 2f))
    }
    var frameWidthPx by remember { mutableStateOf(1) }
    val imageBitmap = remember(source) { source.asImageBitmap() }
    var saving by remember { mutableStateOf(false) }

    fun clampCenter(candidate: Offset, currentZoom: Float): Offset {
        val cropWidth = baseWidth / currentZoom
        val cropHeight = baseHeight / currentZoom
        return Offset(
            candidate.x.coerceIn(cropWidth / 2f, source.width - cropWidth / 2f),
            candidate.y.coerceIn(cropHeight / 2f, source.height - cropHeight / 2f)
        )
    }

    fun cropRect(currentZoom: Float, currentCenter: Offset): IntRect {
        val cropWidth = (baseWidth / currentZoom).coerceAtMost(source.width.toFloat())
        val cropHeight = (baseHeight / currentZoom).coerceAtMost(source.height.toFloat())
        val left = (currentCenter.x - cropWidth / 2f)
            .coerceIn(0f, (source.width - cropWidth).coerceAtLeast(0f))
        val top = (currentCenter.y - cropHeight / 2f)
            .coerceIn(0f, (source.height - cropHeight).coerceAtLeast(0f))
        val width = cropWidth.roundToInt().coerceIn(1, source.width)
        val height = cropHeight.roundToInt().coerceIn(1, source.height)
        val x = left.roundToInt().coerceIn(0, source.width - width)
        val y = top.roundToInt().coerceIn(0, source.height - height)
        return IntRect(left = x, top = y, right = x + width, bottom = y + height)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.crop_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(aspect)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black)
                .onSizeChanged { frameWidthPx = max(1, it.width) }
                .pointerInput(source) {
                    detectTransformGestures { _, pan, gestureZoom, _ ->
                        val newZoom = (zoom * gestureZoom).coerceIn(1f, 10f)
                        val imagePxPerScreenPx = (baseWidth / newZoom) / frameWidthPx
                        center = clampCenter(
                            Offset(
                                center.x - pan.x * imagePxPerScreenPx,
                                center.y - pan.y * imagePxPerScreenPx
                            ),
                            newZoom
                        )
                        zoom = newZoom
                    }
                }
        ) {
            Canvas(Modifier.matchParentSize()) {
                val rect = cropRect(zoom, center)
                drawImage(
                    image = imageBitmap,
                    srcOffset = IntOffset(rect.left, rect.top),
                    srcSize = IntSize(rect.width, rect.height),
                    dstOffset = IntOffset.Zero,
                    dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                    filterQuality = FilterQuality.Medium
                )

                val grid = Color.White.copy(alpha = 0.25f)
                for (i in 1..2) {
                    val x = size.width * i / 3f
                    val y = size.height * i / 3f
                    drawLine(grid, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                    drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Slider(
            value = zoom,
            onValueChange = { value ->
                center = clampCenter(center, value)
                zoom = value
            },
            valueRange = 1f..10f
        )

        Spacer(Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f),
                enabled = !saving
            ) {
                Text(stringResource(R.string.action_cancel))
            }
            Button(
                onClick = {
                    saving = true
                    val rect = cropRect(zoom, center)
                    scope.launch {
                        val result = withContext(Dispatchers.Default) {
                            val cropped = Bitmap.createBitmap(
                                source,
                                rect.left,
                                rect.top,
                                rect.width,
                                rect.height
                            )
                            Bitmap.createScaledBitmap(cropped, targetWidth, targetHeight, true)
                        }
                        onCropped(result)
                    }
                },
                modifier = Modifier.weight(1f),
                enabled = !saving
            ) {
                Text(stringResource(R.string.action_apply))
            }
        }
    }
}
