import {
  constantTimeSecretEqual,
  generateLicenseCode,
  hmacSha256,
  normalizeCode,
  randomOpaque,
  sha256,
  signLease,
} from "./crypto.js";
import { adminAsset } from "./admin-page.js";
import {shareRequest,cleanupShares} from "./shares.js";
import {updateManifestResponse} from "./update-manifest.js";

const PRODUCT = "maisui-travel";
const MAX_BODY_BYTES = 4096;
const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/iu;
const ID_RE = /^[A-Za-z0-9_-]{8,128}$/u;

function json(status, body, extraHeaders = {}) {
  return Response.json(body, {
    status,
    headers: { "Cache-Control": "no-store", ...extraHeaders },
  });
}

function error(status, code, message) {
  return json(status, { error: { code, message } });
}

function integerSetting(env, name, fallback, minimum, maximum) {
  const value = Number.parseInt(env[name] ?? "", 10);
  return Number.isSafeInteger(value) && value >= minimum && value <= maximum ? value : fallback;
}

export async function readJson(request) {
  if (!(request.headers.get("content-type") || "").toLowerCase().startsWith("application/json")) {
    throw new HttpError(415, "UNSUPPORTED_MEDIA_TYPE", "Content-Type must be application/json");
  }
  const declared = Number(request.headers.get("content-length"));
  if (Number.isFinite(declared) && declared > MAX_BODY_BYTES) {
    throw new HttpError(413, "BODY_TOO_LARGE", "Request body is too large");
  }
  if (!request.body) throw new HttpError(400, "INVALID_REQUEST", "Request body must be a JSON object");
  const reader = request.body.getReader();
  const chunks = [];
  let size = 0;
  while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    size += value.byteLength;
    if (size > MAX_BODY_BYTES) {
      await reader.cancel();
      throw new HttpError(413, "BODY_TOO_LARGE", "Request body is too large");
    }
    chunks.push(value);
  }
  const bytes = new Uint8Array(size);
  let offset = 0;
  for (const chunk of chunks) {
    bytes.set(chunk, offset);
    offset += chunk.byteLength;
  }
  try {
    const value = JSON.parse(new TextDecoder("utf-8", { fatal: true }).decode(bytes));
    if (!value || typeof value !== "object" || Array.isArray(value)) throw new Error("object required");
    return value;
  } catch {
    throw new HttpError(400, "INVALID_REQUEST", "Request body must be a JSON object");
  }
}

class HttpError extends Error {
  constructor(status, code, message) {
    super(message);
    this.status = status;
    this.code = code;
  }
}

function requireString(body, field, pattern, maxLength = 256) {
  const value = body[field];
  if (typeof value !== "string" || value.length === 0 || value.length > maxLength || (pattern && !pattern.test(value))) {
    throw new HttpError(400, "INVALID_REQUEST", `Invalid ${field}`);
  }
  return value;
}

async function rateLimit(request, env, scope, limit) {
  const address = request.headers.get("CF-Connecting-IP") || "local";
  const bucket = `${scope}:${await hmacSha256(env.CODE_PEPPER, address)}`;
  const minute = Math.floor(Date.now() / 60_000);
  const row = await env.DB.prepare(`
    INSERT INTO rate_limits (bucket, minute, count) VALUES (?, ?, 1)
    ON CONFLICT(bucket, minute) DO UPDATE SET count = count + 1
    RETURNING count
  `).bind(bucket, minute).first();
  if (Math.random() < 0.01) { try { await env.DB.prepare("DELETE FROM rate_limits WHERE minute < ?").bind(minute - 120).run(); } catch {} }
  if (!row || row.count > limit) throw new HttpError(429, "RATE_LIMITED", "Too many requests");
}

function ensureHttps(request) {
  const url = new URL(request.url);
  const local = url.hostname === "localhost" || url.hostname === "127.0.0.1" || url.hostname === "[::1]";
  if (url.protocol !== "https:" && !local) throw new HttpError(400, "HTTPS_REQUIRED", "HTTPS is required");
  return url;
}

