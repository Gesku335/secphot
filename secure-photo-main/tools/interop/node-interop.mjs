import { webcrypto } from 'node:crypto';
import { readFile, writeFile } from 'node:fs/promises';
const { subtle } = webcrypto;
const b64 = s => Buffer.from(s, 'base64');
const b64u = b => Buffer.from(b).toString('base64url');
const concat = (...xs) => { const out = new Uint8Array(xs.reduce((n, x) => n + x.length, 0)); let p = 0; for (const x of xs) { out.set(x, p); p += x.length; } return out; };
const info = new TextEncoder().encode('securephoto-v1');
const aad = new TextEncoder().encode('interop-photo-0001');
const plaintext = new TextEncoder().encode('fixed JPEG-like bytes: \xff\xd8\xff\xe0 secure-photo interop \xff\xd9');
const privateDer = b64(await readFile('receiver-private.pk8.b64', 'utf8'));
const publicDer = b64(await readFile('receiver-public.spki.b64', 'utf8'));
const ephPrivateDer = b64(await readFile('eph-private.pk8.b64', 'utf8'));
const ephPublicDer = b64(await readFile('eph-public.spki.b64', 'utf8'));
const receiverPrivate = await subtle.importKey('pkcs8', privateDer, { name: 'ECDH', namedCurve: 'P-256' }, false, ['deriveBits']);
const receiverPublic = await subtle.importKey('spki', publicDer, { name: 'ECDH', namedCurve: 'P-256' }, false, []);
async function derive(privateKey, publicKey) {
  const shared = new Uint8Array(await subtle.deriveBits({ name: 'ECDH', public: publicKey }, privateKey, 256));
  const hkdf = await subtle.importKey('raw', shared, 'HKDF', false, ['deriveBits']);
  return new Uint8Array(await subtle.deriveBits({ name: 'HKDF', hash: 'SHA-256', salt: new Uint8Array(32), info }, hkdf, 256));
}
async function encrypt() {
  const eph = {
    privateKey: await subtle.importKey('pkcs8', ephPrivateDer, { name: 'ECDH', namedCurve: 'P-256' }, false, ['deriveBits']),
    publicKey: await subtle.importKey('spki', ephPublicDer, { name: 'ECDH', namedCurve: 'P-256' }, true, [])
  };
  const keyBytes = await derive(eph.privateKey, receiverPublic);
  const key = await subtle.importKey('raw', keyBytes, 'AES-GCM', false, ['encrypt']);
  const iv = Uint8Array.from(Buffer.from('00112233445566778899aabb', 'hex'));
  const ct = new Uint8Array(await subtle.encrypt({ name: 'AES-GCM', iv, additionalData: aad, tagLength: 128 }, key, plaintext));
  const ephRaw = new Uint8Array(await subtle.exportKey('raw', eph.publicKey));
  await writeFile('node-payload.bin', concat(ephRaw, iv, ct));
  await writeFile('node-plaintext.bin', plaintext);
}
async function decryptJavaPayload() {
  const payload = new Uint8Array(await readFile('java-payload.bin'));
  if (payload.length < 65 + 12 + 16 || payload[0] !== 4) throw new Error('bad payload format');
  const raw = payload.slice(0, 65);
  const spkiPrefix = b64('MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE');
  const ephPublic = await subtle.importKey('spki', concat(spkiPrefix, raw.slice(1)), { name: 'ECDH', namedCurve: 'P-256' }, false, []);
  const keyBytes = await derive(receiverPrivate, ephPublic);
  const key = await subtle.importKey('raw', keyBytes, 'AES-GCM', false, ['decrypt']);
  const iv = payload.slice(65, 77), ct = payload.slice(77);
  const out = new Uint8Array(await subtle.decrypt({ name: 'AES-GCM', iv, additionalData: aad, tagLength: 128 }, key, ct));
  await writeFile('node-from-java.bin', out);
}
async function rejectsInvalidInputs() {
  const payload = new Uint8Array(await readFile('node-payload.bin'));
  const raw = payload.slice(0, 65), spkiPrefix = b64('MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE');
  const ephPublic = await subtle.importKey('spki', concat(spkiPrefix, raw.slice(1)), { name: 'ECDH', namedCurve: 'P-256' }, false, []);
  const keyBytes = await derive(receiverPrivate, ephPublic);
  const key = await subtle.importKey('raw', keyBytes, 'AES-GCM', false, ['decrypt']);
  const iv = payload.slice(65, 77), ct = payload.slice(77);
  for (const [name, data, associated] of [['bad-aad', ct, new TextEncoder().encode('wrong-aad')], ['corrupt-ct', (() => { const x = ct.slice(); x[0] ^= 1; return x; })(), aad]]) {
    let failed = false; try { await subtle.decrypt({ name: 'AES-GCM', iv, additionalData: associated, tagLength: 128 }, key, data); } catch { failed = true; }
    if (!failed) throw new Error(`${name} unexpectedly decrypted`);
  }
}
const mode = process.argv[2];
if (mode === 'encrypt') {
  await encrypt();
  console.log('Node WebCrypto: encrypted payload');
} else if (mode === 'decrypt') {
  await decryptJavaPayload();
  await rejectsInvalidInputs();
  console.log('Node WebCrypto: decrypted JVM payload, rejected bad AAD and corrupted ciphertext');
} else {
  throw new Error('usage: node node-interop.mjs encrypt|decrypt');
}
