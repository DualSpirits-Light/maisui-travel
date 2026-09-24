package cn.lvxu.travel;

import android.content.Context;
import android.content.ContextWrapper;
import java.io.*;
import java.net.URI;
import java.security.MessageDigest;
import java.util.Locale;
import okhttp3.*;

/** Exercises the production APK hash, package, version, and signer checks on real signed APKs. */
final class StageNineRealApkTest {
    private static final String APK_ASSET = "update-fixture.apk";
    private static final String WRONG_SIGNER_ASSET = "update-wrong-signer.apk";
    private static final String GITHUB_URL = "https://github.com/DualSpirits-Light/maisui-travel/releases/download/v9.9.9/maisui.apk";
    private static final String CLOUDFLARE_URL = "https://license.zjm0929.cn/updates/apk/9999.apk";

    private StageNineRealApkTest() {}

    static int run(Context target, Context tests) throws Exception {
        byte[] validApk = asset(tests, APK_ASSET);
        byte[] wrongSignerApk = asset(tests, WRONG_SIGNER_ASSET);
        if (validApk == null && wrongSignerApk == null) return 0;
        if (validApk == null || wrongSignerApk == null) throw new AssertionError("both real APK fixtures must be packaged together");

        File cache = new File(target.getCacheDir(), "stage9-real-apk-fixtures");
        Context isolated = new ContextWrapper(target) {
            @Override public Context getApplicationContext() { return this; }
            @Override public File getCacheDir() { return cache; }
        };
        deleteTree(cache);
        if (!cache.mkdirs()) throw new IOException("cannot create isolated update test cache");
        try {
            String hash = sha256(validApk);
            UpdateService service = service(isolated, validApk);
            UpdateService.Release release = release(9999, hash);
            File downloaded = service.downloadAndVerify(release, UpdateService.Route.CLOUDFLARE, null, new UpdateService.Cancellation());
            check(downloaded.isFile() && downloaded.length() == validApk.length, "real same-signer version 9999 APK downloads and verifies");
            check(service.verifyDownloaded(downloaded, release).equals(downloaded), "persisted real APK passes production revalidation");

            UpdateService.Cancellation cancellation = new UpdateService.Cancellation();
            boolean cancelled = false;
            try {
                service.downloadAndVerify(release, UpdateService.Route.CLOUDFLARE, new UpdateService.Progress() {
                    @Override public void onProgress(long done, long total) { cancellation.cancel(); }
                }, cancellation);
            } catch (InterruptedIOException expected) { cancelled = true; }
            check(cancelled, "cancellation interrupts a fixture download");
            check(!part(cache, 9999).exists(), "cancellation removes the partial APK");

            boolean badHash = rejected(service(isolated, validApk), release(9999, "0000000000000000000000000000000000000000000000000000000000000000"));
            check(badHash, "a real APK with the wrong release hash is rejected");
            check(!part(cache, 9999).exists(), "wrong-hash rejection removes the partial APK");

            boolean badVersion = rejected(service(isolated, validApk), release(10000, hash));
            check(badVersion, "a real APK with the wrong release version is rejected");
            check(!part(cache, 10000).exists(), "wrong-version rejection removes the partial APK");

            boolean badSigner = rejected(service(isolated, wrongSignerApk), release(9999, sha256(wrongSignerApk)));
            check(badSigner, "a real APK signed by a different certificate is rejected");
            check(!part(cache, 9999).exists(), "wrong-signer rejection removes the partial APK");
            return 8;
        } finally {
            deleteTree(cache);
        }
    }

    private static UpdateService service(Context context, byte[] apk) {
        OkHttpClient client = new OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .addInterceptor(chain -> {
                if (!CLOUDFLARE_URL.equals(chain.request().url().toString())) throw new IOException("unexpected fixture URL");
                return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                    .body(ResponseBody.create(MediaType.parse("application/vnd.android.package-archive"), apk)).build();
            }).build();
        return new UpdateService(context, new AppPrefs(context), client);
    }

    private static UpdateService.Release release(int versionCode, String hash) {
        return new UpdateService.Release(UpdateService.State.AVAILABLE, versionCode, "9.9.9", "Stage Nine APK fixture", GITHUB_URL, CLOUDFLARE_URL, hash);
    }

    private static boolean rejected(UpdateService service, UpdateService.Release release) throws Exception {
        try {
            service.downloadAndVerify(release, UpdateService.Route.CLOUDFLARE, null, new UpdateService.Cancellation());
            return false;
        } catch (IOException expected) { return true; }
    }

    private static byte[] asset(Context context, String name) throws IOException {
        try (InputStream input = context.getAssets().open(name); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            for (int count; (count = input.read(buffer)) != -1;) output.write(buffer, 0, count);
            return output.toByteArray();
        } catch (FileNotFoundException missing) { return null; }
    }

    private static File part(File cache, int version) { return new File(new File(cache, "updates"), "maisui-" + version + ".apk.part"); }

    private static String sha256(byte[] bytes) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder text = new StringBuilder();
        for (byte value : digest) text.append(String.format(Locale.ROOT, "%02x", value));
        return text.toString();
    }

    private static void deleteTree(File file) {
        if (!file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteTree(child);
        file.delete();
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
