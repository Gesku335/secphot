const text = new TextEncoder();
const state = { room: '', secret: '', publicKey: '' };
const $ = id => document.getElementById(id);
const apiBase = location.origin;
const concat = (...parts) => { const out = new Uint8Array(parts.reduce((n, p) => n + p.length, 0)); let offset = 0; for (const p of parts) { out.set(p, offset); offset += p.length; } return out; };
const uuid = () => crypto.randomUUID();
function decodeUrlPart(value) { return value.replace(/-/g, '+').replace(/_/g, '/') + '='.repeat((4 - value.length % 4) % 4); }
function fromBase64Url(value) { const raw = atob(decodeUrlPart(value)); return Uint8Array.from(raw, c => c.charCodeAt(0)); }
function setStatus(message, error = false) { const el = $('progress'); el.textContent = message; el.style.color = error ? '#b42318' : ''; }
function readInvite() {
  const params = new URLSearchParams(location.hash.slice(1));
  const incoming = { room: params.get('r'), secret: params.get('k'), publicKey: params.get('pk') };
  if (incoming.room && incoming.secret && incoming.publicKey) { localStorage.setItem('secure-photo-invite', JSON.stringify(incoming)); history.replaceState(null, '', `${location.pathname}${location.search}`); return incoming; }
  try { return JSON.parse(localStorage.getItem('secure-photo-invite') || 'null') || {}; } catch { return {}; }
}
async function normalizeImage(file) {
  const bitmap = await createImageBitmap(file, { imageOrientation: 'from-image' });
  const scale = Math.min(1, 2048 / Math.max(bitmap.width, bitmap.height));
  const canvas = document.createElement('canvas'); canvas.width = Math.max(1, Math.round(bitmap.width * scale)); canvas.height = Math.max(1, Math.round(bitmap.height * scale));
  const ctx = canvas.getContext('2d', { alpha: false }); ctx.drawImage(bitmap, 0, 0, canvas.width, canvas.height); bitmap.close();
  const blob = await new Promise(resolve => canvas.toBlob(resolve, 'image/jpeg', .85)); if (!blob) throw new Error('image_encode');
  if (blob.size > 15 * 1024 * 1024) throw new Error('too_large'); return new Uint8Array(await blob.arrayBuffer());
}
async function encryptPhoto(plain, photoId) {
  const receiver = await crypto.subtle.importKey('spki', fromBase64Url(state.publicKey), { name: 'ECDH', namedCurve: 'P-256' }, false, []);
  const ephemeral = await crypto.subtle.generateKey({ name: 'ECDH', namedCurve: 'P-256' }, true, ['deriveBits']);
  const shared = new Uint8Array(await crypto.subtle.deriveBits({ name: 'ECDH', public: receiver }, ephemeral.privateKey, 256));
  const hkdfKey = await crypto.subtle.importKey('raw', shared, 'HKDF', false, ['deriveBits']);
  const keyBytes = new Uint8Array(await crypto.subtle.deriveBits({ name: 'HKDF', hash: 'SHA-256', salt: new Uint8Array(32), info: text.encode('securephoto-v1') }, hkdfKey, 256));
  const aesKey = await crypto.subtle.importKey('raw', keyBytes, 'AES-GCM', false, ['encrypt']);
  const iv = crypto.getRandomValues(new Uint8Array(12)); const aad = text.encode(photoId);
  const ciphertext = new Uint8Array(await crypto.subtle.encrypt({ name: 'AES-GCM', iv, additionalData: aad, tagLength: 128 }, aesKey, plain));
  const ephemeralRaw = new Uint8Array(await crypto.subtle.exportKey('raw', ephemeral.publicKey));
  return concat(ephemeralRaw, iv, ciphertext);
}
async function send() {
  const file = $('photo').files?.[0]; if (!file) return;
  $('send').disabled = true;
  try {
    setStatus('Подготавливаем изображение…'); const plain = await normalizeImage(file); const id = uuid();
    setStatus('Шифруем в браузере…'); const payload = await encryptPhoto(plain, id);
    setStatus('Отправляем шифротекст…'); const response = await fetch(`${apiBase}/api/photos/${id}`, { method: 'PUT', headers: { Authorization: `Bearer ${state.secret}`, 'X-Room-Id': state.room, 'X-Max-Views': $('maxViews').value, 'X-Ttl-Seconds': $('ttl').value, 'Content-Type': 'application/octet-stream' }, body: payload, cache: 'no-store' });
    if (!response.ok) { const code = (await response.json().catch(() => ({}))).error; throw new Error(code || 'network'); }
    setStatus('Фото отправлено. Можно выбрать следующее.'); $('photo').value = ''; $('preview').classList.add('hidden');
  } catch (err) { const messages = { too_large: 'Файл после обработки больше 15 МБ.', image_encode: 'Не удалось обработать изображение.', network: 'Нет сети или ссылка отозвана.' }; setStatus(messages[err.message] || 'Не удалось отправить фото.', true); } finally { $('send').disabled = false; }
}
$('photo').addEventListener('change', () => { const file = $('photo').files?.[0]; if (!file) return; $('preview').src = URL.createObjectURL(file); $('preview').classList.remove('hidden'); $('send').disabled = false; setStatus('Фото готово к отправке.'); });
$('send').addEventListener('click', send);
Object.assign(state, readInvite());
if (!state.room || !state.secret || !state.publicKey || !window.isSecureContext || !crypto.subtle) { $('missing').classList.remove('hidden'); if (!window.isSecureContext) setStatus('Откройте страницу по HTTPS.', true); } else { $('sender').classList.remove('hidden'); }
