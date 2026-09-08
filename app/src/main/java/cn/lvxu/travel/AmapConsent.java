package cn.lvxu.travel;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.SpannableString;
import android.text.method.LinkMovementMethod;
import android.text.style.URLSpan;
import android.widget.TextView;

import com.amap.api.maps.MapsInitializer;

/** Applies the AMap privacy switches only after the user has explicitly agreed. */
final class AmapConsent {
    private static final String PREFS = "amap-privacy";
    private static final String AGREED = "agreed-v1";
    private static final String POLICY = "https://lbs.amap.com/pages/privacy/";

    private AmapConsent() {}

    static boolean granted(Context context) {
        boolean agreed = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(AGREED, false);
        if (agreed) apply(context);
        return agreed;
    }

    static void request(MainActivity activity, Runnable accepted) {
        if (granted(activity)) {
            accepted.run();
            return;
        }
        String copy = "地图由高德地图提供。启用后，高德地图 SDK 会处理设备与网络信息、位置信息，用于地图展示、定位及相关安全保障。请阅读《高德地图开放平台隐私权政策》后决定是否同意。";
        SpannableString message = new SpannableString(copy);
        String label = "《高德地图开放平台隐私权政策》";
        int start = copy.indexOf(label);
        message.setSpan(new URLSpan(POLICY), start, start + label.length(), 0);
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("启用高德地图")
                .setMessage(message)
                .setNegativeButton("暂不启用", null)
                .setPositiveButton("同意并启用", (d, which) -> {
                    activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                            .putBoolean(AGREED, true).apply();
                    apply(activity);
                    accepted.run();
                }).create();
        dialog.setOnShowListener(v -> {
            TextView text = dialog.findViewById(android.R.id.message);
            if (text != null) text.setMovementMethod(LinkMovementMethod.getInstance());
        });
        dialog.show();
    }

    private static void apply(Context context) {
        try {
            MapsInitializer.updatePrivacyShow(context.getApplicationContext(), true, true);
            MapsInitializer.updatePrivacyAgree(context.getApplicationContext(), true);
        } catch (RuntimeException ignored) {
            // AMapUi will fall back cleanly if this SDK cannot initialize.
        }
    }
}
