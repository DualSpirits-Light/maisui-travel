package cn.lvxu.travel;
import android.app.*;
import android.content.*;
import android.view.*;
import android.widget.*;
import android.view.accessibility.AccessibilityNodeInfo;
import java.time.LocalDate;
import java.util.*;
import org.json.*;
/** Focused city and calendar contracts, including the screenshot's right-header edge regression. */
final class CityDatePickerUiTest {
 static int run(Instrumentation in)throws Exception{
  Context c=in.getTargetContext();AppPrefs prefs=new AppPrefs(c);JSONObject old=prefs.exportJson();TestStartupGuard guard=new TestStartupGuard(c);MainActivity activity=null;int n=0;
  try{prefs.setTutorialDone(true);prefs.setLastUpdateDay(LocalDate.now().toString());activity=(MainActivity)in.startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP));MainActivity a=activity;in.waitForIdleSync();
   EditText[] destination={null};AlertDialog[] form={null};in.runOnMainSync(()->{LinearLayout f=a.col();destination[0]=CityPickerUi.field(a,f,"海外目的地");form[0]=new RoundedDialogs.Builder(a).setTitle("目的地测试").setView(f).setNegativeButton("取消",null).show();});in.waitForIdleSync();
   View destinationRow=(View)destination[0].getParent();n+=check(((ViewGroup)destinationRow).getChildCount()==2,"destination has one editable field and dropdown");
   in.runOnMainSync(()->find(form[0].getWindow().getDecorView(),"选择目的地城市").performClick());in.waitForIdleSync();UiAutomation ui=in.getUiAutomation();click(ui,"河北省");
   n+=check(find(ui.getRootInActiveWindow(),"石家庄市")!=null,"province changes cities in current window");n+=check(find(ui.getRootInActiveWindow(),"山西省")!=null,"province column remains visible");click(ui,"石家庄市");in.waitForIdleSync();n+=check(destination[0].getText().toString().equals("石家庄市"),"city selection fills destination");n+=check(find(ui.getRootInActiveWindow(),"目的地测试")!=null,"city selection dismisses only picker");
   in.runOnMainSync(()->{destination[0].setText("京都");form[0].dismiss();});n+=check(destination[0].getText().toString().equals("京都"),"manual destination input retained");
   final LocalDate[] saved={LocalDate.of(2024,2,10)};AlertDialog[] calendar={null};in.runOnMainSync(()->calendar[0]=RoundedDatePicker.show(a,saved[0],d->saved[0]=d));in.waitForIdleSync();
   View decor=calendar[0].getWindow().getDecorView();TextView date=(TextView)find(decor,"2024-02-29");n+=check(date!=null,"leap day available");n+=check(find(decor,"2024-02-30")==null,"invalid February date absent");
   View header=(View)find(decor,"选择年份").getParent();View calendarRoot=(View)header.getParent();n+=check(header.getWidth()==calendarRoot.getWidth(),"header reaches full calendar width");
   ViewGroup week=(ViewGroup)date.getParent();n+=check(week.getChildCount()==7,"calendar week has seven responsive columns");n+=check(week.getWidth()==((View)week.getParent()).getWidth(),"seven columns fit available body width");
   in.runOnMainSync(()->{date.performClick();calendar[0].getButton(AlertDialog.BUTTON_NEGATIVE).performClick();});n+=check(saved[0].equals(LocalDate.of(2024,2,10)),"cancel preserves original value");
   in.runOnMainSync(()->calendar[0]=RoundedDatePicker.show(a,saved[0],d->saved[0]=d));in.waitForIdleSync();in.runOnMainSync(()->find(calendar[0].getWindow().getDecorView(),"下个月").performClick());in.waitForIdleSync();n+=check(find(calendar[0].getWindow().getDecorView(),"2024-03-31")!=null,"month navigation rebuilds month length");
   in.runOnMainSync(()->find(calendar[0].getWindow().getDecorView(),"上个月").performClick());in.waitForIdleSync();in.runOnMainSync(()->{find(calendar[0].getWindow().getDecorView(),"2024-02-29").performClick();calendar[0].getButton(AlertDialog.BUTTON_POSITIVE).performClick();});in.waitForIdleSync();n+=check(saved[0].equals(LocalDate.of(2024,2,29)),"confirm commits selected leap date");
   in.runOnMainSync(()->calendar[0]=RoundedDatePicker.show(a,LocalDate.of(2024,12,31),d->saved[0]=d));in.waitForIdleSync();in.runOnMainSync(()->find(calendar[0].getWindow().getDecorView(),"下个月").performClick());in.waitForIdleSync();n+=check(find(calendar[0].getWindow().getDecorView(),"2025-01-01")!=null,"month navigation crosses year boundary");in.runOnMainSync(()->calendar[0].dismiss());
   for(String theme:new String[]{"light","dark"}){in.runOnMainSync(()->{prefs.setTheme(theme);a.applyPalette();calendar[0]=RoundedDatePicker.show(a,LocalDate.of(2026,10,12),d->saved[0]=d);});in.waitForIdleSync();android.os.SystemClock.sleep(700);android.graphics.Bitmap shot=in.getUiAutomation().takeScreenshot();try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(c.getExternalFilesDir(null),"date-refine-"+theme+".png"))){shot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}shot.recycle();in.runOnMainSync(()->calendar[0].dismiss());}return n;
  }finally{if(activity!=null){MainActivity closing=activity;in.runOnMainSync(closing::finish);}prefs.importJson(old);guard.close();}
 }
 private static int check(boolean ok,String label){if(!ok)throw new AssertionError(label);return 1;}
 private static View find(View root,String label){if(label.contentEquals(root.getContentDescription()==null?"":root.getContentDescription())||(root instanceof TextView&&label.contentEquals(((TextView)root).getText())))return root;if(root instanceof ViewGroup){ViewGroup group=(ViewGroup)root;for(int i=0;i<group.getChildCount();i++){View hit=find(group.getChildAt(i),label);if(hit!=null)return hit;}}return null;}
 private static AccessibilityNodeInfo find(AccessibilityNodeInfo root,String label){if(root==null)return null;if(label.contentEquals(root.getText()==null?"":root.getText())||label.contentEquals(root.getContentDescription()==null?"":root.getContentDescription()))return root;for(int i=0;i<root.getChildCount();i++){AccessibilityNodeInfo hit=find(root.getChild(i),label);if(hit!=null)return hit;}return null;}
 private static void click(UiAutomation ui,String text)throws Exception{AccessibilityNodeInfo node=null;for(int i=0;i<20&&node==null;i++){node=find(ui.getRootInActiveWindow(),text);if(node==null)android.os.SystemClock.sleep(100);}while(node!=null&&!node.isClickable())node=node.getParent();if(node==null||!node.performAction(AccessibilityNodeInfo.ACTION_CLICK))throw new AssertionError("Missing city selection "+text);android.os.SystemClock.sleep(150);}
}
