import assert from "node:assert/strict";
import { generateKeyPairSync, verify } from "node:crypto";
import { readFileSync, readdirSync } from "node:fs";
import { DatabaseSync } from "node:sqlite";
import test from "node:test";

import worker from "../src/index.js";
import { hmacSha256, normalizeCode } from '../src/crypto.js';

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
    const before = this.database.prepare('SELECT total_changes() AS count').get().count;
    this.database.prepare(this.sql).run(...this.values);
    const after = this.database.prepare('SELECT total_changes() AS count').get().count;
    return { success: true, meta: { changes: after - before } };
  }
}

class D1Database {
  constructor() {
    this.sqlite = new DatabaseSync(":memory:");
    for (const file of readdirSync(new URL('../migrations/', import.meta.url)).filter(f => f.endsWith('.sql')).sort()) {
      this.sqlite.exec(readFileSync(new URL('../migrations/' + file, import.meta.url), 'utf8'));
    }
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
    CODE_ENCRYPTION_KEYS: JSON.stringify({ test1: Buffer.alloc(32, 17).toString('base64') }),
    CODE_ENCRYPTION_KEY_ID: 'test1',
    SIGNING_PRIVATE_KEY_PKCS8: privateKeyBase64,
    DEFAULT_LEASE_DAYS: "7",
    DEFAULT_MAX_DEVICES: "1",
    PUBLIC_RATE_LIMIT_PER_MINUTE: "100",
    ADMIN_RATE_LIMIT_PER_MINUTE: "100",
    ...overrides,
  };
}

class FakeUpdateR2 {
  constructor() { this.objects = new Map(); }
  putRelease(versionCode, bytes, { sha256 = "a".repeat(64), metadataVersion = String(versionCode) } = {}) {
    this.objects.set(`updates/releases/${versionCode}.apk`, {
      bytes: new Uint8Array(bytes),
      customMetadata: { versionCode: metadataVersion, sha256 },
    });
  }
  async head(key) {
    const object = this.objects.get(key);
    return object && { size: object.bytes.byteLength, customMetadata: object.customMetadata };
  }
  async get(key, options = {}) {
    const object = this.objects.get(key);
    if (!object) return null;
    const range = options.range;
    const offset = range?.offset ?? 0;
    const length = range?.length ?? object.bytes.byteLength;
    return {
      body: new Blob([object.bytes.slice(offset, offset + length)]).stream(),
      size: object.bytes.byteLength,
      customMetadata: object.customMetadata,
    };
  }
}

function updateEnvironment(updates = new FakeUpdateR2()) {
  updates.putRelease(42, Uint8Array.from([10, 20, 30, 40, 50]));
  return makeEnvironment({
    UPDATES: updates,
    UPDATE_VERSION_CODE: "42",
    UPDATE_VERSION_NAME: "0.5.0",
    UPDATE_GITHUB_APK_URL: "https://github.com/DualSpirits-Light/maisui-travel/releases/download/v0.5.0/maisui-travel-0.5.0.apk",
    UPDATE_APK_URL: "https://github.com/DualSpirits-Light/maisui-travel/releases/download/v0.5.0/maisui-travel-0.5.0.apk",
    UPDATE_APK_SHA256: "a".repeat(64),
    UPDATE_NOTES: "Staged update",
    UPDATES_RATE_LIMIT_PER_MINUTE: "20",
  });
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

async function updateCall(env, method, path, headers = {}) {
  return worker.fetch(new Request(`https://license.example${path}`, {
    method,
    headers: { "CF-Connecting-IP": "198.51.100.20", ...headers },
  }), env);
}

test("update manifest presents one canonical GitHub release and ready Cloudflare source", async (t) => {
  const env = updateEnvironment(); t.after(() => env.DB.close());
  const response = await updateCall(env, "GET", "/updates/latest.json");
  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), {
    packageName: "cn.lvxu.travel",
    versionCode: 42,
    versionName: "0.5.0",
    apkUrl: "https://github.com/DualSpirits-Light/maisui-travel/releases/download/v0.5.0/maisui-travel-0.5.0.apk",
    githubApkUrl: "https://github.com/DualSpirits-Light/maisui-travel/releases/download/v0.5.0/maisui-travel-0.5.0.apk",
    cloudflareApkUrl: "https://license.example/updates/apk/42.apk",
    sha256: "a".repeat(64),
    notes: "Staged update",
  });
});

