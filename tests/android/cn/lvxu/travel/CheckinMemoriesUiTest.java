package cn.lvxu.travel;

import android.app.AlertDialog;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.EditText;
import java.lang.reflect.Field;
import java.time.LocalDate;

/** Save/restore and presentation contract; uses only records owned by this test trip. */
final class CheckinMemoriesUiTest {
    static int run(Instrumentation in)throws Exception {
        Context context=in.getTargetContext();AppPrefs prefs=new AppPrefs(context);org.json.JSONObject original=prefs.exportJson();
        MainActivity host=null;CheckinUi ui=null;java.util.ArrayList<Trip> savedTrips=null;int n=0;
        try {
            prefs.setTutorialDone(true);prefs.setLastUpdateDay(LocalDate.now().toString());
            host=(MainActivity)in.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP));
            in.waitForIdleSync();savedTrips=host.store.read();MainActivity a=host;Trip trip=new Trip();trip.title="回忆专项旅行";trip.city="杭州";trip.start="2026-09-20";trip.days=2;
            Trip.Stop first=new Trip.Stop();first.name="同名地点";first.day=0;trip.stops.add(first);
            Trip.Stop second=new Trip.Stop();second.name=first.name;second.day=1;trip.stops.add(second);
            Trip.Checkin old=new Trip.Checkin();old.place=first.name;old.time="2026-09-20T10:00";trip.checkins.add(old);
            CheckinUi checkins=new CheckinUi(a,a.media);ui=checkins;
            in.runOnMainSync(()->{a.trips.clear();a.trips.add(trip);a.active=trip;checkins.beginNew(trip,second);});in.waitForIdleSync();
            n+=check(second.id.equals(get(checkins,"draftStopId")),"place entry associates exact same-name stop");
            AlertDialog editor=(AlertDialog)get(checkins,"editor");
            n+=check(text(editor.getWindow().getDecorView(),"关联地点：同名地点 · 第 2 天"),"editor explains selected day");
            Bundle state=new Bundle();in.runOnMainSync(()->checkins.saveState(state));
            n+=check(second.id.equals(state.getString("checkin.stopId")),"draft association survives saved state");
            in.runOnMainSync(()->editor.getButton(AlertDialog.BUTTON_POSITIVE).performClick());in.waitForIdleSync();
            n+=check(trip.checkins.size()==2&&second.id.equals(trip.checkins.get(1).stopId),"save retains contextual association");
            n+=check(old.stopId.isEmpty(),"legacy duplicate name never silently changed");
            Trip.Checkin saved=trip.checkins.get(1);
            in.runOnMainSync(()->checkins.beginEdit(saved));in.waitForIdleSync();
            AlertDialog edit=(AlertDialog)get(checkins,"editor");
            String originalPlace=saved.place;String originalLink=saved.stopId;
            in.runOnMainSync(()->{((EditText)safeGet(checkins,"place")).setText("不应保存");((EditText)safeGet(checkins,"companions")).setText(String.join("\n",java.util.Collections.nCopies(51,"同行")));edit.getButton(AlertDialog.BUTTON_POSITIVE).performClick();});in.waitForIdleSync();
            n+=check(originalPlace.equals(saved.place)&&originalLink.equals(saved.stopId)&&edit.isShowing(),"failed validation leaves saved record untouched");
            in.runOnMainSync(()->{((EditText)safeGet(checkins,"place")).setText(originalPlace);((EditText)safeGet(checkins,"companions")).setText("");});set(checkins,"draftStopId","");
            in.runOnMainSync(()->edit.getButton(AlertDialog.BUTTON_POSITIVE).performClick());in.waitForIdleSync();
            n+=check(saved.stopId.isEmpty(),"explicitly clearing association persists");
            set(checkins,"memoryDay","2026-09-21");
            in.runOnMainSync(()->{a.body.removeAllViews();checkins.show();});in.waitForIdleSync();
            n+=check(text(a.body,"这一天还没有打卡回忆"),"empty planned day stays navigable");
            set(checkins,"memoryDay","2026-09-20");
            in.runOnMainSync(()->{a.body.removeAllViews();checkins.show();});in.waitForIdleSync();
            n+=check(text(a.body,"多个同名地点"),"legacy ambiguity visible in daily history");
            capture(in);
            CheckinUi searchEntry=new CheckinUi(a,a.media);
            in.runOnMainSync(()->searchEntry.beginEdit(old));in.waitForIdleSync();
            AlertDialog searchEditor=(AlertDialog)get(searchEntry,"editor");
            n+=check(searchEditor!=null&&searchEditor.isShowing(),"search entry resolves owning trip on first edit");
            in.runOnMainSync(searchEditor::dismiss);
            return n;
        } finally {
            if(ui!=null){AlertDialog dialog=(AlertDialog)get(ui,"editor");if(dialog!=null)in.runOnMainSync(dialog::dismiss);}
            prefs.importJson(original);if(host!=null){MainActivity closing=host;try{if(savedTrips!=null)closing.store.save(savedTrips);}finally{in.runOnMainSync(closing::finish);closing.store.close();}}
        }
    }
    private static void capture(Instrumentation in)throws Exception {
        android.os.SystemClock.sleep(200);android.graphics.Bitmap image=in.getUiAutomation().takeScreenshot();
        if(image==null)throw new AssertionError("memory screenshot unavailable");
        java.io.File folder=new java.io.File(in.getTargetContext().getExternalFilesDir(null),"next-version-evidence");folder.mkdirs();
        try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(folder,"memories-by-day.png"))){image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}finally{image.recycle();}
    }
    private static Object safeGet(Object o,String name){try{return get(o,name);}catch(Exception e){throw new RuntimeException(e);}}
    private static Object get(Object o,String name)throws Exception{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    private static void set(Object o,String name,Object value)throws Exception{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);f.set(o,value);}
    private static int check(boolean value,String label){if(!value)throw new AssertionError(label);return 1;}
    private static boolean text(View view,String value){if(view instanceof TextView&&((TextView)view).getText().toString().contains(value))return true;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)if(text(group.getChildAt(i),value))return true;}return false;}
}
