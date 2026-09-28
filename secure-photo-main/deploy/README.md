# Развёртывание на VPS

Нужен VPS с Docker Compose и доменом, направленным A/AAAA-записями на IP VPS. Перед запуском скопировать `.env.example` в `.env`, указать фактический домен и длинный `REGISTRATION_SECRET`, затем выполнить:

```sh
cd deploy
cp .env.example .env
# отредактировать .env
DOMAIN=photos.example.com docker compose config
DOMAIN=photos.example.com docker compose up -d --build
curl -fsS https://photos.example.com/healthz
```

Caddy сам получает и продлевает сертификат Let’s Encrypt. Порты 80 и 443 должны быть доступны извне. Данные сервера и Caddy находятся в Docker volumes; фото эфемерны и резервное копирование не требуется согласно спецификации.

После запуска заменить `API_BASE_URL` и `WEB_BASE_URL` в Android `app/build.gradle.kts` на `https://photos.example.com`, собрать release APK и установить на Android 15. Страница отправителя доступна по `https://photos.example.com/s/`.

Проверка эксплуатации: `/healthz` доступен, в логах нет заголовков/токенов, на серверном volume blobs имеют расширение `.bin` и не определяются как JPEG, после последнего просмотра blob удаляется.