test("a ready Cloudflare release remains available without a GitHub asset", async (t) => {
  const env = updateEnvironment(); t.after(() => env.DB.close());
  delete env.UPDATE_GITHUB_APK_URL;
  delete env.UPDATE_APK_URL;
  const response = await updateCall(env, "GET", "/updates/latest.json");
  assert.equal(response.status, 200);
  const manifest = await response.json();
  assert.equal(manifest.apkUrl, "https://license.example/updates/apk/42.apk");
  assert.equal(manifest.cloudflareApkUrl, manifest.apkUrl);
  assert.equal(Object.hasOwn(manifest, "githubApkUrl"), false);
  assert.equal((await updateCall(env, "GET", "/updates/apk/42.apk")).status, 200);
  env.UPDATES.objects.clear();
  assert.equal((await updateCall(env, "GET", "/updates/latest.json")).status, 404);
});

test("update download streams only its configured release and supports HEAD and a byte range", async (t) => {
  const env = updateEnvironment(); t.after(() => env.DB.close());
  const ranged = await updateCall(env, "GET", "/updates/apk/42.apk", { Range: "bytes=1-3" });
  assert.equal(ranged.status, 206);
  assert.equal(ranged.headers.get("Content-Type"), "application/vnd.android.package-archive");
  assert.equal(ranged.headers.get("Content-Range"), "bytes 1-3/5");
  assert.equal(ranged.headers.get("Content-Length"), "3");
  assert.deepEqual([...new Uint8Array(await ranged.arrayBuffer())], [20, 30, 40]);
  const head = await updateCall(env, "HEAD", "/updates/apk/42.apk");
  assert.equal(head.status, 200);
  assert.equal(head.headers.get("Content-Length"), "5");
  assert.equal((await head.arrayBuffer()).byteLength, 0);
});

test("update endpoints fail closed for missing objects and release metadata mismatches", async (t) => {
  const missingUpdates = new FakeUpdateR2();
  const missing = updateEnvironment(missingUpdates); missingUpdates.objects.clear();
  t.after(() => missing.DB.close());
  const missingDownload = await updateCall(missing, "GET", "/updates/apk/42.apk");
  assert.equal(missingDownload.status, 404);
  assert.equal((await missingDownload.json()).error.code, "UPDATE_NOT_READY");
  const missingHead = await updateCall(missing, "HEAD", "/updates/apk/42.apk");
  assert.equal(missingHead.status, 404);
  const missingManifest = await updateCall(missing, "GET", "/updates/latest.json");
  assert.equal(Object.hasOwn(await missingManifest.json(), "cloudflareApkUrl"), false);

  const mismatchedUpdates = new FakeUpdateR2();
  const mismatched = updateEnvironment(mismatchedUpdates);
  mismatchedUpdates.putRelease(42, Uint8Array.from([1]), { metadataVersion: "41" });
  t.after(() => mismatched.DB.close());
  const mismatch = await updateCall(mismatched, "GET", "/updates/apk/42.apk");
  assert.equal(mismatch.status, 409);
  assert.equal((await mismatch.json()).error.code, "UPDATE_METADATA_MISMATCH");
  const manifest = await updateCall(mismatched, "GET", "/updates/latest.json");
  assert.equal(Object.hasOwn(await manifest.json(), "cloudflareApkUrl"), false);

  const rangeReady = updateEnvironment();
  t.after(() => rangeReady.DB.close());
  const invalidRange = await updateCall(rangeReady, "GET", "/updates/apk/42.apk", { Range: "bytes=99-100" });
  assert.equal(invalidRange.status, 416);
  assert.equal(invalidRange.headers.get("Content-Range"), "bytes */5");

  const misconfigured = updateEnvironment();
  misconfigured.UPDATE_APK_URL = "https://gh-proxy.example/https://github.com/DualSpirits-Light/maisui-travel/releases/download/v0.5.0/maisui-travel-0.5.0.apk";
  t.after(() => misconfigured.DB.close());
  const invalidManifest = await updateCall(misconfigured, "GET", "/updates/latest.json");
  assert.equal(invalidManifest.status, 404);
  assert.equal((await invalidManifest.json()).error.code, "UPDATE_NOT_CONFIGURED");

  const proxyConfigured = updateEnvironment();
  proxyConfigured.UPDATE_GITHUB_APK_URL = "https://gh-proxy.example/https://github.com/DualSpirits-Light/maisui-travel/releases/download/v0.5.0/maisui-travel-0.5.0.apk";
  proxyConfigured.UPDATE_APK_URL = proxyConfigured.UPDATE_GITHUB_APK_URL;
  t.after(() => proxyConfigured.DB.close());
  assert.equal((await updateCall(proxyConfigured, "GET", "/updates/latest.json")).status, 404);
});

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

