import { constantTimeSecretEqual, deriveAdminPassword, randomOpaque, sha256 } from './crypto.js';

const COOKIE = '__Host-lvxu_admin';
const SESSION_SECONDS = 8 * 3600;
const cookie = (token, seconds = SESSION_SECONDS) => `${COOKIE}=${token}; Path=/; Max-Age=${seconds}; HttpOnly; Secure; SameSite=Strict`;
const bearer = request => (request.headers.get('authorization') || '').replace(/^Bearer /u, '');
const sessionToken = request => (request.headers.get('cookie') || '').split(';').map(v => v.trim()).find(v => v.startsWith(COOKIE + '='))?.slice(COOKIE.length + 1) || '';

export function sameOrigin(request, HttpError) {
  if (!['GET', 'HEAD'].includes(request.method) && request.headers.get('origin') !== new URL(request.url).origin) throw new HttpError(403, 'ADMIN_ORIGIN_INVALID', 'Same-origin request required');
}

export async function requireAdminSession(request, env, HttpError) {
  const token = sessionToken(request);
  if (token) {
    sameOrigin(request, HttpError);
    const row = await env.DB.prepare(`SELECT s.token_hash, a.* FROM admin_sessions s JOIN admin_accounts a ON a.id = s.account_id
      WHERE s.token_hash = ? AND s.expires_at > ? AND s.session_version = a.session_version`).bind(await sha256(token), Math.floor(Date.now() / 1000)).first();
    if (row) return row;
  } else {
    // Transitional automation compatibility ends when the password account exists.
    const account = await env.DB.prepare('SELECT id FROM admin_accounts WHERE id = 1').first();
    if (!account && env.ADMIN_TOKEN && await constantTimeSecretEqual(bearer(request), env.ADMIN_TOKEN)) return { legacy: true };
  }
  throw new HttpError(401, 'ADMIN_UNAUTHORIZED', 'Unauthorized');
}

export async function adminAuthRequest(request, env, helpers) {
  const { HttpError, readJson, json, rateLimit } = helpers;
  const path = new URL(request.url).pathname;
  if (!path.startsWith('/admin/auth/')) return null;
  const fail = () => { throw new HttpError(401, 'ADMIN_UNAUTHORIZED', 'Unauthorized'); };
  const validate = password => {
    if (typeof password !== 'string' || password.length < 12 || password.length > 128) throw new HttpError(400, 'INVALID_PASSWORD', 'Password must contain 12 to 128 characters');
  };
  if (request.method === 'POST' && (path === '/admin/auth/login' || path === '/admin/auth/recover')) {
    sameOrigin(request, HttpError);
    await rateLimit(request, env, path === '/admin/auth/login' ? 'admin-login' : 'admin-recovery', 5);
    // A global ceiling prevents distributed requests from bypassing per-address limits.
    await rateLimit(new Request(request.url), env, 'admin-auth-global', 100);
    const body = await readJson(request);
    const account = await env.DB.prepare('SELECT * FROM admin_accounts WHERE id = 1').first();
    if (path === '/admin/auth/recover') {
      if (typeof env.ADMIN_RECOVERY_TOKEN !== 'string' || env.ADMIN_RECOVERY_TOKEN.length < 32 || !(await constantTimeSecretEqual(bearer(request), env.ADMIN_RECOVERY_TOKEN))) fail();
      const recoveryHash = await sha256(env.ADMIN_RECOVERY_TOKEN);
      if (account?.used_recovery_hash === recoveryHash) fail();
      validate(body.password);
      const salt = randomOpaque(24), hash = await deriveAdminPassword(body.password, salt, env.CODE_PEPPER);
      const result = await env.DB.prepare(`INSERT INTO admin_accounts (id, password_salt, password_hash, password_iterations, session_version, used_recovery_hash, updated_at)
        VALUES (1, ?, ?, 100000, 1, ?, ?)
        ON CONFLICT(id) DO UPDATE SET password_salt = excluded.password_salt, password_hash = excluded.password_hash,
        password_iterations = excluded.password_iterations, session_version = admin_accounts.session_version + 1,
        used_recovery_hash = excluded.used_recovery_hash, updated_at = excluded.updated_at
        WHERE admin_accounts.used_recovery_hash != excluded.used_recovery_hash`).bind(salt, hash, recoveryHash, Math.floor(Date.now() / 1000)).run();
      if (result.meta?.changes !== 1) fail();
      return json(200, { recovered: true }, { 'Set-Cookie': cookie('', 0) });
    }
    if (typeof body.password !== 'string' || body.password.length > 128) fail();
    const candidate = await deriveAdminPassword(body.password, account?.password_salt || 'uninitialized-account-salt', env.CODE_PEPPER, account?.password_iterations || 100000);
    if (!account || !(await constantTimeSecretEqual(candidate, account.password_hash))) fail();
    const token = randomOpaque(32), now = Math.floor(Date.now() / 1000);
    await env.DB.prepare('DELETE FROM admin_sessions WHERE expires_at <= ? OR session_version != (SELECT session_version FROM admin_accounts WHERE id = 1)').bind(now).run();
    await env.DB.prepare('INSERT INTO admin_sessions (token_hash, account_id, session_version, expires_at) VALUES (?, 1, ?, ?)').bind(await sha256(token), account.session_version, now + SESSION_SECONDS).run();
    return json(200, { authenticated: true, expiresAt: now + SESSION_SECONDS }, { 'Set-Cookie': cookie(token) });
  }
  const session = await requireAdminSession(request, env, HttpError);
  if (session.legacy) fail();
  if (request.method === 'GET' && path === '/admin/auth/session') return json(200, { authenticated: true });
  if (request.method === 'POST' && path === '/admin/auth/logout') {
    await env.DB.prepare('DELETE FROM admin_sessions WHERE token_hash = ?').bind(session.token_hash).run();
    return json(200, { loggedOut: true }, { 'Set-Cookie': cookie('', 0) });
  }
  if (request.method === 'POST' && path === '/admin/auth/password') {
    await rateLimit(request, env, 'admin-password', 5);
    const body = await readJson(request); validate(body.password);
    if (typeof body.currentPassword !== 'string' || body.currentPassword.length > 128) fail();
    if (!(await constantTimeSecretEqual(await deriveAdminPassword(body.currentPassword, session.password_salt, env.CODE_PEPPER, session.password_iterations), session.password_hash))) fail();
    const salt = randomOpaque(24), hash = await deriveAdminPassword(body.password, salt, env.CODE_PEPPER);
    const result = await env.DB.prepare(`UPDATE admin_accounts SET password_salt = ?, password_hash = ?, password_iterations = 100000,
      session_version = session_version + 1, updated_at = ? WHERE id = 1 AND session_version = ?`).bind(salt, hash, Math.floor(Date.now() / 1000), session.session_version).run();
    if (result.meta?.changes !== 1) fail();
    return json(200, { passwordChanged: true }, { 'Set-Cookie': cookie('', 0) });
  }
  throw new HttpError(404, 'NOT_FOUND', 'Not found');
}
