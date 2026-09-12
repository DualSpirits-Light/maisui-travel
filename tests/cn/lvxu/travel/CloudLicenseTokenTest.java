package cn.lvxu.travel;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.util.Base64;

/** Pure-Java regression tests for the MS2 offline lease format. */
public final class CloudLicenseTokenTest {
    private static final long NOW = 1_800_000_000L;
    private static int checks;

    private interface Throwing { void run() throws Exception; }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks++;
    }

    private static void rejected(String message, Throwing action) throws Exception {
        try {
            action.run();
            throw new AssertionError(message + " was accepted");
        } catch (SecurityException expected) {
            checks++;
        }
    }

    private static String token(KeyPair keys, String product, String licenseId, String deviceId,
                                long issuedAt, long expiresAt) throws Exception {
        return token(keys, "MS2", product, licenseId, deviceId, issuedAt, expiresAt, null, false);
    }

    private static String token(KeyPair keys, String version, String product, String licenseId, String deviceId,
                                long issuedAt, long expiresAt, Long offlineSeconds, boolean includeOffline) throws Exception {
        JSONObject payload = new JSONObject()
                .put("product", product).put("licenseId", licenseId).put("deviceId", deviceId)
                .put("subject", "Test User").put("issuedAt", issuedAt).put("expiresAt", expiresAt);
        if (includeOffline) payload.put("offlineSeconds", offlineSeconds == null ? JSONObject.NULL : offlineSeconds);
        String encoded = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.toString().getBytes(StandardCharsets.UTF_8));
        String signed = version + "." + encoded;
        Signature signer = Signature.getInstance("SHA256withRSA");
        PrivateKey privateKey = keys.getPrivate();
        signer.initSign(privateKey);
        signer.update(signed.getBytes(StandardCharsets.US_ASCII));
        return signed + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(signer.sign());
    }

    public static void main(String[] args) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keys = generator.generateKeyPair();
        String publicKey = Base64.getEncoder().encodeToString(keys.getPublic().getEncoded());
        String valid = token(keys, "maisui-travel", "lic-1", "device-1", NOW - 1, NOW + 3600);

        CloudLicenseToken accepted = CloudLicenseToken.verify(valid, publicKey);
        accepted.validate("maisui-travel", "device-1", "lic-1", NOW);
        check(accepted.raw.equals(valid), "valid MS2 token retained");

        String permanent = token(keys, "MS3", "maisui-travel", "lic-1", "device-1",
                NOW - 1, 253402300799L, null, true);
        CloudLicenseToken permanentToken = CloudLicenseToken.verify(permanent, publicKey);
        permanentToken.validate("maisui-travel", "device-1", "lic-1", NOW);
        check(permanentToken.offlineSeconds == null && !permanentToken.onlineOnly(), "MS3 permanent offline policy accepted");
        String online = token(keys, "MS3", "maisui-travel", "lic-1", "device-1",
                NOW - 1, NOW + 59, 0L, true);
        check(CloudLicenseToken.verify(online, publicKey).onlineOnly(), "MS3 online-only policy accepted");
        String finite = token(keys, "MS3", "maisui-travel", "lic-1", "device-1",
                NOW - 1, NOW + 3599, 3600L, true);
        check(CloudLicenseToken.verify(finite, publicKey).offlineSeconds == 3600L, "MS3 finite lease accepted");
        String clipped = token(keys, "MS3", "maisui-travel", "lic-1", "device-1",
                NOW - 1, NOW + 99, 3600L, true);
        check(CloudLicenseToken.verify(clipped, publicKey).expiresAt == NOW + 99, "authorization expiry may clip lease");
        String missingPolicy = token(keys, "MS3", "maisui-travel", "lic-1", "device-1",
                NOW - 1, NOW + 59, null, false);
        rejected("MS3 missing policy", () -> CloudLicenseToken.verify(missingPolicy, publicKey));
        String negativePolicy = token(keys, "MS3", "maisui-travel", "lic-1", "device-1",
                NOW - 1, NOW + 59, -1L, true);
        rejected("MS3 negative policy", () -> CloudLicenseToken.verify(negativePolicy, publicKey));
        JSONObject fractionalPayload = new JSONObject().put("product", "maisui-travel").put("licenseId", "lic-1")
                .put("deviceId", "device-1").put("subject", "Test User").put("issuedAt", NOW - 1)
                .put("expiresAt", NOW + 59).put("offlineSeconds", 0.5);
        String fractionalEncoded = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(fractionalPayload.toString().getBytes(StandardCharsets.UTF_8));
        Signature fractionalSigner = Signature.getInstance("SHA256withRSA");
        fractionalSigner.initSign(keys.getPrivate());
        fractionalSigner.update(("MS3." + fractionalEncoded).getBytes(StandardCharsets.US_ASCII));
        String fractional = "MS3." + fractionalEncoded + "." + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(fractionalSigner.sign());
        rejected("MS3 fractional policy", () -> CloudLicenseToken.verify(fractional, publicKey));
        String overPolicy = token(keys, "MS3", "maisui-travel", "lic-1", "device-1",
                NOW - 1, NOW + 7200, 3600L, true);
        rejected("MS3 lease beyond policy", () -> CloudLicenseToken.verify(overPolicy, publicKey));

        String[] altered = valid.split("\\.");
        altered[1] = (altered[1].charAt(0) == 'A' ? "B" : "A") + altered[1].substring(1);
        rejected("tampered payload", () -> CloudLicenseToken.verify(String.join(".", altered), publicKey));
        rejected("wrong product", () -> CloudLicenseToken.verify(valid, publicKey)
                .validate("other-product", "device-1", "lic-1", NOW));
        rejected("wrong device", () -> CloudLicenseToken.verify(valid, publicKey)
                .validate("maisui-travel", "device-2", "lic-1", NOW));
        rejected("wrong license id", () -> CloudLicenseToken.verify(valid, publicKey)
                .validate("maisui-travel", "device-1", "lic-2", NOW));

        String expiresAtNow = token(keys, "maisui-travel", "lic-1", "device-1", NOW - 1, NOW);
        rejected("expiry boundary", () -> CloudLicenseToken.verify(expiresAtNow, publicKey)
                .validate("maisui-travel", "device-1", "lic-1", NOW));
        String future = token(keys, "maisui-travel", "lic-1", "device-1", NOW + 301, NOW + 3600);
        rejected("future issuance", () -> CloudLicenseToken.verify(future, publicKey)
                .validate("maisui-travel", "device-1", "lic-1", NOW));
        String overlong = token(keys, "maisui-travel", "lic-1", "device-1", NOW, NOW + 7 * 24 * 60 * 60 + 301);
        rejected("overlong lease", () -> CloudLicenseToken.verify(overlong, publicKey));

        for (String malformed : new String[]{null, "", "MS1.a.b", "MS4.a.b", "MS2..b", "MS2.a.b.c", "MS2.%%.xx"})
            rejected("malformed token", () -> CloudLicenseToken.verify(malformed, publicKey));

        System.out.println("PASS: " + checks + " CloudLicenseToken assertions");
    }
}
