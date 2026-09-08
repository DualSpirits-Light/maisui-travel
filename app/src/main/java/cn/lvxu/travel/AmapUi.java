package cn.lvxu.travel;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Build;
import android.os.Bundle;
import android.view.MotionEvent;
import android.widget.AdapterView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import com.amap.api.maps.AMap;
import com.amap.api.maps.CameraUpdateFactory;
import com.amap.api.maps.TextureMapView;
import com.amap.api.maps.model.BitmapDescriptorFactory;
import com.amap.api.maps.model.LatLng;
import com.amap.api.maps.model.LatLngBounds;
import com.amap.api.maps.model.MarkerOptions;
import com.amap.api.maps.model.PolylineOptions;

import java.util.ArrayList;
import java.util.Locale;

/** Native AMap itinerary view. Coordinates supplied to AMap are GCJ-02. */
final class AmapUi {
    private final MainActivity a;
    private TextureMapView mapView;
    private MapUi fallback;
    private boolean resumed,disposed;

    AmapUi(MainActivity activity) { a = activity; }

    void show(ArrayList<Trip.Stop> stops) {
        if (!AmapConsent.granted(a)) {
            a.body.addView(a.action("启用高德地图",true,()->AmapConsent.request(a,a::render)));
            a.space(a.body,12);
        }
        if (!supportsNativeSdk() || !AmapConsent.granted(a)) {
            fallback = new MapUi(a);
            fallback.show(stops);
            return;
        }
        int previousChildren=a.body.getChildCount();
        try {
            showNative(stops);
        } catch (Throwable error) {
            destroyNative();
            while(a.body.getChildCount()>previousChildren)a.body.removeViewAt(a.body.getChildCount()-1);
            fallback = new MapUi(a);
            fallback.show(stops);
            a.toast("原生地图暂不可用，已切换备用地图");
        }
    }

