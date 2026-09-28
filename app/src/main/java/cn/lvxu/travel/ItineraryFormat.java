package cn.lvxu.travel;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Shared presentation of itinerary times. Gaps are schedule gaps, never route estimates. */
final class ItineraryFormat {
    private ItineraryFormat() {}
    static int startMinute(Trip.Stop stop) { return LocalTime.parse(stop.time).toSecondOfDay()/60; }
    static String timeRange(Trip.Stop stop) {
        int end=startMinute(stop)+stop.duration;
        String prefix=end>=2880?"第"+(end/1440+1)+"日 ":end>=1440?"次日 ":"";
        return stop.time+" — "+prefix+String.format(Locale.ROOT,"%02d:%02d",end%1440/60,end%60);
    }
    static String dateLabel(Trip trip,int day) {
        LocalDate date=LocalDate.parse(trip.start).plusDays(day);
        String[] weekdays={"星期一","星期二","星期三","星期四","星期五","星期六","星期日"};
        return "第 "+(day+1)+" 天 · "+date.format(DateTimeFormatter.ofPattern("yyyy年M月d日",Locale.CHINA))+" · "+weekdays[date.getDayOfWeek().getValue()-1];
    }
    static String connection(Trip.Stop previous,Trip.Stop next) {
        String mode=next.mode==null||next.mode.trim().isEmpty()?"抵达方式未填写":next.mode+"抵达";
        if(previous==null)return mode;
        int start=startMinute(next),before=startMinute(previous);
        if(start<before)return mode+" · 时间顺序待检查";
        int gap=start-before-previous.duration;
        return mode+(gap<0?" · 时间冲突，前一安排尚未结束":gap==0?" · 安排紧接，无预留间隔":" · 安排间隔 "+gap+" 分钟");
    }
    static boolean overlaps(Trip.Stop a,Trip.Stop b) {
        return startMinute(a)<startMinute(b)+b.duration&&startMinute(b)<startMinute(a)+a.duration;
    }
}
