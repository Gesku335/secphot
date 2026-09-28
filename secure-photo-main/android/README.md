# Android receiver

Приложение рассчитано на Android 15 / API 35, `minSdk 31`.

Безопасность:

- ECDH P-256 receiver key создаётся в Android Keystore под алиасом `receiver_ecdh` и не экспортируется.
- `FLAG_SECURE`, `setRecentsScreenshotEnabled(false)` и `setHideOverlayWindows(true)` выставляются до Compose.
- Backup и device transfer запрещены, `FileProvider` отсутствует.
- OkHttp cache отключён; payload не записывается на диск.
- При закрытии просмотра и `ON_STOP` byte array обнуляется, Bitmap освобождается.
- Текст водяного знака: `все для тебя любимый Л`, поверх фото отображается текущее время.
- Опциональная биометрия и WorkManager-опрос 15 минут реализованы на этапе 4.
- Play Integrity оставлен отключённым hook-ом этапа 5 до появления Google Cloud проекта.

Перед сборкой заменить `API_BASE_URL` и `WEB_BASE_URL` в `app/build.gradle.kts` на фактический HTTPS-домен владельца.
