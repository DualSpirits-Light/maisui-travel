package cn.lvxu.travel;
public final class ItineraryFormatTest {
 public static void main(String[] args) {
  Trip t=new Trip();t.start="2026-09-27";
  Trip.Stop a=new Trip.Stop();a.time="23:30";a.duration=90;
  eq("23:30 — 次日 01:00",ItineraryFormat.timeRange(a));
  a.time="09:05";a.duration=55;eq("09:05 — 10:00",ItineraryFormat.timeRange(a));
  eq("第 2 天 · 2026年9月28日 · 星期一",ItineraryFormat.dateLabel(t,1));
  Trip.Stop b=new Trip.Stop();b.time="11:00";b.mode="公交";
  eq("公交抵达 · 安排间隔 60 分钟",ItineraryFormat.connection(a,b));
  b.time="09:30";eq("公交抵达 · 时间冲突，前一安排尚未结束",ItineraryFormat.connection(a,b));
  b.time="08:00";eq("公交抵达 · 时间顺序待检查",ItineraryFormat.connection(a,b));
  a.time="23:00";a.duration=1500;eq("23:00 — 第3日 00:00",ItineraryFormat.timeRange(a));
  System.out.println("ItineraryFormatTest: 7 checks passed");
 }
 static void eq(String expected,String actual){if(!expected.equals(actual))throw new AssertionError(expected+" != "+actual);}
}
