package cn.lvxu.travel;
import java.time.*;import java.util.*;
/** Device-local plan times only; never infers arrival or completion. */
final class TravelDaySummary {
 enum State {UPCOMING,ACTIVE,GAP,EMPTY,DAY_OVER,PAST,INVALID}
 final State state;final Trip.Stop current,next,reservation;final int todayDay,overlapCount;final LocalDateTime firstDay,lastEnd;
 private TravelDaySummary(State s,Trip.Stop c,Trip.Stop n,Trip.Stop r,int d,int count,LocalDateTime first,LocalDateTime end){state=s;current=c;next=n;reservation=r;todayDay=d;overlapCount=count;firstDay=first;lastEnd=end;}
 static LocalDateTime start(Trip t,Trip.Stop s){return LocalDate.parse(t.start).plusDays(s.day).atTime(LocalTime.parse(s.time));}
 static TravelDaySummary forTrip(Trip t,LocalDateTime now){
  try{
   LocalDate first=LocalDate.parse(t.start);LocalDateTime begin=first.atStartOfDay(),end=first.plusDays(t.days).atStartOfDay();
   ArrayList<Trip.Stop> stops=new ArrayList<>(t.stops);stops.sort(Comparator.comparing(s->start(t,s)));
   Trip.Stop current=null,next=null,reservation=null;int count=0;boolean hasToday=false;
   for(Trip.Stop s:stops){LocalDateTime at=start(t,s),until=at.plusMinutes(s.duration);if(until.isAfter(end))end=until;
    if(at.toLocalDate().equals(now.toLocalDate()))hasToday=true;
    if(!now.isBefore(at)&&now.isBefore(until)){if(current==null)current=s;count++;}
    if(at.isAfter(now)&&next==null)next=s;
    if(s.timeLocked&&until.isAfter(now)&&reservation==null)reservation=s;
   }
   int today=(int)java.time.temporal.ChronoUnit.DAYS.between(first,now.toLocalDate());if(today<0||today>=t.days)today=-1;
   State state=now.isBefore(begin)?State.UPCOMING:!now.isBefore(end)?State.PAST:current!=null?State.ACTIVE:!hasToday?State.EMPTY:next!=null&&start(t,next).toLocalDate().equals(now.toLocalDate())?State.GAP:State.DAY_OVER;
   return new TravelDaySummary(state,current,next,reservation,today,count,begin,end);
  }catch(java.time.DateTimeException|NullPointerException e){return new TravelDaySummary(State.INVALID,null,null,null,-1,0,null,null);}
 }
}
