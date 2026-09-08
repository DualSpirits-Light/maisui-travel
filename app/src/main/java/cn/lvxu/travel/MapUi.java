package cn.lvxu.travel;

import android.annotation.SuppressLint;
import android.content.*;
import android.net.Uri;
import android.webkit.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Local Leaflet SDK with network tiles. No token bridge or arbitrary page scripts. */
final class MapUi {
    private final MainActivity a;
    private WebView web;
    MapUi(MainActivity activity) { a=activity; }

    @SuppressLint("SetJavaScriptEnabled")
    void show(ArrayList<Trip.Stop> stops) {
        ArrayList<Trip.Stop> known = new ArrayList<>();
        JSONArray points = new JSONArray();
        for (Trip.Stop s : stops) if (s.lat != null && s.lon != null) {
            known.add(s);
            double[] p = GeoMath.wgs(s.lat,s.lon,s.coordinateSystem);
            try { points.put(new JSONObject().put("lat",p[0]).put("lon",p[1]).put("name",s.name)); } catch(JSONException ignored) {}
        }
        a.body.addView(a.text("已定位 "+known.size()+" / "+stops.size()+" 个地点 · 连线为直线示意",13,MainActivity.MUTED));
        a.space(a.body,10);
        web = new WebView(a);
        web.setBackgroundColor(MainActivity.PALE);
        WebSettings settings=web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setUserAgentString("MaisuiTravel/0.2 (cn.lvxu.travel; https://github.com/DualSpirits-Light/maisui-travel)");
        settings.setDomStorageEnabled(false);
        web.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest req) {
                Uri uri=req.getUrl();
                if("appassets.androidplatform.net".equals(uri.getHost())) {
                    String path=uri.getPath();
                    if("/map/leaflet.js".equals(path)||"/map/leaflet.css".equals(path)) {
                        try {return new WebResourceResponse(path.endsWith(".js")?"application/javascript":"text/css","UTF-8",a.getAssets().open(path.substring(1)));}catch(IOException ignored){}
                    }
                    return new WebResourceResponse("text/plain","UTF-8",new ByteArrayInputStream(new byte[0]));
                }
                if("tile.openstreetmap.org".equals(uri.getHost())&&"https".equals(uri.getScheme()))return null;
                return new WebResourceResponse("text/plain","UTF-8",new ByteArrayInputStream(new byte[0]));
            }
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest req) {
                Uri u=req.getUrl();
                if("maisui".equals(u.getScheme()) && "pick".equals(u.getHost())) {
                    try {
                        double lat=Double.parseDouble(u.getQueryParameter("lat")),lon=Double.parseDouble(u.getQueryParameter("lon"));
                        if(Math.abs(lat)<=90 && Math.abs(lon)<=180) {Trip.Stop s=new Trip.Stop();s.name="地图选点";s.day=a.day;s.lat=lat;s.lon=lon;s.coordinateSystem="WGS84";a.stopEditorDraft(s);}
                    }catch(Exception ignored){}
                } else if(req.hasGesture() && "https".equals(u.getScheme()) && "www.openstreetmap.org".equals(u.getHost())) {
                    try{a.startActivity(new Intent(Intent.ACTION_VIEW,u));}catch(ActivityNotFoundException ignored){}
                }
                return true;
            }
        });
        web.setOnTouchListener((v,event)->{v.getParent().requestDisallowInterceptTouchEvent(event.getAction()!=MotionEvent.ACTION_UP);return false;});
        a.body.addView(web,new LinearLayout.LayoutParams(-1,a.dp(380)));
        String data=points.toString().replace("<","\\u003c").replace("\u2028","\\u2028").replace("\u2029","\\u2029");
        String html="<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'><link rel='stylesheet' href='leaflet.css'><style>html,body,#map{height:100%;margin:0;font:13px sans-serif}#hint{position:absolute;z-index:1000;top:10px;left:55px;background:#fff;padding:8px;border-radius:8px;max-width:70%}.pin{background:#23644f;color:white;border:2px solid white;border-radius:50%;text-align:center;line-height:28px;font-weight:bold}</style></head><body><div id='map'></div><div id='hint'>双指缩放 · 长按地图添加地点</div><script src='leaflet.js'></script><script>"+
            "const points="+data+";const map=L.map('map',{zoomControl:true}).setView([30.25,120.14],12);"+
            "const tile=L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:19,updateWhenIdle:true,keepBuffer:1,attribution:'&copy; <a href=\"https://www.openstreetmap.org/copyright\">OpenStreetMap</a>'}).addTo(map);tile.on('tileerror',()=>document.getElementById('hint').textContent='底图暂不可用，动线和测距仍可查看');"+
            "const coords=[];points.forEach((p,i)=>{const c=[p.lat,p.lon];coords.push(c);const label=document.createElement('span');label.textContent=(i+1)+'. '+p.name;L.marker(c,{icon:L.divIcon({className:'pin',html:String(i+1),iconSize:[28,28],iconAnchor:[14,14]})}).bindPopup(label).addTo(map)});"+
            "if(coords.length>1){L.polyline(coords,{color:'#23644f',weight:4,dashArray:'8 5'}).addTo(map);map.fitBounds(coords,{padding:[30,30],maxZoom:16})}else if(coords.length===1){map.setView(coords[0],15)}"+
            "map.on('contextmenu',e=>{location.href='maisui://pick?lat='+e.latlng.lat+'&lon='+e.latlng.lng});</script></body></html>";
        web.loadDataWithBaseURL("https://appassets.androidplatform.net/map/",html,"text/html","UTF-8",null);
        a.space(a.body,14);
        if(known.size()>1) {
            LinearLayout c=a.card(a.body);
            c.addView(a.bold("两点测距",18,MainActivity.INK));a.space(c,10);
            String[] names=new String[known.size()];for(int i=0;i<names.length;i++)names[i]=(i+1)+". "+known.get(i).name;
            Spinner from=a.select(c,"起点",names,names[0]),to=a.select(c,"终点",names,names[1]);
            TextView result=a.bold("",24,MainActivity.GREEN);c.addView(result);
            AdapterView.OnItemSelectedListener listener=new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> v){}public void onItemSelected(AdapterView<?> v,View x,int p,long id){Trip.Stop s=known.get(from.getSelectedItemPosition()),t=known.get(to.getSelectedItemPosition());double[] u=GeoMath.wgs(s.lat,s.lon,s.coordinateSystem),w=GeoMath.wgs(t.lat,t.lon,t.coordinateSystem);result.setText("直线约 "+GeoMath.distance(GeoMath.meters(u[0],u[1],w[0],w[1])));}};
            from.setOnItemSelectedListener(listener);to.setOnItemSelectedListener(listener);
        }
        a.body.addView(a.text("地图基于 OpenStreetMap。直线距离不是步行或驾车里程；高德坐标经近似转换展示。",12,MainActivity.MUTED));
        if(known.size()<stops.size()) {a.space(a.body,8);a.body.addView(a.text("未定位的地点可在“编辑地点”中填写经纬度，或导入含位置的高德分享链接。",12,MainActivity.MUTED));}
    }
    void destroy(){if(web!=null){web.stopLoading();web.loadUrl("about:blank");web.destroy();web=null;}}
}
