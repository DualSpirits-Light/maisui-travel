import { base64Bytes, base64Url } from './crypto.js';

const encoder = new TextEncoder();
const decode = value => base64Bytes(value.replaceAll('-', '+').replaceAll('_', '/'));

async function vaultKey(env, keyId) {
  if (typeof keyId !== 'string' || !/^[A-Za-z0-9_-]{1,64}$/u.test(keyId)) throw new Error('Invalid key ID');
  const keys = JSON.parse(env.CODE_ENCRYPTION_KEYS);
  const encoded = keys?.[keyId];
  if (typeof encoded !== 'string' || encoded === env.CODE_PEPPER || !/^[A-Za-z0-9+/]{43}=$/u.test(encoded)) throw new Error('Invalid key');
  const raw = base64Bytes(encoded);
  if (raw.length !== 32) throw new Error('Invalid key length');
  return crypto.subtle.importKey('raw', raw, 'AES-GCM', false, ['encrypt', 'decrypt']);
}

const context = (licenseId, keyId) => encoder.encode(`lvxu-license-code:v1:${licenseId}:${keyId}`);

export async function encryptLicenseCode(env, licenseId, code) {
  const keyId = env.CODE_ENCRYPTION_KEY_ID;
  const key = await vaultKey(env, keyId);
  const iv = crypto.getRandomValues(new Uint8Array(12));
  const encrypted = await crypto.subtle.encrypt({ name: 'AES-GCM', iv, additionalData: context(licenseId, keyId), tagLength: 128 }, key, encoder.encode(code));
  return { keyId, ciphertext: `v1.${base64Url(iv)}.${base64Url(new Uint8Array(encrypted))}` };
}

export async function decryptLicenseCode(env, license) {
  const key = await vaultKey(env, license.code_key_id);
  const [version, iv, ciphertext, extra] = license.code_ciphertext.split('.');
  if (version !== 'v1' || extra !== undefined || decode(iv).length !== 12) throw new Error('Invalid envelope');
  const plaintext = await crypto.subtle.decrypt({ name: 'AES-GCM', iv: decode(iv), additionalData: context(license.id, license.code_key_id), tagLength: 128 }, key, decode(ciphertext));
  return new TextDecoder('utf-8', { fatal: true }).decode(plaintext);
}
