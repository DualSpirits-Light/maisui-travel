package cn.lvxu.travel;
import android.app.Instrumentation;
import android.content.*;
import android.graphics.Bitmap;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.*;
import java.io.*;
import java.time.LocalDate;

final class ControlsPolishUiTest {
 static int run(Instrumentation in)throws Exception {
  Context context=in.getTargetContext();MainActivity host=null;
  try(TestStartupGuard guard=new TestStartupGuard(context)){
   host=(MainActivity)in.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP));in.waitForIdleSync();MainActivity a=host;int n=0;
   Trip trip=new Trip();trip.title="兰州 · 博物馆与黄河慢游";trip.city="兰州市";trip.start=LocalDate.now().toString();trip.days=30;trip.normalize();
   Trip.Stop stop=new Trip.Stop();stop.name="甘肃省博物馆与黄河沿岸慢游";stop.time="09:00";stop.duration=120;stop.timeLocked=true;stop.address="兰州市七里河区西津西路3号";stop.note="提前预约，留一点时间随意逛逛。";trip.stops.add(stop);
   Trip.Expense bill=new Trip.Expense();bill.name="博物馆周边午餐与黄河游览交通";bill.amount=12850;bill.occurredAt=LocalDate.now()+"T12:30";bill.category="餐饮";bill.categoryId=trip.categories.get(0).id;trip.expenses.add(bill);
   for(String theme:new String[]{"light","dark"}){
    in.runOnMainSync(()->{a.prefs.setTheme(theme);a.trips.clear();a.trips.add(trip);a.active=trip;a.page=1;a.day=0;a.mapMode=false;a.render();});in.waitForIdleSync();
    TextView mode=find(a.body,"列表日程"),day=find(a.body,"第1天 · "+LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("M/d")));
    n+=check(mode!=null&&mode.isSelected()&&mode.getHeight()>=a.dp(48),"mode has selected semantics and touch size");
    n+=check(day!=null&&day.isSelected()&&day.getHeight()>=a.dp(48),"day selected without relying only on color");
    AccessibilityNodeInfo info=mode.createAccessibilityNodeInfo();n+=check("android.widget.Button".contentEquals(info.getClassName())&&info.isSelected(),"screen reader sees selected button");info.recycle();
    View nav=findDescription(a.root,"行程");n+=check(nav!=null&&nav.isSelected()&&nav.getHeight()>=a.dp(48),"navigation selected semantics");
    n+=check(nav instanceof ViewGroup&&((ViewGroup)nav).getChildAt(0) instanceof ImageView,"navigation uses consistent image icons");
    n+=check(find(a.body,"预约时间已锁定")!=null,"reservation remains prominent");
    capture(in,"itinerary-"+theme+".png");
    in.runOnMainSync(()->a.body.findViewWithTag("timeline-stop:"+stop.id).performClick());in.waitForIdleSync();
    AccessibilityNodeInfo window=in.getUiAutomation().getRootInActiveWindow();
    n+=check(window!=null&&!window.findAccessibilityNodeInfosByText(stop.address).isEmpty(),"card tap exposes the full place address");
    AccessibilityNodeInfo back=desc(window,"返回");n+=check(back!=null&&back.performAction(AccessibilityNodeInfo.ACTION_CLICK),"details returns through its visible back action");in.waitForIdleSync();
    in.runOnMainSync(()->{a.page=2;a.render();});in.waitForIdleSync();capture(in,"finance-"+theme+".png");
   }
   LinearLayout[] form={null};EditText[] input={null};TextView[] left={null},right={null};
   in.runOnMainSync(()->{form[0]=a.col();left[0]=a.action("这个按钮名称较长但必须完整显示",false,()->{});right[0]=a.action("取消",false,()->{});a.pair(form[0],left[0],right[0]);measure(a,form[0],240);});
   n+=check(right[0].getTop()>=left[0].getBottom()+a.dp(10),"long paired controls stack on narrow screen");
   n+=check(left[0].getHeight()>=a.dp(48)&&right[0].getHeight()>=a.dp(48),"stacked controls keep touch targets");
   in.runOnMainSync(()->{form[0]=a.col();input[0]=a.field(form[0],"旅行天数","invalid",2);a.body.addView(form[0]);try{a.number(input[0],1,60);}catch(IllegalArgumentException expected){};});in.waitForIdleSync();
   n+=check(input[0].hasFocus()&&input[0].getError().toString().contains("旅行天数")&&input[0].getError().toString().contains("1–60"),"invalid field focuses and explains actual item and range");
   n+=check(((TextView)form[0].getChildAt(0)).getLabelFor()==input[0].getId(),"input has associated visible caption");
   return n;
  }finally{if(host!=null){MainActivity a=host;in.runOnMainSync(a::finish);}}
 }
 private static void measure(MainActivity a,View v,int width){v.measure(View.MeasureSpec.makeMeasureSpec(a.dp(width),View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));v.layout(0,0,v.getMeasuredWidth(),v.getMeasuredHeight());}
 private static int check(boolean ok,String message){if(!ok)throw new AssertionError(message);return 1;}
 private static TextView find(View v,String value){if(v instanceof TextView&&value.contentEquals(((TextView)v).getText()))return (TextView)v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){TextView r=find(g.getChildAt(i),value);if(r!=null)return r;}}return null;}
 private static View findDescription(View v,String value){if(value.contentEquals(v.getContentDescription()==null?"":v.getContentDescription()))return v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){View r=findDescription(g.getChildAt(i),value);if(r!=null)return r;}}return null;}
 private static AccessibilityNodeInfo desc(AccessibilityNodeInfo node,String value){if(node==null)return null;if(value.contentEquals(node.getContentDescription()==null?"":node.getContentDescription()))return node;for(int i=0;i<node.getChildCount();i++){AccessibilityNodeInfo r=desc(node.getChild(i),value);if(r!=null)return r;}return null;}
 private static void capture(Instrumentation in,String name)throws Exception {android.os.SystemClock.sleep(600);in.waitForIdleSync();Bitmap b=in.getUiAutomation().takeScreenshot();File dir=new File(in.getTargetContext().getExternalFilesDir(null),"ui-polish-evidence");dir.mkdirs();try(FileOutputStream out=new FileOutputStream(new File(dir,name))){b.compress(Bitmap.CompressFormat.PNG,100,out);}finally{b.recycle();}}
}
