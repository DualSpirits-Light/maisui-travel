package cn.lvxu.travel;
import org.json.JSONObject;
/** Device-scoped requests only. Developer credentials never belong in this client. */
final class MeituanTravelContract {
 static JSONObject request(String city,String query,JSONObject credentials)throws Exception{
  city=city==null?"":city.trim();query=query==null?"":query.trim();
  if(city.isEmpty()||city.length()>64)throw new IllegalArgumentException("请填写目的地城市，最多 64 个字");
  if(query.isEmpty()||query.length()>1000)throw new IllegalArgumentException("请填写旅行需求，最多 1000 个字");
  JSONObject body=new JSONObject().put("city",city).put("query",query);
  for(String field:new String[]{"licenseId","deviceId","deviceSecret"}){
   String value=credentials==null?"":credentials.optString(field,"");
   if(value.isEmpty())throw new IllegalStateException("请先在设置中激活云授权，再使用美团旅行");
   body.put(field,value);
  }return body;
 }
 static String content(JSONObject response){
  Object value=response.opt("content");
  if(!(value instanceof String)||((String)value).trim().isEmpty()||((String)value).length()>131072)throw new IllegalArgumentException("暂未获得完整结果，请调整问题后重试");
  return (String)value;
 }
 static String error(int status,String code){
  if("LICENSE_FROZEN".equals(code))return "授权已冻结，请联系管理员恢复后再试";
  if("LICENSE_REVOKED".equals(code))return "授权已停用，请联系管理员";
  if("LICENSE_EXPIRED".equals(code))return "授权已过期，请续期后再试";
  if(status==401||status==403)return "云授权验证失败，请在设置中检查授权状态";
  if(status==429||"TRAVEL_LIMIT_REACHED".equals(code))return "查询频率或今日额度已达上限，请稍后再试";
  if(status==408||status==504||"TRAVEL_TIMEOUT".equals(code))return "美团查询超时，请稍后重试";
  if(status==400)return "查询内容无效，请检查目的地和旅行需求";
  return "美团旅行服务暂时不可用，请稍后再试";
 }
}
