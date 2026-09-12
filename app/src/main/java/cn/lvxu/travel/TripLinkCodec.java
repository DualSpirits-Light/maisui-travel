package cn.lvxu.travel;
import org.json.*;
import java.util.*;

/** AMap provenance and field-level patches; never interprets arbitrary remote code or URLs. */
final class TripLinkCodec {
 static final String[] FIELDS={"name","address","openingHours","rating","lat","lon","coordinateSystem"};
 interface Resolver { JSONObject resolve(String url)throws Exception; }
 static JSONObject fields(JSONObject source)throws JSONException {JSONObject out=new JSONObject();for(String key:FIELDS)out.put(key,source.has(key)?source.get(key):JSONObject.NULL);return out;}
 static String snapshot(Trip.Stop s){try{return fields(s.json()).toString();}catch(Exception e){throw new IllegalArgumentException("无法保存来源数据",e);}}
 static boolean equalValue(Object a,Object b){if(a instanceof Number&&b instanceof Number)try{return new java.math.BigDecimal(a.toString()).compareTo(new java.math.BigDecimal(b.toString()))==0;}catch(NumberFormatException ignored){}return Objects.equals(a,b)||(a==null&&b==JSONObject.NULL)||(b==null&&a==JSONObject.NULL);}
 static JSONObject changes(JSONObject base,JSONObject current)throws JSONException {JSONObject out=new JSONObject();for(String k:FIELDS){Object a=base.opt(k),b=current.opt(k);if(!equalValue(a,b))out.put(k,b==null?JSONObject.NULL:b);}if(out.has("lat")||out.has("lon")||out.has("coordinateSystem"))for(String k:new String[]{"lat","lon","coordinateSystem"})out.put(k,current.has(k)?current.get(k):JSONObject.NULL);return out;}
 static JSONObject base(Trip.Stop s)throws JSONException {return s.sourceSnapshot.isEmpty()?fields(s.json()):fields(new JSONObject(s.sourceSnapshot));}
 static JSONObject pack(Trip t)throws JSONException {
  JSONObject raw=t.json();JSONArray stops=raw.getJSONArray("stops");
  for(int i=0;i<stops.length();i++){JSONObject s=stops.getJSONObject(i);Trip.Stop original=t.stops.get(i);if(original.sourceUrl.isEmpty())continue;JSONObject base=base(original);JSONObject patch=original.sourceSnapshot.isEmpty()?fields(s):changes(base,s);for(String k:FIELDS)s.remove(k);s.remove("sourceSnapshot");s.put("amap",new JSONObject().put("base",base).put("changes",patch).put("legacy",original.sourceSnapshot.isEmpty()));}
  if(!t.accommodationSourceUrl.isEmpty()){JSONObject current=new JSONObject().put("name",t.accommodation).put("address",t.accommodationAddress),base=t.accommodationSnapshot.isEmpty()?current:new JSONObject(t.accommodationSnapshot),patch=new JSONObject();for(String k:new String[]{"name","address"})if(t.accommodationSnapshot.isEmpty()||!current.optString(k).equals(base.optString(k)))patch.put(k,current.get(k));raw.remove("accommodation");raw.remove("accommodationAddress");raw.remove("accommodationSnapshot");raw.put("amapAccommodation",new JSONObject().put("base",base).put("changes",patch).put("legacy",t.accommodationSnapshot.isEmpty()));}
  return raw;
 }
 static JSONObject expand(JSONObject raw)throws JSONException {
  JSONArray stops=raw.optJSONArray("stops");if(stops==null)return raw;
  for(int i=0;i<stops.length();i++){JSONObject s=stops.getJSONObject(i),link=s.optJSONObject("amap");if(link==null)continue;JSONObject base=fields(link.getJSONObject("base")),patch=link.getJSONObject("changes");for(String k:FIELDS)s.put(k,patch.has(k)?patch.get(k):base.opt(k));s.put("sourceSnapshot",link.optBoolean("legacy")?"":base.toString());s.remove("amap");}
  JSONObject stay=raw.optJSONObject("amapAccommodation");if(stay!=null){JSONObject b=stay.getJSONObject("base"),p=stay.getJSONObject("changes");raw.put("accommodation",p.has("name")?p.get("name"):b.optString("name")).put("accommodationAddress",p.has("address")?p.get("address"):b.optString("address")).put("accommodationSnapshot",stay.optBoolean("legacy")?"":b.toString());raw.remove("amapAccommodation");}
  return raw;
 }
 static int hydrate(Trip t,Resolver resolver)throws Exception {
  int failed=0;long deadline=System.nanoTime()+45_000_000_000L;Map<String,JSONObject> cache=new HashMap<>();Set<String> unavailable=new HashSet<>();
  for(int i=0;i<t.stops.size();i++){Trip.Stop s=t.stops.get(i);if(s.sourceUrl.isEmpty())continue;try{
   JSONObject original=s.json(),base=base(s),patch=s.sourceSnapshot.isEmpty()?fields(original):changes(base,original);
   JSONObject fresh=cache.get(s.sourceUrl);if(fresh==null){if(unavailable.contains(s.sourceUrl)||System.nanoTime()>deadline)throw new Exception();fresh=resolver.resolve(s.sourceUrl);cache.put(s.sourceUrl,fresh);}
   // Missing fields in the latest page never erase previously imported information.
   JSONObject merged=new JSONObject(base.toString());for(String key:FIELDS)if(fresh.has(key)&&!fresh.isNull(key)&&!fresh.optString(key).isEmpty())merged.put(key,fresh.get(key));
   for(String key:FIELDS)original.put(key,patch.has(key)?patch.get(key):merged.opt(key));original.put("sourceSnapshot",merged.toString());t.stops.set(i,Trip.Stop.from(original));
  }catch(Exception e){unavailable.add(s.sourceUrl);failed++;}}
  if(!t.accommodationSourceUrl.isEmpty())try{boolean legacy=t.accommodationSnapshot.isEmpty();JSONObject b=legacy?new JSONObject().put("name",t.accommodation).put("address",t.accommodationAddress):new JSONObject(t.accommodationSnapshot);if(System.nanoTime()>deadline)throw new Exception();JSONObject f=resolver.resolve(t.accommodationSourceUrl);if(!legacy&&t.accommodation.equals(b.optString("name"))&&!f.optString("name").isEmpty())t.accommodation=f.getString("name");if(!legacy&&t.accommodationAddress.equals(b.optString("address"))&&!f.optString("address").isEmpty())t.accommodationAddress=f.getString("address");for(String k:new String[]{"name","address"})if(!f.optString(k).isEmpty())b.put(k,f.get(k));t.accommodationSnapshot=b.toString();}catch(Exception e){failed++;}
  return failed;
 }
 static Trip copyForShare(Trip original,boolean memories,boolean photos)throws Exception {
  Trip t=Trip.from(original.json());t.pinned=false;t.archived=false;t.favorite=false;
  if(!memories){t.companions="";t.vehicleNumber="";t.expenses.clear();t.checkins.clear();for(Trip.Item i:t.items)i.done=false;}
  if(!photos){for(Trip.Stop s:t.stops){s.previewPhoto="";s.notePhotos.clear();}for(Trip.Item i:t.items)i.photo="";for(Trip.Checkin c:t.checkins){c.photo="";c.card="";c.groupPhotos.clear();c.sceneryPhotos.clear();}}
  return t;
 }
}
