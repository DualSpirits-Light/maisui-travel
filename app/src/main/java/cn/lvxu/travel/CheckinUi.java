package cn.lvxu.travel;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.location.*;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

/** Check-in editor, location lookup, card renderer and history screen. */
public final class CheckinUi {
    final MainActivity a; final MediaController media; final Handler main=new Handler(Looper.getMainLooper());
    private final ArrayList<String> groupPhotos=new ArrayList<>(),sceneryPhotos=new ArrayList<>();private EditText place,mood,time,companions;private TextView locationStatus,photoState;private AlertDialog editor;private int draftGeneration,activeDraft;private Trip.Checkin editing;
    private Trip historyTrip;
    private LinearLayout photoList;private final ArrayList<String> ownedDraftPhotos=new ArrayList<>();
    private String memoryDay="",draftStopId="";private TextView associationStatus;
    private Double lat,lon;private float accuracy;private LocationManager locationManager;private LocationListener listener;private Runnable timeout;private LocationSession locationSession;private ExecutorService reverseGeocoder;private int locationResultGeneration;
    private String pendingExport;

    public CheckinUi(MainActivity activity,MediaController controller){a=activity;media=controller;controller.attachCheckin(this);}

    public void show(){
        if(historyTrip==null){
            a.heading("旅行打卡","按旅行整理你的打卡记录。选择一个旅行查看记录。");
            CheckinHistoryUi.addTripGroups(a,a.trips,trip->{historyTrip=trip;memoryDay="";a.active=trip;a.page=4;a.render();});
            return;
        }
        if(!a.trips.contains(historyTrip)){historyTrip=null;show();return;}
        a.heading(historyTrip.title,"把此刻的心情、地点和照片留在旅程里。");
        a.pair(a.body,a.action("← 所有旅行",false,()->{historyTrip=null;a.render();}),a.action("＋ 新建打卡",true,this::beginNew));
        a.space(a.body,8);
        a.body.addView(a.action("选择照片打卡",false,()->{beginNew();media.chooseCheckinPhoto();}));
        a.space(a.body,8);
        a.body.addView(a.action("按天浏览："+(empty(memoryDay)?"全部日期":CheckinMemories.dayLabel(historyTrip,memoryDay)),false,this::chooseMemoryDay));
        a.section("打卡记录");
        Trip owner=historyTrip;
        if(owner.checkins.isEmpty())a.body.addView(a.text("该旅行还没有打卡记录。可以拍照，也可以手动从相册选择。",14,MainActivity.MUTED));
        ArrayList<Trip.Checkin> list=CheckinMemories.records(owner,memoryDay);
        if(list.isEmpty()&&!owner.checkins.isEmpty())a.body.addView(a.text("这一天还没有打卡回忆。可选择其他日期或新建打卡。",14,MainActivity.MUTED));
        int previewCount=0;for(Trip.Checkin c:list){LinearLayout box=a.card(a.body);String preview=primaryPhoto(c);if(!empty(preview)&&previewCount<12){try{Bitmap b=media.files.decode(preview,480);box.addView(CheckinHistoryUi.squareThumbnail(a,b,160));a.space(box,14);previewCount++;}catch(Exception ignored){}}
            box.addView(a.bold(empty(c.place)?"旅途中的此刻":c.place,19,MainActivity.INK));a.space(box,6);box.addView(a.text(CheckinGroups.recordSubtitle(c),13,MainActivity.MUTED));a.space(box,6);box.addView(a.text(CheckinMemories.linkLabel(owner,c),12,MainActivity.MUTED));addPhotoAction(box,c);a.space(box,12);a.pair(box,a.action("编辑",false,()->beginEdit(c)),a.action("生成电子卡片",false,()->generateAndShare(c)));a.space(box,8);box.addView(a.action("删除",false,()->a.confirm("删除这条打卡？",()->{if(CheckinGroups.ownerOf(a.trips,c)!=owner)return;owner.checkins.remove(c);a.active=owner;a.changed();})));}
    }

