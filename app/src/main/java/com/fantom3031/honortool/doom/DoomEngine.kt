package com.fantom3031.honortool.doom

import android.graphics.Bitmap
import android.os.SystemClock
import com.fantom3031.honortool.core.XLog
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipFile
import kotlin.concurrent.thread

/**
 * Мост к нативному PureDOOM (libdoomhook.so).
 *
 * Потоковая модель: движок целиком живёт на потоке [THREAD_NAME] —
 * `doom_init` выполняется в первом `nativeTick`, ввод из UI ставится
 * в очередь внутри C (нативный код не потокобезопасен). Кадры
 * складываются в [frame] под блокировкой; вьюшка тянет их по [frameSeq].
 */
object DoomEngine {

    const val WIDTH = 320
    const val HEIGHT = 200
    const val WAD_ASSET = "assets/doom1.wad"
    const val WAD_SIZE = 4196020L

    /** Клавиши PureDOOM (см. doom_key_t в PureDOOM.h). */
    const val KEY_ENTER = 13
    const val KEY_ESCAPE = 27
    const val KEY_SPACE = 32
    const val KEY_COMMA = ','.code
    const val KEY_PERIOD = '.'.code
    const val KEY_CTRL = (0x80 + 0x1d)
    const val KEY_SHIFT = (0x80 + 0x36)
    const val KEY_UP_ARROW = 0xad
    const val KEY_DOWN_ARROW = 0xaf
    const val KEY_LEFT_ARROW = 0xac
    const val KEY_RIGHT_ARROW = 0xae

    private const val THREAD_NAME = "honortool-doom"
    private const val TICK_MS = 14L

    /** Тактов простоя (по 200 мс) до самоостановки потока. */
    private const val IDLE_EXIT_TICKS = 10

    private val frameLock = Any()

    /** Инициализация прошла успешно — можно рисовать. */
    @Volatile
    var started = false
        private set

    /** Движок тикает только когда вьюшка на экране. */
    @Volatile
    var running = false

    /** Последняя ошибка движка (I_Error) или причина незапуска. */
    @Volatile
    var fatalMessage: String? = null
        private set

    val frame: Bitmap? by lazy {
        if (!started) null
        else Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
    }

    @Volatile
    var frameSeq = 0
        private set

    private val pixels = IntArray(WIDTH * HEIGHT)
    private val libLoaded = AtomicBoolean(false)
    private val startAttempted = AtomicBoolean(false)
    private val attachedViews = java.util.Collections.synchronizedSet(HashSet<Any>())
    private var engineThread: Thread? = null
    @Volatile
    private var stopRequested = false
    private var wadPath: String? = null
    private var saveDirPath: String? = null

    /** Загружает libdoomhook.so из APK модуля (в процессе целевого приложения). */
    fun ensureLibrary(nativeLibraryDir: String): Boolean {
        if (!libLoaded.compareAndSet(false, true)) return libLoaded.get()
        return try {
            System.load("$nativeLibraryDir/libdoomhook.so")
            log("libdoomhook.so загружен из $nativeLibraryDir")
            true
        } catch (t: Throwable) {
            libLoaded.set(false)
            fatalMessage = "libdoomhook.so: ${t.message}"
            XLog.e("[doom] не удалось загрузить libdoomhook.so из $nativeLibraryDir", t)
            false
        }
    }

    /** Распаковывает doom1.wad из APK модуля в cacheDir целевого процесса. */
    fun extractWad(moduleApkPath: String, cacheDir: File): File? {
        val out = File(cacheDir, "doom1.wad")
        if (out.isFile && out.length() == WAD_SIZE) return out
        return try {
            ZipFile(moduleApkPath).use { zip ->
                val entry = zip.getEntry(WAD_ASSET) ?: return null
                zip.getInputStream(entry).use { input ->
                    out.outputStream().use { output -> input.copyTo(output) }
                }
            }
            if (out.isFile) out else null
        } catch (t: Throwable) {
            XLog.e("[doom] не удалось распаковать WAD из $moduleApkPath", t)
            null
        }
    }

    /**
     * Запускает поток движка. Сама инициализация (doom_init) произойдёт
     * на потоке движка в первом nativeTick. Повторный запуск в рамках
     * одного процесса невозможен — doom_init одноразовый.
     */
    fun start(wad: File, saveDir: File): Boolean {
        val wadAbs = wad.absolutePath
        if (!startAttempted.compareAndSet(false, true)) return started
        wadPath = wadAbs
        saveDirPath = saveDir.apply { mkdirs() }.absolutePath
        log("start: wad=$wadAbs (${wad.length()} байт), saves=$saveDirPath")
        ensureThread()
        XLog.i("[doom] поток движка запущен: wad=$wadAbs")
        return true
    }

