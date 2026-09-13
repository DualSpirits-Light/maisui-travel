package cn.lvxu.travel;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;

/** Regression coverage for cancelling a slow verification after another settings action. */
public final class ApiConfigRevisionTest {
 private ApiConfigRevisionTest() {}

 public static int run(Context context) throws Exception {
  ApiConfig config=new ApiConfig(context),other=new ApiConfig(context);
  JSONObject original=config.read();int checks=0;
  try {
   config.reset();
   long fresh=config.revision();
   check(config.putIfRevision(fresh,"baiduSearchKey","fresh-test-key"),"fresh revision accepts verified write");checks++;
   check("fresh-test-key".equals(config.searchKey()),"fresh write is readable");checks++;

   long beforeClear=config.revision();config.put("baiduSearchKey","");
   check(!config.putIfRevision(beforeClear,"baiduSearchKey","late-test-key"),"clear rejects late verification write");checks++;
   check(config.searchKey().isEmpty(),"late write does not restore cleared key");checks++;

   long beforeReset=config.revision();config.reset();
   check(!config.putIfRevision(beforeReset,"mapKey.amap","late-map-key"),"reset rejects late verification write");checks++;
   check(config.mapKey("amap").isEmpty(),"late map key is absent after reset");checks++;

   long beforeOther=config.revision();other.put("mapKey.amap","newer-instance-key");
   check(!config.putIfRevision(beforeOther,"mapKey.amap","late-instance-key"),"other instance write rejects late verification write");checks++;
   check("newer-instance-key".equals(config.mapKey("amap")),"other instance value remains intact");checks++;

   long profileRevision=config.revision();JSONArray profiles=new JSONArray().put(new JSONObject().put("id","test-provider").put("name","Test provider").put("baseUrl","https://example.test/v1").put("model","test-model").put("protocol","openai").put("key","test-key").put("headers","{}"));
   JSONObject group=new JSONObject().put("aiProviders",profiles).put("selectedAiProvider","test-provider");
   check(config.putAllIfRevision(profileRevision,group),"fresh provider group saves atomically");checks++;
   JSONObject saved=config.read();
   check(saved.optJSONArray("aiProviders")!=null&&saved.optJSONArray("aiProviders").length()==1&&"test-provider".equals(saved.optString("selectedAiProvider")),"provider list and selected provider persist together");checks++;
   long staleGroup=config.revision();config.put("prompt","newer prompt");
   check(!config.putAllIfRevision(staleGroup,new JSONObject().put("aiProviders",new JSONArray()).put("selectedAiProvider","")),"stale provider group cannot replace newer settings");checks++;
   check(savedProvider(config).equals("test-provider"),"rejected group keeps prior selected provider");checks++;
   return checks;
  } finally {config.reset();config.putAll(original);}
 }

 private static String savedProvider(ApiConfig config){return config.read().optString("selectedAiProvider","");}
 private static void check(boolean value,String label){if(!value)throw new AssertionError(label);}
}
