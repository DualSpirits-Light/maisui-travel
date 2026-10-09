package cn.lvxu.travel;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.*;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.*;
/** Single-window tag draft regression. Run only on a disposable emulator. */
final class TagChooserUiTest {
 static int run(Instrumentation in)throws Exception{
  Context c=in.getTargetContext();SharedPreferences prefs=c.getSharedPreferences(TagRepository.PREF,0);Map<String,?> saved=new HashMap<>(prefs.getAll());SharedPreferences appPrefs=c.getSharedPreferences("app-prefs-v2",0);boolean hadTutorial=appPrefs.contains("tutorial"),tutorial=appPrefs.getBoolean("tutorial",false);TestStartupGuard startup=new TestStartupGuard(c);MainActivity activity=null;int n=0;
  try{appPrefs.edit().putBoolean("tutorial",true).commit();
   activity=(MainActivity)in.startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP));in.waitForIdleSync();final MainActivity host=activity;
   TagRepository repo=new TagRepository(c);TagRepository.Tag detail=null,simple=null;for(TagRepository.Tag t:repo.all()){if(detail==null&&t.groupIds.contains("sys-preset-1"))detail=t;if(simple==null&&t.groupIds.contains("sys-preset-2"))simple=t;}if(detail==null||simple==null)throw new AssertionError("preset fixture missing");
   TagRepository.Tag custom=repo.create("选择器测试"+System.nanoTime(),TagRepository.DEFAULT_COLOR);final TagRepository.Tag d=detail,s=simple;
   Trip trip=new Trip();trip.tagIds.add("portable-label");trip.tagNames.put("portable-label","导入标签快照");final int[] applied={0};UiAutomation ui=in.getUiAutomation();
   in.runOnMainSync(()->TagUi.tripPicker(host,trip,true,()->applied[0]++));wait(ui,"选择标签");click(ui,"详细预设");click(ui,d.name);click(ui,"简洁预设");click(ui,s.name);click(ui,"自定义");click(ui,custom.name);click(ui,"全部");
   n+=check(wait(ui,"移除标签 "+d.name)!=null,"detailed selection survives switching groups");n+=check(wait(ui,"移除标签 "+s.name)!=null,"simple selection survives switching groups");n+=check(wait(ui,"移除标签 "+custom.name)!=null,"custom selection survives switching groups");
   n+=check(trip.tagIds.equals(Arrays.asList("portable-label"))&&trip.tagNames.get("portable-label").equals("导入标签快照"),"draft leaves saved IDs and names unchanged");click(ui,"取消");in.waitForIdleSync();n+=check(applied[0]==0&&trip.tagIds.size()==1,"cancel does not apply draft");
   in.runOnMainSync(()->TagUi.tripPicker(host,trip,true,()->applied[0]++));click(ui,"详细预设");click(ui,d.name);click(ui,"简洁预设");click(ui,s.name);click(ui,"移除标签 "+d.name);click(ui,"确定");in.waitForIdleSync();
   n+=check(applied[0]==1&&trip.tagIds.contains(s.id)&&!trip.tagIds.contains(d.id),"confirm applies remaining selections once");n+=check("导入标签快照".equals(trip.tagNames.get("portable-label")),"unknown imported label snapshot survives confirmation");
   Trip.Stop stop=new Trip.Stop();in.runOnMainSync(()->TagUi.stopPicker(host,stop,false,()->applied[0]++));click(ui,"详细预设");click(ui,d.name);click(ui,"简洁预设");click(ui,s.name);click(ui,"确定");in.waitForIdleSync();n+=check(stop.tagIds.equals(Arrays.asList(s.id)),"single selection replaces draft across groups");return n;
  }catch(Throwable failure){android.graphics.Bitmap shot=in.getUiAutomation().takeScreenshot();if(shot!=null){java.io.File file=new java.io.File(c.getExternalFilesDir(null),"tag-chooser-failure.png");try(java.io.FileOutputStream out=new java.io.FileOutputStream(file)){shot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}shot.recycle();}throw new AssertionError(failure+" UI="+dump(in.getUiAutomation().getRootInActiveWindow()),failure);}finally{if(activity!=null){MainActivity closing=activity;in.runOnMainSync(closing::finish);}SharedPreferences.Editor e=prefs.edit().clear();for(Map.Entry<String,?> item:saved.entrySet()){Object v=item.getValue();if(v instanceof String)e.putString(item.getKey(),(String)v);else if(v instanceof Boolean)e.putBoolean(item.getKey(),(Boolean)v);else if(v instanceof Integer)e.putInt(item.getKey(),(Integer)v);else if(v instanceof Long)e.putLong(item.getKey(),(Long)v);else if(v instanceof Float)e.putFloat(item.getKey(),(Float)v);}e.commit();SharedPreferences.Editor appEdit=appPrefs.edit();if(hadTutorial)appEdit.putBoolean("tutorial",tutorial);else appEdit.remove("tutorial");appEdit.commit();startup.close();}
 }
 private static String dump(AccessibilityNodeInfo node){if(node==null)return "null";StringBuilder b=new StringBuilder("["+node.getText()+"|"+node.getContentDescription()+"|"+node.getClassName()+"]");for(int i=0;i<node.getChildCount();i++)b.append(dump(node.getChild(i)));return b.toString();}
 private static int check(boolean value,String label){if(!value)throw new AssertionError(label);return 1;}
 private static void click(UiAutomation ui,String text)throws Exception{AccessibilityNodeInfo node=wait(ui,text);while(node!=null&&!node.isClickable())node=node.getParent();if(node==null||!node.performAction(AccessibilityNodeInfo.ACTION_CLICK))throw new AssertionError("Cannot click "+text);SystemClock.sleep(120);}
 private static AccessibilityNodeInfo wait(UiAutomation ui,String text)throws Exception{for(int i=0;i<30;i++){AccessibilityNodeInfo node=find(ui.getRootInActiveWindow(),text);if(node!=null)return node;SystemClock.sleep(100);}throw new AssertionError("Missing chooser item "+text);}
 private static AccessibilityNodeInfo find(AccessibilityNodeInfo node,String text){if(node==null)return null;if(text.contentEquals(node.getText()==null?"":node.getText())||text.contentEquals(node.getContentDescription()==null?"":node.getContentDescription()))return node;for(int i=0;i<node.getChildCount();i++){AccessibilityNodeInfo hit=find(node.getChild(i),text);if(hit!=null)return hit;}return null;}
}
