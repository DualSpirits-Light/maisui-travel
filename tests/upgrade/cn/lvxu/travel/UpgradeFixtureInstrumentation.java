package cn.lvxu.travel;

import android.app.Instrumentation;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;

/** Seeds data with the 0.4.0 classes, then reads it through the installed 0.5.0 classes. */
public final class UpgradeFixtureInstrumentation extends Instrumentation {
    private static final String FIXTURE_PREFS = "upgrade-fixture-v1";
    private static final String APP_PREFS = "app-prefs-v2";
    private static final String TRIP_ID = "upgrade-fixture-trip";
    private static final String STOP_ID = "upgrade-fixture-stop";
    private static final String TAG_NAME = "升级验收标签";
    private static final String SUBJECT = "升级验收用户";
    private static final String EXPIRY = "2999-12-31";
    private static final String TITLE = "升级验收旅行";
    private static final String STOP_NAME = "升级验收地点";
    private static final String SLOGAN = "升级验收设置";

    private Bundle arguments;
    private int assertions;

    @Override public void onCreate(Bundle args) {
        super.onCreate(args);
        arguments = args;
        start();
    }

    @Override public void onStart() {
        String phase = arguments == null ? "" : arguments.getString("phase", "");
        Bundle result = new Bundle();
        try {
            Context target = getTargetContext();
            if ("seed".equals(phase)) seed(target);
            else if ("verify".equals(phase)) verify(target);
            else throw new AssertionError("unknown phase");
            result.putString("stream", "PASS upgrade " + phase + ": " + assertions + " assertions\n");
            finish(-1, result);
        } catch (Throwable failure) {
            // Only a synthetic assertion label is shown; never expose stored user data.
            String reason = failure instanceof AssertionError ? failure.getMessage() : failure.getClass().getSimpleName();
            result.putString("stream", "FAIL upgrade " + phase + ": " + reason + "\n");
            finish(0, result);
        }
    }

