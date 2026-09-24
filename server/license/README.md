# Maisui License Worker 0.3.1

Cloudflare Worker + D1 + R2 backend for `maisui-travel`. It provides the browser license console, signed license leases, three-day itinerary share codes, and the Android update manifest. There is no end-user account system.

## Routes

- `GET /console` serves the unified administration portal with password login, license management, donation records, and account settings. See [administrator setup and recovery](ADMIN-ACCOUNTS.md).
- `POST /v1/activate` activates a license on one installation.
- `POST /v1/refresh` renews that installation's signed lease.
- `POST /v1/shares` uploads a ZIP share package.
- `GET /v1/shares/:code` downloads an unexpired share package.
- `GET /updates/latest.json` returns Android release metadata.

Production requests must use HTTPS. Plain HTTP is accepted only on `localhost`, `127.0.0.1`, and `[::1]` for development.

## License security and lifecycle

- Creation returns a 160-bit license code. Formatting hyphens and letter case are ignored during activation. D1 keeps `HMAC-SHA256(CODE_PEPPER, normalizedCode)` for lookup and an AES-256-GCM encrypted copy for authenticated, audited viewing. Historical HMAC-only codes cannot be recovered. See [code vault, reissue, key rotation and migration](LICENSE-CODES.md).
- The app creates a random installation UUID as `deviceId`. Successful activation returns a 256-bit `deviceSecret`; D1 stores only its SHA-256 hash. Re-activating on the same installation rotates this credential.
- Device admission uses one conditional SQLite statement, so concurrent activations cannot exceed `maxDevices`.
- A license expiry of `null` makes the authorization permanent. A Unix timestamp makes it finite and can be edited before or after expiry. The administrator can switch in either direction.
- `offlineSeconds` controls v0.3.1 leases: `null` permits permanent offline use, `0` requires online validation, and a positive integer permits that many seconds offline. Online-only leases are signed for 60 seconds and require a v0.3.1 client.
- Freezing blocks activation and refresh. A finite license saves its remaining authorization duration; restoring starts that duration from the restoration time. A permanent license stays permanent. Repeated freeze/restore calls are safe.
- Revocation blocks activation and refresh. Restore-use clears revocation and archiving but preserves expiry and freeze status. Only revoked licenses can be archived or deleted. Unarchiving restores visibility without clearing revocation. Deletion removes the license and device bindings, retains code-view audit records, and cannot be undone.
- Unbinding removes one device association and frees a slot.
- Public activation/refresh and admin requests use atomic per-minute D1 counters keyed by a peppered IP digest. Defaults are 30 public and 60 admin requests per IP per minute.
- JSON bodies are limited to 4096 bytes. The Worker does not log request bodies, codes, tokens, or device secrets.

## License protocol

License and admin request bodies use `Content-Type: application/json`. Unix times are whole seconds.

### `POST /v1/activate`

```json
{"code":"ABCD-EFGH-JKLM-NPQR-STUV-WXYZ-2345-6789","deviceId":"f92b17e0-a5f7-4a97-9102-76147e9aa152","clientVersion":31}
```

Response `200`:

```json
{"token":"MS3.<payload>.<signature>","licenseId":"<uuid>","deviceSecret":"<opaque credential>"}
```

### `POST /v1/refresh`

```json
{"licenseId":"<uuid>","deviceId":"f92b17e0-a5f7-4a97-9102-76147e9aa152","deviceSecret":"<opaque credential>","clientVersion":31}
```

Response `200`:

```json
{"token":"MS3.<payload>.<signature>","licenseId":"<uuid>"}
```

Clients with `clientVersion >= 31` receive `MS3` tokens containing `offlineSeconds`. Older clients receive `MS2` with a maximum seven-day lease, further limited by a configured positive offline duration. Older clients cannot use an online-only license and receive `CLIENT_UPDATE_REQUIRED` without rotating their credential.

Tokens are RSA SHA-256/PKCS#1 v1.5 signatures over the ASCII bytes of `<prefix>.<payload>`. A typical MS3 payload is:

