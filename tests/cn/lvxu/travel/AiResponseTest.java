package cn.lvxu.travel;

import org.json.JSONObject;

/** Fixture coverage for provider response normalization without network access. */
public final class AiResponseTest {
 static int count;
 static void check(boolean value,String name){if(!value)throw new AssertionError(name);count++;}
 static void rejects(String body,String name){try{AiResponse.compatible(new JSONObject(body));}catch(Exception expected){count++;return;}throw new AssertionError(name);}
 public static void main(String[] args)throws Exception{
  check(AiResponse.compatible(new JSONObject("{\"choices\":[{\"message\":{\"content\":\"<think>private reasoning</think>最终行程\"}}]}" )).equals("最终行程"),"removes leading paired think block");
  check(AiResponse.compatible(new JSONObject("{\"choices\":[{\"message\":{\"content\":\"保留 <think>正文标记</think>\"}}]}" )).equals("保留 <think>正文标记</think>"),"does not remove non-leading think text");
 rejects("{\"choices\":[{\"finish_reason\":\"length\",\"message\":{\"content\":\"半截\"}}]}","reports compatible truncation");
  rejects("{\"choices\":[{\"finish_reason\":\"content_filter\",\"message\":{\"content\":\"半截\"}}]}","reports compatible content filter");
  rejects("{\"base_resp\":{\"status_code\":1004,\"status_msg\":\"secret detail\"},\"choices\":[]}","does not accept MiniMax embedded error");
  rejects("{\"error\":{\"code\":\"invalid_api_key\",\"message\":\"secret detail\"}}","does not accept compatible error object");
  rejects("{\"choices\":[{\"message\":{\"content\":null}}]}","rejects JSON null content");
  rejects("{\"choices\":[{\"message\":{\"content\":\"<think>unfinished\"}}]}","rejects unclosed leading think block");
  check(AiResponse.gemini(new JSONObject("{\"candidates\":[{\"content\":{\"parts\":[{\"thought\":true,\"text\":\"hidden\"},{\"text\":\"第一段\"},{\"text\":\"第二段\"}]}}]}" )).equals("第一段第二段"),"joins visible Gemini parts");
  try{AiResponse.gemini(new JSONObject("{\"promptFeedback\":{\"blockReason\":\"SAFETY\"}}"));throw new AssertionError("reports Gemini blocked prompt");}catch(Exception expected){count++;}
  System.out.println("PASS: "+count+" AI response assertions");
 }
}
