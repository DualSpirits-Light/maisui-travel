package cn.lvxu.travel;

import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;

/** Opens the configured provider's place search without treating Android SDK keys as Web keys. */
final class MapSearchUi {
    private final MainActivity activity;

    MapSearchUi(MainActivity activity) {
        this.activity = activity;
    }

    void show() {
        String provider = new ApiConfig(activity).mapProvider();
        if ("amap".equals(provider)) {
            if (!AmapRuntime.configured(activity)) {
                new AdvancedSettingsUi(activity).androidKey(this::show);
                return;
            }
            if (AmapRuntime.needsRestart(activity)) {
                activity.toast("高德 Android Key 已更改，请重启应用后搜索");
                return;
            }
            new AmapPlaceSearch(activity).search("");
            return;
        }

        MapService service = new MapService(activity);
        if (!service.configured()) {
            activity.toast("请先在高级设置中配置 " + service.name() + " Web 服务 Key");
            return;
        }
        if (activity.active == null) {
            activity.toast("请先创建旅行");
            return;
        }
        Trip target = activity.active;
        int day = activity.day;
        PageUi page = new PageUi(activity, service.name() + " · 搜索地点");
        EditText queryInput = activity.field(page.body, "地点名称", "", InputType.TYPE_CLASS_TEXT);
        LinearLayout results = activity.col();
        TextView submit = activity.action("搜索", true, () -> {
            String query = queryInput.getText().toString().trim();
            if (query.isEmpty()) {
                activity.toast("请输入地点名称");
                return;
            }
            results.removeAllViews();
            results.addView(activity.text("正在搜索…", 14, MainActivity.MUTED));
            new Thread(() -> searchWebProvider(service, query, target, day, page, results),
                    "map-search").start();
        });
        page.body.addView(submit);
        page.body.addView(results);
        page.show();
    }

    private void searchWebProvider(MapService service, String query, Trip target, int day,
                                   PageUi page, LinearLayout results) {
        try {
            ArrayList<PlaceImporter.Place> found = service.search(query, target.city);
            activity.runOnUiThread(() -> showResults(found, target, day, page, results));
        } catch (Exception error) {
            activity.runOnUiThread(() -> {
                if (!page.alive()) return;
                results.removeAllViews();
                results.addView(activity.text(error.getMessage() == null
                        ? "搜索失败，请重试" : error.getMessage(), 14, MainActivity.ORANGE));
            });
        }
    }

    private void showResults(ArrayList<PlaceImporter.Place> found, Trip target, int day,
                             PageUi page, LinearLayout results) {
        if (!page.alive()) return;
        results.removeAllViews();
        if (found.isEmpty()) {
            results.addView(activity.text("未找到相关地点，请换个关键词。", 14, MainActivity.MUTED));
            return;
        }
        for (PlaceImporter.Place place : found) {
            LinearLayout card = activity.card(results);
            card.addView(activity.bold(place.name, 18, MainActivity.INK));
            card.addView(activity.text(place.address, 13, MainActivity.MUTED));
            card.addView(activity.action("添加到行程", false,
                    () -> addToTrip(place, target, day, page)));
        }
    }

    private void addToTrip(PlaceImporter.Place place, Trip target, int day, PageUi page) {
        if (!activity.trips.contains(target)) {
            activity.toast("旅行已删除");
            return;
        }
        Trip.Stop stop = new Trip.Stop();
        stop.name = place.name;
        stop.address = place.address;
        stop.lat = place.lat;
        stop.lon = place.lon;
        stop.coordinateSystem = place.coordinateSystem;
        stop.sourceUrl = place.sourceUrl;
        stop.openingHours = place.openingHours;
        stop.day = day;
        try {
            stop.rating = Float.valueOf(place.rating);
        } catch (Exception ignored) { }
        stop.sourceSnapshot = TripLinkCodec.snapshot(stop);
        activity.active = target;
        activity.day = day;
        page.dialog.dismiss();
        activity.stopEditorDraft(stop);
    }
}
