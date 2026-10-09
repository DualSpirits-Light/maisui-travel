package cn.lvxu.travel;
import org.json.*;
/** Request and defensive response scope regression, without live provider credentials. */
final class CityScopedSearchTest {
 static int run()throws Exception{int n=0;
  check("true".equals(MapService.provider("amap").searchUrl("fixture-only","博物馆","兰州").queryParameter("citylimit")),"AMap forces city");n++;
  check("true".equals(MapService.provider("baidu").searchUrl("fixture-only","博物馆","兰州").queryParameter("city_limit")),"Baidu forces city");n++;
  check("region(兰州,0)".equals(MapService.provider("tencent").searchUrl("fixture-only","博物馆","兰州").queryParameter("boundary")),"Tencent no automatic extension");n++;
  for(String provider:MapService.IDS){String field="amap".equals(provider)?"pois":"tencent".equals(provider)?"data":"results";JSONArray items=new JSONArray();for(String city:new String[]{"兰州市","杭州市",""}){JSONObject item=new JSONObject().put("name","博物馆");JSONObject info=new JSONObject().put("amap".equals(provider)?"cityname":"city",city);if("tencent".equals(provider))item.put("ad_info",info);else item=info;items.put(item);}JSONObject response=new JSONObject().put(field,items);check(MapService.filterCity(response,provider,"兰州").getJSONArray(field).length()==1,"only trip city for "+provider);n++;
   boolean rejected=false;try{MapService.provider(provider).searchUrl("fixture-only","博物馆","");}catch(IllegalArgumentException expected){rejected=true;}check(rejected,"no default Beijing or nationwide scope");n++;
  }return n;
 }
 private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
