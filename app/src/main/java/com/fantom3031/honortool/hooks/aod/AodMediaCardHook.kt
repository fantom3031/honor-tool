package com.fantom3031.honortool.hooks.aod

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ProgressBar
import android.content.res.ColorStateList
import android.os.SystemClock
import com.fantom3031.honortool.R
import com.fantom3031.honortool.core.BaseHook
import com.fantom3031.honortool.core.XLog
import kotlin.math.abs

/**
 * Карточка «что играет» на Always-On Display.
 *
 * AodApHost.initAodView добавляет основной экран AOD; карточка встраивается
 * туда же и обновляется на события медиа-сессий (как в прошивочном
 * MediaPlayerMonitor: слушатель MediaSessionManager с ComponentName=null).
 * После каждого изменения принудительно перерисовывается цепочка видов
 * до корня — окно AOD перерисовывается редко, само оно карточку не обновит.
 */
object AodMediaCardHook : BaseHook() {

    const val ID = "aod_media_card"

    private const val AOD_HOST = "com.hihonor.aod.doze.AodApHost"
    private const val CARD_TAG = "honortool:aod_media_card"

    /** Отступ карточки от низа контента AOD. */
    private const val CARD_BOTTOM_MARGIN_DP = 64

    /** Период опроса медиа-сессий на AOD. */
    private const val POLL_INTERVAL_MS = 3000L
    private const val PAUSE_KEEP_MS = 15_000L
    private var pausedAt = 0L
    private var lastPlaybackState = PlaybackState.STATE_NONE
    private var progressView: ProgressBar? = null
    private var timeView: TextView? = null

    private val progressTick = object : Runnable {
        override fun run() {
            val card = cardView ?: return
            if (!card.isAttachedToWindow || !card.isShown || rootView?.isShown == false) return
            refresh()
            mainHandler.removeCallbacks(this)
            if (card.visibility == View.VISIBLE) mainHandler.postDelayed(this, 1000L)
        }
    }

    override val id = ID
    override val titleRes = R.string.aod_media_card_title
    override val descriptionRes = R.string.aod_media_card_description
    override val targetPackages = setOf("com.hihonor.aod")
    override val enabledByDefault = false

    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile private var appContext: Context? = null
    @Volatile private var sessionManager: MediaSessionManager? = null
    @Volatile private var activeController: MediaController? = null
    @Volatile private var rootView: ViewGroup? = null

    @Volatile private var cardView: LinearLayout? = null
    @Volatile private var titleView: TextView? = null
    @Volatile private var artistView: TextView? = null
    @Volatile private var iconView: ImageView? = null

