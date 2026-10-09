package cn.lvxu.travel;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
/** Pure form rules shared by creation and consecutive itinerary entry. */
final class TravelFormRules {
 static boolean completed(Trip t,LocalDate today){try{return LocalDate.parse(t.start).plusDays(Math.max(1,t.days)-1).isBefore(today);}catch(Exception ignored){return false;}}
 static String companions(String raw){LinkedHashSet<String> people=new LinkedHashSet<>();for(String value:raw.split("[,，;；、]")){String clean=value.trim();if(!clean.isEmpty()){if(clean.length()>40)throw new IllegalArgumentException("每位同行人姓名最多 40 字");people.add(clean);}}if(people.size()>50)throw new IllegalArgumentException("同行人最多 50 位");return Trip.bounded(String.join("、",people),500);}
 static Trip.Stop next(Trip t,int day){Trip.Stop result=new Trip.Stop();result.day=Math.max(0,Math.min(day,t.days-1));java.util.List<Trip.Stop> stops=t.onDay(result.day);if(stops.isEmpty())return result;Trip.Stop previous=stops.get(0);int latest=-1;for(Trip.Stop stop:stops){int end=LocalTime.parse(stop.time).toSecondOfDay()/60+stop.duration;if(end>latest){latest=end;previous=stop;}}try{return after(t,previous);}catch(IllegalArgumentException ignored){return result;}}
 static Trip.Stop after(Trip t,Trip.Stop previous){Trip.Stop result=new Trip.Stop();result.day=previous.day;try{LocalDateTime end=LocalDate.of(2000,1,1).plusDays(previous.day).atTime(LocalTime.parse(previous.time)).plusMinutes(previous.duration);int nextDay=(int)java.time.temporal.ChronoUnit.DAYS.between(LocalDate.of(2000,1,1),end.toLocalDate());if(nextDay>=t.days)throw new IllegalArgumentException("上一地点已结束于旅行日期之外，请调整旅行天数或手动安排");result.day=nextDay;result.time=end.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"));}catch(java.time.format.DateTimeParseException ignored){}return result;}
}
