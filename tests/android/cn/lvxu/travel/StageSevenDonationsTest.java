package cn.lvxu.travel;

import android.content.Context;
import java.io.File;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import okhttp3.*;

/** A valid full public feed must remain readable immediately after refresh. */
final class StageSevenDonationsTest {
    static int run(Context context) throws Exception {
        String longText = new String(new char[500]).replace('\0', '旅');
        String longName = new String(new char[80]).replace('\0', '麦');
        String longPlatform = new String(new char[40]).replace('\0', '穗');
        StringBuilder json = new StringBuilder("{\"records\":[");
        for (int i = 0; i < 200; i++) {
            if (i > 0) json.append(',');
            json.append("{\"platform\":\"").append(longPlatform).append("\",\"name\":\"").append(longName)
                .append("\",\"amount\":\"20.00\",\"currency\":\"CNY\",\"date\":\"2026-09-20\",\"message\":\"")
                .append(longText).append("\"}");
        }
        json.append("]}");
        byte[] body = json.toString().getBytes(StandardCharsets.UTF_8);
        if (body.length <= 256 * 1024) throw new AssertionError("fixture must exceed the former cache limit");
        File cache = new File(context.getFilesDir(), "donations-cache.json");
        byte[] previous = cache.isFile() ? Files.readAllBytes(cache.toPath()) : null;
        OkHttpClient client = new OkHttpClient.Builder().addInterceptor(chain -> new Response.Builder()
            .request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
            .body(ResponseBody.create(MediaType.parse("application/json"), body)).build()).build();
        try {
            DonationsService service = new DonationsService(context, client, "https://example.invalid/v1/donations");
            service.refresh();
            if (service.cached().size() != 200) throw new AssertionError("refreshed public records must survive the cache roundtrip");
            OkHttpClient oversizeClient = new OkHttpClient.Builder().addInterceptor(chain -> new Response.Builder()
                .request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(ResponseBody.create(MediaType.parse("application/json"), new byte[1024 * 1024 + 1])).build()).build();
            try {
                new DonationsService(context, oversizeClient, "https://example.invalid/v1/donations").refresh();
                throw new AssertionError("oversize feed must be rejected");
            } catch (java.io.IOException expected) {
                if (service.cached().size() != 200) throw new AssertionError("oversize feed must preserve last valid cache");
            }
            return 2;
        } finally {
            if (previous == null) cache.delete(); else Files.write(cache.toPath(), previous);
        }
    }
}
