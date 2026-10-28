# HonorTool — Памятка для переноса в другой чат

## Проект

**Название:** Honor Tool

**Автор модуля:** `fantom3031`
**Пакет:** `com.skb8.honortool` (переименован с `com.skb8.vivotool`)
**Путь:** `C:\Games\realme\bananas\honor-tool`
**APK:** `app/build/outputs/apk/debug/honor-tool-1-debug.apk`
**Сборка:** WSL Debian 13, `./gradlew --no-daemon assembleDebug`
**Устройство:** Honor X7C (ALT-LX1), MagicOS 8, Android 14, root + LSPosed

## Архитектура

```
honor-tool/
├── app/src/main/jni/
│   ├── PureDOOM.h          # single-header DOOM (48604 строк, 1.2MB)
│   ├── doom1.wad           # shareware WAD (4.1MB)
│   ├── doomhook.c          # JNI bridge: весь DOOM на одном потоке
│   ├── Android.mk          # ndkBuild, arm64-v8a
│   └── Application.mk
├── app/src/main/assets/
│   └── doom1.wad           # копия для извлечения в cache целевого процесса
├── app/src/main/java/com/skb8/honortool/
│   ├── core/
│   │   ├── BaseHook.kt     # базовый класс хуков (LibXposed API 102)
│   │   ├── HookEntry.kt    # точка входа, onSystemServerStarting → "system_server"
│   │   ├── HookRegistry.kt # список хуков из HookModules
│   │   ├── HookPrefs.kt    # RemotePreferences для чтения настроек хуками
│   │   ├── Constants.kt    # TAG="HonorTool", ключи настроек
│   │   └── XLog.kt         # лог в logcat + LSPosed журнал
│   ├── hooks/
│   │   ├── HookModules.kt  # ЕДИНСТВЕННОЕ место регистрации хуков
│   │   ├── settings/
│   │   │   ├── AboutPhoneVideoHook.kt   # видео/фото/DOOM в «О телефоне»
│   │   │   ├── AboutDeviceCustomHook.kt # подмена значений (модель, IMEI и т.д.)
│   │   │   └── ImeiPrivacyHook.kt       # IMEI→нули
│   │   ├── systemui/
│   │   │   ├── HonorShadeTransparentHook.kt # прозрачная шторка
│   │   │   └── LockScreenChargingHook.kt     # зарядка на локскрине
│   │   └── launcher/
│   │       └── CardFolderSlotsHook.kt   # все слоты папок — приложения
│   ├── doom/
│   │   ├── DoomEngine.kt   # JNI мост, lifecycle (attach/detach view)
│   │   └── DoomView.kt     # игровая вьюшка (кнопки + свайпы)
│   ├── settings/           # AppSettings, ImageStore, VideoStore
│   └── ui/                 # Compose экраны приложения
├── app/module-scope.txt    # пакеты для LSPosed scope
└── app/proguard-rules.pro
```

## Текущие хуки (в HookModules.kt)

**SelectedFolderSlotsHook** (`launcher_selected_folder_slots`, выключен по умолчанию) — отдельный экспериментальный хук папок по ID (`FolderInfo.id`, унаследованное поле). Экран «Слоты выбранных папок» показывает названия и ID обнаруженных карточных папок, сохраняет `selected_folder_ids`, поддерживает `selected_folder_all`. Список запрашивается адресным broadcast в лаунчер, ответ адресуется release/debug пакету Honor Tool; настройки читаются только из RemotePreferences. После изменения выбора UI отправляет запрос с задержкой 600 мс, лаунчер вызывает refreshWhenSpansChangeButTypeSame при изменении набора. Старый CardFolderSlotsHook не изменён; совместное включение работает как OR: выбранный ID либо full в названии. После первого включения нужен перезапуск лаунчера. Проверено сборкой, список/обновление/совместимость требуют проверки на телефоне.

1. **AboutPhoneVideoHook** (`settings_about_phone_video`) — 3 режима для карточки «О телефоне»:
   - видео: подмена MediaPlayer.setDataSource
   - фото: скрытие TextureView, картинка в HwImageView
   - DOOM: вставка DoomView вместо видео (нативный PureDOOM)

2. **AboutDeviceCustomHook** (`settings_about_device_custom`) — подмена значений на экране «О телефоне». Контроллеры с `getSummary()` (DeviceModelController, FirmwareVersionController и т.д.) + перехват `Preference.setSummary` по ключу. Поля: модель, прошивка, модем, ядро, сборка, MagicOS, ПЗУ, IMEI, MEID, SELinux, процессор. **Проблема:** CPU и RAM — Honor ставит текст через HwCust-контроллеры напрямую, минуя getSummary/setSummary.

