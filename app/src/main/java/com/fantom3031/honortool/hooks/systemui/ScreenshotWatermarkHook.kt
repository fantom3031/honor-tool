package com.fantom3031.honortool.hooks.systemui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.util.Base64
import android.util.TypedValue
import com.fantom3031.honortool.R
import com.fantom3031.honortool.core.BaseHook
import com.fantom3031.honortool.core.HookImages
import com.fantom3031.honortool.core.HookPrefs
import com.fantom3031.honortool.core.ImageKeys
import com.fantom3031.honortool.core.XLog

/**
 * Водяной знак на системных скриншотах (MagicOS 8, Android 14).
 *
 * Bitmap проходит через SystemUI по цепочке
 * ImageCaptureImpl -> ScreenshotData -> ImageExporter$Task (запись файла).
 * Знак наносится в трёх точках, повторное нанесение на тот же Bitmap исключено
 * WeakHashMap-метками. Аппаратные Bitmap копируются в программные для рисования.
 *
 * Настройки живут двумя источниками: RemotePreferences (на старте процесса)
 * и живая синхронизация через broadcast от приложения — чтобы изменения
 * применялись без перезапуска долгоживущего процесса скриншотов.
 */
object ScreenshotWatermarkHook : BaseHook() {

    const val ID = "systemui_screenshot_watermark"
    const val MODE_KEY = "screenshot_watermark_mode"
    const val TEXT_KEY = "screenshot_watermark_text"
    const val TEXT_SIZE_KEY = "screenshot_watermark_text_size"
    const val OPACITY_KEY = "screenshot_watermark_opacity"
    const val MARGIN_KEY = "screenshot_watermark_margin"
    const val POSITION_KEY = "screenshot_watermark_position"
    const val IMAGE_SCALE_KEY = "screenshot_watermark_image_scale"

    const val MODE_TEXT = "text"
    const val MODE_IMAGE = "image"

    const val DEFAULT_TEXT_SIZE = 16
    const val DEFAULT_OPACITY = 80
    const val DEFAULT_MARGIN = 16
    const val DEFAULT_IMAGE_SCALE = 25
    const val DEFAULT_POSITION = "bottom_right"
    const val DEFAULT_COLOR = 0xFFFFFFFF.toInt()

    const val SYNC_ACTION = "com.fantom3031.honortool.WATERMARK_SYNC"
    const val EXTRA_MODE = "mode"
    const val EXTRA_TEXT = "text"
    const val EXTRA_TEXT_SIZE = "text_size"
    const val EXTRA_OPACITY = "opacity"
    const val EXTRA_MARGIN = "margin"
    const val EXTRA_POSITION = "position"
    const val EXTRA_IMAGE_SCALE = "image_scale"
    const val EXTRA_IMAGE_CLEAR = "image_clear"
    const val EXTRA_IMAGE_REVISION = "image_revision"
    const val EXTRA_CHUNK_INDEX = "chunk_index"
    const val EXTRA_CHUNK_TOTAL = "chunk_total"
    const val EXTRA_CHUNK = "chunk"

    override val id = ID
    override val titleRes = R.string.screenshot_watermark_title
    override val descriptionRes = R.string.screenshot_watermark_description
    override val targetPackages = setOf("com.android.systemui")
    override val enabledByDefault = false

    /** Bitmap, на которых знак уже нанесён; повторно не трогаем. */
    private val applied: MutableMap<Bitmap, Boolean> =
        java.util.Collections.synchronizedMap(java.util.WeakHashMap())

    private val colorPrefix = Regex("^\\s*#([0-9A-Fa-f]{6}|[0-9A-Fa-f]{8})\\s+")

    /** Настройки из последнего broadcast; приоритетнее RemotePreferences. */
    private class Live {
        @Volatile var mode: String? = null
        @Volatile var text: String? = null
        @Volatile var textSize = -1
        @Volatile var opacity = -1
        @Volatile var margin = -1
        @Volatile var position: String? = null
        @Volatile var imageScale = -1
        @Volatile var image: Bitmap? = null
        @Volatile var imageAbsent = false
    }

    @Volatile private var live: Live? = null
    @Volatile private var receiver: BroadcastReceiver? = null
    private val chunkBuffers = HashMap<Long, StringBuilder>()