test("donation administrators manage records while the public feed exposes only public display fields", async (t) => {
  const env = makeEnvironment();
  t.after(() => env.DB.close());

  const missingPublicFeed = await call(env, "GET", "/v1/donations");
  assert.equal(missingPublicFeed.response.status, 200);
  assert.deepEqual(missingPublicFeed.body, { records: [] });

  const privateRecord = await call(env, "POST", "/admin/donations", {
    platform: "微信赞赏", name: "不公开的朋友", amount: "12.30", date: "2026-09-21", isPublic: false,
  }, { admin: true });
  assert.equal(privateRecord.response.status, 201);
  assert.equal(privateRecord.body.currency, "CNY");
  assert.equal(privateRecord.body.amount, "12.30");

  const publicRecord = await call(env, "POST", "/admin/donations", {
    platform: "爱发电", name: "旅行者", amount: "20", currency: "CNY", date: "2026-09-22", message: "继续加油", isPublic: true,
  }, { admin: true });
  assert.equal(publicRecord.response.status, 201);

  const feed = await call(env, "GET", "/v1/donations");
  assert.equal(feed.response.status, 200);
  assert.deepEqual(feed.body, { records: [{
    platform: "爱发电", name: "旅行者", amount: "20.00", currency: "CNY", date: "2026-09-22", message: "继续加油",
  }] });
  assert.equal(JSON.stringify(feed.body).includes("不公开的朋友"), false);
  assert.equal(Object.hasOwn(feed.body.records[0], "id"), false);

  const updated = await call(env, "PATCH", `/admin/donations/${privateRecord.body.id}`, {
    amount: "12.34", isPublic: true, message: "匿名支持",
  }, { admin: true });
  assert.equal(updated.response.status, 200);
  assert.equal(updated.body.amount, "12.34");
  assert.equal(updated.body.isPublic, true);
  const updatedFeed = await call(env, "GET", "/v1/donations");
  assert.equal(updatedFeed.body.records.length, 2);
  assert.equal(updatedFeed.body.records.find((record) => record.name === "不公开的朋友").amount, "12.34");

  const removed = await call(env, "DELETE", `/admin/donations/${privateRecord.body.id}`, undefined, { admin: true });
  assert.deepEqual(removed.body, { id: privateRecord.body.id, deleted: true });
  const afterDelete = await call(env, "GET", "/v1/donations");
  assert.deepEqual(afterDelete.body, feed.body);
});