async function leaseToken(license, deviceId, env, now, clientVersion) {
  const modern = clientVersion >= 31;
  const offlineSeconds = license.offline_seconds ?? null;
  const requestedExpiry = modern
    ? (offlineSeconds === null ? 253402300799 : Math.min(253402300799, now + (offlineSeconds === 0 ? 60 : offlineSeconds)))
    : now + Math.min(Math.min(license.lease_days, 7) * 86400, offlineSeconds ?? 7 * 86400);
  const expiresAt = license.expires_at == null ? requestedExpiry : Math.min(requestedExpiry, license.expires_at);
  const payload = {
    product: PRODUCT,
    licenseId: license.id,
    deviceId,
    subject: license.subject,
    issuedAt: now,
    expiresAt,
  };
  if (modern) payload.offlineSeconds = offlineSeconds;
  return signLease(payload, env.SIGNING_PRIVATE_KEY_PKCS8, modern ? "MS3" : "MS2");
}

function assertClientPolicy(license, clientVersion) {
  if (clientVersion < 31 && license.offline_seconds === 0) {
    throw new HttpError(403, "CLIENT_UPDATE_REQUIRED", "Update the client to use an online-only license");
  }
}

function assertLicenseUsable(license, now) {
  if (license.revoked_at != null) throw new HttpError(403, "LICENSE_REVOKED", "License is revoked");
  if (license.frozen_at != null) throw new HttpError(403, "LICENSE_FROZEN", "License is frozen");
  if (license.expires_at != null && license.expires_at <= now) throw new HttpError(403, "LICENSE_EXPIRED", "License is expired");
}

async function activate(request, env) {
  await rateLimit(request, env, "activate", integerSetting(env, "PUBLIC_RATE_LIMIT_PER_MINUTE", 30, 1, 1000));
  const body = await readJson(request);
  const code = normalizeCode(body.code);
  const deviceId = requireString(body, "deviceId", UUID_RE, 64);
  const clientVersion = body.clientVersion ?? 0;
  if (!Number.isSafeInteger(clientVersion) || clientVersion < 0) throw new HttpError(400, "INVALID_REQUEST", "Invalid clientVersion");
  if (!code) throw new HttpError(400, "INVALID_CODE", "Invalid license code");
  const codeHmac = await hmacSha256(env.CODE_PEPPER, code);
  const license = await env.DB.prepare("SELECT * FROM licenses WHERE code_hmac = ?").bind(codeHmac).first();
  if (!license) throw new HttpError(400, "INVALID_CODE", "Invalid license code");
  const now = Math.floor(Date.now() / 1000);
  assertLicenseUsable(license, now);
  assertClientPolicy(license, clientVersion);

  const deviceSecret = randomOpaque(32);
  const secretHash = await sha256(deviceSecret);
  const result = await env.DB.prepare(`
    INSERT INTO devices (license_id, device_id, secret_hash, created_at, updated_at)
    SELECT l.id, ?, ?, ?, ? FROM licenses l
    WHERE l.id = ? AND l.revoked_at IS NULL AND l.frozen_at IS NULL AND (l.expires_at IS NULL OR l.expires_at > ?)
      AND (
        EXISTS (SELECT 1 FROM devices d WHERE d.license_id = l.id AND d.device_id = ?)
        OR (SELECT COUNT(*) FROM devices d WHERE d.license_id = l.id) < l.max_devices
      )
    ON CONFLICT(license_id, device_id) DO UPDATE SET secret_hash = excluded.secret_hash, updated_at = excluded.updated_at
  `).bind(deviceId, secretHash, now, now, license.id, now, deviceId).run();
  if (!result.success || result.meta?.changes !== 1) throw new HttpError(409, "DEVICE_LIMIT_REACHED", "Device limit reached");
  return json(200, { token: await leaseToken(license, deviceId, env, now, clientVersion), licenseId: license.id, deviceSecret });
}

