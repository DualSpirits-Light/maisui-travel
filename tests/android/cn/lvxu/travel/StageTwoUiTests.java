package cn.lvxu.travel;

import android.app.Dialog;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.time.LocalDate;
import java.util.ArrayList;

/** Regression coverage for trip selection, archive filtering, and the About page. */
final class StageTwoUiTests {
    private StageTwoUiTests() {}
    static int run(Instrumentation in) throws Exception {
        Context context=in.getTargetContext(); AppPrefs prefs=new AppPrefs(context); JSONObject original=prefs.exportJson(); MainActivity activity=null; int checks=0;
        try {
            prefs.setTutorialDone(true); prefs.setLastUpdateDay(LocalDate.now().toString()); prefs.setTheme("light");
            activity=(MainActivity)in.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP));
            final MainActivity host=activity; in.waitForIdleSync();
            Trip first=trip("same-name-1","同名旅行",false), second=trip("same-name-2","同名旅行",false), archived=trip("archived-1","同名旅行",true);
            ArrayList<Trip> trips=new ArrayList<>(); trips.add(first);trips.add(second);trips.add(archived);
            in.runOnMainSync(()->{host.trips.clear();host.trips.addAll(trips);host.active=first;host.day=4;host.page=0;host.render();}); in.waitForIdleSync();
            TripPickerUi picker=newPicker(in,host); in.runOnMainSync(picker::show); in.waitForIdleSync();
            checks+=check(picker.dialog.isShowing(),"trip picker shows a dialog"); checks+=check((picker.dialog.getWindow().getAttributes().gravity&Gravity.TOP)!=0,"trip picker window is top aligned");
            checks+=check(picker.list.getChildCount()==2,"default picker excludes archived trips"); checks+=check(card(picker.list,0).getContentDescription().toString().equals("选择旅行：同名旅行")&&card(picker.list,1).getContentDescription().toString().equals("选择旅行：同名旅行"),"duplicate titles expose selection descriptions");
            checks+=check(!second.id.equals(first.id),"duplicate trips retain distinct IDs"); capture(in,"stage2-trips-light.png");
            in.runOnMainSync(()->card(picker.list,1).performClick());in.waitForIdleSync(); checks+=check(host.active==second&&host.day==0,"same-title second card selects its distinct trip");
            in.runOnMainSync(()->{host.active=first;host.day=3;}); Trip beforeCancel=host.active; in.runOnMainSync(()->picker.dialog.dismiss()); in.waitForIdleSync(); TripPickerUi cancelPicker=newPicker(in,host); in.runOnMainSync(cancelPicker::show);in.waitForIdleSync(); in.runOnMainSync(()->findDescription(cancelPicker.surface,"关闭旅行选择").performClick());in.waitForIdleSync(); checks+=check(host.active==beforeCancel,"cancel leaves active trip unchanged");
            TripPickerUi archivedPicker=newPicker(in,host);in.runOnMainSync(archivedPicker::show);in.waitForIdleSync();in.runOnMainSync(()->archivedPicker.footer.performClick());in.waitForIdleSync(); checks+=check(archivedPicker.archived&&archivedPicker.list.getChildCount()==1,"archive toggle shows archived trips");capture(in,"stage2-trips-archived-light.png");in.runOnMainSync(()->card(archivedPicker.list,0).performClick());in.waitForIdleSync();checks+=check(host.active==archived&&host.day==0,"archived trip can be selected and resets day");checks+=check(archived.archived&&!first.archived&&!second.archived,"selection does not change archive state");
            in.runOnMainSync(()->{host.trips.remove(archived);host.active=first;}); TripPickerUi emptyPicker=newPicker(in,host);in.runOnMainSync(emptyPicker::show);in.waitForIdleSync();in.runOnMainSync(()->emptyPicker.footer.performClick());in.waitForIdleSync();checks+=check(emptyPicker.archived&&emptyPicker.list.getChildCount()==1,"empty archive list has a message");in.runOnMainSync(()->emptyPicker.footer.performClick());in.waitForIdleSync();checks+=check(!emptyPicker.archived&&emptyPicker.list.getChildCount()==2,"archive toggle returns to planning list");in.runOnMainSync(emptyPicker.dialog::dismiss);
            in.runOnMainSync(()->{host.active=first;host.page=0;host.render();});in.waitForIdleSync();prefs.setTheme("dark");in.runOnMainSync(host::render);in.waitForIdleSync();TripPickerUi darkPicker=newPicker(in,host);in.runOnMainSync(darkPicker::show);in.waitForIdleSync();capture(in,"stage2-trips-dark.png");in.runOnMainSync(darkPicker.dialog::dismiss);in.waitForIdleSync();
            ArrayList<Trip> many=new ArrayList<>();for(int i=0;i<80;i++)many.add(trip("long-"+i,"长列表旅行 "+i,false));in.runOnMainSync(()->{host.trips.clear();host.trips.addAll(many);host.active=many.get(0);host.page=0;host.render();});in.waitForIdleSync();TripPickerUi longPicker=newPicker(in,host);in.runOnMainSync(longPicker::show);in.waitForIdleSync();int h=host.getResources().getDisplayMetrics().heightPixels;checks+=check(longPicker.surface.getHeight()<=h,"long picker fits the display height");checks+=check(longPicker.scroll.getChildCount()==1&&longPicker.scroll.getChildAt(0).getHeight()>longPicker.scroll.getHeight()&&longPicker.scroll.canScrollVertically(1),"long picker list is scrollable");in.runOnMainSync(longPicker.dialog::dismiss);in.waitForIdleSync();
            checks+=spinnerChecks(in,host);
            java.lang.reflect.Field current=AboutUi.class.getDeclaredField("current");current.setAccessible(true);
            for(String mode:new String[]{"light","dark"}){
                prefs.setTheme(mode);in.runOnMainSync(host::render);in.waitForIdleSync();final AboutUi[] about=new AboutUi[1];in.runOnMainSync(()->{about[0]=new AboutUi(host,()->{});about[0].show();});in.waitForIdleSync();Dialog aboutDialog=(Dialog)current.get(about[0]);checks+=check(aboutDialog!=null&&findDescription(aboutDialog.getWindow().getDecorView(),"返回")!=null,"About page has a back action");capture(in,"stage2-about-"+mode+".png");in.runOnMainSync(aboutDialog::cancel);in.waitForIdleSync();checks+=check(!aboutDialog.isShowing(),"About closes without changing trip");
            }
            return checks;
        } finally { prefs.importJson(original); if(activity!=null){MainActivity closing=activity;in.runOnMainSync(closing::finish);} }
    }
    private static Trip trip(String id,String title,boolean archived){Trip t=new Trip();t.id=id;t.title=title;t.city="测试城";t.start="2026-09-20";t.days=5;t.archived=archived;return t;}
    private static View card(LinearLayout list,int index){return list.getChildAt(index);}
    private static View findDescription(View root,String value){if(root==null)return null;CharSequence d=root.getContentDescription();if(d!=null&&d.toString().contains(value))return root;if(root instanceof ViewGroup){ViewGroup g=(ViewGroup)root;for(int i=0;i<g.getChildCount();i++){View x=findDescription(g.getChildAt(i),value);if(x!=null)return x;}}return null;}
    private static TripPickerUi newPicker(Instrumentation in,MainActivity host){final TripPickerUi[] holder=new TripPickerUi[1];in.runOnMainSync(()->holder[0]=new TripPickerUi(host));return holder[0];}
    private static int spinnerChecks(Instrumentation in,MainActivity host)throws Exception{
        final android.widget.Spinner[] spinner={null};final int[] picked={0};
        in.runOnMainSync(()->{spinner[0]=host.select(host.body,"默认首页测试",new String[]{"旅行","行程","打卡"},"旅行");spinner[0].setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onNothingSelected(android.widget.AdapterView<?> p){}public void onItemSelected(android.widget.AdapterView<?> p,View v,int index,long id){picked[0]=index;}});});in.waitForIdleSync();
        java.lang.reflect.Field field=ChoiceSpinner.class.getDeclaredField("choiceDialog");field.setAccessible(true);
        in.runOnMainSync(()->spinner[0].performClick());in.waitForIdleSync();android.app.AlertDialog dialog=(android.app.AlertDialog)field.get(spinner[0]);
        int count=check(dialog!=null&&dialog.isShowing()&&(dialog.getWindow().getAttributes().gravity&Gravity.VERTICAL_GRAVITY_MASK)==Gravity.TOP,"spinner choices use top window");
        in.runOnMainSync(()->spinner[0].performClick());count+=check(dialog==field.get(spinner[0]),"repeated click keeps one chooser");capture(in,"stage2-selector.png");
        in.runOnMainSync(()->dialog.getListView().performItemClick(null,2,2));in.waitForIdleSync();count+=check(spinner[0].getSelectedItemPosition()==2&&picked[0]==2,"selection updates spinner and listener");
        in.runOnMainSync(()->spinner[0].performClick());in.waitForIdleSync();android.app.AlertDialog cancel=(android.app.AlertDialog)field.get(spinner[0]);in.runOnMainSync(cancel::cancel);in.waitForIdleSync();count+=check(spinner[0].getSelectedItemPosition()==2,"cancel preserves selection");
        in.runOnMainSync(()->spinner[0].performClick());in.waitForIdleSync();android.app.AlertDialog detached=(android.app.AlertDialog)field.get(spinner[0]);in.runOnMainSync(()->((ViewGroup)spinner[0].getParent()).removeView(spinner[0]));in.waitForIdleSync();count+=check(!detached.isShowing(),"detached spinner closes chooser");return count;
    }
    private static int check(boolean value,String label){if(!value)throw new AssertionError(label);return 1;}
    private static void capture(Instrumentation in,String name)throws Exception{SystemClock.sleep(350);Bitmap b=in.getUiAutomation().takeScreenshot();if(b!=null){File f=new File(in.getTargetContext().getExternalFilesDir(null),name);try(FileOutputStream out=new FileOutputStream(f)){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();}}
}