test("donation management rejects invalid money and unauthenticated writes", async (t) => {
  const env = makeEnvironment();
  t.after(() => env.DB.close());
  const unauthenticated = await call(env, "POST", "/admin/donations", {
    platform: "支付宝", name: "支持者", amount: "1", date: "2026-09-21",
  });
  assert.equal(unauthenticated.response.status, 401);
  const invalidMoney = await call(env, "POST", "/admin/donations", {
    platform: "支付宝", name: "支持者", amount: "1.234", date: "2026-09-21",
  }, { admin: true });
  assert.deepEqual([invalidMoney.response.status, invalidMoney.body.error.code], [400, "INVALID_REQUEST"]);
  const invalidDate = await call(env, "POST", "/admin/donations", {
    platform: "支付宝", name: "支持者", amount: "1", date: "2026-13-01",
  }, { admin: true });
  assert.deepEqual([invalidDate.response.status, invalidDate.body.error.code], [400, "INVALID_REQUEST"]);
});

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
  assert.equal(deleted.response.status, 200);
  assert.equal(deleted.body.deleted, true);
  assert.equal(env.DB.sqlite.prepare("SELECT id FROM licenses WHERE id = ?").get(license.id), undefined);
  assert.equal(env.DB.sqlite.prepare("SELECT COUNT(*) AS count FROM devices WHERE license_id = ?").get(license.id).count, 0);
});

test('code reveal is authenticated, audited and encrypted at rest with key rotation support', async t => {
  const env = makeEnvironment(); t.after(() => env.DB.close());
  const license = await createLicense(env);
  const path = `/admin/licenses/${license.id}/code`;
  assert.equal((await call(env, 'GET', path)).response.status, 401);
  const revealed = await call(env, 'GET', path, undefined, { admin: true });
  assert.equal(revealed.response.status, 200);
  assert.equal(revealed.body.code, license.code);
  assert.equal(revealed.response.headers.get('cache-control'), 'no-store');
  const stored = env.DB.sqlite.prepare('SELECT * FROM licenses WHERE id = ?').get(license.id);
  assert.ok(stored.code_ciphertext);
  assert.equal(JSON.stringify(stored).includes(license.code), false);
  const audit = env.DB.sqlite.prepare('SELECT * FROM license_code_audit').all();
  assert.equal(audit.length, 1); assert.equal(audit[0].action, 'reveal'); assert.equal(audit[0].outcome, 'success');
  assert.equal(JSON.stringify(audit).includes(license.code), false);
  env.CODE_ENCRYPTION_KEYS = JSON.stringify({ ...JSON.parse(env.CODE_ENCRYPTION_KEYS), test2: Buffer.alloc(32, 29).toString('base64') });
  env.CODE_ENCRYPTION_KEY_ID = 'test2';
  assert.equal((await call(env, 'GET', path, undefined, { admin: true })).body.code, license.code);
  const next = await createLicense(env);
  assert.equal(env.DB.sqlite.prepare('SELECT code_key_id FROM licenses WHERE id = ?').get(next.id).code_key_id, 'test2');
  env.DB.sqlite.prepare('UPDATE licenses SET code_ciphertext = ? WHERE id = ?').run(stored.code_ciphertext, next.id);
  const tampered = await call(env, 'GET', `/admin/licenses/${next.id}/code`, undefined, { admin: true });
  assert.deepEqual([tampered.response.status, tampered.body.error.code], [503, 'CODE_VAULT_UNAVAILABLE']);
});

test('legacy reissue creates a separate encrypted license without changing original codes or devices', async t => {
  const env = makeEnvironment(); t.after(() => env.DB.close());
  const license = { id: 'legacy-license-one', code: 'AAAA-BBBB-CCCC-DDDD-EEEE-FFFF-GGGG-HHHH' };
  env.DB.sqlite.prepare('INSERT INTO licenses (id,code_hmac,subject,max_devices,lease_days,created_at) VALUES (?,?,?,1,7,1)')
    .run(license.id, await hmacSha256(env.CODE_PEPPER, normalizeCode(license.code)), 'Existing user');
  const activated = await call(env, 'POST', '/v1/activate', { code: license.code, deviceId: DEVICE_ONE, clientVersion: 31 });
  const original = env.DB.sqlite.prepare('SELECT * FROM licenses WHERE id = ?').get(license.id);
  const path = `/admin/licenses/${license.id}`;
  const legacy = await call(env, 'GET', path + '/code', undefined, { admin: true });
  assert.deepEqual([legacy.response.status, legacy.body.error.code], [409, 'LEGACY_CODE_UNAVAILABLE']);
  assert.equal((await call(env, 'POST', path + '/reissue', {}, { admin: true })).response.status, 400);
  const result = await call(env, 'POST', path + '/reissue', { confirm: true }, { admin: true });
  assert.equal(result.response.status, 201); assert.notEqual(result.body.id, license.id);
  assert.equal(result.body.sourceLicenseId, license.id); assert.equal(result.body.originalLicensePreserved, true);
  assert.deepEqual(env.DB.sqlite.prepare('SELECT * FROM licenses WHERE id = ?').get(license.id), original);
  assert.equal((await call(env, 'POST', '/v1/refresh', { licenseId: license.id, deviceId: DEVICE_ONE, deviceSecret: activated.body.deviceSecret, clientVersion: 31 })).response.status, 200);
  assert.equal((await call(env, 'POST', '/v1/activate', { code: license.code, deviceId: DEVICE_ONE, clientVersion: 31 })).response.status, 200);
  assert.equal((await call(env, 'POST', '/v1/activate', { code: result.body.code, deviceId: DEVICE_TWO, clientVersion: 31 })).response.status, 200);
  const list = await call(env, 'GET', '/admin/licenses', undefined, { admin: true });
  assert.equal(list.body.licenses.find(l => l.id === license.id).codeStatus, 'legacy-unavailable');
  assert.equal(list.body.licenses.find(l => l.id === result.body.id).codeStatus, 'available');
});

