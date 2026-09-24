package cn.lvxu.travel;

import android.content.Context;
import okhttp3.*;
import org.json.*;
import java.io.IOException;
import java.util.*;

/** Provider profiles and two intentionally explicit wire adapters. Keys remain inside ApiConfig's vault. */
final class AiProviders {
 static final String OPENAI="openai", GEMINI="gemini";
 static final class Profile {
  String id,name,baseUrl,model,protocol,key,headers;
  Profile(String id,String name,String base,String model,String protocol){this.id=id;this.name=name;baseUrl=base;this.model=model;this.protocol=protocol;headers="{}";}
  JSONObject json()throws JSONException{return new JSONObject().put("id",id).put("name",name).put("baseUrl",baseUrl).put("model",model).put("protocol",protocol).put("key",key==null?"":key).put("headers",headers==null?"{}":headers);}
  static Profile from(JSONObject o)throws JSONException{Profile p=new Profile(clean(o.optString("id"),60,"配置 ID"),clean(o.optString("name"),60,"名称"),https(o.optString("baseUrl")),clean(o.optString("model"),120,"模型"),protocol(o.optString("protocol")));p.key=o.optString("key","");p.headers=headers(o.optString("headers","{}"));return p;}
 }
 static Profile[] presets(){return new Profile[]{new Profile("openai","OpenAI","https://api.openai.com/v1","gpt-5.6-luna",OPENAI),new Profile("deepseek","DeepSeek","https://api.deepseek.com/v1","deepseek-chat",OPENAI),new Profile("gemini","Gemini","https://generativelanguage.googleapis.com/v1beta","gemini-3.8-flash",GEMINI),new Profile("kimi","Kimi / Moonshot","https://api.moonshot.ai/v1","kimi-k2.6",OPENAI),new Profile("qwen","通义千问","https://dashscope.aliyuncs.com/compatible-mode/v1","qwen-turbo",OPENAI),new Profile("minimax","MiniMax（中国 · Token Plan）","https://api.minimaxi.com/v1","MiniMax-M2.7",OPENAI),new Profile("minimax-overseas","MiniMax（海外）","https://api.minimax.io/v1","MiniMax-M2.7",OPENAI),new Profile("glm","智谱 GLM","https://open.bigmodel.cn/api/paas/v4","glm-4-flash",OPENAI),new Profile("custom","自定义兼容服务","https://","",OPENAI)};}
 static ArrayList<Profile> read(Context c){JSONArray a=new ApiConfig(c).read().optJSONArray("aiProviders");ArrayList<Profile> out=new ArrayList<>();if(a!=null)for(int i=0;i<a.length()&&i<20;i++)try{out.add(Profile.from(a.getJSONObject(i)));}catch(Exception ignored){}return out;}
 static String selected(Context c){return new ApiConfig(c).read().optString("selectedAiProvider","");}
 static Profile selectedProfile(Context c){String id=selected(c);for(Profile p:read(c))if(p.id.equals(id))return p;return null;}
 static void save(Context c,List<Profile> profiles,String selected)throws Exception{new ApiConfig(c).putAll(values(profiles,selected));}
 static boolean saveIfRevision(Context c,List<Profile> profiles,String selected,long revision)throws Exception{return new ApiConfig(c).putAllIfRevision(revision,values(profiles,selected));}
 private static JSONObject values(List<Profile> profiles,String selected)throws Exception{if(profiles.size()>20)throw new IllegalArgumentException("最多保存 20 个 AI 配置");JSONArray out=new JSONArray();HashSet<String> ids=new HashSet<>();for(Profile p:profiles){Profile safe=Profile.from(p.json());if(!ids.add(safe.id))throw new IllegalArgumentException("配置 ID 重复");out.put(safe.json());}return new JSONObject().put("aiProviders",out).put("selectedAiProvider",selected==null?"":selected);}
 static String complete(Profile p,String prompt)throws Exception {return complete(p,"",prompt);}
 static String complete(Profile p,String system,String prompt)throws Exception {return adapter(p).complete(p,system==null?"":system,prompt);}
 static void verify(Profile p)throws Exception {String value=complete(p,"只回复：连接成功");if(value.trim().isEmpty())throw new IOException("服务没有返回文本");}
 private interface Adapter {String complete(Profile p,String system,String prompt)throws Exception;}
 private static Adapter adapter(Profile p){return GEMINI.equals(p.protocol)?new Gemini():new Compatible();}
 private static final class Compatible implements Adapter { public String complete(Profile p,String system,String prompt)throws Exception {
   JSONArray messages=new JSONArray();if(!system.trim().isEmpty())messages.put(new JSONObject().put("role","system").put("content",system));messages.put(new JSONObject().put("role","user").put("content",prompt));JSONObject body=new JSONObject().put("model",p.model).put("messages",messages).put("stream",false);
   if(isMiniMax(p.baseUrl)){body.put("reasoning_split",true);body.put("max_tokens",isVerificationPrompt(prompt)?512:8192);}
   HttpUrl endpoint=ApiHttp.url(p.baseUrl).newBuilder().addPathSegments("chat/completions").build();Request.Builder b=new Request.Builder().url(endpoint).post(RequestBody.create(MediaType.parse("application/json; charset=utf-8"),body.toString())).header("Authorization","Bearer "+requiredKey(p));headers(b,p.headers);return AiResponse.compatible(ApiHttp.json(b.build()));
  }}
 private static final class Gemini implements Adapter { public String complete(Profile p,String system,String prompt)throws Exception {
   JSONObject body=new JSONObject().put("contents",new JSONArray().put(new JSONObject().put("role","user").put("parts",new JSONArray().put(new JSONObject().put("text",prompt)))));
   if(!system.trim().isEmpty())body.put("systemInstruction",new JSONObject().put("parts",new JSONArray().put(new JSONObject().put("text",system))));HttpUrl endpoint=ApiHttp.url(p.baseUrl).newBuilder().addPathSegment("models").addPathSegment(p.model+":generateContent").build();Request.Builder b=new Request.Builder().url(endpoint).post(RequestBody.create(MediaType.parse("application/json; charset=utf-8"),body.toString()));headers(b,p.headers);if(!hasHeader(p.headers,"x-goog-api-key"))b.header("x-goog-api-key",requiredKey(p));return AiResponse.gemini(ApiHttp.json(b.build()));
  }}
 private static void headers(Request.Builder b,String source)throws JSONException{JSONObject o=new JSONObject(headers(source));Iterator<String> it=o.keys();while(it.hasNext()){String k=it.next();String v=o.optString(k);if(k.matches("[A-Za-z0-9-]{1,80}")&&!v.contains("\r")&&!v.contains("\n"))b.header(k,v);}}
 private static boolean hasHeader(String source,String name)throws JSONException{JSONObject o=new JSONObject(headers(source));Iterator<String> it=o.keys();while(it.hasNext())if(name.equalsIgnoreCase(it.next()))return true;return false;}
 static String protocol(String v){if(!OPENAI.equals(v)&&!GEMINI.equals(v))throw new IllegalArgumentException("协议无效");return v;}
 static String https(String v){ApiHttp.url(v);return v.replaceAll("/+$","");}
 static String headers(String v){try{JSONObject o=new JSONObject(v==null||v.trim().isEmpty()?"{}":v);if(o.length()>30)throw new IllegalArgumentException("自定义请求头过多");Iterator<String> it=o.keys();while(it.hasNext()){String k=it.next();if(!k.matches("[A-Za-z0-9-]{1,80}")||o.optString(k).length()>2000)throw new IllegalArgumentException("自定义请求头无效");}return o.toString();}catch(JSONException e){throw new IllegalArgumentException("自定义请求头须为 JSON 对象");}}
 private static String clean(String v,int n,String label){v=v==null?"":v.trim();if(v.isEmpty()||v.length()>n)throw new IllegalArgumentException(label+"无效");return v;}
 private static String requiredKey(Profile p){if(p.key==null||p.key.trim().isEmpty())throw new IllegalArgumentException("请填写 API Key");return p.key.trim();}
 private static boolean isVerificationPrompt(String prompt){return "只回复：连接成功".equals(prompt);}
 private static boolean isMiniMax(String baseUrl){try{String host=ApiHttp.url(baseUrl).host();return "api.minimax.cn".equalsIgnoreCase(host)||"api.minimaxi.com".equalsIgnoreCase(host)||"api.minimax.io".equalsIgnoreCase(host);}catch(Exception ignored){return false;}}
}
