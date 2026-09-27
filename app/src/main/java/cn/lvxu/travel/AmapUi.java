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
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import com.amap.api.maps.model.Polyline;

/** Native AMap itinerary view. Coordinates supplied to AMap are GCJ-02. */
final class AmapUi {
    private final MainActivity a;
    private TextureMapView mapView;
    private boolean resumed,disposed;
    private AmapRouteSession routeSession;
    private int routeGeneration;
    private final Map<Integer,List<Polyline>> routeLines=new HashMap<>();
    private final Map<Integer,TextView> routeRows=new HashMap<>();
    private final java.util.Set<Integer> failedRoutes=new java.util.HashSet<>();
    private List<AmapRoadRoutes.Segment> routeSegments;
    private int nextRoute;
    private final AmapRoadRoutes.RetryCursor retryCursor=new AmapRoadRoutes.RetryCursor();
    private android.view.View moreRoutes,retryRoutes;
    private boolean routeBusy;
    private final Bundle restoredState;

    AmapUi(MainActivity activity) { this(activity,null); }
    AmapUi(MainActivity activity,Bundle state) { a = activity; restoredState=state; }

    void show(ArrayList<Trip.Stop> stops) {
        if (!AmapRuntime.configured(a)) {
            a.body.addView(a.text("使用高德原生地图前，请配置您自己的 Android SDK Key。", 14, MainActivity.MUTED));
            a.body.addView(a.action("配置高德 Android Key", true,
                    () -> new AdvancedSettingsUi(a).androidKey(a::render)));
            a.body.post(() -> { if (!disposed && !a.isFinishing())
                new AdvancedSettingsUi(a).androidKey(a::render); });
            return;
        }
        if (AmapRuntime.needsRestart(a)) {
            a.body.addView(a.text("高德 Android Key 已更改。请关闭并重新打开应用后使用地图。",14,MainActivity.ORANGE));
            return;
        }
        if (!AmapConsent.granted(a)) {
            a.body.addView(a.action("启用高德地图",true,()->AmapConsent.request(a,a::render)));
            a.space(a.body,12);
        }
        if (!supportsNativeSdk()) {
            a.body.addView(a.text("当前设备架构不支持内置高德原生地图。请在 ARM 设备上使用地图导览。",14,MainActivity.ORANGE));
            return;
        }
        if (!AmapConsent.granted(a)) {
            a.body.addView(a.text("同意高德地图隐私说明后，才能显示地图导览。",14,MainActivity.MUTED));
            return;
        }
        if (!AmapRuntime.prepare(a)) {
            a.body.addView(a.text("高德地图暂不可用，请检查 Android Key 或重启应用。",14,MainActivity.ORANGE));
            return;
        }
        int previousChildren=a.body.getChildCount();
        try {
            showNative(stops);
        } catch (Throwable error) {
            destroyNative();
            while(a.body.getChildCount()>previousChildren)a.body.removeViewAt(a.body.getChildCount()-1);
            a.body.addView(a.text("高德原生地图暂不可用，请检查设备网络或 Android Key。",14,MainActivity.ORANGE));
        }
    }

