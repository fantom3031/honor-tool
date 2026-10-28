package com.fantom3031.honortool.hooks.launcher

import android.content.Context
import android.graphics.Rect
import android.view.View
import com.fantom3031.honortool.R
import com.fantom3031.honortool.core.BaseHook
import com.fantom3031.honortool.core.XLog
import com.fantom3031.honortool.core.callMethod
import com.fantom3031.honortool.core.getField
import com.fantom3031.honortool.core.setField

/**
 * Многострочный (сеточный) режим экрана недавних.
 *
 * В прошивке режим встроен: HnRecentsView хранит калькулятор раскладки
 * (setRecentsViewCalInterface) и сверяется с RecentsLayoutManager.isDouble(),
 * но на телефоне getRecentsViewCal всегда отдаёт SingleRowRecentsLayoutCal,
 * а sIsSupportDouble берётся из msc.launcher.recents_show_lines_num (=1).
 *
 * Хук подменяет решения менеджера и результат getRecentsViewCal:
 * сетка поддерживается, режим активен, калькулятор — многострочный.
 */
object RecentsGridHook : BaseHook() {

    const val ID = "launcher_recents_grid"

    private const val MANAGER =
        "com.hihonor.android.launcher.quickstep.doublerecentsview.RecentsLayoutManager"
    private const val MULTI_CALC =
        "com.hihonor.android.launcher.quickstep.doublerecentsview.MultiRowRecentsLayoutCal"
    private const val HN_RECENTS_VIEW =
        "com.hihonor.android.launcher.extension.quickstep.views.HnRecentsView"
    private const val DEVICE_PROFILE =
        "com.hihonor.android.launcher.DeviceProfile"

    /** Классы, чей displayOverviewTasksAsGrid решает, показывать ли сетку. */
    private val GRID_STATE_CLASSES = listOf(
        "com.hihonor.android.launcher.quickstep.util.HnLauncherState",
        "com.android.launcher3.uioverrides.states.OverviewState",
        "com.android.launcher3.uioverrides.states.BackgroundAppState",
        "com.android.quickstep.fallback.RecentsState",
    )

    private const val GRID_MAX_COUNT = 8

    /** Ширина карточки сетки относительно ширины карусельной карточки (2 колонки). */
    private const val GRID_CARD_WIDTH_SCALE = 0.48f

    /** Зазор между рядами сетки относительно высоты экрана недавних. */
    private const val GRID_ROW_GAP = 0.04f

    override val id = ID
    override val titleRes = R.string.recents_grid_title
    override val descriptionRes = R.string.recents_grid_description
    override val targetPackages = setOf("com.hihonor.android.launcher")
    override val enabledByDefault = false