```json
{"product":"maisui-travel","licenseId":"<uuid>","deviceId":"<uuid>","subject":"<display subject>","issuedAt":1700000000,"expiresAt":1700604800,"offlineSeconds":604800}
```

Clients must verify the signature before trusting claims, require `product === "maisui-travel"`, match `deviceId`, and enforce the signed expiry and offline policy.

## Admin API

After initialization, log in through `/console` and use its protected same-origin session cookie. See [administrator setup and recovery](ADMIN-ACCOUNTS.md). The old `ADMIN_TOKEN` works only before the password account is initialized and must then be retired.

- `POST /admin/licenses` accepts `{ "subject": string, "expiresAt": number|null, "maxDevices": 1..100, "leaseDays": 1..7, "offlineSeconds": number|null }`. Optional `offlineSeconds` defaults to `leaseDays * 86400`; `leaseDays` remains for older clients. The raw code appears only in this response.
- `GET /admin/licenses` lists up to 500 active-list records. Add `?archived=1` for archived records. Secrets and code HMACs are never returned.
- `PATCH /admin/licenses/:id` accepts `expiresAt` and/or `offlineSeconds`, including `null` for permanent values.
- `GET /admin/licenses/:id/devices` lists bound installations.
- `POST /admin/licenses/:id/devices/:deviceId/unbind` removes a binding.
- `POST /admin/licenses/:id/freeze` pauses a license.
- `POST /admin/licenses/:id/unfreeze` restores it and resumes its saved remaining duration.
- `POST /admin/licenses/:id/revoke` permanently marks a license revoked.
- `POST /admin/licenses/:id/archive` hides a revoked license.
- `POST /admin/licenses/:id/restore` returns an archived, still-revoked license to the active list.
- `DELETE /admin/licenses/:id` permanently deletes a revoked license and its device bindings.
- `GET /admin/donations` and `POST /admin/donations` list and create donation records. `PATCH` or `DELETE /admin/donations/:id` edits or deletes one record. New records are private until explicitly marked public.
- `GET /v1/donations` is public and returns only the display fields of records marked public; it never returns private records or internal IDs.

## Three-day share codes

Share packages are opaque ZIP files. The app decides whether they contain a clean guide, full memories, source links, modification records, and optional pictures; the Worker stores and returns the bytes without inspecting itinerary content.

### Upload

`POST /v1/shares` requires `Content-Type: application/zip` and an exact `Content-Length` from 1 byte through 32 MiB. Success returns:

```json
{"code":"MS31-<32 lowercase hexadecimal characters>","expiresAt":1700259200,"size":12345}
```

Capacity is reserved atomically before R2 upload. Limits are:

- 5 uploads per client IP per UTC day;
- 100 service-wide uploads per UTC day;
- 5,000 service-wide uploads per UTC month;
- 2 GiB of tracked live or pending packages;
- 32 MiB per package.

A full service returns `507`; quota exhaustion returns `429`. Failed R2 writes remove the partial object and reservation. If deletion also fails, D1 intentionally retains the reservation so scheduled cleanup can retry and storage never becomes untracked.

### Download and expiry

`GET /v1/shares/MS31-<id>` returns `application/zip`, `Content-Length`, and `X-Share-Expires`. The `MS31-` prefix is optional at the HTTP route. Successful, missing, guessed, and expired reads all count toward the 100,000-read service-wide monthly ceiling.

Every code becomes unusable exactly 72 hours after creation. Expired and unknown codes return `410`. A Worker cron runs each minute and removes up to 100 expired R2 objects and D1 rows per invocation; requesting an expired code also schedules cleanup. Configure an R2 lifecycle expiration rule as a secondary safeguard because lifecycle deletion may lag behind logical expiry.

Share endpoints use compact errors such as `{ "error": "口令不存在或已过期（有效期为 3 天）" }` and `Cache-Control: no-store`.

## Update manifest

`GET /updates/latest.json` returns:

