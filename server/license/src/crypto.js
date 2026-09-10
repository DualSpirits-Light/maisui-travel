const encoder = new TextEncoder();

export function base64Url(bytes) {
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replaceAll("+", "-").replaceAll("/", "_").replace(/=+$/u, "");
}

export function base64Bytes(value) {
  const binary = atob(value.replace(/\s+/gu, ""));
  return Uint8Array.from(binary, (character) => character.charCodeAt(0));
}

export async function sha256(value) {
  return base64Url(new Uint8Array(await crypto.subtle.digest("SHA-256", encoder.encode(value))));
}

export async function hmacSha256(key, value) {
  const cryptoKey = await crypto.subtle.importKey(
    "raw",
    encoder.encode(key),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"],
  );
  return base64Url(new Uint8Array(await crypto.subtle.sign("HMAC", cryptoKey, encoder.encode(value))));
}

export async function constantTimeSecretEqual(actual, expected) {
  if (typeof actual !== "string" || typeof expected !== "string") return false;
  const [actualHash, expectedHash] = await Promise.all([sha256(actual), sha256(expected)]);
  let difference = actualHash.length ^ expectedHash.length;
  const length = Math.max(actualHash.length, expectedHash.length);
  for (let index = 0; index < length; index += 1) {
    difference |= (actualHash.charCodeAt(index % actualHash.length) || 0)
      ^ (expectedHash.charCodeAt(index % expectedHash.length) || 0);
  }
  return difference === 0;
}

export function randomOpaque(bytes = 32) {
  return base64Url(crypto.getRandomValues(new Uint8Array(bytes)));
}

const BASE32 = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

export function generateLicenseCode() {
  const random = crypto.getRandomValues(new Uint8Array(20));
  let bits = 0;
  let value = 0;
  let output = "";
  for (const byte of random) {
    value = (value << 8) | byte;
    bits += 8;
    while (bits >= 5) {
      output += BASE32[(value >>> (bits - 5)) & 31];
      bits -= 5;
    }
  }
  return output.match(/.{1,4}/gu).join("-");
}

export function normalizeCode(value) {
  if (typeof value !== "string") return null;
  const normalized = value.toUpperCase().replace(/[\s-]/gu, "");
  return /^[A-Z2-9]{26,64}$/u.test(normalized) ? normalized : null;
}

export async function signLease(payload, privateKeyBase64) {
  const body = base64Url(encoder.encode(JSON.stringify(payload)));
  const unsigned = `MS2.${body}`;
  const key = await crypto.subtle.importKey(
    "pkcs8",
    base64Bytes(privateKeyBase64),
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const signature = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, encoder.encode(unsigned));
  return `${unsigned}.${base64Url(new Uint8Array(signature))}`;
}
