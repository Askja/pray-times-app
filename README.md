# Pray Times

Android-приложение на Kotlin для расчета времен намаза, киблы и исламских праздников.

## Что внутри

- Порт расчетов из `adhan-csharp-.net-9.0-master` в `app/src/main/java/com/praytimes/app/adhan`.
- Расчет Fajr, Sunrise, Dhuhr, Asr, Maghrib, Isha по стандартным методам Adhan.
- Выбор мазхаба для Asr, учет high latitude правил и method adjustments.
- Текущая геолокация через Android `LocationManager`.
- Встроенная база городов `app/src/main/assets/cities.csv`.
- Fallback-геокодинг адресов и городов всего мира:
  `OpenStreetMap Nominatim`, `Komoot Photon`, `Yandex Geocoder`, `2GIS Geocoder`, `OpenCage`.
- Сохранение дополнительных локаций в локальную SQLite-БД устройства.
- Заметки к локациям и offsets икамы/джамаата для активной сохраненной локации.
- Расчетный хиджри-календарь и основные мусульманские праздники.
- Ramadan-aware подписи: Fajr/Suhoor, Maghrib/Iftar, Isha/Tarawih.
- Главная показывает текущий намаз, остаток до следующего и подсвечивает активный намаз.
- Расширенный виджет показывает текущий намаз, прогресс, countdown и киблу.
- Настраиваемый звук азана: встроенный, системный или тихое уведомление.
- Экспорт и импорт настроек/локаций в JSON.
- Светлая и темная темы переключаются системным `values-night`.
- 25 ресурсных локалей, включая базовый English.
- Adaptive launcher icon, monochrome icon и иконки для основных действий.

## Материалы для публикации

- Поля темы 4PDA: `docs/4pda-topic-fields.md`.
- Актуальные скриншоты: `screenshots/pray-times-*-current.png`.
- Иконка 4PDA 200x200: `docs/4pda/prayer-times-icon-4pda.png`.
- Реестр релизных материалов: `docs/release-assets.md`.
- F-Droid metadata: `fastlane/metadata/android`.
- Черновик F-Droid metadata для `fdroiddata`: `docs/fdroid/ru.admiral.praytimes.yml`.
- Инструкция публикации в F-Droid: `docs/fdroid/README.md`.

Публичный репозиторий: https://github.com/Askja/pray-times-app

Релиз 0.1.1: https://github.com/Askja/pray-times-app/releases/tag/v0.1.1

## Лицензия

Проект опубликован под Apache-2.0. Автор: Askja.

## API-ключи геокодеров

Nominatim и Photon работают без ключа и используются только по нажатию кнопки поиска. Для коммерческих fallback-провайдеров можно добавить Gradle properties:

```properties
YANDEX_GEOCODER_API_KEY=...
TWOGIS_API_KEY=...
OPENCAGE_API_KEY=...
```

## Сборка

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug lintDebug
```

APK после сборки:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Праздники считаются по табличному календарю Хиджры. Для реального объявления праздников все равно решает локальное наблюдение луны, тут без магии и сказок.