3. **ImeiPrivacyHook** (`settings_imei_privacy`) — TelephonyManager.getImei/getDeviceId/getMeid → 15 нулей в Настройках.

4. **HonorShadeTransparentHook** (`systemui_honor_transparent_shade`) — прозрачная шторка через тонировку WindowBlurView.

5. **LockScreenChargingHook** (`systemui_charging_info`) — отдельный двухстрочный блок зарядки внизу локскрина: статус, затем W/mA/V/°C. Дата остаётся под часами. Обновляется через методы контроллера и каждые 2 секунды, пока view прикреплена.

7. **VolumeStepsHook** (`framework_media_volume_steps`) — 10–100 уровней медиагромкости, по умолчанию 50 (твик выключен по умолчанию). Настройка `media_volume_levels`. Исправление 2026-10-16: вместо перехвата SystemProperties изменяет `AudioService.MAX_STREAM_VOLUME[STREAM_MUSIC]` перед `createStreamStates`; штатный конструктор VolumeStreamState сам копирует максимум в `mIndexMax` (×10) и вызывает нативный `AudioSystem.initStreamVolume`. Значение default пересчитывается пропорционально. `onInitStreamsAndVolumes` деоптимизируется как вызывающий private-метод createStreamStates. После создания состояний логируется фактический максимум (`verified media maximum=50 (internal=500)` при 50 уровнях). Нужны scope `android` и перезагрузка после изменения. `onSystemServerStarting` диспетчеризует хуки с ключом `Constants.SYSTEM_FRAMEWORK` (`android`). Старый перехват свойства не работал по сообщению пользователя; обход оптимизированным кодом — гипотеза, логи с телефона не получены. `assembleDebug` успешен, новая реализация на устройстве не проверена; ADB выполняет пользователь.

6. **CardFolderSlotsHook** (`launcher_card_folder_slots`) — папки с "full" в названии: все слоты — приложения. Динамическая смена через `refreshWhenSpansChangeButTypeSame()`.

## DOOM в «О телефоне» — ключевые детали

- **JNI doomhook.c:** весь DOOM на одном потоке (nativeTick), ввод через очередь в C (nativeKey→queue, drainInput→doom_key_down/up). `#define DOOM_IMPLEMENTATION` + `#define DOOM_IMPLEMENT_MALLOC` ОБЯЗАТЕЛЬНЫ (без второго doom_malloc возвращает NULL → SIGSEGV в IdentifyVersion).
- **Потоки:** никогда не вызывать `doom_init`/`doom_update`/`doom_key_down` из разных потоков — PureDOOM не потокобезопасен.
- **Lifecycle:** DoomEngine.attachView/detachView — поток движка завершается после ~2с простоя, перезапускается при новом открытии. doom_init — один раз на процесс.
- **Управление:** D-pad (кнопки на экране) для движения/стрейфа, свайп правой половины для поворота, FIRE/USE/WPN кнопки, Vol+ = огонь, Vol- = открыть/долгое = Esc.
- **Логи:** `adb logcat -s HonorToolDoom` + `/sdcard/Download/honortool/doom.log`

## Папки (CardFolderSlotsHook) — что выяснено

- Desktop view: `CardFolderIcon` (наследник FolderIcon), число слотов берёт у **калькулятора** (`CardFolderCalculator.getCardFolderMaxNormalIconCount()`)
- Открытая папка: `CardFolder` — копирует значение из того же геттера
- Название: `FolderInfo.mTitle` (не `title`!)
- Динамическая смена: `ClockStyleUpperController` → no, для папок: `CardFolder.refreshWhenSpansChangeButTypeSame()` — тот же путь что при ресайзе
- Регистрация "full": при бинде (`initFolder`) и переименовании (`onTitleChanged`)
- Иконка рабочего стола НЕ в `FolderInfo.mListeners` — хранится в реестре `desktopIcons`

## Зарядка на локскрине — что выяснено

- Нативный виджет: `ChargingStatusTextView` (com.hihonor.keyguard.view.widget) под часами
- Контроллер: `ClockStyleUpperController` (com.android.keyguard.hwlockscreen, classes.dex SystemUI)
- Ключевые методы: `updateChargeViewText(ChargeType)`, `showCharge()`, `showDateOfCharge()`, `isQuickCharging()`, `hideChargeView()`
- Поле `mChargeStatusView` — TextView зарядки; `mChargingState` — ChargeType
- Проблема с исчезновением через 3 сек: `mSwitchDateShowRunnable` переключает на дату
- **НЕ хукать `setText` на TextView** — оно наследуется от TextView и перехватит ВСЕ тексты в SystemUI
- Изменение 2026-10-15: отдельный TextView перенесён в первый почти полноэкранный FrameLayout/RelativeLayout среди предков даты. Выравнивание снизу, отступ 96dp + нижний системный inset, по бокам 24dp. При подключённом питании штатная надпись скрывается, дата остаётся видимой. Блок не зависит от очистки `mChargeStatusView` и штатных анимаций. При отключении питания/выключении экрана скрывается; callbacks удаляются при detach. Требуется визуальная проверка на устройстве, включая контейнер выбранной темы.

