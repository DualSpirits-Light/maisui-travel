package cn.lvxu.travel;
import java.time.*;
import java.util.*;
/** Detached deterministic changes: locked appointments stay put; no inferred visit state. */
final class TravelAdjustment {
 static Trip copy(Trip trip){try{return Trip.from(trip.json());}catch(Exception e){throw new IllegalArgumentException("无法准备行程副本",e);}}
 static Trip shift(Trip source,int day,int minutes){
  if(day<0||day>=source.days||minutes==0||Math.abs((long)minutes)>720)throw new IllegalArgumentException("请选择当天及 1–720 分钟的调整");
  Trip target=copy(source);boolean moved=false;
  for(Trip.Stop stop:target.stops)if(stop.day==day&&!stop.timeLocked){
   int absolute=day*1440+ItineraryFormat.startMinute(stop)+minutes;
   if(absolute<0||absolute>=target.days*1440)throw new IllegalArgumentException("调整会超出旅行日期，请先增加天数或单独编辑地点");
   int newDay=absolute/1440;if(stop.day!=newDay)stop.sortOrder=-1;stop.day=newDay;stop.time=String.format(Locale.ROOT,"%02d:%02d",absolute%1440/60,absolute%60);moved=true;
  }
  if(!moved)throw new IllegalArgumentException("当天没有可调整的地点；已锁定的预约时间不会移动");
  return target;
 }
 static ArrayList<String> conflicts(Trip trip){ArrayList<String> out=new ArrayList<>();for(int i=0;i<trip.stops.size();i++)for(int j=i+1;j<trip.stops.size();j++){Trip.Stop a=trip.stops.get(i),b=trip.stops.get(j);int startA=a.day*1440+ItineraryFormat.startMinute(a),startB=b.day*1440+ItineraryFormat.startMinute(b);if(startA<startB+b.duration&&startB<startA+a.duration){out.add("第 "+(a.day+1)+" 天 "+a.name+" 与第 "+(b.day+1)+" 天 "+b.name+" 时间重叠");if(out.size()>=10)return out;}}return out;}
}