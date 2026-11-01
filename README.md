# Honor Tool

LSPosed module (LibXposed API 102) for Honor smartphones running MagicOS 8 / Android 14.

<p>
  <img src="https://img.shields.io/badge/LSPosed-LibXposed%20API%20102-blue" alt="LSPosed LibXposed API 102">
  <img src="https://img.shields.io/badge/platform-Honor%20MagicOS%208%20·%20Android%2014-green" alt="Honor MagicOS 8">
  <img src="https://img.shields.io/badge/license-GPL--3.0-lightgrey" alt="License">
</p>

[English](README.md) | [Русский](README_RU.md)

## Features

| Tweak | Description |
|---|---|
| **Status bar seconds** | Clock shows `HH:mm:ss`, updated every second |
| **Screenshot watermark** | Text (per-line colors) or PNG image in a chosen corner; changes apply instantly |
| **AOD media card** | Album art, title, artist, progress bar and track time on the Always-On Display |
| **AOD display time** | How long the Always-On Display stays on, 5 to 600 seconds |
| **AOD brightness** | Modes: auto / maximum / stock / dim; applies instantly |
| **Transparent shade** | Removes the grey scrim behind the notification panel |
| **Lockscreen charging info** | Watts, current, voltage and battery temperature at the bottom of the lock screen |
| **Custom About phone values** | Replace model, firmware, IMEI and other fields; multi-line input |
| **About phone animation** | Your own video, photo or DOOM in the About phone card |
| **Folders by ID** | Pick card folders where every slot launches an app directly |
| **RAM cleanup** | Force-stops user and preinstalled apps (root) |

## Requirements

- Honor smartphone with MagicOS 8 (Android 14)
- Root access
- LSPosed (or a compatible LibXposed API 102+ framework)

## Installation

1. Install the APK
2. Enable the module in LSPosed
3. Select the scope:
   - `com.android.settings` — Settings
   - `com.android.systemui` — shade, status bar, screenshots, AOD
   - `com.hihonor.android.launcher` — home screen, folders
   - `com.hihonor.aod` — Always-On Display
4. Reboot the device (or restart the relevant processes)

## Building

```bash
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/honor-tool-1-debug.apk
```

Releases are built automatically by GitHub Actions on every tag (e.g. `v1.2.0`) — see [Actions](.github/workflows/release.yml).

An Android SDK with NDK 27.3 and a Linux/WSL environment are required.

## Project structure

```
app/src/main/java/com/fantom3031/honortool/
├── core/       # BaseHook, HookEntry, HookRegistry, preferences
├── hooks/      # All hooks (HookModules.kt — registration point)
│   ├── settings/
│   ├── systemui/
│   ├── launcher/
│   └── aod/
├── doom/        # PureDOOM for the About phone card
├── settings/    # Settings and image storage
└── ui/          # Compose UI
```

## Adding a new hook

1. Create a file in `hooks/<app>/`
2. Extend `BaseHook`
3. Add the object to `HookModules.all`
4. Add the target package to `module-scope.txt`

## Credits

- [NothingTweaks](https://github.com/RevealedSoulEven/NothingTweaks) — inspiration
- [vivo-tool](https://github.com/skb8/vivo-tool) — the original vivo project Honor Tool grew out of
- [PureDOOM](https://github.com/Daivuk/PureDOOM) — DOOM in the About phone card

## License

GPL-3.0