    private val controllerCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            refresh()
        }

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            refresh()
        }
    }

    /** Последнее состояние для журнала и защиты от спама синхронизаций. */
    private var lastLoggedState = ""
    private var lastSyncAt = 0L

    override fun onHook() {
        findClass(AOD_HOST).hookAfter("initAodView") { p ->
            val self = p.thisObject ?: return@hookAfter
            val targetRoot = (self.fieldOrNull("mTargetViews") as? Array<*>)
                ?.filterIsInstance<ViewGroup>()?.firstOrNull()
            val container = self.fieldOrNull("mDisplayOnscreenView") as? ViewGroup
            val parent = targetRoot ?: container ?: return@hookAfter
            val context = parent.context
            appContext = context.applicationContext
            rootView = container
            attachCard(context, parent)
            startMonitoring(context)
        } ?: XLog.w("[$id] initAodView не найден")

        // AOD сам перерисовывается по тику часов (раз в минуту): синхронизируем
        // карточку тем же кадром — пауза/возобновление возвращаются максимум
        // через минуту, даже если событие медиа не пробило перерисовку.
        findClassOrNull("com.hihonor.aod.doze.BaseViewController")?.hookAfter("onTimeTick") {
            syncNow()
        } ?: XLog.w("[$id] onTimeTick не найден")

        XLog.i("[$id] карточка медиа на AOD готова")
    }

    private fun attachCard(context: Context, parent: ViewGroup) {
        // AOD мог пересоздать свои виды: переносим карточку в актуальный родитель.
        val existing = cardView
        if (existing != null && existing.parent === parent) {
            refresh()
            return
        }
        existing?.let { (it.parent as? ViewGroup)?.removeView(it) }
        val density = context.resources.displayMetrics.density

        val card = existing ?: buildCard(context, density)
        val params = when (parent) {
            is FrameLayout -> FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            ).apply { bottomMargin = (CARD_BOTTOM_MARGIN_DP * density).toInt() }

            is LinearLayout -> LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL }

            else -> ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        runCatching { parent.addView(card, params) }
            .onFailure {
                XLog.w("[$id] параметры не подошли ${parent.javaClass.simpleName}, добавляю как есть")
                parent.addView(card)
            }
        XLog.d("[$id] карточка добавлена в ${parent.javaClass.simpleName}")
        refresh()
    }

    private fun buildCard(context: Context, density: Float): LinearLayout {
        val icon = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                (40 * density).toInt(), (40 * density).toInt()
            )
        }
        val title = TextView(context).apply {
            setTextColor(Color.argb(235, 255, 255, 255))
            typeface = Typeface.DEFAULT_BOLD
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            maxLines = 1
        }
        val artist = TextView(context).apply {
            setTextColor(Color.argb(150, 255, 255, 255))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            maxLines = 1
        }
        val textColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(title)
            addView(artist)
            val progress = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 1000
                progressTintList = ColorStateList.valueOf(Color.WHITE)
                progressBackgroundTintList = ColorStateList.valueOf(0x40FFFFFF)
            }
            val time = TextView(context).apply {
                setTextColor(0xAAFFFFFF.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            }
            addView(progress, LinearLayout.LayoutParams((180 * density).toInt(), (4 * density).toInt()))
            addView(time)
            progressView = progress
            timeView = time
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { leftMargin = (12 * density).toInt() }
        }
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            tag = CARD_TAG
            visibility = View.GONE
            addView(icon)
            addView(textColumn)
            addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    mainHandler.removeCallbacks(progressTick)
                    mainHandler.post(progressTick)
                }
                override fun onViewDetachedFromWindow(v: View) {
                    mainHandler.removeCallbacks(progressTick)
                }
            })
        }
        cardView = card
        titleView = title
        artistView = artist
        iconView = icon
        return card
    }

    private fun startMonitoring(context: Context) {
        // Стандартный слушатель сессий на MagicOS отдаёт не все приложения —
        // опрашиваем прошивочный Ex-метод по кругу: он видит всё.
        sessionManager = context.getSystemService(MediaSessionManager::class.java) ?: return
        mainHandler.removeCallbacks(pollRunnable)
        mainHandler.post(pollRunnable)
    }

    private val pollRunnable = object : Runnable {
        override fun run() {
            val context = appContext
            sessionManager?.let { manager ->
                context?.let { ctx -> fetchActiveSessions(ctx, manager)?.let { rebind(it) } }
            }
            mainHandler.postDelayed(this, POLL_INTERVAL_MS)
        }
    }

    private fun fetchActiveSessions(context: Context, manager: MediaSessionManager): List<MediaController>? =
        runCatching {
            val ex = Class.forName("com.hihonor.android.media.session.MediaSessionManagerEx")
            val method = ex.getMethod(
                "getActiveSessionsForUser",
                MediaSessionManager::class.java,
                android.content.ComponentName::class.java,
                Int::class.javaPrimitiveType
            )
            // Передаём включённый notification-листенер — запрос становится доверенным
            // (как у dynamic island) и видны сессии всех приложений, включая браузеры.
            val cn = enabledListenerComponent(context)
            @Suppress("UNCHECKED_CAST")
            method.invoke(null, manager, cn, -1) as? List<MediaController>
        }.onFailure {
            XLog.w("[$id] не удалось опросить текущие медиа-сессии: ${it.message}")
        }.getOrNull()

    /** Первый включённый notification-листенер — доверенный источник для опроса сессий. */
    private fun enabledListenerComponent(context: Context): android.content.ComponentName? = runCatching {
        // Settings.Secure.ENABLED_NOTIFICATION_LISTENERS скрыт в SDK — используем строку.
        val flat = android.provider.Settings.Secure.getString(
            context.contentResolver, "enabled_notification_listeners"
        )
        flat?.split(":")?.firstNotNullOfOrNull { android.content.ComponentName.unflattenFromString(it) }
    }.getOrNull()

    private fun rebind(controllers: List<MediaController>) {
        // Может быть несколько «играющих» сессий: выбираем самую свежую по времени
        // обновления позиции воспроизведения — она и играет по-настоящему.
        val playing = controllers.filter { it.playbackState?.state == PlaybackState.STATE_PLAYING }
        val target = playing.maxByOrNull { it.playbackState?.lastPositionUpdateTime ?: 0L }
            ?: activeController?.takeIf { old ->
                controllers.any { it.sessionToken == old.sessionToken } &&
                    old.playbackState?.state == PlaybackState.STATE_PAUSED
            }
        if (target?.sessionToken != activeController?.sessionToken) {
            activeController?.unregisterCallback(controllerCallback)
            target?.registerCallback(controllerCallback, mainHandler)
            activeController = target
            pausedAt = 0L
            lastPlaybackState = PlaybackState.STATE_NONE
            XLog.d("[$id] сессий: ${controllers.size}, играющих: ${playing.size}, выбран: ${target?.packageName ?: "нет"}")
        }
        refresh()
    }

    /** Синхронизация на тике часов AOD — не чаще раза в секунду. */
    private fun syncNow() {
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastSyncAt < 1000L) return
        lastSyncAt = now
        refresh()
    }

    private fun refresh() {
        val controller = activeController
        val title = controller?.metadata
            ?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?.takeIf { it.isNotBlank() }
        if (controller == null || title == null) {
            cardView?.visibility = View.GONE
        } else {
            val playback = controller.playbackState
            val playing = playback?.state == PlaybackState.STATE_PLAYING
            val paused = playback?.state == PlaybackState.STATE_PAUSED
            if (paused && lastPlaybackState != PlaybackState.STATE_PAUSED) pausedAt = SystemClock.elapsedRealtime()
            if (playing) pausedAt = 0L
            lastPlaybackState = playback?.state ?: PlaybackState.STATE_NONE
            val keepPaused = paused && pausedAt > 0L && SystemClock.elapsedRealtime() - pausedAt < PAUSE_KEEP_MS
            val artist = controller.metadata
                ?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?.takeIf { it.isNotBlank() }
                ?: appLabel(controller.packageName)
            titleView?.text = title
            artistView?.text = artist.orEmpty()
            iconView?.let { view ->
                runCatching {
                    val metadata = controller.metadata
                    val art = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
                        ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                    if (art != null) {
                        view.scaleType = ImageView.ScaleType.CENTER_CROP
                        view.setImageBitmap(scaleArt(art))
                    } else {
                        view.scaleType = ImageView.ScaleType.FIT_CENTER
                        appContext?.packageManager
                            ?.getApplicationIcon(controller.packageName)?.let { view.setImageDrawable(it) }
                    }
                }
            }
            val duration = controller.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
            val base = playback?.position?.coerceAtLeast(0L) ?: 0L
            val elapsed = if (playing && (playback?.lastPositionUpdateTime ?: 0L) > 0L)
                ((SystemClock.elapsedRealtime() - playback!!.lastPositionUpdateTime).coerceAtLeast(0L) * playback.playbackSpeed).toLong()
            else 0L
            val position = (base + elapsed).coerceAtLeast(0L).let { if (duration > 0L) it.coerceAtMost(duration) else it }
            progressView?.visibility = if (duration > 0L) View.VISIBLE else View.GONE
            timeView?.visibility = if (duration > 0L) View.VISIBLE else View.GONE
            if (duration > 0L) {
                progressView?.progress = (position.toDouble() / duration * 1000).toInt().coerceIn(0, 1000)
                timeView?.text = "${formatTime(position)} / ${formatTime(duration)}"
            }
            cardView?.alpha = if (keepPaused) 0.45f else 1f
            cardView?.visibility = if (playing || keepPaused) View.VISIBLE else View.GONE
            if (cardView?.isShown == true && rootView?.isShown != false && !mainHandler.hasCallbacks(progressTick)) {
                mainHandler.postDelayed(progressTick, 1000L)
            }
        }
        val state = "${title ?: "нет"}|${cardView?.visibility == View.VISIBLE}"
        if (state != lastLoggedState) {
            lastLoggedState = state
            XLog.d("[$id] трек: \"${title ?: "нет"}\", карточка видима: ${cardView?.visibility == View.VISIBLE}")
        }
        forceRedraw()
    }

    /** Окно AOD перерисовывается по своему расписанию — требуем кадр сами. */
    private fun forceRedraw() {
        val card = cardView ?: return
        card.requestLayout()
        var v: View? = card
        while (v != null) {
            v.postInvalidate()
            v = v.parent as? View
        }
        rootView?.postInvalidateOnAnimation()
    }

    private fun appLabel(packageName: String): String? = runCatching {
        val pm = appContext?.packageManager ?: return null
        pm.getApplicationInfo(packageName, 0).loadLabel(pm).toString()
    }.getOrNull()

    private fun formatTime(ms: Long): String {
        val seconds = ms / 1000
        return if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60)
        else "%d:%02d".format(seconds / 60, seconds % 60)
    }

    /** Обложка уменьшается до 120 px по длинной стороне — экономим память процесса AOD. */
    private fun scaleArt(src: android.graphics.Bitmap): android.graphics.Bitmap {
        val size = 120
        val longest = maxOf(src.width, src.height)
        if (longest <= size) return src
        val k = size.toFloat() / longest
        return android.graphics.Bitmap.createScaledBitmap(
            src,
            (src.width * k).toInt().coerceAtLeast(1),
            (src.height * k).toInt().coerceAtLeast(1),
            true
        )
    }
}
