package cn.lvxu.travel;

import org.json.JSONObject;

/** Contract tests for bounded, model-produced travel plans. */
public final class AiPlanTest {
    static int count;
    static void check(boolean value, String name) { if (!value) throw new AssertionError(name); count++; }
    static void rejects(Runnable call, String name) { try { call.run(); } catch (IllegalArgumentException expected) { count++; return; } throw new AssertionError(name); }
    public static void main(String[] args) {
        String json = "{\"days\":[{\"day\":1,\"items\":[{\"name\":\"西湖\",\"time\":\"09:00\",\"duration\":120,\"note\":\"沿湖慢走\"}]}],\"budget\":{\"total\":300,\"currency\":\"CNY\"},\"tips\":[\"确认营业时间\"]}";
        AiPlan plan = AiPlan.parse(json, 2);
        check(plan.days.size() == 1 && plan.days.get(0).items.size() == 1, "reads day items");
        check(plan.budgetCents == 30000, "converts yuan budget to cents");
        check(plan.toStops().get(0).name.equals("西湖"), "converts to stop drafts");
        check(AiPlan.extractJson("说明\n```json\n" + json + "\n```\n") .startsWith("{\"days\""), "extracts fenced JSON");
        rejects(() -> AiPlan.parse("{\"days\":[{\"day\":3,\"items\":[]}]}", 2), "rejects out of range day");
        rejects(() -> AiPlan.parse("{\"days\":[{\"day\":1,\"items\":[{\"name\":\"x\",\"time\":\"9:00\"}]}]}", 1), "rejects malformed time");
        check(AiPlan.parse(json.replace("\"duration\":120", "\"lat\":null,\"lon\":null,\"duration\":120"),2).toStops().get(0).lat==null,"unknown coordinates stay unknown");
        rejects(() -> AiPlan.parse(json.replace("CNY", "USD"), 2), "foreign currency not treated as yuan");
        rejects(() -> AiPlan.parse("{\"days\":[{\"day\":1,\"items\":[]}]}", 1), "empty plan cannot import");
        System.out.println("PASS: " + count + " AI plan assertions");
    }
}
