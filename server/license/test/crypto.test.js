import assert from "node:assert/strict";
import { generateKeyPairSync, verify, webcrypto } from "node:crypto";
import test from "node:test";

globalThis.crypto ??= webcrypto;

const { generateLicenseCode, normalizeCode, sha256, signLease } = await import("../src/crypto.js");

function decodeBase64Url(value) {
  return Buffer.from(value, "base64url");
}

test("generated license codes contain 160 random bits and normalize predictably", () => {
  const codes = new Set(Array.from({ length: 100 }, generateLicenseCode));
  assert.equal(codes.size, 100);
  for (const code of codes) {
    assert.match(code, /^(?:[A-Z2-9]{4}-){7}[A-Z2-9]{4}$/u);
    assert.equal(normalizeCode(` ${code.toLowerCase()} `), code.replaceAll("-", ""));
  }
  assert.equal(normalizeCode("not-a-code"), null);
});

test("MS2 token is exact UTF-8 JSON signed with RSA SHA-256 PKCS#1 v1.5", async () => {
  const { privateKey, publicKey } = generateKeyPairSync("rsa", { modulusLength: 2048 });
  const pkcs8 = privateKey.export({ type: "pkcs8", format: "der" }).toString("base64");
  const payload = {
    product: "maisui-travel",
    licenseId: "license_123",
    deviceId: "f92b17e0-a5f7-4a97-9102-76147e9aa152",
    subject: "测试用户",
    issuedAt: 1_700_000_000,
    expiresAt: 1_700_604_800,
  };
  const token = await signLease(payload, pkcs8);
  const [prefix, body, signature] = token.split(".");
  assert.equal(prefix, "MS2");
  assert.deepEqual(JSON.parse(decodeBase64Url(body).toString("utf8")), payload);
  assert.equal(
    verify("sha256", Buffer.from(`${prefix}.${body}`), publicKey, decodeBase64Url(signature)),
    true,
  );
});

test("secret hashing is deterministic and does not preserve plaintext", async () => {
  const hash = await sha256("opaque-device-secret");
  assert.equal(hash, await sha256("opaque-device-secret"));
  assert.notEqual(hash, "opaque-device-secret");
});