    private void showNative(ArrayList<Trip.Stop> stops) {
        ArrayList<Trip.Stop> known = new ArrayList<>();
        for (Trip.Stop stop : stops) if (stop.lat != null && stop.lon != null) known.add(stop);

        mapView = new TextureMapView(a);
        mapView.onCreate(null);
        mapView.setOnTouchListener((v, event) -> {
            v.getParent().requestDisallowInterceptTouchEvent(
                    event.getActionMasked() != MotionEvent.ACTION_UP &&
                    event.getActionMasked() != MotionEvent.ACTION_CANCEL);
            return false;
        });
        a.body.addView(mapView, new LinearLayout.LayoutParams(-1, a.dp(380)));
        AMap map = mapView.getMap();
        map.getUiSettings().setZoomControlsEnabled(true);
        map.getUiSettings().setCompassEnabled(true);
        map.setOnMapLongClickListener(point -> {
            Trip.Stop draft = new Trip.Stop();
            draft.name = "地图选点";
            draft.day = a.day;
            draft.lat = point.latitude;
            draft.lon = point.longitude;
            draft.coordinateSystem = "GCJ02";
            a.stopEditorDraft(draft);
        });

        ArrayList<LatLng> route = new ArrayList<>();
        LatLngBounds.Builder bounds = LatLngBounds.builder();
        for (int i = 0; i < known.size(); i++) {
            Trip.Stop stop = known.get(i);
            double[] coordinate = gcj(stop);
            LatLng point = new LatLng(coordinate[0], coordinate[1]);
            route.add(point);
            bounds.include(point);
            map.addMarker(new MarkerOptions().position(point).title((i + 1) + ". " + stop.name)
                    .snippet(stop.time + " · " + stop.mode).icon(numberedMarker(i + 1)));
        }
        if (route.size() > 1) {
            map.addPolyline(new PolylineOptions().addAll(route).width(a.dp(4)).color(MainActivity.GREEN));
            mapView.post(() -> {if(!disposed&&mapView!=null)map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), a.dp(42)));});
        } else if (route.size() == 1) {
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(route.get(0), 15f));
        } else {
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(30.25, 120.14), 11f));
        }

        a.space(a.body, 14);
        if (known.size() > 1) addDistanceCard(known);
        a.body.addView(a.text("长按地图可添加地点。连线与测距均为两点间直线，不代表步行或驾车里程。地图服务由高德地图提供。", 12, MainActivity.MUTED));
        if (known.size() < stops.size()) {
            a.space(a.body, 8);
            a.body.addView(a.text("未定位的地点可在“编辑地点”中填写经纬度，或导入含位置的高德分享链接。", 12, MainActivity.MUTED));
        }
    }

    private void addDistanceCard(ArrayList<Trip.Stop> known) {
        LinearLayout card = a.card(a.body);
        card.addView(a.bold("两点测距", 18, MainActivity.INK));
        a.space(card, 10);
        String[] names = new String[known.size()];
        for (int i = 0; i < names.length; i++) names[i] = (i + 1) + ". " + known.get(i).name;
        Spinner from = a.select(card, "起点", names, names[0]);
        Spinner to = a.select(card, "终点", names, names[1]);
        TextView result = a.bold("", 24, MainActivity.GREEN);
        card.addView(result);
        AdapterView.OnItemSelectedListener listener = new AdapterView.OnItemSelectedListener() {
            public void onNothingSelected(AdapterView<?> parent) {}
            public void onItemSelected(AdapterView<?> parent, android.view.View view, int position, long id) {
                Trip.Stop first = known.get(from.getSelectedItemPosition());
                Trip.Stop second = known.get(to.getSelectedItemPosition());
                double[] p = GeoMath.wgs(first.lat, first.lon, first.coordinateSystem);
                double[] q = GeoMath.wgs(second.lat, second.lon, second.coordinateSystem);
                result.setText("直线约 " + GeoMath.distance(GeoMath.meters(p[0], p[1], q[0], q[1])));
            }
        };
        from.setOnItemSelectedListener(listener);
        to.setOnItemSelectedListener(listener);
    }

    private com.amap.api.maps.model.BitmapDescriptor numberedMarker(int number) {
        int size = a.dp(38);
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.WHITE);
        canvas.drawCircle(size / 2f, size / 2f, size * .48f, paint);
        paint.setColor(MainActivity.GREEN);
        canvas.drawCircle(size / 2f, size / 2f, size * .40f, paint);
        paint.setColor(Color.WHITE);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        paint.setTextSize(a.dp(number < 100 ? 15 : 12));
        Paint.FontMetrics metrics = paint.getFontMetrics();
        canvas.drawText(String.format(Locale.ROOT, "%d", number), size / 2f,
                size / 2f - (metrics.ascent + metrics.descent) / 2f, paint);
        return BitmapDescriptorFactory.fromBitmap(bitmap);
    }

    private static double[] gcj(Trip.Stop stop) {
        if ("GCJ02".equals(stop.coordinateSystem)) return new double[]{stop.lat, stop.lon};
        double[] wgs = GeoMath.wgs(stop.lat, stop.lon, stop.coordinateSystem);
        if (wgs[1] < 72.004 || wgs[1] > 137.8347 || wgs[0] < .8293 || wgs[0] > 55.8271)
            return wgs;
        return GeoMath.gcj(wgs[0], wgs[1]);
    }

    private static boolean supportsNativeSdk() {
        for (String abi : Build.SUPPORTED_ABIS)
            if ("arm64-v8a".equals(abi) || "armeabi-v7a".equals(abi)) return true;
        return false;
    }

    void resume() { if (mapView != null&&!resumed) {mapView.onResume();resumed=true;} }
    void pause() { if (mapView != null&&resumed) {mapView.onPause();resumed=false;} }
    void saveState(Bundle state) { if (mapView != null) mapView.onSaveInstanceState(state); }
    void destroy() {
        disposed=true;
        destroyNative();
        if (fallback != null) { fallback.destroy(); fallback = null; }
    }
    private void destroyNative() {
        if (mapView != null) { try{pause();mapView.onDestroy();}catch(RuntimeException ignored){}mapView = null; }
    }
}
