const PACKAGE_NAME = "cn.lvxu.travel";
import { cloudflareUpdateReady, readUpdateConfig } from "./update-download.js";

/**
 * Produces the public Android update manifest once mounted at
 * GET /updates/latest.json by the worker entry point. Keeping this module
 * independent lets release automation supply the current APK values through
 * Worker variables without exposing any licensing data.
 */
export async function updateManifestResponse(request, env) {
  const config = readUpdateConfig(env);
  if (!config) {
    return Response.json({ error: { code: "UPDATE_NOT_CONFIGURED", message: "Update manifest is unavailable" } }, { status: 404, headers: { "Cache-Control": "no-store" } });
  }
  const ready = await cloudflareUpdateReady(env, config);
  const cloudflareUrl = ready ? new URL(`/updates/apk/${config.versionCode}.apk`, request.url).toString() : "";
  if (!config.githubApkUrl && !ready) {
    return Response.json({ error: { code: "UPDATE_NOT_READY", message: "No update download source is ready" } }, { status: 404, headers: { "Cache-Control": "no-store" } });
  }
  const manifest = {
    packageName: PACKAGE_NAME,
    versionCode: config.versionCode,
    versionName: config.versionName,
    apkUrl: config.githubApkUrl || cloudflareUrl,
    sha256: config.sha256,
    notes: config.notes,
  };
  if (config.githubApkUrl) manifest.githubApkUrl = config.githubApkUrl;
  if (ready) manifest.cloudflareApkUrl = cloudflareUrl;
  return Response.json(manifest, { headers: { "Cache-Control": "public, max-age=300" } });
}
