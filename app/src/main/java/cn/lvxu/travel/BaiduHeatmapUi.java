package cn.lvxu.travel;

import android.Manifest;
import android.content.Context;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableString;
import android.text.method.LinkMovementMethod;
import android.text.style.URLSpan;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.*;
import android.view.*;
import android.graphics.Bitmap;
import android.graphics.Point;
import java.util.*;
import java.util.concurrent.*;
import com.baidu.mapapi.map.BaiduMap;
import com.baidu.mapapi.SDKInitializer;
import com.baidu.mapapi.map.MapStatus;
import com.baidu.mapapi.map.MapStatusUpdateFactory;
import com.baidu.mapapi.map.MyLocationData;
import com.baidu.mapapi.map.TextureMapView;
import com.baidu.mapapi.model.LatLng;
import com.baidu.mapapi.utils.CoordinateConverter;

/** Shows only Baidu's own crowd layer; no inferred counts or manufactured scores. */
final class BaiduHeatmapUi {
    static final int LOCATION_PERMISSION_REQUEST=716;
    private final MainActivity a;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private TextureMapView view;
    private BaiduHeatSampler sampler;
    private RegionPickerUi regionPicker;
    private RegionSelection selectedRegion;
    private boolean manualRegion,fullRefreshPending,regionLocating,resetRegionPicker;private int locationGeneration;
    private final NearbyDiscoveryCache discovery;
    private long refreshedAt,lastSampleAt,heatRevision,lastInteractionAt,nextAutoAt;private boolean cycleActive,refreshQueryFailed;private String refreshArea="";private final Map<String,LinearLayout> heatRows=new HashMap<>();private int activeRefreshTicket;private boolean refreshingPlaces;private final ArrayList<NearbyPlace> refreshPlaces=new ArrayList<>();
    private boolean mapLoaded,cameraPending;
    private double[] origin,resolvingLocation;
    private String city="",category="人文",sort="距离";
    private LinearLayout categoryTabs,placesArea;
    private TextView nearbyTitle,nearbyStatus;
    private MapGestureFrame mapFrame;
    private final ArrayList<NearbyPlace> places=new ArrayList<>();
    private final Map<String,ArrayList<NearbyPlace>> cachedPlaces=new HashMap<>();
    private final NearbyFavorites favorites;
    private final ExecutorService queries=Executors.newSingleThreadExecutor();
    private int requestGeneration,captureGeneration;
    private final Runnable analyze=this::captureHeat;
    private final Runnable expire=new Runnable(){public void run(){if(disposed||!resumed)return;patchHeatRows();refreshIfDue();handler.postDelayed(this,30_000);}};
    private PageUi page;
    private LinearLayout body;
    private String diagnosticFailure="";
    private BaiduMap map;
    private TextView status;
    private boolean disposed,resumed,locating,receiverRegistered,authFailed,pendingLocate;
    private LocationManager locations;
    private LocationSession session;
    private final Runnable timeout=()->{boolean fixed=session!=null&&session.best()!=null;stopLocation();if(!disposed&&!fixed)status.setText("定位等待已结束，可以重试或拖动地图查看城市热力。");};
    private final BroadcastReceiver sdkStatus=new BroadcastReceiver(){
        public void onReceive(Context context,Intent intent){
            if(disposed||status==null)return;
            String action=intent.getAction();
            if(SDKInitializer.SDK_BROADTCAST_ACTION_STRING_PERMISSION_CHECK_ERROR.equals(action)){
                authFailed=true;
                status.setText("百度 Android AK 鉴权失败，请核对 AK 类型、包名和安装证书 SHA1。");
            }else if(SDKInitializer.SDK_BROADCAST_ACTION_STRING_NETWORK_ERROR.equals(action)){
                status.setText("百度地图网络连接失败，请检查网络。空白不代表人少。");
            }else if(SDKInitializer.SDK_BROADTCAST_ACTION_STRING_PERMISSION_CHECK_OK.equals(action)){
                authFailed=false;status.setText("百度地图鉴权通过，城市热力已开启；覆盖与更新时间由百度提供。");
            }
        }
    };
    private final LocationListener listener=new LocationListener() {
        @Override public void onLocationChanged(Location location){offer(location,false);}
        @Override public void onProviderEnabled(String provider){}
        @Override public void onProviderDisabled(String provider){}
        @Override public void onStatusChanged(String provider,int state,Bundle extras){}
    };