test('restoring revoked use preserves expiry and frozen duration, while unarchiving alone never restores use', async t => {
  const env = makeEnvironment(); t.after(() => env.DB.close());
  const license = await createLicense(env, { expiresAt: Math.floor(Date.now() / 1000) + 3600 });
  const path = `/admin/licenses/${license.id}`;
  await call(env, 'POST', path + '/freeze', undefined, { admin: true });
  await call(env, 'POST', path + '/revoke', undefined, { admin: true });
  await call(env, 'POST', path + '/archive', undefined, { admin: true });
  const restored = await call(env, 'POST', path + '/restore-use', undefined, { admin: true });
  assert.equal(restored.response.status, 200);
  const row = env.DB.sqlite.prepare('SELECT * FROM licenses WHERE id = ?').get(license.id);
  assert.equal(row.revoked_at, null); assert.equal(row.archived_at, null); assert.ok(row.frozen_at);
  const blocked = await call(env, 'POST', '/v1/activate', { code: license.code, deviceId: DEVICE_ONE, clientVersion: 31 });
  assert.equal(blocked.body.error.code, 'LICENSE_FROZEN');
  await call(env, 'PATCH', path, { expiresAt: null }, { admin: true });
  const resumed = await call(env, 'POST', path + '/unfreeze', undefined, { admin: true });
  assert.equal(resumed.body.expiresAt, null);
  await call(env, 'POST', path + '/revoke', undefined, { admin: true });
  assert.equal((await call(env, 'POST', path + '/freeze', undefined, { admin: true })).response.status, 409);
});

test('unavailable encryption secret blocks issuance but preserves activation and renewal of existing licenses', async t => {
  const env = makeEnvironment(); t.after(() => env.DB.close());
  const license = await createLicense(env);
  delete env.CODE_ENCRYPTION_KEYS;
  const failed = await call(env, 'POST', '/admin/licenses', { subject: 'Cannot encrypt' }, { admin: true });
  assert.equal(failed.response.status, 503);
  assert.equal(failed.body.error.code, 'CODE_VAULT_UNAVAILABLE');
  assert.equal(env.DB.sqlite.prepare('SELECT COUNT(*) AS n FROM licenses').get().n, 1);
  assert.equal((await call(env, 'POST', '/v1/activate', { code: license.code, deviceId: DEVICE_ONE, clientVersion: 31 })).response.status, 200);
  assert.equal((await call(env, 'PATCH', `/admin/licenses/${license.id}`, { expiresAt: null }, { admin: true })).response.status, 200);
});

