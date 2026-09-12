import assert from "node:assert/strict";
import { generateKeyPairSync, verify } from "node:crypto";
import { readFileSync } from "node:fs";
import { DatabaseSync } from "node:sqlite";
import test from "node:test";

import worker from "../src/index.js";

class D1Statement {
  constructor(database, sql, values = []) {
    this.database = database;
    this.sql = sql;
    this.values = values;
  }

  bind(...values) {
    return new D1Statement(this.database, this.sql, values);
  }

  first() {
    return this.database.prepare(this.sql).get(...this.values) ?? null;
  }

  all() {
    return { success: true, results: this.database.prepare(this.sql).all(...this.values) };
  }

  run() {
    const result = this.database.prepare(this.sql).run(...this.values);
    return { success: true, meta: { changes: Number(result.changes) } };
  }
}

class D1Database {
  constructor() {
    this.sqlite = new DatabaseSync(":memory:");
    this.sqlite.exec(readFileSync(new URL("../migrations/0001_initial.sql", import.meta.url), "utf8"));
    this.sqlite.exec(readFileSync(new URL("../migrations/0002_license_admin.sql", import.meta.url), "utf8"));
  }

  prepare(sql) {
    return new D1Statement(this.sqlite, sql);
  }

  close() {
    this.sqlite.close();
  }
}

const { privateKey, publicKey } = generateKeyPairSync("rsa", { modulusLength: 2048 });
const privateKeyBase64 = privateKey.export({ type: "pkcs8", format: "der" }).toString("base64");

function makeEnvironment(overrides = {}) {
  return {
    DB: new D1Database(),
    ADMIN_TOKEN: "admin-test-token-with-sufficient-entropy",
    CODE_PEPPER: "test-only-pepper-with-at-least-32-bytes",
    SIGNING_PRIVATE_KEY_PKCS8: privateKeyBase64,
    DEFAULT_LEASE_DAYS: "7",
    DEFAULT_MAX_DEVICES: "1",
    PUBLIC_RATE_LIMIT_PER_MINUTE: "100",
    ADMIN_RATE_LIMIT_PER_MINUTE: "100",
    ...overrides,
  };
}

async function call(env, method, path, body, { admin = false, ip = "203.0.113.10" } = {}) {
  const headers = { "CF-Connecting-IP": ip };
  if (body !== undefined) headers["Content-Type"] = "application/json";
  if (admin) headers.Authorization = `Bearer ${env.ADMIN_TOKEN}`;
  const response = await worker.fetch(new Request(`https://license.example${path}`, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  }), env);
  return { response, body: await response.json() };
}

async function createLicense(env, fields = {}) {
  const result = await call(env, "POST", "/admin/licenses", {
    subject: "Integration User",
    expiresAt: null,
    maxDevices: 1,
    leaseDays: 7,
    ...fields,
  }, { admin: true });
  assert.equal(result.response.status, 201);
  return result.body;
}

function decodeAndVerifyToken(token, expectedPrefix = "MS2") {
  const [prefix, encodedPayload, encodedSignature] = token.split(".");
  assert.equal(prefix, expectedPrefix);
  assert.equal(
    verify("sha256", Buffer.from(`${prefix}.${encodedPayload}`, "ascii"), publicKey, Buffer.from(encodedSignature, "base64url")),
    true,
  );
  return JSON.parse(Buffer.from(encodedPayload, "base64url").toString("utf8"));
}

const DEVICE_ONE = "f92b17e0-a5f7-4a97-9102-76147e9aa152";
const DEVICE_TWO = "a28a0c40-5e76-4f02-8cef-6d613755ef58";

