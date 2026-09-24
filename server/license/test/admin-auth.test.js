import test from 'node:test';
import assert from 'node:assert/strict';
import { DatabaseSync } from 'node:sqlite';
import { readFileSync, readdirSync } from 'node:fs';
import worker from '../src/index.js';

function environment(t) {
  const sqlite = new DatabaseSync(':memory:');
  for (const file of readdirSync(new URL('../migrations/', import.meta.url)).filter(f => f.endsWith('.sql')).sort()) sqlite.exec(readFileSync(new URL('../migrations/' + file, import.meta.url), 'utf8'));
  t.after(() => sqlite.close());
  const statement = (sql, values = []) => ({ bind: (...args) => statement(sql, args), first: () => sqlite.prepare(sql).get(...values) ?? null, all: () => ({ results: sqlite.prepare(sql).all(...values) }), run: () => ({ success: true, meta: { changes: Number(sqlite.prepare(sql).run(...values).changes) } }) });
  return { DB: { prepare: statement }, sqlite, CODE_PEPPER: 'test-pepper-longer-than-thirty-two-bytes', SIGNING_PRIVATE_KEY_PKCS8: 'unused', ADMIN_TOKEN: 'old-admin-token', ADMIN_RECOVERY_TOKEN: 'recovery-credential-at-least-32-bytes-long' };
}
async function call(env, path, body, cookie, extra = {}) {
  const response = await worker.fetch(new Request('https://license.example' + path, { method: body === undefined ? 'GET' : 'POST', headers: { ...(body === undefined ? {} : { 'Content-Type': 'application/json', Origin: 'https://license.example' }), ...(cookie ? { Cookie: cookie } : {}), ...extra }, body: body === undefined ? undefined : JSON.stringify(body) }), env);
  return { status: response.status, data: await response.json(), cookie: response.headers.get('set-cookie')?.split(';')[0], headers: response.headers };
}
const password = 'correct horse battery staple';
const setup = env => call(env, '/admin/auth/recover', { password }, undefined, { Authorization: 'Bearer ' + env.ADMIN_RECOVERY_TOKEN });
const login = (env, value = password) => call(env, '/admin/auth/login', { password: value });

test('recovery initializes salted account without old token and password login protects administration', async t => {
  const env = environment(t); delete env.ADMIN_TOKEN;
  assert.equal((await call(env, '/admin/auth/recover', { password })).status, 401);
  assert.equal((await setup(env)).status, 200);
  const account = env.sqlite.prepare('SELECT * FROM admin_accounts').get();
  assert.ok(account.password_salt); assert.ok(account.password_hash); assert.ok(!JSON.stringify(account).includes(password));
  assert.equal((await login(env, 'incorrect password')).status, 401);
  const session = await login(env); assert.equal(session.status, 200);
  assert.match(session.headers.get('set-cookie'), /HttpOnly/); assert.match(session.headers.get('set-cookie'), /Secure/); assert.match(session.headers.get('set-cookie'), /SameSite=Strict/);
  assert.equal((await call(env, '/admin/licenses', undefined, session.cookie)).status, 200);
  assert.equal((await call(env, '/admin/licenses')).status, 401);
  assert.equal((await call(env, '/admin/licenses', undefined, undefined, { Authorization: 'Bearer old-admin-token' })).status, 401);
  assert.ok(!JSON.stringify(env.sqlite.prepare('SELECT * FROM admin_sessions').all()).includes(session.cookie.split('=')[1]));
});

test('password change revokes every session and rejects old password and expired sessions', async t => {
  const env = environment(t); await setup(env);
  const a = await login(env), b = await login(env);
  assert.equal((await call(env, '/admin/auth/password', { currentPassword: 'wrong', password: 'new password long enough' }, a.cookie)).status, 401);
  assert.equal((await call(env, '/admin/auth/password', { currentPassword: password, password: 'new password long enough' }, a.cookie)).status, 200);
  assert.equal((await call(env, '/admin/licenses', undefined, a.cookie)).status, 401);
  assert.equal((await call(env, '/admin/licenses', undefined, b.cookie)).status, 401);
  assert.equal((await login(env)).status, 401);
  const next = await login(env, 'new password long enough'); assert.equal(next.status, 200);
  env.sqlite.prepare('UPDATE admin_sessions SET expires_at = 0').run();
  assert.equal((await call(env, '/admin/licenses', undefined, next.cookie)).status, 401);
});

test('logout revokes cookie and cross-origin writes are forbidden', async t => {
  const env = environment(t); await setup(env); const session = await login(env);
  assert.equal((await call(env, '/admin/licenses', { subject: 'CSRF' }, session.cookie, { Origin: 'https://attacker.example' })).status, 403);
  assert.equal((await call(env, '/admin/auth/logout', {}, session.cookie)).status, 200);
  assert.equal((await call(env, '/admin/licenses', undefined, session.cookie)).status, 401);
});

test('rotated independent recovery credential revokes sessions and cannot be replayed', async t => {
  const env = environment(t); await setup(env); const session = await login(env);
  assert.equal((await setup(env)).status, 401);
  env.ADMIN_RECOVERY_TOKEN = 'second-cloudflare-recovery-secret-at-least-32-bytes';
  assert.equal((await call(env, '/admin/auth/recover', { password: 'recovered password long enough' }, undefined, { Authorization: 'Bearer ' + env.ADMIN_RECOVERY_TOKEN })).status, 200);
  assert.equal((await call(env, '/admin/licenses', undefined, session.cookie)).status, 401);
  assert.equal((await login(env)).status, 401);
  assert.equal((await login(env, 'recovered password long enough')).status, 200);
});

test('authentication attempts are limited and passwords enforce length', async t => {
  const env = environment(t);
  assert.equal((await call(env, '/admin/auth/recover', { password: 'short' }, undefined, { Authorization: 'Bearer ' + env.ADMIN_RECOVERY_TOKEN })).status, 400);
  await setup(env);
  for (let i = 0; i < 5; i++) assert.equal((await login(env, 'bad-password')).status, 401);
  const limited = await login(env); assert.equal(limited.status, 429); assert.equal(limited.headers.get('retry-after'), '60');
});

test('initialization preserves legacy records and concurrent recovery consumes the credential once', async t => {
  const env = environment(t);
  env.sqlite.prepare("INSERT INTO licenses (id, code_hmac, subject, max_devices, lease_days, created_at) VALUES ('existing-license', 'existing-hmac', 'existing user', 1, 7, 1)").run();
  const results = await Promise.all([setup(env), setup(env)]);
  assert.deepEqual(results.map(r => r.status).sort(), [200, 401]);
  assert.equal(env.sqlite.prepare('SELECT subject FROM licenses').get().subject, 'existing user');
  const before = env.sqlite.prepare('SELECT password_salt, password_hash FROM admin_accounts').get();
  env.ADMIN_RECOVERY_TOKEN = 'brand-new-second-recovery-credential-long-enough';
  assert.equal((await setup(env)).status, 200);
  const after = env.sqlite.prepare('SELECT password_salt, password_hash FROM admin_accounts').get();
  assert.notEqual(before.password_salt, after.password_salt);
  assert.notEqual(before.password_hash, after.password_hash);
});

test('password authentication rejects cross-origin and missing-origin login requests', async t => {
  const env = environment(t); await setup(env);
  assert.equal((await call(env, '/admin/auth/login', { password }, undefined, { Origin: 'https://foreign.example' })).status, 403);
  assert.equal((await call(env, '/admin/auth/login', { password }, undefined, { Origin: '' })).status, 403);
});
