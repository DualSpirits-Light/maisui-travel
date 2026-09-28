package cn.lvxu.travel;

import org.json.*;
import java.util.*;

/** Strict, deliberately small boundary between model text and local trip data. */
final class AiPlan {
 static final class Day { final int day; String title="",summary=""; final ArrayList<Item> items=new ArrayList<>(); Day(int day){this.day=day;} }
 static final class Item { String name,time,note,address=""; int duration; long cost; Double lat,lon; boolean verifyNeeded,selected=true; }
 final ArrayList<Day> days=new ArrayList<>(); long budgetCents; String currency="CNY",title="",summary="",theme=""; final ArrayList<String> tips=new ArrayList<>();
 static String extractJson(String text){
  if(text==null)throw new IllegalArgumentException("AI 未返回内容");
  String s=text.trim(); int fence=s.indexOf("```");
  if(fence>=0){int line=s.indexOf('\n',fence);int end=line<0?-1:s.indexOf("```",line+1);if(line>=0&&end>line)s=s.substring(line+1,end).trim();}
  int start=s.indexOf('{'),end=s.lastIndexOf('}');if(start<0||end<=start)throw new IllegalArgumentException("AI 未返回行程 JSON");return s.substring(start,end+1);
 }
 static AiPlan parse(String text,int maxDays){
  if(maxDays<1||maxDays>60)throw new IllegalArgumentException("旅行天数无效");
  try{
   JSONObject root=new JSONObject(extractJson(text)); JSONArray source=root.optJSONArray("days");
   if(source==null||source.length()<1||source.length()>maxDays)throw new IllegalArgumentException("行程天数无效");
   AiPlan out=new AiPlan();out.title=clip(root.optString("title",root.optString("tripTitle","")),80);out.summary=clip(root.optString("summary",""),500);out.theme=clip(root.optString("theme",""),120); boolean[] seen=new boolean[maxDays]; int itemCount=0;
   for(int i=0;i<source.length();i++){
    JSONObject d=source.getJSONObject(i);int number=number(d,"day","dayNumber");if(number<1||number>maxDays||seen[number-1])throw new IllegalArgumentException("行程日期无效");seen[number-1]=true;
    Day day=new Day(number);day.title=clip(d.optString("theme",d.optString("title","")),100);day.summary=clip(d.optString("summary",d.optString("notes","")),500);JSONArray values=d.optJSONArray("items");if(values==null)values=d.optJSONArray("places");if(values==null||values.length()>12)throw new IllegalArgumentException("每日地点数量无效");
    int previous=-1;
    for(int j=0;j<values.length();j++){JSONObject v=values.getJSONObject(j);Item item=new Item();item.name=bounded(v.optString("name",v.optString("title","")),120,"地点名称");item.time=time(v.optString("time",v.optString("startTime","")));item.duration=number(v,"duration","durationMinutes");if(item.duration==0)item.duration=60;if(item.duration<15||item.duration>720)throw new IllegalArgumentException("地点时长无效");int minute=Integer.parseInt(item.time.substring(0,2))*60+Integer.parseInt(item.time.substring(3));if(minute<previous)throw new IllegalArgumentException("地点时间顺序无效");previous=minute;item.note=clip(v.optString("note",v.optString("notes","")),800);item.cost=yuan(value(v,"cost","estimatedCost"));item.address=clip(v.optString("address",""),300);item.verifyNeeded=v.optBoolean("verifyNeeded",v.optBoolean("needsVerification",true));if(value(v,"lat","latitude")!=null&&value(v,"lat","latitude")!=JSONObject.NULL||value(v,"lon","longitude")!=null&&value(v,"lon","longitude")!=JSONObject.NULL){item.lat=coordinate(value(v,"lat","latitude"));item.lon=coordinate(value(v,"lon","longitude"));if(item.lat==null||item.lon==null||Math.abs(item.lat)>90||Math.abs(item.lon)>180)throw new IllegalArgumentException("地点坐标无效");}day.items.add(item);if(++itemCount>120)throw new IllegalArgumentException("地点数量过多");}
    out.days.add(day);
   }
   JSONObject budget=root.optJSONObject("budget");if(budget!=null){out.budgetCents=yuan(budget.opt("total"));out.currency=clip(budget.optString("currency","CNY"),8);}
   JSONArray tips=root.optJSONArray("tips");if(tips!=null){if(tips.length()>20)throw new IllegalArgumentException("提示过多");for(int i=0;i<tips.length();i++)out.tips.add(bounded(tips.optString(i),300,"提示"));}
   if(itemCount==0)throw new IllegalArgumentException("AI 未提供可用地点");if(!"CNY".equalsIgnoreCase(out.currency)&&!"RMB".equalsIgnoreCase(out.currency))throw new IllegalArgumentException("当前只支持人民币预算");out.days.sort(Comparator.comparingInt(x->x.day));return out;
  }catch(JSONException e){throw new IllegalArgumentException("AI 返回的行程格式无效");}
 }
 ArrayList<Trip.Stop> toStops(){ArrayList<Trip.Stop> out=new ArrayList<>();for(Day d:days)for(int i=0;i<d.items.size();i++){Item item=d.items.get(i);if(!item.selected)continue;Trip.Stop stop=new Trip.Stop();stop.name=item.name;stop.time=item.time;stop.duration=item.duration;stop.note=(item.verifyNeeded?"请核实：":"")+item.note;stop.cost=item.cost;stop.address=item.address;stop.lat=item.lat;stop.lon=item.lon;stop.day=d.day-1;stop.sortOrder=i;out.add(stop);}return out;}
 /** Validate every field before committing an edit to this unsaved AI draft. */
 static void edit(Item item,String name,String clock,String duration,String cost,String address,String note){
  String n=bounded(name,120,"地点名称"),t=time(clock.trim()),ad=clip(address,300),nt=clip(note,800);int minutes;try{minutes=Integer.parseInt(duration.trim());}catch(Exception e){throw new IllegalArgumentException("地点时长无效");}if(minutes<15||minutes>720)throw new IllegalArgumentException("地点时长须为15–720分钟");long cents=yuan(cost.trim());
  if(!n.equals(item.name)||!ad.equals(item.address)){item.lat=null;item.lon=null;item.verifyNeeded=true;}
  item.name=n;item.time=t;item.duration=minutes;item.cost=cents;item.address=ad;item.note=nt;
 }
 Trip toTrip(String city,String start,int requestedDays,long requestedBudget){Trip trip=new Trip();trip.city=bounded(city,80,"目的地");trip.start=bounded(start,20,"出发日期");trip.days=requestedDays;trip.title=title.isEmpty()?trip.city+(theme.isEmpty()?" 行程":" · "+theme):title;trip.budget=requestedBudget>0?requestedBudget:budgetCents;trip.stops.addAll(toStops());trip.normalize();return trip;}
 private static String bounded(String v,int n,String label){v=v==null?"":v.trim();if(v.isEmpty()||v.length()>n)throw new IllegalArgumentException(label+"无效");return v;}
 private static String clip(String v,int n){v=v==null?"":v.trim();if(v.length()>n)throw new IllegalArgumentException("文字过长");return v;}
 private static String time(String v){if(v==null||!v.matches("[0-2][0-9]:[0-5][0-9]"))throw new IllegalArgumentException("地点时间无效");int h=Integer.parseInt(v.substring(0,2));if(h>23)throw new IllegalArgumentException("地点时间无效");return v;}
 private static long yuan(Object value){if(value==null||value==JSONObject.NULL)return 0;try{return Trip.cents(String.valueOf(value));}catch(Exception e){throw new IllegalArgumentException("预算金额无效");}}
 private static int number(JSONObject o,String primary,String alternate){return o.has(primary)?o.optInt(primary,0):o.optInt(alternate,0);}
 private static Object value(JSONObject o,String primary,String alternate){return o.has(primary)?o.opt(primary):o.opt(alternate);}
 private static Double coordinate(Object value){if(value==null||value==JSONObject.NULL)return null;try{double n=Double.parseDouble(String.valueOf(value));return Double.isFinite(n)?n:null;}catch(Exception e){return null;}}
}
