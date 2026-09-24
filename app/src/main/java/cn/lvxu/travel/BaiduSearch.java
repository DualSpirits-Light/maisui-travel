package cn.lvxu.travel;

import java.io.IOException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Pure request/response contract for Baidu intelligent search. */
final class BaiduSearch {
    static final String DEFAULT_MODEL = "deepseek-v4-flash";
    private static final int MAX_SOURCES = 8;
    private static final int MAX_TITLE = 160;
    private static final int MAX_URL = 2048;
    private static final int MAX_TOKENS = 1024;

    private BaiduSearch() { }

    static JSONObject requestBody(String query) throws JSONException {
        String clean = query == null ? "" : query.trim();
        if (clean.isEmpty()) throw new IllegalArgumentException("请输入搜索内容");
        return new JSONObject()
            .put("model", DEFAULT_MODEL)
            .put("messages", new JSONArray().put(new JSONObject().put("role", "user").put("content", clean)))
            .put("stream", false)
            .put("enable_deep_search", false)
            .put("enable_reasoning", false)
            .put("search_source", "baidu_search_v2")
            .put("max_tokens", MAX_TOKENS);
    }

    static String parse(JSONObject response) throws IOException {
        if (response == null) throw new IOException("百度智能搜索未返回有效内容");
        if (response.has("code") && !"0".equals(response.optString("code"))) throw serviceError(response.optString("message", ""));
        JSONObject error = response.optJSONObject("error");
        if (error != null) throw serviceError(error.optString("code", "") + " " + error.optString("message", ""));
        JSONArray choices = response.optJSONArray("choices");
        JSONObject choice = choices == null || choices.length() == 0 ? null : choices.optJSONObject(0);
        String finish = choice == null ? "" : choice.optString("finish_reason", "");
        if ("length".equalsIgnoreCase(finish) || "max_tokens".equalsIgnoreCase(finish)) throw new IOException("百度智能搜索响应因长度限制被截断，请稍后重试");
        if ("content_filter".equalsIgnoreCase(finish) || "safety".equalsIgnoreCase(finish)) throw new IOException("百度智能搜索响应内容被安全策略拦截，请调整后重试");
        JSONObject message = choice == null ? null : choice.optJSONObject("message");
        Object rawContent = message == null ? null : message.opt("content");
        String content = rawContent instanceof String ? ((String) rawContent).trim() : "";
        if (content.isEmpty()) throw new IOException("百度智能搜索未返回有效内容");
        StringBuilder out = new StringBuilder(content);
        JSONArray references = response.optJSONArray("references");
        appendSources(references == null ? response.optJSONArray("sources") : references, out);
        return out.toString();
    }

    private static IOException serviceError(String message) {
        String text = message == null ? "" : message.toLowerCase(java.util.Locale.ROOT);
        if (text.contains("auth") || text.contains("key") || text.contains("token") || text.contains("鉴权") || text.contains("认证")) return new IOException("百度智能搜索认证失败，请检查 API Key 或服务权限");
        if (text.contains("quota") || text.contains("rate") || text.contains("额度") || text.contains("频繁")) return new IOException("百度智能搜索额度不足或请求过于频繁，请稍后重试");
        return new IOException("百度智能搜索服务返回业务错误，请稍后重试");
    }

    private static void appendSources(JSONArray references, StringBuilder out) {
        if (references == null) return;
        int added = 0;
        for (int i = 0; i < references.length() && added < MAX_SOURCES; i++) {
            JSONObject reference = references.optJSONObject(i);
            if (reference == null) continue;
            String title = clip(reference.optString("title", "").replaceAll("[\\r\\n\\t]+", " ").trim(), MAX_TITLE);
            String url = safeHttpUrl(reference.optString("url", ""));
            if (title.isEmpty() || url.isEmpty()) continue;
            if (added++ == 0) out.append("\n\n来源：");
            out.append("\n• ").append(title).append("\n").append(url);
        }
    }

    private static String safeHttpUrl(String value) {
        String url = value == null ? "" : value.trim();
        if (url.length() > MAX_URL) return "";
        try {
            java.net.URI uri = new java.net.URI(url);
            String scheme = uri.getScheme();
            return ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) && uri.getHost() != null && uri.getUserInfo() == null ? url : "";
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String clip(String value, int limit) { return value.length() <= limit ? value : value.substring(0, limit); }
}
