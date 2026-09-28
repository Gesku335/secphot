import { type Database } from 'better-sqlite3';
import { Storage } from './storage.js';

const MAX_AGE_MS = 60 * 1000; // 60 seconds for downloaded photos

export function startCleanup(db: Database, storage: Storage): () => void {
  const interval = setInterval(async () => {
    try {
      // Remove messages older than 60 seconds
      const cutoff = new Date(Date.now() - MAX_AGE_MS).toISOString().replace('T', ' ').slice(0, 19);
      const stale = db.prepare(
        `SELECT id FROM messages WHERE created_at < datetime(?)`
      ).all(cutoff) as { id: string }[];

      for (const msg of stale) {
        await storage.remove(msg.id);
        db.prepare('DELETE FROM message_views WHERE message_id = ?').run(msg.id);
        db.prepare('DELETE FROM messages WHERE id = ?').run(msg.id);
      }

      // Also clean orphan files
      await storage.cleanup(MAX_AGE_MS);
    } catch (e) {
      console.error('[cleanup]', e);
    }
  }, 10_000);

  return () => clearInterval(interval);
}