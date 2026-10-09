package cn.lvxu.travel;

import android.content.*;
import android.net.Uri;

/** Route start is supplied by the map app's current position, never by trip lodging. */
final class NearbyNavigation {
    static Uri app(String provider,NearbyPlace p){
        if("baidu".equals(provider))return Uri.parse("baidumap://map/direction").buildUpon().appendQueryParameter("destination","latlng:"+p.lat+","+p.lon+"|name:"+p.name).appendQueryParameter("coord_type","gcj02").appendQueryParameter("mode","driving").appendQueryParameter("src","麦穗旅序").build();
        if("tencent".equals(provider))return Uri.parse("qqmap://map/routeplan").buildUpon().appendQueryParameter("type","drive").appendQueryParameter("to",p.name).appendQueryParameter("tocoord",p.lat+","+p.lon).appendQueryParameter("referer","maisui").build();
        return Uri.parse("amapuri://route/plan/").buildUpon().appendQueryParameter("sourceApplication","麦穗旅序").appendQueryParameter("dlat",String.valueOf(p.lat)).appendQueryParameter("dlon",String.valueOf(p.lon)).appendQueryParameter("dname",p.name).appendQueryParameter("dev","0").appendQueryParameter("t","0").build();
    }
    static void open(MainActivity a,NearbyPlace p){String provider=new ApiConfig(a).mapProvider();try{a.startActivity(new Intent(Intent.ACTION_VIEW,app(provider,p)));}catch(ActivityNotFoundException e){
        Uri web=Uri.parse("https://uri.amap.com/navigation").buildUpon().appendQueryParameter("to",p.lon+","+p.lat+","+p.name).appendQueryParameter("mode","car").appendQueryParameter("coordinate","gaode").appendQueryParameter("callnative","1").build();
        try{a.startActivity(new Intent(Intent.ACTION_VIEW,web));}catch(ActivityNotFoundException ignored){a.toast("请安装地图应用或浏览器");}
    }}
}
