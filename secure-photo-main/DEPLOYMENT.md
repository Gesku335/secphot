# Пошаговое развёртывание Secure Photo на VPS

Инструкция рассчитана на чистый Linux VPS с публичным IPv4/IPv6, Docker Engine, Docker Compose v2 и доменом. Сервер обслуживает одну пару пользователей (`MAX_ROOMS=1`) и хранит на диске только зашифрованные payload-файлы.

## 1. Подготовить DNS и firewall

Создайте A-запись домена на публичный IPv4 VPS и, если используете IPv6, AAAA-запись. До запуска проверьте, что домен резолвится на этот VPS. Откройте TCP-порты 80 и 443; SSH оставьте доступным только с административных адресов.

Caddy использует порт 80 для ACME challenge и перенаправления на HTTPS, а порт 443 — для приложения.

## 2. Установить Docker

На Ubuntu Server выполните официальную установку Docker Engine и Compose plugin, затем проверьте:

```sh
sudo docker --version
sudo docker compose version
```

Не запускайте приложение от имени root внутри контейнера: production Dockerfile использует пользователя `node`.

## 3. Получить исходники

```sh
git clone <URL-РЕПОЗИТОРИЯ> secure-photo
cd secure-photo
```

Перед запуском убедитесь, что в репозитории присутствуют `deploy/docker-compose.yml`, `deploy/Caddyfile`, `server/Dockerfile` и каталог `web`.

## 4. Создать секреты окружения

```sh
cd deploy
cp .env.example .env
openssl rand -base64 48
```

Откройте `.env` и укажите:

```dotenv
DOMAIN=photos.example.com
REGISTRATION_SECRET=<длинная-случайная-строка>
MAX_ROOMS=1
```

`REGISTRATION_SECRET` нужен только приложению-получателю при первичном создании комнаты. Не коммитьте `.env`, не вставляйте секреты в URL, issue или логи.

## 5. Проверить итоговую Compose-конфигурацию

```sh
docker compose --env-file .env config
```

В выводе не должно быть пропущенных переменных, а `ALLOWED_ORIGIN` должен указывать на `https://<DOMAIN>`.

## 6. Запустить приложение

```sh
docker compose --env-file .env up -d --build
docker compose ps
docker compose logs --tail=100 server caddy
```

Caddy автоматически запросит сертификат Let’s Encrypt. Если сертификат не выпускается, проверьте DNS, открытые порты 80/443 и отсутствие другого reverse proxy.

## 7. Проверить health endpoint и HTTPS

```sh
curl --fail --silent --show-error https://photos.example.com/healthz
curl --head https://photos.example.com/s/
```

Ожидается JSON `{"ok":true}`, успешный HTTPS-ответ и заголовок `Strict-Transport-Security`.

## 8. Настроить Android release

В `android/app/build.gradle.kts` замените обе строки:

```kotlin
buildConfigField("String", "API_BASE_URL", "\"https://secure-photo.example\"")
buildConfigField("String", "WEB_BASE_URL", "\"https://secure-photo.example\"")
```

на фактический домен, например:

```kotlin
buildConfigField("String", "API_BASE_URL", "\"https://photos.example.com\"")
buildConfigField("String", "WEB_BASE_URL", "\"https://photos.example.com\"")
```

Соберите release APK на машине с Android SDK/Gradle:

```sh
cd android
./gradlew assembleRelease
```

Перед установкой проверьте, что release не debuggable. На Android 15 выполните ручной security-чеклист из `SPEC.md`: screenshot, screen recording, recent apps, cast, `adb screencap`, backup, `logcat`, filesystem и последний просмотр.

## 9. Первичная настройка комнаты

1. Запустите приложение на Android.
2. На экране onboarding введите `REGISTRATION_SECRET`.
3. Приложение создаст ECDH-ключ в Keystore и комнату на сервере.
4. Покажите QR или поделитесь ссылкой отправителю.
5. Откройте ссылку на iPhone в Safari и отправьте тестовое фото.
6. В Android откройте фото и подтвердите, что отображение работает.

## 10. Проверить end-to-end безопасность

Проверьте, что в server volume нет JPEG-файлов:

```sh
docker volume ls | grep secure
# имя volume уточняется командой выше
sudo find /var/lib/docker/volumes -type f -name '*.bin' -print
```

После исчерпания лимита просмотров сервер должен вернуть `410`, а blob должен быть удалён cleanup-задачей. Не включайте debug logging и не добавляйте middleware, которое пишет request headers/body.

## 11. Обновление

```sh
cd secure-photo/deploy
git pull --ff-only
docker compose --env-file .env up -d --build
docker compose ps
docker compose logs --tail=100 server caddy
```

Данные находятся в Docker volumes и переживают обновление контейнеров. Перед обновлением сохраняйте `.env` отдельно безопасным способом; не добавляйте его в Git.

## 12. Остановка и аварийная диагностика

```sh
docker compose ps
docker compose logs --tail=200 server
docker compose logs --tail=200 caddy
docker compose restart server caddy
```

Для остановки без удаления данных:

```sh
docker compose down
```

Не выполняйте `docker compose down -v`, если хотите сохранить SQLite и файлы до истечения их TTL. Удаление volumes уничтожает эфемерные данные без возможности восстановления.
