const PACKAGE_NAME = "cn.lvxu.travel";
const SHA256 = /^[0-9a-f]{64}$/iu;

/**
 * Produces the public Android update manifest once mounted at
 * GET /updates/latest.json by the worker entry point. Keeping this module
 * independent lets release automation supply the current APK values through
 * Worker variables without exposing any licensing data.
 */
export function updateManifestResponse(env) {
  const versionCode = Number.parseInt(env.UPDATE_VERSION_CODE ?? "", 10);
  const versionName = env.UPDATE_VERSION_NAME ?? "";
  const apkUrl = env.UPDATE_APK_URL ?? "";
  const sha256 = (env.UPDATE_APK_SHA256 ?? "").toLowerCase();
  const notes = env.UPDATE_NOTES ?? "";
  let validUrl = false;
  try { validUrl = new URL(apkUrl).protocol === "https:"; } catch {}
  if (!Number.isSafeInteger(versionCode) || versionCode < 1 || versionName.length > 100 || !validUrl || !SHA256.test(sha256) || notes.length > 2000) {
    return Response.json({ error: { code: "UPDATE_NOT_CONFIGURED", message: "Update manifest is unavailable" } }, { status: 404, headers: { "Cache-Control": "no-store" } });
  }
  return Response.json({ packageName: PACKAGE_NAME, versionCode, versionName, apkUrl, sha256, notes }, { headers: { "Cache-Control": "public, max-age=300" } });
}
