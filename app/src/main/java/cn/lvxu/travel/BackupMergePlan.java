package cn.lvxu.travel;

import java.util.*;
import java.io.IOException;
import org.json.*;

/** Detached restore decisions; never modifies the current database snapshot. */
final class BackupMergePlan {
 final ArrayList<Trip> merged,added=new ArrayList<>();
 final Set<String> copies=new HashSet<>();
 interface MediaKey {String get(String path)throws Exception;}
 BackupMergePlan(List<Trip> existing,List<Trip> incoming)throws Exception {
  this(existing,incoming,p->p,p->p);
 }
 BackupMergePlan(List<Trip> existing,List<Trip> incoming,MediaKey currentMedia,MediaKey incomingMedia)throws Exception {
  merged=new ArrayList<>(existing);Map<String,Trip> ids=new HashMap<>();Set<String> names=new HashSet<>();for(Trip t:existing){ids.put(t.id,t);names.add(t.title);}
  for(Trip source:incoming){Trip t=Trip.from(source.json());t.updatedAt=source.updatedAt;
   t.pinned=source.pinned;t.favorite=source.favorite;t.archived=source.archived;
   Trip local=ids.get(t.id);if(local!=null){if(same(local,source,currentMedia,incomingMedia))continue;
    String content=logicalContent(source,incomingMedia),origin="restore-"+UUID.nameUUIDFromBytes((source.id+"\n"+source.title+"\n"+content).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    boolean restored=false;for(Trip previous:merged){String provenance=copyId(origin,previous.title);if((previous.id.equals(provenance)||previous.id.startsWith(provenance+"-"))&&restoreTitle(previous.title,source.title)&&content.equals(logicalContent(previous,currentMedia))){restored=true;break;}}if(restored)continue;
    regenerate(t);
    int count=1;String title;do{String suffix=count==1?"（恢复副本）":"（恢复副本 "+count+"）";title=source.title.substring(0,Math.min(source.title.length(),80-suffix.length()))+suffix;count++;}while(names.contains(title));t.title=title;
    String copyId=copyId(origin,title);for(int serial=2;ids.containsKey(copyId);serial++)copyId=copyId(origin,title)+"-"+serial;t.id=copyId;copies.add(t.id);
   }ids.put(t.id,t);names.add(t.title);added.add(t);merged.add(t);
  }
  if(merged.size()>100)throw new IOException("合并后旅行超过 100 个");
 }
 private static String copyId(String origin,String title){return origin+"-"+UUID.nameUUIDFromBytes(title.getBytes(java.nio.charset.StandardCharsets.UTF_8));}
 private static boolean restoreTitle(String title,String source){for(int count=1;count<=101;count++){String suffix=count==1?"（恢复副本）":"（恢复副本 "+count+"）";if(title.equals(source.substring(0,Math.min(source.length(),80-suffix.length()))+suffix))return true;}return false;}
 /** Normalize only regenerated entity IDs, preserving their associations and all user content. */
 private static String logicalContent(Trip trip,MediaKey media)throws Exception {
  JSONObject raw=trip.json();raw.remove("id");raw.remove("title");Map<String,String> categories=normalizeIds(raw.getJSONArray("categories"),"category"),lists=normalizeIds(raw.getJSONArray("lists"),"list"),stops=normalizeIds(raw.getJSONArray("stops"),"stop");
  JSONArray expenses=raw.getJSONArray("expenses"),items=raw.getJSONArray("items"),checkins=raw.getJSONArray("checkins");normalizeIds(expenses,"expense");normalizeIds(items,"item");normalizeIds(checkins,"checkin");
  for(int i=0;i<expenses.length();i++)remap(expenses.getJSONObject(i),"categoryId",categories);
  for(int i=0;i<items.length();i++)remap(items.getJSONObject(i),"listId",lists);
  for(int i=0;i<checkins.length();i++)remap(checkins.getJSONObject(i),"stopId",stops);
  Map<String,String> memberIds=normalizeIds(raw.getJSONArray("members"),"member");JSONArray transfers=raw.getJSONArray("settlements");normalizeIds(transfers,"settlement");for(int i=0;i<expenses.length();i++){JSONObject e=expenses.getJSONObject(i);remap(e,"payerId",memberIds);JSONArray shares=e.getJSONArray("shares");for(int j=0;j<shares.length();j++)remap(shares.getJSONObject(j),"memberId",memberIds);}for(int i=0;i<transfers.length();i++){remap(transfers.getJSONObject(i),"fromId",memberIds);remap(transfers.getJSONObject(i),"toId",memberIds);}return canonical(raw,media);
 }
 private static Map<String,String> normalizeIds(JSONArray entries,String prefix)throws Exception {Map<String,String> ids=new HashMap<>();for(int i=0;i<entries.length();i++){JSONObject entry=entries.getJSONObject(i);String replacement=prefix+"-"+i;ids.put(entry.getString("id"),replacement);entry.put("id",replacement);}return ids;}
 private static void remap(JSONObject entry,String field,Map<String,String> ids)throws Exception {String old=entry.optString(field,"");if(ids.containsKey(old))entry.put(field,ids.get(old));}
 private static boolean same(Trip a,Trip b,MediaKey left,MediaKey right)throws Exception {
  return a.pinned==b.pinned&&a.favorite==b.favorite&&a.archived==b.archived&&canonical(a.json(),left).equals(canonical(b.json(),right));
 }
 private static String canonical(Object value,MediaKey media)throws Exception {
  return canonical(value,media,false);
 }
 private static String canonical(Object value,MediaKey media,boolean mediaValue)throws Exception {
  if(value instanceof JSONObject){JSONObject o=(JSONObject)value;TreeSet<String> keys=new TreeSet<>();for(Iterator<String> it=o.keys();it.hasNext();)keys.add(it.next());StringBuilder out=new StringBuilder("{");for(String k:keys)if(!k.equals("updatedAt"))out.append(JSONObject.quote(k)).append(':').append(canonical(o.get(k),media,Arrays.asList("photo","card","previewPhoto","notePhotos","groupPhotos","sceneryPhotos").contains(k))).append(',');return out.append('}').toString();}
  if(value instanceof JSONArray){JSONArray a=(JSONArray)value;StringBuilder out=new StringBuilder("[");for(int i=0;i<a.length();i++)out.append(canonical(a.get(i),media,mediaValue)).append(',');return out.append(']').toString();}
  if(value instanceof Number)return new java.math.BigDecimal(value.toString()).stripTrailingZeros().toPlainString();
  if(value instanceof String){String text=(String)value;return JSONObject.quote(mediaValue&&text.startsWith("media/")?media.get(text):text);}
  return String.valueOf(value);
 }
 private static void regenerate(Trip trip){AaLedger.regenerate(trip);
  trip.id=Trip.uid();Map<String,String> categories=new HashMap<>(),lists=new HashMap<>(),stops=new HashMap<>();Set<String> ambiguous=new HashSet<>();
  for(Trip.Category c:trip.categories){String old=c.id;c.id=Trip.uid();categories.put(old,c.id);}
  for(Trip.Checklist l:trip.lists){String old=l.id;l.id=Trip.uid();lists.put(old,l.id);}
  for(Trip.Stop s:trip.stops){String old=s.id;s.id=Trip.uid();if(stops.containsKey(old))ambiguous.add(old);stops.put(old,s.id);}
  for(Trip.Expense e:trip.expenses){e.id=Trip.uid();e.categoryId=categories.getOrDefault(e.categoryId,e.categoryId);}
  for(Trip.Item i:trip.items){i.id=Trip.uid();i.listId=lists.getOrDefault(i.listId,i.listId);}
  for(Trip.Checkin c:trip.checkins){c.id=Trip.uid();c.stopId=ambiguous.contains(c.stopId)?"":stops.getOrDefault(c.stopId,c.stopId);}
 }
}