async function refresh(request, env) {
  await rateLimit(request, env, "refresh", integerSetting(env, "PUBLIC_RATE_LIMIT_PER_MINUTE", 30, 1, 1000));
  const body = await readJson(request);
  const licenseId = requireString(body, "licenseId", ID_RE, 128);
  const deviceId = requireString(body, "deviceId", UUID_RE, 64);
  const deviceSecret = requireString(body, "deviceSecret", /^[A-Za-z0-9_-]{40,128}$/u, 128);
  const clientVersion = body.clientVersion ?? 0;
  if (!Number.isSafeInteger(clientVersion) || clientVersion < 0) throw new HttpError(400, "INVALID_REQUEST", "Invalid clientVersion");
  const [license, device] = await Promise.all([
    env.DB.prepare("SELECT * FROM licenses WHERE id = ?").bind(licenseId).first(),
    env.DB.prepare("SELECT secret_hash FROM devices WHERE license_id = ? AND device_id = ?").bind(licenseId, deviceId).first(),
  ]);
  if (!license || !device || !(await constantTimeSecretEqual(await sha256(deviceSecret), device.secret_hash))) {
    throw new HttpError(401, "INVALID_CREDENTIAL", "Invalid device credential");
  }
  const now = Math.floor(Date.now() / 1000);
  assertLicenseUsable(license, now);
  assertClientPolicy(license, clientVersion);
  const authorized = await env.DB.prepare(`
    SELECT l.id FROM licenses l JOIN devices d ON d.license_id = l.id
    WHERE l.id = ? AND d.device_id = ? AND d.secret_hash = ?
      AND l.revoked_at IS NULL AND l.frozen_at IS NULL AND (l.expires_at IS NULL OR l.expires_at > ?)
  `).bind(licenseId, deviceId, device.secret_hash, now).first();
  if (!authorized) {
    const current = await env.DB.prepare("SELECT * FROM licenses WHERE id = ?").bind(licenseId).first();
    if (current) assertLicenseUsable(current, now);
    throw new HttpError(401, "INVALID_CREDENTIAL", "Invalid device credential");
  }
  return json(200, { token: await leaseToken(license, deviceId, env, now, clientVersion), licenseId });
}

async function requireAdmin(request, env) {
  const authorization = request.headers.get("authorization") || "";
  const token = authorization.startsWith("Bearer ") ? authorization.slice(7) : "";
  if (!env.ADMIN_TOKEN || !(await constantTimeSecretEqual(token, env.ADMIN_TOKEN))) {
    throw new HttpError(401, "ADMIN_UNAUTHORIZED", "Unauthorized");
  }
}

async function createLicense(request, env) {
  const body = await readJson(request);
  const subject = requireString(body, "subject", null, 200).trim();
  if (!subject) throw new HttpError(400, "INVALID_REQUEST", "Invalid subject");
  const now = Math.floor(Date.now() / 1000);
  const expiresAt = body.expiresAt === null || body.expiresAt === undefined ? null : body.expiresAt;
  if (expiresAt !== null && (!Number.isSafeInteger(expiresAt) || expiresAt <= now)) throw new HttpError(400, "INVALID_REQUEST", "Invalid expiresAt");
  const maxDevices = body.maxDevices ?? integerSetting(env, "DEFAULT_MAX_DEVICES", 1, 1, 100);
  const leaseDays = body.leaseDays ?? integerSetting(env, "DEFAULT_LEASE_DAYS", 7, 1, 7);
  const offlineSeconds = body.offlineSeconds === undefined ? leaseDays * 86400 : body.offlineSeconds;
  if (!Number.isSafeInteger(maxDevices) || maxDevices < 1 || maxDevices > 100) throw new HttpError(400, "INVALID_REQUEST", "Invalid maxDevices");
  if (!Number.isSafeInteger(leaseDays) || leaseDays < 1 || leaseDays > 7) throw new HttpError(400, "INVALID_REQUEST", "Invalid leaseDays");
  if (offlineSeconds !== null && (!Number.isSafeInteger(offlineSeconds) || offlineSeconds < 0)) throw new HttpError(400, "INVALID_REQUEST", "Invalid offlineSeconds");
  const id = crypto.randomUUID();
  const code = generateLicenseCode();
  await env.DB.prepare(`
    INSERT INTO licenses (id, code_hmac, subject, expires_at, max_devices, lease_days, offline_seconds, revoked_at, created_at)
    VALUES (?, ?, ?, ?, ?, ?, ?, NULL, ?)
  `).bind(id, await hmacSha256(env.CODE_PEPPER, normalizeCode(code)), subject, expiresAt, maxDevices, leaseDays, offlineSeconds, now).run();
  return json(201, { id, code, subject, expiresAt, maxDevices, leaseDays, offlineSeconds, createdAt: now });
}

