package cn.lvxu.travel;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.os.Build;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;

import com.amap.api.services.core.AMapException;
import com.amap.api.services.core.LatLonPoint;
import com.amap.api.services.core.PoiItemV2;
import com.amap.api.services.core.ServiceSettings;
import com.amap.api.services.poisearch.Business;
import com.amap.api.services.poisearch.PoiResultV2;
import com.amap.api.services.poisearch.PoiSearchV2;
import com.amap.api.services.poisearch.VisualSearchResult;

import java.util.ArrayList;

/** User-initiated AMap keyword search which creates a draft itinerary stop. */
final class AmapPlaceSearch {
    private static final int PAGE_SIZE = 20;
    private final MainActivity activity;
    private ProgressDialog progress;
    private int requestGeneration;

    AmapPlaceSearch(MainActivity activity) {
        this.activity = activity;
    }

    void search(String keyword) {
        final String clean = keyword == null ? "" : keyword.trim();
        if (clean.isEmpty()) {
            showSearchDialog();
            return;
        }
        if (clean.length() > 120) {
            activity.toast("搜索关键词过长");
            return;
        }
        if (activity.active == null) {
            activity.toast("请先创建一个旅行");
            return;
        }
        AmapConsent.request(activity, () -> beginSearch(clean));
    }

    private void showSearchDialog() {
        if (!alive()) return;
        LinearLayout form = activity.col();
        activity.pad(form, 20);
        EditText input = activity.field(form, "地点名称或关键词", "",
                InputType.TYPE_CLASS_TEXT);
        input.setHint("例如：西湖、博物馆、咖啡");
        String city = activity.active == null ? "" : safe(activity.active.city);
        if (!city.isEmpty()) form.addView(activity.text("搜索城市：" + city, 12, MainActivity.MUTED));
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("搜索高德地点")
                .setView(form)
                .setNegativeButton("取消", null)
                .setPositiveButton("搜索", null)
                .create();
        dialog.setOnShowListener(v -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(button -> {
                    String value = input.getText().toString().trim();
                    if (value.isEmpty()) {
                        input.setError("请输入搜索内容");
                        return;
                    }
                    dialog.dismiss();
                    search(value);
                }));
        dialog.show();
    }

    private void beginSearch(String keyword) {
        if (!alive() || !AmapConsent.granted(activity)) return;
        final int generation = ++requestGeneration;
        final Trip targetTrip = activity.active;
        final int targetDay = activity.day;
        if (targetTrip == null) return;
        dismissProgress();
        progress = new ProgressDialog(activity);
        progress.setMessage("正在搜索高德地点…");
        progress.setCancelable(true);
        progress.setOnCancelListener(dialog -> requestGeneration++);
        progress.show();
        try {
            // These SDK privacy calls are deliberately reached only after consent is granted.
            ServiceSettings settings = ServiceSettings.getInstance();
            settings.updatePrivacyShow(activity, true, true);
            settings.updatePrivacyAgree(activity, true);
            String city = safe(targetTrip.city);
            PoiSearchV2.Query query = new PoiSearchV2.Query(keyword, "", city);
            query.setPageSize(PAGE_SIZE);
            query.setPageNum(1);
            query.setCityLimit(!city.isEmpty());
            query.setShowFields(new PoiSearchV2.ShowFields(PoiSearchV2.ShowFields.BUSINESS | PoiSearchV2.ShowFields.PHOTOS));
            PoiSearchV2 search = new PoiSearchV2(activity, query);
            search.setOnPoiSearchListener(new Listener(generation, targetTrip, targetDay));
            search.searchPOIAsyn();
        } catch (AMapException | RuntimeException e) {
            if (generation == requestGeneration) {
                dismissProgress();
                activity.toast("暂时无法搜索高德地点");
            }
        }
    }

