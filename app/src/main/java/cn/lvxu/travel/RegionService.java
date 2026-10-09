package cn.lvxu.travel;

import android.content.Context;
import com.amap.api.services.core.*;
import com.amap.api.services.district.*;
import com.amap.api.services.geocoder.*;
import java.util.*;
import okhttp3.*;
import org.json.*;

/** Call on a worker thread. Uses user credentials, with a 30-day administrative-directory cache. */
final class RegionService {
 static final class Node {final String name,adcode,level;final double lat,lon;final ArrayList<Node> children;Node(String n,String a,String l,double la,double lo,ArrayList<Node> c){name=n;adcode=a;level=l;lat=la;lon=lo;children=c;}}
 static ArrayList<Node> children(Context c,String parent)throws Exception{ArrayList<Node> nodes=lookup(c,parent,1).children;ArrayList<Node> out=new ArrayList<>();for(Node node:nodes){if("市辖区".equals(node.name)||"县".equals(node.name)){out.addAll(lookup(c,node.adcode,1).children);}else out.add(node);}return out;}
 static Node lookup(Context c,String keyword,int depth)throws Exception{
  String cacheKey="district-v1:"+keyword+":"+depth;android.content.SharedPreferences prefs=c.getSharedPreferences("region-directory",0);String saved=prefs.getString(cacheKey,"");long age=System.currentTimeMillis()-prefs.getLong(cacheKey+":at",0);
  if(!saved.isEmpty()&&age>=0&&age<30L*24*60*60*1000)try{return fromJson(new JSONObject(saved));}catch(Exception ignored){}
  Node result;String web=new ApiConfig(c).mapKey("amap");
  if(!web.isEmpty())result=rest(c,web,keyword,depth);else result=sdk(c,keyword,depth);
  android.content.SharedPreferences.Editor edit=prefs.edit();ArrayList<String> keys=new ArrayList<>();for(String k:prefs.getAll().keySet())if(k.endsWith(":at"))keys.add(k);keys.sort((a,b)->Long.compare(prefs.getLong(a,0),prefs.getLong(b,0)));while(keys.size()>=128){String old=keys.remove(0);edit.remove(old).remove(old.substring(0,old.length()-3));}edit.putString(cacheKey,toJson(result).toString()).putLong(cacheKey+":at",System.currentTimeMillis()).apply();return result;
 }
 private static Node rest(Context c,String key,String keyword,int depth)throws Exception{
  HttpUrl url=ApiHttp.url("https://restapi.amap.com/v3/config/district").newBuilder().addQueryParameter("key",key).addQueryParameter("keywords",keyword).addQueryParameter("subdistrict",String.valueOf(depth)).addQueryParameter("extensions","base").build();
  JSONObject json=ApiHttp.json(new Request.Builder().url(url).build());MapService.status(json,"amap");JSONArray nodes=json.optJSONArray("districts");if(nodes==null||nodes.length()==0)throw new java.io.IOException("未找到对应行政区域，请重新选择");return fromJson(nodes.getJSONObject(0));
 }
 private static Node sdk(Context c,String keyword,int depth)throws Exception{
  if(!AmapConsent.granted(c)||!AmapRuntime.prepare(c))throw new java.io.IOException("请配置高德 Web 服务 Key，或配置 Android Key 并确认高德隐私说明");
  ServiceSettings.getInstance().updatePrivacyShow(c,true,true);ServiceSettings.getInstance().updatePrivacyAgree(c,true);
  DistrictSearch search=new DistrictSearch(c);DistrictSearchQuery q=new DistrictSearchQuery();q.setKeywords(keyword);q.setSubDistrict(depth);q.setShowBoundary(false);search.setQuery(q);DistrictResult result=search.searchDistrict();
  if(result==null||result.getDistrict()==null||result.getDistrict().isEmpty())throw new java.io.IOException("行政区域查询暂时没有结果");if(result.getAMapException()!=null&&result.getAMapException().getErrorCode()!=1000)throw result.getAMapException();return fromSdk(result.getDistrict().get(0));
 }
 private static Node fromSdk(DistrictItem item){ArrayList<Node> children=new ArrayList<>();if(item.getSubDistrict()!=null)for(DistrictItem child:item.getSubDistrict())children.add(fromSdk(child));LatLonPoint p=item.getCenter();return new Node(item.getName(),item.getAdcode(),item.getLevel(),p==null?Double.NaN:p.getLatitude(),p==null?Double.NaN:p.getLongitude(),children);}
 static Node fromJson(JSONObject item)throws JSONException {ArrayList<Node> children=new ArrayList<>();JSONArray a=item.optJSONArray("districts");if(a!=null)for(int i=0;i<a.length();i++)children.add(fromJson(a.getJSONObject(i)));String[] p=item.optString("center").split(",");double lat=Double.NaN,lon=Double.NaN;try{lon=Double.parseDouble(p[0]);lat=Double.parseDouble(p[1]);}catch(Exception ignored){}return new Node(item.optString("name"),item.optString("adcode"),item.optString("level"),lat,lon,children);}
 private static JSONObject toJson(Node node)throws JSONException {JSONArray children=new JSONArray();for(Node child:node.children)children.put(toJson(child));return new JSONObject().put("name",node.name).put("adcode",node.adcode).put("level",node.level).put("center",node.lon+","+node.lat).put("districts",children);}
 static RegionSelection resolve(Context c,String province,String city,String district)throws Exception {
  if(RegionSelection.clean(province).isEmpty())return RegionSelection.nationwide();Node node=lookup(c,province,0);
  if(!RegionSelection.clean(city).isEmpty()&&!province.equals(city)){Node found=find(children(c,node.adcode),city);if(found==null)throw new java.io.IOException("该省份未找到所选城市");node=found;}
  if(!RegionSelection.clean(district).isEmpty()){Node found=find(children(c,node.adcode),district);if(found==null)throw new java.io.IOException("该城市未找到所选区县");node=found;}
  return new RegionSelection(province,city,district,node.adcode,node.level,node.lat,node.lon);
 }
 private static Node find(ArrayList<Node> nodes,String name){for(Node node:nodes)if(name.equals(node.name))return node;return null;}
 static RegionSelection locate(Context c,double[] wgs)throws Exception {
  double[] p=NearbyPlacesService.gcj(wgs);String web=new ApiConfig(c).mapKey("amap");String province,city,district,adcode;
  if(!web.isEmpty()){HttpUrl url=ApiHttp.url("https://restapi.amap.com/v3/geocode/regeo").newBuilder().addQueryParameter("key",web).addQueryParameter("location",p[1]+","+p[0]).addQueryParameter("extensions","base").build();JSONObject json=ApiHttp.json(new Request.Builder().url(url).build());MapService.status(json,"amap");JSONObject r=json.optJSONObject("regeocode"),a=r==null?null:r.optJSONObject("addressComponent");if(a==null)throw new java.io.IOException("暂时无法识别当前位置");province=a.optString("province");city=a.opt("city") instanceof String?a.optString("city"):province;if(RegionSelection.clean(city).isEmpty())city=province;district=a.optString("district");adcode=a.optString("adcode");}
  else {if(!AmapConsent.granted(c)||!AmapRuntime.prepare(c))throw new java.io.IOException("请先配置并确认高德地图服务");RegeocodeAddress a=new GeocodeSearch(c).getFromLocation(new RegeocodeQuery(new LatLonPoint(p[0],p[1]),500,GeocodeSearch.AMAP));if(a==null)throw new java.io.IOException("暂时无法识别当前位置");province=a.getProvince();city=RegionSelection.clean(a.getCity()).isEmpty()?province:a.getCity();district=a.getDistrict();adcode=a.getAdCode();}
  return new RegionSelection(province,city,district,adcode,"district",p[0],p[1]);
 }
}
