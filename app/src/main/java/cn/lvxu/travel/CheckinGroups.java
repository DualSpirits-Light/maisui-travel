package cn.lvxu.travel;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Pure grouping and ownership rules for the check-in home and history pages. */
final class CheckinGroups {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy年M月d日");
    private static final DateTimeFormatter EDIT_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter LEGACY_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    private CheckinGroups() {}

    static final class Group {
        final Trip trip;
        final String title;
        final String dateRange;
        final int recordCount;

        Group(Trip trip) {
            this.trip = trip;
            this.title = trip == null ? "" : trip.title;
            this.dateRange = dateRange(trip);
            this.recordCount = records(trip).size();
        }
    }

    static ArrayList<Group> forTrips(List<Trip> trips) {
        ArrayList<Group> groups = new ArrayList<>();
        if (trips == null) return groups;
        for (Trip trip : trips) if (trip != null) groups.add(new Group(trip));
        return groups;
    }

    static ArrayList<Trip.Checkin> records(Trip trip) {
        ArrayList<Trip.Checkin> records = new ArrayList<>();
        if (trip == null || trip.checkins == null) return records;
        records.addAll(trip.checkins);
        records.sort((left, right) -> {
            LocalDateTime a = parse(left == null ? "" : left.time);
            LocalDateTime b = parse(right == null ? "" : right.time);
            if (a != null && b != null) return b.compareTo(a);
            if (a != null) return -1;
            if (b != null) return 1;
            String as = left == null || left.time == null ? "" : left.time;
            String bs = right == null || right.time == null ? "" : right.time;
            return bs.compareTo(as);
        });
        return records;
    }

    static Trip ownerOf(List<Trip> trips, Trip.Checkin checkin) {
        if (trips == null || checkin == null) return null;
        for (Trip trip : trips) {
            if (trip == null || trip.checkins == null) continue;
            for (Trip.Checkin candidate : trip.checkins) if (candidate == checkin) return trip;
        }
        if (checkin.id == null || checkin.id.trim().isEmpty()) return null;
        for (Trip trip : trips) {
            if (trip == null || trip.checkins == null) continue;
            for (Trip.Checkin candidate : trip.checkins)
                if (candidate != null && checkin.id.equals(candidate.id)) return trip;
        }
        return null;
    }

    static String dateRange(Trip trip) {
        if (trip == null) return "";
        try {
            LocalDate start = LocalDate.parse(trip.start);
            LocalDate end = start.plusDays(Math.max(0, trip.days - 1L));
            return start.format(DATE) + " - " + end.format(DATE);
        } catch (Exception ignored) {
            return trip.start == null ? "" : trip.start.replace('T', ' ');
        }
    }

    static String emptyMessage(Trip trip) {
        return trip == null ? "还没有旅行。" : "该旅行还没有打卡记录。";
    }

    static String emptyMessage(List<Trip> trips) {
        return trips == null || trips.isEmpty() ? "还没有旅行。" : "";
    }

    /** One human-readable line for a record card; storage formatting never leaks into UI. */
    static String recordSubtitle(Trip.Checkin record) {
        if (record == null) return "未填写心情";
        String mood = record.mood == null || record.mood.trim().isEmpty()
                ? "未填写心情" : record.mood.trim();
        String when = DateTimeValues.display(record.time);
        String people = record.companions == null || record.companions.isEmpty()
                ? "" : " · 同行 " + String.join("、", record.companions);
        return when.isEmpty() ? mood + people : mood + "  ·  " + when + people;
    }

    private static LocalDateTime parse(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        String candidate = value.trim();
        for (DateTimeFormatter formatter : new DateTimeFormatter[]{EDIT_DATE_TIME, LEGACY_DATE_TIME}) {
            try { return LocalDateTime.parse(candidate, formatter); } catch (Exception ignored) {}
        }
        try { return LocalDateTime.parse(candidate); } catch (Exception ignored) {}
        return null;
    }
}
