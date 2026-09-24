package cn.lvxu.travel;

import android.graphics.Bitmap;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.List;

/** Small UI building blocks for the trip-grouped check-in home and history page. */
final class CheckinHistoryUi {
    interface TripListener { void open(Trip trip); }

    private CheckinHistoryUi() {}

    static void addTripGroups(MainActivity a, List<Trip> trips, TripListener listener) {
        if (trips == null || trips.isEmpty()) {
            a.body.addView(a.text(CheckinGroups.emptyMessage(trips), 14, MainActivity.MUTED));
            return;
        }
        for (CheckinGroups.Group group : CheckinGroups.forTrips(trips)) {
            LinearLayout card = a.card(a.body);
            TextView title = a.bold(group.title, 19, MainActivity.INK);
            title.setContentDescription("打开：" + group.title);
            card.addView(title);
            a.space(card, 6);
            card.addView(a.text(group.dateRange, 13, MainActivity.MUTED));
            a.space(card, 4);
            card.addView(a.text(group.recordCount + " 条打卡记录", 13, MainActivity.MUTED));
            if (group.recordCount == 0) {
                a.space(card, 10);
                card.addView(a.text("该旅行还没有打卡记录。", 13, MainActivity.MUTED));
            }
            card.setContentDescription("打开旅行打卡：" + group.title);
            card.setOnClickListener(v -> { if (listener != null) listener.open(group.trip); });
        }
    }

    static void addRecords(MainActivity a, Trip trip, TripListener ownerGuard,
                           java.util.function.Consumer<Trip.Checkin> openRecord) {
        if (trip == null) {
            a.body.addView(a.text("还没有旅行。", 14, MainActivity.MUTED));
            return;
        }
        List<Trip.Checkin> records = CheckinGroups.records(trip);
        if (records.isEmpty()) {
            a.body.addView(a.text("该旅行还没有打卡记录。", 14, MainActivity.MUTED));
            return;
        }
        for (Trip.Checkin record : records) {
            if (record == null) continue;
            LinearLayout card = a.card(a.body);
            card.addView(a.bold(record.place == null || record.place.trim().isEmpty()
                    ? "旅途中的此刻" : record.place, 18, MainActivity.INK));
            a.space(card, 6);
            card.addView(a.text(DateTimeFields.displayDateTime(record.time), 13, MainActivity.MUTED));
            card.setOnClickListener(v -> {
                if (CheckinGroups.ownerOf(java.util.Collections.singletonList(trip), record) != trip) return;
                if (openRecord != null) openRecord.accept(record);
            });
        }
    }

    /** Returns a fixed square thumbnail view; CENTER_CROP never rewrites the source bitmap/file. */
    static ImageView squareThumbnail(MainActivity a, Bitmap bitmap, int sizeDp) {
        ImageView image = new ImageView(a);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setImageBitmap(bitmap);
        image.setContentDescription("打卡照片缩略图（正方形预览）");
        int size = a.dp(Math.max(1, sizeDp));
        image.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        return image;
    }
}
