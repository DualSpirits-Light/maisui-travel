import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { DatabaseSync } from "node:sqlite";
import test from "node:test";

import { cleanupShares, shareRequest } from "../src/shares.js";

class D1Statement {
  constructor(database, sql, values = []) { this.database = database; this.sql = sql; this.values = values; }
  bind(...values) { return new D1Statement(this.database, this.sql, values); }
  first() { return this.database.prepare(this.sql).get(...this.values) ?? null; }
  all() { return { success: true, results: this.database.prepare(this.sql).all(...this.values) }; }
  run() {
    const result = this.database.prepare(this.sql).run(...this.values);
    return { success: true, meta: { changes: Number(result.changes) } };
  }
}

class D1Database {
  constructor() {
    this.sqlite = new DatabaseSync(":memory:");
    this.sqlite.exec(readFileSync(new URL("../migrations/0003_shares.sql", import.meta.url), "utf8"));
  }
  prepare(sql) { return new D1Statement(this.sqlite, sql); }
  close() { this.sqlite.close(); }
}

class FakeR2 {
  constructor() { this.objects = new Map(); this.deleted = []; this.failPuts = false; }
  async put(key, body) {
    if (this.failPuts) throw new Error("R2 unavailable");
    const bytes = new Uint8Array(await new Response(body).arrayBuffer());
    this.objects.set(key, bytes);
    return { key, size: bytes.byteLength };
  }
  async get(key) {
    const bytes = this.objects.get(key);
    return bytes ? { body: bytes } : null;
  }
  async delete(key) { this.deleted.push(key); this.objects.delete(key); }
}

function environment() {
  return { DB: new D1Database(), SHARES: new FakeR2(), CODE_PEPPER: "share-test-pepper-with-sufficient-entropy" };
}

function uploadRequest(bytes, { ip = "203.0.113.1", declaredSize = bytes.byteLength, type = "application/zip" } = {}) {
  return new Request("https://license.example/v1/shares", {
    method: "POST",
    headers: { "Content-Type": type, "Content-Length": String(declaredSize), "CF-Connecting-IP": ip },
    body: bytes,
  });
}

async function responseJson(response) { return { status: response.status, body: await response.json() }; }

test("upload validates media type and the 32 MB boundary before touching R2", async (t) => {
  const env = environment();
  t.after(() => env.DB.close());
  for (const request of [
    uploadRequest(new Uint8Array([1]), { declaredSize: 0 }),
    uploadRequest(new Uint8Array([1]), { declaredSize: 32 * 1024 * 1024 + 1 }),
    uploadRequest(new Uint8Array([1]), { type: "application/octet-stream" }),
  ]) {
    const result = await responseJson(await shareRequest(request, env));
    assert.equal(result.status, 413);
  }
  assert.equal(env.SHARES.objects.size, 0);
  assert.equal(env.DB.sqlite.prepare("SELECT COUNT(*) AS count FROM share_limits").get().count, 0);
});

test("upload quotas enforce 5 per IP, 100 daily, and 5000 monthly writes", async (t) => {
  const env = environment();
  t.after(() => env.DB.close());
  for (let index = 0; index < 5; index += 1) {
    assert.equal((await shareRequest(uploadRequest(new Uint8Array([index]), { ip: "198.51.100.9" }), env)).status, 201);
  }
  assert.equal((await shareRequest(uploadRequest(new Uint8Array([9]), { ip: "198.51.100.9" }), env)).status, 429);

  const day = Math.floor(Date.now() / 1000 / 86400);
  env.DB.sqlite.prepare("UPDATE share_limits SET count=100 WHERE bucket=?").run(`uploads:${day}`);
  assert.equal((await shareRequest(uploadRequest(new Uint8Array([7]), { ip: "198.51.100.10" }), env)).status, 429);
  env.DB.sqlite.prepare("UPDATE share_limits SET count=99 WHERE bucket=?").run(`uploads:${day}`);
  const month = new Date().toISOString().slice(0, 7);
  env.DB.sqlite.prepare("UPDATE share_limits SET count=5000 WHERE bucket=?").run(`writes:${month}`);
  assert.equal((await shareRequest(uploadRequest(new Uint8Array([8]), { ip: "198.51.100.11" }), env)).status, 429);
});

test("storage reservation is atomic across concurrent uploads at the 2 GB cap", async (t) => {
  const env = environment();
  t.after(() => env.DB.close());
  const max = 32 * 1024 * 1024;
  const insert = env.DB.sqlite.prepare("INSERT INTO shares(id,size,created_at,expires_at,ready) VALUES (?,?,?,?,1)");
  const now = Math.floor(Date.now() / 1000);
  for (let index = 0; index < 63; index += 1) insert.run(index.toString(16).padStart(32, "0"), max, now, now + 3600);
  env.SHARES.put = async (key) => { env.SHARES.objects.set(key, new Uint8Array([1])); return { key, size: max }; };
  const results = await Promise.all([
    shareRequest(uploadRequest(new Uint8Array([1]), { declaredSize: max, ip: "192.0.2.1" }), env),
    shareRequest(uploadRequest(new Uint8Array([2]), { declaredSize: max, ip: "192.0.2.2" }), env),
  ]);
  assert.deepEqual(results.map((response) => response.status).sort(), [201, 507]);
  assert.equal(env.DB.sqlite.prepare("SELECT SUM(size) AS size FROM shares").get().size, 2 * 1024 * 1024 * 1024);
});

