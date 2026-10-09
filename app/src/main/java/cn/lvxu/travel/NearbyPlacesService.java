package cn.lvxu.travel;

import android.content.Context;
import com.amap.api.services.core.*;
import com.amap.api.services.geocoder.*;
import com.amap.api.services.poisearch.*;
import java.util.*;
import okhttp3.*;
import org.json.*;

/** City discovery uses the device origin, independently of the selected travel destination. */
final class NearbyPlacesService {
    static final String[] CATEGORIES={"收藏","人文","博物馆","风景","美食","逛街"};
    static final class Result {final String city;final ArrayList<NearbyPlace> places;Result(String c,ArrayList<NearbyPlace> p){city=c;places=p;}}
    static String types(String category){switch(category){case "博物馆":return "140100";case "人文":return "110200|140100|140200";case "美食":return "050000";case "逛街":return "060100|060101|060102";default:return "110000";}}
    static String keyword(String category){return "美食".equals(category)?"小吃街|美食街":"";}
    static double[] gcj(double[] wgs){return wgs[1]>=72.004&&wgs[1]<=137.8347&&wgs[0]>=.8293&&wgs[0]<=55.8271?GeoMath.gcj(wgs[0],wgs[1]):wgs.clone();}
    static Result search(Context c,String category,double[] wgs,String knownCity,boolean sdk)throws Exception{
        double[] point=gcj(wgs);if(!sdk)return rest(c,category,point,knownCity);
        try{return sdk(c,category,point,knownCity);}catch(Exception|LinkageError unavailable){if(new ApiConfig(c).mapKey("amap").isEmpty())throw unavailable;return rest(c,category,point,knownCity);}
    }
    /** Explicit administrative browsing never substitutes the device city or lodging. */
    static Result searchRegion(Context c,String category,RegionSelection region,boolean sdk)throws Exception{
        if(region==null)throw new IllegalArgumentException("请选择行政区域");String web=new ApiConfig(c).mapKey("amap");
        if(!web.isEmpty())return parseRegion(ApiHttp.json(new Request.Builder().url(regionUrl(web,category,region)).build()),category,region);
        if(!sdk||!AmapConsent.granted(c)||!AmapRuntime.prepare(c))throw new java.io.IOException("请先配置高德 Web 服务 Key，或确认 Android 地图服务");
        ServiceSettings settings=ServiceSettings.getInstance();settings.updatePrivacyShow(c,true,true);settings.updatePrivacyAgree(c,true);
        boolean country="country".equals(region.level);PoiSearchV2.Query query=new PoiSearchV2.Query(keyword(category),types(category),country?"":region.adcode);query.setCityLimit(!country);query.setPageSize(20);query.setPageNum(1);query.setShowFields(new PoiSearchV2.ShowFields(PoiSearchV2.ShowFields.BUSINESS));
        PoiResultV2 result=new PoiSearchV2(c,query).searchPOI();ArrayList<NearbyPlace> out=new ArrayList<>();if(result==null||result.getPois()==null)throw new java.io.IOException("地点查询暂时没有返回结果");
        for(PoiItemV2 p:result.getPois())if(p.getLatLonPoint()!=null&&!first(p.getTitle()).isEmpty()&&contains(region,p.getAdCode())){
            Float score=null;Business b=p.getBusiness();try{if(b!=null)score=Float.valueOf(b.getmRating());}catch(Exception ignored){}
            out.add(new NearbyPlace(p.getPoiId(),p.getTitle(),p.getSnippet(),first(p.getCityName(),p.getProvinceName()),category,p.getLatLonPoint().getLatitude(),p.getLatLonPoint().getLongitude(),score));
        }
        return new Result(region.label(),out);
    }
    static HttpUrl regionUrl(String key,String category,RegionSelection region){HttpUrl.Builder builder=ApiHttp.url("https://restapi.amap.com/v3/place/text").newBuilder().addQueryParameter("key",key).addQueryParameter("types",types(category)).addQueryParameter("keywords",keyword(category)).addQueryParameter("extensions","all").addQueryParameter("offset","20");if(!"country".equals(region.level))builder.addQueryParameter("city",region.adcode).addQueryParameter("citylimit","true");return builder.build();}
    static boolean contains(RegionSelection region,String returnedCode){if("country".equals(region.level))return true;String code=first(returnedCode),scope=region.adcode;if(!scope.matches("[0-9]{6}")||!code.matches("[0-9]{6}"))return false;if("province".equals(region.level))return code.startsWith(scope.substring(0,2));if("city".equals(region.level))return code.startsWith(scope.substring(0,4));return code.equals(scope);}
    static Result parseRegion(JSONObject response,String category,RegionSelection region)throws Exception{
        MapService.status(response,"amap");ArrayList<NearbyPlace> out=new ArrayList<>();JSONArray pois=response.optJSONArray("pois");if(pois==null)return new Result(region.label(),out);
        for(int i=0;i<Math.min(20,pois.length());i++){JSONObject poi=pois.getJSONObject(i);if(!contains(region,poi.optString("adcode")))continue;String[] xy=poi.optString("location").split(",");String name=poi.optString("name");if(xy.length!=2||name.trim().isEmpty())continue;
            try{double lon=Double.parseDouble(xy[0]),lat=Double.parseDouble(xy[1]);if(!Double.isFinite(lat)||!Double.isFinite(lon)||Math.abs(lat)>90||Math.abs(lon)>180)continue;JSONObject business=poi.optJSONObject("biz_ext");Float rating=null;try{if(business!=null)rating=Float.valueOf(business.optString("rating"));}catch(Exception ignored){}String actualCity=first(poi.opt("cityname") instanceof String?poi.optString("cityname"):"",poi.optString("pname"));out.add(new NearbyPlace(poi.optString("id"),name,poi.optString("address"),actualCity,category,lat,lon,rating));}catch(NumberFormatException ignored){}
        }
        return new Result(region.label(),out);
    }
    private static Result sdk(Context c,String category,double[] point,String city)throws Exception{
        if(!AmapConsent.granted(c)||!AmapRuntime.prepare(c))throw new java.io.IOException("请先完成高德 Android 服务配置和隐私确认");
        ServiceSettings settings=ServiceSettings.getInstance();settings.updatePrivacyShow(c,true,true);settings.updatePrivacyAgree(c,true);
        if(city.isEmpty()){
            RegeocodeAddress address=new GeocodeSearch(c).getFromLocation(new RegeocodeQuery(new LatLonPoint(point[0],point[1]),500,GeocodeSearch.AMAP));
            if(address==null)throw new java.io.IOException("暂时无法识别当前位置的城市");city=first(address.getCity(),address.getProvince());
        }
        if(city.isEmpty())throw new java.io.IOException("当前位置暂不支持城市查询");
        PoiSearchV2.Query query=new PoiSearchV2.Query(keyword(category),types(category),city);query.setCityLimit(true);query.setPageSize(20);query.setPageNum(1);
        query.setShowFields(new PoiSearchV2.ShowFields(PoiSearchV2.ShowFields.BUSINESS));
        PoiResultV2 result=new PoiSearchV2(c,query).searchPOI();ArrayList<NearbyPlace> out=new ArrayList<>();
        if(result==null||result.getPois()==null)throw new java.io.IOException("地点查询暂时没有返回结果");
        for(PoiItemV2 p:result.getPois())if(p.getLatLonPoint()!=null&&!first(p.getTitle()).isEmpty()){
            Float score=null;Business b=p.getBusiness();try{if(b!=null)score=Float.valueOf(b.getmRating());}catch(Exception ignored){}
            out.add(new NearbyPlace(p.getPoiId(),p.getTitle(),p.getSnippet(),city,category,p.getLatLonPoint().getLatitude(),p.getLatLonPoint().getLongitude(),score));
        }
        return new Result(city,out);
    }
    static HttpUrl searchUrl(String key,String category,String city){return ApiHttp.url("https://restapi.amap.com/v3/place/text").newBuilder().addQueryParameter("key",key).addQueryParameter("city",city).addQueryParameter("citylimit","true").addQueryParameter("types",types(category)).addQueryParameter("keywords",keyword(category)).addQueryParameter("extensions","all").addQueryParameter("offset","20").build();}
    private static Result rest(Context c,String category,double[] point,String city)throws Exception{
        String key=new ApiConfig(c).mapKey("amap");if(key.isEmpty())throw new java.io.IOException("请先配置高德 Web 服务 Key");
        if(city.isEmpty()){
            HttpUrl url=ApiHttp.url("https://restapi.amap.com/v3/geocode/regeo").newBuilder().addQueryParameter("key",key).addQueryParameter("location",point[1]+","+point[0]).addQueryParameter("extensions","base").build();
            JSONObject json=ApiHttp.json(new Request.Builder().url(url).build());MapService.status(json,"amap");JSONObject regeo=json.optJSONObject("regeocode"),address=regeo==null?null:regeo.optJSONObject("addressComponent");
            if(address!=null)city=first(address.opt("city") instanceof String?address.optString("city"):"",address.optString("province"));
        }
        if(city.isEmpty())throw new java.io.IOException("暂时无法识别当前位置的城市");
        ArrayList<NearbyPlace> out=new ArrayList<>();
        for(PlaceImporter.Place p:new MapService.Amap().parse(ApiHttp.json(new Request.Builder().url(searchUrl(key,category,city)).build()))){
            if(p.lat==null||p.lon==null)continue;Float score=null;try{score=Float.valueOf(p.rating);}catch(Exception ignored){}
            out.add(new NearbyPlace(p.poiId,p.name,p.address,city,category,p.lat,p.lon,score));
        }
        return new Result(city,out);
    }
    private static String first(String... values){for(String value:values)if(value!=null&&!value.trim().isEmpty())return value.trim();return "";}
}
