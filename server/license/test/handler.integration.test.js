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

function decodeAndVerifyToken(token) {
  const [prefix, encodedPayload, encodedSignature] = token.split(".");
  assert.equal(prefix, "MS2");
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
