package cn.lvxu.travel;
import android.content.*;
import android.net.Uri;
import okhttp3.*;
import org.json.*;
import java.io.IOException;
import java.util.*;

/** Map REST and external navigation adapters. Pages never choose a vendor endpoint. */
final class MapService {
 static final String[] IDS={"amap","tencent","baidu"},NAMES={"高德地图","腾讯地图","百度地图"};
 interface Provider {String id(); HttpUrl searchUrl(String key,String query,String city); ArrayList<PlaceImporter.Place> parse(JSONObject o)throws Exception; Uri navigation(Trip.Stop stop,String city);}
 static Provider provider(String id){if("tencent".equals(id))return new Tencent();if("baidu".equals(id))return new Baidu();return new Amap();}
 static String name(String id){for(int i=0;i<IDS.length;i++)if(IDS[i].equals(id))return NAMES[i];return NAMES[0];}
 private final ApiConfig config;
 MapService(Context c){config=new ApiConfig(c);}
 String name(){return name(config.mapProvider());}
 boolean configured(){return !config.mapKey(config.mapProvider()).isEmpty();}
 ArrayList<PlaceImporter.Place> search(String query,String city)throws Exception {return search(config.mapProvider(),config.mapKey(config.mapProvider()),query,city);}
 static ArrayList<PlaceImporter.Place> search(String id,String key,String query,String city)throws Exception {if(key.trim().isEmpty())throw new IOException("请先在高级设置中配置地图 API Key");Provider p=provider(id);return p.parse(ApiHttp.json(new Request.Builder().url(p.searchUrl(key,query,city.isEmpty()?"北京":city)).build()));}
 static void validate(String id,String key)throws Exception {search(id,key,"博物馆","北京");}
 void navigate(Context c,Trip.Stop s,String city){try{c.startActivity(new Intent(Intent.ACTION_VIEW,provider(config.mapProvider()).navigation(s,city)));}catch(ActivityNotFoundException e){android.widget.Toast.makeText(c,"请安装浏览器或对应地图应用",android.widget.Toast.LENGTH_LONG).show();}}
 static double[] gcj(Trip.Stop s){double[] p=GeoMath.wgs(s.lat,s.lon,s.coordinateSystem);if(p[1]<72.004||p[1]>137.8347||p[0]<.8293||p[0]>55.8271)return p;return GeoMath.gcj(p[0],p[1]);}
 static PlaceImporter.Place place(String name,String address,double lat,double lon,String system,String url){PlaceImporter.Place p=new PlaceImporter.Place();p.name=PlaceImporter.clip(name,120);p.address=PlaceImporter.clip(address,300);if(Double.isFinite(lat)&&Double.isFinite(lon)&&Math.abs(lat)<=90&&Math.abs(lon)<=180){p.lat=lat;p.lon=lon;p.coordinateSystem=system;}p.sourceUrl=url;return p;}
 static void status(JSONObject o,boolean amap)throws Exception{if(amap? !"1".equals(o.optString("status")):o.optInt("status",-1)!=0)throw new IOException("地图服务验证失败，请检查 Web 服务密钥及权限（"+(amap?o.optString("infocode","未知"):String.valueOf(o.optInt("status",-1)))+"）");}
 static class Amap implements Provider {
  public String id(){return "amap";}
  public HttpUrl searchUrl(String k,String q,String city){return ApiHttp.url("https://restapi.amap.com/v3/place/text").newBuilder().addQueryParameter("key",k).addQueryParameter("keywords",q).addQueryParameter("city",city).addQueryParameter("offset","20").addQueryParameter("extensions","all").build();}
  public ArrayList<PlaceImporter.Place> parse(JSONObject o)throws Exception{status(o,true);ArrayList<PlaceImporter.Place> out=new ArrayList<>();JSONArray items=o.optJSONArray("pois");if(items==null)return out;for(int i=0;i<Math.min(20,items.length());i++){JSONObject x=items.getJSONObject(i);String[] xy=x.optString("location","").split(",");if(xy.length!=2)continue;try{PlaceImporter.Place p=place(x.optString("name"),x.optString("address"),Double.parseDouble(xy[1]),Double.parseDouble(xy[0]),"GCJ02","https://www.amap.com/place/"+x.optString("id"));p.poiId=x.optString("id");JSONObject b=x.optJSONObject("biz_ext");if(b!=null){p.rating=b.optString("rating","");p.openingHours=b.optString("open_time","");}out.add(p);}catch(NumberFormatException ignored){}}return out;}
  public Uri navigation(Trip.Stop s,String city){Uri.Builder b=Uri.parse("https://uri.amap.com/search").buildUpon().appendQueryParameter("keyword",s.name).appendQueryParameter("city",city).appendQueryParameter("view","map");if(s.lat!=null&&s.lon!=null){double[] p=gcj(s);b=Uri.parse("https://uri.amap.com/marker").buildUpon().appendQueryParameter("position",p[1]+","+p[0]).appendQueryParameter("name",s.name);}return b.build();}
 }
 static class Tencent implements Provider {
  public String id(){return "tencent";}
  public HttpUrl searchUrl(String k,String q,String city){return ApiHttp.url("https://apis.map.qq.com/ws/place/v1/search").newBuilder().addQueryParameter("key",k).addQueryParameter("keyword",q).addQueryParameter("boundary","region("+city+",0)").addQueryParameter("page_size","20").build();}
  public ArrayList<PlaceImporter.Place> parse(JSONObject o)throws Exception{status(o,false);ArrayList<PlaceImporter.Place> out=new ArrayList<>();JSONArray ar=o.optJSONArray("data");if(ar==null)return out;for(int i=0;i<Math.min(20,ar.length());i++){JSONObject x=ar.getJSONObject(i),l=x.optJSONObject("location");if(l==null)continue;PlaceImporter.Place p=place(x.optString("title"),x.optString("address"),l.optDouble("lat"),l.optDouble("lng"),"GCJ02","");p.sourceUrl=Uri.parse("https://apis.map.qq.com/uri/v1/marker").buildUpon().appendQueryParameter("marker","coord:"+p.lat+","+p.lon+";title:"+p.name+";addr:"+p.address).appendQueryParameter("referer","maisui").build().toString();out.add(p);}return out;}
  public Uri navigation(Trip.Stop s,String city){if(s.lat==null||s.lon==null)return Uri.parse("https://map.qq.com/").buildUpon().appendQueryParameter("keyword",s.name).build();double[] p=gcj(s);return Uri.parse("https://apis.map.qq.com/uri/v1/marker").buildUpon().appendQueryParameter("marker","coord:"+p[0]+","+p[1]+";title:"+s.name+";addr:"+s.address).appendQueryParameter("referer","maisui").build();}
 }
 static class Baidu implements Provider {
  public String id(){return "baidu";}
  public HttpUrl searchUrl(String k,String q,String city){return ApiHttp.url("https://api.map.baidu.com/place/v2/search").newBuilder().addQueryParameter("ak",k).addQueryParameter("query",q).addQueryParameter("region",city).addQueryParameter("output","json").addQueryParameter("scope","2").addQueryParameter("page_size","20").build();}
  public ArrayList<PlaceImporter.Place> parse(JSONObject o)throws Exception{status(o,false);ArrayList<PlaceImporter.Place> out=new ArrayList<>();JSONArray ar=o.optJSONArray("results");if(ar==null)return out;for(int i=0;i<Math.min(20,ar.length());i++){JSONObject x=ar.getJSONObject(i),l=x.optJSONObject("location");if(l==null)continue;PlaceImporter.Place p=place(x.optString("name"),x.optString("address"),l.optDouble("lat"),l.optDouble("lng"),"BD09","");p.sourceUrl=Uri.parse("https://api.map.baidu.com/marker").buildUpon().appendQueryParameter("location",p.lat+","+p.lon).appendQueryParameter("title",p.name).appendQueryParameter("content",p.address).appendQueryParameter("output","html").build().toString();JSONObject d=x.optJSONObject("detail_info");if(d!=null)p.rating=d.optString("overall_rating","");out.add(p);}return out;}
  public Uri navigation(Trip.Stop s,String city){if(s.lat==null||s.lon==null)return Uri.parse("https://map.baidu.com/search/"+Uri.encode(s.name));double[] p=gcj(s);return Uri.parse("https://api.map.baidu.com/marker").buildUpon().appendQueryParameter("location",p[0]+","+p[1]).appendQueryParameter("coord_type","gcj02").appendQueryParameter("title",s.name).appendQueryParameter("content",s.address).appendQueryParameter("output","html").build();}
 }
}