    private void showNative(ArrayList<Trip.Stop> stops) {
        ArrayList<Trip.Stop> known = new ArrayList<>();
        for (Trip.Stop stop : stops) if (AmapRoadRoutes.coordinate(stop) != null) known.add(stop);

        mapView = new TextureMapView(a);
        mapView.onCreate(restoredState);
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
        for (int i = 0; i < stops.size(); i++) {
            Trip.Stop stop = stops.get(i);
            if(AmapRoadRoutes.coordinate(stop)==null)continue;
            double[] coordinate = gcj(stop);
            LatLng point = new LatLng(coordinate[0], coordinate[1]);
            route.add(point);
            bounds.include(point);
            map.addMarker(new MarkerOptions().position(point).title((i + 1) + ". " + stop.name)
                    .snippet(stop.time + " · " + stop.mode).icon(numberedMarker(i + 1)));
        }
        if (route.size() > 1) {
            mapView.post(() -> {if(!disposed&&mapView!=null)try{map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), a.dp(42)));}catch(RuntimeException ignored){}});
        } else if (route.size() == 1) {
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(route.get(0), 15f));
        } else {
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(30.25, 120.14), 11f));
        }

        a.space(a.body, 14);
        addRoutes(map, stops);
        if (known.size() > 1) addDistanceCard(known);
        a.body.addView(a.text("长按地图可添加地点。道路路线由高德 Web 服务提供；两点测距仍为直线距离。路线仅供参考，请核实实际交通情况。", 12, MainActivity.MUTED));
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

    private static double[] gcj(Trip.Stop stop) { return AmapRoadRoutes.coordinate(stop); }

    private void addRoutes(AMap map,ArrayList<Trip.Stop> stops) {
        routeSegments=AmapRoadRoutes.snapshot(stops);
        if(routeSegments.isEmpty()){a.body.addView(a.text("添加至少两个地点后可查询道路路线。",14,MainActivity.MUTED));return;}
        LinearLayout card=a.card(a.body);
        card.addView(a.bold("当日道路路线",18,MainActivity.INK));
        card.addView(a.text("按地点顺序逐段查询，使用终点的抵达方式。公交方案含步行；地铁采用公共交通推荐方案，可能含公交换乘。",12,MainActivity.MUTED));
        String key=new ApiConfig(a).mapKey("amap");
        boolean queryable=false;for(AmapRoadRoutes.Segment segment:routeSegments)if(segment.unavailable.isEmpty())queryable=true;
        if(!queryable){for(AmapRoadRoutes.Segment segment:routeSegments)card.addView(a.text(routeLabel(segment)+"\n"+segment.unavailable,14,MainActivity.MUTED));return;}
        if(key.isEmpty()){
            card.addView(a.text("道路路线需要高德 Web 服务 Key，与显示底图的 Android SDK Key 不同。配置后将返回当前行程。",14,MainActivity.MUTED));
            card.addView(a.action("配置高德 Web 服务 Key",true,()->new AdvancedSettingsUi(a).mapKey("amap",a::render)));
            card.post(()->{if(alive())new AdvancedSettingsUi(a).mapKey("amap",a::render);});
            return;
        }
        for(AmapRoadRoutes.Segment segment:routeSegments){
            TextView row=a.text(routeLabel(segment)+"\n"+(segment.unavailable.isEmpty()?"等待查询":segment.unavailable),14,MainActivity.MUTED);
            routeRows.put(segment.index,row);card.addView(row);a.space(card,8);
        }
        if(routeSegments.size()>AmapRoadRoutes.MAX_SEGMENTS)card.addView(a.text("每批最多查询 20 段；完成后可继续加载后续路线。未查询或失败的路段不会画直线替代。",12,MainActivity.MUTED));
        retryRoutes=a.action("重试失败路段",false,()->loadRoutes(map,key,true));card.addView(retryRoutes);
        moreRoutes=a.action("加载后续路线",false,()->loadRoutes(map,key,false));card.addView(moreRoutes);
        loadRoutes(map,key,false);
    }
    private String routeLabel(AmapRoadRoutes.Segment segment){return (segment.index+1)+" → "+(segment.index+2)+" · "+segment.fromName+" → "+segment.toName+" · "+segment.label;}
    private boolean alive(){return !disposed&&!a.isFinishing()&&!a.isDestroyed()&&mapView!=null;}
    private void routeButtons(){
        if(moreRoutes!=null){moreRoutes.setVisibility(nextRoute<routeSegments.size()?android.view.View.VISIBLE:android.view.View.GONE);moreRoutes.setEnabled(!routeBusy);}
        if(retryRoutes!=null){retryRoutes.setVisibility(failedRoutes.isEmpty()?android.view.View.GONE:android.view.View.VISIBLE);retryRoutes.setEnabled(!routeBusy);}
    }
    private void loadRoutes(AMap map,String key,boolean retry){
        if(!alive()||routeBusy)return;
        if(!key.equals(new ApiConfig(a).mapKey("amap"))){a.render();return;}
        List<AmapRoadRoutes.Segment> batch=new ArrayList<>();
        if(retry){batch.addAll(retryCursor.batch(routeSegments,failedRoutes));}
        else {int end=Math.min(routeSegments.size(),nextRoute+AmapRoadRoutes.MAX_SEGMENTS);batch.addAll(routeSegments.subList(nextRoute,end));nextRoute=end;}
        if(batch.isEmpty())return;
        if(routeSession!=null)routeSession.cancel();
        final int generation=++routeGeneration;
        final int[] remaining={batch.size()};
        routeBusy=true;routeButtons();
        for(AmapRoadRoutes.Segment s:batch)if(s.unavailable.isEmpty())routeRows.get(s.index).setText(routeLabel(s)+"\n正在查询高德道路路线…");
        routeSession=new AmapRouteSession(key);
        routeSession.start(batch,(segment,result,error)->a.runOnUiThread(()->{
            if(!alive()||generation!=routeGeneration)return;
            if(!key.equals(new ApiConfig(a).mapKey("amap"))){routeSession.cancel();a.render();return;}
            TextView row=routeRows.get(segment.index);
            List<Polyline> drawn=new ArrayList<>();
            try {
            if(result!=null){
                failedRoutes.remove(segment.index);
                List<Polyline> previous=routeLines.remove(segment.index);if(previous!=null)for(Polyline line:previous)line.remove();

                for(List<AmapRoadRoutes.Point> line:result.lines){
                    List<LatLng> points=new ArrayList<>();for(AmapRoadRoutes.Point point:line)points.add(new LatLng(point.lat,point.lon));
                    drawn.add(map.addPolyline(new PolylineOptions().addAll(points).width(a.dp(4)).color(MainActivity.GREEN)));
                }
                routeLines.put(segment.index,drawn);
                row.setText(routeLabel(segment)+"\n"+GeoMath.distance(result.meters)+" / 约 "+Math.max(1,Math.round(result.seconds/60))+" 分钟"+(result.partial?" · 仅显示部分路线（缺少道路坐标的路段未绘制）":""));
            }else {row.setText(routeLabel(segment)+"\n"+error);if(segment.unavailable.isEmpty())failedRoutes.add(segment.index);}
            }catch(RuntimeException drawError){
                for(Polyline line:drawn)try{line.remove();}catch(RuntimeException ignored){}
                failedRoutes.add(segment.index);row.setText(routeLabel(segment)+"\n地图绘制失败，请重试此路段");
            }finally{if(--remaining[0]==0){routeBusy=false;routeButtons();}}
        }));
    }

    private static boolean supportsNativeSdk() {
        for (String abi : Build.SUPPORTED_ABIS)
            if ("arm64-v8a".equals(abi) || "armeabi-v7a".equals(abi)) return true;
        return false;
    }

    void resume() { if (mapView != null&&!resumed) {mapView.onResume();resumed=true;} }
    void pause() { if (mapView != null&&resumed) {mapView.onPause();resumed=false;} }
    void saveState(Bundle state) { if (mapView != null) {Bundle saved=new Bundle();mapView.onSaveInstanceState(saved);state.putBundle("amap-state",saved);} }
    void destroy() {
        disposed=true;
        ++routeGeneration;
        if(routeSession!=null)routeSession.cancel();
        destroyNative();
    }
    private void destroyNative() {
        ++routeGeneration;if(routeSession!=null)routeSession.cancel();
        if (mapView != null) { try{pause();mapView.onDestroy();}catch(RuntimeException ignored){}mapView = null; }
    }
}
