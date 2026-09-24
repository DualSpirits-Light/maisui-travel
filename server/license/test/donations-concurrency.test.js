import test from 'node:test';
import assert from 'node:assert/strict';
import { DatabaseSync } from 'node:sqlite';
import { readFileSync } from 'node:fs';
import { createDonationHandlers } from '../src/donations.js';

test('unpublishing remains private when an unrelated edit races it', async t => {
  const sqlite = new DatabaseSync(':memory:');
  t.after(() => sqlite.close());
  sqlite.exec(readFileSync(new URL('../migrations/0005_donations.sql', import.meta.url), 'utf8'));
  sqlite.prepare(`INSERT INTO donations VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`).run(
    'donation-1', '爱发电', '甲', 1000, 'CNY', '2026-09-20', 'initial', 1, 1, 1);
  let readers = 0, release;
  const bothRead = new Promise(resolve => { release = resolve; });
  const DB = { prepare(sql) { return { bind(...args) { return {
    async first() {
      const row = sqlite.prepare(sql).get(...args) ?? null;
      if (sql.includes('FROM donations') && ++readers === 2) release();
      if (sql.includes('FROM donations')) await bothRead;
      return row;
    },
    async run() { const result = sqlite.prepare(sql).run(...args); return { success: true, meta: { changes: Number(result.changes) } }; },
    async all() { return { results: sqlite.prepare(sql).all(...args) }; },
  }; } }; } };
  class HttpError extends Error { constructor(status, code, message) { super(message); this.status = status; this.code = code; } }
  const handlers = createDonationHandlers({ readJson: request => request.json(), HttpError,
    json: (status, body) => ({ status, body }) });
  const env = { DB };
  const request = body => new Request('https://license.example/admin/donations/donation-1',
    { method: 'PATCH', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) });
  await Promise.all([
    handlers.update('donation-1', request({ isPublic: false }), env),
    handlers.update('donation-1', request({ message: 'changed note' }), env),
  ]);
  const row = sqlite.prepare('SELECT is_public, message FROM donations WHERE id = ?').get('donation-1');
  assert.equal(row.is_public, 0);
  assert.equal(row.message, 'changed note');
});
