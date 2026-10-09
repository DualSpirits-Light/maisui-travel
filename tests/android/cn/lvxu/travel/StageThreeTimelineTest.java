package cn.lvxu.travel;
import android.app.Instrumentation;
import android.content.*;
import android.graphics.*;
import android.os.SystemClock;
import android.view.*;
import android.widget.*;
import org.json.JSONObject;
import java.io.*;
import java.util.*;

final class StageThreeTimelineTest {
 static int run(Instrumentation in)throws Exception {
  Context c=in.getTargetContext();AppPrefs prefs=new AppPrefs(c);JSONObject oldPrefs=prefs.exportJson();MainActivity activity=null;ArrayList<Trip> saved=null;int n=0;
  try {
   prefs.setTutorialDone(true);prefs.setLastUpdateDay(java.time.LocalDate.now().toString());prefs.resetColors();prefs.setTheme("light");
   activity=(MainActivity)in.startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP));MainActivity a=activity;in.waitForIdleSync();saved=a.store.read();
   Trip trip=new Trip();trip.title="苏州慢游";trip.city="苏州";trip.start="2026-09-27";trip.days=2;trip.normalize();
   Trip.Stop first=stop("平江路沿河漫步与老街巷探访，长标题完整换行显示","23:30",90,"步行");first.address="苏州市姑苏区平江路";first.openingHours="全天";first.note="晚间散步，留意末班交通。";
   Trip.Stop second=stop("河畔夜景","23:45",30,"公交");trip.stops.add(first);trip.stops.add(second);
   for(String theme:new String[]{"light","dark"}){
    in.runOnMainSync(()->{prefs.setTheme(theme);a.trips.clear();a.trips.add(trip);a.active=trip;a.page=1;a.day=0;a.mapMode=false;a.render();});in.waitForIdleSync();
    n+=check(find(a.body,"23:30 — 次日 01:00")!=null,"cross midnight time rendered");n+=check(find(a.body,"9月27日 周日")!=null,"date and weekday rendered");
    n+=check(find(a.body,"公交抵达 · 时间冲突，前一安排尚未结束")!=null,"destination transport and conflict rendered");
    n+=check(find(a.body,"与其他地点的安排时间重叠，请检查。")!=null,"overlap visible");n+=check(PlaceDetailsContent.rows(trip,first).stream().anyMatch(r->first.address.equals(r.value))&&PlaceDetailsContent.rows(trip,first).stream().anyMatch(r->"全天".equals(r.value))&&find(a.body,first.note)!=null,"metadata retained");
    StopReorderLayout list=(StopReorderLayout)type(a.body,StopReorderLayout.class);n+=check(list.getChildCount()==2,"only stop rows are direct reorder children");
    n+=check(find(a.root,"行程工具")!=null&&find(a.body,"编辑")!=null,"quick look and edit available");
    in.runOnMainSync(()->((ScrollView)a.body.getParent()).scrollTo(0,list.getTop()-a.dp(10)));in.waitForIdleSync();capture(in,"timeline-"+theme+".png");
    TextView title=(TextView)find(a.body,first.name);n+=check(title.getLineCount()>1&&title.getEllipsize()==null,"long title wraps without truncation");
   }
   in.runOnMainSync(()->{a.day=1;a.render();});in.waitForIdleSync();n+=check(find(a.body,"今天，想去哪里？")!=null,"empty day guidance");
   // Use compact destinations for a real injected long press drag; persistence is restored below.
   first.name="地点 A";first.address="";first.openingHours="";first.note="";first.time="09:00";first.duration=30;second.name="地点 B";second.time="10:00";
   in.runOnMainSync(()->{a.day=0;a.render();});in.waitForIdleSync();StopReorderLayout list=(StopReorderLayout)type(a.body,StopReorderLayout.class);
   in.runOnMainSync(()->((ScrollView)a.body.getParent()).scrollTo(0,list.getTop()));in.waitForIdleSync();SystemClock.sleep(250);
   Rect top=bounds(list.getChildAt(0)),bottom=bounds(list.getChildAt(1));Rect scrollVisible=new Rect(),listVisible=new Rect();((ScrollView)a.body.getParent()).getGlobalVisibleRect(scrollVisible);list.getGlobalVisibleRect(listVisible);capture(in,"drag-before.png");
   int x=top.left+a.dp(48),y=top.top+top.height()/2-a.dp(20);n+=check(listVisible.contains(x,y)&&scrollVisible.contains(x,y)&&y<top.top+top.height()/2,"drag destination is visible inside the first stop above its insertion midpoint");
   gesture(in,bottom.left+a.dp(48),bottom.top+a.dp(50),x,y);SystemClock.sleep(400);in.waitForIdleSync();
   capture(in,"drag-after.png");n+=check(trip.onDay(0).get(0).id.equals(second.id),"touch drag reorders entire stop rows; first="+top+", second="+bottom+", destination="+x+","+y+", visible="+listVisible+", actual="+trip.onDay(0).get(0).name);n+=check(first.time.equals("09:00")&&second.time.equals("10:00"),"drag preserves time");
   n+=check(a.store.read().get(0).onDay(0).get(0).id.equals(second.id),"drag order persisted");
   return n;
  } finally {
   if(activity!=null){MainActivity a=activity;if(saved!=null)a.store.save(saved);in.runOnMainSync(a::finish);}prefs.importJson(oldPrefs);
  }
 }
 static Trip.Stop stop(String name,String time,int duration,String mode){Trip.Stop s=new Trip.Stop();s.name=name;s.time=time;s.duration=duration;s.mode=mode;return s;}
 static int check(boolean ok,String label){if(!ok)throw new AssertionError(label);return 1;}
 static View find(View v,String text){if(v instanceof TextView&&text.contentEquals(((TextView)v).getText()))return v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){View x=find(g.getChildAt(i),text);if(x!=null)return x;}}return null;}
 static View type(View v,Class<?> type){if(type.isInstance(v))return v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){View x=type(g.getChildAt(i),type);if(x!=null)return x;}}return null;}
 static Rect bounds(View v){int[] p=new int[2];v.getLocationOnScreen(p);return new Rect(p[0],p[1],p[0]+v.getWidth(),p[1]+v.getHeight());}
 static void gesture(Instrumentation in,float x,float y,float ex,float ey){long down=SystemClock.uptimeMillis();send(in,down,MotionEvent.ACTION_DOWN,x,y);SystemClock.sleep(ViewConfiguration.getLongPressTimeout()+180);for(int i=1;i<=16;i++){send(in,down,MotionEvent.ACTION_MOVE,x+(ex-x)*i/16,y+(ey-y)*i/16);SystemClock.sleep(18);}send(in,down,MotionEvent.ACTION_UP,ex,ey);}
 static void send(Instrumentation in,long down,int action,float x,float y){MotionEvent e=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x,y,0);e.setSource(InputDevice.SOURCE_TOUCHSCREEN);try{if(!in.getUiAutomation().injectInputEvent(e,true))throw new AssertionError("touch injection failed");}finally{e.recycle();}}
 static void capture(Instrumentation in,String name)throws Exception{SystemClock.sleep(150);Bitmap b=in.getUiAutomation().takeScreenshot();if(b==null)throw new AssertionError("screenshot unavailable");File dir=new File(in.getTargetContext().getExternalFilesDir(null),"stage3-evidence");dir.mkdirs();try(FileOutputStream out=new FileOutputStream(new File(dir,name))){b.compress(Bitmap.CompressFormat.PNG,100,out);}finally{b.recycle();}}
}
