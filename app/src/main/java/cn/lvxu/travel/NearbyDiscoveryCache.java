package cn.lvxu.travel;

import android.content.*;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

/** Bounded disposable discovery cache. Coordinates belong to public POIs, never device fixes. */
final class NearbyDiscoveryCache {
    static final long TTL_MS=30*60_000L, RETENTION_MS=7*24*60*60_000L;
    private static final Object LOCK=new Object();
    private static Long observedRevision;
    private final Context context;private final SharedPreferences prefs;
    NearbyDiscoveryCache(Context c){context=c.getApplicationContext();prefs=context.getSharedPreferences("nearby-discovery-cache-v1",Context.MODE_PRIVATE);}
    private String source(){try{ApiConfig a=new ApiConfig(context);String value=a.amapAndroidKey()+"\n"+a.mapKey("amap")+"\n"+a.baiduAndroidKey();byte[] bytes=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));StringBuilder out=new StringBuilder();for(byte b:bytes)out.append(String.format(Locale.ROOT,"%02x",b&255));return out.toString();}catch(Exception e){return "invalid";}}
    private JSONObject read(){try{JSONObject o=new JSONObject(prefs.getString("data","{}"));String signature=source();long revision=new ApiConfig(context).revision();boolean changed=observedRevision!=null&&observedRevision!=revision;observedRevision=revision;if(!signature.equals(o.optString("source"))||changed){JSONObject empty=new JSONObject().put("source",signature).put("revision",revision).put("pois",new JSONObject()).put("heat",new JSONObject()).put("regions",new JSONObject());write(empty);return empty;}return o;}catch(Exception e){return new JSONObject();}}
    private void write(JSONObject o){prefs.edit().putString("data",o.toString()).apply();}
    private static boolean fresh(long at,long now){return at>0&&now>=at&&now-at<TTL_MS;}
    private static boolean retained(long at,long now){return at>0&&now>=at&&now-at<RETENTION_MS;}
    private static String key(String region,String category){return region.trim()+"|"+category.trim();}
    ArrayList<NearbyPlace> getPlaces(String region,String category){synchronized(LOCK){JSONObject o=read(),entry=o.optJSONObject("pois")==null?null:o.optJSONObject("pois").optJSONObject(key(region,category));ArrayList<NearbyPlace> out=new ArrayList<>();if(entry==null||!retained(entry.optLong("at"),System.currentTimeMillis()))return out;try{JSONArray values=entry.getJSONArray("places");for(int i=0;i<values.length();i++){NearbyPlace p=NearbyPlace.from(values.getJSONObject(i));applyHeatLocked(o,p);out.add(p);}}catch(Exception ignored){out.clear();}return out;}}
    void putPlaces(String region,String category,Collection<NearbyPlace> places){synchronized(LOCK){try{JSONObject o=read(),entries=o.getJSONObject("pois");JSONArray values=new JSONArray();for(NearbyPlace p:places){if(values.length()>=50)break;values.put(p.json());}String actualCity=places.isEmpty()?"":places.iterator().next().city;JSONObject registry=o.optJSONObject("regions");if(registry==null){registry=new JSONObject();o.put("regions",registry);}if(!actualCity.isEmpty()){registry.put(actualCity,new JSONObject().put("at",System.currentTimeMillis()));o.put("latestCity",actualCity);}trim(registry,12);entries.put(key(region,category),new JSONObject().put("at",System.currentTimeMillis()).put("region",region.trim()).put("places",values));trim(entries,36);pruneLocked(o);write(o);}catch(Exception ignored){}}}
    void applyHeat(NearbyPlace p){synchronized(LOCK){applyHeatLocked(read(),p);}}
    private void applyHeatLocked(JSONObject o,NearbyPlace p){JSONObject all=o.optJSONObject("heat"),entry=all==null?null:all.optJSONObject(p.id);if(entry!=null&&retained(entry.optLong("at"),System.currentTimeMillis())&&Math.abs(entry.optDouble("lat")-p.lat)<.000001&&Math.abs(entry.optDouble("lon")-p.lon)<.000001){p.heat=entry.optInt("level");p.heatAt=entry.optLong("at");}else{p.heat=0;p.heatAt=0;}}
    void putHeat(NearbyPlace p,int level,long sampledAt){putHeat(p.city,p,level,sampledAt);}
    void putHeat(String region,NearbyPlace p,int level,long sampledAt){if(level!=1&&level!=3&&level!=5)return;synchronized(LOCK){try{JSONObject o=read();if(putHeatLocked(o,region,p,level,sampledAt)){trim(o.getJSONObject("heat"),500);pruneLocked(o);write(o);}}catch(Exception ignored){}}}
    void putHeatBatch(String scope,Collection<NearbyPlace> places){synchronized(LOCK){try{JSONObject o=read();boolean changed=false;for(NearbyPlace p:places)changed|=putHeatLocked(o,scope,p,p.heat,p.heatAt);if(changed){trim(o.getJSONObject("heat"),500);pruneLocked(o);write(o);}}catch(Exception ignored){}}}
    private static boolean putHeatLocked(JSONObject o,String scope,NearbyPlace p,int level,long at)throws JSONException{if((level!=1&&level!=3&&level!=5)||!retained(at,System.currentTimeMillis()))return false;JSONObject all=o.optJSONObject("heat");if(all==null){all=new JSONObject();o.put("heat",all);}JSONObject old=all.optJSONObject(p.id);if(old!=null&&old.optLong("at")>at)return false;all.put(p.id,new JSONObject().put("region",scope.trim()).put("level",level).put("at",at).put("lat",p.lat).put("lon",p.lon));return true;}
    long lastCompleteRefresh(String scope){return completedAt(scope);}
    void markCompleteRefresh(String scope,long at){markRefreshCompleted(scope,at);}
    boolean refreshDue(String scope){return shouldRefresh(scope,System.currentTimeMillis());}
    boolean placesFresh(String scope,String category){return fresh(cachedAt(scope,category),System.currentTimeMillis());}
    long completedAt(String scope){synchronized(LOCK){JSONObject all=read().optJSONObject("completed");return all==null?0:all.optLong(scope.trim());}}
    boolean shouldRefresh(String scope,long now){return !fresh(completedAt(scope),now);}
    void markRefreshCompleted(String scope,long at){if(!fresh(at,System.currentTimeMillis()))return;synchronized(LOCK){try{JSONObject o=read(),all=o.optJSONObject("completed");if(all==null){all=new JSONObject();o.put("completed",all);}long old=all.optLong(scope.trim());if(old<=at){all.put(scope.trim(),at);while(all.length()>36){String remove=null;long time=Long.MAX_VALUE;Iterator<String> keys=all.keys();while(keys.hasNext()){String k=keys.next();if(all.optLong(k)<time){time=all.optLong(k);remove=k;}}if(remove==null)break;all.remove(remove);}write(o);}}catch(Exception ignored){}}}
    String latestCity(){synchronized(LOCK){return read().optString("latestCity");}}
    void clearHeatPlaces(Collection<NearbyPlace> places){synchronized(LOCK){JSONObject o=read(),all=o.optJSONObject("heat");if(all!=null){for(NearbyPlace p:places)all.remove(p.id);write(o);}}}
    long cachedAt(String region,String category){synchronized(LOCK){JSONObject all=read().optJSONObject("pois"),entry=all==null?null:all.optJSONObject(key(region,category));return entry==null?0:entry.optLong("at");}}
    java.util.List<String> regions(){synchronized(LOCK){ArrayList<String> out=new ArrayList<>();JSONObject all=read().optJSONObject("regions");if(all!=null){Iterator<String> keys=all.keys();while(keys.hasNext())out.add(keys.next());}return out;}}
    void clearHeat(String region){clearRegion("heat",region);}
    void clearPlaces(String region){clearRegion("pois",region);}
    private void clearRegion(String field,String region){synchronized(LOCK){JSONObject o=read(),all=o.optJSONObject(field);if(all!=null){ArrayList<String> remove=new ArrayList<>();Iterator<String> keys=all.keys();while(keys.hasNext()){String k=keys.next();JSONObject entry=all.optJSONObject(k);if(entry!=null&&region.trim().equals(entry.optString("region")))remove.add(k);}for(String k:remove)all.remove(k);write(o);}}}
    void clearHeat(){synchronized(LOCK){try{JSONObject o=read();o.put("heat",new JSONObject());write(o);}catch(Exception ignored){}}}
    void prune(){synchronized(LOCK){JSONObject o=read();pruneLocked(o);write(o);}}
    private static void pruneLocked(JSONObject o){long now=System.currentTimeMillis();for(String field:new String[]{"pois","heat"}){JSONObject all=o.optJSONObject(field);if(all==null)continue;ArrayList<String> remove=new ArrayList<>();Iterator<String> keys=all.keys();while(keys.hasNext()){String k=keys.next();JSONObject entry=all.optJSONObject(k);if(entry==null||!retained(entry.optLong("at"),now))remove.add(k);}for(String k:remove)all.remove(k);}}
    private static void trim(JSONObject all,int max){while(all.length()>max){String oldest=null;long at=Long.MAX_VALUE;Iterator<String> keys=all.keys();while(keys.hasNext()){String k=keys.next();long value=all.optJSONObject(k).optLong("at");if(value<at){oldest=k;at=value;}}if(oldest==null)break;all.remove(oldest);}}
}
