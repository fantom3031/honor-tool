package com.fantom3031.honortool.hooks.systemui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.text.format.DateFormat
import android.widget.TextView
import com.fantom3031.honortool.R
import com.fantom3031.honortool.core.BaseHook
import com.fantom3031.honortool.core.XLog
import java.util.Calendar
import java.util.IdentityHashMap

/** Seconds for Honor's status-bar clock, without changing TimeManager's other clients. */
object StatusBarSecondsHook : BaseHook() {
    override val id = "systemui_status_bar_seconds"
    override val titleRes = R.string.status_bar_seconds_title
    override val descriptionRes = R.string.status_bar_seconds_description
    override val targetPackages = setOf("com.android.systemui")
    override val enabledByDefault = false

    private val clocks = IdentityHashMap<TextView, ClockTicker>()

    override fun onHook() {
        val clock = findClass("com.android.systemui.time.HwStatusBarClock")
        checkNotNull(clock.hookAfter("onAttachedToWindow") { p ->
            val view = p.thisObject as? TextView ?: return@hookAfter
            if (!clocks.containsKey(view)) {
                val ticker = ClockTicker(view)
                ticker.start()
                clocks[view] = ticker
                XLog.i("[$id] Honor status-bar clock attached")
            }
        })
        checkNotNull(clock.hookAfter("onDetachedFromWindow") { p ->
            (p.thisObject as? TextView)?.let { clocks.remove(it)?.stop() }
        })
        // Honor overwrites the label on minute/time broadcasts. Restore seconds immediately.
        checkNotNull(clock.hookAfter("onTimeChange", Long::class.javaPrimitiveType) { p ->
            (p.thisObject as? TextView)?.let { clocks[it]?.refresh() }
        })
        XLog.i("[$id] Honor clock seconds hooks installed")
    }

    private class ClockTicker(private val view: TextView) {
        private val handler = Handler(Looper.getMainLooper())
        private val power = view.context.getSystemService(PowerManager::class.java)
        private var registered = false
        private val tick = object : Runnable {
            override fun run() {
                if (!view.isAttachedToWindow || power?.isInteractive != true) return
                refresh()
                handler.postDelayed(this, 1000L - System.currentTimeMillis().mod(1000L))
            }
        }
        private val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                handler.removeCallbacks(tick)
                if (intent.action != Intent.ACTION_SCREEN_OFF) tick.run()
            }
        }

        fun start() {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
                addAction(Intent.ACTION_LOCALE_CHANGED)
                addAction(Intent.ACTION_CONFIGURATION_CHANGED)
            }
            view.context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            registered = true
            tick.run()
        }

        fun refresh() {
            if (!view.isAttachedToWindow || power?.isInteractive != true) return
            val locale = view.resources.configuration.locales[0]
            val skeleton = if (DateFormat.is24HourFormat(view.context)) "Hms" else "hms"
            val pattern = DateFormat.getBestDateTimePattern(locale, skeleton)
            view.text = DateFormat.format(pattern, Calendar.getInstance())
        }

        fun stop() {
            handler.removeCallbacks(tick)
            if (registered) {
                view.context.unregisterReceiver(receiver)
                registered = false
            }
        }
    }
}
