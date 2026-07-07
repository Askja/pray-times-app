# F-Droid публикация

F-Droid не принимает APK как файлик на загрузку. Он собирает приложение из публичного исходного кода, поэтому сначала нужен открытый Git-репозиторий с реальным кодом, FLOSS-лицензией и тегом релиза.

## Что уже подготовлено

- `LICENSE` - Apache-2.0, автор Askja.
- `fastlane/metadata/android/en-US` - fallback-метаданные для F-Droid.
- `fastlane/metadata/android/ru` - русские метаданные.
- `docs/fdroid/ru.admiral.praytimes.yml` - черновик metadata-файла для `fdroiddata`.

## Что сделать перед merge request в F-Droid

1. Создать публичный репозиторий GitHub/GitLab/Codeberg.
2. Добавить remote и запушить проект:

```powershell
git remote add origin https://github.com/Askja/pray-times-app.git
git push -u origin main
```

3. Поставить тег на коммит релиза:

```powershell
git tag -a v0.1.1 -m "Версия 0.1.1"
git push origin v0.1.1
```

4. Проверить, что `docs/fdroid/ru.admiral.praytimes.yml` указывает на реальный URL:
   `https://github.com/Askja/pray-times-app`.
5. Проверить, что бинарные ассеты тоже можно публиковать как FLOSS:
   `app/src/main/res/raw/adhan.ogg`,
   `app/src/main/res/drawable-nodpi/*.png`,
   `app/src/main/res/drawable-night-nodpi/*.png`.
   Если что-то не своё и без свободной лицензии, заменить до MR, а не устраивать юридический косплей.
6. Форкнуть `https://gitlab.com/fdroid/fdroiddata`.
7. Скопировать YAML в `metadata/ru.admiral.praytimes.yml` внутри `fdroiddata`.
8. Запустить локальную проверку, если установлен `fdroidserver`:

```powershell
fdroid lint ru.admiral.praytimes
fdroid build -v -l ru.admiral.praytimes
```

9. Открыть merge request в `fdroid/fdroiddata`.

Без публичного `Repo` и тега `v0.1.1` MR завернут. И правильно сделают, потому что магия из APK без исходников F-Droid не нужна.
