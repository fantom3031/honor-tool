package com.fantom3031.honortool.hooks.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.view.View
import com.fantom3031.honortool.R
import com.fantom3031.honortool.core.*
import java.util.WeakHashMap

/** Independent of the title-based hook: either hook may enable a folder. */
object SelectedFolderSlotsHook : BaseHook() {
    const val ID = "launcher_selected_folder_slots"
    const val IDS_KEY = "selected_folder_ids"
    const val ALL_KEY = "selected_folder_all"
    const val QUERY = "com.fantom3031.honortool.QUERY_FOLDERS"
    const val RESPONSE = "com.fantom3031.honortool.FOLDER_LIST"
    const val LAUNCHER = "com.hihonor.android.launcher"
    override val id = ID
    override val titleRes = R.string.selected_folders_title
    override val descriptionRes = R.string.selected_folders_description
    override val targetPackages = setOf(LAUNCHER)
    override val enabledByDefault = false

    private val icons = WeakHashMap<Any, Any>()
    private val calculators = WeakHashMap<Any, Any>()
    private var receiver: BroadcastReceiver? = null
    private var lastSelection: Pair<Boolean, Set<String>>? = null

    private fun folderId(info: Any): String? =
        (info.fieldOrNull("id") as? Number)?.toLong()?.takeIf { it >= 0 }?.toString()

    private fun selected(info: Any): Boolean = HookPrefs.getBoolean(ALL_KEY, false) ||
        folderId(info)?.let { it in HookPrefs.getStringSet(IDS_KEY) } == true

    private fun capacity(calc: Any): Int =
        ((calc.fieldOrNull("mMaxColumns") as? Int) ?: 0) *
            ((calc.fieldOrNull("mMaxRows") as? Int) ?: 0)

    override fun onHook() {
        val prefix = "com.hihonor.android.launcher."
        findClass(prefix + "FolderIcon").hookAllAfter("initFolder") { p ->
            val icon = p.thisObject ?: return@hookAllAfter
            val info = icon.fieldOrNull("mInfo") ?: return@hookAllAfter
            val calc = icon.fieldOrNull("mCalculator") ?: return@hookAllAfter
            icons[icon] = info
            calculators[calc] = info
            XLog.d("[$id] discovered id=${folderId(info)}, name=${info.fieldOrNull("mTitle")}")
        }
        findClass(prefix + "cardstyle.cardfolder.CardFolderCalculator")
            .hookAllBefore("getCardFolderMaxNormalIconCount") { p ->
                val calc = p.thisObject ?: return@hookAllBefore
                val info = calculators[calc] ?: return@hookAllBefore
                if (selected(info)) capacity(calc).takeIf { it > 0 }?.let { p.result = it }
            }
        val card = findClass(prefix + "cardstyle.cardfolder.CardFolder")
        card.hookAllBefore("getPreviewMaxNormalIconCount") { p ->
            val calc = p.thisObject?.fieldOrNull("mCalculator") ?: return@hookAllBefore
            val info = calculators[calc] ?: return@hookAllBefore
            if (selected(info)) capacity(calc).takeIf { it > 0 }?.let { p.result = it }
        }
        listOf("reCalculate", "relayout").forEach { method ->
            card.hookAllAfter(method) { p ->
                val folder = p.thisObject ?: return@hookAllAfter
                val calc = folder.fieldOrNull("mCalculator") ?: return@hookAllAfter
                val info = calculators[calc] ?: return@hookAllAfter
                if (selected(info)) capacity(calc).takeIf { it > 0 }?.let {
                    folder.setField("mMaxNormalItemCount", it)
                }
            }
        }
        // Клик по слоту превью решает FolderIcon.toAddFullFolder(item): без «полной»
        // папки он false и слот открывает папку. Для выбранных папок слоты-приложения
        // делаем полноценными (кроме самой папки — её открывает штатная логика).
        findClass(prefix + "FolderIcon").hookAllAfter("toAddFullFolder") { p ->
            if (p.result == true) return@hookAllAfter
            val icon = p.thisObject ?: return@hookAllAfter
            val info = icon.fieldOrNull("mInfo") ?: return@hookAllAfter
            val item = p.args.firstOrNull() ?: return@hookAllAfter
            if (item === info) return@hookAllAfter
            if (selected(info)) p.result = true
        }
        findClass(prefix + "Launcher").hookAllAfter("onCreate") { p ->
            val context = (p.thisObject as? Context)?.applicationContext ?: return@hookAllAfter
            if (receiver != null) return@hookAllAfter
            val r = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    if (intent.action != QUERY) return
                    // Reply only to this APK, and accept no settings from external broadcasts.
                    val target = intent.getStringExtra("replyPackage")
                    if (target != "com.fantom3031.honortool" && target != "com.fantom3031.honortool.debug") return
                    val entries = icons.values.toList().mapNotNull { info ->
                        folderId(info)?.let { it to (info.fieldOrNull("mTitle")?.toString().orEmpty()) }
                    }.distinctBy { it.first }.sortedBy { it.second }
                    context.sendBroadcast(Intent(RESPONSE).setPackage(target)
                        .putExtra("ids", entries.map { it.first }.toTypedArray())
                        .putExtra("names", entries.map { it.second }.toTypedArray()))
                    // Use the same resize path as the existing full-title hook.
                    val selection = HookPrefs.getBoolean(ALL_KEY, false) to
                        HookPrefs.getStringSet(IDS_KEY).toSet()
                    if (selection != lastSelection) {
                        lastSelection = selection
                        icons.keys.toList().forEach { icon ->
                            runCatching { icon.callMethod("refreshWhenSpansChangeButTypeSame") }
                                .onFailure { XLog.e("[$id] folder refresh failed", it) }
                            (icon as? View)?.invalidate()
                        }
                    }
                }
            }
            if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(r, IntentFilter(QUERY), Context.RECEIVER_EXPORTED)
            else context.registerReceiver(r, IntentFilter(QUERY))
            receiver = r
            XLog.i("[$id] folder list receiver ready")
        }
    }
}
