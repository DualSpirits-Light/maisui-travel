package cn.lvxu.travel;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;

/** Existing offline entitlements survive removal of the old code entry point. */
final class LegacyLicenseMigrationTest {
    static int run(Context context) {
        Context isolated = new ContextWrapper(context) {
            @Override public Context getApplicationContext() { return this; }
            @Override public SharedPreferences getSharedPreferences(String name, int mode) {
                return super.getSharedPreferences("stage8-license-" + name, mode);
            }
        };
        SharedPreferences stored = isolated.getSharedPreferences("app-prefs-v2", Context.MODE_PRIVATE);
        try {
            stored.edit().clear().putBoolean("paid", true)
                    .putString("activationSubject", "既有用户")
                    .putString("activationExpires", "2999-12-31").commit();
            AppPrefs valid = new AppPrefs(isolated);
            if (!valid.paid() || !"既有用户".equals(valid.activationSubject()))
                throw new AssertionError("valid historical entitlement must survive upgrade");
            stored.edit().putString("activationExpires", "2000-01-01").commit();
            if (new AppPrefs(isolated).paid())
                throw new AssertionError("expired historical entitlement must not remain active");
            stored.edit().putBoolean("paid", true).putString("activationExpires", "invalid").commit();
            if (new AppPrefs(isolated).paid())
                throw new AssertionError("invalid historical expiry must not remain active");
            return 3;
        } finally {
            stored.edit().clear().commit();
            isolated.getSharedPreferences("cloud-license-v1", Context.MODE_PRIVATE).edit().clear().commit();
            isolated.getSharedPreferences("tag-registry-v1", Context.MODE_PRIVATE).edit().clear().commit();
        }
    }
}