    private void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
        assertions++;
    }

    private void seed(Context context) throws Exception {
        SharedPreferences marker = context.getSharedPreferences(FIXTURE_PREFS, Context.MODE_PRIVATE);
        check(!marker.contains("ready"), "fixture already seeded");
        try (TripStore store = new TripStore(context)) {
            check(!store.exists() || store.read().isEmpty(), "emulator app data is not empty");

            TagRepository tags = new TagRepository(context);
            TagRepository.Tag tag = tags.create(TAG_NAME, 0xff336699);
            Trip trip = new Trip();
            trip.id = TRIP_ID;
            trip.title = TITLE;
            trip.city = "杭州";
            trip.start = "2026-10-01";
            trip.days = 2;
            trip.budget = 123456;
            trip.normalize();
            trip.tagIds.add(tag.id);
            trip.tagNames.put(tag.id, tag.name);

            Trip.Stop stop = new Trip.Stop();
            stop.id = STOP_ID;
            stop.name = STOP_NAME;
            stop.day = 0;
            stop.time = "09:30";
            stop.duration = 75;
            stop.address = "升级验收地址";
            stop.note = "升级验收备注";
            stop.tagIds.add(tag.id);
            stop.tagNames.put(tag.id, tag.name);

            MediaFiles media = new MediaFiles(context);
            String path = media.newPath("photos", ".png");
            File photo = media.file(path);
            Bitmap bitmap = Bitmap.createBitmap(4, 3, Bitmap.Config.ARGB_8888);
            try {
                bitmap.eraseColor(0xff2277bb);
                bitmap.setPixel(1, 1, 0xffffcc00);
                try (FileOutputStream output = new FileOutputStream(photo)) {
                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output), "PNG creation");
                }
            } finally {
                bitmap.recycle();
            }
            stop.previewPhoto = path;
            stop.notePhotos.add(path);
            trip.stops.add(stop);

            Trip.Checkin checkin = new Trip.Checkin();
            checkin.place = STOP_NAME;
            checkin.time = "2026-10-01T11:00";
            checkin.photo = path;
            checkin.groupPhotos.add(path);
            trip.checkins.add(checkin);
            store.save(Collections.singletonList(trip));

            AppPrefs prefs = new AppPrefs(context);
            prefs.setTheme("dark");
            prefs.setSlogan(SLOGAN);
            prefs.setDefaultHome("checkin");
            prefs.setShowPlaceCoordinates(true);
            check(context.getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE).edit()
                    .putBoolean("paid", true)
                    .putString("activationSubject", SUBJECT)
                    .putString("activationExpires", EXPIRY).commit(), "legacy entitlement write");

            check(marker.edit().putString("tripId", TRIP_ID).putString("tagId", tag.id)
                    .putString("photo", path).putString("photoSha256", sha256(photo))
                    .putBoolean("ready", true).commit(), "fixture marker write");
            check(store.read().size() == 1, "old trip roundtrip");
            check(new AppPrefs(context).paid(), "old offline entitlement");
            check(photo.isFile() && photo.length() > 40, "old PNG persisted");
        }
    }

    private void verify(Context context) throws Exception {
        SharedPreferences marker = context.getSharedPreferences(FIXTURE_PREFS, Context.MODE_PRIVATE);
        check(marker.getBoolean("ready", false), "seed marker missing");
        try (TripStore store = new TripStore(context)) {
            ArrayList<Trip> trips = store.read();
            check(trips.size() == 1, "trip count changed");
            Trip trip = trips.get(0);
            check(marker.getString("tripId", "").equals(trip.id), "trip identity changed");
            check(TITLE.equals(trip.title) && "杭州".equals(trip.city) && "2026-10-01".equals(trip.start), "trip text changed");
            check(trip.days == 2 && trip.budget == 123456, "trip fields changed");
            check(trip.stops.size() == 1, "place count changed");
            Trip.Stop stop = trip.stops.get(0);
            check(STOP_ID.equals(stop.id) && STOP_NAME.equals(stop.name), "place identity changed");
            check("09:30".equals(stop.time) && stop.duration == 75 && "升级验收备注".equals(stop.note), "place fields changed");

            String tagId = marker.getString("tagId", "");
            TagRepository.Tag tag = new TagRepository(context).find(tagId);
            check(tag != null && TAG_NAME.equals(tag.name), "tag registry changed");
            check(trip.tagIds.contains(tagId) && TAG_NAME.equals(trip.tagNames.get(tagId)), "trip tag changed");
            check(stop.tagIds.contains(tagId) && TAG_NAME.equals(stop.tagNames.get(tagId)), "place tag changed");

            String path = marker.getString("photo", "");
            check(path.equals(stop.previewPhoto) && stop.notePhotos.contains(path), "place photo references changed");
            check(trip.checkins.size() == 1 && STOP_NAME.equals(trip.checkins.get(0).place), "check-in changed");
            check(path.equals(trip.checkins.get(0).photo) && trip.checkins.get(0).groupPhotos.contains(path), "check-in photo changed");
            File photo = MediaFiles.file(context, path);
            check(photo != null && photo.isFile() && marker.getString("photoSha256", "").equals(sha256(photo)), "PNG bytes changed");
            Bitmap decoded = BitmapFactory.decodeFile(photo.getAbsolutePath());
            try {
                check(decoded != null && decoded.getWidth() == 4 && decoded.getHeight() == 3, "PNG cannot decode");
            } finally {
                if (decoded != null) decoded.recycle();
            }

            AppPrefs prefs = new AppPrefs(context);
            check("dark".equals(prefs.theme()) && SLOGAN.equals(prefs.slogan()), "appearance settings changed");
            check("checkin".equals(prefs.defaultHome()) && prefs.showPlaceCoordinates(), "navigation settings changed");
            check(prefs.paid() && SUBJECT.equals(prefs.activationSubject()) && EXPIRY.equals(prefs.activationExpires()), "historical offline entitlement changed");
        }
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] bytes = new byte[8192];
            int length;
            while ((length = input.read(bytes)) != -1) digest.update(bytes, 0, length);
        }
        StringBuilder value = new StringBuilder(64);
        for (byte b : digest.digest()) value.append(String.format("%02x", b & 0xff));
        return value.toString();
    }
}
