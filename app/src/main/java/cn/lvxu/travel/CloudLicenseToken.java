package cn.lvxu.travel;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/** Parser and verifier for the MS2 signed offline-lease token. */
public final class CloudLicenseToken {
    private static final long CLOCK_SKEW_SECONDS = 5 * 60;
    private static final long MAX_LEASE_SECONDS = 7 * 24 * 60 * 60 + CLOCK_SKEW_SECONDS;

    public final String raw;
    public final String product;
    public final String licenseId;
    public final String deviceId;
    public final String subject;
    public final long issuedAt;
    public final long expiresAt;

    private CloudLicenseToken(String raw, JSONObject json) throws Exception {
        this.raw = raw;
        product = required(json, "product", 100);
        licenseId = required(json, "licenseId", 256);
        deviceId = required(json, "deviceId", 128);
        subject = required(json, "subject", 512);
        issuedAt = json.getLong("issuedAt");
        expiresAt = json.getLong("expiresAt");
        if (issuedAt <= 0 || expiresAt <= issuedAt || expiresAt - issuedAt > MAX_LEASE_SECONDS)
            throw new SecurityException("授权有效期无效");
    }

    public static CloudLicenseToken verify(String token, String spkiBase64) throws Exception {
        if (token == null || token.length() > 16 * 1024) throw new SecurityException("授权令牌无效");
        String[] parts = token.split("\\.", -1);
        if (parts.length != 3 || !"MS2".equals(parts[0]) || parts[1].isEmpty() || parts[2].isEmpty())
            throw new SecurityException("授权令牌格式无效");
        byte[] payload = decodeUrl(parts[1]);
        byte[] signatureBytes = decodeUrl(parts[2]);
        if (payload.length == 0 || payload.length > 12 * 1024 || signatureBytes.length > 1024)
            throw new SecurityException("授权令牌大小无效");
        byte[] keyBytes = Base64.getMimeDecoder().decode(spkiBase64 == null ? "" : spkiBase64);
        PublicKey key = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(keyBytes));
        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(key);
        verifier.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
        if (!verifier.verify(signatureBytes)) throw new SecurityException("授权令牌签名无效");
        return new CloudLicenseToken(token, new JSONObject(new String(payload, StandardCharsets.UTF_8)));
    }

    public void validate(String expectedProduct, String expectedDeviceId, String expectedLicenseId,
                         long nowSeconds) {
        if (!product.equals(expectedProduct)) throw new SecurityException("授权产品不匹配");
        if (!deviceId.equals(expectedDeviceId)) throw new SecurityException("授权设备不匹配");
        if (expectedLicenseId != null && !expectedLicenseId.equals(licenseId))
            throw new SecurityException("授权编号不匹配");
        if (issuedAt > nowSeconds + CLOCK_SKEW_SECONDS) throw new SecurityException("授权签发时间无效");
        if (expiresAt <= nowSeconds) throw new SecurityException("授权已过期");
    }

    private static byte[] decodeUrl(String value) {
        if (!value.matches("[A-Za-z0-9_-]+")) throw new SecurityException("授权令牌编码无效");
        try { return Base64.getUrlDecoder().decode(value); }
        catch (IllegalArgumentException e) { throw new SecurityException("授权令牌编码无效", e); }
    }

    private static String required(JSONObject json, String name, int max) throws Exception {
        String value = json.getString(name);
        if (value.isEmpty() || value.length() > max) throw new SecurityException("授权字段无效");
        return value;
    }
}
