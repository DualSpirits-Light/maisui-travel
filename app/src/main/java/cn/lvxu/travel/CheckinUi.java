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
    private String photo="";private EditText place,mood,time;private TextView locationStatus,photoState;private AlertDialog editor;private int draftGeneration,activeDraft;
    private Double lat,lon;private float accuracy;private LocationManager locationManager;private LocationListener listener;private Runnable timeout;
    private String pendingExport;

    public CheckinUi(MainActivity activity,MediaController controller){a=activity;media=controller;controller.attachCheckin(this);}

    public void show(){
        a.tripLabel();a.heading("旅行打卡","把此刻的心情、地点和照片留在旅程里。");
        a.pair(a.body,a.action("＋ 新建打卡",true,this::beginNew),a.action("选择照片打卡",false,()->{beginNew();media.chooseCheckinPhoto();}));
        a.section("打卡记录");
        if(a.active.checkins.isEmpty())a.body.addView(a.text("还没有打卡。可以拍照，也可以手动从相册选择。",14,MainActivity.MUTED));
        ArrayList<Trip.Checkin> list=new ArrayList<>(a.active.checkins);list.sort((x,y)->y.time.compareTo(x.time));
        int previewCount=0;for(Trip.Checkin c:list){LinearLayout box=a.card(a.body);if(c.photo!=null&&!c.photo.isEmpty()&&previewCount<12){try{Bitmap b=media.files.decode(c.photo,480);ImageView image=new ImageView(a);image.setScaleType(ImageView.ScaleType.CENTER_CROP);image.setImageBitmap(b);box.addView(image,new LinearLayout.LayoutParams(-1,a.dp(180)));a.space(box,14);previewCount++;}catch(Exception ignored){}}
            box.addView(a.bold(empty(c.place)?"旅途中的此刻":c.place,19,MainActivity.INK));a.space(box,6);box.addView(a.text((empty(c.mood)?"未填写心情":c.mood)+"  ·  "+displayTime(c.time),13,MainActivity.MUTED));a.space(box,12);a.pair(box,a.action("生成电子卡片",false,()->generateAndShare(c)),a.action("删除",false,()->a.confirm("删除这条打卡？",()->{a.active.checkins.remove(c);a.changed();})));}
    }

    private void beginNew(){photo="";lat=null;lon=null;accuracy=0;activeDraft=++draftGeneration;editNew();}
    private void editNew(){
        if(activeDraft==0)activeDraft=++draftGeneration;LinearLayout f=a.col();photoState=a.text(photo.isEmpty()?"照片：可选（拍照或从相册选择）":"照片：已添加",13,MainActivity.MUTED);f.addView(photoState);a.space(f,8);f.addView(a.action("添加 / 更换照片",false,()->media.chooseCheckinPhoto()));a.space(f,12);
        place=a.field(f,"地点","",android.text.InputType.TYPE_CLASS_TEXT);mood=a.field(f,"此刻心情","开心",android.text.InputType.TYPE_CLASS_TEXT);time=a.field(f,"日期与时间",LocalDateTime.now().withSecond(0).withNano(0).toString(),android.text.InputType.TYPE_CLASS_DATETIME);time.setFocusable(false);time.setOnClickListener(v->pickDateTime());locationStatus=a.text("定位未开启；地点也可以手动填写。",12,MainActivity.MUTED);f.addView(locationStatus);a.space(f,8);f.addView(a.action("使用当前位置",false,this::requestLocation));
        ScrollView scroll=new ScrollView(a);scroll.addView(f);editor=new AlertDialog.Builder(a).setTitle("新建打卡").setView(scroll).setNegativeButton("取消",null).setPositiveButton("保存",null).create();a.pad(f,22);
        editor.setOnShowListener(v->editor.getButton(-1).setOnClickListener(w->{try{if(a.active.checkins.size()>=1000)throw new IllegalArgumentException("每段旅行最多保存 1000 条打卡");String p=place.getText().toString().trim(),m=mood.getText().toString().trim();if(p.length()>120||m.length()>80)throw new IllegalArgumentException("地点最多 120 字，心情最多 80 字");LocalDateTime when=LocalDateTime.parse(time.getText().toString().trim());Trip.Checkin c=new Trip.Checkin();c.id=UUID.randomUUID().toString();c.photo=photo;c.place=p;c.mood=m;c.time=when.toString();c.lat=lat;c.lon=lon;c.accuracy=accuracy;c.coordinateSystem="WGS84";a.active.checkins.add(c);editor.dismiss();photo="";lat=null;lon=null;accuracy=0;activeDraft=0;a.changed();}catch(Exception e){a.toast(e instanceof IllegalArgumentException?e.getMessage():"日期时间格式应为 2026-09-07T18:30");}}));editor.setOnDismissListener(v->{stopLocation();if(activeDraft!=0&&editor!=null&&!editor.isShowing())draftGeneration++;});editor.show();
    }

    private void pickDateTime(){LocalDateTime current;try{current=LocalDateTime.parse(time.getText().toString());}catch(Exception e){current=LocalDateTime.now();}final LocalDateTime base=current;new DatePickerDialog(a,(picker,y,m,d)->new TimePickerDialog(a,(clock,h,min)->time.setText(LocalDateTime.of(y,m+1,d,h,min).toString()),base.getHour(),base.getMinute(),true).show(),base.getYear(),base.getMonthValue()-1,base.getDayOfMonth()).show();}

    void photoSelected(String path){photo=path==null?"":path;if(editor==null||!editor.isShowing())editNew();if(photoState!=null)photoState.setText(photo.isEmpty()?"照片：未添加":"照片：已添加");a.toast(photo.isEmpty()?"将创建无照片打卡":"照片已添加，正在尝试获取位置");if(!photo.isEmpty()&&lat==null)requestLocation();}

    private void requestLocation(){if(a.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED&&a.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED){a.requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},MediaController.LOCATION);return;}startLocation();}
    void onLocationPermission(int[] results){boolean granted=false;for(int r:results)if(r==PackageManager.PERMISSION_GRANTED)granted=true;if(granted&&locationStatus!=null)startLocation();else{if(locationStatus!=null)locationStatus.setText("未授予定位权限；请手动填写地点。");a.toast(granted?"请重新打开打卡编辑器使用位置":"可以继续手动填写打卡地点");}}

    @SuppressWarnings("MissingPermission") private void startLocation(){
        locationStatus.setText("正在获取当前位置…");locationManager=(LocationManager)a.getSystemService(Context.LOCATION_SERVICE);listener=new LocationListener(){@Override public void onLocationChanged(Location l){finishLocation(l);}@Override public void onProviderEnabled(String p){}@Override public void onProviderDisabled(String p){}@Override public void onStatusChanged(String p,int s,Bundle b){}};
        try{String provider=locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)?LocationManager.GPS_PROVIDER:LocationManager.NETWORK_PROVIDER;locationManager.requestLocationUpdates(provider,0,0,listener,Looper.getMainLooper());Location last=locationManager.getLastKnownLocation(provider);if(last!=null&&System.currentTimeMillis()-last.getTime()<120000)finishLocation(last);else{timeout=()->{stopLocation();locationStatus.setText("定位超时；请检查定位开关或手动填写地点。");};main.postDelayed(timeout,12000);}}catch(Exception e){stopLocation();locationStatus.setText("暂时无法定位；请手动填写地点。");}
    }
    private void stopLocation(){if(timeout!=null)main.removeCallbacks(timeout);if(locationManager!=null&&listener!=null)try{locationManager.removeUpdates(listener);}catch(Exception ignored){}listener=null;}
    private void finishLocation(Location l){stopLocation();lat=l.getLatitude();lon=l.getLongitude();accuracy=l.getAccuracy();final double queryLat=lat,queryLon=lon;final int token=activeDraft;locationStatus.setText(String.format(Locale.ROOT,"位置已获取 · 精度约 %.0f 米，正在识别地点…",accuracy));ExecutorService e=Executors.newSingleThreadExecutor();e.execute(()->{String name="";try{Geocoder g=new Geocoder(a,Locale.getDefault());List<Address> found=g.getFromLocation(queryLat,queryLon,1);if(found!=null&&!found.isEmpty()){Address ad=found.get(0);name=ad.getFeatureName();if(empty(name))name=ad.getLocality();if(empty(name))name=ad.getAdminArea();}}catch(Exception ignored){}String result=name;e.shutdown();main.post(()->{if(token!=activeDraft||editor==null||!editor.isShowing())return;if(!empty(result)&&place!=null&&place.getText().toString().trim().isEmpty())place.setText(result);if(locationStatus!=null)locationStatus.setText(empty(result)?"位置已获取；地点名称请手动填写。":"位置已获取 · "+result);});});}

    private void generateAndShare(Trip.Checkin c){final String[] path={null};a.runJob("正在生成电子卡片",()->{Bitmap card=drawCard(c);path[0]=media.files.save(card,"cards",92);card.recycle();return path[0];},()->{c.card=path[0];a.save();previewCard(path[0]);});}
    private void previewCard(String relative){try{Bitmap bitmap=media.files.decode(relative,1200);ImageView image=new ImageView(a);image.setAdjustViewBounds(true);image.setImageBitmap(bitmap);ScrollView scroll=new ScrollView(a);scroll.addView(image);AlertDialog d=new AlertDialog.Builder(a).setTitle("电子卡片预览").setView(scroll).setNegativeButton("稍后保存",null).setPositiveButton("保存到相册",(x,w)->exportCard(relative)).create();d.setOnDismissListener(x->{if(!bitmap.isRecycled())bitmap.recycle();});d.show();}catch(Exception e){a.toast("卡片已生成，但暂时无法预览");}}
    private Bitmap drawCard(Trip.Checkin c)throws IOException{int w=1200,h=1600;Bitmap out=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(out);Paint p=new Paint(3);p.setColor(0xffF1EBDD);canvas.drawRect(0,0,w,h,p);if(!empty(c.photo)){Bitmap image=media.files.decode(c.photo,1800);float s=Math.max(w/(float)image.getWidth(),1000f/image.getHeight());float dw=image.getWidth()*s,dh=image.getHeight()*s;canvas.drawBitmap(image,null,new RectF((w-dw)/2,0,(w+dw)/2,dh),p);image.recycle();}p.setColor(0xcc173D34);canvas.drawRect(0,980,w,1600,p);p.setColor(Color.WHITE);p.setTypeface(Typeface.create(Typeface.SANS_SERIF,Typeface.BOLD));p.setTextSize(68);drawText(canvas,p,empty(c.place)?"旅途中的此刻":c.place,80,1095,1040);p.setTypeface(Typeface.create(Typeface.SANS_SERIF,Typeface.NORMAL));p.setTextSize(39);drawWrapped(canvas,p,empty(c.mood)?"这一刻，值得记住":c.mood,80,1190,1040,50,4);p.setTextSize(29);p.setColor(0xffD9E7DF);String coordinates=c.lat==null||c.lon==null?"位置坐标未记录":String.format(Locale.ROOT,"WGS84  %.5f, %.5f  ·  精度约 %.0f 米",c.lat,c.lon,c.accuracy);drawText(canvas,p,coordinates,80,1430,1040);canvas.drawText(displayTime(c.time),80,1480,p);canvas.drawText("麦穗旅序 · 我的旅行打卡",80,1530,p);return out;}
    private static void drawText(Canvas c,Paint p,String text,float x,float y,float max){String s=text==null?"":text;while(p.measureText(s)>max&&s.length()>1)s=s.substring(0,s.length()-1);if(!s.equals(text))s+="…";c.drawText(s,x,y,p);}
    private static void drawWrapped(Canvas c,Paint p,String text,float x,float y,float max,float lineHeight,int maxLines){String s=text==null?"":text;int start=0,line=0;while(start<s.length()&&line<maxLines){int end=start+1;while(end<=s.length()&&p.measureText(s.substring(start,end))<=max)end++;end=Math.max(start+1,end-1);c.drawText(s.substring(start,end),x,y+line*lineHeight,p);start=end;line++;}}

    private void exportCard(String relative){if(relative==null)return;if(Build.VERSION.SDK_INT>=29){Uri uri=null;try{ContentValues v=new ContentValues();v.put(MediaStore.Images.Media.DISPLAY_NAME,"麦穗旅序-打卡-"+System.currentTimeMillis()+".jpg");v.put(MediaStore.Images.Media.MIME_TYPE,"image/jpeg");v.put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/麦穗旅序");v.put(MediaStore.Images.Media.IS_PENDING,1);uri=a.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,v);if(uri==null)throw new IOException();copy(relative,uri);v.clear();v.put(MediaStore.Images.Media.IS_PENDING,0);a.getContentResolver().update(uri,v,null,null);a.toast("电子卡片已保存到相册");}catch(Exception e){if(uri!=null)try{a.getContentResolver().delete(uri,null,null);}catch(Exception ignored){}a.toast("卡片已保存在应用内，但无法写入相册");}}else{pendingExport=relative;Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("image/jpeg").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,"麦穗旅序-打卡.jpg");a.startActivityForResult(i,MediaController.EXPORT_CARD);}}
    boolean onResult(int req,int result,Intent data){if(req!=MediaController.EXPORT_CARD)return false;if(result==Activity.RESULT_OK&&data!=null&&data.getData()!=null&&pendingExport!=null){try{copy(pendingExport,data.getData());a.toast("电子卡片已保存");}catch(Exception e){a.toast("保存电子卡片失败");}}pendingExport=null;return true;}
    private void copy(String relative,Uri dest)throws IOException{try(InputStream in=new FileInputStream(media.files.file(relative));OutputStream out=a.getContentResolver().openOutputStream(dest,"w")){if(out==null)throw new IOException();byte[] b=new byte[16384];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}}
    void saveState(Bundle b){if(pendingExport!=null)b.putString("checkin.export",pendingExport);b.putString("checkin.photo",photo);if(lat!=null)b.putDouble("checkin.lat",lat);if(lon!=null)b.putDouble("checkin.lon",lon);b.putFloat("checkin.accuracy",accuracy);if(editor!=null&&editor.isShowing()){b.putBoolean("checkin.editing",true);b.putString("checkin.place",place.getText().toString());b.putString("checkin.mood",mood.getText().toString());b.putString("checkin.time",time.getText().toString());}}
    void restoreState(Bundle b){pendingExport=b.getString("checkin.export");photo=b.getString("checkin.photo","");if(b.containsKey("checkin.lat"))lat=b.getDouble("checkin.lat");if(b.containsKey("checkin.lon"))lon=b.getDouble("checkin.lon");accuracy=b.getFloat("checkin.accuracy");if(b.getBoolean("checkin.editing")){String savedPlace=b.getString("checkin.place",""),savedMood=b.getString("checkin.mood","开心"),savedTime=b.getString("checkin.time",LocalDateTime.now().withSecond(0).withNano(0).toString());main.post(()->{if(a.isFinishing()||a.isDestroyed())return;editNew();place.setText(savedPlace);mood.setText(savedMood);time.setText(savedTime);if(lat!=null&&lon!=null)locationStatus.setText(String.format(Locale.ROOT,"位置已获取 · %.5f, %.5f",lat,lon));});}}
    static String displayTime(String iso){try{return LocalDateTime.parse(iso).format(DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm"));}catch(Exception e){return iso==null?"":iso;}}
    static boolean empty(String s){return s==null||s.trim().isEmpty();}
}
