package com.fantom3031.honortool.doom

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.view.Choreographer
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View

/**
 * Вьюшка DOOM для карточки «О телефоне»: тянет кадры из [DoomEngine]
 * через Choreographer и рисует экранные кнопки управления.
 *
 * Управление:
 *  - D-pad слева: движение вперёд/назад и стрейф;
 *  - кнопки поворота справа по центру;
 *  - FIRE (в меню — Enter), USE/открыть (в меню — Esc), WPN — смена оружия;
 *  - свайпы работают как раньше: левая половина — движение, правая — поворот;
 *  - кнопки громкости: Vol+ — огонь/вверх, Vol- — действие/вниз (долгое — Esc).
 */
@SuppressLint("ViewConstructor")
class DoomView(context: Context) : View(context) {

    companion object {
        const val TAG = "honortool_doom_view"

        private const val TAP_SLOP = 24f
        private const val TAP_TIME_MS = 200L
        private const val ESC_LONG_MS = 500L
        private const val MENU_REPEAT_MS = 160L

        /** Диспетчер кнопок громкости; вызывается хуком из Activity.dispatchKeyEvent. */
        val volumeKeys = VolumeKeys()

        fun dispatchVolumeKey(keyCode: Int, down: Boolean): Boolean =
            volumeKeys.onKey(keyCode, down)
    }

    // ------------------------------------------------------------------
    // Экранные кнопки
    // ------------------------------------------------------------------

    /** id — индекс в [buttons]. */
    private class TouchBtn(
        val label: String,
        /** Клавиша PureDOOM в геймплее. */
        val gameKey: Int,
        /** Клавиша в меню (0 — та же, что gameKey). */
        val menuKey: Int = 0,
        /** Автоповтор в меню при удержании. */
        val repeatInMenu: Boolean = false,
        /** Диаметр в dp. */
        val sizeDp: Float,
        var rect: RectF = RectF(),
        var pressed: Boolean = false
    )

    private val btnUp = TouchBtn("▲", DoomEngine.KEY_UP_ARROW, repeatInMenu = true, sizeDp = 42f)
    private val btnDown = TouchBtn("▼", DoomEngine.KEY_DOWN_ARROW, repeatInMenu = true, sizeDp = 42f)
    private val btnLeft = TouchBtn("◀", DoomEngine.KEY_COMMA, repeatInMenu = true, sizeDp = 42f)
    private val btnRight = TouchBtn("▶", DoomEngine.KEY_PERIOD, repeatInMenu = true, sizeDp = 42f)
    private val btnTurnL = TouchBtn("↺", DoomEngine.KEY_LEFT_ARROW, repeatInMenu = true, sizeDp = 42f)
    private val btnTurnR = TouchBtn("↻", DoomEngine.KEY_RIGHT_ARROW, repeatInMenu = true, sizeDp = 42f)
    private val btnFire = TouchBtn("FIRE", DoomEngine.KEY_CTRL, menuKey = DoomEngine.KEY_ENTER, sizeDp = 64f)
    private val btnUse = TouchBtn("USE", DoomEngine.KEY_SPACE, menuKey = DoomEngine.KEY_ESCAPE, sizeDp = 46f)
    private val btnWeapon = TouchBtn("WPN", 0, sizeDp = 40f)

    private val buttons = listOf(
        btnUp, btnDown, btnLeft, btnRight,
        btnTurnL, btnTurnR, btnFire, btnUse, btnWeapon
    )

    /** pointerId → кнопка. */
    private val pointerButtons = HashMap<Int, TouchBtn>()