test("failed R2 uploads remove both the reservation and any partial object", async (t) => {
  const env = environment();
  t.after(() => env.DB.close());
  env.SHARES.put = async (key) => { env.SHARES.objects.set(key, new Uint8Array([1])); return { key, size: 999 }; };
  const response = await shareRequest(uploadRequest(new Uint8Array([1, 2, 3])), env);
  assert.equal(response.status, 503);
  assert.equal(env.DB.sqlite.prepare("SELECT COUNT(*) AS count FROM shares").get().count, 0);
  assert.equal(env.SHARES.objects.size, 0);
  assert.equal(env.SHARES.deleted.length, 1);
});

test("failed R2 deletion retains a tracked reservation until expiry cleanup can retry", async (t) => {
  const env = environment();
  t.after(() => env.DB.close());
  env.SHARES.put = async () => { throw new Error("R2 put failed"); };
  let deleteAttempts = 0;
  env.SHARES.delete = async (key) => {
    deleteAttempts += 1;
    if (deleteAttempts === 1) throw new Error("R2 delete failed");
    env.SHARES.objects.delete(key);
  };
  const response = await shareRequest(uploadRequest(new Uint8Array([1, 2, 3])), env);
  assert.equal(response.status, 503);
  const reservation = env.DB.sqlite.prepare("SELECT ready,expires_at AS expiresAt FROM shares").get();
  // Keep failed deletions tracked so a possibly-created R2 object never becomes an untracked billed orphan.
  assert.equal(reservation.ready, 0);
  await cleanupShares(env, reservation.expiresAt);
  assert.equal(deleteAttempts, 2);
  assert.equal(env.DB.sqlite.prepare("SELECT COUNT(*) AS count FROM shares").get().count, 0);
});

test("expired GET is denied and deferred cleanup deletes object and metadata", async (t) => {
  const env = environment();
  t.after(() => env.DB.close());
  const id = "a".repeat(32), now = Math.floor(Date.now() / 1000);
  env.DB.sqlite.prepare("INSERT INTO shares VALUES (?,?,?,?,1)").run(id, 3, now - 100, now - 1);
  env.SHARES.objects.set(id, new Uint8Array([1, 2, 3]));
  const deferred = [];
  const response = await shareRequest(new Request(`https://license.example/v1/shares/MS31-${id}`), env, { waitUntil(promise) { deferred.push(promise); } });
  assert.equal(response.status, 410);
  assert.equal(deferred.length, 1);
  await Promise.all(deferred);
  assert.equal(env.DB.sqlite.prepare("SELECT COUNT(*) AS count FROM shares").get().count, 0);
  assert.equal(env.SHARES.objects.has(id), false);
  assert.deepEqual(env.SHARES.deleted, [id]);
});

test("scheduled cleanup removes every expired metadata row and object in bounded batches", async (t) => {
  const env = environment();
  t.after(() => env.DB.close());
  const now = Math.floor(Date.now() / 1000);
  for (let index = 0; index < 3; index += 1) {
    const id = (index + 10).toString(16).padStart(32, "0");
    env.DB.sqlite.prepare("INSERT INTO shares VALUES (?,?,?,?,1)").run(id, 1, now - 10, now - 1);
    env.SHARES.objects.set(id, new Uint8Array([index]));
  }
  await cleanupShares(env, now);
  assert.equal(env.DB.sqlite.prepare("SELECT COUNT(*) AS count FROM shares").get().count, 0);
  assert.equal(env.SHARES.objects.size, 0);
});

test("GET counts successful, missing, and expired attempts against the 100000 monthly read cap", async (t) => {
  const env = environment();
  t.after(() => env.DB.close());
  const now = Math.floor(Date.now() / 1000), month = new Date(now * 1000).toISOString().slice(0, 7);
  const id = "b".repeat(32);
  env.DB.sqlite.prepare("INSERT INTO shares VALUES (?,?,?,?,1)").run(id, 2, now, now + 3600);
  env.SHARES.objects.set(id, new Uint8Array([4, 5]));
  env.DB.sqlite.prepare("INSERT INTO share_limits VALUES (?,?,?)").run(`reads:${month}`, 99998, now + 3600);
  const success = await shareRequest(new Request(`https://license.example/v1/shares/${id}`), env);
  assert.equal(success.status, 200);
  assert.deepEqual([...new Uint8Array(await success.arrayBuffer())], [4, 5]);
  const missing = await shareRequest(new Request(`https://license.example/v1/shares/${"c".repeat(32)}`), env);
  assert.equal(missing.status, 410);
  const limited = await shareRequest(new Request(`https://license.example/v1/shares/${id}`), env);
  assert.equal(limited.status, 429);
  assert.equal(env.DB.sqlite.prepare("SELECT count FROM share_limits WHERE bucket=?").get(`reads:${month}`).count, 100000);
});
