package cn.lvxu.travel;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.SystemClock;
import java.io.*;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

final class QuickLookUiTest {
    static int run(Instrumentation in)throws Exception {
        Context context=in.getTargetContext();AppPrefs prefs=new AppPrefs(context);org.json.JSONObject original=prefs.exportJson();MainActivity host=null;final QuickLookUi[] ui={null};int n=0;
        try{
            prefs.setTutorialDone(true);prefs.setLastUpdateDay(LocalDate.now().toString());
            host=(MainActivity)in.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP));final MainActivity a=host;in.waitForIdleSync();
            Trip trip=Trip.demo();String before=trip.json().toString();AtomicInteger calls=new AtomicInteger();Set<String> successful=new HashSet<>();
            in.runOnMainSync(()->{ui[0]=new QuickLookUi(a,ItineraryImageRenderer::render,(c,f,name)->{int count=calls.incrementAndGet();if(count==2)throw new IOException("测试保存失败");if(!successful.add(f.getAbsolutePath()))throw new AssertionError("duplicate save");return Uri.parse("content://test/"+count);});ui[0].show(trip,1);});
            QuickLookUi q=ui[0];n+=check(q.dates.get(1).isChecked()&&!q.dates.get(0).isChecked(),"selected date default");n+=check(q.notes.isChecked()&&q.addresses.isChecked()&&q.tags.isChecked()&&!q.costs.isChecked()&&!q.photos.isChecked(),"private content opt in");
            in.runOnMainSync(()->{q.dates.get(0).setChecked(true);q.generate();q.generate();});await(in,()->q.preview!=null);
            n+=check(q.sheets.size()>=2&&calls.get()==0,"generate does not save");n+=check(trip.json().toString().equals(before),"export keeps source unchanged");
            SystemClock.sleep(600);Bitmap shot=in.getUiAutomation().takeScreenshot();if(shot!=null){File folder=new File(context.getExternalFilesDir(null),"stage3-evidence");folder.mkdirs();try(FileOutputStream out=new FileOutputStream(new File(folder,"quicklook-preview.png"))){shot.compress(Bitmap.CompressFormat.PNG,100,out);}shot.recycle();}
            in.runOnMainSync(()->{q.permissionPending=true;q.onRequestPermissionsResult(QuickLookUi.STORAGE_REQUEST,new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE},new int[]{-1});});n+=check(q.preview!=null&&q.status.getText().toString().contains("预览已保留"),"denied permission retains preview");
            // Invoke granted continuation independently of emulator API level.
            in.runOnMainSync(()->{q.permissionPending=true;q.onRequestPermissionsResult(QuickLookUi.STORAGE_REQUEST,new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE},new int[]{0});});await(in,()->!q.busy);n+=check(q.saved.size()==1&&q.status.getText().toString().contains("其余未保存"),"partial save reports actual count");
            in.runOnMainSync(()->{q.permissionPending=true;q.onRequestPermissionsResult(QuickLookUi.STORAGE_REQUEST,new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE},new int[]{0});});await(in,()->!q.busy);n+=check(q.saved.size()==q.sheets.size()&&calls.get()==q.sheets.size()+1,"retry skips successful images");
            File old=q.directory;in.runOnMainSync(q::destroy);await(in,()->!old.exists());n+=check(q.dialog==null,"destroy dismisses and cleans cache");
            CountDownLatch started=new CountDownLatch(1),release=new CountDownLatch(1);final Trip[] captured={null};final File[] temp={null};
            in.runOnMainSync(()->{ui[0]=new QuickLookUi(a,(c,t,d,o,dir)->{captured[0]=t;temp[0]=dir;started.countDown();release.await(5,TimeUnit.SECONDS);return ItineraryImageRenderer.render(c,t,d,o,dir);},(c,f,name)->Uri.EMPTY);ui[0].show(trip,0);ui[0].generate();});
            if(!started.await(5,TimeUnit.SECONDS))throw new AssertionError("worker did not start");QuickLookUi cancelled=ui[0];in.runOnMainSync(()->{trip.title="changed after snapshot";cancelled.destroy();});release.countDown();await(in,()->temp[0]!=null&&!temp[0].exists());
            n+=check(!captured[0].title.equals(trip.title)&&cancelled.dialog==null&&cancelled.preview==null,"snapshot isolated and stale completion suppressed");return n;
        }finally{if(ui[0]!=null)in.runOnMainSync(ui[0]::destroy);prefs.importJson(original);if(host!=null){MainActivity end=host;in.runOnMainSync(end::finish);}}
    }
    interface Condition{boolean ok();}
    private static void await(Instrumentation in,Condition c)throws Exception{long end=SystemClock.uptimeMillis()+15000;while(SystemClock.uptimeMillis()<end){in.waitForIdleSync();if(c.ok())return;SystemClock.sleep(50);}throw new AssertionError("quicklook timed out");}
    private static int check(boolean condition,String label){if(!condition)throw new AssertionError(label);return 1;}
}