test('concurrent expiry and offline edits preserve both updates while a license is frozen', async t => {
  const env = makeEnvironment(); t.after(() => env.DB.close());
  const license = await createLicense(env, { expiresAt: Math.floor(Date.now() / 1000) + 3600 });
  const path = `/admin/licenses/${license.id}`;
  await call(env, 'POST', path + '/freeze', undefined, { admin: true });
  const expiry = Math.floor(Date.now() / 1000) + 7200;
  // Hold both pre-update reads at the database boundary to expose lost updates.
  const prepare = env.DB.prepare.bind(env.DB);
  let reads = 0, release;
  const bothRead = new Promise(resolve => { release = resolve; });
  env.DB.prepare = sql => {
    const stmt = prepare(sql);
    if (sql !== 'SELECT * FROM licenses WHERE id = ?') return stmt;
    return { bind: (...args) => ({ first: async () => {
      const row = stmt.bind(...args).first();
      if (++reads === 2) release();
      await bothRead;
      return row;
    } }) };
  };
  const results = await Promise.all([
    call(env, 'PATCH', path, { expiresAt: expiry }, { admin: true }),
    call(env, 'PATCH', path, { offlineSeconds: 60 }, { admin: true }),
  ]);
  assert.deepEqual(results.map(r => r.response.status), [200, 200]);
  const row = env.DB.sqlite.prepare('SELECT * FROM licenses WHERE id = ?').get(license.id);
  assert.equal(row.expires_at, expiry); assert.equal(row.offline_seconds, 60);
  assert.ok(row.frozen_remaining_seconds >= 7198);
});

test('code reveal fails closed on audit failure and binds ciphertext to its own license', async t => {
  const env = makeEnvironment(); t.after(() => env.DB.close());
  const a = await createLicense(env), b = await createLicense(env);
  const ciphertext = env.DB.sqlite.prepare('SELECT code_ciphertext FROM licenses WHERE id = ?').get(a.id).code_ciphertext;
  env.DB.sqlite.prepare('UPDATE licenses SET code_ciphertext = ? WHERE id = ?').run(ciphertext, b.id);
  assert.equal((await call(env, 'GET', `/admin/licenses/${b.id}/code`, undefined, { admin: true })).response.status, 503);
  env.DB.sqlite.exec("CREATE TRIGGER block_audit BEFORE INSERT ON license_code_audit BEGIN SELECT RAISE(ABORT, 'audit unavailable'); END");
  const failed = await call(env, 'GET', `/admin/licenses/${a.id}/code`, undefined, { admin: true });
  assert.equal(failed.response.status, 500); assert.equal(JSON.stringify(failed.body).includes(a.code), false);
});

test('reissue preserves revoked frozen archived states and recovery alone never renews expiry', async t => {
  const env = makeEnvironment(); t.after(() => env.DB.close());
  const license = await createLicense(env);
  const path = `/admin/licenses/${license.id}`;
  await call(env, 'POST', path + '/freeze', undefined, { admin: true });
  await call(env, 'POST', path + '/revoke', undefined, { admin: true });
  await call(env, 'POST', path + '/archive', undefined, { admin: true });
  const reissued = await call(env, 'POST', path + '/reissue', { confirm: true }, { admin: true });
  assert.equal(reissued.response.status, 201);
  const original = env.DB.sqlite.prepare('SELECT revoked_at,frozen_at,archived_at FROM licenses WHERE id = ?').get(license.id);
  assert.deepEqual(env.DB.sqlite.prepare('SELECT revoked_at,frozen_at,archived_at FROM licenses WHERE id = ?').get(reissued.body.id), original);
  await call(env, 'POST', path + '/restore-use', undefined, { admin: true });
  await call(env, 'POST', path + '/unfreeze', undefined, { admin: true });
  env.DB.sqlite.prepare('UPDATE licenses SET expires_at = 1, revoked_at = 1 WHERE id = ?').run(license.id);
  await call(env, 'POST', path + '/restore-use', undefined, { admin: true });
  assert.equal((await call(env, 'POST', '/v1/activate', { code: license.code, deviceId: DEVICE_ONE })).body.error.code, 'LICENSE_EXPIRED');
});

