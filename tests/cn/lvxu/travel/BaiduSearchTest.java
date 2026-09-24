package cn.lvxu.travel;

import java.io.IOException;
import org.json.JSONArray;
import org.json.JSONObject;

/** Offline contract tests for Baidu smart-search request and response handling. */
public final class BaiduSearchTest {
    static int count;
    static void check(boolean value, String name) { if (!value) throw new AssertionError(name); count++; }
    static void rejects(ThrowingCall call, String expected, String name) {
        try { call.run(); } catch (IOException e) { check(e.getMessage().contains(expected), name); return; }
        catch (Exception e) { throw new AssertionError(name + ": wrong exception", e); }
        throw new AssertionError(name + ": accepted invalid response");
    }
    interface ThrowingCall { void run() throws Exception; }

    public static void main(String[] args) throws Exception {
        JSONObject request = BaiduSearch.requestBody("  北京国家博物馆在哪里？  ");
        check(BaiduSearch.DEFAULT_MODEL.equals(request.getString("model")), "uses verified model");
        check(!request.getBoolean("stream") && !request.getBoolean("enable_deep_search") && !request.getBoolean("enable_reasoning"), "uses bounded non-stream search");
        check("baidu_search_v2".equals(request.getString("search_source")) && request.getInt("max_tokens") == 1024, "uses production search settings");
        check(request.getJSONArray("messages").getJSONObject(0).getString("content").equals("北京国家博物馆在哪里？"), "trims user query");

        JSONObject response = new JSONObject().put("choices", new JSONArray().put(new JSONObject().put("message", new JSONObject().put("content", "在东长安街。"))))
            .put("references", new JSONArray()
                .put(new JSONObject().put("title", "官方页面").put("url", "https://example.org/museum"))
                .put(new JSONObject().put("title", "坏链接").put("url", "javascript:alert(1)"))
                .put(new JSONObject().put("title", "缺链接")));
        String rendered = BaiduSearch.parse(response);
        check(rendered.contains("在东长安街。") && rendered.contains("官方页面") && rendered.contains("https://example.org/museum"), "renders answer and safe source");
        check(!rendered.contains("坏链接") && !rendered.contains("缺链接") && !rendered.contains("javascript:"), "drops unsafe or incomplete sources");

        JSONObject sourceAlias = new JSONObject().put("choices", new JSONArray().put(new JSONObject().put("message", new JSONObject().put("content", "答案"))))
            .put("sources", new JSONArray().put(new JSONObject().put("title", "备用来源").put("url", "http://example.org/source")));
        check(BaiduSearch.parse(sourceAlias).contains("备用来源"), "reads sources alias");

        rejects(() -> BaiduSearch.parse(new JSONObject().put("code", 216003).put("message", "Authentication error")), "认证失败", "maps semantic authentication error");
        rejects(() -> BaiduSearch.parse(new JSONObject().put("error", new JSONObject().put("message", "Authentication error"))), "认证失败", "maps error object");
        rejects(() -> BaiduSearch.parse(new JSONObject().put("choices", new JSONArray().put(new JSONObject().put("finish_reason", "length").put("message", new JSONObject().put("content", "半截"))))), "长度限制", "reports truncated response");
        rejects(() -> BaiduSearch.parse(new JSONObject().put("choices", new JSONArray().put(new JSONObject().put("message", new JSONObject().put("content", JSONObject.NULL))))), "未返回有效内容", "rejects JSON null content");
        rejects(() -> BaiduSearch.parse(new JSONObject()), "未返回有效内容", "rejects empty response");
        System.out.println("PASS: " + count + " Baidu search assertions");
    }
}