    void beginNew(){beginNew("");}
    /** Package entry for a place-details page: switches to the owning trip before opening its draft. */
    void beginNew(Trip trip,Trip.Stop stop){
        if(trip==null||stop==null||!a.trips.contains(trip)||!trip.stops.contains(stop)){a.toast("所属旅行已删除，无法新建打卡");return;}
        CheckinPrefill.Draft draft=CheckinPrefill.forStop(trip,stop);
        historyTrip=draft.trip;a.active=draft.trip;a.day=Math.max(0,Math.min(stop.day,draft.trip.days-1));a.page=4;a.render();
        beginNew(draft.place);draftStopId=stop.id;updateAssociation();
    }
    private void beginNew(String prefilledPlace){editing=null;draftStopId="";groupPhotos.clear();sceneryPhotos.clear();lat=null;lon=null;accuracy=0;activeDraft=++draftGeneration;editNew(prefilledPlace);}
    void beginEdit(Trip.Checkin c){
        Trip owner=CheckinGroups.ownerOf(a.trips,c);if(owner==null){a.toast("这条打卡已不属于当前旅行");return;}historyTrip=owner;
        a.active=historyTrip;editing=c;draftStopId=c.stopId;groupPhotos.clear();groupPhotos.addAll(CheckinEditRules.editableGroupPhotos(c.groupPhotos,c.sceneryPhotos,c.photo));sceneryPhotos.clear();sceneryPhotos.addAll(c.sceneryPhotos);lat=c.lat;lon=c.lon;accuracy=c.accuracy;activeDraft=++draftGeneration;editNew();
    }
    private void editNew(){editNew("");}
    private void editNew(String prefilledPlace){
        if(activeDraft==0)activeDraft=++draftGeneration;LinearLayout f=a.col();photoState=a.text(photoSummary(),13,MainActivity.MUTED);f.addView(photoState);a.space(f,8);a.pair(f,a.action("添加合照",false,()->media.chooseCheckinPhoto("group")),a.action("添加风景",false,()->media.chooseCheckinPhoto("scenery")));a.space(f,8);photoList=a.col();f.addView(photoList);refreshPhotos();a.space(f,12);
        associationStatus=a.text("",12,MainActivity.MUTED);f.addView(associationStatus);f.addView(a.action("选择关联行程地点",false,this::chooseAssociation));updateAssociation();a.space(f,8);
        place=a.field(f,"地点",editing==null?prefilledPlace:editing.place,android.text.InputType.TYPE_CLASS_TEXT);mood=a.field(f,"此刻心情",editing==null?"开心":editing.mood,android.text.InputType.TYPE_CLASS_TEXT);time=DateTimeFields.dateTime(a,f,"日期与时间",editing==null?LocalDateTime.now().withSecond(0).withNano(0).toString():editing.time);companions=a.field(f,"同行人员（逗号、分号或换行分隔）",editing==null?"":String.join("\n",editing.companions),android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);locationStatus=a.text(lat==null?"定位未开启；地点也可以手动填写。":String.format(Locale.ROOT,"位置已记录 · %.5f, %.5f",lat,lon),12,MainActivity.MUTED);f.addView(locationStatus);a.space(f,8);f.addView(a.action("使用当前位置",false,this::requestLocation));
        ScrollView scroll=new ScrollView(a);scroll.addView(f);editor=new RoundedDialogs.Builder(a).setTitle(editing==null?"新建打卡":"编辑打卡").setView(scroll).setNegativeButton("取消",null).setPositiveButton("保存",null).create();a.pad(f,22);
        editor.setOnShowListener(v->editor.getButton(-1).setOnClickListener(w->saveDraft()));
        editor.setOnDismissListener(v->{stopLocation();releaseDraftPhotos();if(activeDraft!=0&&editor!=null&&!editor.isShowing())draftGeneration++;});editor.show();
    }

