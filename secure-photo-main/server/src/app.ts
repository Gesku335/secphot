import Fastify from 'fastify';
import cors from '@fastify/cors';
import multipart from '@fastify/multipart';
import rateLimit from '@fastify/rate-limit';
import websocket from '@fastify/websocket';
import { type Database } from 'better-sqlite3';
import { generateToken, bearer } from './auth.js';
import { Storage } from './storage.js';
import { WebSocket } from 'ws';

export interface Options {
  db: Database;
  storage: Storage;
  maxFileSize: number;
  maxRooms: number;
  registrationSecret: string;
}

export function build(opts: Options) {
  const { db, storage, maxFileSize, maxRooms, registrationSecret } = opts;

  const app = Fastify({ logger: true });

  // ── Sender devices ─────────────────────────────────────────────
  interface SenderDevice {
    socket: WebSocket;
    roomId: string;
  }
  const senders = new Set<SenderDevice>();

  app.register(cors, {
    origin: (origin, cb) => cb(null, !origin || /localhost|127\.0\.0\.1/.test(origin)),
    credentials: true,
    methods: ['GET', 'POST', 'PUT', 'DELETE', 'OPTIONS'],
    allowedHeaders: ['Content-Type', 'Authorization', 'Accept', 'Origin', 'X-Requested-With'],
    exposedHeaders: ['Content-Length'],
    maxAge: 86400,
  });
  app.register(multipart, { limits: { fileSize: maxFileSize } });
  app.register(rateLimit, { max: 100, timeWindow: '1 minute' });
  app.register(websocket);

  // ── Sender account ─────────────────────────────────────────────
  app.post('/api/register', async (req, reply) => {
    const body = req.body as Record<string, unknown>;
    if (typeof body?.secret !== 'string' || body.secret !== registrationSecret)
      return reply.code(403).send({ error: 'Invalid registration secret' });
    const maxRoomsLimit = typeof body.maxRooms === 'number' ? body.maxRooms : maxRooms;
    const roomCount = db.prepare('SELECT COUNT(*) as c FROM rooms').get() as { c: number };
    if (roomCount.c >= maxRoomsLimit) return reply.code(409).send({ error: 'Room limit reached' });
    const roomToken = generateToken(32);
    const roomId = generateToken(16);
    db.prepare('INSERT INTO rooms (id, token, created_at) VALUES (?, ?, datetime(\'now\'))').run(roomId, roomToken);
    return reply.send({ roomId, roomToken });
  });

  app.get('/api/verify', async (req, reply) => {
    const token = bearer(req);
    if (!token) return reply.code(401).send({ error: 'Missing token' });
    const room = db.prepare('SELECT id FROM rooms WHERE token = ?').get(token);
    if (!room) return reply.code(401).send({ error: 'Invalid token' });
    return reply.send({ ok: true });
  });

  // ── WebSocket for sender ───────────────────────────────────────
  app.get('/api/ws', { websocket: true }, (socket, req) => {
    const token = new URL(req.url, 'http://localhost').searchParams.get('token');
    const room = db.prepare('SELECT id FROM rooms WHERE token = ?').get(token) as { id: string } | undefined;
    if (!room) { socket.close(4001, 'Invalid token'); return; }

    const device: SenderDevice = { socket: socket as unknown as WebSocket, roomId: room.id };
    senders.add(device);

    // Send pending inbox items
    try {
      const pending = db.prepare(
        `SELECT m.id, m.created_at, m.size,
                CASE WHEN EXISTS (SELECT 1 FROM message_views WHERE message_id = m.id) THEN 1 ELSE 0 END as viewed
         FROM messages m WHERE m.room_id = ? ORDER BY m.created_at DESC LIMIT 20`
      ).all(room.id) as { id: string; created_at: string; size: number; viewed: number }[];

      socket.send(JSON.stringify({
        type: 'inbox',
        items: pending.map(m => ({ id: m.id, ts: new Date(m.created_at + 'Z').getTime(), bytes: m.size, viewed: m.viewed === 1 }))
      }));
    } catch { /* empty db */ }

    socket.on('close', () => senders.delete(device));
    socket.on('error', () => senders.delete(device));
  });

  // ── Health ─────────────────────────────────────────────────────
  app.get('/healthz', async () => ({ status: 'ok' }));

  // ── Upload (sender) ────────────────────────────────────────────
  app.post('/api/upload', async (req, reply) => {
    const token = bearer(req);
    if (!token) return reply.code(401).send({ error: 'Missing token' });
    const room = db.prepare('SELECT id FROM rooms WHERE token = ?').get(token) as { id: string } | undefined;
    if (!room) return reply.code(401).send({ error: 'Invalid token' });

    const file = await req.file();
    if (!file) return reply.code(400).send({ error: 'No file' });
    const chunks: Buffer[] = [];
    for await (const chunk of file.file) chunks.push(chunk);
    const buf = Buffer.concat(chunks);
    if (buf.length > maxFileSize) return reply.code(413).send({ error: 'File too large' });

    const id = generateToken(16);
    await storage.save(id, buf);

    db.prepare('INSERT INTO messages (id, room_id, file_name, size, created_at) VALUES (?, ?, ?, ?, datetime(\'now\'))')
      .run(id, room.id, file.filename, buf.length);

    // Notify senders via WebSocket
    const inboxItem = { id, ts: Date.now(), bytes: buf.length, viewed: false };
    for (const d of senders) {
      if (d.roomId === room.id && d.socket.readyState === 1) {
        d.socket.send(JSON.stringify({ type: 'new', item: inboxItem }));
      }
    }

    return reply.send({ id });
  });

  // ── Inbox (sender) ─────────────────────────────────────────────
  app.get('/api/inbox', async (req, reply) => {
    const token = bearer(req);
    if (!token) return reply.code(401).send({ error: 'Missing token' });
    const room = db.prepare('SELECT id FROM rooms WHERE token = ?').get(token) as { id: string } | undefined;
    if (!room) return reply.code(401).send({ error: 'Invalid token' });

    const rows = db.prepare(
      `SELECT m.id, m.created_at, m.size,
              CASE WHEN EXISTS (SELECT 1 FROM message_views WHERE message_id = m.id) THEN 1 ELSE 0 END as viewed
       FROM messages m WHERE m.room_id = ? ORDER BY m.created_at DESC LIMIT 20`
    ).all(room.id) as { id: string; created_at: string; size: number; viewed: number }[];

    return reply.send({
      items: rows.map(r => ({ id: r.id, ts: new Date(r.created_at + 'Z').getTime(), bytes: r.size, viewed: r.viewed === 1 }))
    });
  });

  // ── Download (receiver, public) ────────────────────────────────
  app.get('/api/m/:id', async (req, reply) => {
    const { id } = req.params as { id: string };
    const msg = db.prepare('SELECT id FROM messages WHERE id = ?').get(id);
    if (!msg) return reply.code(404).send({ error: 'Not found' });

    const buf = await storage.load(id);
    if (!buf) return reply.code(404).send({ error: 'File missing' });

    db.prepare('INSERT OR IGNORE INTO message_views (message_id, viewed_at) VALUES (?, datetime(\'now\'))').run(id);

    return reply
      .header('Content-Type', 'image/jpeg')
      .header('Content-Disposition', `inline; filename="${id}.jpg"`)
      .header('Cache-Control', 'no-store')
      .send(buf);
  });

  // ── Delete message (sender) ────────────────────────────────────
  app.delete('/api/m/:id', async (req, reply) => {
    const token = bearer(req);
    if (!token) return reply.code(401).send({ error: 'Missing token' });
    const room = db.prepare('SELECT id FROM rooms WHERE token = ?').get(token) as { id: string } | undefined;
    if (!room) return reply.code(401).send({ error: 'Invalid token' });

    const { id } = req.params as { id: string };
    const msg = db.prepare('SELECT id FROM messages WHERE id = ? AND room_id = ?').get(id, room.id);
    if (!msg) return reply.code(404).send({ error: 'Not found' });

    await storage.remove(id);
    db.prepare('DELETE FROM message_views WHERE message_id = ?').run(id);
    db.prepare('DELETE FROM messages WHERE id = ?').run(id);

    return reply.send({ ok: true });
  });

  // ── Push subscription (sender) ─────────────────────────────────
  app.post('/api/push/subscribe', async (req, reply) => {
    const token = bearer(req);
    if (!token) return reply.code(401).send({ error: 'Missing token' });
    const room = db.prepare('SELECT id FROM rooms WHERE token = ?').get(token) as { id: string } | undefined;
    if (!room) return reply.code(401).send({ error: 'Invalid token' });

    const body = req.body as { endpoint?: string; keys?: { p256dh?: string; auth?: string } };
    if (!body?.endpoint || !body?.keys?.p256dh || !body?.keys?.auth)
      return reply.code(400).send({ error: 'Missing subscription fields' });

    db.prepare(
      'INSERT OR REPLACE INTO push_subscriptions (room_id, endpoint, p256dh, auth) VALUES (?, ?, ?, ?)'
    ).run(room.id, body.endpoint, body.keys.p256dh, body.keys.auth);

    return reply.send({ ok: true });
  });

  // ── Web Push keys ──────────────────────────────────────────────
  app.get('/api/push/vapid', async (_req, reply) => {
    return reply.send({ publicKey: process.env.VAPID_PUBLIC_KEY || '' });
  });

  return app;
}