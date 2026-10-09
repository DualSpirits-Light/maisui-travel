package cn.lvxu.travel;

import android.app.AlertDialog;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import java.lang.reflect.Field;
import java.util.ArrayList;

/** Audit regressions: classified photos, complete removal, validation and SQLite failure. */
final class CheckinAuditUiTest {
    static int run(Instrumentation in) throws Exception {
        Context context=in.getTargetContext();AppPrefs prefs=new AppPrefs(context);
        org.json.JSONObject originalPrefs=prefs.exportJson();MainActivity host=null;
        ArrayList<Trip> originalTrips=null;CheckinUi ui=null;int n=0;
        try {
            prefs.setTutorialDone(true);prefs.setLastUpdateDay(java.time.LocalDate.now().toString());
            host=(MainActivity)in.startActivitySync(new Intent(context,MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP));
            in.waitForIdleSync();originalTrips=host.store.read();MainActivity a=host;
            Trip trip=new Trip();trip.title="打卡审计测试";trip.city="兰州市";trip.start="2026-10-05";trip.days=2;
            Trip.Checkin c=new Trip.Checkin();c.place="原地点";c.time="2026-10-05T09:00";
            c.sceneryPhotos.add("media/audit/scenery.jpg");c.photo="media/audit/scenery.jpg";trip.checkins.add(c);
            CheckinUi checkins=new CheckinUi(a,a.media);ui=checkins;
            in.runOnMainSync(()->{a.trips.clear();a.trips.add(trip);a.active=trip;checkins.beginEdit(c);});
            in.waitForIdleSync();AlertDialog dialog=(AlertDialog)get(checkins,"editor");
            n+=check(((ArrayList<?>)get(checkins,"groupPhotos")).isEmpty(),"scenery-only edit adds no group photo");
            n+=check(findDescription(dialog.getWindow().getDecorView(),"预览风景照片 1")!=null,"selected photo exposes a preview thumbnail");
            AlertDialog firstSave=dialog;
            in.runOnMainSync(()->firstSave.getButton(-1).performClick());in.waitForIdleSync();
            Trip.Checkin reread=a.store.read().get(0).checkins.get(0);
            n+=check(reread.groupPhotos.isEmpty()&&reread.sceneryPhotos.size()==1,"unchanged save keeps scenery classification on disk; showing="+firstSave.isShowing()+", model="+c.json()+", stored="+reread.json()+", time="+((EditText)get(checkins,"time")).getText());
            in.runOnMainSync(()->checkins.beginEdit(c));in.waitForIdleSync();dialog=(AlertDialog)get(checkins,"editor");
            View remove=findText(dialog.getWindow().getDecorView(),"移除全部照片");
            in.runOnMainSync(remove::performClick);AlertDialog clearing=dialog;
            in.runOnMainSync(()->clearing.getButton(-1).performClick());in.waitForIdleSync();
            reread=a.store.read().get(0).checkins.get(0);
            n+=check(c.photo.isEmpty()&&c.groupPhotos.isEmpty()&&c.sceneryPhotos.isEmpty(),"remove-all clears saved cover and groups");
            n+=check(reread.photo.isEmpty()&&reread.groupPhotos.isEmpty()&&reread.sceneryPhotos.isEmpty(),"removed cover cannot resurrect after reload");
            in.runOnMainSync(()->checkins.beginEdit(c));in.waitForIdleSync();AlertDialog draft=(AlertDialog)get(checkins,"editor");
            EditText people=(EditText)get(checkins,"companions"),place=(EditText)get(checkins,"place");
            in.runOnMainSync(()->{place.setText("未保存草稿");people.setText("人".repeat(41));draft.getButton(-1).performClick();});in.waitForIdleSync();
            n+=check(draft.isShowing()&&people.getText().length()==41&&place.getText().toString().equals("未保存草稿"),"overlong companion preserves open draft");
            n+=check(c.place.equals("原地点")&&c.companions.isEmpty(),"invalid input leaves stored record unchanged");
            // A real SQLite abort checks the failure path after validation and candidate application.
            a.store.getWritableDatabase().execSQL("CREATE TEMP TRIGGER checkin_audit_fail BEFORE INSERT ON snapshot BEGIN SELECT RAISE(ABORT,'audit write failure'); END");
            in.runOnMainSync(()->{people.setText("小王");draft.getButton(-1).performClick();});in.waitForIdleSync();
            n+=check(draft.isShowing()&&place.getText().toString().equals("未保存草稿"),"write failure leaves draft editable");
            n+=check(c.place.equals("原地点")&&c.companions.isEmpty()&&trip.checkins.size()==1,"write failure rolls back record without duplicates");
            a.store.getWritableDatabase().execSQL("DROP TRIGGER checkin_audit_fail");
            in.runOnMainSync(()->draft.getButton(-1).performClick());in.waitForIdleSync();
            n+=check(!draft.isShowing()&&c.place.equals("未保存草稿")&&c.companions.size()==1,"retry persists successfully before closing");
            n+=check(a.store.read().get(0).checkins.get(0).place.equals("未保存草稿"),"retry is durable on disk");
            in.runOnMainSync(checkins::beginNew);in.waitForIdleSync();AlertDialog newDraft=(AlertDialog)get(checkins,"editor");
            in.runOnMainSync(()->checkins.photoSelected("media/audit/new.jpg","group"));in.waitForIdleSync();
            n+=check(get(checkins,"locationSession")==null&&get(checkins,"locationManager")==null,"adding photo does not start location acquisition");
            n+=check(findText(newDraft.getWindow().getDecorView(),"使用当前位置")!=null,"manual location action stays available");
            String owned="media/audit/cancel-"+Trip.uid()+".jpg",shared="media/audit/shared-"+Trip.uid()+".jpg";
            java.io.File ownFile=a.mediaFile(owned),sharedFile=a.mediaFile(shared);ownFile.getParentFile().mkdirs();java.nio.file.Files.write(ownFile.toPath(),new byte[]{1,2});java.nio.file.Files.write(sharedFile.toPath(),new byte[]{1,2});Trip.Stop reference=new Trip.Stop();reference.name="共享照片地点";reference.previewPhoto=shared;trip.stops.add(reference);
            in.runOnMainSync(()->{checkins.photoSelected(owned,"group");checkins.photoSelected(shared,"scenery");newDraft.dismiss();});in.waitForIdleSync();n+=check(!ownFile.exists(),"cancelled newly imported photo is released");n+=check(sharedFile.isFile(),"cancel leaves photo referenced by another record intact");sharedFile.delete();
            in.runOnMainSync(checkins::beginNew);in.waitForIdleSync();AlertDialog itemDraft=(AlertDialog)get(checkins,"editor");String itemPhoto="media/audit/item-"+Trip.uid()+".jpg";java.io.File itemFile=a.mediaFile(itemPhoto);java.nio.file.Files.write(itemFile.toPath(),new byte[]{1,2});Trip.Item item=new Trip.Item("清单共享照片");item.photo=itemPhoto;trip.items.add(item);in.runOnMainSync(()->{checkins.photoSelected(itemPhoto,"group");itemDraft.dismiss();});in.waitForIdleSync();n+=check(itemFile.isFile(),"cancel leaves photo referenced by checklist intact");itemFile.delete();
            return n;
        } finally {
            if(host!=null){MainActivity a=host;
                a.store.getWritableDatabase().execSQL("DROP TRIGGER IF EXISTS checkin_audit_fail");
                if(ui!=null){AlertDialog dialog=(AlertDialog)get(ui,"editor");if(dialog!=null)in.runOnMainSync(dialog::dismiss);}
                if(originalTrips!=null)a.store.save(originalTrips);
                in.runOnMainSync(a::finish);a.store.close();
            }
            prefs.importJson(originalPrefs);
        }
    }
    private static Object get(Object o,String name)throws Exception{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    private static int check(boolean ok,String name){if(!ok)throw new AssertionError(name);return 1;}
    private static View findText(View v,String text){if(v instanceof TextView&&((TextView)v).getText().toString().equals(text))return v;return descend(v,text,false);}
    private static View findDescription(View v,String text){if(text.contentEquals(v.getContentDescription()==null?"":v.getContentDescription()))return v;return descend(v,text,true);}
    private static View descend(View v,String text,boolean description){if(v instanceof ViewGroup){ViewGroup group=(ViewGroup)v;for(int i=0;i<group.getChildCount();i++){View found=description?findDescription(group.getChildAt(i),text):findText(group.getChildAt(i),text);if(found!=null)return found;}}return null;}
}