    private void saveDraft(){
        try{
            Trip owner=historyTrip==null?a.active:historyTrip;
            if(owner==null||!a.trips.contains(owner))throw new IllegalArgumentException("请先选择旅行");
            if(editing!=null&&CheckinGroups.ownerOf(a.trips,editing)!=owner)throw new IllegalArgumentException("这条打卡已不属于当前旅行");
            if(editing==null&&owner.checkins.size()>=1000)throw new IllegalArgumentException("每段旅行最多保存 1000 条打卡");
            String p=place.getText().toString().trim(),m=mood.getText().toString().trim();
            if(p.length()>120||m.length()>80)throw new IllegalArgumentException("地点最多 120 字，心情最多 80 字");
            String entered=time.getText().toString().trim();
            if(DateTimeFields.parseDateTime(entered,null)==null)throw new IllegalArgumentException("日期时间格式应为 2026-09-07 18:30");
            ArrayList<String> people=CheckinEditRules.companions(companions.getText().toString());
            Trip.Checkin candidate=new Trip.Checkin();
            if(editing!=null){candidate.id=editing.id;candidate.card=editing.card;}
            candidate.groupPhotos.addAll(groupPhotos);candidate.sceneryPhotos.addAll(sceneryPhotos);
            candidate.photo=CheckinEditRules.cover(groupPhotos,sceneryPhotos,"");candidate.stopId=draftStopId;
            candidate.place=p;candidate.mood=m;candidate.time=DateTimeFields.storageDateTime(entered);
            candidate.companions.addAll(people);candidate.lat=lat;candidate.lon=lon;candidate.accuracy=accuracy;
            candidate.coordinateSystem="WGS84";
            // Keep the saved object identity for open history/search pages, but rollback on disk failure.
            Trip.Checkin record=editing==null?candidate:editing;
            Trip.Checkin previous=editing==null?null:new Trip.Checkin();
            if(previous!=null)copyRecord(editing,previous);
            if(editing==null)owner.checkins.add(record);else copyRecord(candidate,record);
            a.active=owner;
            if(!a.save()){
                if(previous==null)owner.checkins.remove(record);else copyRecord(previous,record);
                return;
            }
            editor.dismiss();groupPhotos.clear();sceneryPhotos.clear();lat=null;lon=null;accuracy=0;
            editing=null;activeDraft=0;a.render();
        }catch(IllegalArgumentException e){a.toast(e.getMessage());}
        catch(Exception e){a.toast("打卡未保存，请重试；填写内容已保留");}
    }
    private static void copyRecord(Trip.Checkin from,Trip.Checkin to){
        to.id=from.id;to.photo=from.photo;to.card=from.card;to.stopId=from.stopId;to.place=from.place;
        to.mood=from.mood;to.time=from.time;to.lat=from.lat;to.lon=from.lon;to.accuracy=from.accuracy;
        to.coordinateSystem=from.coordinateSystem;
        to.groupPhotos.clear();to.groupPhotos.addAll(from.groupPhotos);
        to.sceneryPhotos.clear();to.sceneryPhotos.addAll(from.sceneryPhotos);
        to.companions.clear();to.companions.addAll(from.companions);

    }