async function listLicenses(env, archived = false) {
  const result = await env.DB.prepare(`
    SELECT l.id, l.subject, l.expires_at AS expiresAt, l.max_devices AS maxDevices,
      l.lease_days AS leaseDays, l.offline_seconds AS offlineSeconds, l.revoked_at AS revokedAt,
      l.archived_at AS archivedAt, l.frozen_at AS frozenAt, l.frozen_remaining_seconds AS frozenRemainingSeconds,
      l.created_at AS createdAt,
      COUNT(d.device_id) AS deviceCount
    FROM licenses l LEFT JOIN devices d ON d.license_id = l.id
    WHERE ${archived ? "l.archived_at IS NOT NULL" : "l.archived_at IS NULL"}
    GROUP BY l.id ORDER BY l.created_at DESC LIMIT 500
  `).all();
  return json(200, { licenses: result.results || [] });
}

function validateLicenseId(id) {
  if (!ID_RE.test(id)) throw new HttpError(400, "INVALID_REQUEST", "Invalid license id");
}

async function updateLicense(id, request, env) {
  validateLicenseId(id);
  const body = await readJson(request);
  const current = await env.DB.prepare("SELECT * FROM licenses WHERE id = ?").bind(id).first();
  if (!current) throw new HttpError(404, "LICENSE_NOT_FOUND", "License not found");
  const expiresAt = Object.hasOwn(body, "expiresAt") ? body.expiresAt : current.expires_at;
  const offlineSeconds = Object.hasOwn(body, "offlineSeconds") ? body.offlineSeconds : current.offline_seconds;
  if (expiresAt !== null && (!Number.isSafeInteger(expiresAt) || expiresAt < 0)) throw new HttpError(400, "INVALID_REQUEST", "Invalid expiresAt");
  if (offlineSeconds !== null && (!Number.isSafeInteger(offlineSeconds) || offlineSeconds < 0)) throw new HttpError(400, "INVALID_REQUEST", "Invalid offlineSeconds");
  let frozenRemaining = current.frozen_remaining_seconds;
  if (current.frozen_at != null && Object.hasOwn(body, "expiresAt")) {
    frozenRemaining = expiresAt === null ? null : Math.max(0, expiresAt - Math.floor(Date.now() / 1000));
  }
  await env.DB.prepare("UPDATE licenses SET expires_at = ?, offline_seconds = ?, frozen_remaining_seconds = ? WHERE id = ?")
    .bind(expiresAt, offlineSeconds, frozenRemaining, id).run();
  return json(200, { id, expiresAt, offlineSeconds, frozenAt: current.frozen_at, frozenRemainingSeconds: frozenRemaining });
}

