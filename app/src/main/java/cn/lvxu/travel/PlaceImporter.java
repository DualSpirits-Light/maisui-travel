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
        String name="",address="",openingHours="",rating="",sourceUrl="",poiId="",imageUrl="";
        Double lat,lon;
        String coordinateSystem="GCJ02";
    }
    static boolean allowed(URI u) {
        String host=u.getHost();
        return "https".equalsIgnoreCase(u.getScheme()) && host!=null && u.getUserInfo()==null &&
            (u.getPort()==-1 || u.getPort()==443) &&
            (host.equalsIgnoreCase("amap.com")||host.toLowerCase(Locale.ROOT).endsWith(".amap.com")||host.equalsIgnoreCase("gaode.com")||host.toLowerCase(Locale.ROOT).endsWith(".gaode.com")||(host.equalsIgnoreCase("guinness.autonavi.com")&&uriPath(u).startsWith("/activity/2020CommonLanding/")));
    }
    private static String uriPath(URI u){return u.getPath()==null?"":u.getPath();}
    static String favoriteFolderId(URI uri){
        if(!allowed(uri))return "";try{String schema=queryValue(uri,"schema");if(!schema.startsWith("amapuri://ajx_favorites/folder?"))return "";JSONObject data=new JSONObject(queryValue(URI.create(schema),"data"));String id=data.optString("ugcId","");return id.matches("[0-9]{1,40}")?id:"";}catch(Exception ignored){return "";}
    }
    private static String queryValue(URI uri,String key){String query=uri.getRawQuery();if(query!=null)for(String part:query.split("&")){String[] bits=part.split("=",2);if(decode(bits[0]).equals(key))return bits.length==2?decode(bits[1]):"";}return "";}
    static URI extractUrl(String text) {
        if(text==null||text.length()>12000)throw new IllegalArgumentException("分享文字为空或过长");
        Matcher m=Pattern.compile("https?://[^\\s<>\\\"'“”‘’，。；）】\\]\\}\\)]+",Pattern.CASE_INSENSITIVE).matcher(text);
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
        p.poiId=first(q,"poiid","poiId","id");String packed=q.get("p");if(packed!=null){String[] bits=packed.split(",",5);if(bits.length>=4){p.poiId=bits[0];setCoords(p,bits[1],bits[2]);p.name=bits[3];if(bits.length==5)p.address=bits[4];}}
        String directName=first(q,"name","poiname","name1");if(!directName.isEmpty())p.name=directName;String directAddress=first(q,"address","addr");if(!directAddress.isEmpty())p.address=directAddress;
        p.openingHours=first(q,"opening_hours","opentime","business_time");p.rating=first(q,"rating","score");
        if("wgs84".equalsIgnoreCase(q.get("coordinate")))p.coordinateSystem="WGS84";
        String position=first(q,"position","location");
        if(position.contains(",")){String[] coords=position.split(",");if(coords.length==2)setCoords(p,coords[1],coords[0]);}
        if(p.lat==null)setCoords(p,first(q,"lat","latitude"),first(q,"lon","lng","longitude"));
        return p;
    }
    static Place resolve(String sharedText) throws IOException {return resolve(sharedText,false);}
    static Place resolveFresh(String sharedText) throws IOException {return resolve(sharedText,true);}
    private static Place resolve(String sharedText,boolean fresh) throws IOException {
        URI uri=extractUrl(sharedText);if(!favoriteFolderId(uri).isEmpty())throw new IOException("已识别为高德收藏夹，但该分享页未提供地点、备注和照片列表。请在高德逐个分享地点链接导入。");Place p=parseUrl(uri);Place copied=parseShareText(sharedText);if(p.name.isEmpty())p.name=copied.name;if(p.address.isEmpty())p.address=copied.address;
        // Complete coordinate links work offline; no page request is needed.
        if(!fresh && p.lat!=null && !p.name.trim().isEmpty())return clean(p);
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
                    if(!fresh&&p.lat!=null&&!p.name.trim().isEmpty())return clean(p);
                    continue;
                }
                if(code!=200){if(!fresh&&!p.name.trim().isEmpty())return clean(p);throw new IOException("高德分享页面暂不可用（"+code+"），可以手动填写");}
                String html;
                try(InputStream in=conn.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                    byte[] buf=new byte[8192];int n;while((n=in.read(buf))!=-1){if(out.size()+n>MAX_BYTES)throw new IOException("分享页面过大");out.write(buf,0,n);}html=out.toString("UTF-8");
                }
                if(fresh){Place latest=new Place();parseHtml(latest,html);if(latest.name.isEmpty()&&latest.address.isEmpty()&&latest.lat==null&&latest.openingHours.isEmpty()&&latest.rating.isEmpty())throw new IOException("高德页面未返回可更新的地点资料");merge(p,latest);}else parseHtml(p,html);return clean(p);
            } catch(IOException e){if(!fresh&&!p.name.trim().isEmpty())return clean(p);throw e;} finally {conn.disconnect();}
        }
        throw new IOException("分享链接跳转次数过多，请使用完整地点链接");
    }
    /** Parses the human-readable text that AMap copies above a short URL. */
    static Place parseShareText(String sharedText) {
        Place p=new Place();if(sharedText==null)return p;
        for(String raw:sharedText.replace('\r','\n').split("\\n")){
            String line=raw.trim();if(line.isEmpty()||line.contains("http://")||line.contains("https://"))continue;
            boolean priceOrCategory=line.startsWith("¥")||line.startsWith("￥")||line.matches(".*(?:/人|人均).*" );
            if(p.name.isEmpty()&&!priceOrCategory){p.name=line;continue;}
            if(p.address.isEmpty()&&!priceOrCategory)p.address=line;
        }
        return p;
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
        if(p.poiId.isEmpty())p.poiId=jsonString(normalized,"poiid","poiId");
        if(p.imageUrl.isEmpty()){String photos=find(normalized,"\"photos\"\\s*:\\s*(\\[[\\s\\S]*?\\])");p.imageUrl=jsonString(photos,"url");}
        if(p.imageUrl.isEmpty())p.imageUrl=find(html,"<meta[^>]*(?:property|name)=[\"']og:image[\"'][^>]*content=[\"']([^\"']+)");
        if(p.name.trim().isEmpty())p.name=jsonString(normalized,"poiname","poi_name");
        if(p.address.trim().isEmpty())p.address=jsonString(normalized,"address","poi_address");
        if(p.openingHours.trim().isEmpty())p.openingHours=jsonString(normalized,"opentime_today","opentime_week","opentime","opening_hours","business_time");
        if(p.rating.trim().isEmpty())p.rating=jsonString(normalized,"rating","rating_score");
        if(p.rating.trim().isEmpty())p.rating=find(normalized,"\"(?:rating|rating_score)\"\\s*:\\s*(\\d+(?:\\.\\d+)?)");
        if(p.lat==null){String location=jsonString(normalized,"location");String[] coords=location.split(",");if(coords.length==2)setCoords(p,coords[1],coords[0]);}
    }
    static String jsonString(String html,String...keys){for(String key:keys){String v=find(html,"\""+key+"\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");if(!v.trim().isEmpty()){try{return new JSONArray("[\""+v+"\"]").getString(0);}catch(JSONException ignored){}}}return "";}
    static String find(String s,String pattern){Matcher m=Pattern.compile(pattern,Pattern.CASE_INSENSITIVE|Pattern.DOTALL).matcher(s);return m.find()?m.group(1):"";}
    static String first(Map<String,String> m,String...keys){for(String k:keys){String v=m.get(k);if(v!=null&&!v.trim().isEmpty())return v;}return "";}
    static String decode(String s){try{return URLDecoder.decode(s,"UTF-8");}catch(Exception ignored){return s;}}
    static void setCoords(Place p,String lat,String lon){try{double a=Double.parseDouble(lat),b=Double.parseDouble(lon);if(Double.isFinite(a)&&Double.isFinite(b)&&Math.abs(a)<=90&&Math.abs(b)<=180){p.lat=a;p.lon=b;}}catch(Exception ignored){}}
    static void merge(Place target,Place source){if(!source.poiId.isEmpty())target.poiId=source.poiId;if(!source.imageUrl.isEmpty())target.imageUrl=source.imageUrl;if(!source.name.trim().isEmpty())target.name=source.name;if(!source.address.trim().isEmpty())target.address=source.address;if(!source.openingHours.isEmpty())target.openingHours=source.openingHours;if(!source.rating.isEmpty())target.rating=source.rating;if(source.lat!=null){target.lat=source.lat;target.lon=source.lon;target.coordinateSystem=source.coordinateSystem;}}
    static Place clean(Place p){p.name=clip(p.name,120);p.address=clip(p.address,300);p.openingHours=clip(p.openingHours,300);p.rating=clip(p.rating,30);return p;}
    static String clip(String s,int n){String clean=s.replace("&amp;","&").replace("&quot;","\"").replace("&#39;","'").replaceAll("<[^>]+>","").trim();return clean.substring(0,Math.min(n,clean.length()));}
}
