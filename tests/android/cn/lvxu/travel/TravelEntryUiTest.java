package cn.lvxu.travel;
import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import android.view.accessibility.AccessibilityNodeInfo;
import org.json.JSONObject;
import java.time.LocalDate;
import java.util.*;
/** Creation-to-itinerary workflow and homepage preference regression on a disposable emulator. */
final class TravelEntryUiTest {
 static int run(Instrumentation in)throws Exception{
  Context c=in.getTargetContext();AppPrefs prefs=new AppPrefs(c);JSONObject oldPrefs=prefs.exportJson();TripStore store=new TripStore(c);ArrayList<Trip> saved=store.read();TestStartupGuard startup=new TestStartupGuard(c);MainActivity activity=null;int n=0;
  try{prefs.setTutorialDone(true);prefs.setLastUpdateDay(LocalDate.now().toString());prefs.setDefaultHome("home");prefs.setShowCompletedTrips(true);
   activity=(MainActivity)in.startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP));final MainActivity a=activity;in.waitForIdleSync();
   in.runOnMainSync(()->{a.trips.clear();a.active=null;a.page=0;a.render();TripEditorUi.show(a,null);});in.waitForIdleSync();UiAutomation ui=in.getUiAutomation();
   set(ui,"旅行名称","工作流测试旅行");click(ui,"选择目的地城市");click(ui,"河北省");click(ui,"石家庄市");click(ui,"更多信息（选填） ▾");set(ui,"同行人（选填）","小麦，小穗; 小麦、阿青");click(ui,"保存");wait(ui,"旅行创建成功");
   n+=check(a.trips.size()==1&&a.active.city.equals("石家庄市"),"offline city selection saved in new travel");n+=check(a.active.companions.equals("小麦、小穗、阿青"),"creation normalizes companion names");n+=check(a.active.stops.isEmpty(),"creation prompt does not invent itinerary stops");click(ui,"安排行程");wait(ui,"添加地点");
   n+=check(a.page==1&&a.day==0,"creation plan action opens first travel day");set(ui,"地点名称","午夜前散步");set(ui,"开始时间","23:30");set(ui,"停留时长（分钟）","90");
   n+=check(find(ui.getRootInActiveWindow(),"搜索地点")!=null,"stop name row exposes compact search action");n+=check(find(ui.getRootInActiveWindow(),"导入地点链接")==null&&find(ui.getRootInActiveWindow(),"搜索并添加地点")==null,"retired standalone import/search actions absent");
   click(ui,"保存并继续新增");wait(ui,"添加地点");n+=check(a.active.stops.size()==1&&a.active.stops.get(0).name.equals("午夜前散步"),"continue saves first stop once");
   n+=check(field(ui,"开始时间").getText().toString().equals("01:00"),"continued editor starts at previous stop end after midnight");click(ui,"取消");n+=check(a.active.stops.size()==1,"cancel continued entry leaves no blank second record");
   Trip ended=trip("已结束边界旅行",LocalDate.now().minusDays(2),2),today=trip("结束当天边界旅行",LocalDate.now().minusDays(1),2),future=trip("将来边界旅行",LocalDate.now().plusDays(1),2),archived=trip("归档边界旅行",LocalDate.now().minusDays(3),1);archived.archived=true;
   in.runOnMainSync(()->{a.trips.clear();a.trips.addAll(Arrays.asList(ended,today,future,archived));a.active=today;a.page=0;a.render();});in.waitForIdleSync();n+=check(text(a.root,ended.title)!=null,"completed travel visible when preference on");
   in.runOnMainSync(()->{prefs.setShowCompletedTrips(false);a.render();});in.waitForIdleSync();n+=check(text(a.root,ended.title)==null,"completed travel hidden when preference off");n+=check(text(a.root,today.title)!=null&&text(a.root,future.title)!=null,"end-date today and future travel remain visible");n+=check(text(a.root,archived.title)==null,"archived travel remains separate");n+=check(!new AppPrefs(c).showCompletedTrips(),"completed visibility preference persists");return n;
  }catch(Throwable failure){android.graphics.Bitmap shot=in.getUiAutomation().takeScreenshot();if(shot!=null){try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(c.getExternalFilesDir(null),"travel-entry-failure.png"))){shot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}shot.recycle();}ArrayList<AccessibilityNodeInfo> nodes=new ArrayList<>();flatten(in.getUiAutomation().getRootInActiveWindow(),nodes);StringBuilder labels=new StringBuilder();for(AccessibilityNodeInfo node:nodes)labels.append("[").append(node.getText()).append("|").append(node.getClassName()).append("|editable=").append(node.isEditable()).append("|scroll=").append(node.isScrollable()).append("]");throw new AssertionError(failure+" UI="+labels,failure);}finally{store.save(saved);store.close();if(activity!=null){MainActivity closing=activity;in.runOnMainSync(closing::finish);}prefs.importJson(oldPrefs);startup.close();}
 }
 private static Trip trip(String title,LocalDate start,int days){Trip t=new Trip();t.title=title;t.city="测试城";t.start=start.toString();t.days=days;t.normalize();return t;}
 private static int check(boolean yes,String label){if(!yes)throw new AssertionError(label);return 1;}
 private static View text(View root,String value){if(root instanceof TextView&&value.contentEquals(((TextView)root).getText()))return root;if(root instanceof ViewGroup){ViewGroup g=(ViewGroup)root;for(int i=0;i<g.getChildCount();i++){View hit=text(g.getChildAt(i),value);if(hit!=null)return hit;}}return null;}
 private static void set(UiAutomation ui,String label,String value)throws Exception{AccessibilityNodeInfo node=field(ui,label);Bundle args=new Bundle();args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,value);if(!node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,args))throw new AssertionError("Cannot enter "+label);SystemClock.sleep(100);}
 private static AccessibilityNodeInfo field(UiAutomation ui,String label)throws Exception{for(int attempt=0;attempt<15;attempt++){ArrayList<AccessibilityNodeInfo> nodes=new ArrayList<>();flatten(ui.getRootInActiveWindow(),nodes);boolean after=false;for(AccessibilityNodeInfo node:nodes){if(label.contentEquals(node.getText()==null?"":node.getText()))after=true;else if(after&&node.isEditable())return node;}scroll(ui);SystemClock.sleep(100);}throw new AssertionError("Missing field "+label);}
 private static void click(UiAutomation ui,String value)throws Exception{AccessibilityNodeInfo node=wait(ui,value);AccessibilityNodeInfo direct=clickable(ui.getRootInActiveWindow(),value);if(direct!=null)node=direct;while(node!=null&&!node.isClickable())node=node.getParent();if(node==null||!node.performAction(AccessibilityNodeInfo.ACTION_CLICK))throw new AssertionError("Cannot click "+value);SystemClock.sleep(150);}
 private static AccessibilityNodeInfo wait(UiAutomation ui,String value)throws Exception{for(int attempt=0;attempt<30;attempt++){AccessibilityNodeInfo hit=find(ui.getRootInActiveWindow(),value);if(hit!=null)return hit;if(attempt>2)scroll(ui);SystemClock.sleep(100);}throw new AssertionError("Missing UI "+value);}
 private static void flatten(AccessibilityNodeInfo root,List<AccessibilityNodeInfo> out){if(root==null)return;out.add(root);for(int i=0;i<root.getChildCount();i++)flatten(root.getChild(i),out);}
 private static AccessibilityNodeInfo find(AccessibilityNodeInfo root,String value){if(root==null)return null;if(value.contentEquals(root.getText()==null?"":root.getText())||value.contentEquals(root.getContentDescription()==null?"":root.getContentDescription()))return root;for(int i=0;i<root.getChildCount();i++){AccessibilityNodeInfo hit=find(root.getChild(i),value);if(hit!=null)return hit;}return null;}
 private static AccessibilityNodeInfo clickable(AccessibilityNodeInfo node,String value){if(node==null)return null;if(node.isClickable()&&(value.contentEquals(node.getText()==null?"":node.getText())||value.contentEquals(node.getContentDescription()==null?"":node.getContentDescription())))return node;for(int i=0;i<node.getChildCount();i++){AccessibilityNodeInfo hit=clickable(node.getChild(i),value);if(hit!=null)return hit;}return null;}
 private static void scroll(UiAutomation ui){ArrayList<AccessibilityNodeInfo> nodes=new ArrayList<>();flatten(ui.getRootInActiveWindow(),nodes);for(AccessibilityNodeInfo node:nodes)if(node.isScrollable()){node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);return;}}
}
