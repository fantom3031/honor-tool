package com.fantom3031.honortool.hooks.systemui

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.RelativeLayout
import android.widget.TextView
import com.fantom3031.honortool.R
import com.fantom3031.honortool.core.BaseHook
import com.fantom3031.honortool.core.XLog
import java.util.Locale
import java.lang.ref.WeakReference

/**
 * Доп. информация о зарядке на заблокированном экране.
 *
 * Отдельный двухстрочный блок внизу локскрина. Не зависит от анимации
 * ChargingStatusTextView и переключения зарядка/дата в контроллере Honor.
 */
object LockScreenChargingHook : BaseHook() {

    const val ID = "systemui_charging_info"

    private const val SYSTEMUI_PACKAGE = "com.android.systemui"
    private const val UPPER_CONTROLLER = "com.android.keyguard.hwlockscreen.ClockStyleUpperController"
    private const val DETAILS_TAG = "honortool:lockscreen_charging_details"
    private const val STACK_LAYOUT =
        "com.android.systemui.statusbar.notification.stack.NotificationStackScrollLayout"

    override val id: String = ID
    override val titleRes: Int = R.string.hook_charging_info_title
    override val descriptionRes: Int = R.string.hook_charging_info_description
    override val targetPackages: Set<String> = setOf(SYSTEMUI_PACKAGE)
    override val enabledByDefault: Boolean = true

    override fun onHook() {
        val controller = findClassOrNull(UPPER_CONTROLLER) ?: run {
            XLog.w("[$id] класс $UPPER_CONTROLLER не найден")
            return
        }

        for (method in listOf("init", "updateChargeViewText", "updateBatteryInfo",
            "showCharge", "showDateOfCharge", "showDateView")) {
            controller.hookAllAfter(method) { param ->
                val self = param.thisObject ?: return@hookAllAfter
                updateDetails(self)
                if (method == "init") {
                    val owner = WeakReference(self)
                    (self.fieldOrNull("mDateLunarView") as? View)?.post {
                        owner.get()?.let { updateDetails(it) }
                    }
                }
            }
        }
        XLog.i("[$id] постоянный блок зарядки внизу локскрина установлен")
    }

