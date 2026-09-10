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
        JSONObject payload = new JSONObject()
                .put("product", product).put("licenseId", licenseId).put("deviceId", deviceId)
                .put("subject", "Test User").put("issuedAt", issuedAt).put("expiresAt", expiresAt);
        String encoded = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.toString().getBytes(StandardCharsets.UTF_8));
        String signed = "MS2." + encoded;
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

        for (String malformed : new String[]{null, "", "MS1.a.b", "MS2..b", "MS2.a.b.c", "MS2.%%.xx"})
            rejected("malformed token", () -> CloudLicenseToken.verify(malformed, publicKey));

        System.out.println("PASS: " + checks + " CloudLicenseToken assertions");
    }
}
