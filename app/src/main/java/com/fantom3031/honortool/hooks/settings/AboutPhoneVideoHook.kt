package com.fantom3031.honortool.hooks.settings

import android.view.KeyEvent
import android.view.ViewGroup
import com.fantom3031.honortool.R
import com.fantom3031.honortool.core.BaseHook
import com.fantom3031.honortool.core.Constants
import com.fantom3031.honortool.core.HookEntry
import com.fantom3031.honortool.core.HookImages
import com.fantom3031.honortool.core.HookParam
import com.fantom3031.honortool.core.HookPrefs
import com.fantom3031.honortool.core.ImageKeys
import com.fantom3031.honortool.core.XLog
import com.fantom3031.honortool.core.setField
import com.fantom3031.honortool.doom.DoomEngine
import com.fantom3031.honortool.doom.DoomView
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.Uri
import android.view.View
import java.io.File
import java.util.Collections

/**
 * Анимация карточки «О телефоне» (`magic_anim`) в настройках Honor.
 *
 * `com.android.settings.CustomLogoPreference` проигрывает видео через
 * `MediaPlayer.setDataSource(Context, Uri)` c `android.resource://com.android.settings/<id>`
 * в `HwTextureView` и держит рядом `HwImageView`.
 *
 * Три режима:
 *  - видео: `setDataSource` подменяется на файл из [Constants.ABOUT_VIDEO_PATH_KEY];
 *  - фото: видео-вью скрывается, `HwImageView` растягивается на ширину карточки
 *    и получает картинку пользователя (ключ [ImageKeys.ABOUT_PHONE_ROM]);
 *  - DOOM: все штатные вью скрываются, в карточку вставляется [DoomView] —
 *    нативный PureDOOM (libdoomhook.so), кадры идут вместо видео. Управление:
 *    свайпы по карточке + кнопки громкости (перехватываются в этом же процессе).
 */
object AboutPhoneVideoHook : BaseHook() {

    const val ID = "settings_about_phone_video"

    /** Размер баннера для фото-режима (обрезка в CropScreen). */
    const val BANNER_WIDTH = 720
    const val BANNER_HEIGHT = 360

    private const val SETTINGS_PACKAGE = "com.android.settings"
    private const val LOGO_PREFERENCE_CLASS = "com.android.settings.CustomLogoPreference"
    private const val DOOM_VIEW_TAG = "honortool_doom_view"

    /** Уже залогированные URI — каждый пишем один раз. */
    private val loggedUris = Collections.synchronizedSet(HashSet<String>())

    /** Уже залогированные комбинации (тема, режим) биндинга. */
    private val loggedBinds = Collections.synchronizedSet(HashSet<String>())

    override val id: String = ID

    override val titleRes: Int = R.string.hook_about_phone_video_title

    override val descriptionRes: Int = R.string.hook_about_phone_video_description

    override val targetPackages: Set<String> = setOf(SETTINGS_PACKAGE)

    override val enabledByDefault: Boolean = false

    override fun onHook() {
        hookVideoSource()
        hookPhotoMode()
        hookVolumeKeys()
    }

    private fun mode(): String =
        HookPrefs.getString(Constants.ABOUT_VIDEO_MODE_KEY, Constants.ABOUT_VIDEO_MODE_VIDEO)

    private fun isPhotoMode(): Boolean = mode() == Constants.ABOUT_VIDEO_MODE_PHOTO

    private fun isDoomMode(): Boolean = mode() == Constants.ABOUT_VIDEO_MODE_DOOM

