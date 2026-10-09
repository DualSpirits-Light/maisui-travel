package cn.lvxu.travel;
import java.time.LocalDate;
/** Date boundary and consecutive-entry regression tests independent of Android UI. */
public final class TravelFormRulesTest {
 private static int checks;
 private static void check(boolean value,String label){if(!value)throw new AssertionError(label);checks++;}
 public static int run(){checks=0;Trip t=new Trip();t.start="2026-10-01";t.days=3;
  check(!TravelFormRules.completed(t,LocalDate.of(2026,10,3)),"last travel date stays current");
  check(TravelFormRules.completed(t,LocalDate.of(2026,10,4)),"completion begins the day after last travel date");
  check(!TravelFormRules.completed(t,LocalDate.of(2026,9,30)),"future travel remains current");
  t.start="broken";check(!TravelFormRules.completed(t,LocalDate.of(2026,10,4)),"invalid date cannot hide a trip as completed");
  check(TravelFormRules.companions(" 小麦,小穗；小麦、阿青; 小黄， ").equals("小麦、小穗、阿青、小黄"),"all separators trim and deduplicate in entry order");
  check(TravelFormRules.companions(" ,；、 ").isEmpty(),"empty names are ignored");
  boolean rejected=false;try{TravelFormRules.companions("人".repeat(41));}catch(IllegalArgumentException expected){rejected=true;}check(rejected,"oversize individual name rejected");
  StringBuilder people=new StringBuilder();for(int i=0;i<51;i++)people.append("人").append(i).append(',');rejected=false;try{TravelFormRules.companions(people.toString());}catch(IllegalArgumentException expected){rejected=true;}check(rejected,"more than fifty companions rejected");
  Trip.Stop p=new Trip.Stop();p.day=0;p.time="23:30";p.duration=90;Trip.Stop next=TravelFormRules.after(t,p);
  check(next.day==1&&next.time.equals("01:00"),"late stop carries into next travel day");
  check(p.day==0&&p.time.equals("23:30")&&p.duration==90,"suggestion does not change previous stop");
  p.time="23:00";p.duration=60;next=TravelFormRules.after(t,p);check(next.day==1&&next.time.equals("00:00"),"exact midnight advances date");
  p.day=2;rejected=false;try{TravelFormRules.after(t,p);}catch(IllegalArgumentException expected){rejected=true;}check(rejected,"final-day midnight cannot silently wrap outside trip");
  t.stops.add(p);Trip.Stop manual=TravelFormRules.next(t,2);check(manual.day==2&&manual.time.equals("09:00"),"final-day inferred overflow still allows manual entry");t.stops.clear();
  p.day=0;p.time="09:15";p.duration=45;t.stops.add(p);next=TravelFormRules.next(t,0);check(next.day==0&&next.time.equals("10:00"),"consecutive entry uses previous stop duration");
  Trip.Stop earlier=new Trip.Stop();earlier.time="08:00";earlier.duration=30;earlier.sortOrder=1;p.sortOrder=0;t.stops.add(earlier);next=TravelFormRules.next(t,0);check(next.time.equals("10:00"),"manual reorder cannot reset new stop before latest end");
  next=TravelFormRules.next(t,1);check(next.day==1&&next.time.equals(new Trip.Stop().time),"empty day retains default start time");return checks;
 }
 public static void main(String[] args){System.out.println("PASS: "+run()+" travel form rule assertions");}
}
