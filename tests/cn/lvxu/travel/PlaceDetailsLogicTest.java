package cn.lvxu.travel;

import java.util.List;
import java.util.Arrays;

/** Contract tests for place-details rendering data, external map links and check-in prefill. */
public final class PlaceDetailsLogicTest {
    private static int checks;

    private static void check(boolean value, String name) {
        if (!value) throw new AssertionError(name);
        checks++;
    }

    private static String value(List<PlaceDetailsContent.Row> rows, String label) {
        for (PlaceDetailsContent.Row row : rows) if (label.equals(row.label)) return row.value;
        return "";
    }

    public static void main(String[] args) {
        Trip trip = new Trip();
        trip.start = "2026-09-20";
        trip.days = 2;
        Trip.Stop stop = new Trip.Stop();
        stop.name = "夜游西湖";
        stop.day = 0;
        stop.time = "23:30";
        stop.duration = 120;
        stop.mode = "地铁";
        stop.address = "";
        stop.openingHours = "";
        stop.note = "";
        stop.rating = 0f;
        List<PlaceDetailsContent.Row> rows = PlaceDetailsContent.rows(trip, stop);
        check("2026年9月20日".equals(value(rows, "旅行日期")), "uses trip date and stop day");
        check("23:30 - 次日 01:30".equals(value(rows, "开始 - 结束")), "labels cross-day end time");
        check("地铁".equals(value(rows, "交通")), "shows transportation");
        check(value(rows, "地址").isEmpty() && value(rows, "营业时间").isEmpty() && value(rows, "备注").isEmpty(), "omits missing optional fields");
        check(PlaceDetailsContent.rating(0f).contains("☆☆☆☆☆") && PlaceDetailsContent.rating(0f).contains("0 颗整星"), "renders zero-star rating readably");
        check(PlaceDetailsContent.rating(3.5f).contains("½") && PlaceDetailsContent.rating(3.5f).contains("1 颗半星"), "renders half-star rating readably");
        check(PlaceDetailsContent.rating(5f).contains("★★★★★") && PlaceDetailsContent.rating(5f).contains("5 颗整星"), "renders full-star rating readably");

        MapSearchLinks.Links amap = MapSearchLinks.forPlace("amap", "西湖 断桥");
        check(amap.appUri.startsWith("androidamap://poi?") && amap.appUri.contains("keywords=")
                && !amap.appUri.contains("keywordNavi") && amap.webUri.startsWith("https://")
                && amap.appUri.contains("%20"), "AMap opens the documented POI search action with HTTPS fallback");
        MapSearchLinks.Links tencent = MapSearchLinks.forPlace("tencent", "西湖");
        check(tencent.appUri.startsWith("qqmap://") && tencent.packageName.equals("com.tencent.map"), "Tencent link uses selected service");
        MapSearchLinks.Links baidu = MapSearchLinks.forPlace("baidu", "西湖");
        check(baidu.appUri.startsWith("baidumap://") && baidu.webUri.contains("map.baidu.com"), "Baidu link uses selected service");

        List<PlaceDetailsContent.Photo> noteOnly = PlaceDetailsContent.photos("",
                Arrays.asList("photos/note-one.jpg", "", "photos/note-two.jpg"));
        check(noteOnly.size() == 2 && "备注照片 1".equals(noteOnly.get(0).label)
                && "备注照片 2".equals(noteOnly.get(1).label), "note-only photos never receive a preview label");
        List<PlaceDetailsContent.Photo> withPreview = PlaceDetailsContent.photos("places/preview.jpg",
                Arrays.asList("photos/note-one.jpg"));
        check("预览图".equals(withPreview.get(0).label) && "备注照片 1".equals(withPreview.get(1).label),
                "preview and notes keep their distinct labels");

        CheckinPrefill.Draft draft = CheckinPrefill.forStop(trip, stop);
        check(draft.trip == trip && "夜游西湖".equals(draft.place), "check-in prefill keeps its trip and place context");
        System.out.println("PASS: " + checks + " place-details assertions");
    }
}
