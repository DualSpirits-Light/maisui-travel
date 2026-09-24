package cn.lvxu.travel;

/** Safe, provider-specific descriptions for REST API status codes. */
final class MapErrors {
 private MapErrors(){}

 static boolean success(String provider,String code){
  return "amap".equals(provider)?"1".equals(code):"0".equals(code);
 }

 static String message(String provider,String code){
  String safe=safeCode(code);
  if("amap".equals(provider)){
   if("10009".equals(safe))return "高德地图错误（10009）：Web 服务平台类型不匹配。请确认 Web 服务 Key 的平台类型；Android 密钥请用于 SDK。";
   return "高德地图服务返回错误码（"+safe+"），请检查 Web 服务配置及权限。";
  }
  if("tencent".equals(provider)){
   if("199".equals(safe))return "腾讯地图错误（199）：WebServiceAPI 服务未开通或 Key 类型不匹配。请开通 WebServiceAPI，并配置 Web 服务类型 Key。";
   return "腾讯地图服务返回错误码（"+safe+"），请检查 WebServiceAPI 配置及权限。";
  }
  if("baidu".equals(provider)){
   if("240".equals(safe))return "百度地图错误（240）：Web 服务不可用。请检查应用状态、是否误填 Android AK 及 Web 服务权限。";
   return "百度地图服务返回错误码（"+safe+"），请检查 Web 服务配置及权限。";
  }
  return "地图服务返回错误码（"+safe+"），请检查服务配置及权限。";
 }

 private static String safeCode(String code){
  if(code==null)return "未知";
  String value=code.trim();
  return value.matches("[0-9]{1,8}")?value:"未知";
 }
}
