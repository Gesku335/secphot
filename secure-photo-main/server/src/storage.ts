import { mkdir, writeFile, readFile, unlink, readdir, stat } from 'node:fs/promises';
import { join } from 'node:path';

export class Storage {
  constructor(private dir: string) {}

  async init(): Promise<void> {
    await mkdir(this.dir, { recursive: true });
  }

  path(id: string): string {
    return join(this.dir, `${id}.jpg`);
  }

  async save(id: string, data: Buffer): Promise<void> {
    await writeFile(this.path(id), data);
  }

  async load(id: string): Promise<Buffer | null> {
    try { return await readFile(this.path(id)); } catch { return null; }
  }

  async remove(id: string): Promise<void> {
    try { await unlink(this.path(id)); } catch { /* ignore */ }
  }

  async listFiles(): Promise<string[]> {
    try {
      const files = await readdir(this.dir);
      return files.filter(f => f.endsWith('.jpg'));
    } catch { return []; }
  }

  async cleanup(maxAgeMs: number): Promise<number> {
    const now = Date.now();
    let removed = 0;
    for (const file of await this.listFiles()) {
      try {
        const s = await stat(join(this.dir, file));
        if (now - s.mtimeMs > maxAgeMs) {
          await unlink(join(this.dir, file));
          removed++;
        }
      } catch { /* ignore */ }
    }
    return removed;
  }
}