    /** Видео-режим: подмена источника MediaPlayer на файл пользователя. */
    private fun hookVideoSource() {
        val mediaPlayer = findClassOrNull("android.media.MediaPlayer") ?: run {
            XLog.w("[$id] android.media.MediaPlayer не найден")
            return
        }
        mediaPlayer.hookAllBefore("setDataSource") { param ->
            // Диагностика: каждый уникальный URI (любой схемы) — в лог.
            // Для ресурсов настроек добавляем стек вызова: он показывает,
            // какой класс играет видео (важно для светлой темы).
            val diagUri = param.args.firstOrNull { it is Uri } as? Uri
            if (diagUri != null && loggedUris.add(diagUri.toString())) {
                val stack = if (diagUri.host == SETTINGS_PACKAGE) {
                    val frames = try {
                        Throwable().stackTrace
                    } catch (_: Throwable) {
                        emptyArray()
                    }
                    frames.filter {
                        it.className.startsWith("com.android.settings") ||
                            it.className.startsWith("com.hihonor")
                    }.take(8).joinToString(" <- ") { "${it.className}.${it.methodName}" }
                } else ""
                XLog.i("[$id] setDataSource uri=$diagUri $stack")
            }

            if (isPhotoMode() || isDoomMode()) return@hookAllBefore
            val index = param.args.indexOfFirst { it is Uri }
            if (index < 0) return@hookAllBefore
            val uri = param.args[index] as Uri
            if (uri.scheme != "android.resource" || uri.host != SETTINGS_PACKAGE) return@hookAllBefore

            val path = HookPrefs.getString(Constants.ABOUT_VIDEO_PATH_KEY, Constants.DEFAULT_ABOUT_VIDEO_PATH)
            val file = File(path)
            if (file.isFile && file.canRead()) {
                param.args[index] = Uri.fromFile(file)
                XLog.i("[$id] видео подменено на $path (${file.length()} байт)")
            } else {
                XLog.d("[$id] файл видео не найден: $path")
            }
        }
    }

    /** Фото-режим и DOOM: переключаем содержимое карточки при биндинге. */
    private fun hookPhotoMode() {
        val prefClass = findClassOrNull(LOGO_PREFERENCE_CLASS) ?: run {
            XLog.w("[$id] класс $LOGO_PREFERENCE_CLASS не найден")
            return
        }
        prefClass.hookAllAfter("onBindViewHolder") { param ->
            // hookAllAfter заходит в иерархию и ловит базовый метод всех
            // преференсов — работаем только с нашей карточкой.
            val self = param.thisObject
            if (self == null || !prefClass.isInstance(self)) return@hookAllAfter
            logBind(param)
            when {
                isPhotoMode() -> bindPhoto(param)
                isDoomMode() -> bindDoom(param)
                else -> removeDoomView(param)
            }
        }
        XLog.i("[$id] хук карточки установлен (видео/фото/DOOM)")
    }

    /**
     * Диагностика биндинга: тема (day/night) + режим — один раз на комбинацию.
     * Если на светлой теме этих строк нет — карточку биндит другой класс.
     */
    private fun logBind(param: HookParam) {
        val self = param.thisObject ?: return
        val context = self.fieldOrNull("mContext") as? Context
        val night = context?.resources?.configuration?.let {
            (it.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        } ?: return
        val key = (if (night) "night" else "day") + ":" + mode()
        if (loggedBinds.add(key)) {
            val fields = listOf("i", "m", "o").joinToString(",") { name ->
                self.fieldOrNull(name)?.let { v ->
                    "$name=${v.javaClass.simpleName}"
                } ?: "$name=null"
            }
            XLog.i("[$id] bind CustomLogoPreference theme=$key вьюшки: $fields")
        }
    }

    private fun bindPhoto(param: HookParam) {
        val bitmap = HookImages.bitmap(ImageKeys.ABOUT_PHONE_ROM) ?: run {
            removeDoomView(param)
            return
        }

        val self = param.thisObject ?: return
        removeDoomView(param)

        val textureView = self.fieldOrNull("i")
        try {
            (textureView as? View)?.visibility = View.GONE
        } catch (_: Throwable) {
        }

        // Два HwImageView в карточке: картинку ставим в первый, второй скрываем,
        // иначе получается дубль.
        val main = self.fieldOrNull("m") as? View
        if (main != null) {
            applyBannerBitmap(main, bitmap)
        }
        try {
            (self.fieldOrNull("o") as? View)?.visibility = View.GONE
        } catch (_: Throwable) {
        }
    }

    /** DOOM: прячем штатные вью и вставляем игровую вьюшку. */
    private fun bindDoom(param: HookParam) {
        val self = param.thisObject ?: return
        val main = self.fieldOrNull("m") as? View ?: run {
            XLog.w("[$id] карточка без вьюшек, DOOM не вставить")
            return
        }
        val parent = main.parent as? ViewGroup ?: return

        val textureView = self.fieldOrNull("i")
        try {
            (textureView as? View)?.visibility = View.GONE
        } catch (_: Throwable) {
        }
        try {
            (self.fieldOrNull("o") as? View)?.visibility = View.GONE
        } catch (_: Throwable) {
        }
        main.visibility = View.GONE

        val context = main.context
        var doomView = findDoomView(parent)
        if (doomView == null) {
            doomView = DoomView(context)
            doomView.tag = DOOM_VIEW_TAG
            val screenW = context.resources.displayMetrics.widthPixels
            // Свежие LayoutParams: чужой объект переиспользовать нельзя.
            doomView.layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                screenW * DoomEngine.HEIGHT / DoomEngine.WIDTH
            )
            parent.addView(doomView)
            XLog.i("[$id] DoomView вставлен в карточку (${screenW}x${screenW * DoomEngine.HEIGHT / DoomEngine.WIDTH})")
        } else {
            doomView.visibility = View.VISIBLE
        }

        startEngine(context)
    }

