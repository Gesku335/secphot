# Secure Photo server

Сервер этапа 1 реализует API из раздела 7 спецификации на Fastify + SQLite. Сервер хранит на диске только зашифрованный blob; токены и sender-secret в SQLite хранятся только как SHA-256 хэши.

## Проверка

```sh
npm install
npm run build
npm test
```

Ожидаемый результат: 6 зелёных тестов, включая параллельное открытие последнего разрешённого просмотра.

## Запуск

```sh
REGISTRATION_SECRET='change-me' \
MAX_ROOMS=1 \
DATA_DIR=./data \
ALLOWED_ORIGIN='https://example.com' \
PORT=3000 \
npm start
```

В production приложение должно быть доступно только через HTTPS reverse proxy. Поддержанные переменные: `REGISTRATION_SECRET`, `MAX_ROOMS`, `DATA_DIR`, `ALLOWED_ORIGIN`, `PORT`.

`GET /healthz` — единственный endpoint без авторизации. Фоновая очистка запускается внутри процесса раз в минуту; она помечает истёкшие/исчерпанные фото и удаляет их зашифрованные blobs.
