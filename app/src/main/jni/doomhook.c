/*
 * HonorTool DOOM bridge: PureDOOM (single-header DOOM) для карточки «О телефоне».
 *
 * Потоковая модель (важно: PureDOOM не потокобезопасен):
 *  - ВСЕ вызовы движка (doom_init, doom_update, doom_key_*, doom_mouse_move)
 *    выполняются ТОЛЬКО на потоке движка — nativeTick;
 *  - ввод из UI-потока (кнопки громкости, свайпы) попадает в очередь
 *    (g_key_queue / g_mouse_*), которую nativeTick вычитает перед doom_update;
 *  - кадры конвертируются из RGBA (doom_get_framebuffer) в ARGB под мьютексом,
 *    вьюшка забирает их через nativeGetFrame;
 *  - звук не используется.
 *
 * Схема интеграции повторяет проверенный порт для LK (fastboot).
 */

#include <jni.h>
#include <android/log.h>
#include <pthread.h>
#include <string.h>
#include <stdio.h>
#include <stdlib.h>
#include <time.h>
#include <stdarg.h>
#include <sys/stat.h>

/* КРИТИЧНО: без этого дефолтный doom_malloc_impl возвращает NULL
 * (первый же malloc в IdentifyVersion даёт SIGSEGV). */
#define DOOM_IMPLEMENT_MALLOC

#define DOOM_IMPLEMENTATION
#include "PureDOOM.h"

#define LOG_TAG "HonorToolDoom"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

/* Файловый лог, который можно забрать с устройства:
 *  1) /sdcard/Download/honortool/doom.log
 *  2) fallback: <save_dir>/doom.log (кэш настроек, доступно через root) */
static char g_log_path[1024];
static FILE* g_log = 0;
static pthread_mutex_t g_log_lock = PTHREAD_MUTEX_INITIALIZER;

static void doom_log_write(const char* s) {
    pthread_mutex_lock(&g_log_lock);
    if (g_log) {
        fputs(s, g_log);
        fputc('\n', g_log);
        fflush(g_log);
    }
    pthread_mutex_unlock(&g_log_lock);
}

/** Лог с меткой времени: и в logcat, и в файл. */
static void doom_logf(const char* fmt, ...) {
    char buf[1024];
    va_list ap;
    va_start(ap, fmt);
    vsnprintf(buf, sizeof(buf), fmt, ap);
    va_end(ap);

    struct timespec ts;
    clock_gettime(CLOCK_REALTIME, &ts);
    struct tm tmv;
    localtime_r(&ts.tv_sec, &tmv);
    char line[1200];
    snprintf(line, sizeof(line), "[%02d:%02d:%02d] %s",
             tmv.tm_hour, tmv.tm_min, tmv.tm_sec, buf);

    LOGI("%s", buf);
    doom_log_write(line);
}

static void log_init(const char* save_dir) {
    if (g_log) return;
    static const char* dirs[] = {
        "/sdcard/Download/honortool",
        "/storage/emulated/0/Download/honortool",
    };
    for (size_t i = 0; i < sizeof(dirs) / sizeof(dirs[0]) && !g_log; i++) {
        mkdir(dirs[i], 0775);
        char path[1024];
        snprintf(path, sizeof(path), "%s/doom.log", dirs[i]);
        g_log = fopen(path, "a");
        if (g_log) snprintf(g_log_path, sizeof(g_log_path), "%s", path);
    }
    if (!g_log) {
        char path[1024];
        snprintf(path, sizeof(path), "%s/doom.log", save_dir);
        g_log = fopen(path, "a");
        if (g_log) snprintf(g_log_path, sizeof(g_log_path), "%s", path);
    }
    doom_logf("=== log file: %s ===", g_log ? g_log_path : "unavailable");
}

#define FRAME_W 320
#define FRAME_H 200
#define FRAME_PIXELS (FRAME_W * FRAME_H)

/* Очередь ввода: пишёт любой поток, читает только поток движка. */
#define INPUT_QUEUE_SIZE 512

typedef struct {
    int key;
    int down;
} key_event_t;

static key_event_t g_key_queue[INPUT_QUEUE_SIZE];
static int g_key_head = 0; /* пишем */
static int g_key_tail = 0; /* читаем */
static pthread_mutex_t g_input_lock = PTHREAD_MUTEX_INITIALIZER;

