# Релизные материалы

Обновлено: 05.07.2026.

## Иконки

| Файл | Назначение |
| --- | --- |
| `docs/4pda/prayer-times-icon-4pda.png` | Основная иконка для 4PDA, 200x200 PNG. |
| `docs/4pda/prayer-times-icon-circle-4pda.png` | Круглая transparent-версия 200x200. |
| `docs/4pda/prayer-times-icon-512.png` | Крупная версия для черновиков и превью. |
| `fastlane/metadata/android/*/images/icon.png` | Иконка для F-Droid metadata. |

## Скриншоты

| Файл | Что показывает |
| --- | --- |
| `screenshots/pray-times-main-current.png` | Главный экран, активный намаз без красного текста на зеленом фоне. |
| `screenshots/pray-times-location-top-current.png` | Управление локациями: карта, поиск города/адреса, источники локации. |
| `screenshots/pray-times-location-note-current.png` | Сохранение локации: имя, заметка, цвет, иконка. |
| `screenshots/pray-times-settings-current.png` | Верх настроек и входы в связанные экраны. |
| `screenshots/pray-times-settings-iqama-current.png` | Калибровка минут, икама/джамаат offsets и начало уведомлений. |
| `screenshots/pray-times-settings-backup-actions-current.png` | Звук азана, профили уведомлений и JSON backup. |

## Дополнительные кадры

| Файл | Что показывает |
| --- | --- |
| `screenshots/pray-times-settings-notifications-current.png` | Общие настройки, автопереключение локации, порядок блоков главного экрана. |
| `screenshots/pray-times-settings-adhan-current.png` | Порядок блоков и переход к калибровкам. |
| `screenshots/pray-times-settings-backup-current.png` | Уведомления, звук азана и профили по намазам. |

## F-Droid

| Файл | Назначение |
| --- | --- |
| `LICENSE` | FLOSS-лицензия проекта. |
| `fastlane/metadata/android/en-US` | Обязательные fallback-метаданные F-Droid. |
| `fastlane/metadata/android/ru` | Русские метаданные F-Droid. |
| `docs/fdroid/ru.admiral.praytimes.yml` | Черновик metadata для merge request в `fdroiddata`. |
| `docs/fdroid/README.md` | Порядок публикации в F-Droid. |

## Перед публикацией

- Загружать `app/build/outputs/apk/release/pray-times-0.1.1-4pda-signed.apk`, а не debug, unsigned или aligned.
- Для пересборки подписанного APK использовать `scripts/build-4pda-release.ps1`.
- Проверить, что версия в теме совпадает с `versionName`.
- Перед F-Droid merge request опубликовать Git-репозиторий и тег `v0.1.1`.
- Перед F-Droid merge request подтвердить свободную лицензию `adhan.ogg` и texture PNG.
- Для 4PDA использовать `docs/4pda/prayer-times-icon-4pda.png`, потому что это уже нужный размер, без плясок с ресайзом.
