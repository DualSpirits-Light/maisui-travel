# Maisui License Worker

Cloudflare Worker + D1 authorization-code service for `maisui-travel`. It issues short-lived, offline-verifiable RSA-signed leases. It has no end-user account system and stores neither raw license codes nor raw device credentials.

## Security and lifecycle

- Admin creation returns a 160-bit license code once. Formatting hyphens and letter case are ignored during activation. D1 stores `HMAC-SHA256(CODE_PEPPER, normalizedCode)` only.
- A client creates and persists a random installation UUID as `deviceId`. Successful activation returns a 256-bit `deviceSecret`; D1 stores its SHA-256 hash only.
- Re-activating the same code on the same `deviceId` explicitly rotates its credential. The old secret stops working. A code used from another device never reveals an existing device's secret.
- Device admission is one conditional SQLite `INSERT ... SELECT ... ON CONFLICT` statement. The count check and insert are atomic inside SQLite, so concurrent first activations cannot exceed `maxDevices`.
- A license is permanent when `expiresAt` is `null`. Otherwise, its signed lease expiry is clipped to the license expiry.
- Revocation blocks activation and refresh. Unbinding deletes one device binding and frees a slot.
- Requests are limited by atomic per-minute D1 counters keyed by a peppered client-IP digest. Old buckets are sampled for cleanup. Defaults are 30 public and 60 admin requests per IP per minute.
- Production requests must use HTTPS. Plain HTTP is accepted only for `localhost`, `127.0.0.1`, and `[::1]` development URLs.
- JSON bodies are limited to 4096 bytes. The Worker does not log bodies, license tokens, codes, or device secrets.

## API protocol

All bodies use `Content-Type: application/json`. Success and error responses include `Cache-Control: no-store`. Unix times are whole seconds.

### `POST /v1/activate`

Request:

```json
{"code":"ABCD-EFGH-JKLM-NPQR-STUV-WXYZ-2345-6789","deviceId":"f92b17e0-a5f7-4a97-9102-76147e9aa152"}
```

Response `200`:

```json
{"token":"MS2.<payload>.<signature>","licenseId":"<uuid>","deviceSecret":"<opaque credential>"}
```

### `POST /v1/refresh`

Request:

```json
{"licenseId":"<uuid>","deviceId":"f92b17e0-a5f7-4a97-9102-76147e9aa152","deviceSecret":"<opaque credential>"}
```

Response `200`:

```json
{"token":"MS2.<payload>.<signature>","licenseId":"<uuid>"}
```

The client should refresh before lease expiry and retain a currently valid lease through temporary network failures.

### Lease token

The format is `MS2.base64url(payload JSON UTF-8).base64url(signature)`. The signature is RSA SHA-256 with PKCS#1 v1.5 over the ASCII bytes of `MS2.<payload>`. The decoded payload is:

```json
{"product":"maisui-travel","licenseId":"<uuid>","deviceId":"<uuid>","subject":"<display subject>","issuedAt":1700000000,"expiresAt":1700604800}
```

Clients must embed the matching RSA public key, verify the signature before reading claims, require `product === "maisui-travel"`, match `deviceId`, and reject an expired lease.

## Admin API

Send `Authorization: Bearer <ADMIN_TOKEN>`.

- `POST /admin/licenses` accepts `{ "subject": string, "expiresAt": number|null, "maxDevices": 1..100, "leaseDays": 1..7 }`. The last three fields are optional. It returns the raw `code` once with the license record. The seven-day cap matches the current Android client's offline lease ceiling.
- `GET /admin/licenses` lists up to 500 records and device counts. It never returns raw codes, code HMACs, or device secrets/hashes.
- `GET /admin/licenses/:id/devices` lists bound device IDs and their created/updated times for unbinding.
- `POST /admin/licenses/:id/revoke` permanently marks a license revoked. Repeating it is safe.
- `POST /admin/licenses/:id/devices/:deviceId/unbind` removes one device binding.

## Stable errors

