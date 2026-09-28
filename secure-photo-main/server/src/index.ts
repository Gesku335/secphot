import { createDb } from './db.js';
import { Storage } from './storage.js';
import { build } from './app.js';
import { startCleanup } from './cleanup.js';
import { join } from 'node:path';

const PORT = Number(process.env.PORT ?? 3000);
const DATA_DIR = process.env.DATA_DIR ?? join(import.meta.dirname, '..', 'data');
const REGISTRATION_SECRET = process.env.REGISTRATION_SECRET ?? '';
const MAX_ROOMS = Number(process.env.MAX_ROOMS ?? 1);
const MAX_FILE_SIZE = 10 * 1024 * 1024; // 10 MB

const db = createDb(join(DATA_DIR, 'secure-photo.db'));
const storage = new Storage(join(DATA_DIR, 'files'));
await storage.init();

const app = build({ db, storage, maxFileSize: MAX_FILE_SIZE, maxRooms: MAX_ROOMS, registrationSecret: REGISTRATION_SECRET });

const stopCleanup = startCleanup(db, storage);

app.addHook('onClose', async () => {
  stopCleanup();
  db.close();
});

app.listen({ port: PORT, host: '0.0.0.0' }, (err) => {
  if (err) { console.error(err); process.exit(1); }
  console.log(`Server listening on ${PORT}`);
});