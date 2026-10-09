package cn.lvxu.travel;
import android.app.Instrumentation;
import android.graphics.*;
import android.location.*;
import android.os.SystemClock;
import android.view.*;
import android.widget.*;
import com.baidu.mapapi.map.*;
import com.baidu.mapapi.model.LatLng;
import java.lang.reflect.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** Opt-in actual POI + paired map checks; never prints provider credentials or response bodies. */
final class NearbyHeatLiveChecks {
 static int run(Instrumentation in,MainActivity host,BaiduHeatmapUi ui,File evidence)throws Exception{
  int n=0;Object tabs=field(ui,"categoryTabs");n+=check(tabs!=null&&((LinearLayout)tabs).getChildCount()==6&&"人文".equals(field(ui,"category")),"favorites first, default human culture category");
  for(int i=0;i<80;i++){if(!((ArrayList<?>)field(ui,"places")).isEmpty())break;SystemClock.sleep(250);}
  ArrayList<NearbyPlace> list=(ArrayList<NearbyPlace>)field(ui,"places");n+=check(!list.isEmpty(),"actual current-city places loaded");
  n+=check(((String)field(ui,"city")).contains("北京")&&list.stream().allMatch(p->p.city.contains("北京")),"query city from current position, not active travel");
  Method load=method("loadNearby");in.runOnMainSync(()->{try{for(int i=0;i<((LinearLayout)tabs).getChildCount();i++){android.widget.TextView tab=(android.widget.TextView)((LinearLayout)tabs).getChildAt(i);if("博物馆".contentEquals(tab.getText()))tab.performClick();}}catch(Exception e){throw new RuntimeException(e);}});
  for(int i=0;i<80;i++){if(!((ArrayList<?>)field(ui,"places")).isEmpty())break;SystemClock.sleep(250);}
  list=(ArrayList<NearbyPlace>)field(ui,"places");n+=check(!list.isEmpty()&&list.get(0).category.equals("博物馆"),"museum category returns actual POIs");
  final NearbyPlace focus=list.get(0);Method focusing=method("focus",NearbyPlace.class);in.runOnMainSync(()->{try{focusing.invoke(ui,focus);}catch(Exception e){throw new RuntimeException(e);}});SystemClock.sleep(6000);in.waitForIdleSync();
  n+=check(field(ui,"map")!=null&&field(ui,"view")!=null&&field(ui,"sampler")!=null&&!hasField("referenceMap")&&!hasField("referenceView")&&!hasField("referenceRendered"),"heatmap uses one map renderer and sampler");
  n+=check(focus.heat==0||focus.freshHeat()&&focus.heat>0&&focus.heat<=5,"actual heat is bounded or honestly unknown: name="+focus.name+", level="+focus.heat);
  BaiduMap map=(BaiduMap)field(ui,"map");MapStatus before=map.getMapStatus();
  double[] converted=GeoMath.wgs(focus.lat,focus.lon,"GCJ02");double[] back=GeoMath.wgs(before.target.latitude,before.target.longitude,"BD09");
  n+=check(GeoMath.meters(converted[0],converted[1],back[0],back[1])<50,"POI click focuses converted coordinate");
  in.runOnMainSync(()->{try{set(ui,"cameraPending",true);View frame=(View)field(ui,"mapFrame");long time=SystemClock.uptimeMillis();MotionEvent down=MotionEvent.obtain(time,time,MotionEvent.ACTION_DOWN,50,50,0),up=MotionEvent.obtain(time,time+1,MotionEvent.ACTION_UP,50,50,0);frame.dispatchTouchEvent(down);frame.dispatchTouchEvent(up);down.recycle();up.recycle();}catch(Exception e){throw new RuntimeException(e);}});
  n+=check(Boolean.FALSE.equals(field(ui,"cameraPending")),"touch cancels pending auto centering");MapStatus browsing=map.getMapStatus();
  Method offer=method("offer",Location.class,boolean.class);in.runOnMainSync(()->{try{LocationSession session=new LocationSession(System.currentTimeMillis());session.start(true,false,false);set(ui,"session",session);set(ui,"locating",true);Location location=new Location(LocationManager.GPS_PROVIDER);location.setLatitude(39.915);location.setLongitude(116.404);location.setAccuracy(1);location.setTime(System.currentTimeMillis());offer.invoke(ui,location,false);}catch(Exception e){throw new RuntimeException(e);}});SystemClock.sleep(500);
  MapStatus after=map.getMapStatus();n+=check(GeoMath.meters(browsing.target.latitude,browsing.target.longitude,after.target.latitude,after.target.longitude)<2,"later device fix does not recenter browsed map");
  int retainedHeat=focus.heat;long retainedAt=focus.heatAt;Method browsingMethod=method("browsing");in.runOnMainSync(()->{try{browsingMethod.invoke(ui);}catch(Exception e){throw new RuntimeException(e);}});n+=check(focus.heat==retainedHeat&&focus.heatAt==retainedAt,"panning retains cached heat; unknown remains unknown");
  final Bitmap[] snapshot=new Bitmap[1];CountDownLatch ready=new CountDownLatch(1);
  in.runOnMainSync(()->map.snapshot(b->{snapshot[0]=b;ready.countDown();}));boolean snapshots=ready.await(10,TimeUnit.SECONDS);
  n+=check(snapshots&&snapshot[0]!=null,"single registered SDK renderer snapshot available");
  if(snapshot[0]!=null){try(FileOutputStream out=new FileOutputStream(new File(evidence,"heat-single-map.png"))){snapshot[0].compress(Bitmap.CompressFormat.PNG,100,out);}snapshot[0].recycle();}
  Bitmap screen=in.getUiAutomation().takeScreenshot();try(FileOutputStream out=new FileOutputStream(new File(evidence,"heat-browser-final.png"))){screen.compress(Bitmap.CompressFormat.PNG,100,out);}screen.recycle();
  in.runOnMainSync(()->{try{android.view.View body=(android.view.View)field(ui,"body");if(body.getParent() instanceof ScrollView)((ScrollView)body.getParent()).scrollTo(0,host.dp(540));}catch(Exception e){throw new RuntimeException(e);}});in.waitForIdleSync();SystemClock.sleep(900);Bitmap cards=in.getUiAutomation().takeScreenshot();try(FileOutputStream out=new FileOutputStream(new File(evidence,"heat-browser-cards.png"))){cards.compress(Bitmap.CompressFormat.PNG,100,out);}cards.recycle();
  BaiduHeatSampler sampling=(BaiduHeatSampler)field(ui,"sampler");final MapStatus[] retained=new MapStatus[1];
  in.runOnMainSync(()->{try{View body=(View)field(ui,"body");((ScrollView)body.getParent()).scrollTo(0,0);}catch(Exception e){throw new RuntimeException(e);}sampling.cancel();retained[0]=map.getMapStatus();sampling.start(Collections.singletonList(focus),true);});
  for(int i=0;i<80&&sampling.busy();i++)SystemClock.sleep(250);
  n+=check(!sampling.busy()&&BaiduHeatSampler.same(retained[0],map.getMapStatus())&&map.isBaiduHeatMapEnabled(),"serial batch sampling restores visible camera and city heat layer");
  in.runOnMainSync(()->sampling.start(Collections.singletonList(focus),true));SystemClock.sleep(300);in.runOnMainSync(sampling::cancel);
  n+=check(!sampling.busy()&&BaiduHeatSampler.same(retained[0],map.getMapStatus()),"cancel batch restores original viewport");
  RegionSelection region=new RegionSelection("甘肃省","兰州市","城关区","620102","district",36.06,103.83);
  NearbyPlacesService.Result regional=NearbyPlacesService.searchRegion(host,"博物馆",region,false);
  n+=check(!regional.places.isEmpty()&&regional.places.stream().allMatch(p->p.city.contains("兰州")),"live district POI scope keeps actual Lanzhou city");
  RegionSelection resolved=RegionService.resolve(host,"甘肃省","兰州市","城关区");n+=check("620102".equals(resolved.adcode)&&resolved.hasCenter(),"live province city district selection resolves correct center");
  RegionSelection located=RegionService.locate(host,new double[]{39.915,116.404});
  n+=check(located.province.contains("北京")&&!located.district.isEmpty(),"live reverse geocode resolves province city district");return n;
 }
 private static Object field(Object o,String name)throws Exception{return BaiduHeatmapUiTest.field(o,name);}
 private static boolean hasField(String name){try{BaiduHeatmapUi.class.getDeclaredField(name);return true;}catch(NoSuchFieldException missing){return false;}}
 private static void set(Object o,String name,Object value)throws Exception{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);f.set(o,value);}
 private static Method method(String name,Class<?>... args)throws Exception{Method m=BaiduHeatmapUi.class.getDeclaredMethod(name,args);m.setAccessible(true);return m;}
 private static int check(boolean yes,String label){if(!yes)throw new AssertionError(label);return 1;}
}