    override fun onHook() {
        val capture = findClassOrNull("com.android.systemui.screenshot.ImageCaptureImpl")
        if (capture == null) {
            XLog.w("[$id] ImageCaptureImpl не найден")
        } else {
            capture.hookAfter(
                "captureDisplay",
                Int::class.javaPrimitiveType,
                Rect::class.java
            ) { p ->
                (p.result as? Bitmap)?.let { p.result = watermark(it) }
            } ?: XLog.w("[$id] captureDisplay не найден")
        }

        val data = findClassOrNull("com.android.systemui.screenshot.ScreenshotData")
        if (data == null) {
            XLog.w("[$id] ScreenshotData не найден")
        } else {
            data.hookBefore("setBitmap", Bitmap::class.java) { p ->
                (p.args[0] as? Bitmap)?.let { p.args[0] = watermark(it) }
            } ?: XLog.w("[$id] ScreenshotData.setBitmap не найден")
        }

        val task = findClassOrNull("com.android.systemui.screenshot.ImageExporter\$Task")
        if (task == null) {
            XLog.w("[$id] ImageExporter\$Task не найден")
        } else {
            check(task.hookAllConstructorsBefore { p ->
                for (i in p.args.indices) {
                    val a = p.args[i]
                    if (a is Bitmap) p.args[i] = watermark(a)
                }
            }.isNotEmpty()) { "[$id] конструкторы ImageExporter\$Task не найдены" }
        }
        ensureReceiver()
        XLog.i("[$id] точки водяного знака установлены")
    }

