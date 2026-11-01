# HonorTool — Памятка для переноса в другой чат

## Проект

**Название:** Honor Tool
**Автор модуля:** `fantom3031`
**Пакет:** `com.fantom3031.honortool` (debug: `com.fantom3031.honortool.debug`)
**Путь:** `C:\Games\realme\bananas\honor-tool`
**APK:** `app/build/outputs/apk/debug/honor-tool-1-debug.apk`
**Git:** инициализирован, коммит `5a82cf5`, автор fantom3031 + noreply-почта GitHub; remote не добавлен, пуш не выполнен.
**Сборка:** WSL Debian 13, `./gradlew --no-daemon assembleDebug`
**Устройство:** Honor X7C (ALT-LX1), MagicOS 8, Android 14, временный root + LSPosed/Vector, LibXposed API 102
**SDK:** `/home/banana/Android/Sdk` (симлинк), NDK `27.3.13750724` = `/home/banana/android-ndk-r27d` (симлинк `ndk/27.3.13750724` в fh2/android-sdk)
**Место в WSL:** следить за `/` — 24 ГБ, было 100%; чистка `~/.gradle/caches/8.9` и `wrapper/dists/gradle-8.9-bin` освобождает ~1.2 ГБ.

## Архитектура

```
honor-tool/
├── app/src/main/jni/          # PureDOOM (doomhook.c, arm64-v8a, ndkBuild)
├── app/src/main/assets/doom1.wad
├── app/src/main/java/com/fantom3031/honortool/
│   ├── core/                  # BaseHook (LibXposed API 102), HookEntry (onSystemServerStarting → system_server), HookRegistry, HookPrefs (RemotePreferences), XLog, XposedExt (reflection), ServiceBridge, XposedScopeManager
│   ├── hooks/
│   │   ├── HookModules.kt     # ЕДИНСТВЕННОЕ место регистрации хуков
│   │   ├── settings/          # AboutPhoneVideoHook (видео/фото/DOOM), AboutDeviceCustomHook, ImeiPrivacyHook
│   │   ├── systemui/          # HonorShadeTransparentHook, LockScreenChargingHook, StatusBarSecondsHook, ScreenshotWatermarkHook
│   │   ├── launcher/          # SelectedFolderSlotsHook (ID-выбор папок), RecentsGridHook (помечен нерекомендуемым)
│   │   └── aod/               # AodDisplayTimeHook, AodMediaCardHook, AodBrightnessHook
│   ├── doom/                  # DoomEngine (JNI), DoomView
│   ├── settings/              # AppSettings, ImageStore (base64 в RemotePreferences), VideoStore, WorldReadable
│   └── ui/                    # Compose экраны; MemoryCleanupCard (root очистка ОЗУ), AppControl (force-stop)
├── app/module-scope.txt       # scope: settings, systemui, launcher, aod, self
└── .gitignore                 # analysis/ (дампы) исключён
```

## Текущие хуки (HookModules.kt)

1. **AboutPhoneVideoHook** (`settings_about_phone_video`) — видео/фото/DOOM в карточке «О телефоне».
2. **AboutDeviceCustomHook** (`settings_about_device_custom`) — подмена значений; поля многострочные (singleLine=false); ПЗУ (InternalStorageController.getSummary) подменяется только при вызовах со стеком deviceinfo (не ломает подписи разделов настроек).
3. **ImeiPrivacyHook** (`settings_imei_privacy`) — IMEI → нули в Настройках.
4. **HonorShadeTransparentHook** (`systemui_honor_transparent_shade`) — прозрачная шторка.
5. **LockScreenChargingHook** (`systemui_charging_info`) — двухстрочный блок зарядки внизу локскрина; скрыт вне локскрина/при уведомлениях/шторке.
6. **StatusBarSecondsHook** (`systemui_status_bar_seconds`) — секунды в часах статус-бара.
7. **ScreenshotWatermarkHook** (`systemui_screenshot_watermark`) — текст (hex-цвет строк) или PNG-обложка; 3 точки перехвата (captureDisplay/ScreenshotData.setBitmap/ImageExporter$Task); динамическая синхронизация broadcast WATERMARK_SYNC; обложка ≤1080px/700KB (PNG→WEBP fallback).
8. **SelectedFolderSlotsHook** (`launcher_selected_folder_slots`) — выбор папок по ID; клик по слоту решает FolderIcon.toAddFullFolder(item) — хук подменяет result=true для выбранных папок (работает после перезагрузки systemui). Свежий фикс!
9. **RecentsGridHook** (`launcher_recents_grid`) — помечен «не рекомендуется» в UI; многоитерационный порт сетки недавних.
10. **AodDisplayTimeHook** (`aod_display_time`) — длительность AOD 5–600 с (слайдер + ручной ввод через клик по значению).
11. **AodMediaCardHook** (`aod_media_card`) — карточка «что играет»: обложка (METADATA_KEY_ART/ALBUM_ART, ≤120px), прогресс-бар + время (1:24/3:50, обновление раз в секунду), 15 с полупрозрачного удержания на паузе. Сессии опрашиваются MediaSessionManagerEx.getActiveSessionsForUser(mms, enabledNotificationListener, -1) каждые 3 с (видит все приложения).
12. **AodBrightnessHook** (`aod_brightness`) — дискретные режимы: Авто/Максимум(1)/Штатная(3)/Тусклая(7); динамическое применение broadcast AOD_BRIGHTNESS_SYNC.

