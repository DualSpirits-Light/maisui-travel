package cn.lvxu.travel;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.view.*;
import android.widget.*;
import org.json.JSONObject;
import java.io.*;
import java.util.*;

/** Native button tests with synthetic replies; never calls a paid provider. */
final class AiExperienceUiTest {
 static int run(Instrumentation in)throws Exception {
  Context context=in.getTargetContext();AppPrefs prefs=new AppPrefs(context);JSONObject oldPrefs=prefs.exportJson();MainActivity activity=null;ArrayList<Trip> saved=null;final PageUi[] open={null};final AiExploreUi[] ui={null};int n=0;
  try{
   prefs.setTutorialDone(true);prefs.setLastUpdateDay(java.time.LocalDate.now().toString());
   activity=(MainActivity)in.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP));MainActivity a=activity;in.waitForIdleSync();saved=a.store.read();
   Trip trip=Trip.demo();String original=trip.json().toString();
   in.runOnMainSync(()->{a.trips=new ArrayList<>();a.trips.add(trip);a.active=trip;a.loadFailed=false;a.page=1;a.day=0;a.mapMode=false;a.render();});a.store.save(a.trips);
   String reply="{\"title\":\"杭州慢游建议\",\"days\":[{\"day\":1,\"items\":[{\"name\":\"西湖\",\"time\":\"09:00\",\"duration\":60,\"address\":\"沿湖步道\",\"note\":\"慢走\",\"lat\":30,\"lon\":120},{\"name\":\"北山街\",\"time\":\"11:00\",\"duration\":60}]}]}";
   AiPlan plan=AiPlan.parse(reply,trip.days);
   final LinearLayout[] preview={null};final TextView[] status={null};
   in.runOnMainSync(()->{ui[0]=new AiExploreUi(a);open[0]=new PageUi(a,"AI 规划离线预览");preview[0]=a.col();status[0]=a.text("",14,MainActivity.INK);open[0].body.addView(status[0]);open[0].body.addView(preview[0]);ui[0].showPreview(preview[0],open[0],trip,trip,plan,reply,status[0]);open[0].show();});in.waitForIdleSync();
   n+=check(collect(preview[0],CheckBox.class).size()==2,"per-place import choices");
   click(in,find(preview[0],"修改地点"));AlertDialog edit=ui[0].interactionDialog;List<EditText> fields=collect(edit.getWindow().getDecorView(),EditText.class);
   in.runOnMainSync(()->{fields.get(0).setText("取消的名称");edit.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();});in.waitForIdleSync();
   n+=check(plan.days.get(0).items.get(0).name.equals("西湖"),"cancel editor keeps draft");
   click(in,find(preview[0],"修改地点"));AlertDialog secondEditor=ui[0].interactionDialog;List<EditText> secondFields=collect(secondEditor.getWindow().getDecorView(),EditText.class);
   in.runOnMainSync(()->{secondFields.get(0).setText("湖边散步");secondFields.get(1).setText("25:00");secondEditor.getButton(AlertDialog.BUTTON_POSITIVE).performClick();});in.waitForIdleSync();
   n+=check(secondEditor.isShowing()&&plan.days.get(0).items.get(0).name.equals("西湖"),"invalid editor stays open and atomic");
   in.runOnMainSync(()->{secondFields.get(1).setText("10:00");secondFields.get(2).setText("90");secondFields.get(3).setText("12.50");secondFields.get(4).setText("新的沿湖入口");secondFields.get(5).setText("核实开放情况");secondEditor.getButton(AlertDialog.BUTTON_POSITIVE).performClick();});in.waitForIdleSync();
   AiPlan.Item edited=plan.days.get(0).items.get(0);
   n+=check(!secondEditor.isShowing()&&edited.name.equals("湖边散步")&&edited.time.equals("10:00")&&edited.cost==1250,"save editor updates preview draft");
   n+=check(edited.lat==null&&edited.lon==null&&edited.address.equals("新的沿湖入口"),"edited place clears coordinates and retains address");
   n+=check(original.equals(trip.json().toString()),"editing preview leaves current trip intact");
   capture(in,"ai-plan-preview.png");
   CheckBox firstImport=collect(preview[0],CheckBox.class).get(0);in.runOnMainSync(()->firstImport.setChecked(false));
   n+=check(plan.toStops().size()==1&&plan.toStops().get(0).name.equals("北山街"),"unchecked draft excluded from import");
   in.runOnMainSync(open[0].dialog::dismiss);n+=check(a.store.read().get(0).json().toString().equals(original),"cancel whole preview keeps persisted trip");
   // Exercise the real final confirmation, including persistence of edited/selected content.
   in.runOnMainSync(()->{plan.days.get(0).items.get(0).selected=true;plan.days.get(0).items.get(1).selected=false;open[0]=new PageUi(a,"AI 规划导入检查");preview[0]=a.col();status[0]=a.text("",14,MainActivity.INK);open[0].body.addView(status[0]);open[0].body.addView(preview[0]);ui[0].showPreview(preview[0],open[0],trip,trip,plan,reply,status[0]);open[0].show();});in.waitForIdleSync();
   click(in,find(preview[0],"将勾选地点追加到当前旅行"));
   confirmAccessible(in,"导入");
   Trip imported=a.active;
   n+=check(imported!=trip&&imported.stops.size()==trip.stops.size()+1,"final import adds only selected draft");
   Trip.Stop importedStop=imported.stops.get(imported.stops.size()-1);
   n+=check(importedStop.name.equals("湖边散步")&&importedStop.time.equals("10:00")&&importedStop.cost==1250,"final import preserves edited fields");
   n+=check(a.store.read().get(0).stops.size()==trip.stops.size()+1&&!open[0].alive(),"confirmed import persisted and closed");
   n+=check(trip.json().toString().equals(original),"confirmed import keeps source object unchanged");
   in.runOnMainSync(()->{a.trips=new ArrayList<>();a.trips.add(trip);a.active=trip;a.render();});a.store.save(a.trips);
   String optimization=optimization(trip);AiOptimization proposal=AiOptimization.parse(optimization,trip);
   showOptimization(in,a,ui,open,preview,status,trip,proposal);List<CheckBox> choices=collect(preview[0],CheckBox.class);
   n+=check(choices.size()==2&&!choices.get(0).isChecked()&&!choices.get(1).isChecked(),"optimization defaults to no selection");
   n+=check(findContains(preview[0],"修改前")!=null&&findContains(preview[0],"修改后")!=null&&findContains(preview[0],"原因：避开拥挤")!=null,"before after and reasons displayed");
   capture(in,"ai-optimization-preview.png");
   click(in,find(preview[0],"应用勾选的建议"));n+=check(ui[0].interactionDialog==null,"empty selection does not confirm");
   in.runOnMainSync(()->choices.get(0).setChecked(true));click(in,find(preview[0],"应用勾选的建议"));AlertDialog confirmation=ui[0].interactionDialog;
   click(in,confirmation.getButton(AlertDialog.BUTTON_NEGATIVE));n+=check(original.equals(trip.json().toString()),"cancel optimization preserves source");
   click(in,find(preview[0],"应用勾选的建议"));click(in,ui[0].interactionDialog.getButton(AlertDialog.BUTTON_POSITIVE));
   Trip applied=a.active;
   n+=check(applied!=trip&&applied.stops.get(0).time.equals("10:00"),"selected suggestion applied as detached trip");
   n+=check(applied.stops.get(1).time.equals(trip.stops.get(1).time)&&trip.stops.get(0).time.equals("09:00"),"unselected suggestion and source unchanged");
   n+=check(a.store.read().get(0).stops.get(0).time.equals("10:00"),"selected optimization persisted");
   n+=check(!open[0].alive(),"successful apply closes preview");
   AiOptimization stale=AiOptimization.parse(optimization(applied),applied);showOptimization(in,a,ui,open,preview,status,applied,stale);
   List<CheckBox> staleChoices=collect(preview[0],CheckBox.class);in.runOnMainSync(()->{staleChoices.get(0).setChecked(true);applied.stops.get(0).note="用户刚补充的内容";});
   click(in,find(preview[0],"应用勾选的建议"));click(in,ui[0].interactionDialog.getButton(AlertDialog.BUTTON_POSITIVE));
   n+=check(a.active==applied&&applied.stops.get(0).note.equals("用户刚补充的内容")&&open[0].alive(),"changed stop rejects stale suggestion");
   n+=check(!a.store.read().get(0).stops.get(0).note.equals("用户刚补充的内容"),"stale rejection did not save");
   in.runOnMainSync(()->a.trips.clear());click(in,find(preview[0],"应用勾选的建议"));click(in,ui[0].interactionDialog.getButton(AlertDialog.BUTTON_POSITIVE));
   n+=check(a.trips.isEmpty()&&a.active==applied&&open[0].alive(),"deleted trip cannot be restored by proposal");
   in.runOnMainSync(()->{a.trips.add(applied);a.active=Trip.demo();});click(in,find(preview[0],"应用勾选的建议"));click(in,ui[0].interactionDialog.getButton(AlertDialog.BUTTON_POSITIVE));
   n+=check(a.active!=applied&&a.trips.get(0)==applied,"changed active trip rejects old preview");
   in.runOnMainSync(()->{a.active=applied;open[0].dialog.dismiss();ui[0].applyOptimization(open[0],applied,stale,Collections.singleton(applied.stops.get(0).id));});
   n+=check(a.active==applied,"closed page cannot apply");
   AiOptimization none=AiOptimization.parse("{\"suggestions\":[]}",applied);showOptimization(in,a,ui,open,preview,status,applied,none);
   n+=check(preview[0].getChildCount()==0&&status[0].getText().toString().contains("没有需要修改"),"zero suggestions is friendly and read only");
   return n;
  }finally{
   if(activity!=null){MainActivity a=activity;in.runOnMainSync(()->{if(ui[0]!=null&&ui[0].interactionDialog!=null)ui[0].interactionDialog.dismiss();if(open[0]!=null)open[0].dialog.dismiss();a.finish();});if(saved!=null)a.store.save(saved);}prefs.importJson(oldPrefs);
  }
 }
 static String optimization(Trip t){String time=t.stops.get(0).time.equals("10:00")?"10:30":"10:00";return "{\"suggestions\":[{\"stopId\":\""+t.stops.get(0).id+"\",\"reason\":\"避开拥挤\",\"changes\":{\"time\":\""+time+"\"}},{\"stopId\":\""+t.stops.get(1).id+"\",\"reason\":\"留出午饭时间\",\"changes\":{\"time\":\"12:00\"}}]}";}
 static void showOptimization(Instrumentation in,MainActivity a,AiExploreUi[] ui,PageUi[] page,LinearLayout[] box,TextView[] result,Trip current,AiOptimization proposal){in.runOnMainSync(()->{if(page[0]!=null)page[0].dialog.dismiss();ui[0]=new AiExploreUi(a);page[0]=new PageUi(a,"AI 优化离线预览");box[0]=a.col();result[0]=a.text("",14,MainActivity.INK);page[0].body.addView(result[0]);page[0].body.addView(box[0]);ui[0].showOptimizationPreview(page[0],box[0],current,proposal,result[0]);page[0].show();});in.waitForIdleSync();}
 static void confirmAccessible(Instrumentation in,String label)throws Exception {
  android.view.accessibility.AccessibilityNodeInfo root=in.getUiAutomation().getRootInActiveWindow();
  if(root==null)throw new AssertionError("confirmation window missing");
  boolean clicked=false;try{for(android.view.accessibility.AccessibilityNodeInfo node:root.findAccessibilityNodeInfosByText(label)){
   try{if(label.contentEquals(node.getText()==null?"":node.getText())&&node.isClickable()){clicked=node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK);if(clicked)break;}}finally{node.recycle();}
  }}finally{root.recycle();}
  if(!clicked)throw new AssertionError("confirmation button missing: "+label);
  SystemClock.sleep(250);in.waitForIdleSync();
 }
 static void click(Instrumentation in,View view){if(view==null)throw new AssertionError("button missing");in.runOnMainSync(view::performClick);in.waitForIdleSync();SystemClock.sleep(100);in.waitForIdleSync();}
 static int check(boolean b,String label){if(!b)throw new AssertionError(label);return 1;}
 static View find(View v,String text){if(v instanceof TextView&&text.contentEquals(((TextView)v).getText()))return v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){View found=find(g.getChildAt(i),text);if(found!=null)return found;}}return null;}
 static View findContains(View v,String text){if(v instanceof TextView&&((TextView)v).getText().toString().contains(text))return v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){View found=findContains(g.getChildAt(i),text);if(found!=null)return found;}}return null;}
 static <T> List<T> collect(View v,Class<T> type){ArrayList<T> list=new ArrayList<>();if(type.isInstance(v))list.add(type.cast(v));if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++)list.addAll(collect(g.getChildAt(i),type));}return list;}
 static void capture(Instrumentation in,String name)throws Exception{SystemClock.sleep(200);Bitmap b=in.getUiAutomation().takeScreenshot();if(b==null)throw new AssertionError("screenshot unavailable");File dir=new File(in.getTargetContext().getExternalFilesDir(null),"next-version-evidence");dir.mkdirs();try(FileOutputStream out=new FileOutputStream(new File(dir,name))){b.compress(Bitmap.CompressFormat.PNG,100,out);}finally{b.recycle();}}
}
