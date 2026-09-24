const PACKAGE_APK_TYPE = "application/vnd.android.package-archive";
const SHA256 = /^[0-9a-f]{64}$/iu;
const VERSION_CODE = /^[1-9][0-9]{0,8}$/u;
const MAX_APK_BYTES = 256 * 1024 * 1024;

function responseError(status, code, message, headers = {}) {
  return Response.json({ error: { code, message } }, {
    status,
    headers: { "Cache-Control": "no-store", ...headers },
  });
}

function canonicalGitHubReleaseUrl(value) {
  try {
    const url = new URL(value);
    return url.protocol === "https:"
      && url.hostname === "github.com"
      && !url.port
      && !url.username
      && !url.password
      && !url.search
      && !url.hash
      && /^\/DualSpirits-Light\/maisui-travel\/releases\/download\/[^/]+\/[^/]+\.apk$/u.test(url.pathname);
  } catch { return false; }
}

function maxApkBytes(env) {
  const value = Number.parseInt(env.UPDATE_MAX_APK_BYTES ?? "", 10);
  return Number.isSafeInteger(value) && value >= 1 && value <= MAX_APK_BYTES ? value : MAX_APK_BYTES;
}

export function updateObjectKey(versionCode) {
  return `updates/releases/${versionCode}.apk`;
}

export function readUpdateConfig(env) {
  const rawVersionCode = env.UPDATE_VERSION_CODE ?? "";
  const versionCode = Number(rawVersionCode);
  const versionName = env.UPDATE_VERSION_NAME ?? "";
  const githubApkUrl = env.UPDATE_GITHUB_APK_URL ?? env.UPDATE_APK_URL ?? "";
  const legacyApkUrl = env.UPDATE_APK_URL ?? githubApkUrl;
  const sha256 = (env.UPDATE_APK_SHA256 ?? "").toLowerCase();
  const notes = env.UPDATE_NOTES ?? "";
  if (!VERSION_CODE.test(rawVersionCode)
    || !Number.isSafeInteger(versionCode)
    || versionName.length > 100
    || (githubApkUrl !== "" && !canonicalGitHubReleaseUrl(githubApkUrl))
    || legacyApkUrl !== githubApkUrl
    || !SHA256.test(sha256)
    || notes.length > 2000) return null;
  return { versionCode, versionName, githubApkUrl, apkUrl: legacyApkUrl, sha256, notes, maxBytes: maxApkBytes(env) };
}

function metadataMatches(object, config) {
  const metadata = object?.customMetadata;
  return object
    && Number.isSafeInteger(object.size)
    && object.size > 0
    && object.size <= config.maxBytes
    && metadata?.versionCode === String(config.versionCode)
    && typeof metadata?.sha256 === "string"
    && metadata.sha256.toLowerCase() === config.sha256;
}

async function headRelease(env, config) {
  if (!env.UPDATES?.head) return { kind: "unavailable" };
  try {
    const object = await env.UPDATES.head(updateObjectKey(config.versionCode));
    if (!object) return { kind: "missing" };
    if (!metadataMatches(object, config)) return { kind: "mismatch" };
    return { kind: "ready", object };
  } catch {
    return { kind: "unavailable" };
  }
}

export async function cloudflareUpdateReady(env, config) {
  return (await headRelease(env, config)).kind === "ready";
}

function parseRange(value, size) {
  if (!value) return null;
  if (value.length > 100 || value.includes(",")) return { invalid: true };
  const match = /^bytes=(\d*)-(\d*)$/u.exec(value.trim());
  if (!match || (!match[1] && !match[2])) return { invalid: true };
  if (!match[1]) {
    const requested = Number(match[2]);
    if (!Number.isSafeInteger(requested) || requested < 1) return { invalid: true };
    const length = Math.min(requested, size);
    return { start: size - length, end: size - 1 };
  }
  const start = Number(match[1]);
  const requestedEnd = match[2] ? Number(match[2]) : size - 1;
  if (!Number.isSafeInteger(start) || !Number.isSafeInteger(requestedEnd) || start > requestedEnd || start >= size) return { invalid: true };
  return { start, end: Math.min(requestedEnd, size - 1) };
}

function downloadHeaders(config, size, range) {
  const headers = new Headers({
    "Accept-Ranges": "bytes",
    "Cache-Control": "public, max-age=300",
    "Content-Disposition": `attachment; filename="maisui-travel-${config.versionCode}.apk"`,
    "Content-Type": PACKAGE_APK_TYPE,
    "Content-Length": String(range ? range.end - range.start + 1 : size),
  });
  if (range) headers.set("Content-Range", `bytes ${range.start}-${range.end}/${size}`);
  return headers;
}

/**
 * Serves exactly the configured release APK. The object key is derived from
 * the configured version code and is never accepted from a public request.
 */
export async function updateDownloadResponse(request, env) {
  const config = readUpdateConfig(env);
  if (!config) return responseError(404, "UPDATE_NOT_CONFIGURED", "Update download is unavailable");
  const path = new URL(request.url).pathname;
  const match = /^\/updates\/apk\/([1-9][0-9]{0,8})\.apk$/u.exec(path);
  if (!match || Number(match[1]) !== config.versionCode) return responseError(404, "UPDATE_NOT_FOUND", "Update release not found");
  if (request.method !== "GET" && request.method !== "HEAD") return responseError(405, "METHOD_NOT_ALLOWED", "Only GET and HEAD are supported", { Allow: "GET, HEAD" });

  const release = await headRelease(env, config);
  if (release.kind === "missing") return responseError(404, "UPDATE_NOT_READY", "Update release is not ready");
  if (release.kind === "mismatch") return responseError(409, "UPDATE_METADATA_MISMATCH", "Update release metadata does not match the manifest");
  if (release.kind !== "ready") return responseError(503, "UPDATE_SOURCE_UNAVAILABLE", "Update download source is unavailable");

  const range = parseRange(request.headers.get("Range"), release.object.size);
  if (range?.invalid) return responseError(416, "RANGE_NOT_SATISFIABLE", "Requested byte range is invalid", { "Content-Range": `bytes */${release.object.size}` });
  const headers = downloadHeaders(config, release.object.size, range);
  if (request.method === "HEAD") return new Response(null, { status: range ? 206 : 200, headers });
  try {
    const object = range
      ? await env.UPDATES.get(updateObjectKey(config.versionCode), { range: { offset: range.start, length: range.end - range.start + 1 } })
      : await env.UPDATES.get(updateObjectKey(config.versionCode));
    if (!object?.body) return responseError(404, "UPDATE_NOT_READY", "Update release is not ready");
    return new Response(object.body, { status: range ? 206 : 200, headers });
  } catch {
    return responseError(503, "UPDATE_SOURCE_UNAVAILABLE", "Update download source is unavailable");
  }
}