```json
{"packageName":"cn.lvxu.travel","versionCode":11,"versionName":"0.5.0","apkUrl":"https://github.com/DualSpirits-Light/maisui-travel/releases/download/v0.5.0/maisui-travel-0.5.0.apk","githubApkUrl":"https://github.com/DualSpirits-Light/maisui-travel/releases/download/v0.5.0/maisui-travel-0.5.0.apk","cloudflareApkUrl":"https://license.example/updates/apk/11.apk","sha256":"<64 hexadecimal characters>","notes":"<release notes>"}
```

`apkUrl` remains the compatibility field: it uses the canonical GitHub Release URL when one is configured, or the ready Cloudflare URL otherwise. `githubApkUrl` is optional and appears only when a GitHub URL is configured. `UPDATE_GITHUB_APK_URL` must be the canonical raw GitHub URL `https://github.com/DualSpirits-Light/maisui-travel/releases/download/<tag>/<asset>.apk`; a proxy URL is rejected. Android applies its configured, trusted public gh-proxy prefix to this canonical value when the user selects the accelerated GitHub route. `UPDATE_APK_URL` is accepted only as a legacy alias and, when both are set, must be identical. The manifest also needs `UPDATE_VERSION_CODE`, `UPDATE_VERSION_NAME`, `UPDATE_APK_SHA256`, and `UPDATE_NOTES`.

`cloudflareApkUrl` is included only after the `UPDATES` R2 binding contains the matching object. It is deliberately omitted when the bucket binding is absent, the object is missing, oversized, or its metadata does not match; this keeps a partially staged mirror out of client choices. Missing or invalid manifest configuration returns `404 UPDATE_NOT_CONFIGURED`; valid configuration with neither a GitHub URL nor a ready Cloudflare APK returns `404 UPDATE_NOT_READY`.

`GET` and `HEAD` on `/updates/apk/:versionCode.apk` serve only the current configured version. The Worker derives the fixed object key `updates/releases/:versionCode.apk`; it never accepts an R2 key from the URL, query string, or headers. It streams the object, supports one standard byte range, sends APK content and length headers, and caps a release at 256 MiB by default (`UPDATE_MAX_APK_BYTES` can lower that cap). Download and HEAD requests have a peppered-IP, per-minute D1 limit of 30 by default (`UPDATES_RATE_LIMIT_PER_MINUTE`, 1–120). Responses cache publicly for at most five minutes.

Before the eventual Stage 10 release, create and bind the dedicated `maisui-updates` bucket without a public bucket domain. Upload the APK only to `updates/releases/<versionCode>.apk`, with R2 custom metadata `versionCode=<versionCode>` and lowercase `sha256=<APK SHA-256>`. First download the public GitHub/gh-proxy candidate and the R2 object in a controlled environment, verify their bytes against the same SHA-256 and the signed APK identity, then stage the Worker variables in an untracked local `.dev.vars` such as:

```ini
UPDATE_VERSION_CODE=11
UPDATE_VERSION_NAME=0.5.0
UPDATE_GITHUB_APK_URL=https://github.com/DualSpirits-Light/maisui-travel/releases/download/v0.5.0/maisui-travel-0.5.0.apk
UPDATE_APK_SHA256=<64 lowercase hexadecimal characters>
UPDATE_NOTES=<release notes>
UPDATE_MAX_APK_BYTES=268435456
UPDATES_RATE_LIMIT_PER_MINUTE=30
```

Do not upload an APK, set release variables remotely, deploy this Worker, create a GitHub Release, or restore deleted historical Releases during Stage 9. Those actions remain together in the approved Stage 10 rollout after the real-network checks.

## Stable license errors

License/admin errors use `{ "error": { "code": "INVALID_CODE", "message": "..." } }`.

