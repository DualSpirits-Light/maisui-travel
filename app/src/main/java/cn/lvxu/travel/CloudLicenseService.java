package cn.lvxu.travel;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/** Cloud activation client with signed, server-selected offline policy. */
public final class CloudLicenseService {
    private static final String PREFS = "cloud-license-v1";
    private static final String KEY_DEVICE = "device-id";
    private static final String KEY_CREDENTIAL = "credential";
    private static final String KEY_MAX_WALL = "max-wall-seconds";
    private static final String KEY_LAST_ATTEMPT = "last-refresh-at";
    private static final String KEY_LAST_FAILURE = "last-refresh-failure-at";
    private static final String KEY_LAST_SUCCESS = "last-refresh-success-at";
    private static final String KEY_STATUS = "last-status";
    private static final String KEY_ALIAS = "lvxu.cloud-license.v1";
    private static final long ROLLBACK_ALLOWANCE = 5 * 60;
    private static final long REFRESH_INTERVAL = 24 * 60 * 60;
    private static final long RETRY_INTERVAL = 60 * 60;
    private static final long REFRESH_BEFORE_EXPIRY = 24 * 60 * 60;
    private static final int MAX_RESPONSE = 16 * 1024;
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private static final Object LOCK = new Object();
    private static final Object NETWORK_LOCK = new Object();
    private final SharedPreferences prefs;
    private final OkHttpClient client;
    private final String configuredEndpoint;
    private final String configuredPublicKey;
    private final BooleanSupplier networkConnected;
    private volatile boolean onlineOnlyVerifiedThisSession;
    private final AtomicBoolean backgroundRefreshInFlight = new AtomicBoolean();