    private fun findDoomView(parent: ViewGroup): DoomView? {
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            if (child is DoomView && child.tag == DOOM_VIEW_TAG) return child
        }
        return null
    }

    private fun removeDoomView(param: HookParam) {
        val self = param.thisObject ?: return
        val main = self.fieldOrNull("m") as? View ?: return
        val parent = main.parent as? ViewGroup ?: return
        val doomView = findDoomView(parent) ?: return
        try {
            parent.removeView(doomView)
            XLog.i("[$id] DoomView удалён из карточки")
        } catch (t: Throwable) {
            XLog.w("[$id] не удалось убрать DoomView: ${t.message}")
        }
        DoomEngine.running = false
    }

    private fun startEngine(context: android.content.Context) {
        val moduleApk = HookEntry.modulePath
        val nativeDir = HookEntry.moduleNativeDir
        if (moduleApk == null || nativeDir == null) {
            XLog.w("[$id] путь модуля недоступен, DOOM не стартует")
            return
        }
        if (!DoomEngine.ensureLibrary(nativeDir)) return

        val cacheDir = context.cacheDir
        val wad = DoomEngine.extractWad(moduleApk, cacheDir)
        if (wad == null) {
            XLog.e("[$id] doom1.wad не найден в APK модуля")
            return
        }
        val saveDir = File(cacheDir, "doom-saves")
        DoomEngine.start(wad, saveDir)
    }

    /** Кнопки громкости становятся управлением DOOM, пока он активен. */
    private fun hookVolumeKeys() {
        val activityClass = findClassOrNull("android.app.Activity") ?: return
        activityClass.hookAllBefore("dispatchKeyEvent") { param ->
            if (!isDoomMode()) return@hookAllBefore
            val event = param.args.getOrNull(0) as? KeyEvent ?: return@hookAllBefore
            // Автоповтор системы игнорируем: повторы в меню делает свой таймер.
            if (event.repeatCount > 0) return@hookAllBefore
            val down = event.action == KeyEvent.ACTION_DOWN
            if (!down && event.action != KeyEvent.ACTION_UP) return@hookAllBefore
            if (DoomView.dispatchVolumeKey(event.keyCode, down)) {
                param.result = true
            }
        }
    }

    private fun applyBannerBitmap(view: View, bitmap: Bitmap) {
        try {
            view.callOrNull("setImageBitmap", bitmap)
            val lp = view.callOrNull("getLayoutParams") ?: return
            lp.setField("width", ViewGroup.LayoutParams.MATCH_PARENT)
            val aspect = if (bitmap.width > 0) bitmap.height.toFloat() / bitmap.width else 1f
            val screenW = view.resources.displayMetrics.widthPixels
            lp.setField("height", (screenW * aspect).toInt().coerceAtLeast(1))
            view.callOrNull("setLayoutParams", lp)
            view.visibility = View.VISIBLE
        } catch (t: Throwable) {
            XLog.e("[$id] не удалось применить картинку баннера", t)
        }
    }
}