| HTTP | Code | Meaning |
|---:|---|---|
| 400 | `INVALID_REQUEST` | Invalid JSON or field/path value |
| 400 | `INVALID_CODE` | Malformed or unknown code; the response does not distinguish them |
| 400 | `HTTPS_REQUIRED` | A non-local request used HTTP |
| 401 | `INVALID_CREDENTIAL` | Unknown license/device or incorrect device secret |
| 401 | `ADMIN_UNAUTHORIZED` | Missing or invalid admin bearer token |
| 403 | `CLIENT_UPDATE_REQUIRED` | An old app attempted to use an online-only license |
| 403 | `LICENSE_REVOKED` | License was revoked |
| 403 | `LICENSE_FROZEN` | License is frozen |
| 403 | `LICENSE_EXPIRED` | Authorization expiry was reached |
| 404 | `NOT_FOUND`, `LICENSE_NOT_FOUND`, `DEVICE_NOT_FOUND` | Route or target absent |
| 409 | `DEVICE_LIMIT_REACHED` | All device slots are occupied |
| 409 | `LICENSE_NOT_REVOKED` | Archive or deletion was attempted before revocation |
| 413 | `BODY_TOO_LARGE` | JSON body exceeds 4096 bytes |
| 415 | `UNSUPPORTED_MEDIA_TYPE` | License/admin body is not JSON |
| 429 | `RATE_LIMITED` | Per-minute limit exceeded; includes `Retry-After: 60` |
| 500 | `INTERNAL_ERROR` | Unexpected failure without internal details |
| 503 | `SERVER_MISCONFIGURED` | Required binding or secret is absent |

## Setup and deployment

Requirements are a Cloudflare account, Node.js 20 or newer, and Wrangler authentication. Do not put credentials in this repository.

1. Run `npm install`.
2. Create a D1 database with `npx wrangler d1 create maisui-license` and configure its ID in `wrangler.toml`.
3. Create an R2 bucket and bind it as `SHARES`. Add a three-day object lifecycle rule as a backup to the minute cron.
4. Apply all migrations with `npm run db:migrate:local` or `npm run db:migrate:remote`.
5. Generate an RSA key pair. Store the base64 PKCS#8 DER private key in `SIGNING_PRIVATE_KEY_PKCS8` and ship the matching public key in Android.
6. Set `CODE_PEPPER` and `SIGNING_PRIVATE_KEY_PKCS8` with `npx wrangler secret put`. Set a new independent `ADMIN_RECOVERY_TOKEN` of at least 32 random characters, then initialize the password account through `/console` as described in [administrator setup and recovery](ADMIN-ACCOUNTS.md). A pre-existing `ADMIN_TOKEN` is transitional only and should be removed after initialization.
7. Set update variables and configure the `* * * * *` cron trigger.
8. Run `npm test` and `npm run dev`; deploy only after verifying bindings, variables, migrations, and secrets.

Wrangler can load local secrets from an untracked `.dev.vars`. References: [D1 Worker API](https://developers.cloudflare.com/d1/worker-api/), [prepared statements](https://developers.cloudflare.com/d1/worker-api/prepared-statements/), [R2 pricing](https://developers.cloudflare.com/r2/pricing/), [R2 object lifecycles](https://developers.cloudflare.com/r2/buckets/object-lifecycles/), and [Workers secrets](https://developers.cloudflare.com/workers/configuration/secrets/).

## Tests and operating limits

`npm test` covers normalization and signing, bounded JSON parsing, schema and atomic admission, admin UI behavior, MS2/MS3 issuance, expiry edits, freeze/restore timing, revocation, archive/restore/delete, unbinding, rate limits, share quotas, capacity reservation, R2 failure recovery, 72-hour expiry, and scheduled cleanup. The separate process concurrency test is skipped when SQLite CLI is unavailable.

D1 replication and Worker availability determine online activation and refresh. A device with a valid finite offline lease continues until that lease expires. A permanent offline lease cannot learn about a later freeze or revocation until it reconnects; use finite or online-only policy where prompt remote enforcement matters. RSA key rotation requires clients to ship the matching public key; introduce a token key ID and client trust set before operating multiple signing keys.
