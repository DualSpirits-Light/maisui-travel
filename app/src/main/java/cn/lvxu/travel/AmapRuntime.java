package cn.lvxu.travel;

import android.content.Context;
import com.amap.api.maps.MapsInitializer;
import com.amap.api.services.core.ServiceSettings;

/** Keeps Android SDK credentials separate from optional Web service credentials. */
final class AmapRuntime {
    private static String initializedKey;
    private AmapRuntime() {}

    static String key(Context context) {
        return new ApiConfig(context).amapAndroidKey().trim();
    }

    static boolean configured(Context context) { return !key(context).isEmpty(); }

    static synchronized boolean needsRestart(Context context) {
        return initializedKey != null && !initializedKey.equals(key(context));
    }

    /** Call only after consent, immediately before constructing an AMap SDK object. */
    static synchronized boolean prepare(Context context) {
        String candidate = key(context);
        if (candidate.isEmpty() || (initializedKey != null && !initializedKey.equals(candidate))) return false;
        if (!AmapConsent.granted(context)) return false;
        try {
            AmapConsent.apply(context);
            MapsInitializer.setApiKey(candidate);
            ServiceSettings.getInstance().setApiKey(candidate);
            initializedKey = candidate;
            return true;
        } catch (RuntimeException | LinkageError error) {
            return false;
        }
    }
}
