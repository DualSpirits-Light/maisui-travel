package cn.lvxu.travel;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Looper;

import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;

/** Android integration coverage for the cloud-license client. Called by IntegrationInstrumentation. */
public final class CloudLicenseTests {
    private static final String PREFS = "cloud-license-v1";
    private static final String ENDPOINT = "https://license.test";

    private CloudLicenseTests() {}

    public static int run(Context context) throws Exception {
        if (Looper.myLooper() == Looper.getMainLooper())
            throw new AssertionError("CloudLicenseTests must run off the UI thread");
        SharedPreferences preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        Map<String, ?> saved = new HashMap<>(preferences.getAll());
        int checks = 0;
        try {
            check(preferences.edit().clear().commit(), "test preferences cleared"); checks++;
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair pair = generator.generateKeyPair();
            String publicKey = java.util.Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());
            Fixture fixture = new Fixture(pair);
            OkHttpClient client = new OkHttpClient.Builder().addInterceptor(fixture).build();
            CloudLicenseService service = new CloudLicenseService(context, client, ENDPOINT, publicKey);

            check(service.configured(), "injected public configuration accepted"); checks++;
            check(!service.hasLicense() && !service.isValid(), "fresh install has no cloud license"); checks++;
            String device = service.deviceId();
            check(device.equals(service.deviceId()), "device id is stable"); checks++;

            check("Test User".equals(service.activate("GOOD-CODE")), "activation returns subject"); checks++;
            check(service.hasLicense() && service.isValid(), "activation persists a valid lease"); checks++;
            check("Test User".equals(service.subject()), "valid subject is readable"); checks++;
            CloudLicenseService second = new CloudLicenseService(context, client, ENDPOINT, publicKey);
            check(second.hasLicense() && second.isValid() && device.equals(second.deviceId()),
                    "new service instance reads encrypted persisted state"); checks++;
            String rawPreferences = preferences.getAll().toString();
            check(!rawPreferences.contains(Fixture.SECRET), "credential preferences contain no plaintext secret"); checks++;
            String exported = new AppPrefs(context).exportJson().toString().toLowerCase(java.util.Locale.ROOT);
            check(!exported.contains("cloud") && !exported.contains(Fixture.SECRET.toLowerCase(java.util.Locale.ROOT)),
                    "normal preference export excludes cloud authorization"); checks++;

            fixture.mode = Mode.WRONG_DEVICE;
            expectFailure(() -> service.activate("WRONG-DEVICE"), "wrong-device token rejected"); checks++;
            fixture.mode = Mode.WRONG_PRODUCT;
            expectFailure(() -> service.activate("WRONG-PRODUCT"), "wrong-product token rejected"); checks++;
            fixture.mode = Mode.BAD_SIGNATURE;
            expectFailure(() -> service.activate("BAD-SIGNATURE"), "bad signature rejected"); checks++;
            fixture.mode = Mode.EXPIRED;
            expectFailure(() -> service.activate("EXPIRED"), "expired token rejected"); checks++;
            check(service.isValid(), "rejected activations do not replace valid state"); checks++;

            fixture.mode = Mode.REFRESH_LICENSE_MISMATCH;
            expectFailure(service::refresh, "refresh response license id mismatch rejected"); checks++;
            check(service.isValid(), "mismatched refresh preserves valid lease"); checks++;
            fixture.mode = Mode.STATUS_503;
            expectFailure(service::refresh, "503 reported as failure"); checks++;
            check(service.isValid(), "503 preserves valid lease"); checks++;
            fixture.mode = Mode.IO_FAILURE;
            expectFailure(service::refresh, "transport error reported as failure"); checks++;
            check(service.isValid(), "transport error preserves valid lease"); checks++;

            fixture.mode = Mode.BLOCK_REFRESH;
            fixture.entered = new CountDownLatch(1);
            fixture.release = new CountDownLatch(1);
            AtomicReference<Throwable> networkFailure = new AtomicReference<>();
            Thread network = new Thread(() -> {
                try { service.refresh(); } catch (Throwable t) { networkFailure.set(t); }
            }, "cloud-license-test-network");
            network.start();
            check(fixture.entered.await(2, TimeUnit.SECONDS), "refresh reached paused network"); checks++;
            long start = System.nanoTime();
            check(service.hasLicense() && service.isValid(), "read state remains available during network");
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            check(elapsedMs < 750, "network does not hold state lock"); checks += 2;
            fixture.release.countDown();
            network.join(3000);
            check(!network.isAlive() && networkFailure.get() == null, "paused refresh completes cleanly"); checks++;

            fixture.mode = Mode.STATUS_401;
            expectFailure(service::refresh, "401 reported as failure"); checks++;
            check(service.hasLicense() && !service.isValid(), "401 clears token but retains cloud state"); checks++;
            fixture.mode = Mode.NORMAL;
            service.activate("RESTORE-AFTER-401");
            fixture.mode = Mode.STATUS_403;
            expectFailure(service::refresh, "403 reported as failure"); checks++;
            check(service.hasLicense() && !service.isValid(), "403 clears token but retains cloud state"); checks++;
            return checks;
        } finally {
            restore(preferences, saved);
        }
    }

    private static void restore(SharedPreferences preferences, Map<String, ?> saved) {
        SharedPreferences.Editor editor = preferences.edit().clear();
        for (Map.Entry<String, ?> entry : saved.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String) editor.putString(entry.getKey(), (String) value);
            else if (value instanceof Boolean) editor.putBoolean(entry.getKey(), (Boolean) value);
            else if (value instanceof Long) editor.putLong(entry.getKey(), (Long) value);
            else if (value instanceof Integer) editor.putInt(entry.getKey(), (Integer) value);
            else if (value instanceof Float) editor.putFloat(entry.getKey(), (Float) value);
            else if (value instanceof java.util.Set)
                editor.putStringSet(entry.getKey(), new java.util.HashSet<>((java.util.Set<String>) value));
        }
        if (!editor.commit()) throw new AssertionError("cloud license test preferences were not restored");
    }

    private static void expectFailure(ThrowingRunnable action, String message) throws Exception {
        try { action.run(); }
        catch (Exception expected) { return; }
        throw new AssertionError(message);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private interface ThrowingRunnable { void run() throws Exception; }

    private enum Mode {
        NORMAL, WRONG_DEVICE, WRONG_PRODUCT, BAD_SIGNATURE, EXPIRED,
        REFRESH_LICENSE_MISMATCH, STATUS_503, IO_FAILURE, STATUS_401, STATUS_403, BLOCK_REFRESH
    }

    private static final class Fixture implements Interceptor {
        static final String SECRET = "test-device-secret-never-plaintext";
        final KeyPair pair;
        volatile Mode mode = Mode.NORMAL;
        volatile CountDownLatch entered, release;

        Fixture(KeyPair pair) { this.pair = pair; }

        @Override public Response intercept(Chain chain) throws IOException {
            Request request = chain.request();
            if (mode == Mode.IO_FAILURE) throw new IOException("simulated transport failure");
            if (mode == Mode.BLOCK_REFRESH && request.url().encodedPath().endsWith("/v1/refresh")) {
                entered.countDown();
                try {
                    if (!release.await(3, TimeUnit.SECONDS)) throw new IOException("test latch timed out");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("test interrupted", e);
                }
            }
            if (mode == Mode.STATUS_503) return response(request, 503, error("SERVER_MISCONFIGURED"));
            if (mode == Mode.STATUS_401) return response(request, 401, error("INVALID_CREDENTIAL"));
            if (mode == Mode.STATUS_403) return response(request, 403, error("LICENSE_REVOKED"));
            try {
                JSONObject input = requestJson(request);
                String device = input.getString("deviceId");
                boolean refresh = request.url().encodedPath().endsWith("/v1/refresh");
                String product = mode == Mode.WRONG_PRODUCT ? "another-product" : LicenseConfig.PRODUCT;
                String tokenDevice = mode == Mode.WRONG_DEVICE ? "00000000-0000-0000-0000-000000000000" : device;
                long now = System.currentTimeMillis() / 1000L;
                long issued = mode == Mode.EXPIRED ? now - 700 : now - 5;
                long expires = mode == Mode.EXPIRED ? now - 1 : now + 7 * 24 * 60 * 60 - 5;
                String token = token(product, "lic-1", tokenDevice, issued, expires);
                if (mode == Mode.BAD_SIGNATURE) {
                    int start = token.lastIndexOf('.') + 1;
                    token = token.substring(0, start) + (token.charAt(start) == 'A' ? 'B' : 'A')
                            + token.substring(start + 1);
                }
                JSONObject output = new JSONObject().put("token", token)
                        .put("licenseId", mode == Mode.REFRESH_LICENSE_MISMATCH ? "lic-other" : "lic-1");
                if (!refresh) output.put("deviceSecret", SECRET);
                return response(request, 200, output.toString());
            } catch (Exception e) { throw new IOException("fixture failure", e); }
        }

        private String token(String product, String licenseId, String device, long issued, long expires) throws Exception {
            JSONObject payload = new JSONObject().put("product", product).put("licenseId", licenseId)
                    .put("deviceId", device).put("subject", "Test User")
                    .put("issuedAt", issued).put("expiresAt", expires);
            String encoded = java.util.Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(payload.toString().getBytes(StandardCharsets.UTF_8));
            String signed = "MS2." + encoded;
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(pair.getPrivate());
            signature.update(signed.getBytes(StandardCharsets.US_ASCII));
            return signed + "." + java.util.Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(signature.sign());
        }

        private static JSONObject requestJson(Request request) throws Exception {
            Buffer buffer = new Buffer();
            request.body().writeTo(buffer);
            return new JSONObject(buffer.readUtf8());
        }

        private static String error(String code) {
            try { return new JSONObject().put("error", new JSONObject().put("code", code).put("message", "test")).toString(); }
            catch (Exception impossible) { throw new AssertionError(impossible); }
        }

        private static Response response(Request request, int status, String json) {
            return new Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(status)
                    .message("fixture").body(ResponseBody.create(json, MediaType.get("application/json"))).build();
        }
    }
}