async function freezeLicense(id, env) {
  validateLicenseId(id);
  const current = await env.DB.prepare("SELECT * FROM licenses WHERE id = ?").bind(id).first();
  if (!current) throw new HttpError(404, "LICENSE_NOT_FOUND", "License not found");
  if (current.frozen_at != null) return json(200, { id, frozen: true, frozenAt: current.frozen_at });
  const now = Math.floor(Date.now() / 1000);
  const remaining = current.expires_at == null ? null : Math.max(0, current.expires_at - now);
  await env.DB.prepare("UPDATE licenses SET frozen_at = ?, frozen_remaining_seconds = ? WHERE id = ?")
    .bind(now, remaining, id).run();
  return json(200, { id, frozen: true, frozenAt: now, frozenRemainingSeconds: remaining });
}

async function unfreezeLicense(id, env) {
  validateLicenseId(id);
  const current = await env.DB.prepare("SELECT * FROM licenses WHERE id = ?").bind(id).first();
  if (!current) throw new HttpError(404, "LICENSE_NOT_FOUND", "License not found");
  if (current.frozen_at == null) return json(200, { id, frozen: false, expiresAt: current.expires_at });
  const expiresAt = current.frozen_remaining_seconds == null ? null : Math.floor(Date.now() / 1000) + current.frozen_remaining_seconds;
  await env.DB.prepare("UPDATE licenses SET expires_at = ?, frozen_at = NULL, frozen_remaining_seconds = NULL WHERE id = ?")
    .bind(expiresAt, id).run();
  return json(200, { id, frozen: false, expiresAt });
}

async function archiveLicense(id, restore, env) {
  validateLicenseId(id);
  const current = await env.DB.prepare("SELECT revoked_at, archived_at FROM licenses WHERE id = ?").bind(id).first();
  if (!current) throw new HttpError(404, "LICENSE_NOT_FOUND", "License not found");
  if (!restore && current.revoked_at == null) throw new HttpError(409, "LICENSE_NOT_REVOKED", "Only revoked licenses can be archived");
  const archivedAt = restore ? null : (current.archived_at ?? Math.floor(Date.now() / 1000));
  await env.DB.prepare("UPDATE licenses SET archived_at = ? WHERE id = ?").bind(archivedAt, id).run();
  return json(200, { id, archived: !restore, archivedAt });
}

async function deleteLicense(id, env) {
  validateLicenseId(id);
  const current = await env.DB.prepare("SELECT revoked_at FROM licenses WHERE id = ?").bind(id).first();
  if (!current) throw new HttpError(404, "LICENSE_NOT_FOUND", "License not found");
  if (current.revoked_at == null) throw new HttpError(409, "LICENSE_NOT_REVOKED", "Only revoked licenses can be deleted");
  await env.DB.prepare("DELETE FROM licenses WHERE id = ?").bind(id).run();
  return json(200, { id, deleted: true });
}

async function listDevices(licenseId, env) {
  if (!ID_RE.test(licenseId)) throw new HttpError(400, "INVALID_REQUEST", "Invalid license id");
  const license = await env.DB.prepare("SELECT id FROM licenses WHERE id = ?").bind(licenseId).first();
  if (!license) throw new HttpError(404, "LICENSE_NOT_FOUND", "License not found");
  const result = await env.DB.prepare(`
    SELECT device_id AS deviceId, created_at AS createdAt, updated_at AS updatedAt
    FROM devices WHERE license_id = ? ORDER BY created_at ASC
  `).bind(licenseId).all();
  return json(200, { licenseId, devices: result.results || [] });
}

async function revokeLicense(id, env) {
  if (!ID_RE.test(id)) throw new HttpError(400, "INVALID_REQUEST", "Invalid license id");
  const result = await env.DB.prepare("UPDATE licenses SET revoked_at = COALESCE(revoked_at, ?) WHERE id = ?").bind(Math.floor(Date.now() / 1000), id).run();
  if (!result.success || result.meta?.changes !== 1) throw new HttpError(404, "LICENSE_NOT_FOUND", "License not found");
  return json(200, { id, revoked: true });
}

