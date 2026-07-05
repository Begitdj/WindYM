# WindYM Android — Руководство по сборке

## Требования

- JDK 11+ (установлен: OpenJDK 26)
- Android SDK (установлен в `/opt/android-sdk`)
- Gradle 8.9+
- Platform: android-37.0
- Build Tools: 37.0.0

## Сборка

```bash
cd android
export ANDROID_HOME=/opt/android-sdk
export ANDROID_SDK_ROOT=/opt/android-sdk

# Debug APK
gradle assembleDebug

# Release APK (без подписи)
gradle assembleRelease
```

APK находится в: `app/build/outputs/apk/debug/app-debug.apk`

## Установка на устройство

```bash
# Через ADB (USB отладка должна быть включена)
adb install app/build/outputs/apk/debug/app-debug.apk

# Или через эмулятор
adb -e install app-debug.apk
```

## Возможные проблемы

### Ошибка лицензий
```bash
$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --licenses
```

### Нет platform android-37
```bash
$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager "platforms;android-37"
```

## Структура кода

### API Client (`YandexMusicApiClient.java`)
- Имитирует Electron-клиент v5.78.7
- User-Agent: `YandexMusic/5.78.7 Electron/31.7.7`
- Все заголовки X-Yandex-Music-*
- TLS 1.2/1.3 через OkHttp 4.12

### Плеер (`PlayerService.java`)
- ExoPlayer 1.5.1 в foreground service
- Уведомление с управлением
- MediaSession для аппаратных кнопок

### UI
- Material Design 3 (тёмная тема)
- RecyclerView с Picasso для обложек
- Bottom Navigation (3 вкладки)
- Mini player bar в MainActivity
- Full player в NowPlayingActivity