Плюс **MemoryCleanupCard** (не хук, на главном экране): «Очистка ОЗУ» — force-stop пакетов с appId 10000–19999 (сторонние + предустановленные, включая GMS/VPN/торренты), затем am kill-all; защищены systemui/launcher/клавиатура/телефония/разрешения/хранилище/root-менеджеры.

## Ключевые находки реверса (для повторного использования)

- **Вол + при гашеном экране** (удалён): HwPhoneWindowManager.interceptKeyBeforeQueueing; double Vol− 400 мс в HwPhoneWindowManager; switchFlashLight / notifyRapidCaptureService("start") в services-magic.jar.
- **Сетка недавних**: RecentsLayoutManager (sIsSupportDouble ← `msc.launcher.recents_show_lines_num`), getRecentsViewCal → MultiRow/SingleRow, displayOverviewTasksAsGrid (4 класса), TaskView.updateTaskSize (планшетная ветка), updateGridProperties(ZI) mTaskGridVerticalDiff/mTopBottomRowHeightDiff, getCurrentBrightLevel.
- **AOD** (com.hihonor.aod, дампы в analysis/aod/): DozeService (SwingConfig.getNudgeDisplayTime), AodApHost.initAodView (mTargetViews[0] = LinearLayout контент AOD), ApSolutionUtil.setDozeScreenBrightness(I)I → PowerManagerEx.setAodBrightness, BaseViewController.onTimeTick (минутный тик перерисовки), MediaPlayerMonitor/MediaPlayer (прошивочный монитор сессий).
- **AOD гестуры невозможны**: касания обрабатываются сенсорным хабом прошивки; MotionEvent с координатами в AOD-процесс не доставляются (подтверждено экспериментом снятия NOT_TOUCHABLE).
- **Папки лаунчера**: FolderIcon.toAddFullFolder(item) — гейт «все слоты — приложения» (Folder.isFull + itemType); CardFolderCalculator.getCardFolderMaxNormalIconCount.
- **RemotePreferences лимит**: файл `honor_tool_images.xml` > ~1 МБ не доставляется процессам; картинки хранить ≤700 КБ (WEBP fallback).

## Свежие правки (последняя сборка)

- Яркость AOD: «Тускл.» — одна строка.
- Очистка ОЗУ: без «максимальной», описание сокращено.
- Сетка недавних: помечена «не рекомендуется».
- «О телефоне»: поля многострочные.
- ПЗУ: подмена только в deviceinfo-стеках.
- Логи: шумные XLog.i → XLog.d (кандидат/трек/карточка/discovered/яркость).

## Что не проверено на устройстве

-_folder toAddFullFolder фикс после перезагрузки systemui (логика верна по дампам).
- Многострочные значения «О телефоне» (отображение \n).
- ПЗУ-фикс (мини-описания разделов).
- Прогресс-бар и 15 с удержание карточки AOD.

## Файлы анализа (в C:\Games\realme\bananas\)

- `services-magic.jar`, `framework-magic.jar`, `hncustservices.jar`, `services.jar` — system_server дампы
- `Постоянное_отображение_(14.0.0.036).apk` → analysis/aod/ (дампнуты classes.dex, 2, 3)
- `Рабочий_экран_HONOR_(14.0.0.245).apks` → analysis/launcher_d/ (HnLauncher6.apk, дампнуты classes*.dex)
- `Интерфейс_системы_(14).apk` → analysis/systemui/ (дампнуты classes*.dex)
- Дампы лаунчера: RecentsGridHook детали в classes2.dex.txt ~854k; toAddFullFolder в classes2.dex.txt ~1507153
- AOD дампы: ApAodSensorMonitor в classes.dex.txt ~1201319 (onAlpmLightChange/onTriggerMotion)

## Dexdump команда

```bash
unzip -qo <apk> 'classes*.dex'
/home/banana/Android/Sdk/build-tools/34.0.0/dexdump -d classes2.dex > classes2.dex.txt
```

## Сборка

```bash
# Из Windows:
wsl -e bash -c 'cd /mnt/c/Games/realme/bananas/honor-tool && ./gradlew --no-daemon assembleDebug'
# APK: app/build/outputs/apk/debug/honor-tool-1-debug.apk
```

## Отладка

```bash
# Логи модуля
adb logcat -s HonorTool

# Перезапуск AOD (нужен после обновления APK)
adb shell su -c "am force-stop com.hihonor.aod"

# Перезапуск лончера
adb shell am force-stop com.hihonor.android.launcher

# Диагностика system_server (файл создаётся только при внедрении)
adb shell su -c 'cat /data/system/honor-tool-volume.log'
```

## Источник вдохновения

- NothingTweaks: https://github.com/RevealedSoulEven/NothingTweaks
- PureDOOM: https://github.com/Daivuk/PureDOOM