    BaiduHeatmapUi(MainActivity activity){a=activity;favorites=new NearbyFavorites(a);discovery=new NearbyDiscoveryCache(a);}
    void show() {
        page=new PageUi(a,"附近城市热力");body=page.body;
        page.dialog.setOnDismissListener(d->destroy());
        page.show();renderContent();
    }
    private void renderContent() {
        if(disposed||!page.alive())return;
        releaseMap();body.removeAllViews();if(regionPicker!=null)regionPicker.close();regionPicker=new RegionPickerUi(a,this::selectRegion);body.addView(regionPicker.view);
        origin=NearbyWarmup.lastFix();if(city.isEmpty())city=discovery.latestCity();refreshedAt=discovery.lastCompleteRefresh(scope());
        body.addView(a.text("拖动查看城市热力，点击下方地点查看周边。空白或未加载时，热力显示未知。",13,MainActivity.MUTED));
        if(!BaiduHeatmapRuntime.configured(a)) {
            body.addView(a.action("配置百度 Android AK",true,()->new AdvancedSettingsUi(a).baiduAndroidKey(this::renderContent)));
            return;
        }
        if(BaiduHeatmapRuntime.needsRestart(a)) {
            body.addView(a.text("百度 Android AK 已更改，请重新打开应用后查看热力图。",14,MainActivity.ORANGE));return;
        }
        if(!BaiduHeatmapRuntime.consent(a)) {
            body.addView(a.action("启用百度城市热力",true,this::requestConsent));return;
        }
        int children=body.getChildCount();
        try {
            status=a.text("拖动地图查看城市热力，或定位到我的位置。",13,MainActivity.MUTED);
            body.addView(status);
            IntentFilter filter=new IntentFilter();
            filter.addAction(SDKInitializer.SDK_BROADTCAST_ACTION_STRING_PERMISSION_CHECK_ERROR);
            filter.addAction(SDKInitializer.SDK_BROADTCAST_ACTION_STRING_PERMISSION_CHECK_OK);
            filter.addAction(SDKInitializer.SDK_BROADCAST_ACTION_STRING_NETWORK_ERROR);
            if(android.os.Build.VERSION.SDK_INT>=33)a.registerReceiver(sdkStatus,filter,Context.RECEIVER_NOT_EXPORTED);else a.registerReceiver(sdkStatus,filter);
            receiverRegistered=true;
            if(!BaiduHeatmapRuntime.prepare(a))throw new IllegalStateException("SDK unavailable");
            view=new TextureMapView(a);
            MapGestureFrame touch=new MapGestureFrame(a);
            mapFrame=touch;touch.onGestureStart(this::browsing);
            touch.addView(view,new android.widget.FrameLayout.LayoutParams(-1,-1));
            body.addView(touch,new LinearLayout.LayoutParams(-1,a.dp(320)));
            map=view.getMap();
            sampler=new BaiduHeatSampler(a,map,view,touch,updated->{if(heatRevision!=new ApiConfig(a).revision()){cancelCapture();invalidateHeat();return;}discovery.putHeatBatch(scope(),updated);for(NearbyPlace fresh:updated)for(NearbyPlace p:places)if(p.id.equals(fresh.id)){p.heat=fresh.heat;p.heatAt=fresh.heatAt;}patchHeatRows();},message->{if(!disposed&&status!=null)status.setText(message);});sampler.onFinished(this::refreshCompleted);
            map.setBaiduHeatMapEnabled(true);
            map.setMyLocationEnabled(true);
            map.getUiSettings().setRotateGesturesEnabled(false);map.getUiSettings().setOverlookingGesturesEnabled(false);
            map.setOnMapLoadedCallback(()->handler.post(()->{if(disposed)return;mapLoaded=true;scheduleHeat();}));
            map.setOnMapStatusChangeListener(new BaiduMap.OnMapStatusChangeListener(){
                public void onMapStatusChangeStart(MapStatus s){}
                public void onMapStatusChangeStart(MapStatus s,int reason){if(reason==1)handler.post(BaiduHeatmapUi.this::browsing);}
                public void onMapStatusChange(MapStatus s){}
                public void onMapStatusChangeFinish(MapStatus s){handler.post(()->{if(disposed||sampler!=null&&sampler.busy())return;if(!locating&&!authFailed)status.setText(s.zoom<11||s.zoom>21?"请放大地图查看城市热力。":"当前视野已保留，可以继续拖动查看城市热力。");});}
            });
            ArrayList<NearbyPlace> initialPlaces=discovery.getPlaces(city,category);LatLng cachedCenter=initialPlaces.isEmpty()?new LatLng(39.915,116.404):BaiduHeatSampler.point(initialPlaces.get(0));LatLng initial=origin==null?cachedCenter:new CoordinateConverter().from(CoordinateConverter.CoordType.GPS).coord(new LatLng(origin[0],origin[1])).convert();map.setMapStatus(MapStatusUpdateFactory.newLatLngZoom(initial,13));
            body.addView(a.action("定位到我的位置",true,this::locateExplicit));
            buildNearby();loadNearby();if(origin!=null)resolveLocation(origin.clone());
            resume();
            view.post(()->{if(!disposed&&page.alive())locate();});
        } catch(RuntimeException|LinkageError error) {
            diagnosticFailure=BaiduHeatmapRuntime.diagnostic("map-view",error)+"; "+BaiduHeatmapRuntime.initializationFailure();
            releaseMap();
            while(body.getChildCount()>children)body.removeViewAt(body.getChildCount()-1);
            body.addView(a.text("百度热力地图暂不可用，请检查设备架构、网络和 Android AK。",14,MainActivity.ORANGE));
        }
    }
    private void requestConsent() {
        String label="《百度地图开放平台隐私政策》";
        String copy="城市热力地图由百度地图提供。启用后 SDK 会处理设备、网络及相关位置信息，用于地图服务和安全保障。请阅读"+label+"后决定是否启用。定位按钮会另行请求系统位置权限。";
        SpannableString message=new SpannableString(copy);
        int start=copy.indexOf(label);
        message.setSpan(new URLSpan("https://lbsyun.baidu.com/index.php?title=openprivacy"),start,start+label.length(),0);
        android.app.AlertDialog dialog=new RoundedDialogs.Builder(a).setTitle("启用百度城市热力").setMessage(message)
            .setNegativeButton("暂不启用",null).setPositiveButton("同意并启用",(d,w)->{BaiduHeatmapRuntime.agree(a);renderContent();}).create();
        dialog.setOnShowListener(v->{TextView text=dialog.findViewById(android.R.id.message);if(text!=null)text.setMovementMethod(LinkMovementMethod.getInstance());});
        dialog.show();
    }
    private boolean permitted(){return a.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED||a.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED;}
    private void locateExplicit(){resetRegionPicker=true;manualRegion=false;selectedRegion=null;cachedPlaces.clear();cancelCapture();locate();if(origin!=null)resolveLocation(origin.clone());}
    private void locate() {
        if(disposed||map==null)return;
        if(!resumed){pendingLocate=true;return;}
        if(!permitted()){a.requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},LOCATION_PERMISSION_REQUEST);return;}
        pendingLocate=false;stopLocation();cameraPending=true;
        locations=(LocationManager)a.getSystemService(Context.LOCATION_SERVICE);
        session=new LocationSession(System.currentTimeMillis());
        boolean fine=a.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED;
        boolean gps=fine&&enabled(LocationManager.GPS_PROVIDER),network=enabled(LocationManager.NETWORK_PROVIDER);
        if(session.start(gps,network,false)==LocationSession.Started.NO_PROVIDER){status.setText("当前位置服务未开启，请开启系统定位后重试。仍可拖动地图查看热力。");return;}
        locating=true;status.setText("正在定位…");
        try {
            for(LocationSession.Provider p:session.providers()) {
                String provider=p==LocationSession.Provider.GPS?LocationManager.GPS_PROVIDER:LocationManager.NETWORK_PROVIDER;
                Location cached=locations.getLastKnownLocation(provider);
                if(cached!=null)offer(cached,true);
                if(!locating)break;
                locations.requestLocationUpdates(provider,1000,0,listener,Looper.getMainLooper());
            }
            if(locating)handler.postDelayed(timeout,20_000);
        } catch(SecurityException|IllegalArgumentException error){stopLocation();status.setText("暂时无法定位，请检查系统位置权限与定位服务。仍可拖动地图查看热力。");}
    }
    private boolean enabled(String provider){try{return locations!=null&&locations.isProviderEnabled(provider);}catch(RuntimeException error){return false;}}
    void onLocationPermissionResult(boolean granted){if(disposed||map==null)return;if(granted&&permitted())locate();else status.setText("未允许定位，可拖动地图查看城市热力。");}
    private void offer(Location location,boolean cached) {
        if(disposed||!locating||session==null||location==null||!location.hasAccuracy())return;
        LocationSession.Provider provider=LocationManager.GPS_PROVIDER.equals(location.getProvider())?LocationSession.Provider.GPS:LocationSession.Provider.NETWORK;
        LocationSession.Fix fix=new LocationSession.Fix(provider,location.getLatitude(),location.getLongitude(),location.getAccuracy(),location.getTime());
        LocationSession.Fix selected=cached?session.offerCached(java.util.Collections.singletonList(fix)):session.offerRealtime(fix);
        if(selected==null)return;
        try {
        LatLng point=new CoordinateConverter().from(CoordinateConverter.CoordType.GPS).coord(new LatLng(selected.latitude,selected.longitude)).convert();
        MyLocationData marker=new MyLocationData.Builder().latitude(point.latitude).longitude(point.longitude).accuracy(selected.accuracy).build();map.setMyLocationData(marker);
        if(cameraPending){cameraPending=false;map.animateMapStatus(MapStatusUpdateFactory.newLatLngZoom(point,15));}
        boolean first=origin==null;boolean changed=origin!=null&&GeoMath.meters(origin[0],origin[1],selected.latitude,selected.longitude)>2000;
        origin=new double[]{selected.latitude,selected.longitude};
        if(first||changed||resetRegionPicker){if(changed&&!manualRegion){city="";cachedPlaces.clear();}resolveLocation(origin.clone());if(!manualRegion)loadNearby();}else patchHeatRows();
        if(!authFailed)status.setText("已定位（精度约 "+Math.round(selected.accuracy)+" 米）。百度城市热力已开启，空白不代表人少。");if(selected.accuracy<=100)stopLocation();
        } catch(RuntimeException|LinkageError error){stopLocation();status.setText("位置转换或地图定位暂不可用，可以拖动地图查看城市热力。");}
    }
    private void stopLocation(){handler.removeCallbacks(timeout);locating=false;if(session!=null)session.cancel();if(locations!=null)try{locations.removeUpdates(listener);}catch(RuntimeException ignored){}session=null;}
    void resume(){if(disposed||view==null||resumed)return;view.onResume();resumed=true;if(pendingLocate)locate();scheduleHeat();handler.removeCallbacks(expire);handler.postDelayed(expire,30_000);}
    void pause(){pendingLocate=false;cameraPending=false;stopLocation();cancelCapture();handler.removeCallbacks(expire);if(view!=null&&resumed){view.onPause();resumed=false;}}
    private void releaseMap(){pause();requestGeneration++;locationGeneration++;regionLocating=false;refreshingPlaces=false;fullRefreshPending=false;refreshPlaces.clear();mapLoaded=false;if(sampler!=null){sampler.destroy();sampler=null;}if(receiverRegistered){try{a.unregisterReceiver(sdkStatus);}catch(RuntimeException ignored){}receiverRegistered=false;}if(view!=null){try{view.onDestroy();}catch(RuntimeException|LinkageError ignored){}view=null;}map=null;}
    void destroy(){if(disposed)return;releaseMap();disposed=true;if(regionPicker!=null)regionPicker.close();queries.shutdownNow();if(page!=null&&page.dialog.isShowing())page.dialog.dismiss();}

    private void browsing(){cameraPending=false;lastInteractionAt=System.currentTimeMillis();nextAutoAt=lastInteractionAt+60_000;cancelCapture();}
    private void invalidateHeat(){discovery.prune();for(NearbyPlace p:places)discovery.applyHeat(p);renderPlaces();}
    private void buildNearby(){
        nearbyTitle=a.bold("发现当地",20,MainActivity.INK);body.addView(nearbyTitle);a.space(body,8);
        HorizontalScrollView tabs=new HorizontalScrollView(a);tabs.setHorizontalScrollBarEnabled(false);categoryTabs=a.row();tabs.addView(categoryTabs);body.addView(tabs);renderCategories();
        LinearLayout controls=a.row();TextView sorting=a.text("排序："+sort+" ▾",14,MainActivity.GREEN);sorting.setPadding(a.dp(8),a.dp(10),a.dp(8),a.dp(10));sorting.setOnClickListener(v->new RoundedDialogs.Builder(a).setTitle("地点排序").setItems(new String[]{"距离","评分","拥挤程度"},(d,w)->{sort=new String[]{"距离","评分","拥挤程度"}[w];sorting.setText("排序："+sort+" ▾");renderPlaces();}).show());controls.addView(sorting,new LinearLayout.LayoutParams(0,-2,1));TextView refresh=a.text("刷新",14,MainActivity.GREEN);refresh.setPadding(a.dp(12),a.dp(10),a.dp(12),a.dp(10));refresh.setText("刷新全部热力");refresh.setOnClickListener(v->refreshAll());controls.addView(refresh);body.addView(controls);
        nearbyStatus=a.text("允许定位后，显示所在城市的地点；距离从手机当前位置计算。",12,MainActivity.MUTED);body.addView(nearbyStatus);
        placesArea=a.col();body.addView(placesArea);
        body.addView(a.text("小人表示当前地图颜色的热力参考：绿色较低、黄色中等、红色较高。不是现场人数或景区拥挤率；问号表示无可靠数据。评分由高德提供，距离为直线距离。",12,MainActivity.MUTED));
        renderPlaces();
    }
    private void renderCategories(){if(categoryTabs==null)return;categoryTabs.removeAllViews();for(String name:NearbyPlacesService.CATEGORIES){TextView tab=a.text(name,14,category.equals(name)?MainActivity.SURFACE:MainActivity.INK);tab.setGravity(Gravity.CENTER);tab.setMinHeight(a.dp(44));tab.setPadding(a.dp(14),a.dp(8),a.dp(14),a.dp(8));int bg=category.equals(name)?MainActivity.GREEN:MainActivity.SURFACE;tab.setTextColor(MainActivity.readable(category.equals(name)?0xffffffff:MainActivity.INK,bg));tab.setBackground(a.shape(bg,16));tab.setSelected(category.equals(name));tab.setOnClickListener(v->{cancelCapture();nextAutoAt=0;lastInteractionAt=0;category=name;requestGeneration++;renderCategories();loadNearby();});LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-2);lp.rightMargin=a.dp(8);categoryTabs.addView(tab,lp);}}
    private void loadNearby(){
        if(disposed||placesArea==null)return;
        requestGeneration++;
        if("收藏".equals(category)){places.clear();places.addAll(favorites.all());nearbyStatus.setText(origin==null?"收藏地点 · 定位后可显示距离":"收藏地点 · 距离从手机当前位置计算");renderPlaces();scheduleHeat();return;}
        if(origin==null&&selectedRegion==null&&city.isEmpty()){places.clear();nearbyStatus.setText("请先定位到我的位置，再查看所在城市的地点。");renderPlaces();return;}
        ArrayList<NearbyPlace> saved=discovery.getPlaces(scope(),category);if(!saved.isEmpty()){places.clear();places.addAll(saved);nearbyTitle.setText("发现 "+(selectedRegion==null?city:selectedRegion.label()));nearbyStatus.setText("已缓存地点 · "+category+" · "+places.size()+" 个地点");renderPlaces();scheduleHeat();return;}
        ApiConfig config=new ApiConfig(a);boolean sdk=AmapRuntime.configured(a);if(!sdk&&config.mapKey("amap").isEmpty()){
            places.clear();renderPlaces();nearbyStatus.setText("当地地点查询需要高德服务配置，百度热力地图可以继续使用。");placesArea.addView(a.action("配置高德地点查询",false,()->new RoundedDialogs.Builder(a).setTitle("高德地点查询配置").setItems(new String[]{"Android SDK Key","Web 服务 Key"},(d,w)->{if(w==0)new AdvancedSettingsUi(a).androidKey(this::loadNearby);else new AdvancedSettingsUi(a).mapKey("amap",this::loadNearby);}).show()));return;
        }
        if(sdk&&!AmapConsent.granted(a)){nearbyStatus.setText("查看当地地点前，请确认高德服务隐私说明。");AmapConsent.request(a,this::loadNearby,()->{if(!disposed)nearbyStatus.setText("未启用高德地点查询，仍可浏览百度热力。");});return;}
        if(sdk&&AmapRuntime.needsRestart(a)){nearbyStatus.setText("高德 Android Key 已更改，请重启应用后查询地点。");return;}
        final int generation=requestGeneration;final String requested=category,knownCity=city;final double[] position=origin==null?new double[]{0,0}:origin.clone();final RegionSelection requestedRegion=selectedRegion;final String requestedScope=scope();final long revision=config.revision();
        places.clear();renderPlaces();nearbyStatus.setText("正在查询当地"+requested+"…");
        queries.execute(()->{try{NearbyPlacesService.Result result=requestedRegion==null?NearbyPlacesService.search(a,requested,position,knownCity,sdk):NearbyPlacesService.searchRegion(a,requested,requestedRegion,sdk);handler.post(()->{if(disposed||generation!=requestGeneration||revision!=new ApiConfig(a).revision())return;if(requestedRegion==null)city=result.city;for(NearbyPlace p:result.places)discovery.applyHeat(p);discovery.putPlaces(requestedScope.isEmpty()?result.city:requestedScope,requested,result.places);cachedPlaces.put(requested,result.places);places.clear();places.addAll(result.places);nearbyTitle.setText("发现 "+(selectedRegion==null?city:selectedRegion.label()));nearbyStatus.setText(city+" · "+requested+" · "+places.size()+" 个地点");renderPlaces();scheduleHeat();});}
            catch(Exception|LinkageError error){handler.post(()->{if(disposed||generation!=requestGeneration)return;nearbyStatus.setText("当地地点查询失败，请检查高德 Key 权限与网络后刷新。");});}});
    }
    private void renderPlaces(){if(placesArea==null||disposed)return;placesArea.removeAllViews();heatRows.clear();
        if(places.isEmpty()){placesArea.addView(a.text("收藏".equals(category)?"还没有收藏地点，看到喜欢的地方可以添加收藏。":"暂无地点，请定位或刷新后查看。",14,MainActivity.MUTED));return;}
        for(NearbyPlace p:NearbyPlace.sorted(places,sort,origin)){
            LinearLayout card=a.card(placesArea);TextView title=a.bold(p.name,18,MainActivity.INK);title.setOnClickListener(v->focus(p));card.setOnClickListener(v->focus(p));card.addView(title);
            card.addView(a.text(p.address,12,MainActivity.MUTED));String distance=origin==null?"距离未知":GeoMath.distance(p.distance(origin))+" · 直线";card.addView(a.text((p.rating==null?"暂无评分":"★ "+String.format(java.util.Locale.ROOT,"%.1f",p.rating))+"   ·   "+distance,13,MainActivity.MUTED));
            LinearLayout heat=a.row();heatRows.put(p.id,heat);updateHeatRow(heat,p);card.addView(heat);
            LinearLayout actions=a.row();TextView nav=a.text("导航到这里",14,MainActivity.GREEN);nav.setPadding(a.dp(4),a.dp(12),a.dp(4),a.dp(12));nav.setOnClickListener(v->NearbyNavigation.open(a,p));actions.addView(nav,new LinearLayout.LayoutParams(0,-2,1));TextView save=a.text(favorites.contains(p.id)?"已收藏 ★":"添加进收藏 ☆",14,MainActivity.GREEN);save.setPadding(a.dp(8),a.dp(12),a.dp(4),a.dp(12));save.setOnClickListener(v->{try{favorites.toggle(p);if("收藏".equals(category)){loadNearby();}else renderPlaces();}catch(Exception e){a.toast("收藏保存失败，请重试");}});actions.addView(save);card.addView(actions);ThemeViews.apply(card,MainActivity.SURFACE);
        }
    }
    private String heatLabel(NearbyPlace p){if(!p.hasHeat())return "热力未知";String value=p.heat<=2?"较低":p.heat<=4?"中等":"较高";String time=java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm").format(java.time.Instant.ofEpochMilli(p.heatAt).atZone(java.time.ZoneId.systemDefault()));return (p.staleHeat()?"上次参考 · ":"热力参考 · ")+value+" · "+time;}
    private void updateHeatRow(LinearLayout row,NearbyPlace p){int level=p.hasHeat()?p.heat:0;String label=heatLabel(p),tag=level+"|"+label;if(tag.equals(row.getTag()))return;row.setTag(tag);row.removeAllViews();row.addView(new CrowdIcons(a,level),new LinearLayout.LayoutParams(a.dp(92),a.dp(32)));row.addView(a.text(label,12,MainActivity.MUTED));}
    private void patchHeatRows(){if(disposed)return;for(NearbyPlace p:places){LinearLayout row=heatRows.get(p.id);if(row!=null)updateHeatRow(row,p);}}
    private void focus(NearbyPlace p){if(map==null||disposed)return;browsing();LatLng target=new CoordinateConverter().from(CoordinateConverter.CoordType.COMMON).coord(new LatLng(p.lat,p.lon)).convert();map.animateMapStatus(MapStatusUpdateFactory.newLatLngZoom(target,16));if(body.getParent() instanceof ScrollView)((ScrollView)body.getParent()).smoothScrollTo(0,0);status.setText("正在查看 "+p.name+" 的周边热力");}
    private String scope(){return selectedRegion==null?city:selectedRegion.query();}
    private void selectRegion(RegionSelection region){cancelCapture();lastInteractionAt=0;nextAutoAt=0;manualRegion=true;selectedRegion=region;cameraPending=false;requestGeneration++;cachedPlaces.clear();cancelCapture();if(map!=null)map.animateMapStatus(MapStatusUpdateFactory.newLatLngZoom(new CoordinateConverter().from(CoordinateConverter.CoordType.COMMON).coord(new LatLng(region.lat,region.lon)).convert(),region.zoom()));loadNearby();}
    private void resolveLocation(double[] location){if(manualRegion||regionLocating&&resolvingLocation!=null&&GeoMath.meters(resolvingLocation[0],resolvingLocation[1],location[0],location[1])<2000)return;regionLocating=true;resolvingLocation=location.clone();final int locationTicket=++locationGeneration;final long revision=new ApiConfig(a).revision();queries.execute(()->{try{RegionSelection region=RegionService.locate(a,location);handler.post(()->{if(locationTicket!=locationGeneration)return;regionLocating=false;if(disposed||manualRegion||revision!=new ApiConfig(a).revision())return;if(resetRegionPicker){regionPicker.resetToLocation(region);resetRegionPicker=false;}else regionPicker.setLocation(region);city=region.city;loadNearby();});}catch(Exception|LinkageError ignored){handler.post(()->{if(locationTicket==locationGeneration)regionLocating=false;});}});}
    private void refreshAll(){beginRefresh(true);}
    private void refreshIfDue(){if(disposed||!resumed||!mapLoaded||cycleActive||places.isEmpty()||System.currentTimeMillis()<nextAutoAt||System.currentTimeMillis()-lastInteractionAt<15_000)return;if(discovery.refreshDue(scope()))beginRefresh(false);}
    private void beginRefresh(boolean force){
        if(disposed||sampler==null||cycleActive)return;
        ApiConfig cfg=new ApiConfig(a);final boolean sdk=AmapRuntime.configured(a)&&AmapConsent.granted(a);
        if(!sdk&&cfg.mapKey("amap").isEmpty())return;
        if(origin==null&&selectedRegion==null&&city.isEmpty()){locate();return;}
        cancelCapture();cycleActive=true;refreshQueryFailed=false;lastSampleAt=0;final int ticket=++requestGeneration;
        final String area=scope(),knownCity=city;refreshArea=area;final RegionSelection region=selectedRegion;final double[] position=origin==null?new double[]{0,0}:origin.clone();final long revision=cfg.revision();heatRevision=revision;
        refreshPlaces.clear();refreshingPlaces=true;activeRefreshTicket=ticket;nextAutoAt=System.currentTimeMillis()+5*60_000;
        nearbyStatus.setText("正在逐条刷新，暂未更新的地点保留上次参考…");
        queries.execute(()->{try{LinkedHashMap<String,NearbyPlace> all=new LinkedHashMap<>();boolean failed=false;
            for(String name:NearbyPlacesService.CATEGORIES){if("收藏".equals(name))continue;try{
                ArrayList<NearbyPlace> result;
                if(!force&&discovery.placesFresh(area,name))result=discovery.getPlaces(area,name);
                else {NearbyPlacesService.Result fetched=region==null?NearbyPlacesService.search(a,name,position,knownCity,sdk):NearbyPlacesService.searchRegion(a,name,region,sdk);result=fetched.places;}
                if(disposed||ticket!=requestGeneration||revision!=new ApiConfig(a).revision())return;
                for(NearbyPlace p:result)discovery.applyHeat(p);discovery.putPlaces(area,name,result);for(NearbyPlace p:result)all.put(p.id,p);
                final String completedCategory=name;final ArrayList<NearbyPlace> visible=result;
                handler.post(()->{if(disposed||ticket!=requestGeneration)return;if(category.equals(completedCategory)){places.clear();places.addAll(visible);nearbyTitle.setText("发现 "+(selectedRegion==null?city:selectedRegion.label()));renderPlaces();}});
            }catch(Exception|LinkageError ignored){failed=true;for(NearbyPlace p:discovery.getPlaces(area,name))all.put(p.id,p);}}
            final boolean partial=failed;handler.post(()->{if(disposed||ticket!=requestGeneration)return;refreshingPlaces=false;refreshQueryFailed=partial;refreshPlaces.addAll(all.values());fullRefreshPending=true;nearbyStatus.setText(partial?"部分地点查询失败，保留上次结果并继续刷新可用参考。":"地点已加载，正在逐条更新热力参考…");scheduleHeat();});
        }finally{handler.post(()->{if(activeRefreshTicket==ticket&&requestGeneration!=ticket){refreshingPlaces=false;cycleActive=false;}});}});
    }
    private void refreshCompleted(boolean success){if(!cycleActive)return;boolean complete=success&&!refreshQueryFailed&&heatRevision==new ApiConfig(a).revision()&&refreshArea.equals(scope());cycleActive=false;refreshingPlaces=false;fullRefreshPending=false;refreshPlaces.clear();if(complete){refreshedAt=System.currentTimeMillis();discovery.markCompleteRefresh(refreshArea,refreshedAt);nextAutoAt=refreshedAt+NearbyDiscoveryCache.TTL_MS;nearbyStatus.setText("最近刷新完成："+java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm").format(java.time.Instant.ofEpochMilli(refreshedAt).atZone(java.time.ZoneId.systemDefault()))+"；无新数据的地点保留上次参考。");}else{nextAutoAt=System.currentTimeMillis()+5*60_000;nearbyStatus.setText("刷新未全部完成，已更新的地点已保存，其余保留上次参考。");}patchHeatRows();}
    private void cancelCapture(){captureGeneration++;handler.removeCallbacks(analyze);boolean active=cycleActive;cycleActive=false;refreshingPlaces=false;fullRefreshPending=false;refreshPlaces.clear();if(active){requestGeneration++;if(nearbyStatus!=null)nearbyStatus.setText("已暂停刷新，已更新的地点已保存，其余保留上次参考。");}if(sampler!=null)sampler.cancel();}
    private void scheduleHeat(){handler.removeCallbacks(analyze);if(disposed||!resumed||map==null||!mapLoaded||sampler==null||sampler.busy()||refreshingPlaces)return;if(!cycleActive){refreshIfDue();return;}if(fullRefreshPending)handler.postDelayed(analyze,700);}
    private void captureHeat(){if(disposed||!resumed||sampler==null||sampler.busy()||!cycleActive||!fullRefreshPending)return;fullRefreshPending=false;ArrayList<NearbyPlace> pending=new ArrayList<>(refreshPlaces);refreshPlaces.clear();if(pending.isEmpty()){refreshCompleted(!refreshQueryFailed);return;}lastSampleAt=System.currentTimeMillis();sampler.start(pending,true);if(!sampler.busy())refreshCompleted(false);}
}