    private void chooseMemoryDay(){
        ArrayList<String> keys=CheckinMemories.days(historyTrip);String[] labels=new String[keys.size()+1];labels[0]="全部日期";
        for(int i=0;i<keys.size();i++)labels[i+1]=CheckinMemories.dayLabel(historyTrip,keys.get(i));
        new RoundedDialogs.Builder(a).setTitle("按天浏览回忆").setItems(labels,(d,i)->{memoryDay=i==0?"":keys.get(i-1);a.render();}).show();
    }
    private void updateAssociation(){
        if(associationStatus==null)return;Trip.Checkin preview=new Trip.Checkin();preview.stopId=draftStopId;
        preview.place=editing==null?"":editing.place;associationStatus.setText(CheckinMemories.linkLabel(historyTrip==null?a.active:historyTrip,preview));
    }
    private void chooseAssociation(){
        Trip owner=historyTrip==null?a.active:historyTrip;if(owner==null)return;
        ArrayList<Trip.Stop> stops=new ArrayList<>(owner.stops);String[] labels=new String[stops.size()+1];labels[0]="不关联行程地点";
        for(int i=0;i<stops.size();i++){Trip.Stop stop=stops.get(i);labels[i+1]="第 "+(stop.day+1)+" 天 · "+stop.time+" · "+stop.name+(empty(stop.address)?"":" · "+stop.address);}
        new RoundedDialogs.Builder(a).setTitle("关联地点（请核对日期和地址）").setItems(labels,(d,i)->{
            if(i==0)draftStopId="";else{Trip.Stop selected=stops.get(i-1);if(!owner.stops.contains(selected)){a.toast("地点已删除，请重新选择");return;}draftStopId=selected.id;if(place!=null&&empty(place.getText().toString()))place.setText(selected.name);}updateAssociation();
        }).show();
    }
    private void addPhotoAction(LinearLayout box,Trip.Checkin record){
        ArrayList<String> photos=CheckinMemories.photos(record);if(photos.isEmpty())return;a.space(box,6);
        box.addView(a.action("查看打卡照片（"+photos.size()+" 张）",false,()->{
            String[] labels=new String[photos.size()];for(int i=0;i<labels.length;i++)labels[i]="照片 "+(i+1);
            new RoundedDialogs.Builder(a).setTitle("打卡照片").setItems(labels,(d,i)->PhotoPreviewUi.show(a,photos.get(i))).show();
        }));
    }
    void showPlaceMemories(Trip trip,Trip.Stop stop){
        if(trip==null||stop==null||!a.trips.contains(trip)||!trip.stops.contains(stop)){a.toast("地点已删除");return;}
        PageUi page=new PageUi(a,"地点回忆 · "+stop.name);ArrayList<Trip.Checkin> records=CheckinMemories.forStop(trip,stop);
        if(records.isEmpty())page.body.addView(a.text("还没有明确关联到此地点的打卡。旧记录可在旅行打卡中编辑并选择关联地点。",14,MainActivity.MUTED));
        for(Trip.Checkin record:records){LinearLayout card=a.card(page.body);card.addView(a.bold(empty(record.place)?stop.name:record.place,18,MainActivity.INK));card.addView(a.text(CheckinGroups.recordSubtitle(record),13,MainActivity.MUTED));addPhotoAction(card,record);
            card.addView(a.action("编辑这条打卡",false,()->{page.dialog.dismiss();historyTrip=trip;a.active=trip;beginEdit(record);}));}
        page.body.addView(a.action("以此地点新建打卡",true,()->{page.dialog.dismiss();beginNew(trip,stop);}));page.show();
    }

    private void pickDateTime(){LocalDateTime current=DateTimeFields.parseDateTime(time==null?"":time.getText().toString(),LocalDateTime.now());final LocalDateTime base=current;new DatePickerDialog(a,(picker,y,m,d)->new TimePickerDialog(a,(clock,h,min)->time.setText(DateTimeValues.normalize(LocalDateTime.of(y,m+1,d,h,min).toString())),base.getHour(),base.getMinute(),true).show(),base.getYear(),base.getMonthValue()-1,base.getDayOfMonth()).show();}

    void photoSelected(String path){photoSelected(path,"group");}
    void photoSelected(String path,String type){if(!empty(path)){ownedDraftPhotos.add(path);ArrayList<String> target="scenery".equals(type)?sceneryPhotos:groupPhotos;if(target.size()>=50){a.toast("每类照片最多 50 张");return;}target.add(path);}if(editor==null||!editor.isShowing())editNew();refreshPhotos();}
    private void releaseDraftPhotos(){if(a.isChangingConfigurations()||a.isDestroyed())return;media.detachSelection(MediaController.SelectionOwner.CHECKIN);for(String path:ownedDraftPhotos)DraftMediaFiles.discard(a,path);ownedDraftPhotos.clear();}
    private String photoSummary(){return "照片：合照 "+groupPhotos.size()+" 张，风景 "+sceneryPhotos.size()+" 张";}
    private void refreshPhotos(){
        if(photoState!=null)photoState.setText(photoSummary());
        if(photoList==null)return;photoList.removeAllViews();
        addDraftPhotos(groupPhotos,"合照");addDraftPhotos(sceneryPhotos,"风景");
        if(!groupPhotos.isEmpty()||!sceneryPhotos.isEmpty())photoList.addView(a.action("移除全部照片",false,()->{
            groupPhotos.clear();sceneryPhotos.clear();refreshPhotos();
        }));
    }
    private void addDraftPhotos(ArrayList<String> photos,String label){
        if(photos.isEmpty())return;
        photoList.addView(a.text(label+"（点击缩略图预览）",12,MainActivity.MUTED));
        HorizontalScrollView scroll=new HorizontalScrollView(a);LinearLayout row=a.row();scroll.addView(row);
        for(int i=0;i<photos.size();i++){
            final int index=i;String path=photos.get(i);LinearLayout tile=a.col();a.pad(tile,4);
            ImageView image=new ImageView(a);image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setContentDescription("预览"+label+"照片 "+(i+1));
            try{image.setImageBitmap(media.files.decode(path,160));}catch(Exception ignored){image.setImageResource(android.R.drawable.ic_menu_gallery);}
            tile.addView(image,new LinearLayout.LayoutParams(a.dp(76),a.dp(76)));
            image.setOnClickListener(v->PhotoPreviewUi.show(a,path));
            tile.addView(a.action("移除"+label+" "+(i+1),false,()->{if(index<photos.size())photos.remove(index);refreshPhotos();}));
            row.addView(tile);
        }
        photoList.addView(scroll);
    }
    private static String primaryPhoto(Trip.Checkin c){
        String selected=CheckinEditRules.cover(c.groupPhotos,c.sceneryPhotos,"");
        return empty(selected)?(c.photo==null?"":c.photo):selected;
    }

