package cn.lvxu.travel;
import java.time.*;import java.lang.reflect.*;
public final class TravelDaySummaryTest {
 static int n;static Class<?> type;static Method summary;
 public static void main(String[] args)throws Exception{
  try{type=Class.forName("cn.lvxu.travel.TravelDaySummary");}catch(ClassNotFoundException e){throw new AssertionError("Missing today itinerary summary",e);}
  summary=type.getDeclaredMethod("forTrip",Trip.class,LocalDateTime.class);summary.setAccessible(true);
  Trip t=new Trip();t.start="2026-10-07";t.days=2;
  Trip.Stop late=stop(t,"夜游",0,"23:30",120),next=stop(t,"预约博物馆",1,"10:00",90);next.timeLocked=true;
  Object r=get(t,"2026-10-08T00:30");eq("ACTIVE",field(r,"state").toString());eq(late,field(r,"current"));eq(next,field(r,"next"));eq(next,field(r,"reservation"));eq(1,field(r,"todayDay"));
  r=get(t,"2026-10-08T01:30");eq(null,field(r,"current"));eq(next,field(r,"next"));eq("GAP",field(r,"state").toString());
  Trip.Stop overlap=stop(t,"重叠",1,"10:30",60);r=get(t,"2026-10-08T10:45");eq(2,field(r,"overlapCount"));
  r=get(t,"2026-10-08T12:00");eq("DAY_OVER",field(r,"state").toString());eq(null,field(r,"current"));eq(null,field(r,"reservation"));
  r=get(t,"2026-10-09T00:00");eq("PAST",field(r,"state").toString());
  r=get(t,"2026-10-06T12:00");eq("UPCOMING",field(r,"state").toString());eq(-1,field(r,"todayDay"));eq(late,field(r,"next"));
  Trip overnight=new Trip();overnight.start=t.start;overnight.days=1;Trip.Stop last=stop(overnight,"跨夜末站",0,"23:30",120);
  r=get(overnight,"2026-10-08T00:30");eq("ACTIVE",field(r,"state").toString());eq(last,field(r,"current"));r=get(overnight,"2026-10-08T01:30");eq("PAST",field(r,"state").toString());
  Trip empty=new Trip();empty.start=t.start;empty.days=3;r=get(empty,"2026-10-08T12:00");eq("EMPTY",field(r,"state").toString());
  Trip.Stop future=stop(empty,"第三天",2,"09:00",60);r=get(empty,"2026-10-08T12:00");eq("EMPTY",field(r,"state").toString());eq(future,field(r,"next"));
  Trip order=new Trip();order.start=t.start;order.days=1;Trip.Stop b=stop(order,"晚",0,"14:00",60),a=stop(order,"早",0,"09:00",60);a.sortOrder=10;b.sortOrder=0;r=get(order,"2026-10-07T08:00");eq(a,field(r,"next"));eq(b,order.stops.get(0));
  System.out.println("TravelDaySummaryTest: "+n+" checks passed");
 }
 static Trip.Stop stop(Trip t,String name,int day,String time,int duration){Trip.Stop s=new Trip.Stop();s.name=name;s.day=day;s.time=time;s.duration=duration;t.stops.add(s);return s;}
 static Object get(Trip t,String now)throws Exception{return summary.invoke(null,t,LocalDateTime.parse(now));}
 static Object field(Object o,String name)throws Exception{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
 static void eq(Object expected,Object actual){if(!java.util.Objects.equals(expected,actual))throw new AssertionError("expected "+expected+" got "+actual);n++;}
}
