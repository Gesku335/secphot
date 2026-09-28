import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { Db } from '../src/db.js';

test('cleanup marks expired and exhausted photos', async () => {
  const dir = await mkdtemp(`${tmpdir()}/secure-photo-cleanup-`);
  const db = new Db(`${dir}/db.sqlite`);
  const now = Date.now();
  db.createRoom({id:'room',receiver_pubkey:'pk',receiver_token_hash:'rt',sender_secret_hash:'st',created_at:now});
  db.insertPhoto({id:'expired',room_id:'room',blob_path:'/tmp/expired',size:100,max_views:0,views_used:0,expires_at:now-1,created_at:now-1000,deleted_at:null});
  db.insertPhoto({id:'exhausted',room_id:'room',blob_path:'/tmp/exhausted',size:100,max_views:1,views_used:1,expires_at:now+60000,created_at:now-1000,deleted_at:null});
  assert.equal(db.cleanup(now),2);
  assert.notEqual(db.getPhoto('expired')?.deleted_at,null);
  assert.notEqual(db.getPhoto('exhausted')?.deleted_at,null);
  db.close();
});