test('an unfreeze racing a permanent-term edit retains the latest permanent term', async t => {
  const env = makeEnvironment(); t.after(() => env.DB.close());
  const license = await createLicense(env, { expiresAt: Math.floor(Date.now() / 1000) + 3600 });
  const path = `/admin/licenses/${license.id}`;
  await call(env, 'POST', path + '/freeze', undefined, { admin: true });
  const prepare = env.DB.prepare.bind(env.DB);
  let release, started;
  const gate = new Promise(resolve => { release = resolve; });
  const readStarted = new Promise(resolve => { started = resolve; });
  env.DB.prepare = sql => {
    const stmt = prepare(sql);
    if (sql !== 'SELECT * FROM licenses WHERE id = ?') return stmt;
    env.DB.prepare = prepare;
    return { bind: (...args) => ({ first: async () => {
      const row = stmt.bind(...args).first(); started(); await gate; return row;
    } }) };
  };
  const unfreeze = call(env, 'POST', path + '/unfreeze', undefined, { admin: true });
  await readStarted;
  await call(env, 'PATCH', path, { expiresAt: null }, { admin: true });
  release();
  const result = await unfreeze;
  assert.equal(result.response.status, 200); assert.equal(result.body.expiresAt, null);
});

test('archiving racing restore-use cannot hide a now-active license', async t => {
  const env = makeEnvironment(); t.after(() => env.DB.close());
  const license = await createLicense(env);
  const path = `/admin/licenses/${license.id}`;
  await call(env, 'POST', path + '/revoke', undefined, { admin: true });
  const prepare = env.DB.prepare.bind(env.DB);
  let release, started;
  const gate = new Promise(resolve => { release = resolve; });
  const readStarted = new Promise(resolve => { started = resolve; });
  env.DB.prepare = sql => {
    const stmt = prepare(sql);
    if (sql !== 'SELECT revoked_at, archived_at FROM licenses WHERE id = ?') return stmt;
    return { bind: (...args) => ({ first: async () => {
      const row = stmt.bind(...args).first(); started(); await gate; return row;
    } }) };
  };
  const archive = call(env, 'POST', path + '/archive', undefined, { admin: true });
  await readStarted;
  await call(env, 'POST', path + '/restore-use', undefined, { admin: true });
  release();
  const result = await archive;
  assert.equal(result.response.status, 409);
  assert.equal(env.DB.sqlite.prepare('SELECT archived_at FROM licenses WHERE id = ?').get(license.id).archived_at, null);
});

test('freeze uses the latest term and delete refuses a concurrently restored license', async t => {
  const env = makeEnvironment(); t.after(() => env.DB.close());
  const license = await createLicense(env, { expiresAt: Math.floor(Date.now() / 1000) + 3600 });
  const path = `/admin/licenses/${license.id}`;
  const prepare = env.DB.prepare.bind(env.DB);
  async function raceRead(sqlMatch, operation, concurrent) {
    let release, started;
    const gate = new Promise(resolve => { release = resolve; });
    const readStarted = new Promise(resolve => { started = resolve; });
    env.DB.prepare = sql => {
      const stmt = prepare(sql);
      if (sql !== sqlMatch) return stmt;
      env.DB.prepare = prepare;
      return { bind: (...args) => ({ first: async () => {
        const row = stmt.bind(...args).first(); started(); await gate; return row;
      } }) };
    };
    const pending = operation(); await readStarted; await concurrent(); release(); return pending;
  }
  await raceRead('SELECT * FROM licenses WHERE id = ?',
    () => call(env, 'POST', path + '/freeze', undefined, { admin: true }),
    () => call(env, 'PATCH', path, { expiresAt: null }, { admin: true }));
  assert.equal(env.DB.sqlite.prepare('SELECT frozen_remaining_seconds FROM licenses WHERE id = ?').get(license.id).frozen_remaining_seconds, null);
  await call(env, 'POST', path + '/revoke', undefined, { admin: true });
  const deletion = await raceRead('SELECT revoked_at FROM licenses WHERE id = ?',
    () => call(env, 'DELETE', path, undefined, { admin: true }),
    () => call(env, 'POST', path + '/restore-use', undefined, { admin: true }));
  assert.equal(deletion.response.status, 409);
  assert.ok(env.DB.sqlite.prepare('SELECT id FROM licenses WHERE id = ?').get(license.id));
});
