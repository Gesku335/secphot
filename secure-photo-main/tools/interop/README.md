# Этап 0: WebCrypto ↔ JVM interop

Запуск из корня репозитория:

```sh
./tools/interop/run.sh
```

Сценарий компилирует `InteropJvm.java`, выполняет фиксированное направление Node WebCrypto → JVM и обратное направление JVM → Node WebCrypto. Проверяются:

- P-256 ECDH и raw ephemeral public key длиной 65 байт;
- HKDF-SHA256 с нулевым 32-байтовым salt и `securephoto-v1`;
- AES-256-GCM с 12-байтовым IV, 128-битным тегом и AAD `photo_id`;
- отказ при неверном AAD;
- отказ при изменении ciphertext;
- сравнение расшифрованных байтов с исходным фиксированным тестовым файлом.

`fixed-vector.sha256` содержит SHA-256 контрольные суммы payload и plaintext, созданные последним успешным запуском.