    /** Жёсткая остановка (движок умрёт до конца жизни процесса). */
    fun stop() {
        stopRequested = true
        running = false
        nativeStop()
    }

    /**
     * Жизненный цикл: движок живёт, пока карточка на экране.
     * Поток завершается сам после [IDLE_EXIT_TICKS] тактов простоя
     * (пережидаем ребинд карточки) и перезапускается при новом открытии.
     * Нативное состояние (doom_init) живёт до смерти процесса — per-process
     * ограничение PureDOOM; при повторном открытии игра продолжается.
     */
    fun attachView(view: Any) {
        attachedViews.add(view)
        running = true
        ensureThread()
    }

    fun detachView(view: Any) {
        attachedViews.remove(view)
        if (attachedViews.isEmpty()) {
            running = false // поток сам завершится после паузы
            XLog.i("[doom] карточка закрыта — движок останавливается")
        }
    }

    private fun ensureThread() {
        if (engineThread?.isAlive == true) return
        stopRequested = false
        engineThread = thread(name = THREAD_NAME, isDaemon = true) { loop() }
    }

    private fun loop() {
        val wad = wadPath ?: return
        val dir = saveDirPath ?: return
        if (!nativeStart(wad, dir)) {
            fatalMessage = nativeLastMessage() ?: "doom init failed"
            log("ошибка подготовки: $fatalMessage")
            XLog.e("[doom] не удалось подготовить движок: $fatalMessage")
            return
        }
        var idle = 0
        while (true) {
            if (running) {
                idle = 0
                nativeTick()
                if (!started) {
                    if (nativeIsRunning()) {
                        started = true
                        log("движок инициализирован")
                        XLog.i("[doom] движок инициализирован")
                    } else {
                        fatalMessage = nativeLastMessage() ?: "DOOM init failed"
                        log("инициализация не удалась: $fatalMessage")
                        XLog.e("[doom] инициализация не удалась: $fatalMessage")
                        return
                    }
                } else if (!nativeIsRunning()) {
                    fatalMessage = nativeLastMessage() ?: "DOOM crashed"
                    log("движок упал: $fatalMessage")
                    XLog.e("[doom] движок упал: $fatalMessage")
                    return
                }
                pullFrameLocked()
                SystemClock.sleep(TICK_MS)
            } else {
                // Карточки нет на экране: пережидаем ребинд и выходим,
                // чтобы движок не висел в фоне. Новый поток поднимет attachView.
                if (++idle > IDLE_EXIT_TICKS) {
                    XLog.i("[doom] поток движка остановлен (карточка закрыта)")
                    return
                }
                SystemClock.sleep(200)
            }
        }
    }

    /** Обновляет [frame] из нативного буфера. Только на потоке движка. */
    private fun pullFrameLocked() {
        val bmp = frame ?: return
        val seq = nativeFrameSeq()
        if (seq == frameSeq) return
        frameSeq = seq
        nativeGetFrame(pixels)
        synchronized(frameLock) {
            bmp.setPixels(pixels, 0, WIDTH, 0, 0, WIDTH, HEIGHT)
        }
    }

    /** Рисование кадра вьюшкой — под той же блокировкой. */
    fun withFrame(action: (Bitmap) -> Unit) {
        val bmp = frame ?: return
        synchronized(frameLock) { action(bmp) }
    }

    /** 0 = меню/титры, 1 = геймплей (кэш, обновляемый потоком движка). */
    fun inputMode(): Int = nativeInputMode()

    /** Жив ли движок (кэшированное значение из C). */
    val isAlive: Boolean
        get() = nativeIsRunning()

    fun key(code: Int, down: Boolean) = nativeKey(code, down)

    fun keyTap(code: Int) {
        nativeKey(code, true)
        nativeKey(code, false)
    }

    fun mouseMove(dx: Int, dy: Int) = nativeMouseMove(dx, dy)

    private external fun nativeStart(wadPath: String, saveDir: String): Boolean
    private external fun nativeStop()
    private external fun nativeTick(): Int
    private external fun nativeFrameSeq(): Int
    private external fun nativeGetFrame(out: IntArray)
    private external fun nativeKey(key: Int, down: Boolean)
    private external fun nativeMouseMove(dx: Int, dy: Int)
    private external fun nativeInputMode(): Int
    private external fun nativeIsRunning(): Boolean
    private external fun nativeLastMessage(): String?
    private external fun nativeLogPath(): String?
    private external fun nativeLogWrite(line: String?)

    /** Путь к файловому логу движка (если доступен). */
    val logPath: String?
        get() = if (libLoaded.get()) nativeLogPath()?.takeIf { it.isNotEmpty() } else null

    /** Пишет строку в файловый лог движка (если библиотека загружена). */
    fun log(line: String) {
        if (libLoaded.get()) runCatching { nativeLogWrite(line) }
    }
}