Errors have the shape `{ "error": { "code": "INVALID_CODE", "message": "..." } }`.

| HTTP | Code | Meaning |
|---:|---|---|
| 400 | `INVALID_REQUEST` | Invalid JSON or field/path value |
| 400 | `INVALID_CODE` | Malformed or unknown activation code; the response does not distinguish them |
| 400 | `HTTPS_REQUIRED` | Non-local request used plain HTTP |
| 401 | `INVALID_CREDENTIAL` | Unknown license/device or wrong device secret |
| 401 | `ADMIN_UNAUTHORIZED` | Missing or invalid admin bearer token |
| 403 | `LICENSE_REVOKED` | Known license was revoked |
| 403 | `LICENSE_EXPIRED` | Known license reached its permanent expiry |
| 404 | `NOT_FOUND`, `LICENSE_NOT_FOUND`, `DEVICE_NOT_FOUND` | Route or admin target absent |
| 409 | `DEVICE_LIMIT_REACHED` | All allowed device slots are occupied |
| 413 | `BODY_TOO_LARGE` | JSON body exceeds 4096 bytes |
| 415 | `UNSUPPORTED_MEDIA_TYPE` | Content type is not JSON |
| 429 | `RATE_LIMITED` | Per-minute limit exceeded; response includes `Retry-After: 60` |
| 500 | `INTERNAL_ERROR` | Unexpected server failure without internal details |
| 503 | `SERVER_MISCONFIGURED` | Required Worker binding/secret is absent |

## Setup

Requirements: a Cloudflare account, Wrangler authentication, and Node.js 20 or newer. No credentials belong in this repository.

1. Install tools with `npm install`.
2. Create a D1 database: `npx wrangler d1 create maisui-license`.
3. Replace `REPLACE_WITH_D1_DATABASE_ID` in `wrangler.toml` with the returned ID.
4. Apply the schema locally with `npm run db:migrate:local`, or to the chosen remote database with `npm run db:migrate:remote`.
5. Generate an RSA key pair. Put the base64-encoded PKCS#8 DER private key in `SIGNING_PRIVATE_KEY_PKCS8`; embed/export the corresponding public key in the Android client.
6. Add secrets with `npx wrangler secret put ADMIN_TOKEN`, `npx wrangler secret put CODE_PEPPER`, and `npx wrangler secret put SIGNING_PRIVATE_KEY_PKCS8`. Use independent random values of at least 32 bytes for the first two.
7. Run `npm test` and `npm run dev`. Deploy only after replacing the database ID and configuring secrets.

For local development, Wrangler can load the three secrets from an untracked `.dev.vars` file. Do not commit that file. Cloudflare's D1 binding, prepared-statement, batch semantics, and secret configuration are documented at [D1 Worker API](https://developers.cloudflare.com/d1/worker-api/), [prepared statements](https://developers.cloudflare.com/d1/worker-api/prepared-statements/), [D1 database/batch](https://developers.cloudflare.com/d1/worker-api/d1-database/), and [Workers secrets](https://developers.cloudflare.com/workers/configuration/secrets/).

## Tests and limitations

`npm test` verifies code entropy/normalization, token encoding and RSA verification, one-way secret hashing, bounded streaming JSON, schema application, and concurrent conditional admission using the installed native SQLite CLI. Full handler integration tests use Node's real in-memory SQLite engine and exercise admin authentication and issuance, activation, refresh, credential rotation, device limits, revocation, unbinding, expiry clipping, seven-day enforcement, rate limiting, and protection of stored/listed secrets. The separate process concurrency test is skipped when the SQLite CLI is unavailable.

Rate limiting is intentionally small and self-contained; high-volume deployments may prefer Cloudflare Rate Limiting or a Durable Object. D1 replication and Worker availability determine online activation/refresh availability, while already issued leases remain locally verifiable until expiry. Key rotation currently requires clients to ship the matching public key; add a token key ID and a client trust set before operating multiple signing keys.