    private void deliver(int generation, Trip targetTrip, int targetDay,
                         PoiResultV2 result, int code) {
        if (generation != requestGeneration) return;
        if (!alive()) { dismissProgress(); return; }
        dismissProgress();
        if (code != 1000 || result == null || result.getPois() == null) {
            activity.toast("高德地点搜索失败（" + code + "）");
            return;
        }
        ArrayList<PoiItemV2> pois = result.getPois();
        if (pois.isEmpty()) {
            activity.toast("没有找到相关地点，请换个关键词");
            return;
        }
        String[] labels = new String[pois.size()];
        for (int i = 0; i < pois.size(); i++) {
            PoiItemV2 poi = pois.get(i);
            String name = safe(poi.getTitle());
            String address = safe(poi.getSnippet());
            labels[i] = address.isEmpty() ? name : name + "\n" + address;
        }
        new AlertDialog.Builder(activity)
            .setTitle("选择地点")
            .setItems(labels, (dialog, which) -> openDraft(pois.get(which), targetTrip, targetDay))
            .setNegativeButton("取消", null)
            .show();
    }

    private void openDraft(PoiItemV2 poi, Trip targetTrip, int targetDay) {
        if (!alive() || poi == null) return;
        if (activity.active != targetTrip) {
            activity.toast("已切换旅行，请在当前行程里重新搜索");
            return;
        }
        String name = clip(safe(poi.getTitle()), 120);
        if (name.isEmpty()) {
            activity.toast("该搜索结果缺少地点名称");
            return;
        }
        Trip.Stop stop = new Trip.Stop();
        stop.name = name;
        stop.address = clip(safe(poi.getSnippet()), 300);
        stop.coordinateSystem = "GCJ02";
        stop.day = Math.max(0, Math.min(targetDay, targetTrip.days - 1));
        LatLonPoint point = poi.getLatLonPoint();
        if (point != null) {
            double lat = point.getLatitude(), lon = point.getLongitude();
            if (Double.isFinite(lat) && Double.isFinite(lon)
                    && Math.abs(lat) <= 90 && Math.abs(lon) <= 180) {
                stop.lat = lat;
                stop.lon = lon;
            }
        }
        Business business = poi.getBusiness();
        if (business != null) {
            stop.openingHours = clip(safe(business.getOpentimeToday()), 300);if(stop.openingHours.isEmpty())stop.openingHours=clip(safe(business.getOpentimeWeek()),300);
            String rawRating = safe(business.getmRating());
            try {
                if (!rawRating.isEmpty()) {
                    float rating = Float.parseFloat(rawRating);
                    if (Float.isFinite(rating) && rating >= 0 && rating <= 5) stop.rating = rating;
                }
            } catch (NumberFormatException ignored) { }
        }
        PlaceImporter.Place info=new PlaceImporter.Place();AmapDetails.fill(info,poi);
        activity.runJob("正在读取地点照片…",()->{try{stop.previewPhoto=PlaceMediaUi.download(activity,info.imageUrl);}catch(Exception ignored){}return null;},()->activity.stopEditorDraft(stop));
    }

    private boolean alive() {
        return !activity.isFinishing()
            && (Build.VERSION.SDK_INT < 17 || !activity.isDestroyed());
    }

    private void dismissProgress() {
        if (progress != null) {
            if (progress.isShowing()) progress.dismiss();
            progress = null;
        }
    }

    private static String safe(String value) { return value == null ? "" : value.trim(); }
    private static String clip(String value, int max) {
        return value.substring(0, Math.min(max, value.length()));
    }

    private final class Listener implements PoiSearchV2.OnPoiSearchListener {
        private final int generation;
        private final Trip targetTrip;
        private final int targetDay;
        Listener(int generation, Trip targetTrip, int targetDay) {
            this.generation = generation;
            this.targetTrip = targetTrip;
            this.targetDay = targetDay;
        }
        @Override public void onPoiSearched(PoiResultV2 result, int code) {
            deliver(generation, targetTrip, targetDay, result, code);
        }
        @Override public void onPoiItemSearched(PoiItemV2 item, int code) { }
        @Override public void onVisualSearched(VisualSearchResult result, int code) { }
    }
}
