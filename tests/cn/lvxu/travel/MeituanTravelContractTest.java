package cn.lvxu.travel;
import org.json.JSONObject;
import java.lang.reflect.*;
public final class MeituanTravelContractTest {
 static int n;
 static void ok(boolean v,String s){if(!v)throw new AssertionError(s);n++;}
 public static void main(String[] args)throws Exception{
  Class<?> c=Class.forName("cn.lvxu.travel.MeituanTravelContract");
  Method request=c.getDeclaredMethod("request",String.class,String.class,JSONObject.class),content=c.getDeclaredMethod("content",JSONObject.class),error=c.getDeclaredMethod("error",int.class,String.class);
  request.setAccessible(true);content.setAccessible(true);error.setAccessible(true);
  JSONObject credentials=new JSONObject().put("licenseId","license-id").put("deviceId","device-id").put("deviceSecret","device-secret").put("unexpected","do-not-forward");
  JSONObject body=(JSONObject)request.invoke(null," 兰州 "," 周末去哪里？ ",credentials);
  ok(body.getString("city").equals("兰州"),"trim city");ok(body.getString("query").equals("周末去哪里？"),"trim query");ok(body.length()==5&&!body.has("unexpected"),"only necessary fields");
  ok(body.getString("deviceSecret").equals("device-secret"),"device proof");ok(credentials.has("unexpected"),"do not mutate credential input");
  for(String[] values:new String[][]{{"","去哪"},{"兰州",""},{"a".repeat(65),"去哪"},{"兰州","a".repeat(1001)}}){try{request.invoke(null,values[0],values[1],credentials);throw new AssertionError("invalid input accepted");}catch(InvocationTargetException e){ok(e.getCause() instanceof IllegalArgumentException,"bounded form");}}
  try{request.invoke(null,"兰州","去哪",new JSONObject());throw new AssertionError("missing auth accepted");}catch(InvocationTargetException e){ok(e.getCause() instanceof IllegalStateException,"requires device credentials");}
  String raw="**酒店** ￥4XX [查看](https://example.com)";ok(raw.equals(content.invoke(null,new JSONObject().put("content",raw))),"preserve price and link");
  for(Object bad:new Object[]{"",JSONObject.NULL,123,"a".repeat(131073)}){try{content.invoke(null,new JSONObject().put("content",bad));throw new AssertionError("bad content accepted");}catch(InvocationTargetException e){ok(e.getCause() instanceof IllegalArgumentException,"reject bad result");}}
  ok(!((String)error.invoke(null,500,"sensitive upstream token")).contains("sensitive"),"never echo upstream error");
  ok(((String)error.invoke(null,403,"LICENSE_FROZEN")).contains("冻结"),"Chinese frozen reason");
  ok(((String)error.invoke(null,429,"DAILY_LIMIT")).contains("额度"),"quota reason");
  System.out.println("PASS: "+n+" Meituan contract assertions");
 }
}
