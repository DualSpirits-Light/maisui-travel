package cn.lvxu.travel;

import android.content.Context;
import android.content.SharedPreferences;
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

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/** Cloud activation client with a signed seven-day offline lease. */
public final class CloudLicenseService {
    private static final String PREFS = "cloud-license-v1";
    private static final String KEY_DEVICE = "device-id";
    private static final String KEY_CREDENTIAL = "credential";
    private static final String KEY_MAX_WALL = "max-wall-seconds";
    private static final String KEY_LAST_ATTEMPT = "last-refresh-at";
    private static final String KEY_LAST_SUCCESS = "last-refresh-success-at";
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

    public CloudLicenseService(Context context) {
        this(context, new OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS).writeTimeout(20, TimeUnit.SECONDS)
                .followRedirects(false).followSslRedirects(false).build(),
                LicenseConfig.ENDPOINT, LicenseConfig.RSA_PUBLIC_KEY_SPKI_BASE64);
    }

    /** Test seam: production callers always use the public constructor above. */
    CloudLicenseService(Context context, OkHttpClient client, String endpoint, String publicKey) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        this.client = client;
        configuredEndpoint = endpoint;
        configuredPublicKey = publicKey;
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
                return token.expiresAt > now;
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
                String date = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
                        .format(new Date(token.expiresAt * 1000L));
                return "授权有效 · 离线凭证有效至 " + date;
            } catch (Exception ignored) { return "授权有效"; }
        }
        return hasLicense() ? "授权需要联网复核" : "尚未激活";
    }

    public String activate(String code) throws Exception {
        synchronized (NETWORK_LOCK) {
            if (!configured()) throw new IOException("云授权服务尚未配置");
            code = code == null ? "" : code.trim();
            if (code.isEmpty() || code.length() > 512) throw new IOException("激活码无效");
            JSONObject request = new JSONObject().put("code", code).put("deviceId", deviceId());
            JSONObject response = post("/v1/activate", request);
            String tokenRaw = required(response, "token", MAX_RESPONSE);
            String licenseId = required(response, "licenseId", 256);
            String secret = required(response, "deviceSecret", 2048);
            CloudLicenseToken token = verified(tokenRaw, licenseId, now());
            save(new Credential(tokenRaw, licenseId, secret), now());
            return token.subject;
        }
    }

    public void refresh() throws Exception {
        synchronized (NETWORK_LOCK) {
            if (!configured()) throw new IOException("云授权服务尚未配置");
            Credential old = credential();
            if (old.licenseId.isEmpty() || old.secret.isEmpty()) throw new IOException("没有可复核的授权");
            JSONObject request = new JSONObject().put("licenseId", old.licenseId)
                    .put("deviceId", deviceId()).put("deviceSecret", old.secret);
            try {
                JSONObject response = post("/v1/refresh", request);
                String tokenRaw = required(response, "token", MAX_RESPONSE);
                String licenseId = required(response, "licenseId", 256);
                verified(tokenRaw, old.licenseId, now());
                if (!old.licenseId.equals(licenseId)) throw new SecurityException("授权编号不匹配");
                save(new Credential(tokenRaw, old.licenseId, old.secret), now());
            } catch (HttpStatusException e) {
                if (e.status == 401 || e.status == 403) save(new Credential("", old.licenseId, old.secret), 0);
                throw e;
            }
        }
    }

    /** Runs at most daily, retries failures no more than hourly, and preserves a valid lease on network errors. */
    public boolean refreshIfDue() {
        long now = now();
        try {
            synchronized (LOCK) {
                Credential c = credential();
                if (c.licenseId.isEmpty() || c.secret.isEmpty()) return false;
                long lastAttempt = prefs.getLong(KEY_LAST_ATTEMPT, 0);
                if (lastAttempt > 0 && now >= lastAttempt && now - lastAttempt < RETRY_INTERVAL) return isValid();
                boolean due = now - prefs.getLong(KEY_LAST_SUCCESS, 0) >= REFRESH_INTERVAL;
                if (!c.token.isEmpty()) {
                    try { due |= verified(c.token, c.licenseId, now).expiresAt - now <= REFRESH_BEFORE_EXPIRY; }
                    catch (Exception ignored) { due = true; }
                } else due = true;
                if (!due) return isValid();
                prefs.edit().putLong(KEY_LAST_ATTEMPT, now).commit();
            }
            refresh();
        } catch (Exception ignored) { /* Background caller observes the resulting validity only. */ }
        return isValid();
    }

    private CloudLicenseToken verified(String raw, String licenseId, long now) throws Exception {
        CloudLicenseToken token = CloudLicenseToken.verify(raw, configuredPublicKey);
        token.validate(LicenseConfig.PRODUCT, deviceId(), licenseId, now);
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
            if (successAt > 0) editor.putLong(KEY_LAST_SUCCESS, successAt).putLong(KEY_MAX_WALL, successAt);
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
