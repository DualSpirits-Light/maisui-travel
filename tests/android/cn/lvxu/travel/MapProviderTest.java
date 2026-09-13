package cn.lvxu.travel;
import org.json.*;import android.net.Uri;
final class MapProviderTest {
 static int run()throws Exception {int n=0;String key="fixture-only";check(MapService.provider("amap").searchUrl(key,"西湖","杭州").queryParameter("key").equals(key),"amap key query");n++;check(MapService.provider("tencent").searchUrl(key,"西湖","杭州").queryParameter("boundary").equals("region(杭州,0)"),"tencent region");n++;check(MapService.provider("baidu").searchUrl(key,"西湖","杭州").queryParameter("ak").equals(key),"baidu ak query");n++;
 JSONObject poi=new JSONObject().put("status","1").put("pois",new JSONArray().put(new JSONObject().put("id","x").put("name","西湖").put("location","120.15,30.25")));PlaceImporter.Place place=MapService.provider("amap").parse(poi).get(0);check(place.lat==30.25&&place.lon==120.15&&place.coordinateSystem.equals("GCJ02"),"amap lonlat mapping");n++;
 JSONObject t=new JSONObject().put("status",0).put("data",new JSONArray().put(new JSONObject().put("title","公园").put("location",new JSONObject().put("lat",30).put("lng",120))));place=MapService.provider("tencent").parse(t).get(0);check(MapLinks.parse(Uri.parse(place.sourceUrl)).name.equals("公园"),"tencent source roundtrip");n++;
 JSONObject b=new JSONObject().put("status",0).put("results",new JSONArray().put(new JSONObject().put("name","公园").put("location",new JSONObject().put("lat",30).put("lng",120))));place=MapService.provider("baidu").parse(b).get(0);check(place.coordinateSystem.equals("BD09")&&MapLinks.parse(Uri.parse(place.sourceUrl)).coordinateSystem.equals("BD09"),"baidu coordinates remain BD09");n++;
 boolean rejected=false;try{MapService.provider("tencent").parse(new JSONObject().put("status",311));}catch(Exception e){rejected=true;}check(rejected,"provider error is not validation success");n++;
 check(!MapLinks.allowed(Uri.parse("https://map.qq.com.evil.example/a"))&&!MapLinks.allowed(Uri.parse("http://map.qq.com/a")),"share URL confinement");n++;
 double[] from={30,120},to={31,121};check(MapRoutes.adapter("amap").url(key,from,to,"walking").queryParameter("origin").equals("120.000000,30.000000"),"amap route lonlat");n++;check(MapRoutes.adapter("baidu").url(key,from,to,"walking").queryParameter("origin").equals("30.000000,120.000000"),"baidu route latlon");n++;return n;
 }
 static void check(boolean condition,String label){if(!condition)throw new AssertionError(label);}
}
