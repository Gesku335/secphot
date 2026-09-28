#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
rm -f InteropJvm.class node-payload.bin java-payload.bin node-plaintext.bin node-from-java.bin
javac InteropJvm.java
node node-interop.mjs encrypt
java InteropJvm
node node-interop.mjs decrypt
cmp -s node-from-java.bin node-plaintext.bin
printf 'Interop stage 0: PASS (Node WebCrypto <-> JVM, format, AAD, tamper checks)\n'
