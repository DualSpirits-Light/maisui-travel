package cn.lvxu.travel;

/** Public cloud-license configuration. These values contain no credentials. */
public final class LicenseConfig {
    private LicenseConfig() {}

    /** HTTPS Worker origin, for example https://license.example.workers.dev */
    public static final String ENDPOINT = "https://license.zjm0929.cn";

    /** Base64-encoded X.509 SubjectPublicKeyInfo RSA public key. */
    public static final String RSA_PUBLIC_KEY_SPKI_BASE64 = "MIIBojANBgkqhkiG9w0BAQEFAAOCAY8AMIIBigKCAYEA1lGfDs4Iof9I2N9H9pFXPiNifJ92BXfPZ8XsxUWNMUCdJpLLtPQSHKlzgTzgVCgJxIkzlnAsox7priUy8iSn5J4sFAUp00xvawFaMomRPqPHV8GVCstjfszsJLFuE0uMNS239aUfwFbLa3Zu8jMhLRtoO8VnZrb6IZoOZyV4iugwJpf/1WqVBDPlErIdgH1qxYrMGjZBnwweL0/T71P/WkYvsYVpX+C4Hsp6Uv2PB56tdfsWAb+3hHrx2vD6nfXbekEo75DuuIwUWaAszngtuuRmNiHHGBB30GUZPH+kLyDzkQ1dCdAgXAWVgfYjvsiiYPEtqtbmbb2+X63KsyOa0/cGIaimXLMZUcjcdJO6tH8UaqjXJ2nWb0u2QGkY54QBRMjbx5WuiLHWyteadKH09qrHThvNZ02Evi9ItvfNSaHZQPirL3kQHooUBuT5lktiiZQua5LUu39jtbfzyPD7lOdV81Cioehda24Km1lb+TaqXxeeMHAxErNqFSMxr5jFAgMBAAE=";

    public static final String PRODUCT = "maisui-travel";
}