test("admin issuance, activation, signed lease, device limit, and secret rotation work end to end", async (t) => {
  const env = makeEnvironment();
  t.after(() => env.DB.close());

  const unauthorized = await call(env, "POST", "/admin/licenses", { subject: "No" });
  assert.equal(unauthorized.response.status, 401);
  assert.equal(unauthorized.body.error.code, "ADMIN_UNAUTHORIZED");

  const license = await createLicense(env);
  assert.equal(license.expiresAt, null);
  assert.match(license.code, /^(?:[A-Z2-9]{4}-){7}[A-Z2-9]{4}$/u);
  const stored = env.DB.sqlite.prepare("SELECT code_hmac FROM licenses WHERE id = ?").get(license.id);
  assert.ok(stored.code_hmac);
  assert.equal(JSON.stringify(stored).includes(license.code), false);

  const listed = await call(env, "GET", "/admin/licenses", undefined, { admin: true });
  assert.equal(listed.response.status, 200);
  assert.equal(JSON.stringify(listed.body).includes(license.code), false);
  assert.equal(JSON.stringify(listed.body).includes("code_hmac"), false);

  const malformed = await call(env, "POST", "/v1/activate", { code: "bad", deviceId: DEVICE_ONE });
  const unknown = await call(env, "POST", "/v1/activate", { code: "AAAA-BBBB-CCCC-DDDD-EEEE-FFFF-GGGG-HHHH", deviceId: DEVICE_ONE });
  assert.deepEqual([malformed.response.status, malformed.body.error.code], [400, "INVALID_CODE"]);
  assert.deepEqual([unknown.response.status, unknown.body.error.code], [400, "INVALID_CODE"]);

  const activated = await call(env, "POST", "/v1/activate", { code: license.code, deviceId: DEVICE_ONE });
  assert.equal(activated.response.status, 200);
  const payload = decodeAndVerifyToken(activated.body.token);
  assert.equal(payload.product, "maisui-travel");
  assert.equal(payload.licenseId, license.id);
  assert.equal(payload.deviceId, DEVICE_ONE);
  assert.equal(payload.subject, "Integration User");
  assert.equal(payload.expiresAt - payload.issuedAt, 7 * 86400);
  const deviceRow = env.DB.sqlite.prepare("SELECT secret_hash FROM devices WHERE license_id = ?").get(license.id);
  assert.equal(JSON.stringify(deviceRow).includes(activated.body.deviceSecret), false);

  const full = await call(env, "POST", "/v1/activate", { code: license.code, deviceId: DEVICE_TWO });
  assert.deepEqual([full.response.status, full.body.error.code], [409, "DEVICE_LIMIT_REACHED"]);

  const rotated = await call(env, "POST", "/v1/activate", { code: license.code, deviceId: DEVICE_ONE });
  assert.equal(rotated.response.status, 200);
  assert.notEqual(rotated.body.deviceSecret, activated.body.deviceSecret);
  const oldRefresh = await call(env, "POST", "/v1/refresh", {
    licenseId: license.id, deviceId: DEVICE_ONE, deviceSecret: activated.body.deviceSecret,
  });
  assert.deepEqual([oldRefresh.response.status, oldRefresh.body.error.code], [401, "INVALID_CREDENTIAL"]);
  const newRefresh = await call(env, "POST", "/v1/refresh", {
    licenseId: license.id, deviceId: DEVICE_ONE, deviceSecret: rotated.body.deviceSecret,
  });
  assert.equal(newRefresh.response.status, 200);
  decodeAndVerifyToken(newRefresh.body.token);
});

