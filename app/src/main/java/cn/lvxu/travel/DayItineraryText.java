package cn.lvxu.travel;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** Selected-day copy/share text. Only explicitly listed planning fields are exported. */
final class DayItineraryText {
    private DayItineraryText() { }

    static String format(Trip trip, int day) { return format(trip, day, false); }

    static String format(Trip trip, int day, boolean includeNotes) {
        if (trip == null || day < 0 || day >= trip.days)
            throw new IllegalArgumentException("请选择有效的旅行日期");
        LocalDate date = LocalDate.parse(trip.start).plusDays(day);
        StringBuilder text = new StringBuilder();
        text.append(clean(trip.title)).append('\n');
        text.append("目的地：").append(clean(trip.city)).append('\n');
        text.append("第").append(day + 1).append("天 · ")
                .append(date.format(DateTimeFormatter.ofPattern("yyyy年M月d日 EEEE", Locale.CHINA)))
                .append('\n');
        text.append("计划安排，请确认开放时间及交通\n");
        List<Trip.Stop> stops = trip.onDay(day);
        if (stops.isEmpty()) return text.append("\n这一天还没有安排地点。\n").toString();
        for (int index = 0; index < stops.size(); index++) {
            Trip.Stop stop = stops.get(index);
            text.append('\n').append(index + 1).append(". ").append(timeRange(stop))
                    .append(" · ").append(clean(stop.name)).append('\n');
            String mode = clean(stop.mode);
            text.append("交通：").append(mode.isEmpty() ? "未填写" : mode).append('\n');
            if (stop.timeLocked) text.append("固定预约（时间已锁定）\n");
            if (includeNotes) {
                appendDetail(text, "地址", stop.address);
                appendDetail(text, "备注", stop.note);
            }
        }
        return text.toString();
    }

    private static String timeRange(Trip.Stop stop) {
        LocalTime start = LocalTime.parse(stop.time);
        long endMinute = start.toSecondOfDay() / 60L + stop.duration;
        long daysLater = endMinute / 1440;
        String endDay = daysLater == 0 ? "" : daysLater == 1 ? "次日 " : daysLater + "日后 ";
        return start.format(DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)) + " — " + endDay
                + String.format(Locale.ROOT, "%02d:%02d", endMinute % 1440 / 60, endMinute % 60);
    }

    private static void appendDetail(StringBuilder text, String label, String value) {
        String detail = clean(value);
        if (!detail.isEmpty()) text.append(label).append('：').append(detail).append('\n');
    }

    private static String clean(String value) { return value == null ? "" : value.trim(); }
}