static int g_mouse_dx = 0;
static int g_mouse_dy = 0;

static pthread_mutex_t g_lock = PTHREAD_MUTEX_INITIALIZER;
static jint g_frame[FRAME_PIXELS];
static volatile int g_frame_seq = 0;

static volatile int g_exit = 0;
static volatile int g_inited = 0; /* 0 — не инициализирован, 1 — ok, -1 — ошибка */

/* Кэш режима ввода: обновляется в nativeTick, читается из UI. */
static volatile int g_mode_cache = 0;

static char g_wad_dir[512];
static char g_wad_path[512];
static char g_save_dir[512];
static char g_last_msg[512];

static char* g_argv[1];

/* ------------------------------------------------------------------ */
/* Callbacks движка (вызываются на потоке движка)                      */
/* ------------------------------------------------------------------ */

static void print_cb(const char* str) {
    if (!str) return;
    LOGI("%s", str);
    doom_log_write(str);
    size_t n = strlen(str);
    if (n >= sizeof(g_last_msg)) n = sizeof(g_last_msg) - 1;
    memcpy(g_last_msg, str, n);
    g_last_msg[n] = 0;
}

static const char* base_name(const char* filename) {
    const char* base = filename;
    for (const char* p = filename; *p; p++) {
        if (*p == '/' || *p == '\\') base = p + 1;
    }
    return base;
}

/* Чтения: сначала как есть (пути из DOOMWADDIR), затем save-каталог,
 * затем маппинг doom1.wad на наш WAD. Записи (default.cfg, сейвы) —
 * всегда в save-каталог. */
static void* open_cb(const char* filename, const char* mode) {
    if (!filename) return NULL;

    if (mode && (*mode == 'w' || *mode == 'a')) {
        char path[1024];
        snprintf(path, sizeof(path), "%s/%s", g_save_dir, base_name(filename));
        return fopen(path, mode);
    }

    FILE* f = fopen(filename, "rb");
    if (f) return f;

    char path[1024];
    snprintf(path, sizeof(path), "%s/%s", g_save_dir, base_name(filename));
    f = fopen(path, "rb");
    if (f) return f;

    if (strcmp(base_name(filename), "doom1.wad") == 0)
        return fopen(g_wad_path, "rb");

    return NULL;
}

static void close_cb(void* handle) { if (handle) fclose((FILE*)handle); }

static int read_cb(void* handle, void* buf, int count) {
    return handle ? (int)fread(buf, 1, (size_t)count, (FILE*)handle) : 0;
}

static int write_cb(void* handle, const void* buf, int count) {
    return handle ? (int)fwrite(buf, 1, (size_t)count, (FILE*)handle) : 0;
}

static int seek_cb(void* handle, int offset, doom_seek_t origin) {
    if (!handle) return -1;
    int whence = SEEK_CUR;
    if (origin == DOOM_SEEK_SET) whence = SEEK_SET;
    else if (origin == DOOM_SEEK_END) whence = SEEK_END;
    return fseek((FILE*)handle, (long)offset, whence);
}

static int tell_cb(void* handle) {
    return handle ? (int)ftell((FILE*)handle) : -1;
}

static int eof_cb(void* handle) {
    return handle ? feof((FILE*)handle) : 1;
}

static void gettime_cb(int* sec, int* usec) {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    *sec = (int)ts.tv_sec;
    *usec = (int)(ts.tv_nsec / 1000);
}

static void exit_cb(int code) {
    doom_logf("DOOM exit(%d): %s", code, g_last_msg);
    LOGE("DOOM exit(%d): %s", code, g_last_msg);
    g_exit = 1;
}

static char* getenv_cb(const char* var) {
    if (!var) return NULL;
    if (strcmp(var, "DOOMWADDIR") == 0) return g_wad_dir;
    if (strcmp(var, "HOME") == 0) return g_save_dir;
    return NULL;
}

/* ------------------------------------------------------------------ */
/* Ввод: очередь                                                       */
/* ------------------------------------------------------------------ */

static void queue_push(int key, int down) {
    pthread_mutex_lock(&g_input_lock);
    int next = (g_key_head + 1) % INPUT_QUEUE_SIZE;
    if (next != g_key_tail) { /* не переполняем */
        g_key_queue[g_key_head].key = key;
        g_key_queue[g_key_head].down = down;
        g_key_head = next;
    }
    pthread_mutex_unlock(&g_input_lock);
}

