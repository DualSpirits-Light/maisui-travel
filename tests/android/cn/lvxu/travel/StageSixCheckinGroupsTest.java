package cn.lvxu.travel;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import java.util.ArrayList;

/** Android interaction coverage for trip-grouped check-in history. */
final class StageSixCheckinGroupsTest {
    private StageSixCheckinGroupsTest() {}

    static int run(Instrumentation in) throws Exception {
        Context context = in.getTargetContext();
        MainActivity activity = null;
        try {
            context.getSharedPreferences("app-prefs-v2", 0).edit().putBoolean("tutorial", true).apply();
            activity = (MainActivity) in.startActivitySync(new Intent(context, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
            MainActivity host = activity;
            Trip first = trip("stage-six-first", "西湖周末", "2026-09-20");
            Trip second = trip("stage-six-second", "苏州秋游", "2026-10-01");
            Trip.Checkin checkin = new Trip.Checkin();
            checkin.place = "平江路";
            checkin.time = "2026-10-01T09:30";
            second.checkins.add(checkin);
            ArrayList<Trip> trips = new ArrayList<>();
            trips.add(first);
            trips.add(second);
            in.runOnMainSync(() -> {
                host.trips.clear();
                host.trips.addAll(trips);
                host.active = first;
                host.page = 4;
                host.render();
            });
            in.waitForIdleSync();
            int checks = 0;
            checks += check(findDescription(host.root, "打开旅行打卡：西湖周末") != null,
                    "check-in home groups the first trip");
            checks += check(findDescription(host.root, "打开旅行打卡：苏州秋游") != null,
                    "check-in home groups the second trip");
            checks += check(findText(host.root, "2026年10月1日 - 2026年10月1日") != null,
                    "trip group displays a visible date range");
            View secondGroup = findDescription(host.root, "打开旅行打卡：苏州秋游");
            in.runOnMainSync(secondGroup::performClick);
            in.waitForIdleSync();
            checks += check(host.active == second, "opening a group selects its owning trip");
            checks += check(findTextContaining(host.root, "2026年10月1日 09:30") != null,
                    "history displays a Chinese date-time without T");
            return checks;
        } finally {
            if (activity != null) {
                MainActivity closing = activity;
                in.runOnMainSync(closing::finish);
            }
        }
    }

    private static Trip trip(String id, String title, String start) {
        Trip trip = new Trip();
        trip.id = id;
        trip.title = title;
        trip.city = "测试城";
        trip.start = start;
        trip.days = 1;
        return trip;
    }

    private static int check(boolean value, String name) {
        if (!value) throw new AssertionError(name);
        return 1;
    }

    private static View findDescription(View root, String value) {
        if (root == null) return null;
        CharSequence description = root.getContentDescription();
        if (description != null && description.toString().contains(value)) return root;
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findDescription(group.getChildAt(i), value);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static TextView findText(View root, String value) {
        if (root instanceof TextView && value.equals(((TextView) root).getText().toString()))
            return (TextView) root;
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                TextView found = findText(group.getChildAt(i), value);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static TextView findTextContaining(View root, String value) {
        if (root instanceof TextView && ((TextView) root).getText().toString().contains(value))
            return (TextView) root;
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                TextView found = findTextContaining(group.getChildAt(i), value);
                if (found != null) return found;
            }
        }
        return null;
    }
}