test("unbind frees a slot and revocation blocks both activation and refresh", async (t) => {
  const env = makeEnvironment();
  t.after(() => env.DB.close());
  const license = await createLicense(env);
  const activation = await call(env, "POST", "/v1/activate", { code: license.code, deviceId: DEVICE_ONE });

  const devices = await call(env, "GET", `/admin/licenses/${license.id}/devices`, undefined, { admin: true });
  assert.deepEqual(devices.body.devices.map((row) => row.deviceId), [DEVICE_ONE]);
  assert.equal(JSON.stringify(devices.body).includes(activation.body.deviceSecret), false);

  const unbound = await call(env, "POST", `/admin/licenses/${license.id}/devices/${DEVICE_ONE}/unbind`, undefined, { admin: true });
  assert.equal(unbound.response.status, 200);
  const staleRefresh = await call(env, "POST", "/v1/refresh", {
    licenseId: license.id, deviceId: DEVICE_ONE, deviceSecret: activation.body.deviceSecret,
  });
  assert.deepEqual([staleRefresh.response.status, staleRefresh.body.error.code], [401, "INVALID_CREDENTIAL"]);
  const replacement = await call(env, "POST", "/v1/activate", { code: license.code, deviceId: DEVICE_TWO });
  assert.equal(replacement.response.status, 200);

  const revoked = await call(env, "POST", `/admin/licenses/${license.id}/revoke`, undefined, { admin: true });
  assert.equal(revoked.response.status, 200);
  const refreshAfterRevoke = await call(env, "POST", "/v1/refresh", {
    licenseId: license.id, deviceId: DEVICE_TWO, deviceSecret: replacement.body.deviceSecret,
  });
  assert.deepEqual([refreshAfterRevoke.response.status, refreshAfterRevoke.body.error.code], [403, "LICENSE_REVOKED"]);
  const activateAfterRevoke = await call(env, "POST", "/v1/activate", { code: license.code, deviceId: DEVICE_TWO });
  assert.deepEqual([activateAfterRevoke.response.status, activateAfterRevoke.body.error.code], [403, "LICENSE_REVOKED"]);
});

test("expiry clips leases, expired licenses fail, and leaseDays cannot exceed seven", async (t) => {
  const env = makeEnvironment();
  t.after(() => env.DB.close());
  const now = Math.floor(Date.now() / 1000);
  const expiry = now + 120;
  const license = await createLicense(env, { expiresAt: expiry });
  const activation = await call(env, "POST", "/v1/activate", { code: license.code, deviceId: DEVICE_ONE });
  assert.equal(decodeAndVerifyToken(activation.body.token).expiresAt, expiry);

  env.DB.sqlite.prepare("UPDATE licenses SET expires_at = ? WHERE id = ?").run(now - 1, license.id);
  const expiredRefresh = await call(env, "POST", "/v1/refresh", {
    licenseId: license.id, deviceId: DEVICE_ONE, deviceSecret: activation.body.deviceSecret,
  });
  assert.deepEqual([expiredRefresh.response.status, expiredRefresh.body.error.code], [403, "LICENSE_EXPIRED"]);
  const expiredActivate = await call(env, "POST", "/v1/activate", { code: license.code, deviceId: DEVICE_ONE });
  assert.deepEqual([expiredActivate.response.status, expiredActivate.body.error.code], [403, "LICENSE_EXPIRED"]);

  const tooLong = await call(env, "POST", "/admin/licenses", {
    subject: "Too long", expiresAt: null, maxDevices: 1, leaseDays: 8,
  }, { admin: true });
  assert.deepEqual([tooLong.response.status, tooLong.body.error.code], [400, "INVALID_REQUEST"]);
});

test("D1 per-minute counter returns stable 429 after the configured public limit", async (t) => {
  const env = makeEnvironment({ PUBLIC_RATE_LIMIT_PER_MINUTE: "2" });
  t.after(() => env.DB.close());
  const options = { ip: "198.51.100.77" };
  await call(env, "POST", "/v1/activate", { code: "bad", deviceId: DEVICE_ONE }, options);
  await call(env, "POST", "/v1/activate", { code: "bad", deviceId: DEVICE_ONE }, options);
  const limited = await call(env, "POST", "/v1/activate", { code: "bad", deviceId: DEVICE_ONE }, options);
  assert.deepEqual([limited.response.status, limited.body.error.code], [429, "RATE_LIMITED"]);
  assert.equal(limited.response.headers.get("Retry-After"), "60");
  const counter = env.DB.sqlite.prepare("SELECT count FROM rate_limits WHERE bucket LIKE 'activate:%'").get();
  assert.equal(counter.count, 3);
});