    private val btnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val btnPressedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xCCFF7043.toInt()
    }
    private val btnTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val density get() = resources.displayMetrics.density
    private val repeatHandler = Handler(Looper.getMainLooper())

    private val repeatRunnable = object : Runnable {
        override fun run() {
            val mode = DoomEngine.inputMode()
            for (btn in buttons) {
                if (btn.pressed && btn.repeatInMenu && mode == 0 && effectiveKey(btn, 0) != 0) {
                    DoomEngine.keyTap(effectiveKey(btn, 0))
                }
            }
            repeatHandler.postDelayed(this, MENU_REPEAT_MS)
        }
    }

    private fun effectiveKey(btn: TouchBtn, mode: Int): Int =
        if (mode == 0 && btn.menuKey != 0) btn.menuKey else btn.gameKey

    private fun layoutButtons() {
        val dp = density
        fun size(b: TouchBtn) = b.sizeDp * dp
        val pad = 6f * dp

        // D-pad крест: левый нижний угол. Кнопки 42dp, зазор между краями 26dp.
        val up = size(btnUp)
        val gap = 26f * dp
        val arm = up + gap // расстояние между центрами соседних кнопок
        val half = arm / 2 + up / 2
        val crossCx = pad + half
        val crossCy = height - pad - half
        fun place(b: TouchBtn, cx: Float, cy: Float) {
            val s = size(b)
            b.rect = RectF(cx - s / 2, cy - s / 2, cx + s / 2, cy + s / 2)
        }
        place(btnUp, crossCx, crossCy - arm / 2)
        place(btnDown, crossCx, crossCy + arm / 2)
        place(btnLeft, crossCx - arm / 2, crossCy)
        place(btnRight, crossCx + arm / 2, crossCy)

        // Поворот: правый край, верхняя половина — не пересекается с WPN/FIRE.
        val t = size(btnTurnL)
        val turnX = width - pad - t / 2
        val turnTop = height * 0.28f
        place(btnTurnL, turnX, turnTop)
        place(btnTurnR, turnX, turnTop + t + 8 * dp)

        // FIRE — правый нижний угол; USE — левее; WPN — над USE.
        val f = size(btnFire)
        place(btnFire, width - pad - f / 2, height - pad - f / 2)
        val u = size(btnUse)
        place(btnUse, width - pad - f - u / 2 - 6 * dp, height - pad - u / 2)
        val w = size(btnWeapon)
        place(btnWeapon, width - pad - f - w / 2 - 6 * dp, height - pad - u - w / 2 - 6 * dp)

        btnTextPaint.textSize = 14f * dp
    }

    /** Попадание по кнопке — круг с запасом 10dp (прощает неточные нажатия). */
    private fun hitButton(x: Float, y: Float): TouchBtn? {
        val slop = 10f * density
        val order = listOf(btnFire, btnUse, btnWeapon, btnUp, btnDown, btnLeft, btnRight, btnTurnL, btnTurnR)
        for (btn in order) {
            val cx = btn.rect.centerX()
            val cy = btn.rect.centerY()
            val r = btn.rect.width() / 2 + slop
            if ((x - cx) * (x - cx) + (y - cy) * (y - cy) <= r * r) return btn
        }
        return null
    }

    private fun pressButton(btn: TouchBtn) {
        if (btn.pressed) return
        btn.pressed = true
        val mode = DoomEngine.inputMode()
        if (btn === btnWeapon) {
            cycleWeapon()
            return
        }
        val key = effectiveKey(btn, mode)
        if (key != 0) DoomEngine.key(key, true)
        invalidate()
    }

    private fun releaseButton(btn: TouchBtn) {
        if (!btn.pressed) return
        btn.pressed = false
        val mode = DoomEngine.inputMode()
        if (btn === btnWeapon) {
            invalidate()
            return
        }
        val key = effectiveKey(btn, mode)
        if (key != 0) DoomEngine.key(key, false)
        invalidate()
    }

    /** Цикл пистолет → дробовик → кулак (shareware). */
    private var lastWeapon = 2
    private fun cycleWeapon() {
        lastWeapon = when (lastWeapon) {
            2 -> 3
            3 -> 1
            else -> 2
        }
        DoomEngine.keyTap(('0'.code + lastWeapon))
    }

    // ------------------------------------------------------------------
    // Отрисовка
    // ------------------------------------------------------------------

    private val choreographer = Choreographer.getInstance()
    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!isAttachedToWindow) return
            choreographer.postFrameCallback(this)
            if (DoomEngine.frameSeq != drawnSeq) {
                invalidate()
            }
        }
    }

    private var drawnSeq = -1

    private val bitmapPaint = Paint().apply {
        isFilterBitmap = false // пиксели как в 1993-м
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 28f
    }
    private val hintBgPaint = Paint().apply {
        color = 0xB3000000.toInt()
    }
    private var hintUntil = 0L

    // --- свайп поворота (правая половина) ---
    private var rightPtr = -1
    private var rightStartX = 0f
    private var rightEngaged = false
    private var rightLastX = 0f
    private var downTime = 0L
    private var downX = 0f
    private var downY = 0f

    init {
        volumeKeys.attach(this)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) layoutButtons()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        hintUntil = System.currentTimeMillis() + 6000
        drawnSeq = -1
        DoomEngine.attachView(this)
        choreographer.postFrameCallback(frameCallback)
        repeatHandler.postDelayed(repeatRunnable, MENU_REPEAT_MS)
        volumeKeys.attach(this)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        choreographer.removeFrameCallback(frameCallback)
        repeatHandler.removeCallbacks(repeatRunnable)
        pointerButtons.values.forEach { releaseButton(it) }
        pointerButtons.clear()
        volumeKeys.detach(this)
        DoomEngine.detachView(this)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!DoomEngine.started) {
            drawMessage(canvas, DoomEngine.fatalMessage ?: "DOOM…")
            return
        }
        DoomEngine.withFrame { bmp ->
            if (width > 0 && height > 0) {
                canvas.drawBitmap(bmp, null, fitRect(), bitmapPaint)
            }
        }
        drawnSeq = DoomEngine.frameSeq

        drawButtons(canvas)

        val now = System.currentTimeMillis()
        if (now < hintUntil) {
            drawHint(canvas)
        }
        if (!DoomEngine.isAlive) {
            drawMessage(canvas, DoomEngine.fatalMessage ?: "DOOM stopped")
        }
    }

    /** Пропорции 320×200 (8:5) — вписываем в вьюшку по центру. */
    private fun fitRect(): RectF {
        val vw = width.toFloat()
        val vh = height.toFloat()
        var w = vw
        var h = vw * DoomEngine.HEIGHT / DoomEngine.WIDTH
        if (h > vh) {
            h = vh
            w = vh * DoomEngine.WIDTH / DoomEngine.HEIGHT
        }
        val left = (vw - w) / 2f
        val top = (vh - h) / 2f
        return RectF(left, top, left + w, top + h)
    }

    private fun drawButtons(canvas: Canvas) {
        val mode = DoomEngine.inputMode()
        for (btn in buttons) {
            // Кнопку смены оружия прячем в меню.
            if (btn === btnWeapon && mode == 0) continue
            btnPaint.color = if (mode == 0) 0x60808080.toInt() else 0x80808080.toInt()
            canvas.drawCircle(
                btn.rect.centerX(), btn.rect.centerY(), btn.rect.width() / 2f,
                if (btn.pressed) btnPressedPaint else btnPaint
            )
            val label = if (btn === btnFire && mode == 0) "OK" else btn.label
            val cy = btn.rect.centerY() - (btnTextPaint.descent() + btnTextPaint.ascent()) / 2
            canvas.drawText(label, btn.rect.centerX(), cy, btnTextPaint)
        }
    }

    private fun drawHint(canvas: Canvas) {
        val mode = if (DoomEngine.inputMode() == 1) HINT_GAME else HINT_MENU
        val pad = 16f
        canvas.drawRect(0f, 0f, width.toFloat(), 54f, hintBgPaint)
        canvas.drawText(mode, pad, 38f, hintPaint)
    }

    private fun drawMessage(canvas: Canvas, text: String) {
        canvas.drawColor(0xFF101010.toInt())
        val pad = 24f
        canvas.drawText(text, pad, height / 2f, hintPaint)
    }

    // ------------------------------------------------------------------
    // Сенсор: кнопки + свайпы
    // ------------------------------------------------------------------

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downTime = System.currentTimeMillis()
                downX = event.x
                downY = event.y
                val btn = hitButton(event.x, event.y)
                if (btn != null) {
                    pointerButtons[event.getPointerId(event.actionIndex)] = btn
                    pressButton(btn)
                } else {
                    startSwipe(event, event.actionIndex)
                }
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                val idx = event.actionIndex
                val btn = hitButton(event.getX(idx), event.getY(idx))
                if (btn != null) {
                    pointerButtons[event.getPointerId(idx)] = btn
                    pressButton(btn)
                } else {
                    startSwipe(event, idx)
                }
            }

            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) {
                    val id = event.getPointerId(i)
                    val btn = pointerButtons[id]
                    if (btn != null) {
                        // Палец может соскользнуть с кнопки — отпускаем.
                        if (!btn.rect.contains(event.getX(i), event.getY(i))) {
                            releaseButton(btn)
                            pointerButtons.remove(id)
                        }
                    } else if (id == rightPtr) {
                        moveRight(event.getX(i))
                    }
                }
            }

            MotionEvent.ACTION_POINTER_UP -> {
                val id = event.getPointerId(event.actionIndex)
                pointerButtons.remove(id)?.let { releaseButton(it) }
                endSwipe(event, event.actionIndex)
            }

            MotionEvent.ACTION_UP -> {
                val id = event.getPointerId(event.actionIndex)
                pointerButtons.remove(id)?.let { releaseButton(it) }
                endSwipe(event, event.actionIndex)
                if (System.currentTimeMillis() - downTime < TAP_TIME_MS &&
                    (event.x - downX) * (event.x - downX) +
                    (event.y - downY) * (event.y - downY) < TAP_SLOP * TAP_SLOP &&
                    hitButton(downX, downY) == null
                ) {
                    onTap()
                }
            }

            MotionEvent.ACTION_CANCEL -> {
                pointerButtons.values.forEach { releaseButton(it) }
                pointerButtons.clear()
                rightPtr = -1
            }
        }
        return true
    }

    private fun startSwipe(event: MotionEvent, index: Int) {
        val id = event.getPointerId(index)
        // Свайп — только поворот, и только в правой половине вне кнопок.
        // Движение и стрейф — на D-pad: свайп-«мышь» слева убрана —
        // именно она давала рывок вперёд при промахе по кнопкам.
        if (event.getX(index) >= width / 2f && rightPtr == -1) {
            rightPtr = id
            rightStartX = event.getX(index)
            rightLastX = rightStartX
            rightEngaged = false
        }
    }

    private fun endSwipe(event: MotionEvent, index: Int) {
        val id = event.getPointerId(event.actionIndex)
        if (id == rightPtr) {
            rightPtr = -1
        }
    }

    private fun moveRight(x: Float) {
        if (!rightEngaged) {
            // Мёртвая зона: поворот включается после заметного движения.
            if (kotlin.math.abs(x - rightStartX) < 12f * density) return
            rightEngaged = true
            rightLastX = x
            return
        }
        val dx = x - rightLastX
        rightLastX = x
        if (dx != 0f) DoomEngine.mouseMove((dx * 2.2f).toInt(), 0)
    }

    private fun onTap() {
        if (DoomEngine.inputMode() == 0) {
            DoomEngine.keyTap(DoomEngine.KEY_ENTER)
        }
    }

    // ------------------------------------------------------------------
    // Кнопки громкости
    // ------------------------------------------------------------------

    /**
     * Логика кнопок громкости. Живёт в companion, чтобы хук Activity
     * мог доставлять события даже между ребиндами вьюшки.
     */
    class VolumeKeys {
        private val handler = Handler(Looper.getMainLooper())
        private var view: DoomView? = null

        private var up = false
        private var down = false
        private var downAt = 0L
        private var longFired = false
        private var fireHeld = false

        private val repeatUp = Runnable { sendRepeat(DoomEngine.KEY_UP_ARROW) }
        private val repeatDown = Runnable { sendRepeat(DoomEngine.KEY_DOWN_ARROW) }
        private val longEsc = Runnable {
            longFired = true
            DoomEngine.keyTap(DoomEngine.KEY_ESCAPE)
        }

        fun attach(v: DoomView) {
            view = v
        }

        fun detach(v: DoomView) {
            if (view === v) view = null
            releaseAll()
        }

        /** true — событие обработано (громкость меняться не должна). */
        fun onKey(keyCode: Int, isDown: Boolean): Boolean {
            val isUp = keyCode == KeyEvent.KEYCODE_VOLUME_UP
            val isDownKey = keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
            if (!isUp && !isDownKey) return false
            if (!DoomEngine.started || view?.isAttachedToWindow != true) return false
            if (!DoomEngine.isAlive) return false

            if (isDown) {
                if (isUp) up = true else down = true
                if (up && down) {
                    cancelRepeats()
                    if (DoomEngine.inputMode() == 0) {
                        DoomEngine.keyTap(DoomEngine.KEY_ESCAPE)
                    }
                    return true
                }
                if (isUp) {
                    if (DoomEngine.inputMode() == 1) {
                        fireHeld = true
                        DoomEngine.key(DoomEngine.KEY_CTRL, true)
                    } else {
                        sendRepeat(DoomEngine.KEY_UP_ARROW)
                        handler.postDelayed(repeatUp, MENU_REPEAT_MS)
                    }
                } else {
                    downAt = System.currentTimeMillis()
                    longFired = false
                    if (DoomEngine.inputMode() == 1) {
                        handler.postDelayed(longEsc, ESC_LONG_MS)
                    } else {
                        sendRepeat(DoomEngine.KEY_DOWN_ARROW)
                        handler.postDelayed(repeatDown, MENU_REPEAT_MS)
                    }
                }
            } else {
                if (isUp) {
                    up = false
                    handler.removeCallbacks(repeatUp)
                    if (fireHeld) {
                        fireHeld = false
                        DoomEngine.key(DoomEngine.KEY_CTRL, false)
                    }
                } else {
                    down = false
                    handler.removeCallbacks(repeatDown)
                    handler.removeCallbacks(longEsc)
                    if (!longFired && DoomEngine.inputMode() == 1 &&
                        System.currentTimeMillis() - downAt < ESC_LONG_MS
                    ) {
                        DoomEngine.keyTap(DoomEngine.KEY_SPACE)
                    }
                    longFired = false
                }
            }
            return true
        }

        private fun sendRepeat(key: Int) {
            DoomEngine.keyTap(key)
        }

        private fun cancelRepeats() {
            handler.removeCallbacks(repeatUp)
            handler.removeCallbacks(repeatDown)
            handler.removeCallbacks(longEsc)
        }

        private fun releaseAll() {
            cancelRepeats()
            up = false
            down = false
            fireHeld = false
            longFired = false
            DoomEngine.key(DoomEngine.KEY_CTRL, false)
            DoomEngine.key(DoomEngine.KEY_COMMA, false)
            DoomEngine.key(DoomEngine.KEY_PERIOD, false)
        }
    }
}

private const val HINT_GAME = "Кнопки на экране · Vol+ — огонь · Vol- — открыть"
private const val HINT_MENU = "▲▼ — выбор · FIRE (OK) — Enter · USE — Esc"