async function unbindDevice(licenseId, deviceId, env) {
  if (!ID_RE.test(licenseId) || !UUID_RE.test(deviceId)) throw new HttpError(400, "INVALID_REQUEST", "Invalid path parameters");
  const result = await env.DB.prepare("DELETE FROM devices WHERE license_id = ? AND device_id = ?").bind(licenseId, deviceId).run();
  if (!result.success || result.meta?.changes !== 1) throw new HttpError(404, "DEVICE_NOT_FOUND", "Device not found");
  return json(200, { licenseId, deviceId, unbound: true });
}

export async function handleRequest(request, env, ctx) {
  const url = ensureHttps(request);
  if(request.method === "GET" && url.pathname === "/updates/latest.json") return updateManifestResponse(env);
  const share = await shareRequest(request,env,ctx); if(share) return share;
  if (request.method === "GET") {
    const asset = adminAsset(url.pathname);
    if (asset) return asset;
  }
  if (request.method === "POST" && url.pathname === "/v1/activate") return activate(request, env);
  if (request.method === "POST" && url.pathname === "/v1/refresh") return refresh(request, env);
  if (url.pathname.startsWith("/admin/")) {
    await rateLimit(request, env, "admin", integerSetting(env, "ADMIN_RATE_LIMIT_PER_MINUTE", 60, 1, 1000));
    await requireAdmin(request, env);
    if (request.method === "POST" && url.pathname === "/admin/licenses") return createLicense(request, env);
    if (request.method === "GET" && url.pathname === "/admin/licenses") return listLicenses(env, url.searchParams.get("archived") === "1");
    const license = url.pathname.match(/^\/admin\/licenses\/([^/]+)$/u);
    if (request.method === "PATCH" && license) return updateLicense(decodeURIComponent(license[1]), request, env);
    if (request.method === "DELETE" && license) return deleteLicense(decodeURIComponent(license[1]), env);
    const devices = url.pathname.match(/^\/admin\/licenses\/([^/]+)\/devices$/u);
    if (request.method === "GET" && devices) return listDevices(decodeURIComponent(devices[1]), env);
    const revoke = url.pathname.match(/^\/admin\/licenses\/([^/]+)\/revoke$/u);
    if (request.method === "POST" && revoke) return revokeLicense(decodeURIComponent(revoke[1]), env);
    const lifecycle = url.pathname.match(/^\/admin\/licenses\/([^/]+)\/(freeze|unfreeze|archive|restore)$/u);
    if (request.method === "POST" && lifecycle) {
      const id = decodeURIComponent(lifecycle[1]);
      if (lifecycle[2] === "freeze") return freezeLicense(id, env);
      if (lifecycle[2] === "unfreeze") return unfreezeLicense(id, env);
      return archiveLicense(id, lifecycle[2] === "restore", env);
    }
    const unbind = url.pathname.match(/^\/admin\/licenses\/([^/]+)\/devices\/([^/]+)\/unbind$/u);
    if (request.method === "POST" && unbind) return unbindDevice(decodeURIComponent(unbind[1]), decodeURIComponent(unbind[2]), env);
  }
  throw new HttpError(404, "NOT_FOUND", "Not found");
}

export default {
  async scheduled(controller,env,ctx) {ctx.waitUntil(cleanupShares(env));},
  async fetch(request, env, ctx) {
    try {
      if (!env.DB || !env.ADMIN_TOKEN || !env.CODE_PEPPER || !env.SIGNING_PRIVATE_KEY_PKCS8) return error(503, "SERVER_MISCONFIGURED", "Service unavailable");
      return await handleRequest(request, env, ctx);
    } catch (caught) {
      if (caught instanceof HttpError) {
        const headers = caught.status === 429 ? { "Retry-After": "60" } : {};
        return json(caught.status, { error: { code: caught.code, message: caught.message } }, headers);
      }
      return error(500, "INTERNAL_ERROR", "Internal server error");
    }
  },
};
