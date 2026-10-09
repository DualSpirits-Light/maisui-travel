package cn.lvxu.travel;

import org.json.*;
import java.util.*;

/** Provider POIs plus transient visual estimates. Cached values are timestamped visual references, never occupancy measurements. */
final class NearbyPlace {
    final String id,name,address,city,category;
    final double lat,lon;
    final Float rating;
    int heat; long heatAt;
    NearbyPlace(String id,String name,String address,String city,String category,double lat,double lon,Float rating){
        if(!Double.isFinite(lat)||!Double.isFinite(lon)||Math.abs(lat)>90||Math.abs(lon)>180)throw new IllegalArgumentException("地点坐标无效");
        this.name=clean(name,120);if(this.name.isEmpty())throw new IllegalArgumentException("地点名称缺失");
        this.id=clean(id,100).isEmpty()?this.name+"@"+lat+","+lon:clean(id,100);
        this.address=clean(address,300);this.city=clean(city,80);this.category=clean(category,30);this.lat=lat;this.lon=lon;
        this.rating=rating!=null&&Float.isFinite(rating)&&rating>=0&&rating<=5?rating:null;
    }
    double distance(double[] wgsOrigin){if(wgsOrigin==null)return Double.POSITIVE_INFINITY;double[] dest=GeoMath.wgs(lat,lon,"GCJ02");return GeoMath.meters(wgsOrigin[0],wgsOrigin[1],dest[0],dest[1]);}
    boolean freshHeat(){long age=System.currentTimeMillis()-heatAt;return heat>0&&age>=0&&age<30*60_000L;}
    boolean hasHeat(){long age=System.currentTimeMillis()-heatAt;return (heat==1||heat==3||heat==5)&&age>=0&&age<7*24*60*60_000L;}
    boolean staleHeat(){return hasHeat()&&!freshHeat();}
    static List<NearbyPlace> sorted(Collection<NearbyPlace> source,String sort,double[] origin){
        ArrayList<NearbyPlace> out=new ArrayList<>(source);
        Comparator<NearbyPlace> compare="拥挤程度".equals(sort)?Comparator.comparingInt(p->p.hasHeat()?p.heat:Integer.MAX_VALUE):
            "评分".equals(sort)?Comparator.comparingDouble(p->p.rating==null?Double.POSITIVE_INFINITY:-p.rating):Comparator.comparingDouble(p->p.distance(origin));
        out.sort(compare.thenComparingDouble(p->p.distance(origin)).thenComparing(p->p.name));return out;
    }
    JSONObject json()throws JSONException{return new JSONObject().put("id",id).put("name",name).put("address",address).put("city",city).put("category",category).put("lat",lat).put("lon",lon).put("rating",rating==null?JSONObject.NULL:rating);}
    static NearbyPlace from(JSONObject o)throws JSONException{return new NearbyPlace(o.getString("id"),o.getString("name"),o.optString("address"),o.optString("city"),o.optString("category"),o.getDouble("lat"),o.getDouble("lon"),o.isNull("rating")?null:(float)o.getDouble("rating"));}
    private static String clean(String s,int max){s=s==null?"":s.trim();return s.substring(0,Math.min(s.length(),max));}
}
