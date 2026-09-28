package cn.lvxu.travel;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.widget.LinearLayout;

import java.util.List;

/** Read-only place page with source photos, map search and a contextual check-in action. */
final class PlaceDetailsUi {
    private final MainActivity activity;
    private final CheckinUi checkins;

    PlaceDetailsUi(MainActivity activity, CheckinUi checkins) {
        this.activity = activity;
        this.checkins = checkins;
    }

    void show(Trip trip, Trip.Stop stop) {
        if (trip == null || stop == null) {
            activity.toast("地点已删除");
            return;
        }
        PageUi page = new PageUi(activity, "地点详情");
        page.body.addView(activity.bold(name(stop), 24, MainActivity.INK));
        activity.space(page.body, 12);
        for (PlaceDetailsContent.Row row : PlaceDetailsContent.rows(trip, stop)) addRow(page.body, row);
        addPhotos(page.body, stop);
        String service = new ApiConfig(activity).mapProvider();
        MapSearchLinks.Links links = MapSearchLinks.forPlace(service, stop.name);
        activity.space(page.body, 10);
        page.body.addView(activity.action("在 " + MapService.name(service) + " 中搜索", true,
                () -> openSearch(links, stop.name)));
        activity.space(page.body, 8);
        page.body.addView(activity.action("以此地点新建打卡", false, () -> {
            page.dialog.dismiss();
            checkins.beginNew(trip, stop);
        }));
        activity.space(page.body,8);
        page.body.addView(activity.action("查看地点回忆（"+CheckinMemories.forStop(trip,stop).size()+" 条）",false,()->checkins.showPlaceMemories(trip,stop)));
        page.show();
    }

    private void addRow(LinearLayout parent, PlaceDetailsContent.Row row) {
        LinearLayout card = activity.card(parent);
        card.addView(activity.text(row.label, 13, MainActivity.MUTED));
        activity.space(card, 4);
        card.addView(activity.text(row.value, 16, MainActivity.INK));
    }

    private void addPhotos(LinearLayout parent, Trip.Stop stop) {
        List<PlaceDetailsContent.Photo> photos = PlaceDetailsContent.photos(stop.previewPhoto, stop.notePhotos);
        if (photos.isEmpty()) return;
        activity.space(parent, 2);
        parent.addView(activity.action("查看原始照片（" + photos.size() + " 张）", false,
                () -> choosePhoto(photos)));
    }

    private void choosePhoto(List<PlaceDetailsContent.Photo> photos) {
        String[] labels = new String[photos.size()];
        for (int index = 0; index < labels.length; index++) labels[index] = photos.get(index).label;
        new RoundedDialogs.Builder(activity).setTitle("原始照片").setItems(labels,
                (dialog, which) -> PhotoPreviewUi.show(activity, photos.get(which).path)).show();
    }

    private void openSearch(MapSearchLinks.Links links, String placeName) {
        if (empty(placeName)) {
            activity.toast("地点名称为空，无法发起地图搜索");
            return;
        }
        try {
            activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(links.appUri))
                    .setPackage(links.packageName));
            return;
        } catch (ActivityNotFoundException ignored) { }
        try {
            activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(links.webUri)));
            return;
        } catch (ActivityNotFoundException ignored) { }
        activity.toast("未找到可用的地图应用或浏览器");
    }

    private static String name(Trip.Stop stop) {
        String name = stop.name == null ? "" : stop.name.trim();
        return name.isEmpty() ? "未命名地点" : name;
    }

    private static boolean empty(String value) { return value == null || value.trim().isEmpty(); }
}