test("modern clients receive MS3 offline policy while legacy leases remain capped at seven days", async (t) => {
  const env = makeEnvironment();
  t.after(() => env.DB.close());
  const license = await createLicense(env, { offlineSeconds: 0 });
  const modern = await call(env, "POST", "/v1/activate", { code: license.code, deviceId: DEVICE_ONE, clientVersion: 31 });
  const modernPayload = decodeAndVerifyToken(modern.body.token, "MS3");
  assert.equal(modernPayload.offlineSeconds, 0);
  assert.equal(modernPayload.expiresAt - modernPayload.issuedAt, 60);
  const permanentOffline = await createLicense(env, { subject: "Permanent offline", maxDevices: 2, offlineSeconds: null });
  const permanentActivation = await call(env, "POST", "/v1/activate", { code: permanentOffline.code, deviceId: DEVICE_TWO, clientVersion: 31 });
  const permanentPayload = decodeAndVerifyToken(permanentActivation.body.token, "MS3");
  assert.equal(permanentPayload.offlineSeconds, null);
  assert.equal(permanentPayload.expiresAt, 253402300799);
  const legacy = await call(env, "POST", "/v1/refresh", {
    licenseId: permanentOffline.id, deviceId: DEVICE_TWO, deviceSecret: permanentActivation.body.deviceSecret, clientVersion: 30,
  });
  const legacyPayload = decodeAndVerifyToken(legacy.body.token);
  assert.equal(legacyPayload.offlineSeconds, undefined);
  assert.ok(legacyPayload.expiresAt - legacyPayload.issuedAt <= 7 * 86400);
  await call(env, "POST", `/admin/licenses/${permanentOffline.id}/freeze`, undefined, { admin: true });
  const resumedPermanent = await call(env, "POST", `/admin/licenses/${permanentOffline.id}/unfreeze`, undefined, { admin: true });
  assert.equal(resumedPermanent.body.expiresAt, null);
  const hugeOffline = await createLicense(env, { subject: "Huge finite offline", offlineSeconds: Number.MAX_SAFE_INTEGER });
  const hugeActivation = await call(env, "POST", "/v1/activate", { code: hugeOffline.code, deviceId: DEVICE_ONE, clientVersion: 31 });
  const hugePayload = decodeAndVerifyToken(hugeActivation.body.token, "MS3");
  assert.equal(hugePayload.offlineSeconds, Number.MAX_SAFE_INTEGER);
  assert.equal(hugePayload.expiresAt, 253402300799);
});

test("legacy MS2 obeys shorter offline limits and online-only rejection does not reserve or rotate a device", async (t) => {
  const env = makeEnvironment();
  t.after(() => env.DB.close());
  const short = await createLicense(env, { offlineSeconds: 300, leaseDays: 7 });
  const shortActivation = await call(env, "POST", "/v1/activate", { code: short.code, deviceId: DEVICE_ONE, clientVersion: 30 });
  const shortPayload = decodeAndVerifyToken(shortActivation.body.token);
  assert.equal(shortPayload.expiresAt - shortPayload.issuedAt, 300);

  const onlineOnly = await createLicense(env, { offlineSeconds: 0 });
  const rejected = await call(env, "POST", "/v1/activate", { code: onlineOnly.code, deviceId: DEVICE_TWO, clientVersion: 30 });
  assert.deepEqual([rejected.response.status, rejected.body.error.code], [403, "CLIENT_UPDATE_REQUIRED"]);
  assert.equal(env.DB.sqlite.prepare("SELECT COUNT(*) AS count FROM devices WHERE license_id = ?").get(onlineOnly.id).count, 0);

  const modern = await call(env, "POST", "/v1/activate", { code: onlineOnly.code, deviceId: DEVICE_TWO, clientVersion: 31 });
  assert.equal(modern.response.status, 200);
  const secretHashBefore = env.DB.sqlite.prepare("SELECT secret_hash AS secretHash FROM devices WHERE license_id = ?").get(onlineOnly.id).secretHash;
  const legacyRefresh = await call(env, "POST", "/v1/refresh", {
    licenseId: onlineOnly.id, deviceId: DEVICE_TWO, deviceSecret: modern.body.deviceSecret, clientVersion: 30,
  });
  assert.deepEqual([legacyRefresh.response.status, legacyRefresh.body.error.code], [403, "CLIENT_UPDATE_REQUIRED"]);
  assert.equal(env.DB.sqlite.prepare("SELECT secret_hash AS secretHash FROM devices WHERE license_id = ?").get(onlineOnly.id).secretHash, secretHashBefore);
});

