# Secure Photo

Secure Photo обслуживает одну пару: iOS отправляет фото из Safari, Android получает его в защищённом приложении. Шифрование выполняется на стороне браузера; сервер хранит только ciphertext.

Реализация выполнена по этапам из `SPEC.md`: этап 0 — Node WebCrypto ↔ JVM interop; этап 1 — Fastify/SQLite API; этап 2 — статическая Safari-страница; этап 3 — Android Keystore/Compose receiver; этап 4 — биометрия, ротация и 15-минутный polling; этап 5 — Play Integrity hook; этап 6 — Docker Compose и Caddy.

## Проверка

```sh
cd server
npm install
npm run build
npm test
cd ../tools/interop
./run.sh
```

Перед Android release нужно заменить URL-заглушки в `android/app/build.gradle.kts`, а перед VPS-деплоем заполнить `deploy/.env`.

Основные документы: [спецификация](SPEC.md), [протокол](docs/PROTOCOL.md), [журнал решений](docs/DECISIONS.md), [сервер](server/README.md), [Android](android/README.md), [деплой](deploy/README.md).
