package cn.lvxu.travel;

import android.app.*;
import android.content.*;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.lang.reflect.*;
import java.util.*;

/** Travels retain unsaved input independently of the live model during controller recreation. */
final class TravelDraftUiTest {
 static int run(Instrumentation in)throws Exception {
  Context c=in.getTargetContext();TestStartupGuard startup=new TestStartupGuard(c);
  MainActivity a=(MainActivity)in.startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
  in.waitForIdleSync();int checks=0;
  try {
   Trip source=Trip.demo();String originalTitle=source.title;
   in.runOnMainSync(()->{a.trips.clear();a.trips.add(source);a.active=source;TripEditorUi.show(a,source);});in.waitForIdleSync();
   // Reflection keeps this test compilable against the old implementation: absence is the intended red.
   Method save=method("saveState"),restore=method("restoreState");
   Object draft=draft(a);AlertDialog dialog=(AlertDialog)get(draft,"dialog");
   ArrayList<EditText> fields=fields((View)get(draft,"form"));
   Bundle state=new Bundle();
   in.runOnMainSync(()->{
    fields.get(0).setText("未保存的旅行名字");fields.get(1).setText("兰州市");fields.get(2).setText("2026-10-30");fields.get(3).setText("invalid-days");
    fields.get(3).setError("请输入 1–60");fields.get(4).setText("预算暂未填好");fields.get(5).setText("石家庄市");fields.get(6).setText("小麦; 小穗");fields.get(7).setText("未保存航班");
    ((LinearLayout)getUnchecked(draft,"optional")).setVisibility(View.VISIBLE);
    Trip tags=(Trip)getUnchecked(draft,"tags");tags.tagIds.add("draft-city-tag");tags.tagNames.put("draft-city-tag","城市漫游");
    PlaceImporter.Place place=new PlaceImporter.Place();place.name="测试住宿";place.address="测试地址";place.sourceUrl="https://amap.com/detail?poiid=draft-fixture";place.lat=36.06;place.lon=103.83;place.openingHours="全天";place.rating="4.5";place.poiId="draft-fixture";place.imageUrl="https://example.com/lodging.jpg";place.coordinateSystem="GCJ02";
    ((PlaceImporter.Place[])getUnchecked(draft,"imported"))[0]=place;fields.get(8).setText("测试住宿 · 测试地址");
    invoke(save,a,state);dialog.dismiss();invoke(restore,a,state);
   });in.waitForIdleSync();
   Object resumed=draft(a);ArrayList<EditText> next=fields((View)get(resumed,"form"));
   String[] expected={"未保存的旅行名字","兰州市","2026-10-30","invalid-days","预算暂未填好","石家庄市","小麦; 小穗","未保存航班","测试住宿 · 测试地址"};
   for(int i=0;i<expected.length;i++){check(next.get(i).getText().toString().equals(expected[i]),"travel draft field "+i+" lost on recreation");checks++;}
   check("请输入 1–60".contentEquals(next.get(3).getError()),"field validation error lost");checks++;
   check(((View)get(resumed,"optional")).getVisibility()==View.VISIBLE,"optional expansion lost");checks++;
   Trip tags=(Trip)get(resumed,"tags");check(tags.tagIds.contains("draft-city-tag")&&"城市漫游".equals(tags.tagNames.get("draft-city-tag")),"unsaved tags lost");checks++;
   PlaceImporter.Place imported=((PlaceImporter.Place[])get(resumed,"imported"))[0];check(imported!=null&&imported.sourceUrl.contains("draft-fixture")&&imported.lat==36.06&&imported.lon==103.83&&imported.rating.equals("4.5")&&imported.openingHours.equals("全天")&&imported.poiId.equals("draft-fixture")&&imported.imageUrl.equals("https://example.com/lodging.jpg")&&imported.coordinateSystem.equals("GCJ02"),"lodging source evidence lost");checks++;
   check(source.title.equals(originalTitle)&&!source.tagIds.contains("draft-city-tag"),"draft restoration mutated live trip");checks++;
   AlertDialog resumedDialog=(AlertDialog)get(resumed,"dialog");
   in.runOnMainSync(()->resumedDialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick());in.waitForIdleSync();
   check(resumedDialog.isShowing()&&a.active==source,"invalid restored fields bypassed save validation");checks++;
   in.runOnMainSync(()->{next.get(3).setText("3");next.get(4).setText("1000");resumedDialog.getButton(AlertDialog.BUTTON_NEUTRAL).performClick();});in.waitForIdleSync();
   check(resumedDialog.isShowing(),"delete confirmation dismissed unsaved travel form");checks++;
   // Dismiss confirmation using system back; keep the original editor and its fields.
   in.getUiAutomation().performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK);in.waitForIdleSync();
   check(resumedDialog.isShowing()&&next.get(0).getText().toString().equals(expected[0]),"cancel delete lost travel fields");checks++;
   in.runOnMainSync(resumedDialog::dismiss);
   Bundle closed=new Bundle();in.runOnMainSync(()->invoke(save,a,closed));check(!closed.containsKey("travel.draft"),"dismissed travel draft saved again");checks++;
   in.runOnMainSync(()->{a.trips.clear();invoke(restore,a,state);});check(draftOrNull(a)==null,"deleted trip draft resurrected");checks++;
   // New-travel drafts do not require an existing model and retain collapsed optional fields.
   in.runOnMainSync(()->TripEditorUi.show(a,null));Object creation=draft(a);ArrayList<EditText> createFields=fields((View)get(creation,"form"));Bundle createState=new Bundle();
   in.runOnMainSync(()->{createFields.get(0).setText("尚未创建的旅行");createFields.get(1).setText("广州市");createFields.get(6).setText("小麦; 小穗");invoke(save,a,createState);((AlertDialog)getUnchecked(creation,"dialog")).dismiss();});
   android.os.Parcel parcel=android.os.Parcel.obtain();Bundle fromParcel;try{parcel.writeBundle(createState);parcel.setDataPosition(0);fromParcel=parcel.readBundle(TravelDraftUiTest.class.getClassLoader());}finally{parcel.recycle();}
   in.runOnMainSync(()->invoke(restore,a,fromParcel));Object fresh=draft(a);ArrayList<EditText> freshFields=fields((View)get(fresh,"form"));
   check(freshFields.get(0).getText().toString().equals("尚未创建的旅行")&&freshFields.get(1).getText().toString().equals("广州市")&&freshFields.get(6).getText().toString().equals("小麦; 小穗"),"new travel parcel roundtrip lost raw inputs");checks++;
   check(((View)get(fresh,"optional")).getVisibility()==View.GONE&&a.trips.isEmpty(),"new travel restore expanded or saved prematurely");checks++;
   in.runOnMainSync(()->((AlertDialog)getUnchecked(fresh,"dialog")).dismiss());
   return checks;
  } finally {in.runOnMainSync(a::finish);in.waitForIdleSync();startup.close();}
 }
 private static Method method(String name){try{return TripEditorUi.class.getDeclaredMethod(name,MainActivity.class,Bundle.class);}catch(NoSuchMethodException e){throw new AssertionError("Travel form cannot survive recreation: missing "+name,e);}}
 private static void invoke(Method m,MainActivity a,Bundle b){try{m.invoke(null,a,b);}catch(Exception e){throw new AssertionError("Travel draft lifecycle failed",e);}}
 private static Object draft(MainActivity a)throws Exception{Object value=draftOrNull(a);if(value==null)throw new AssertionError("Travel draft not attached");return value;}
 private static Object draftOrNull(MainActivity a)throws Exception{Method m=TripEditorUi.class.getDeclaredMethod("currentDraft",MainActivity.class);m.setAccessible(true);return m.invoke(null,a);}
 private static Object get(Object value,String name)throws Exception{Field f=value.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(value);}
 private static Object getUnchecked(Object value,String name){try{return get(value,name);}catch(Exception e){throw new AssertionError(e);}}
 private static ArrayList<EditText> fields(View root){ArrayList<EditText> out=new ArrayList<>();collect(root,out);return out;}
 private static void collect(View root,ArrayList<EditText> out){if(root instanceof EditText)out.add((EditText)root);if(root instanceof ViewGroup){ViewGroup g=(ViewGroup)root;for(int i=0;i<g.getChildCount();i++)collect(g.getChildAt(i),out);}}
 private static void check(boolean value,String name){if(!value)throw new AssertionError(name);}
}