test("freeze pauses finite duration, edit can renew expired licenses, and unfreeze restores remaining time", async (t) => {
  const env = makeEnvironment();
  t.after(() => env.DB.close());
  const now = Math.floor(Date.now() / 1000);
  const license = await createLicense(env, { expiresAt: now + 3600, offlineSeconds: null });
  const activated = await call(env, "POST", "/v1/activate", { code: license.code, deviceId: DEVICE_ONE, clientVersion: 31 });
  const frozen = await call(env, "POST", `/admin/licenses/${license.id}/freeze`, undefined, { admin: true });
  assert.equal(frozen.response.status, 200);
  const frozenAgain = await call(env, "POST", `/admin/licenses/${license.id}/freeze`, undefined, { admin: true });
  assert.equal(frozenAgain.body.frozenAt, frozen.body.frozenAt);
  const blocked = await call(env, "POST", "/v1/refresh", {
    licenseId: license.id, deviceId: DEVICE_ONE, deviceSecret: activated.body.deviceSecret, clientVersion: 31,
  });
  assert.deepEqual([blocked.response.status, blocked.body.error.code], [403, "LICENSE_FROZEN"]);
  const edited = await call(env, "PATCH", `/admin/licenses/${license.id}`, { expiresAt: now + 7200, offlineSeconds: 12345 }, { admin: true });
  assert.equal(edited.body.offlineSeconds, 12345);
  assert.ok(edited.body.frozenRemainingSeconds >= 7198);
  const resumed = await call(env, "POST", `/admin/licenses/${license.id}/unfreeze`, undefined, { admin: true });
  assert.ok(resumed.body.expiresAt >= now + 7198);
  const resumedAgain = await call(env, "POST", `/admin/licenses/${license.id}/unfreeze`, undefined, { admin: true });
  assert.equal(resumedAgain.body.expiresAt, resumed.body.expiresAt);
  env.DB.sqlite.prepare("UPDATE licenses SET expires_at = ? WHERE id = ?").run(now - 10, license.id);
  const renewed = await call(env, "PATCH", `/admin/licenses/${license.id}`, { expiresAt: now + 1000 }, { admin: true });
  assert.equal(renewed.response.status, 200);
});

test("only revoked licenses archive or delete, restore preserves revocation, and delete cascades devices", async (t) => {
  const env = makeEnvironment();
  t.after(() => env.DB.close());
  const license = await createLicense(env);
  await call(env, "POST", "/v1/activate", { code: license.code, deviceId: DEVICE_ONE });
  const premature = await call(env, "POST", `/admin/licenses/${license.id}/archive`, undefined, { admin: true });
  assert.deepEqual([premature.response.status, premature.body.error.code], [409, "LICENSE_NOT_REVOKED"]);
  const prematureDelete = await call(env, "DELETE", `/admin/licenses/${license.id}`, undefined, { admin: true });
  assert.deepEqual([prematureDelete.response.status, prematureDelete.body.error.code], [409, "LICENSE_NOT_REVOKED"]);
  await call(env, "POST", `/admin/licenses/${license.id}/revoke`, undefined, { admin: true });
  await call(env, "POST", `/admin/licenses/${license.id}/archive`, undefined, { admin: true });
  const visible = await call(env, "GET", "/admin/licenses", undefined, { admin: true });
  assert.equal(visible.body.licenses.some((row) => row.id === license.id), false);
  const archived = await call(env, "GET", "/admin/licenses?archived=1", undefined, { admin: true });
  assert.equal(archived.body.licenses[0].id, license.id);
  await call(env, "POST", `/admin/licenses/${license.id}/restore`, undefined, { admin: true });
  assert.ok(env.DB.sqlite.prepare("SELECT revoked_at FROM licenses WHERE id = ?").get(license.id).revoked_at);
  const deleted = await call(env, "DELETE", `/admin/licenses/${license.id}`, undefined, { admin: true });
  assert.equal(deleted.body.deleted, true);
  assert.equal(env.DB.sqlite.prepare("SELECT COUNT(*) AS count FROM devices WHERE license_id = ?").get(license.id).count, 0);
});