    public CloudLicenseService(Context context) {
        this(context, new OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS).writeTimeout(20, TimeUnit.SECONDS)
                .followRedirects(false).followSslRedirects(false).build(),
                LicenseConfig.ENDPOINT, LicenseConfig.RSA_PUBLIC_KEY_SPKI_BASE64,
                connectivityCheck(context));
    }

    /** Test seam: production callers always use the public constructor above. */
    CloudLicenseService(Context context, OkHttpClient client, String endpoint, String publicKey) {
        this(context, client, endpoint, publicKey, () -> true);
    }

    CloudLicenseService(Context context, OkHttpClient client, String endpoint, String publicKey,
                        BooleanSupplier networkConnected) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        this.client = client;
        configuredEndpoint = endpoint;
        configuredPublicKey = publicKey;
        this.networkConnected = networkConnected;
    }

    private static BooleanSupplier connectivityCheck(Context context) {
        Context app = context.getApplicationContext();
        return () -> {
            ConnectivityManager manager = (ConnectivityManager) app.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo info = manager == null ? null : manager.getActiveNetworkInfo();
            return info != null && info.isConnected();
        };
    }

    public boolean configured() {
        try { return endpoint() != null && configuredPublicKey != null && !configuredPublicKey.trim().isEmpty(); }
        catch (Exception ignored) { return false; }
    }

    public String deviceId() {
        synchronized (LOCK) {
            String value = prefs.getString(KEY_DEVICE, "");
            if (!value.isEmpty()) return value;
            value = UUID.randomUUID().toString();
            if (!prefs.edit().putString(KEY_DEVICE, value).commit())
                throw new IllegalStateException("无法保存设备编号");
            return value;
        }
    }

    /** True when cloud state exists, even if its current offline lease has expired. */
    public boolean hasLicense() {
        synchronized (LOCK) {
            try { return !credential().licenseId.isEmpty(); }
            catch (Exception ignored) { return prefs.contains(KEY_CREDENTIAL); }
        }
    }

    public boolean isValid() {
        synchronized (LOCK) {
            try {
                Credential c = credential();
                if (c.token.isEmpty()) return false;
                long now = now();
                long max = prefs.getLong(KEY_MAX_WALL, 0);
                if (max > 0 && now + ROLLBACK_ALLOWANCE < max) return false;
                CloudLicenseToken token = verified(c.token, c.licenseId, now);
                if (now > max) prefs.edit().putLong(KEY_MAX_WALL, now).commit();
                return token.expiresAt > now && (!token.onlineOnly()
                        || (onlineOnlyVerifiedThisSession && networkConnected.getAsBoolean()));
            } catch (Exception ignored) { return false; }
        }
    }

    public String subject() {
        synchronized (LOCK) {
            try {
                Credential c = credential();
                return c.token.isEmpty() ? "" : verified(c.token, c.licenseId, now()).subject;
            } catch (Exception ignored) { return ""; }
        }
    }

    public String statusText() {
        if (!configured()) return "云授权尚未配置";
        if (isValid()) {
            try {
                Credential c = credential();
                CloudLicenseToken token = verified(c.token, c.licenseId, now());
                if (token.offlineSeconds == null && token.expiresAt == 253402300799L)
                    return "授权有效 · 可永久离线使用";
                if (token.onlineOnly()) return "授权有效 · 本次使用已联网验证";
                String date = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(new Date(token.expiresAt * 1000L));
                return token.offlineSeconds == null ? "授权有效 · 授权有效至 " + date
                        : "授权有效 · 可离线使用至 " + date;
            } catch (Exception ignored) { return "授权有效"; }
        }
        String status = prefs.getString(KEY_STATUS, "");
        return hasLicense() ? (status.isEmpty() ? "授权需要联网复核" : status) : "尚未激活";
    }

    /** Called synchronously before a foreground refresh is queued. */
    public void beginForegroundSession() {
        onlineOnlyVerifiedThisSession = false;
    }

    /** Called when the app leaves the foreground; online-only access must not carry over. */
    public void endForegroundSession() {
        onlineOnlyVerifiedThisSession = false;
    }

    public long nextRefreshDelayMillis() {
        try {
            Credential c = credential();
            if (c.token.isEmpty()) return 30_000;
            CloudLicenseToken token = verifiedPolicy(c.token, c.licenseId);
            if (token.onlineOnly()) return 30_000;
            if (token.offlineSeconds != null)
                return Math.min(30, Math.max(1, token.offlineSeconds / 3)) * 1000L;
        } catch (Exception ignored) { return 30_000; }
        return 30_000;
    }

    public String activate(String code) throws Exception {
        synchronized (NETWORK_LOCK) {
            if (!configured()) throw new IOException("云授权服务尚未配置");
            code = code == null ? "" : code.trim();
            if (code.isEmpty() || code.length() > 512) throw new IOException("激活码无效");
            JSONObject request = new JSONObject().put("code", code).put("deviceId", deviceId())
                    .put("clientVersion", 31);
            try {
                JSONObject response = post("/v1/activate", request);
                String tokenRaw = required(response, "token", MAX_RESPONSE);
                String licenseId = required(response, "licenseId", 256);
                String secret = required(response, "deviceSecret", 2048);
                CloudLicenseToken token = verified(tokenRaw, licenseId, now());
                save(new Credential(tokenRaw, licenseId, secret), now());
                onlineOnlyVerifiedThisSession = token.onlineOnly();
                return token.subject;
            } catch (Exception e) {
                invalidateOnlineOnly();
                throw e;
            }
        }
    }

    public void refresh() throws Exception {
        synchronized (NETWORK_LOCK) {
            if (!configured()) throw new IOException("云授权服务尚未配置");
            Credential old = credential();
            if (old.licenseId.isEmpty() || old.secret.isEmpty()) throw new IOException("没有可复核的授权");
            JSONObject request = new JSONObject().put("licenseId", old.licenseId)
                    .put("deviceId", deviceId()).put("deviceSecret", old.secret).put("clientVersion", 31);
            try {
                JSONObject response = post("/v1/refresh", request);
                String tokenRaw = required(response, "token", MAX_RESPONSE);
                String licenseId = required(response, "licenseId", 256);
                CloudLicenseToken token = verified(tokenRaw, old.licenseId, now());
                if (!old.licenseId.equals(licenseId)) throw new SecurityException("授权编号不匹配");
                save(new Credential(tokenRaw, old.licenseId, old.secret), now());
                onlineOnlyVerifiedThisSession = token.onlineOnly();
            } catch (HttpStatusException e) {
                invalidateOnlineOnly();
                if (e.status == 401 || e.status == 403) {
                    prefs.edit().putString(KEY_STATUS, e.getMessage()).commit();
                    save(new Credential("", old.licenseId, old.secret), 0);
                }
                throw e;
            } catch (Exception e) {
                invalidateOnlineOnly();
                throw e;
            }
        }
    }

    /** Policy-aware background refresh; concurrent heartbeat calls collapse into one request. */
    public boolean refreshIfDue() {
        if (!backgroundRefreshInFlight.compareAndSet(false, true)) return isValid();
        long now = now();
        try {
            synchronized (LOCK) {
                Credential c = credential();
                if (c.licenseId.isEmpty() || c.secret.isEmpty()) return false;
                boolean onlineOnly = false;
                Long offlineSeconds = null;
                long remaining = Long.MIN_VALUE;
                if (!c.token.isEmpty()) {
                    try {
                        CloudLicenseToken policy = verifiedPolicy(c.token, c.licenseId);
                        onlineOnly = policy.onlineOnly();
                        offlineSeconds = policy.offlineSeconds;
                        remaining = policy.expiresAt - now;
                    }
                    catch (Exception ignored) { }
                }
                long lastFailure = prefs.getLong(KEY_LAST_FAILURE, 0);
                long retry = RETRY_INTERVAL;
                if (offlineSeconds != null && offlineSeconds > 0)
                    retry = Math.min(60, Math.max(1, offlineSeconds / 2));
                if (!onlineOnly && remaining > 0 && lastFailure > 0 && now >= lastFailure
                        && now - lastFailure < retry) return isValid();
                boolean due = onlineOnly || now - prefs.getLong(KEY_LAST_SUCCESS, 0) >= REFRESH_INTERVAL;
                if (offlineSeconds != null && offlineSeconds > 0) {
                    long refreshWindow = Math.min(REFRESH_BEFORE_EXPIRY, Math.max(1, offlineSeconds / 3));
                    due |= remaining <= refreshWindow;
                }
                if (!c.token.isEmpty()) {
                    try { due |= (offlineSeconds == null) && verified(c.token, c.licenseId, now).expiresAt - now <= REFRESH_BEFORE_EXPIRY; }
                    catch (Exception ignored) { due = true; }
                } else due = true;
                if (!due) return isValid();
                prefs.edit().putLong(KEY_LAST_ATTEMPT, now).commit();
            }
            refresh();
            return isValid();
        } catch (Exception ignored) {
            prefs.edit().putLong(KEY_LAST_FAILURE, now).commit();
            return isValid();
        } finally {
            backgroundRefreshInFlight.set(false);
        }
    }

    private CloudLicenseToken verified(String raw, String licenseId, long now) throws Exception {
        CloudLicenseToken token = CloudLicenseToken.verify(raw, configuredPublicKey);
        token.validate(LicenseConfig.PRODUCT, deviceId(), licenseId, now);
        return token;
    }

    /** Verifies signature, schema and binding while allowing an expired online heartbeat token to refresh. */
    private CloudLicenseToken verifiedPolicy(String raw, String licenseId) throws Exception {
        CloudLicenseToken token = CloudLicenseToken.verify(raw, configuredPublicKey);
        if (!LicenseConfig.PRODUCT.equals(token.product)) throw new SecurityException("授权产品不匹配");
        if (!deviceId().equals(token.deviceId)) throw new SecurityException("授权设备不匹配");
        if (licenseId != null && !licenseId.equals(token.licenseId)) throw new SecurityException("授权编号不匹配");
        return token;
    }

    private JSONObject post(String path, JSONObject json) throws Exception {
        String target = endpoint() + path;
        Request request = new Request.Builder().url(target).post(RequestBody.create(json.toString(), JSON))
                .header("Accept", "application/json").header("User-Agent", "MaisuiTravel/2").build();
        try (Response response = client.newCall(request).execute()) {
            if (response.code() < 200 || response.code() >= 300) {
                String body = "";
                try { body = readBounded(response.body() == null ? null : response.body().byteStream()); }
                catch (IOException ignored) { /* HTTP status remains authoritative. */ }
                throw new HttpStatusException(response.code(), errorCode(body));
            }
            String body = readBounded(response.body() == null ? null : response.body().byteStream());
            try { return new JSONObject(body); }
            catch (Exception e) { throw new IOException("授权服务响应无效", e); }
        }
    }

    private String endpoint() throws Exception {
        String value = configuredEndpoint == null ? "" : configuredEndpoint.trim();
        if (value.isEmpty()) return null;
        URL url = new URL(value);
        if (!"https".equalsIgnoreCase(url.getProtocol()) || url.getUserInfo() != null || url.getHost().isEmpty()
                || url.getQuery() != null || url.getRef() != null || (!url.getPath().isEmpty() && !"/".equals(url.getPath())))
            throw new IOException("云授权地址必须是 HTTPS 服务根地址");
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private Credential credential() throws Exception {
        synchronized (LOCK) {
            String encrypted = prefs.getString(KEY_CREDENTIAL, "");
            if (encrypted.isEmpty()) return new Credential("", "", "");
            String plain = decrypt(encrypted);
            JSONObject json = new JSONObject(plain);
            return new Credential(json.optString("token", ""), json.optString("licenseId", ""), json.optString("deviceSecret", ""));
        }
    }

    private void save(Credential value, long successAt) throws Exception {
        synchronized (LOCK) {
            JSONObject json = new JSONObject().put("token", value.token).put("licenseId", value.licenseId)
                    .put("deviceSecret", value.secret);
            String encrypted = encrypt(json.toString());
            SharedPreferences.Editor editor = prefs.edit().putString(KEY_CREDENTIAL, encrypted);
            // A newly verified server response permits recovery after the user corrects a bad local clock.
            if (successAt > 0) editor.putLong(KEY_LAST_SUCCESS, successAt).putLong(KEY_MAX_WALL, successAt)
                    .remove(KEY_STATUS).remove(KEY_LAST_FAILURE);
            if (!editor.commit()) throw new IOException("无法保存授权信息");
        }
    }

    private static String encrypt(String plain) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key());
        byte[] ciphertext = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
        return Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP) + "." +
                Base64.encodeToString(ciphertext, Base64.NO_WRAP);
    }

    private static String decrypt(String encrypted) throws Exception {
        String[] parts = encrypted.split("\\.", -1);
        if (parts.length != 2) throw new SecurityException("授权存储无效");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)));
        return new String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), StandardCharsets.UTF_8);
    }

    private static SecretKey key() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if (!store.containsAlias(KEY_ALIAS)) {
            KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true).build());
            generator.generateKey();
        }
        return (SecretKey) store.getKey(KEY_ALIAS, null);
    }

    private static String readBounded(InputStream input) throws IOException {
        if (input == null) return "";
        try (InputStream in = input; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int total = 0;
            for (int count; (count = in.read(buffer)) != -1;) {
                total += count;
                if (total > MAX_RESPONSE) throw new IOException("授权服务响应过大");
                out.write(buffer, 0, count);
            }
            return out.toString("UTF-8");
        }
    }

    private static String required(JSONObject json, String name, int max) throws Exception {
        String value = json.getString(name);
        if (value.isEmpty() || value.length() > max) throw new IOException("授权服务响应缺少必要字段");
        return value;
    }

    private static long now() { return System.currentTimeMillis() / 1000L; }

    private void invalidateOnlineOnly() {
        try {
            Credential c = credential();
            if (!c.token.isEmpty() && verifiedPolicy(c.token, c.licenseId).onlineOnly())
                onlineOnlyVerifiedThisSession = false;
        } catch (Exception ignored) { onlineOnlyVerifiedThisSession = false; }
    }

    private static final class Credential {
        final String token, licenseId, secret;
        Credential(String token, String licenseId, String secret) {
            this.token = token; this.licenseId = licenseId; this.secret = secret;
        }
    }

    private static String errorCode(String body) {
        try {
            JSONObject json = new JSONObject(body);
            Object error = json.opt("error");
            if (error instanceof JSONObject) return ((JSONObject) error).optString("code", "");
            return json.optString("code", error instanceof String ? (String) error : "");
        }
        catch (Exception ignored) { return ""; }
    }

    private static final class HttpStatusException extends IOException {
        final int status;
        HttpStatusException(int status, String code) { super(message(status, code)); this.status = status; }
        private static String message(int status, String code) {
            if ("INVALID_CODE".equals(code)) return "激活码无效";
            if ("DEVICE_LIMIT_REACHED".equals(code)) return "激活码已绑定其他设备";
            if ("LICENSE_FROZEN".equals(code)) return "授权已被冻结，请联系管理员";
            if ("LICENSE_REVOKED".equals(code)) return "授权已被管理员解绑或停用";
            if ("LICENSE_EXPIRED".equals(code)) return "授权已失效";
            if ("INVALID_CREDENTIAL".equals(code)) return "设备授权凭据无效，请联系管理员解绑后重新激活";
            if ("RATE_LIMITED".equals(code) || status == 429) return "请求过于频繁，请稍后重试";
            if ("SERVER_MISCONFIGURED".equals(code)) return "授权服务尚未正确配置";
            if (status == 401 || status == 403) return "授权复核未通过";
            if (status >= 500) return "授权服务暂时不可用，请稍后重试";
            return "授权服务请求失败（" + status + "）";
        }
    }
}
