package cn.lvxu.travel;

import org.json.*;
import java.io.*;
import java.net.*;
import javax.net.ssl.HttpsURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;

/** Conservative parser for user supplied AMap shares. Missing fields remain empty. */
final class PlaceImporter {
    static final int MAX_BYTES=2*1024*1024;
    static final class Place {
        String name="",address="",openingHours="",rating="",sourceUrl="";
        Double lat,lon;
        String coordinateSystem="GCJ02";
    }
    static boolean allowed(URI u) {
        String host=u.getHost();
        return "https".equalsIgnoreCase(u.getScheme()) && host!=null && u.getUserInfo()==null &&
            (u.getPort()==-1 || u.getPort()==443) &&
            (host.equalsIgnoreCase("amap.com")||host.toLowerCase(Locale.ROOT).endsWith(".amap.com")||host.equalsIgnoreCase("gaode.com")||host.toLowerCase(Locale.ROOT).endsWith(".gaode.com"));
    }
    static URI extractUrl(String text) {
        if(text==null||text.length()>12000)throw new IllegalArgumentException("分享文字为空或过长");
        Matcher m=Pattern.compile("https?://[^\\s<>\\\"，。；）】]+",Pattern.CASE_INSENSITIVE).matcher(text);
        if(!m.find())throw new IllegalArgumentException("请粘贴高德分享的 https 链接");
        URI uri=URI.create(m.group().replace("&amp;","&"));
        if("http".equalsIgnoreCase(uri.getScheme()))uri=URI.create("https"+uri.toString().substring(4));
        if(!allowed(uri))throw new IllegalArgumentException("目前仅识别高德地图官方分享链接");
        return uri;
    }
    static Place parseUrl(URI uri) {
        Place p=new Place();p.sourceUrl=uri.toString();Map<String,String> q=new HashMap<>();
        String query=uri.getRawQuery();
        if(query!=null)for(String part:query.split("&")){String[] bits=part.split("=",2);q.put(decode(bits[0]),bits.length==2?decode(bits[1]):"");}
        p.name=first(q,"name","poiname","name1");p.address=first(q,"address","addr");
        p.openingHours=first(q,"opening_hours","opentime","business_time");p.rating=first(q,"rating","score");
        if("wgs84".equalsIgnoreCase(q.get("coordinate")))p.coordinateSystem="WGS84";
        String position=first(q,"position","location");
        if(position.contains(",")){String[] coords=position.split(",");if(coords.length==2)setCoords(p,coords[1],coords[0]);}
        if(p.lat==null)setCoords(p,first(q,"lat","latitude"),first(q,"lon","lng","longitude"));
        return p;
    }
    static Place resolve(String sharedText) throws IOException {
        URI uri=extractUrl(sharedText);Place p=parseUrl(uri);
        // Complete coordinate links work offline; no page request is needed.
        if(p.lat!=null && !p.name.trim().isEmpty())return clean(p);
        for(int redirects=0;redirects<6;redirects++) {
            if(!allowed(uri))throw new IOException("分享链接跳转到不支持的地址，请手动填写");
            HttpsURLConnection conn=(HttpsURLConnection)uri.toURL().openConnection();
            conn.setConnectTimeout(10000);conn.setReadTimeout(10000);conn.setInstanceFollowRedirects(false);
            conn.setRequestProperty("User-Agent","MaisuiTravel/0.2 Android");
            try {
                int code=conn.getResponseCode();
                if(code>=300 && code<400) {
                    String location=conn.getHeaderField("Location");if(location==null)throw new IOException("分享跳转缺少地址");
                    uri=uri.resolve(location);
                    if(!allowed(uri))throw new IOException("分享链接跳转到不支持的地址，请手动填写");
                    merge(p,parseUrl(uri));
                    if(p.lat!=null&&!p.name.trim().isEmpty())return clean(p);
                    continue;
                }
                if(code!=200)throw new IOException("高德分享页面暂不可用（"+code+"），可以手动填写");
                String html;
                try(InputStream in=conn.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                    byte[] buf=new byte[8192];int n;while((n=in.read(buf))!=-1){if(out.size()+n>MAX_BYTES)throw new IOException("分享页面过大");out.write(buf,0,n);}html=out.toString("UTF-8");
                }
                parseHtml(p,html);return clean(p);
            } finally {conn.disconnect();}
        }
        throw new IOException("分享链接跳转次数过多，请使用完整地点链接");
    }
    static void parseHtml(Place p,String html) {
        if(p.name.trim().isEmpty()) {
            String title=find(html,"<meta[^>]*(?:property|name)=[\"']og:title[\"'][^>]*content=[\"']([^\"']+)");
            if(title.trim().isEmpty())title=find(html,"<title[^>]*>([^<]+)</title>");
            title=title.replaceAll("\\s*[-_|·]\\s*(高德地图|AMap).*$","").trim();
            if(!title.equals("高德地图") && !title.toLowerCase(Locale.ROOT).contains("javascript"))p.name=title;
        }
        // Read structured POI fields only. Do not infer ratings/opening hours from arbitrary page text.
        String normalized=html.replace("\\\"","\"");
        if(p.name.trim().isEmpty())p.name=jsonString(normalized,"poiname","poi_name");
        if(p.address.trim().isEmpty())p.address=jsonString(normalized,"address","poi_address");
        if(p.openingHours.trim().isEmpty())p.openingHours=jsonString(normalized,"opentime","opening_hours","business_time");
        if(p.rating.trim().isEmpty())p.rating=jsonString(normalized,"rating","rating_score");
        if(p.rating.trim().isEmpty())p.rating=find(normalized,"\"(?:rating|rating_score)\"\\s*:\\s*(\\d+(?:\\.\\d+)?)");
        if(p.lat==null){String location=jsonString(normalized,"location");String[] coords=location.split(",");if(coords.length==2)setCoords(p,coords[1],coords[0]);}
    }
    static String jsonString(String html,String...keys){for(String key:keys){String v=find(html,"\""+key+"\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");if(!v.trim().isEmpty()){try{return new JSONArray("[\""+v+"\"]").getString(0);}catch(JSONException ignored){}}}return "";}
    static String find(String s,String pattern){Matcher m=Pattern.compile(pattern,Pattern.CASE_INSENSITIVE|Pattern.DOTALL).matcher(s);return m.find()?m.group(1):"";}
    static String first(Map<String,String> m,String...keys){for(String k:keys){String v=m.get(k);if(v!=null&&!v.trim().isEmpty())return v;}return "";}
    static String decode(String s){try{return URLDecoder.decode(s,"UTF-8");}catch(Exception ignored){return s;}}
    static void setCoords(Place p,String lat,String lon){try{double a=Double.parseDouble(lat),b=Double.parseDouble(lon);if(Double.isFinite(a)&&Double.isFinite(b)&&Math.abs(a)<=90&&Math.abs(b)<=180){p.lat=a;p.lon=b;}}catch(Exception ignored){}}
    static void merge(Place target,Place source){if(!source.name.trim().isEmpty())target.name=source.name;if(!source.address.trim().isEmpty())target.address=source.address;if(!source.openingHours.isEmpty())target.openingHours=source.openingHours;if(!source.rating.isEmpty())target.rating=source.rating;if(source.lat!=null){target.lat=source.lat;target.lon=source.lon;target.coordinateSystem=source.coordinateSystem;}}
    static Place clean(Place p){p.name=clip(p.name,120);p.address=clip(p.address,300);p.openingHours=clip(p.openingHours,300);p.rating=clip(p.rating,30);return p;}
    static String clip(String s,int n){String clean=s.replace("&amp;","&").replace("&quot;","\"").replace("&#39;","'").replaceAll("<[^>]+>","").trim();return clean.substring(0,Math.min(n,clean.length()));}
}