    override fun onHook() {
        val manager = findClass(MANAGER)

        manager.replace("isSupportDouble") { true }
            ?: XLog.w("[$id] isSupportDouble не найден")
        manager.replace("needChangeToDouble") { true }
            ?: XLog.w("[$id] needChangeToDouble не найден")
        manager.replace("isDouble") { true }
            ?: XLog.w("[$id] isDouble не найден")
        manager.replace("getMaxCount") { GRID_MAX_COUNT }
            ?: XLog.w("[$id] getMaxCount не найден")

        val multiCalc = findClass(MULTI_CALC)
        manager.hookAfter(
            "getRecentsViewCal",
            Context::class.java,
            HN_RECENTS_VIEW
        ) { p ->
            runCatching {
                val calc = multiCalc.newInstance()
                calc.callMethod("init", p.args[0], p.args[1])
                p.result = calc
                XLog.i("[$id] многострочный калькулятор раскладки установлен")
            }.onFailure { XLog.e("[$id] не удалось создать многострочный калькулятор", it) }
        } ?: XLog.w("[$id] getRecentsViewCal не найден")

        GRID_STATE_CLASSES.forEach { name ->
            findClassOrNull(name)?.replace(
                "displayOverviewTasksAsGrid",
                DEVICE_PROFILE
            ) { true }
                ?: XLog.w("[$id] displayOverviewTasksAsGrid не найден в $name")
        }

        // На телефоне вертикальные смещения рядов считаются планшетной геометрией
        // (calculateGridTaskSize) и прижимают ряды к краям экрана. Считаем сетку
        // от ширины (2 колонки) и центрируем по высоте экрана недавних.
        val recentsView = findClass("com.android.quickstep.views.RecentsView")
        recentsView.hookAfter("updateSizeAndPadding") { p ->
            val v = p.thisObject as? View ?: return@hookAfter
            runCatching {
                val task = v.getField("mLastComputedTaskSize") as? Rect
                    ?: return@runCatching
                val grid = v.getField("mLastComputedGridTaskSize") as? Rect
                    ?: return@runCatching
                val gridWidth = (task.width() * GRID_CARD_WIDTH_SCALE).toInt().coerceAtLeast(1)
                val gridHeight =
                    (gridWidth * task.height().toFloat() / task.width().coerceAtLeast(1))
                        .toInt().coerceAtLeast(1)
                val rowGap = (v.height * GRID_ROW_GAP).toInt()
                val total = gridHeight * 2 + rowGap
                val gridTop = ((v.height - total) / 2).coerceAtLeast(0)
                val centerX = v.width / 2
                grid.set(
                    centerX - gridWidth / 2, gridTop,
                    centerX + gridWidth / 2, gridTop + gridHeight
                )
                v.setField("mTaskGridVerticalDiff", (gridTop - task.top).toFloat())
                v.setField("mTopBottomRowHeightDiff", (gridHeight + rowGap).toFloat())
            }.onFailure {
                XLog.e("[$id] не удалось выровнять сетку по экрану", it)
            }
        } ?: XLog.w("[$id] updateSizeAndPadding не найден")

        // Компактный вид содержимого карточки (иконка + строка) штатно включается
        // только на планшетах; включаем его при полном входе в сетку.
        recentsView.hookAfter("setGridProgress", Float::class.javaPrimitiveType) { p ->
            val progress = p.args.getOrNull(0) as? Float ?: return@hookAfter
            if (progress < 0.99f) return@hookAfter
            val v = p.thisObject ?: return@hookAfter
            runCatching {
                val count = v.callMethod("getTaskViewCount") as? Int ?: return@runCatching
                for (i in 0 until count) {
                    v.callMethod("getTaskViewAt", i)?.callMethod(
                        "setIconsAndBannersTransitionProgress", 1f, false
                    )
                }
            }.onFailure {
                XLog.e("[$id] не удалось включить компактный вид карточек", it)
            }
        } ?: XLog.w("[$id] setGridProgress не найден")

        // На телефоне TaskView.updateTaskSize пропускает изменение размера карточки,
        // оставляя полноэкранную карусельную в геометрии сетки («забор»).
        // Повторяем планшетную ветку: карточка уменьшается до gridWidth x gridHeight,
        // а шапка (иконка/имя) и кнопки масштабируются вместе с карточкой.
        findClass("com.android.quickstep.views.TaskView").replace("updateTaskSize") { p ->
            val view = p.thisObject as? View ?: return@replace null
            runCatching {
                val deviceProfile = view.getField("mActivity")?.callMethod("getDeviceProfile") ?: return@runCatching
                val topMargin = (deviceProfile.getField("overviewTaskThumbnailTopMarginPx") as Number).toInt()
                val recents = view.callMethod("getRecentsView") ?: return@runCatching
                val taskRect = recents.callMethod("getLastComputedTaskSize") as? Rect ?: return@runCatching
                val gridRect = recents.callMethod("getLastComputedGridTaskSize") as? Rect ?: return@runCatching
                val height = gridRect.height() + topMargin
                val width = gridRect.width().coerceAtLeast(1)
                view.callMethod(
                    "setNonGridScale",
                    taskRect.width().toFloat() / width
                )
                view.callMethod(
                    "setBoxTranslationY",
                    (gridRect.height() - taskRect.height()) / 2f
                )
                val params = view.layoutParams
                if (params.width != width || params.height != height) {
                    params.width = width
                    params.height = height
                    view.layoutParams = params
                }
                val contentScale = width.toFloat() / taskRect.width().coerceAtLeast(1)
                scaleContent(view.getField("mTaskViewHeader") as? View, contentScale)
                scaleContent(view.getField("mActionButtonView") as? View, contentScale)
            }.onFailure { XLog.e("[$id] не удалось уменьшить карточку сетки", it) }
            null
        } ?: XLog.w("[$id] TaskView.updateTaskSize не найден")

        // Путь входа «из приложения» не пересчитывает размеры карточек — форсируем
        // после обновления свойств сетки на любом пути входа.
        recentsView.hookAfter(
            "updateGridProperties",
            Boolean::class.javaPrimitiveType,
            Int::class.javaPrimitiveType
        ) { p ->
            val v = p.thisObject ?: return@hookAfter
            runCatching {
                val count = v.callMethod("getTaskViewCount") as? Int ?: return@runCatching
                for (i in 0 until count) {
                    v.callMethod("getTaskViewAt", i)?.callMethod("updateTaskSize")
                }
            }.onFailure { XLog.e("[$id] не удалось пересчитать карточки", it) }
        } ?: XLog.w("[$id] updateGridProperties(ZI) не найден")

        XLog.i("[$id] сеточный режим недавних включён")
    }

    /** Уменьшает шапку/кнопки карточки, сохраняя привязку к низу. */
    private fun scaleContent(content: View?, scale: Float) {
        if (content == null || scale <= 0f || scale >= 1f) return
        content.pivotX = content.width / 2f
        content.pivotY = content.height.toFloat()
        content.scaleX = scale
        content.scaleY = scale
    }
}
