package cn.lvxu.travel;
import android.app.*;
import android.content.*;
import android.os.*;
import android.graphics.Rect;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Real host edits and small-screen accessibility regressions. */
final class AuditHostUiTest {
 static int run(Instrumentation in)throws Exception{
  Context c=in.getTargetContext();TripStore store=new TripStore(c);ArrayList<Trip> original=store.read();AppPrefs prefs=new AppPrefs(c);org.json.JSONObject old=prefs.exportJson();TestStartupGuard guard=new TestStartupGuard(c);MainActivity host=null;int n=0;
  try{prefs.setShowCompletedTrips(true);prefs.setTheme("light");host=(MainActivity)in.startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP));MainActivity a=host;in.waitForIdleSync();
   Trip trip=new Trip();trip.title="兰州审计旅行";trip.city="兰州市";trip.favorite=true;trip.pinned=true;trip.start=java.time.LocalDate.now().toString();trip.days=3;Trip.Stop stop=new Trip.Stop();stop.name="甘肃省博物馆";trip.stops.add(stop);Trip archived=new Trip();archived.title="不计入首页的归档";archived.city="兰州";archived.archived=true;
   in.runOnMainSync(()->{a.trips.clear();a.trips.add(trip);a.trips.add(archived);a.active=trip;a.page=0;a.render();a.save();});in.waitForIdleSync();
   capture(in,"首页");n+=check(find(a.body,"我的旅行 · 1")!=null,"home count includes displayed trips only");n+=check(find(a.root,"设置",true)!=null,"settings entry named in Chinese");
   View card=find(a.root,"打开："+trip.title,true);Rect cardBounds=new Rect();n+=check(card!=null&&card.getGlobalVisibleRect(cardBounds),"travel title appears before inspiration hero on small screen");
   in.runOnMainSync(()->TripEditorUi.show(a,trip));in.waitForIdleSync();capture(in,"旅行表单");click(in,"保存");in.waitForIdleSync();Trip edited=a.store.read().stream().filter(t->t.id.equals(trip.id)).findFirst().orElseThrow();
   n+=check(edited.favorite&&edited.pinned&&!edited.archived,"unchanged actual edit keeps favorite pin and archive flags");
   in.runOnMainSync(()->{a.page=1;a.day=0;a.mapMode=false;a.render();});in.waitForIdleSync();capture(in,"行程");View name=find(a.body,stop.name);Rect stopBounds=new Rect();n+=check(name!=null&&name.getGlobalVisibleRect(stopBounds),"first stop visible without scrolling");
   View add=find(a.root,"＋ 添加地点");Rect addBounds=new Rect();n+=check(add!=null&&add.getGlobalVisibleRect(addBounds),"add remains visible outside itinerary scroll");n+=check(find(a.root,"行程工具")!=null,"tools available beside persistent add");
   in.runOnMainSync(()->{a.page=0;a.render();ScrollView scroll=(ScrollView)a.body.getParent();scroll.scrollTo(0,120);});in.waitForIdleSync();ScrollView before=(ScrollView)a.body.getParent();int y=before.getScrollY();in.runOnMainSync(a::render);SystemClock.sleep(100);in.waitForIdleSync();n+=check(((ScrollView)a.body.getParent()).getScrollY()==y,"same-page rerender retains scroll");
   in.runOnMainSync(()->{prefs.setTheme("dark");a.page=1;a.render();});in.waitForIdleSync();capture(in,"行程-深色");
   AtomicInteger cleaned=new AtomicInteger();in.runOnMainSync(()->a.runJob("测试失败清理",()->{throw new java.io.IOException("测试失败");},()->{throw new AssertionError("must not invoke success");},cleaned::incrementAndGet));await(in,()->cleaned.get()==1);n+=check(cleaned.get()==1,"failure cleanup exactly once");
   in.runOnMainSync(a::finish);in.waitForIdleSync();in.runOnMainSync(()->a.runJob("已关闭",()->{throw new AssertionError("destroyed job must not run");},null,cleaned::incrementAndGet));n+=check(cleaned.get()==2,"destroyed host still releases prepared resources");
   return n;
  }finally{store.save(original);store.close();prefs.importJson(old);guard.close();if(host!=null){MainActivity a=host;in.runOnMainSync(a::finish);}}
 }
 static View find(View v,String s){return find(v,s,false);}static View find(View v,String s,boolean desc){if(desc?s.contentEquals(v.getContentDescription()==null?"":v.getContentDescription()):v instanceof TextView&&s.contentEquals(((TextView)v).getText()))return v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){View x=find(g.getChildAt(i),s,desc);if(x!=null)return x;}}return null;}
 static void click(Instrumentation in,String label)throws Exception{long end=SystemClock.uptimeMillis()+4000;while(SystemClock.uptimeMillis()<end){in.waitForIdleSync();AccessibilityNodeInfo root=in.getUiAutomation().getRootInActiveWindow();if(root!=null)for(AccessibilityNodeInfo node:root.findAccessibilityNodeInfosByText(label))if(label.contentEquals(node.getText())&&node.isClickable()&&node.performAction(AccessibilityNodeInfo.ACTION_CLICK))return;SystemClock.sleep(50);}throw new AssertionError("missing clickable "+label);}
 interface Ready{boolean ok();}static void await(Instrumentation in,Ready f)throws Exception{long end=SystemClock.uptimeMillis()+6000;while(SystemClock.uptimeMillis()<end){in.waitForIdleSync();if(f.ok())return;SystemClock.sleep(40);}throw new AssertionError("cleanup timed out");}
 static void capture(Instrumentation in,String name)throws Exception{SystemClock.sleep("首页".equals(name)?14000:1000);in.waitForIdleSync();android.graphics.Bitmap shot=in.getUiAutomation().takeScreenshot();if(shot==null)return;float scale=in.getTargetContext().getResources().getConfiguration().fontScale;java.io.File dir=new java.io.File(in.getTargetContext().getExternalFilesDir(null),"audit-fixes-evidence");dir.mkdirs();try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(dir,name+"-"+scale+".png"))){shot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}shot.recycle();}
 static int check(boolean ok,String label){if(!ok)throw new AssertionError(label);return 1;}
}