    private fun updateDetails(self: Any) {
        val native = self.fieldOrNull("mChargeStatusView") as? TextView ?: return
        val date = self.fieldOrNull("mDateLunarView") as? View ?: return
        val parent = findBottomContainer(date) ?: return
        val details = parent.findViewWithTag<TextView>(DETAILS_TAG)
            ?: createDetails(self, native, parent) ?: return
        val power = native.context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        if (power?.isInteractive == false) {
            details.visibility = View.GONE
            return
        }
        val battery = native.context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val plugged = (battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
        if (!plugged) {
            details.visibility = View.GONE
            details.text = ""
            return
        }
        // Блок принадлежит локскрину: вне его (шторка в приложении, heads-up и т.д.) — скрыт.
        if (!keyguardContentVisible(date, parent)) {
            details.visibility = View.GONE
            return
        }
        // Уведомления на локскрине и раскрытая шторка занимают низ экрана.
        if (notificationsCoverBottom(details)) {
            details.visibility = View.GONE
            return
        }

        // Штатная анимация может скрыть и очистить свою надпись; наш блок независим.
        native.visibility = View.INVISIBLE
        date.visibility = View.VISIBLE
        val status = (self.fieldOrNull("mChargingInfo") as? String)?.takeIf { it.isNotBlank() }
            ?: battery?.let {
                val level = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = it.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                if (level >= 0 && scale > 0) "${level * 100 / scale}%" else null
            }.orEmpty()
        val prefix = if (self.callOrNull("isQuickCharging") == true) "⚡ " else ""
        val extra = getChargingDetails(native.context)
        details.text = if (extra.isBlank()) "$prefix$status" else "$prefix$status\n$extra"
        details.setTextColor(native.textColors)
        details.typeface = native.typeface
        details.visibility = View.VISIBLE
    }

    private fun findBottomContainer(date: View): ViewGroup? {
        val screenHeight = date.resources.displayMetrics.heightPixels
        var ancestor = date.parent
        while (ancestor is ViewGroup) {
            // Первый полноэкранный контейнер локскрина, а не корень окна SystemUI.
            if (ancestor.height >= screenHeight * 0.7f &&
                (ancestor is FrameLayout || ancestor is RelativeLayout)) return ancestor
            ancestor = ancestor.parent
        }
        // При init размеры ещё не измерены; следующий update/таймер повторит поиск.
        return null
    }

    /**
     * Виден ли путь от области часов локскрина до контейнера блока:
     * вне локскрина (шторка поверх приложения, heads-up) цепочка скрыта
     * или затухает (раскрытие шторки на локскрине).
     */
    private fun keyguardContentVisible(date: View, container: ViewGroup): Boolean {
        if (!date.isAttachedToWindow) return false
        var v: View? = date.parent as? View ?: return false
        while (v != null) {
            if (v === container) return true
            if (v.visibility != View.VISIBLE || v.alpha < 0.5f) return false
            v = v.parent as? View
        }
        return false
    }

    /** Есть ли видимые уведомления в стеке (локскрин или раскрытая шторка). */
    private fun notificationsCoverBottom(details: TextView): Boolean {
        val stack = findStackView(details.parent as? ViewGroup)
            ?: findStackView(details.rootView as? ViewGroup)
            ?: return false
        if (!stack.isShown) return false
        for (i in 0 until stack.childCount) {
            val child = stack.getChildAt(i)
            if (child.visibility == View.VISIBLE && child.height > 0) return true
        }
        return false
    }

    private fun findStackView(root: ViewGroup?): ViewGroup? {
        var current = root ?: return null
        val queue = ArrayDeque<ViewGroup>()
        queue.add(current)
        while (queue.isNotEmpty()) {
            val group = queue.removeFirst()
            if (group.javaClass.name == STACK_LAYOUT) return group
            for (i in 0 until group.childCount) {
                (group.getChildAt(i) as? ViewGroup)?.let { queue.add(it) }
            }
        }
        return null
    }

    private fun createDetails(self: Any, native: TextView, parent: ViewGroup): TextView? {
        val density = native.resources.displayMetrics.density
        val bottomGap = (96 * density).toInt() +
            (parent.rootWindowInsets?.stableInsetBottom ?: 0)
        val sideGap = (24 * density).toInt()
        val params = when {
            parent is FrameLayout ->
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
                    bottomMargin = bottomGap
                    leftMargin = sideGap
                    rightMargin = sideGap
                }
            parent is RelativeLayout ->
                RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
                    addRule(RelativeLayout.CENTER_HORIZONTAL)
                    bottomMargin = bottomGap
                    leftMargin = sideGap
                    rightMargin = sideGap
                }
            else -> {
                XLog.w("[$id] неподдерживаемый контейнер даты: ${parent.javaClass.name}")
                return null
            }
        }
        val details = TextView(native.context).apply {
            tag = DETAILS_TAG
            gravity = Gravity.CENTER
            setSingleLine(false)
            setLines(2)
            ellipsize = null
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setAutoSizeTextTypeUniformWithConfiguration(10, 14, 1, TypedValue.COMPLEX_UNIT_SP)
            val gap = (4 * resources.displayMetrics.density).toInt()
            setPadding(native.paddingLeft, gap, native.paddingRight, gap)
            visibility = View.GONE
        }
        // Не удерживаем уничтоженный контроллер через очередь сообщений UI.
        val owner = WeakReference(self)
        val refresh = object : Runnable {
            override fun run() {
                if (!details.isAttachedToWindow) return
                val controller = owner.get() ?: return
                try {
                    updateDetails(controller)
                } catch (t: Throwable) {
                    XLog.e("[$id] обновление зарядки не удалось", t)
                }
                details.postDelayed(this, 2000)
            }
        }
        details.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                v.removeCallbacks(refresh)
                v.post(refresh)
            }
            override fun onViewDetachedFromWindow(v: View) {
                v.removeCallbacks(refresh)
            }
        })
        parent.addView(details, params)
        return details
    }

    private fun getChargingDetails(ctx: Context): String {
        val parts = mutableListOf<String>()
        try {
            val intent = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return ""
            val voltageMv = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)
            val tempTenths = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
            val currentNow = try {
                (ctx.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager)
                    ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) ?: 0
            } catch (_: Throwable) { 0 }
            val currentAbs = if (currentNow == Int.MIN_VALUE) 0L else kotlin.math.abs(currentNow.toLong())
            val currentMa = (if (currentAbs > 10_000) currentAbs / 1000 else currentAbs).toInt()
            val watts = if (currentMa > 0 && voltageMv > 0)
                (currentMa.toLong() * voltageMv) / 1_000_000.0f else 0.0f

            if (watts > 0.01f) parts.add(String.format(Locale.US, "%.1fW", watts))
            if (currentMa > 0) parts.add("${currentMa}mA")
            if (voltageMv > 0) parts.add(String.format(Locale.US, "%.1fV", voltageMv / 1000.0f))
            if (tempTenths > 0) parts.add(String.format(Locale.US, "%.1f°C", tempTenths / 10.0f))
        } catch (_: Throwable) {}
        return parts.joinToString(" · ")
    }
}
