package cn.lvxu.travel;

import android.app.*;
import android.content.*;
import android.view.accessibility.AccessibilityNodeInfo;
import java.lang.reflect.*;

/** Offline marker-selection business behavior; does not initialize or authenticate the native SDK. */
final class AmapStopSelectionUiTest {
 static int run(Instrumentation in)throws Exception {
  Context c=in.getTargetContext();TestStartupGuard startup=new TestStartupGuard(c);
  MainActivity a=(MainActivity)in.startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));in.waitForIdleSync();
  int checks=0;
  try {
   Method select=method("selectStop"),focus=method("focusStop");
   Trip trip=Trip.demo();Trip.Stop first=trip.stops.get(0),second=trip.stops.get(1);first.name="同名地点";second.name="同名地点";second.note="第二个同名地点的备注";
   final AmapUi[] map={null};in.runOnMainSync(()->{a.trips.clear();a.trips.add(trip);a.active=trip;a.day=0;a.page=1;map[0]=new AmapUi(a);});
   final boolean[] result={false};in.runOnMainSync(()->result[0]=call(select,map[0],second.id));in.waitForIdleSync();
   check(result[0]&&second.id.equals(selected(a)),"marker selection must use stable ID rather than duplicate name");checks++;
   check(waitMenuVisible(in,map[0],"地点详情","导航到这里","编辑地点"),"selected marker exposes visible details, navigation and edit");checks++;
   clickMenu(in,map[0],"地点详情");check(waitText(in,"第二个同名地点的备注"),"marker details resolved wrong same-name stop");checks++;
   in.getUiAutomation().performGlobalAction(1);in.waitForIdleSync();
   // A changed live stop must be looked up again by ID, not held as a stale object.
   Trip.Stop replacement=Trip.Stop.from(second.json());replacement.note="修改后的地点备注";
   in.runOnMainSync(()->{trip.stops.set(1,replacement);call(select,map[0],second.id);});in.waitForIdleSync();clickMenu(in,map[0],"地点详情");in.waitForIdleSync();
   check(waitText(in,"修改后的地点备注"),"selection kept stale Stop object");checks++;
   in.getUiAutomation().performGlobalAction(1);in.waitForIdleSync();
   in.runOnMainSync(()->call(select,map[0],second.id));in.waitForIdleSync();clickMenu(in,map[0],"编辑地点");in.waitForIdleSync();
   Field originalId=MainActivity.class.getDeclaredField("stopDraftOriginal");originalId.setAccessible(true);check(second.id.equals(originalId.get(a)),"marker edit opened wrong same-name stop");checks++;
   Field stopDialog=MainActivity.class.getDeclaredField("stopDraftDialog");stopDialog.setAccessible(true);AlertDialog editor=(AlertDialog)stopDialog.get(a);in.runOnMainSync(editor::dismiss);in.waitForIdleSync();
   in.runOnMainSync(()->result[0]=call(select,map[0],"missing-stop"));check(!result[0],"unknown marker ID selected a different stop");checks++;
   Trip.Stop absent=trip.stops.get(2);absent.lat=null;absent.lon=null;
   in.runOnMainSync(()->result[0]=call(select,map[0],absent.id));check(!result[0],"unlocated stop fabricated a marker selection");checks++;
   in.runOnMainSync(()->result[0]=call(focus,map[0],second.id));check(!result[0],"focus claimed to move a map that was not initialized");checks++;
   Trip other=Trip.demo();in.runOnMainSync(()->{a.trips.add(other);a.active=other;result[0]=call(select,map[0],second.id);});check(!result[0],"stale map handled a marker after travel changed");checks++;
   in.runOnMainSync(()->a.active=trip);
   in.runOnMainSync(()->{a.day=1;result[0]=call(select,map[0],second.id);});check(!result[0],"stale map selected a stop after day changed");checks++;
   in.runOnMainSync(()->{a.day=0;trip.stops.remove(replacement);result[0]=call(select,map[0],second.id);});check(!result[0],"deleted stop opened old marker actions");checks++;
   in.runOnMainSync(()->{map[0].destroy();result[0]=call(select,map[0],first.id);});check(!result[0],"destroyed renderer handled marker action");checks++;
   return checks;
  } finally {in.runOnMainSync(a::finish);in.waitForIdleSync();startup.close();}
 }
 private static Method method(String name){try{Method m=AmapUi.class.getDeclaredMethod(name,String.class);m.setAccessible(true);return m;}catch(NoSuchMethodException e){throw new AssertionError("Itinerary marker cannot link to its stop: missing "+name,e);}}
 private static boolean call(Method m,AmapUi map,String id){try{return (Boolean)m.invoke(map,id);}catch(Exception e){throw new AssertionError("Marker selection failed",e);}}
 private static String selected(MainActivity a)throws Exception{Field f=MainActivity.class.getDeclaredField("selectedStopId");f.setAccessible(true);return (String)f.get(a);}
 private static void check(boolean value,String name){if(!value)throw new AssertionError(name);}
 private static AccessibilityNodeInfo find(Instrumentation in,String text){return find(in.getUiAutomation().getRootInActiveWindow(),text);}
 private static AccessibilityNodeInfo find(AccessibilityNodeInfo node,String text){if(node==null)return null;if(text.contentEquals(node.getText()==null?"":node.getText())||text.contentEquals(node.getContentDescription()==null?"":node.getContentDescription()))return node;for(int i=0;i<node.getChildCount();i++){AccessibilityNodeInfo hit=find(node.getChild(i),text);if(hit!=null)return hit;}return null;}
 private static void clickMenu(Instrumentation in,AmapUi map,String text)throws Exception{
  if(!waitMenuVisible(in,map,text))throw new AssertionError("Missing visible marker menu "+text);
  AlertDialog menu=menu(map);
  // These are native ListView items, whose row TextView need not advertise isClickable.
  // Exercise the actual item listener, following StageTwoUiTests' native selection pattern.
  in.runOnMainSync(()->{android.widget.ListView list=menu.getListView();if(list==null)throw new AssertionError("Marker menu has no action list");for(int i=0;i<list.getAdapter().getCount();i++)if(text.equals(String.valueOf(list.getAdapter().getItem(i)))){android.view.View row=list.getChildAt(i-list.getFirstVisiblePosition());if(!list.performItemClick(row,i,list.getAdapter().getItemId(i)))throw new AssertionError("Marker action listener missing "+text);return;}throw new AssertionError("Marker menu action missing "+text);});
  in.waitForIdleSync();
 }
 private static AlertDialog menu(AmapUi map)throws Exception{Field f=AmapUi.class.getDeclaredField("stopMenu");f.setAccessible(true);return (AlertDialog)f.get(map);}
 private static boolean waitMenuVisible(Instrumentation in,AmapUi map,String...labels)throws Exception{
  for(int attempt=0;attempt<60;attempt++){
   AlertDialog menu=menu(map);final boolean[] visible={false};
   in.runOnMainSync(()->{if(menu==null||!menu.isShowing()||menu.getWindow()==null)return;android.view.View root=menu.getWindow().getDecorView();for(String label:labels)if(!visibleText(root,label))return;visible[0]=true;});
   if(visible[0])return true;android.os.SystemClock.sleep(50);in.waitForIdleSync();
  }
  return false;
 }
 private static boolean visibleText(android.view.View root,String text){
  if(root instanceof android.widget.TextView&&text.contentEquals(((android.widget.TextView)root).getText())){android.graphics.Rect rect=new android.graphics.Rect();return root.isShown()&&root.getGlobalVisibleRect(rect)&&!rect.isEmpty();}
  if(root instanceof android.view.ViewGroup){android.view.ViewGroup group=(android.view.ViewGroup)root;for(int i=0;i<group.getChildCount();i++)if(visibleText(group.getChildAt(i),text))return true;}
  return false;
 }
 private static boolean waitText(Instrumentation in,String text){for(int attempt=0;attempt<60;attempt++){AccessibilityNodeInfo node=find(in,text);if(node!=null&&node.isVisibleToUser())return true;android.os.SystemClock.sleep(50);in.waitForIdleSync();}return false;}
}
