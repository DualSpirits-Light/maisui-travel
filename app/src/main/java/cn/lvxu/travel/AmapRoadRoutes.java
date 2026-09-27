package cn.lvxu.travel;

import org.json.*;
import java.io.IOException;
import java.util.*;

/** Immutable per-render route inputs and bounded, discontinuity-preserving AMap geometry. */
final class AmapRoadRoutes {
    static final int MAX_SEGMENTS=20, MAX_POINTS=20000;
    enum Mode { WALK, DRIVE, CYCLE, TRANSIT, UNSUPPORTED }
    static Mode mode(String value) {
        if("步行".equals(value)) return Mode.WALK;
        if("自驾".equals(value)||"驾车".equals(value)||"出租车".equals(value)) return Mode.DRIVE;
        if("骑行".equals(value)||"自行车".equals(value)) return Mode.CYCLE;
        if("公交".equals(value)||"地铁".equals(value)) return Mode.TRANSIT;
        return Mode.UNSUPPORTED;
    }
    static boolean valid(Double lat,Double lon) {
        return lat!=null&&lon!=null&&Double.isFinite(lat)&&Double.isFinite(lon)&&Math.abs(lat)<=90&&Math.abs(lon)<=180;
    }
    static double[] coordinate(Trip.Stop stop) {
        if(!valid(stop.lat,stop.lon)) return null;
        if("GCJ02".equals(stop.coordinateSystem)) return new double[]{stop.lat,stop.lon};
        if(!"WGS84".equals(stop.coordinateSystem)&&!"BD09".equals(stop.coordinateSystem)) return null;
        double[] p=GeoMath.wgs(stop.lat,stop.lon,stop.coordinateSystem);
        if(p[1]>=72.004&&p[1]<=137.8347&&p[0]>=.8293&&p[0]<=55.8271)p=GeoMath.gcj(p[0],p[1]);
        return valid(p[0],p[1])?p:null;
    }
    static final class Segment {
        final int index; final String fromName,toName,label; final Mode mode;
        final double fromLat,fromLon,toLat,toLon; final String unavailable;
        Segment(int index,Trip.Stop from,Trip.Stop to) {
            this.index=index;fromName=from.name;toName=to.name;label=to.mode==null?"其他":to.mode;mode=mode(label);
            double[] f=coordinate(from),t=coordinate(to);
            fromLat=f==null?Double.NaN:f[0];fromLon=f==null?Double.NaN:f[1];toLat=t==null?Double.NaN:t[0];toLon=t==null?Double.NaN:t[1];
            unavailable=f==null||t==null?"地点缺少有效坐标，请编辑地点补充":mode==Mode.UNSUPPORTED?"暂不支持此出行方式的道路路线，请在对应交通应用中查询":"";
        }
        double[] from(){return new double[]{fromLat,fromLon};} double[] to(){return new double[]{toLat,toLon};}
    }
    static List<Segment> snapshot(List<Trip.Stop> stops) {
        List<Segment> result=new ArrayList<>();
        // Preserve every original adjacent pair: a missing middle coordinate never creates A -> C.
        for(int i=1;i<stops.size();i++)result.add(new Segment(i-1,stops.get(i-1),stops.get(i)));
        return Collections.unmodifiableList(result);
    }
    /** Rotates across failures so a persistently failing first page cannot starve later pages. */
    static final class RetryCursor {
        private int next;
        List<Segment> batch(List<Segment> segments,Set<Integer> failed) {
            List<Segment> result=new ArrayList<>();
            int size=segments.size();
            if(size==0)return result;
            int start=next%size;
            for(int visited=0;visited<size&&result.size()<MAX_SEGMENTS;visited++){
                int position=(start+visited)%size;
                Segment segment=segments.get(position);
                next=(position+1)%size;
                if(failed.contains(segment.index))result.add(segment);
            }
            return result;
        }
    }
    static final class Point {
        final double lat,lon;
        Point(double lat,double lon){this.lat=lat;this.lon=lon;}
    }
    static final class Result {
        final double meters,seconds; final List<List<Point>> lines; final boolean partial;
        Result(double m,double s,List<List<Point>> lines,boolean partial){meters=m;seconds=s;this.lines=Collections.unmodifiableList(lines);this.partial=partial;}
    }
    static void status(JSONObject object,Mode mode)throws IOException {
        boolean cycling=mode==Mode.CYCLE;
        if(cycling?object.optInt("errcode",-1)!=0:!"1".equals(object.optString("status")))
            throw new IOException(cycling?"高德骑行查询失败，请检查 Web 服务 Key、权限或额度":MapErrors.message("amap",object.optString("infocode")));
    }
    static Result parse(JSONObject object,Mode mode)throws Exception {
        status(object,mode);
        JSONObject route=object.optJSONObject(mode==Mode.CYCLE?"data":"route");
        JSONArray choices=route==null?null:route.optJSONArray(mode==Mode.TRANSIT?"transits":"paths");
        if(choices==null||choices.length()==0)throw new IOException("未找到可用路线，请调整地点或出行方式");
        JSONObject path=choices.getJSONObject(0);
        double meters=path.optDouble("distance",-1),seconds=path.optDouble("duration",-1);
        if(!Double.isFinite(meters)||!Double.isFinite(seconds)||meters<0||seconds<0)throw new IOException("路线里程或时间数据不完整，请重试");
        Geometry geometry=new Geometry();
        if(mode==Mode.TRANSIT){
            JSONArray segments=path.optJSONArray("segments");
            if(segments==null)throw new IOException("公共交通路线没有道路数据");
            for(int i=0;i<segments.length();i++){
                JSONObject segment=segments.getJSONObject(i),walking=segment.optJSONObject("walking"),bus=segment.optJSONObject("bus");
                if(walking!=null)geometry.steps(walking.optJSONArray("steps"));
                JSONArray buslines=bus==null?null:bus.optJSONArray("buslines");
                if(buslines!=null&&buslines.length()>0)geometry.polyline(buslines.getJSONObject(0).optString("polyline",""));
                JSONObject railway=segment.optJSONObject("railway"),taxi=segment.optJSONObject("taxi");
                if(railway!=null&&railway.length()>0||taxi!=null&&taxi.length()>0)geometry.partial=true;
            }
        }else geometry.steps(path.optJSONArray("steps"));
        if(geometry.lines.isEmpty())throw new IOException("高德未返回可绘制的道路路线，请重试");
        return new Result(meters,seconds,geometry.lines,geometry.partial);
    }
    private static final class Geometry {
        final List<List<Point>> lines=new ArrayList<>(); int count; boolean partial;
        void steps(JSONArray steps)throws Exception {
            if(steps==null||steps.length()==0){partial=true;return;}
            for(int i=0;i<steps.length();i++)polyline(steps.getJSONObject(i).optString("polyline",""));
        }
        void polyline(String value)throws IOException {
            if(value.isEmpty()){partial=true;return;}
            List<Point> line=new ArrayList<>();
            for(String pair:value.split(";",-1)){
                if(++count>MAX_POINTS)throw new IOException("路线坐标过多，请缩短查询路段");
                try{
                    String[] xy=pair.split(",",-1);
                    if(xy.length!=2)throw new NumberFormatException();
                    double lon=Double.parseDouble(xy[0]),lat=Double.parseDouble(xy[1]);
                    if(!valid(lat,lon))throw new NumberFormatException();
                    line.add(new Point(lat,lon));
                }catch(NumberFormatException e){
                    // Never bridge across a malformed or missing coordinate.
                    add(line);line=new ArrayList<>();partial=true;
                }
            }
            add(line);
        }
        void add(List<Point> line){if(line.size()>1)lines.add(Collections.unmodifiableList(line));else if(!line.isEmpty())partial=true;}
    }
    static String city(JSONObject object)throws Exception {
        status(object,Mode.WALK);
        JSONObject geo=object.optJSONObject("regeocode"),address=geo==null?null:geo.optJSONObject("addressComponent");
        String code=address==null?"":address.optString("citycode","");
        if(!code.matches("[0-9]{3,4}"))throw new IOException("无法确定地点所属城市，暂不能查询公共交通路线");
        return code;
    }
}