static void drain_input(void) {
    /* Клавиши. */
    pthread_mutex_lock(&g_input_lock);
    while (g_key_tail != g_key_head) {
        key_event_t ev = g_key_queue[g_key_tail];
        g_key_tail = (g_key_tail + 1) % INPUT_QUEUE_SIZE;
        if (ev.down) doom_key_down((doom_key_t)ev.key);
        else doom_key_up((doom_key_t)ev.key);
    }
    int dx = g_mouse_dx;
    int dy = g_mouse_dy;
    g_mouse_dx = 0;
    g_mouse_dy = 0;
    pthread_mutex_unlock(&g_input_lock);

    if (dx || dy) doom_mouse_move(dx, dy);
}

/* ------------------------------------------------------------------ */
/* Инициализация: ТОЛЬКО на потоке движка (первый nativeTick)          */
/* ------------------------------------------------------------------ */

static void doom_thread_init(void) {
    log_init(g_save_dir);

    /* WAD доступен? */
    FILE* probe = fopen(g_wad_path, "rb");
    if (!probe) {
        snprintf(g_last_msg, sizeof(g_last_msg), "WAD not found: %s", g_wad_path);
        doom_logf("WAD not found: %s", g_wad_path);
        g_inited = -1;
        return;
    }
    /* Размер заодно. */
    fseek(probe, 0, SEEK_END);
    long wad_size = ftell(probe);
    fclose(probe);
    doom_logf("WAD ok: %s (%ld bytes), saves: %s", g_wad_path, wad_size, g_save_dir);

    doom_set_print(print_cb);
    doom_set_file_io(open_cb, close_cb, read_cb, write_cb,
                     seek_cb, tell_cb, eof_cb);
    doom_set_gettime(gettime_cb);
    doom_set_exit(exit_cb);
    doom_set_getenv(getenv_cb);

    g_exit = 0;
    g_argv[0] = (char*)"doom";
    doom_logf("doom_init: запуск…");
    doom_init(1, g_argv,
              DOOM_FLAG_HIDE_SOUND_OPTIONS |
              DOOM_FLAG_HIDE_MUSIC_OPTIONS |
              DOOM_FLAG_HIDE_MOUSE_OPTIONS);
    g_inited = 1;
    doom_logf("doom_init: успех (gamemode определён)");
}

/* ------------------------------------------------------------------ */
/* JNI                                                                 */
/* ------------------------------------------------------------------ */

static void path_store(JNIEnv* env, jstring src, char* dst, size_t cap) {
    const char* s = (*env)->GetStringUTFChars(env, src, NULL);
    if (!s) { dst[0] = 0; return; }
    snprintf(dst, cap, "%s", s);
    (*env)->ReleaseStringUTFChars(env, src, s);
}

static void split_wad_path(void) {
    snprintf(g_wad_dir, sizeof(g_wad_dir), "%s", g_wad_path);
    char* slash = strrchr(g_wad_dir, '/');
    if (slash) *slash = 0;
    else g_wad_dir[0] = 0;
}

