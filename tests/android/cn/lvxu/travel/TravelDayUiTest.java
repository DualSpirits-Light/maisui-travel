package cn.lvxu.travel;
import android.app.*;import android.content.*;import android.graphics.Bitmap;import android.view.*;import android.widget.*;import java.time.*;import java.io.*;import java.lang.reflect.*;
final class TravelDayUiTest {
 static int run(Instrumentation in)throws Exception{
  int n=0;MainActivity host=null;PageUi overview=null,copy=null;
  try(TestStartupGuard guard=new TestStartupGuard(in.getTargetContext())){
   host=(MainActivity)in.startActivitySync(new Intent(in.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP));MainActivity a=host;in.waitForIdleSync();
   LocalDateTime now=LocalDateTime.now(),at=now.minusMinutes(15);Trip t=new Trip();t.title="兰州 · 出行体验";t.city="兰州";t.start=now.toLocalDate().minusDays(1).toString();t.days=3;t.normalize();
   Trip.Stop s=new Trip.Stop();s.name="甘肃省博物馆";s.day=(int)java.time.temporal.ChronoUnit.DAYS.between(LocalDate.parse(t.start),at.toLocalDate());s.time=at.toLocalTime().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));s.duration=90;s.timeLocked=true;s.address="兰州市七里河区";s.note="预约凭证请保管";t.stops.add(s);t.items.add(new Trip.Item("身份证"));String original=t.json().toString();
   in.runOnMainSync(()->{a.prefs.setTheme("light");a.trips.clear();a.trips.add(t);a.active=t;a.page=1;a.day=0;a.mapMode=false;a.render();});in.waitForIdleSync();
   TextView entry=find(a.body,"出行速览");n+=check(entry!=null&&entry.getHeight()>=a.dp(48),"visible overview entry with touch target");in.runOnMainSync(entry::performClick);in.waitForIdleSync();
   TravelDayUi[] holder={null};in.runOnMainSync(()->holder[0]=new TravelDayUi(a,t));TravelDayUi ui=holder[0];Field field=TravelDayUi.class.getDeclaredField("page");field.setAccessible(true);PageUi[] p={null};
   in.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);in.waitForIdleSync();
   in.runOnMainSync(()->ui.show());in.waitForIdleSync();overview=(PageUi)field.get(ui);PageUi ov=overview;
   n+=check(find(ov.body,"今天 · 按计划此刻")!=null,"today independent of selected past day");n+=check(find(ov.body,"固定预约")!=null,"locked appointment visible");n+=check(find(ov.body,"仅依据计划时间")!=null,"no fake completion or navigation claims");
   n+=check(find(ov.body,"地图查看")!=null&&find(ov.body,"地点详情")!=null,"place actions visible");
   Field timerField=TravelDayUi.class.getDeclaredField("timer"),tickField=TravelDayUi.class.getDeclaredField("tick");timerField.setAccessible(true);tickField.setAccessible(true);android.os.Handler timer=(android.os.Handler)timerField.get(ui);Runnable tick=(Runnable)tickField.get(ui);
   Dialog[] blocker={null};in.runOnMainSync(()->{blocker[0]=new Dialog(a);blocker[0].setContentView(new TextView(a));blocker[0].show();});android.os.SystemClock.sleep(500);in.waitForIdleSync();n+=check(!timer.hasCallbacks(tick),"background stops minute task");
   in.runOnMainSync(()->{ov.body.removeAllViews();blocker[0].dismiss();});android.os.SystemClock.sleep(500);in.waitForIdleSync();n+=check(find(ov.body,"今天 · 按计划此刻")!=null&&timer.hasCallbacks(tick),"focus return immediately refreshes and restarts task");
   screenshot(in,"today-light.png");
   TextView jump=find(ov.body,"查看今天行程");in.runOnMainSync(jump::performClick);in.waitForIdleSync();n+=check(a.day==1&&a.page==1,"jump uses actual today");n+=check(!ov.dialog.isShowing(),"overview dismisses when jumping");
   in.runOnMainSync(()->{a.prefs.setTheme("dark");a.render();holder[0]=new TravelDayUi(a,t);holder[0].show();});in.waitForIdleSync();PageUi darkPage=(PageUi)field.get(holder[0]);overview=darkPage;screenshot(in,"today-dark.png");in.runOnMainSync(()->darkPage.dialog.dismiss());
   in.runOnMainSync(()->p[0]=DayItineraryTextUi.show(a,t,s.day));in.waitForIdleSync();copy=p[0];PageUi cp=copy;
   n+=check(find(cp.body,"包含地点地址和备注") instanceof CheckBox&&!((CheckBox)find(cp.body,"包含地点地址和备注")).isChecked(),"optional notes initially off");
   n+=check(find(cp.body,"复制文字")!=null&&find(cp.body,"发送给同行人")!=null,"copy and system chooser separate");
   TextView preview=find(cp.body,"目的地：兰州");n+=check(preview!=null&&!preview.getText().toString().contains(s.note),"default preview excludes notes");
   in.runOnMainSync(()->((CheckBox)find(cp.body,"包含地点地址和备注")).setChecked(true));in.waitForIdleSync();n+=check(preview.getText().toString().contains(s.note),"notes included only after opt in");
   screenshot(in,"day-copy-dark.png");in.runOnMainSync(()->find(cp.body,"复制文字").performClick());in.waitForIdleSync();
   ClipboardManager clipboard=(ClipboardManager)a.getSystemService(Context.CLIPBOARD_SERVICE);n+=check(clipboard.getPrimaryClip()!=null&&clipboard.getPrimaryClip().getItemAt(0).getText().toString().contains(s.name),"real clipboard receives selected itinerary");
   in.runOnMainSync(()->cp.dialog.dismiss());n+=check(original.equals(t.json().toString()),"overview and export do not modify travel data");
   return n;
  }finally{PageUi ov=overview,cp=copy;MainActivity a=host;if(a!=null)in.runOnMainSync(()->{if(ov!=null)ov.dialog.dismiss();if(cp!=null)cp.dialog.dismiss();a.finish();});}
 }
 static TextView find(View v,String text){if(v instanceof TextView&&((TextView)v).getText().toString().contains(text))return (TextView)v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){TextView r=find(g.getChildAt(i),text);if(r!=null)return r;}}return null;}
 static int check(boolean ok,String label){if(!ok)throw new AssertionError(label);return 1;}
 static void screenshot(Instrumentation in,String name)throws Exception{android.os.SystemClock.sleep(600);in.waitForIdleSync();Bitmap bitmap=in.getUiAutomation().takeScreenshot();File dir=new File(in.getTargetContext().getExternalFilesDir(null),"travel-day-evidence");dir.mkdirs();try(FileOutputStream out=new FileOutputStream(new File(dir,name))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}finally{bitmap.recycle();}}
}
