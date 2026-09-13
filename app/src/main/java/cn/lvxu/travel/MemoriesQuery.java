package cn.lvxu.travel;

import java.time.*;import java.util.*;

/** Queries source records directly; no memory copy is persisted. */
final class MemoriesQuery {
 static final class Memory {final int year;final String kind,tripId,itemId,title,detail,photo;final LocalDate date;Memory(int year,String kind,String tripId,String itemId,String title,String detail,String photo,LocalDate date){this.year=year;this.kind=kind;this.tripId=tripId;this.itemId=itemId;this.title=title;this.detail=detail;this.photo=photo;this.date=date;}}
 static List<Memory> forDate(Collection<Trip> trips,LocalDate today){ArrayList<Memory> exact=collect(trips,today,0),near=collect(trips,today,3);ArrayList<Memory> result=exact.isEmpty()?near:exact;result.sort((a,b)->Integer.compare(b.year,a.year));return result;}
 private static ArrayList<Memory> collect(Collection<Trip> trips,LocalDate today,int radius){ArrayList<Memory> out=new ArrayList<>();for(Trip t:trips){LocalDate travel;try{travel=LocalDate.parse(t.start);}catch(Exception e){continue;}if(matches(travel,today,radius))out.add(new Memory(travel.getYear(),"旅行",t.id,t.id,t.title,t.city,"",travel));for(Trip.Stop s:t.stops){LocalDate d=travel.plusDays(s.day);if(matches(d,today,radius))out.add(new Memory(d.getYear(),"地点",t.id,s.id,s.name,t.title+" · "+s.note,(s.previewPhoto.isEmpty()&&!s.notePhotos.isEmpty()?s.notePhotos.get(0):s.previewPhoto),d));}for(Trip.Checkin c:t.checkins){try{LocalDate d=LocalDateTime.parse(c.time).toLocalDate();if(matches(d,today,radius))out.add(new Memory(d.getYear(),"打卡",t.id,c.id,c.place.isEmpty()?"旅途中的此刻":c.place,c.mood,photo(c),d));}catch(Exception ignored){}}}return out;}
 private static boolean matches(LocalDate d,LocalDate today,int radius){if(d.getYear()>=today.getYear())return false;for(int offset=-radius;offset<=radius;offset++)if(MonthDay.from(d).equals(MonthDay.from(today.plusDays(offset))))return true;return false;}private static String photo(Trip.Checkin c){if(!c.sceneryPhotos.isEmpty())return c.sceneryPhotos.get(0);if(!c.groupPhotos.isEmpty())return c.groupPhotos.get(0);return c.photo;}
}