/* Сохраняет пути; сама инициализация — в nativeTick на потоке движка. */
JNIEXPORT jboolean JNICALL
Java_com_fantom3031_honortool_doom_DoomEngine_nativeStart(JNIEnv* env, jobject thiz,
                                                    jstring wadPath,
                                                    jstring saveDir) {
    (void)thiz;
    if (g_inited) return JNI_TRUE;
    if (!wadPath || !saveDir) return JNI_FALSE;

    path_store(env, wadPath, g_wad_path, sizeof(g_wad_path));
    path_store(env, saveDir, g_save_dir, sizeof(g_save_dir));
    split_wad_path();
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_fantom3031_honortool_doom_DoomEngine_nativeStop(JNIEnv* env, jobject thiz) {
    (void)env; (void)thiz;
    g_exit = 1;
}

/* Один шаг движка + снятие кадра. Вызывается ТОЛЬКО с потока движка. */
JNIEXPORT jint JNICALL
Java_com_fantom3031_honortool_doom_DoomEngine_nativeTick(JNIEnv* env, jobject thiz) {
    (void)env; (void)thiz;

    if (g_inited == 0) doom_thread_init();
    if (g_inited <= 0 || g_exit) return g_frame_seq;

    drain_input();

    doom_update();
    if (g_exit) return g_frame_seq;

    g_mode_cache = (menuactive || demoplayback || gamestate != GS_LEVEL) ? 0 : 1;

    const unsigned char* fb = doom_get_framebuffer(4);
    if (fb) {
        pthread_mutex_lock(&g_lock);
        const unsigned char* p = fb;
        jint* dst = g_frame;
        for (int i = 0; i < FRAME_PIXELS; i++) {
            unsigned r = p[0], g = p[1], b = p[2], a = p[3];
            dst[i] = (jint)((a << 24) | (r << 16) | (g << 8) | b);
            p += 4;
        }
        g_frame_seq++;
        pthread_mutex_unlock(&g_lock);
    }
    return g_frame_seq;
}

JNIEXPORT jint JNICALL
Java_com_fantom3031_honortool_doom_DoomEngine_nativeFrameSeq(JNIEnv* env, jobject thiz) {
    (void)env; (void)thiz;
    return g_frame_seq;
}

/* Копирует текущий кадр в out (массив 320*200, ARGB_8888). */
JNIEXPORT void JNICALL
Java_com_fantom3031_honortool_doom_DoomEngine_nativeGetFrame(JNIEnv* env, jobject thiz,
                                                      jintArray out) {
    (void)thiz;
    if (!out) return;
    jint* elems = (*env)->GetPrimitiveArrayCritical(env, out, NULL);
    if (!elems) return;
    pthread_mutex_lock(&g_lock);
    memcpy(elems, g_frame, sizeof(jint) * FRAME_PIXELS);
    pthread_mutex_unlock(&g_lock);
    (*env)->ReleasePrimitiveArrayCritical(env, out, elems, 0);
}

/* Ввод из UI-потока: только постановка в очередь. */
JNIEXPORT void JNICALL
Java_com_fantom3031_honortool_doom_DoomEngine_nativeKey(JNIEnv* env, jobject thiz,
                                                 jint key, jboolean down) {
    (void)env; (void)thiz;
    queue_push((int)key, down ? 1 : 0);
}

JNIEXPORT void JNICALL
Java_com_fantom3031_honortool_doom_DoomEngine_nativeMouseMove(JNIEnv* env, jobject thiz,
                                                       jint dx, jint dy) {
    (void)env; (void)thiz;
    pthread_mutex_lock(&g_input_lock);
    g_mouse_dx += (int)dx;
    g_mouse_dy += (int)dy;
    pthread_mutex_unlock(&g_input_lock);
}

/* 0 = меню/демо/титры, 1 = геймплей. Читает кэш, обновляемый потоком движка. */
JNIEXPORT jint JNICALL
Java_com_fantom3031_honortool_doom_DoomEngine_nativeInputMode(JNIEnv* env, jobject thiz) {
    (void)env; (void)thiz;
    return g_mode_cache;
}

JNIEXPORT jboolean JNICALL
Java_com_fantom3031_honortool_doom_DoomEngine_nativeIsRunning(JNIEnv* env, jobject thiz) {
    (void)env; (void)thiz;
    return (g_inited > 0 && !g_exit) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_com_fantom3031_honortool_doom_DoomEngine_nativeLastMessage(JNIEnv* env, jobject thiz) {
    (void)thiz;
    return (*env)->NewStringUTF(env, g_last_msg);
}

/* Путь к файловому логу (пустой, если недоступен). */
JNIEXPORT jstring JNICALL
Java_com_fantom3031_honortool_doom_DoomEngine_nativeLogPath(JNIEnv* env, jobject thiz) {
    (void)thiz;
    return (*env)->NewStringUTF(env, g_log_path[0] ? g_log_path : "");
}

/* Записать строку в файловый лог из Kotlin. */
JNIEXPORT void JNICALL
Java_com_fantom3031_honortool_doom_DoomEngine_nativeLogWrite(JNIEnv* env, jobject thiz,
                                                      jstring line) {
    (void)thiz;
    if (!line) return;
    const char* s = (*env)->GetStringUTFChars(env, line, NULL);
    if (!s) return;
    doom_log_write(s);
    LOGI("%s", s);
    (*env)->ReleaseStringUTFChars(env, line, s);
}
