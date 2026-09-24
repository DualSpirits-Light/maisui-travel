package cn.lvxu.travel;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Pure display data for a saved place, kept separate from the Android page. */
final class PlaceDetailsContent {
    static final class Row {
        final String label;
        final String value;
        Row(String label, String value) { this.label = label; this.value = value; }
    }
    static final class Photo {
        final String path;
        final String label;
        Photo(String path, String label) { this.path = path; this.label = label; }
    }

    private PlaceDetailsContent() { }

    static List<Row> rows(Trip trip, Trip.Stop stop) {
        ArrayList<Row> rows = new ArrayList<>();
        add(rows, "旅行日期", date(trip, stop));
        add(rows, "开始 - 结束", timeRange(stop));
        add(rows, "交通", clean(stop == null ? "" : stop.mode));
        add(rows, "地址", clean(stop == null ? "" : stop.address));
        add(rows, "营业时间", clean(stop == null ? "" : stop.openingHours));
        if (stop != null && stop.rating != null) add(rows, "评分", rating(stop.rating));
        add(rows, "备注", clean(stop == null ? "" : stop.note));
        return rows;
    }

    static String rating(float score) {
        float normalized = Math.max(0f, Math.min(5f, score));
        int halfSteps = Math.round(normalized * 2f);
        int full = halfSteps / 2;
        boolean half = halfSteps % 2 == 1;
        StringBuilder stars = new StringBuilder();
        for (int index = 0; index < full; index++) stars.append('★');
        if (half) stars.append('½');
        while (stars.length() < 5) stars.append('☆');
        String detail = full + " 颗整星" + (half ? "和 1 颗半星" : "");
        return stars + " " + String.format(Locale.ROOT, "%.1f", normalized) + " 分（" + detail + "）";
    }

    static List<Photo> photos(String previewPhoto, List<String> notePhotos) {
        ArrayList<Photo> photos = new ArrayList<>();
        String preview = clean(previewPhoto);
        if (!preview.isEmpty()) photos.add(new Photo(preview, "预览图"));
        int noteNumber = 0;
        if (notePhotos != null) for (String note : notePhotos) {
            String path = clean(note);
            if (!path.isEmpty()) photos.add(new Photo(path, "备注照片 " + (++noteNumber)));
        }
        return photos;
    }

    private static String date(Trip trip, Trip.Stop stop) {
        if (trip == null || stop == null) return "";
        try {
            return LocalDate.parse(trip.start).plusDays(stop.day)
                    .format(DateTimeFormatter.ofPattern("yyyy年M月d日"));
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String timeRange(Trip.Stop stop) {
        if (stop == null) return "";
        try {
            LocalTime start = LocalTime.parse(stop.time);
            int minutes = Math.max(0, stop.duration);
            LocalTime end = start.plusMinutes(minutes);
            long total = start.toSecondOfDay() / 60L + minutes;
            return start + " - " + (total >= 24 * 60 ? "次日 " : "") + end;
        } catch (Exception ignored) {
            return "";
        }
    }

    private static void add(List<Row> rows, String label, String value) {
        if (!value.isEmpty()) rows.add(new Row(label, value));
    }

    private static String clean(String value) { return value == null ? "" : value.trim(); }
}
