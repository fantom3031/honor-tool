# Honor Tool

Модуль LSPosed (LibXposed API 102) для смартфонов Honor на MagicOS 8 / Android 14.

<p>
  <img src="https://img.shields.io/badge/LSPosed-LibXposed%20API%20102-blue" alt="LSPosed LibXposed API 102">
  <img src="https://img.shields.io/badge/platform-Honor%20MagicOS%208%20·%20Android%2014-green" alt="Honor MagicOS 8">
  <img src="https://img.shields.io/badge/license-GPL--3.0-lightgrey" alt="License">
</p>

[English](README.md) | [Русский](README_RU.md)

## Возможности

| Твик | Описание |
|---|---|
| **Секунды в статус-баре** | Часы показывают `HH:mm:ss`, обновление каждую секунду |
| **Водяной знак на скриншотах** | Текст (цветной построчно) или PNG-картинка в выбранном углу; изменения применяются сразу |
| **Карточка «что играет» на AOD** | Обложка, название, исполнитель, прогресс-бар и время трека на экране Always-On Display |
| **Длительность AOD** | Время показа Always-On Display от 5 до 600 секунд |
| **Яркость AOD** | Режимы: авто / максимум / штатная / тусклая; применяется сразу |
| **Прозрачная шторка** | Убирает серый фон панели уведомлений |
| **Информация о зарядке на локскрине** | Ватты, ток, напряжение и температура батареи внизу экрана блокировки |
| **Свои значения «О телефоне»** | Подмена модели, прошивки, IMEI и других полей; многострочный ввод |
| **Анимация «О телефоне»** | Своё видео, фото или DOOM в карточке «О телефоне» |
| **Папки по ID** | Выбор карточных папок, где каждый слот открывает приложение напрямую |
| **Очистка ОЗУ** | Принудительная остановка сторонних и предустановленных приложений (root) |

## Требования

- Смартфон Honor с MagicOS 8 (Android 14)
- Root-доступ
- LSPosed (или совместимый фреймворк LibXposed API 102+)

## Установка

1. Установите APK
2. Активируйте модуль в LSPosed
3. Выберите область действия (scope):
   - `com.android.settings` — Настройки
   - `com.android.systemui` — шторка, статус-бар, скриншоты, AOD
   - `com.hihonor.android.launcher` — рабочий стол, папки
   - `com.hihonor.aod` — Always-On Display
4. Перезагрузите устройство (или перезапустите соответствующие процессы)

## Сборка

```bash
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/honor-tool-1-debug.apk
```

Релизы собираются автоматически GitHub Actions при создании тега (например, `v1.2.0`) — см. [Actions](.github/workflows/release.yml).

Требуется Android SDK с NDK 27.3 и окружение Linux/WSL.

## Структура

```
app/src/main/java/com/fantom3031/honortool/
├── core/       # BaseHook, HookEntry, HookRegistry, настройки
├── hooks/      # Все хуки (HookModules.kt — точка регистрации)
│   ├── settings/
│   ├── systemui/
│   ├── launcher/
│   └── aod/
├── doom/        # PureDOOM для «О телефоне»
├── settings/    # Хранение настроек и изображений
└── ui/          # Compose-интерфейс
```

## Добавление нового хука

1. Создайте файл в `hooks/<приложение>/`
2. Унаследуйте `BaseHook`
3. Добавьте объект в `HookModules.all`
4. Добавьте пакет в `module-scope.txt`

## Благодарности

- [NothingTweaks](https://github.com/RevealedSoulEven/NothingTweaks) — вдохновение
- [vivo-tool](https://github.com/skb8/vivo-tool) — оригинальный vivo-проект, из которого вырос Honor Tool
- [PureDOOM](https://github.com/Daivuk/PureDOOM) — DOOM в «О телефоне»

## Лицензия

GPL-3.0