    private fun ensureReceiver() {
        if (receiver != null) return
        val context = currentApplication() ?: return
        val r = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.action != SYNC_ACTION) return
                val state = live ?: Live().also { live = it }
                intent.getStringExtra(EXTRA_MODE)?.let { state.mode = it }
                intent.getStringExtra(EXTRA_TEXT)?.let { state.text = it }
                if (intent.hasExtra(EXTRA_TEXT_SIZE)) state.textSize = intent.getIntExtra(EXTRA_TEXT_SIZE, -1)
                if (intent.hasExtra(EXTRA_OPACITY)) state.opacity = intent.getIntExtra(EXTRA_OPACITY, -1)
                if (intent.hasExtra(EXTRA_MARGIN)) state.margin = intent.getIntExtra(EXTRA_MARGIN, -1)
                intent.getStringExtra(EXTRA_POSITION)?.let { state.position = it }
                if (intent.hasExtra(EXTRA_IMAGE_SCALE)) state.imageScale = intent.getIntExtra(EXTRA_IMAGE_SCALE, -1)
                if (intent.getBooleanExtra(EXTRA_IMAGE_CLEAR, false)) {
                    state.image = null
                    state.imageAbsent = true
                    XLog.i("[$id] синхронизация: картинка убрана")
                } else if (intent.hasExtra(EXTRA_IMAGE_REVISION)) {
                    receiveImage(state, intent)
                }
            }
        }
        try {
            context.registerReceiver(r, IntentFilter(SYNC_ACTION), Context.RECEIVER_EXPORTED)
            receiver = r
            XLog.i("[$id] приёмник живой синхронизации готов")
        } catch (t: Throwable) {
            XLog.e("[$id] не удалось зарегистрировать приёмник синхронизации", t)
        }
    }

    private fun receiveImage(state: Live, intent: Intent) {
        val revision = intent.getLongExtra(EXTRA_IMAGE_REVISION, 0L)
        val chunk = intent.getStringExtra(EXTRA_CHUNK) ?: return
        val index = intent.getIntExtra(EXTRA_CHUNK_INDEX, -1)
        val total = intent.getIntExtra(EXTRA_CHUNK_TOTAL, 0)
        if (revision <= 0L || index < 0 || total <= 0) return
        val buffer = synchronized(chunkBuffers) {
            chunkBuffers.keys.removeAll { it != revision }
            chunkBuffers.getOrPut(revision) { StringBuilder() }
        }
        synchronized(buffer) { buffer.append(chunk) }
        if (index != total - 1) return
        val assembled = synchronized(chunkBuffers) { chunkBuffers.remove(revision)?.toString() } ?: return
        try {
            val bytes = Base64.decode(assembled, Base64.NO_WRAP)
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            if (bitmap != null) {
                state.image = bitmap
                state.imageAbsent = false
                XLog.i("[$id] синхронизация: картинка получена (${bytes.size} байт)")
            } else {
                XLog.e("[$id] синхронизация: не удалось декодировать картинку")
            }
        } catch (t: Throwable) {
            XLog.e("[$id] синхронизация: битая картинка", t)
        }
    }

    private fun currentApplication(): Context? = try {
        val thread = Class.forName("android.app.ActivityThread")
            .getMethod("currentActivityThread").invoke(null)
        thread?.javaClass?.getMethod("getApplication")?.invoke(thread) as? Context
    } catch (_: Throwable) {
        null
    }

    private fun currentMode(): String = live?.mode ?: HookPrefs.getString(MODE_KEY, MODE_TEXT)
    private fun currentText(): String = live?.text ?: HookPrefs.getString(TEXT_KEY, "") ?: ""
    private fun currentTextSize(): Int =
        (live?.textSize ?: -1).takeIf { it > 0 } ?: HookPrefs.getInt(TEXT_SIZE_KEY, DEFAULT_TEXT_SIZE)
    private fun currentOpacity(): Int =
        (live?.opacity ?: -1).takeIf { it > 0 } ?: HookPrefs.getInt(OPACITY_KEY, DEFAULT_OPACITY)
    private fun currentMargin(): Int =
        (live?.margin ?: -1).takeIf { it >= 0 } ?: HookPrefs.getInt(MARGIN_KEY, DEFAULT_MARGIN)
    private fun currentPosition(): String = live?.position ?: HookPrefs.getString(POSITION_KEY, DEFAULT_POSITION)
    private fun currentImageScale(): Int =
        (live?.imageScale ?: -1).takeIf { it > 0 } ?: HookPrefs.getInt(IMAGE_SCALE_KEY, DEFAULT_IMAGE_SCALE)

    private fun currentImage(): Bitmap? {
        val state = live ?: return HookImages.bitmap(ImageKeys.SCREENSHOT_WATERMARK)
        if (state.imageAbsent) return null
        return state.image ?: HookImages.bitmap(ImageKeys.SCREENSHOT_WATERMARK)
    }

    /** Цвет строки вида «#RRGGBB текст» или null. */
    fun lineColor(line: String): Int? {
        val hex = colorPrefix.find(line)?.groupValues?.get(1) ?: return null
        return try {
            Color.parseColor("#$hex")
        } catch (_: Throwable) {
            null
        }
    }

    /** Текст строки без цветового префикса. */
    fun lineText(line: String): String = colorPrefix.replace(line, "")

    private fun watermark(source: Bitmap): Bitmap {
        ensureReceiver()
        if (applied.containsKey(source)) return source
        return try {
            val target = when {
                source.config == Bitmap.Config.HARDWARE ->
                    source.copy(Bitmap.Config.ARGB_8888, true)

                source.isMutable -> source
                else -> source.copy(source.config ?: Bitmap.Config.ARGB_8888, true)
            } ?: source
            if (drawWatermark(target)) {
                applied[source] = true
                applied[target] = true
                target
            } else {
                source
            }
        } catch (t: Throwable) {
            XLog.e("[$id] не удалось наложить водяной знак", t)
            source
        }
    }

    private fun drawWatermark(target: Bitmap): Boolean {
        val opacity = currentOpacity().coerceIn(10, 100)
        val marginPx = dp(currentMargin().coerceIn(0, 64))
        val position = currentPosition()
        val top = position.startsWith("top")
        val right = position.endsWith("right")
        val alpha = opacity * 255 / 100
        return if (currentMode() == MODE_IMAGE) {
            drawImage(target, alpha, marginPx, top, right)
        } else {
            drawText(target, alpha, marginPx, top, right)
        }
    }

    private fun drawText(
        target: Bitmap,
        alpha: Int,
        marginPx: Int,
        top: Boolean,
        right: Boolean
    ): Boolean {
        val lines = currentText().split('\n').map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return false

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.DEFAULT_BOLD
            textSize = sp(currentTextSize().coerceIn(8, 64))
        }
        paint.setShadowLayer(
            paint.textSize / 8f,
            0f,
            paint.textSize / 12f,
            Color.argb(alpha * 60 / 100, 0, 0, 0)
        )
        val maxWidth = (target.width - 2 * marginPx).coerceAtLeast(1).toFloat()
        while (lines.maxOf { paint.measureText(lineText(it)) } > maxWidth &&
            paint.textSize > 8f
        ) {
            paint.textSize -= 1f
        }

        val canvas = Canvas(target)
        val fm = paint.fontMetrics
        val lineHeight = fm.descent - fm.ascent
        val baseline = if (top) {
            marginPx - fm.ascent
        } else {
            target.height - marginPx - lineHeight * lines.size - fm.ascent
        }
        lines.forEachIndexed { i, line ->
            val text = lineText(line)
            paint.color = lineColor(line) ?: DEFAULT_COLOR
            paint.alpha = alpha
            val x = if (right) {
                target.width - marginPx - paint.measureText(text)
            } else {
                marginPx.toFloat()
            }
            canvas.drawText(text, x, baseline + lineHeight * i, paint)
        }
        XLog.i("[$id] наложен текст (${lines.size} строк, ${paint.textSize}px)")
        return true
    }

    private fun drawImage(
        target: Bitmap,
        alpha: Int,
        marginPx: Int,
        top: Boolean,
        right: Boolean
    ): Boolean {
        val bitmap = currentImage() ?: run {
            XLog.w("[$id] картинка знака не выбрана")
            return false
        }
        val scale = currentImageScale().coerceIn(5, 100)
        val width = (target.width * scale / 100).coerceAtLeast(1)
        val height = (width.toLong() * bitmap.height / bitmap.width).coerceAtLeast(1L).toInt()
        val left = if (right) target.width - marginPx - width else marginPx
        val y = if (top) marginPx else target.height - marginPx - height
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            this.alpha = alpha
        }
        Canvas(target).drawBitmap(
            bitmap,
            null,
            Rect(left, y, left + width, y + height),
            paint
        )
        XLog.i("[$id] наложена картинка ${width}x$height")
        return true
    }

    private fun dp(value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), android.content.res.Resources.getSystem().displayMetrics
    ).toInt()

    private fun sp(value: Int): Float = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_SP, value.toFloat(), android.content.res.Resources.getSystem().displayMetrics
    )
}
