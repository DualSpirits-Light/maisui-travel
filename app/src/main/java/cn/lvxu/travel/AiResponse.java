package cn.lvxu.travel;

import java.io.IOException;
import org.json.*;

/** Normalizes supported provider response envelopes into displayable final text. */
final class AiResponse {
 private AiResponse(){}

 static String compatible(JSONObject response)throws IOException {
  if(response==null)throw new IOException("服务未返回有效内容");
  JSONObject base=response.optJSONObject("base_resp");
  if(base!=null&&base.optInt("status_code",0)!=0)throw new IOException("服务返回错误，请检查配置后重试");
  JSONObject error=response.optJSONObject("error");
  if(error!=null)throw serviceError(error.optString("code","")+" "+error.optString("type","")+" "+error.optString("message", ""));
  JSONArray choices=response.optJSONArray("choices");
  JSONObject choice=choices==null||choices.length()==0?null:choices.optJSONObject(0);
  String finish=choice==null?"":choice.optString("finish_reason","");
  checkFinish(finish);
  JSONObject message=choice==null?null:choice.optJSONObject("message");
  Object content=message==null?null:message.opt("content");
  String text=content instanceof String?(String)content:"";
  text=stripLeadingThink(text).trim();
  if(text.isEmpty())throw new IOException("服务未返回可用文本");
  return text;
 }

 static String gemini(JSONObject response)throws IOException {
  if(response==null)throw new IOException("服务未返回有效内容");
  JSONObject feedback=response.optJSONObject("promptFeedback");
  if(feedback!=null&&!feedback.optString("blockReason","").isEmpty())throw new IOException("请求内容被安全策略拦截，请调整后重试");
  JSONObject error=response.optJSONObject("error");
  if(error!=null)throw serviceError(error.optString("code","")+" "+error.optString("status","")+" "+error.optString("message", ""));
  JSONArray candidates=response.optJSONArray("candidates");
  JSONObject candidate=candidates==null||candidates.length()==0?null:candidates.optJSONObject(0);
  String finish=candidate==null?"":candidate.optString("finishReason","");
  if("MAX_TOKENS".equalsIgnoreCase(finish))throw new IOException("响应因长度限制被截断，请稍后重试");
  if("SAFETY".equalsIgnoreCase(finish)||"RECITATION".equalsIgnoreCase(finish))throw new IOException("响应内容被服务安全策略拦截，请调整后重试");
  JSONObject content=candidate==null?null:candidate.optJSONObject("content");
  JSONArray parts=content==null?null:content.optJSONArray("parts");
  StringBuilder text=new StringBuilder();
  if(parts!=null)for(int i=0;i<parts.length();i++){
   JSONObject part=parts.optJSONObject(i);
   if(part!=null&&!part.optBoolean("thought",false)&&part.has("text")&&!part.isNull("text"))text.append(part.optString("text"));
  }
  String value=text.toString().trim();
  if(value.isEmpty())throw new IOException("服务未返回可用文本");
  return value;
 }

 private static void checkFinish(String finish)throws IOException {
  if("length".equalsIgnoreCase(finish)||"max_tokens".equalsIgnoreCase(finish))throw new IOException("响应因长度限制被截断，请稍后重试");
  if("content_filter".equalsIgnoreCase(finish)||"safety".equalsIgnoreCase(finish))throw new IOException("响应内容被服务安全策略拦截，请调整后重试");
 }

 private static IOException serviceError(String detail){
  String value=detail==null?"":detail.toLowerCase(java.util.Locale.ROOT);
  if(value.contains("auth")||value.contains("key")||value.contains("token")||value.contains("permission"))return new IOException("服务认证失败，请检查 API Key 或服务权限");
  if(value.contains("quota")||value.contains("rate")||value.contains("limit"))return new IOException("服务额度不足或请求过于频繁，请稍后重试");
  return new IOException("服务返回业务错误，请稍后重试");
 }

 static String stripLeadingThink(String value)throws IOException {
  String text=value==null?"":value.trim();
  if(!text.startsWith("<think>"))return text;
  int end=text.indexOf("</think>","<think>".length());
  if(end<0)throw new IOException("服务未返回完整文本，请重试");
  return text.substring(end+"</think>".length()).trim();
 }
}
