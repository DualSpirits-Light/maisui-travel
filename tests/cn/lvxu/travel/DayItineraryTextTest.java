package cn.lvxu.travel;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** Copy/share text regression checks without Android dependencies. */
public final class DayItineraryTextTest {
    private static int checks;
    private static Method formatter;

    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
        checks++;
    }

    private static String format(Trip trip, int day, boolean notes) throws Exception {
        try { return (String) formatter.invoke(null, trip, day, notes); }
        catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof IllegalArgumentException)
                throw (IllegalArgumentException) failure.getCause();
            throw failure;
        }
    }

    public static int run() throws Exception {
        checks = 0;
        try { formatter = Class.forName("cn.lvxu.travel.DayItineraryText")
                .getDeclaredMethod("format", Trip.class, int.class, boolean.class); }
        catch (ClassNotFoundException missing) { throw new AssertionError("Missing class: DayItineraryText", missing); }
        formatter.setAccessible(true);
        Trip trip = new Trip();
        trip.title = "杭州慢游"; trip.city = "杭州"; trip.start = "2026-12-31"; trip.days = 3;
        trip.budget = 987654; trip.companions = "PRIVATE_MEMBER_13812345678";
        trip.vehicleNumber = "PRIVATE_TICKET"; trip.accommodation = "PRIVATE_LODGING";
        trip.accommodationAddress = "PRIVATE_LODGING_ADDRESS";
        trip.accommodationSourceUrl = "PRIVATE_LODGING_LINK";
        Trip.Stop night = new Trip.Stop();
        night.name = "跨年夜景"; night.day = 0; night.time = "23:30"; night.duration = 90;
        night.mode = "地铁"; night.timeLocked = true; night.sortOrder = 0;
        night.note = "晚点出门"; night.address = "湖滨路"; night.cost = 456789;
        night.sourceUrl = "https://www.amap.com/PRIVATE_SOURCE";
        night.sourceSnapshot = "PRIVATE_API_KEY"; night.previewPhoto = "PRIVATE_PHOTO_PATH";
        night.notePhotos.add("PRIVATE_NOTE_PHOTO");
        Trip.Stop morning = new Trip.Stop(); morning.name = "早餐"; morning.day = 0;
        morning.time = "08:00"; morning.duration = 45; morning.sortOrder = 1;
        Trip.Stop otherDay = new Trip.Stop(); otherDay.name = "OTHER_DAY_STOP"; otherDay.day = 1;
        trip.stops.add(morning); trip.stops.add(otherDay); trip.stops.add(night);
        String text = format(trip, 0, false);
        Method defaultFormatter = formatter.getDeclaringClass().getDeclaredMethod("format", Trip.class, int.class);
        defaultFormatter.setAccessible(true);
        check(text.equals(defaultFormatter.invoke(null, trip, 0)), "default overload excludes optional details");
        check(text.contains("杭州慢游"), "trip title retained");
        check(text.contains("目的地：杭州"), "destination explicit");
        check(text.contains("2026年12月31日"), "full Chinese date retained");
        check(text.contains("第1天"), "day number retained");
        check(text.contains("23:30") && text.contains("次日 01:00"), "midnight end explicit");
        check(text.contains("交通：地铁"), "planned transport retained");
        check(text.contains("固定预约"), "locked time explicit");
        check(text.indexOf("跨年夜景") < text.indexOf("早餐"), "manual drag order retained");
        check(!text.contains("OTHER_DAY_STOP"), "only selected day exported");
        check(!text.contains("晚点出门") && !text.contains("湖滨路"), "notes and address excluded by default");
        check(text.contains("计划安排，请确认开放时间及交通"), "plan verification reminder included");
        String detail = format(trip, 0, true);
        check(detail.contains("备注：晚点出门") && detail.contains("地址：湖滨路"), "optional notes and address included");
        for (String excluded : new String[]{"987654", "456789", "PRIVATE_", "amap.com", "预算", "费用", "¥"}) {
            check(!text.contains(excluded), "default excludes " + excluded);
            check(!detail.contains(excluded), "detailed excludes " + excluded);
        }
        String empty = format(trip, 2, false);
        check(empty.contains("2027年1月2日"), "date crosses year correctly");
        check(empty.contains("这一天还没有安排地点"), "empty day explanation");
        check(empty.contains("计划安排，请确认开放时间及交通"), "empty day reminder");
        for (int invalid : new int[]{-1, 3, Integer.MAX_VALUE}) {
            boolean rejected = false;
            try { format(trip, invalid, false); } catch (IllegalArgumentException expected) { rejected = true; }
            check(rejected, "invalid day rejected: " + invalid);
        }
        boolean nullRejected = false;
        try { format(null, 0, false); } catch (IllegalArgumentException expected) { nullRejected = true; }
        check(nullRejected, "null trip rejected");
        night.time = "23:00"; night.duration = 60;
        check(format(trip, 0, false).contains("次日 00:00"), "exact midnight remains explicit");
        night.time = "00:00"; night.duration = 1440;
        check(format(trip, 0, false).contains("次日 00:00"), "full day duration remains explicit");
        check(trip.stops.get(0) == morning && night.sortOrder == 0 && night.timeLocked,
                "export leaves source order and locked state unchanged");
        return checks;
    }

    public static void main(String[] args) throws Exception {
        System.out.println("PASS: " + run() + " day itinerary text assertions");
    }
}
