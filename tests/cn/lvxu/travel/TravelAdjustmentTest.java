package cn.lvxu.travel;
import java.lang.reflect.*;
import java.util.*;
public final class TravelAdjustmentTest {
 static int checks;
 public static void main(String[] args)throws Exception{
  Class<?> type;try{type=Class.forName("cn.lvxu.travel.TravelAdjustment");}catch(ClassNotFoundException e){throw new AssertionError("Missing safe itinerary adjustment",e);}
  Trip t=new Trip();t.title="兰州三日";t.city="兰州";t.days=3;t.normalize();
  Trip.Stop a=stop("博物馆","09:00",0),b=stop("午餐","12:00",0),c=stop("黄河桥","23:40",0);a.timeLocked=true;t.stops.addAll(Arrays.asList(a,b,c));
  Method shift=type.getDeclaredMethod("shift",Trip.class,int.class,int.class);shift.setAccessible(true);
  Trip out=(Trip)shift.invoke(null,t,0,30);eq("09:00",out.stops.get(0).time);eq("12:30",out.stops.get(1).time);eq("00:10",out.stops.get(2).time);eq(1,out.stops.get(2).day);eq("12:00",t.stops.get(1).time);eq(b.id,out.stops.get(1).id);
  Trip overnight=new Trip();overnight.days=2;Trip.Stop late=stop("跨夜预约","23:45",0),next=stop("次日安排","00:10",1);late.duration=60;overnight.stops.addAll(Arrays.asList(late,next));Method conflicts=type.getDeclaredMethod("conflicts",Trip.class);conflicts.setAccessible(true);eq(false,((java.util.List<?>)conflicts.invoke(null,overnight)).isEmpty());
  t.days=1;boolean rejected=false;try{shift.invoke(null,t,0,30);}catch(InvocationTargetException expected){rejected=expected.getCause() instanceof IllegalArgumentException;}eq(true,rejected);
  Class<?> undo=Class.forName("cn.lvxu.travel.TravelUndo");Object record=undo.getDeclaredConstructor(String.class,Trip.class,Trip.class).newInstance("顺延",t,t);
  Method restore=undo.getDeclaredMethod("restore",Trip.class);restore.setAccessible(true);Trip restored=(Trip)restore.invoke(record,t);eq(t.id,restored.id);
  t.title="后续修改";rejected=false;try{restore.invoke(record,t);}catch(InvocationTargetException expected){rejected=expected.getCause() instanceof IllegalArgumentException;}eq(true,rejected);
  System.out.println("TravelAdjustmentTest: "+checks+" checks passed");
 }
 static Trip.Stop stop(String name,String time,int day){Trip.Stop s=new Trip.Stop();s.name=name;s.time=time;s.day=day;return s;}
 static void eq(Object expected,Object actual){checks++;if(!Objects.equals(expected,actual))throw new AssertionError(expected+" != "+actual);}
}