    private void requestLocation(){boolean fine=a.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED,coarse=a.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED;if(!fine&&!coarse){a.requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},MediaController.LOCATION);return;}startLocation(fine);}
    void onLocationPermission(int[] results){boolean fine=a.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED,coarse=a.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED;if((fine||coarse)&&locationStatus!=null)startLocation(fine);else{if(locationStatus!=null)locationStatus.setText("未授予定位权限；可重试或手动填写地点。");a.toast("可以继续手动填写打卡地点");}}

    @SuppressWarnings("MissingPermission") private void startLocation(boolean precise){
        stopLocation();if(locationStatus==null||editor==null||!editor.isShowing())return;
        locationStatus.setText(precise?"正在获取当前位置…":"正在获取大致位置…");locationManager=(LocationManager)a.getSystemService(Context.LOCATION_SERVICE);if(locationManager==null){locationStatus.setText("暂时无法定位；请重试或手动填写地点。");return;}
        if(!systemLocationEnabled()){locationManager=null;locationStatus.setText("系统定位已关闭；请打开定位后重试，或手动填写地点。");return;}
        boolean gps=providerEnabled(LocationManager.GPS_PROVIDER),network=providerEnabled(LocationManager.NETWORK_PROVIDER),passive=providerEnabled(LocationManager.PASSIVE_PROVIDER);locationSession=new LocationSession(System.currentTimeMillis());
        if(locationSession.start(gps,network,passive)==LocationSession.Started.NO_PROVIDER){locationSession=null;locationStatus.setText("系统定位已关闭；请打开定位后重试，或手动填写地点。");return;}
        listener=new LocationListener(){@Override public void onLocationChanged(Location l){onLocationUpdate(l);}@Override public void onProviderEnabled(String p){}@Override public void onProviderDisabled(String p){if(!systemLocationEnabled()||(!providerEnabled(LocationManager.GPS_PROVIDER)&&!providerEnabled(LocationManager.NETWORK_PROVIDER)&&!providerEnabled(LocationManager.PASSIVE_PROVIDER))){stopLocation();if(locationStatus!=null)locationStatus.setText("系统定位已关闭；请打开定位后重试，或手动填写地点。");}}@Override public void onStatusChanged(String p,int s,Bundle b){}};
        boolean registered=false;ArrayList<LocationSession.Fix> cached=new ArrayList<>();
        for(LocationSession.Provider provider:locationSession.providers())try{String name=providerName(provider);locationManager.requestLocationUpdates(name,0,0,listener,Looper.getMainLooper());registered=true;Location last=locationManager.getLastKnownLocation(name);if(last!=null)cached.add(toFix(provider,last));}catch(Exception ignored){}
        if(!registered){stopLocation();locationStatus.setText("暂时无法定位；请重试或手动填写地点。");return;}
        LocationSession.Fix fallback=locationSession.offerCached(cached);if(fallback!=null)acceptLocation(fallback);
        final LocationSession session=locationSession;timeout=()->{if(session!=locationSession||!session.active())return;LocationSession.Fix best=session.best();session.timeout();stopLocation();if(locationStatus==null||editor==null||!editor.isShowing())return;if(best==null)locationStatus.setText("定位超时；请检查系统定位、重试或手动填写地点。");else if(LocationSession.weak(best))locationStatus.setText(String.format(Locale.ROOT,"定位信号较弱（精度约 %.0f 米）；可重试或手动填写地点。",best.accuracy));else locationStatus.setText(String.format(Locale.ROOT,"定位成功 · 精度约 %.0f 米",best.accuracy));};main.postDelayed(timeout,15_000);
    }
    private boolean providerEnabled(String provider){try{return locationManager!=null&&locationManager.isProviderEnabled(provider);}catch(Exception ignored){return false;}}
    private boolean systemLocationEnabled(){try{if(locationManager==null)return false;if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.P)return locationManager.isLocationEnabled();return providerEnabled(LocationManager.GPS_PROVIDER)||providerEnabled(LocationManager.NETWORK_PROVIDER)||providerEnabled(LocationManager.PASSIVE_PROVIDER);}catch(Exception ignored){return false;}}
    private static String providerName(LocationSession.Provider provider){switch(provider){case GPS:return LocationManager.GPS_PROVIDER;case NETWORK:return LocationManager.NETWORK_PROVIDER;default:return LocationManager.PASSIVE_PROVIDER;}}
    private static LocationSession.Provider providerFor(String provider){if(LocationManager.GPS_PROVIDER.equals(provider))return LocationSession.Provider.GPS;if(LocationManager.NETWORK_PROVIDER.equals(provider))return LocationSession.Provider.NETWORK;if(LocationManager.PASSIVE_PROVIDER.equals(provider))return LocationSession.Provider.PASSIVE;return null;}
    private static LocationSession.Fix toFix(LocationSession.Provider provider,Location location){return new LocationSession.Fix(provider,location.getLatitude(),location.getLongitude(),location.getAccuracy(),location.getTime());}
    private void onLocationUpdate(Location location){LocationSession session=locationSession;if(session==null)return;LocationSession.Fix accepted=session.offerRealtime(toFix(providerFor(location.getProvider()),location));if(accepted!=null)acceptLocation(accepted);}
    private void stopLocation(){locationResultGeneration++;if(timeout!=null){main.removeCallbacks(timeout);timeout=null;}if(locationSession!=null)locationSession.cancel();locationSession=null;if(locationManager!=null&&listener!=null)try{locationManager.removeUpdates(listener);}catch(Exception ignored){}listener=null;locationManager=null;if(reverseGeocoder!=null){reverseGeocoder.shutdownNow();reverseGeocoder=null;}}
    private void acceptLocation(LocationSession.Fix fix){lat=fix.latitude;lon=fix.longitude;accuracy=fix.accuracy;final double queryLat=lat,queryLon=lon;final int draftToken=activeDraft,lookupToken=++locationResultGeneration;locationStatus.setText(LocationSession.weak(fix)?String.format(Locale.ROOT,"定位信号较弱 · 精度约 %.0f 米，继续尝试更准确的位置…",accuracy):String.format(Locale.ROOT,"位置已获取 · 精度约 %.0f 米，正在识别地点…",accuracy));if(reverseGeocoder!=null)reverseGeocoder.shutdownNow();ExecutorService e=Executors.newSingleThreadExecutor();reverseGeocoder=e;e.execute(()->{String name="";try{Geocoder g=new Geocoder(a,Locale.getDefault());List<Address> found=g.getFromLocation(queryLat,queryLon,1);if(found!=null&&!found.isEmpty()){Address ad=found.get(0);name=ad.getFeatureName();if(empty(name))name=ad.getLocality();if(empty(name))name=ad.getAdminArea();}}catch(Exception ignored){}String result=name;e.shutdown();main.post(()->{if(draftToken!=activeDraft||lookupToken!=locationResultGeneration||editor==null||!editor.isShowing())return;if(!empty(result)&&place!=null&&place.getText().toString().trim().isEmpty())place.setText(result);if(locationStatus!=null)locationStatus.setText(empty(result)?"位置已获取；地点名称请手动填写。":"位置已获取 · "+result);});});}

    private void generateAndShare(Trip.Checkin c){final String[] path={null};a.runJob("正在生成电子卡片",()->{Bitmap card=drawCard(c);path[0]=media.files.save(card,"cards",92);card.recycle();return path[0];},()->{c.card=path[0];a.save();previewCard(path[0]);});}
    private void previewCard(String relative){try{Bitmap bitmap=media.files.decode(relative,1200);ImageView image=new ImageView(a);image.setAdjustViewBounds(true);image.setImageBitmap(bitmap);ScrollView scroll=new ScrollView(a);scroll.addView(image);AlertDialog d=new RoundedDialogs.Builder(a).setTitle("电子卡片预览").setView(scroll).setNegativeButton("稍后保存",null).setPositiveButton("保存到相册",(x,w)->exportCard(relative)).create();d.setOnDismissListener(x->{if(!bitmap.isRecycled())bitmap.recycle();});d.show();}catch(Exception e){a.toast("卡片已生成，但暂时无法预览");}}
    private Bitmap drawCard(Trip.Checkin c)throws IOException{int w=1200,h=1600;Bitmap out=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(out);Paint p=new Paint(3);p.setColor(0xffF1EBDD);canvas.drawRect(0,0,w,h,p);if(!empty(c.photo)){Bitmap image=media.files.decode(c.photo,1800);float s=Math.max(w/(float)image.getWidth(),1000f/image.getHeight());float dw=image.getWidth()*s,dh=image.getHeight()*s;canvas.drawBitmap(image,null,new RectF((w-dw)/2,0,(w+dw)/2,dh),p);image.recycle();}p.setColor(0xcc173D34);canvas.drawRect(0,980,w,1600,p);p.setColor(Color.WHITE);p.setTypeface(Typeface.create(Typeface.SANS_SERIF,Typeface.BOLD));p.setTextSize(68);drawText(canvas,p,empty(c.place)?"旅途中的此刻":c.place,80,1095,1040);p.setTypeface(Typeface.create(Typeface.SANS_SERIF,Typeface.NORMAL));p.setTextSize(39);drawWrapped(canvas,p,empty(c.mood)?"这一刻，值得记住":c.mood,80,1190,1040,50,4);p.setTextSize(29);p.setColor(0xffD9E7DF);String coordinates=c.lat==null||c.lon==null?"位置坐标未记录":String.format(Locale.ROOT,"WGS84  %.5f, %.5f  ·  精度约 %.0f 米",c.lat,c.lon,c.accuracy);drawText(canvas,p,coordinates,80,1430,1040);canvas.drawText(displayTime(c.time),80,1480,p);canvas.drawText("麦穗旅序 · 我的旅行打卡",80,1530,p);return out;}
    private static void drawText(Canvas c,Paint p,String text,float x,float y,float max){String s=text==null?"":text;while(p.measureText(s)>max&&s.length()>1)s=s.substring(0,s.length()-1);if(!s.equals(text))s+="…";c.drawText(s,x,y,p);}
    private static void drawWrapped(Canvas c,Paint p,String text,float x,float y,float max,float lineHeight,int maxLines){String s=text==null?"":text;int start=0,line=0;while(start<s.length()&&line<maxLines){int end=start+1;while(end<=s.length()&&p.measureText(s.substring(start,end))<=max)end++;end=Math.max(start+1,end-1);c.drawText(s.substring(start,end),x,y+line*lineHeight,p);start=end;line++;}}

    private void exportCard(String relative){if(relative==null)return;if(Build.VERSION.SDK_INT>=29){Uri uri=null;try{ContentValues v=new ContentValues();v.put(MediaStore.Images.Media.DISPLAY_NAME,"麦穗旅序-打卡-"+System.currentTimeMillis()+".jpg");v.put(MediaStore.Images.Media.MIME_TYPE,"image/jpeg");v.put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/麦穗旅序");v.put(MediaStore.Images.Media.IS_PENDING,1);uri=a.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,v);if(uri==null)throw new IOException();copy(relative,uri);v.clear();v.put(MediaStore.Images.Media.IS_PENDING,0);a.getContentResolver().update(uri,v,null,null);a.toast("电子卡片已保存到相册");}catch(Exception e){if(uri!=null)try{a.getContentResolver().delete(uri,null,null);}catch(Exception ignored){}a.toast("卡片已保存在应用内，但无法写入相册");}}else{pendingExport=relative;Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("image/jpeg").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,"麦穗旅序-打卡.jpg");a.startActivityForResult(i,MediaController.EXPORT_CARD);}}
    boolean onResult(int req,int result,Intent data){if(req!=MediaController.EXPORT_CARD)return false;if(result==Activity.RESULT_OK&&data!=null&&data.getData()!=null&&pendingExport!=null){try{copy(pendingExport,data.getData());a.toast("电子卡片已保存");}catch(Exception e){a.toast("保存电子卡片失败");}}pendingExport=null;return true;}
    private void copy(String relative,Uri dest)throws IOException{try(InputStream in=new FileInputStream(media.files.file(relative));OutputStream out=a.getContentResolver().openOutputStream(dest,"w")){if(out==null)throw new IOException();byte[] b=new byte[16384];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}}
    void saveState(Bundle b){b.putStringArrayList("checkin.owned",new ArrayList<>(ownedDraftPhotos));if(historyTrip!=null)b.putString("checkin.tripId",historyTrip.id);b.putString("checkin.memoryDay",memoryDay);b.putString("checkin.stopId",draftStopId);if(pendingExport!=null)b.putString("checkin.export",pendingExport);b.putStringArrayList("checkin.group",new ArrayList<>(groupPhotos));b.putStringArrayList("checkin.scenery",new ArrayList<>(sceneryPhotos));if(lat!=null)b.putDouble("checkin.lat",lat);if(lon!=null)b.putDouble("checkin.lon",lon);b.putFloat("checkin.accuracy",accuracy);if(editor!=null&&editor.isShowing()){b.putBoolean("checkin.editing",true);if(editing!=null)b.putString("checkin.editingId",editing.id);b.putString("checkin.place",place.getText().toString());b.putString("checkin.mood",mood.getText().toString());b.putString("checkin.time",time.getText().toString());b.putString("checkin.companions",companions.getText().toString());}}
    void restoreState(Bundle b){ownedDraftPhotos.clear();ArrayList<String> owned=b.getStringArrayList("checkin.owned");if(owned!=null)ownedDraftPhotos.addAll(owned);String tripId=b.getString("checkin.tripId","");for(Trip trip:a.trips)if(tripId.equals(trip.id)){historyTrip=trip;break;}memoryDay=b.getString("checkin.memoryDay","");draftStopId=b.getString("checkin.stopId","");pendingExport=b.getString("checkin.export");groupPhotos.clear();ArrayList<String> group=b.getStringArrayList("checkin.group");if(group!=null)groupPhotos.addAll(group);sceneryPhotos.clear();ArrayList<String> scenery=b.getStringArrayList("checkin.scenery");if(scenery!=null)sceneryPhotos.addAll(scenery);if(b.containsKey("checkin.lat"))lat=b.getDouble("checkin.lat");if(b.containsKey("checkin.lon"))lon=b.getDouble("checkin.lon");accuracy=b.getFloat("checkin.accuracy");if(b.getBoolean("checkin.editing")){String editingId=b.getString("checkin.editingId","");editing=null;Trip restoreOwner=historyTrip==null?a.active:historyTrip;if(restoreOwner!=null&&!editingId.isEmpty())for(Trip.Checkin candidate:restoreOwner.checkins)if(editingId.equals(candidate.id)){editing=candidate;break;}String savedPlace=b.getString("checkin.place",""),savedMood=b.getString("checkin.mood","开心"),savedTime=b.getString("checkin.time",LocalDateTime.now().withSecond(0).withNano(0).toString()),savedCompanions=b.getString("checkin.companions","");main.post(()->{if(a.isFinishing()||a.isDestroyed())return;editNew();place.setText(savedPlace);mood.setText(savedMood);time.setText(savedTime);companions.setText(savedCompanions);if(lat!=null&&lon!=null)locationStatus.setText(String.format(Locale.ROOT,"位置已获取 · %.5f, %.5f",lat,lon));});}}
    static String displayTime(String iso){try{return LocalDateTime.parse(iso).format(DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm"));}catch(Exception e){return iso==null?"":iso;}}
    static boolean empty(String s){return s==null||s.trim().isEmpty();}
}