## Известные нерешённые проблемы

1. **CPU и RAM в «О телефоне»:** Honor использует HwCust-контроллеры (DeviceCpuAndStorageController, DeviceFeatureInfoController) без `getSummary()`. Текст ставится через `displayPreference` напрямую в HnCardPreference. Решение не найдено.
2. **Зарядка на локскрине:** реализован отдельный постоянный двухстрочный блок внизу экрана; debug-сборка прошла, проверка на устройстве ещё не выполнена.
3. **CardFolderLogHook удалён** — файл удалён, но если у пользователя старый APK, спам в логах продолжается до переустановки.

## Файлы для анализа (в C:\Games\realme\bananas\)

- Исследование жеста Vol+ (2026-10-16): добавлены `services-magic.jar`, `hncustservices.jar`, `framework-magic.jar`, все содержат DEX. `analysis/inspect_volume_keys.py [имя.jar]` извлекает релевантные классы. В `analysis/services-magic-volume-keys.txt` найден `com.android.server.policy.HwPhoneWindowManager.interceptKeyBeforeQueueing(KeyEvent,int)` — штатный двойной Vol−, интервал 400 мс, mVolDownCnt/mLastVolumeDownKeyDownTime, проверка proximity, флаги mIsFlashLightEnable/mIsRapidCaptureEnable. Фонарик: private switchFlashLight(). Камера: private notifyRapidCaptureService(String), action com.hihonor.RapidCapture, package com.hihonor.camera, extra command=wakeup/start; startServiceAsUser(CURRENT_OR_SELF), wakelock 500 мс. Это путь system_server; загрузка Honor Tool туда пока не подтверждена. Новый жест ещё не реализован. Нельзя объявлять команду start подтверждённой фоновой съёмкой без анализа APK камеры и проверки.

- `services.jar` — system_server (дампить dexdump)
- `Интерфейс_системы_(14).apk` — SystemUI (Honor MagicOS 8)
- `Настройки_(14.0.0.210).apks` → внутри `SettingsOversea.apk`
- `Рабочий_экран_HONOR_(14.0.0.245).apks` → внутри `HnLauncher6.apk` (лончер)
- `honor-tool/analysis/systemui/` — уже дампнутые classes*.dex.txt

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

Диагностика system_server (2026-10-16): `XLog` дублирует все уровни и stack traces в `/data/system/honor-tool-volume.log` только при `ModuleLoadedParam.isSystemServer=true`. Файл доступен до разблокировки; ротация после 512 KiB в `.previous`. Читать: `adb shell su -c 'cat /data/system/honor-tool-volume.log'`. Ошибки записи (включая SELinux) выводятся в logcat и журнал Xposed. Если модуль не внедряется в system_server, файл не создаётся. `onPackageReady("android")` пропускается, чтобы не искать AudioService в приложениях, загружающих ресурсы android. Логируются enabled и ошибки RemotePreferences. У хука дополнительно деоптимизируются Nest-мост и AudioHandler.handleMessage, записываются аргументы и status нативного initStreamVolume (0=OK). Последняя debug-сборка успешна; нужна проверка пользователем с темп-рутом после перезапуска zygote.

```bash
# Логи модуля
adb logcat -s HonorTool

# Логи DOOM
adb logcat -s HonorToolDoom

# Логи папок
adb logcat -s HonorToolFolder

# Перезапуск лончера
adb shell am force-stop com.hihonor.android.launcher

# Софт-ребут (kill system_server)
adb shell su -c "kill $(pidof system_server)"
```

## Удалённые хуки (файлы удалены)

- SettingsDividersHook — разделители списков (не работали)
- SoftRebootHook — софт-ребут (пользователь попросил убрать)
- CardFolderLogHook — логи папок (спамил)
- CardFeatureConfigHook, CameraZeissHook, CameraHorizonEisHook — камера (Vivo)
- VivoMultiFreeformHook, VivoIslandAppsHook, VivoShareNoTimeoutHook, VivoNewVolumeUiHook — Vivo
- VolumeStepHook — громкость 2% (не работала в system_server)

## Источник вдохновения

- NothingTweaks: https://github.com/RevealedSoulEven/NothingTweaks
- PureDOOM: https://github.com/Daivuk/PureDOOM
- vivo-tool (наш старый проект): https://github.com/skb8/vivo-tool
