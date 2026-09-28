# Secure Photo — протокол шифрования

## 5. Протокол шифрования (нормативная часть)

Схема ECIES-подобная, на примитивах, которые есть и в WebCrypto, и в Android без сторонних библиотек.

Ключи получателя:
- Пара ECDH на кривой P-256 (`secp256r1`), создаётся в Android Keystore, закрытый ключ неизвлекаемый.
- Публичный ключ экспортируется в формате X.509 SubjectPublicKeyInfo (SPKI, то, что возвращает `PublicKey.getEncoded()`) и кодируется base64url без padding.

Шифрование (на стороне отправителя, в браузере):
1. `photo_id` = случайный UUID v4, генерируется в браузере.
2. Импортировать публичный ключ получателя: `crypto.subtle.importKey('spki', ..., {name:'ECDH', namedCurve:'P-256'}, false, [])`.
3. Сгенерировать эфемерную пару ECDH P-256.
4. `shared = ECDH(eph_private, receiver_public)` (32 байта, `deriveBits`, 256 бит).
5. `key = HKDF-SHA256(ikm = shared, salt = 32 нулевых байта, info = UTF-8("securephoto-v1"), length = 32)`.
6. `iv` = 12 случайных байт.
7. `ct = AES-256-GCM(key, iv, plaintext = JPEG-байты, aad = UTF-8(photo_id))`, тег 128 бит дописан в конец шифротекста (стандартное поведение WebCrypto).
8. Полезная нагрузка (то, что уходит на сервер): `eph_public_raw (65 байт: 0x04 || X || Y) || iv (12 байт) || ct`.

Расшифровка (Android):
1. Разобрать нагрузку: первые 65 байт, затем 12, остальное шифротекст.
2. Из 65 байт собрать `ECPublicKey` через `ECPublicKeySpec(ECPoint(x, y), params)`, где `params` взяты из публичного ключа получателя.
3. `KeyAgreement.getInstance("ECDH")`, `init(keystorePrivateKey)`, `doPhase(ephPublic, true)`, `generateSecret()`.
4. HKDF вручную через HMAC-SHA256: `prk = HMAC(key = 32 нулевых байта, data = shared)`, `key = HMAC(key = prk, data = info || 0x01)`. Длина 32 байта, поэтому нужен один блок.
5. `Cipher.getInstance("AES/GCM/NoPadding")`, `GCMParameterSpec(128, iv)`, `updateAAD(photo_id)`, `doFinal(ct)`.

Требования к входным данным браузера: перед шифрованием фото перекодируется через canvas в JPEG, качество 0.85, длинная сторона не более 2048 px. Это заодно убирает EXIF (геометки, модель камеры). Итоговый размер не более 15 МБ.
