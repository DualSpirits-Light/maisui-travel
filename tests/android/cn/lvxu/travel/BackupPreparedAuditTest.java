package cn.lvxu.travel;

import android.app.Instrumentation;
import android.content.Context;
import android.database.Cursor;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.io.*;
import java.util.*;
import org.json.JSONObject;

/** Real SQLite regressions for detached backup preparation and current-store commits. */
final class BackupPreparedAuditTest {
    static int run(Instrumentation in)throws Exception {
        Context context=in.getTargetContext();TripStore store=new TripStore(context);AppPrefs prefs=new AppPrefs(context);
        ArrayList<Trip> originalTrips=store.read();JSONObject originalPrefs=prefs.exportJson();
        Set<String> oldStages=stages(context);BackupArchive.PreparedRestore prepared=null;
        TestStartupGuard guard=new TestStartupGuard(context);int n=0;
        try {
            // Avoid including any pre-existing user avatar/background media in this fixture.
            prefs.setAvatar("");prefs.setBackground("");
            Trip source=Trip.demo();source.title="备份准备审计";source.items.get(0).note="备份清单";
            Trip.Checkin memory=new Trip.Checkin();memory.place=source.stops.get(0).name;
            memory.time=source.start+"T09:00";memory.stopId=source.stops.get(0).id;source.checkins.add(memory);
            onMain(in,()->store.save(Collections.singletonList(source)));
            ByteArrayOutputStream output=new ByteArrayOutputStream();BackupArchive.write(context,Collections.singletonList(source),prefs,output);byte[] bytes=output.toByteArray();

            prepared=BackupArchive.prepare(context,new ByteArrayInputStream(bytes));
            BackupArchive.PreparedRestore same=prepared;int[] added={-1};
            onMain(in,()->added[0]=same.commit(store,prefs));
            n+=check(added[0]==0,"same-ID identical backup skips adding a trip");
            ArrayList<Trip> unchanged=store.read();n+=check(unchanged.size()==1&&unchanged.get(0).id.equals(source.id),"same-ID restore keeps one durable SQLite trip");
            prepared.close();prepared=null;

            prefs.setSlogan("准备阶段不得写入偏好");String before=TripStore.encode(store.read());
            prepared=BackupArchive.prepare(context,new ByteArrayInputStream(bytes));
            n+=check(before.equals(TripStore.encode(store.read()))&&prefs.slogan().equals("准备阶段不得写入偏好"),"prepare does not change current SQLite snapshot or preferences");
            Set<String> staged=stages(context);staged.removeAll(oldStages);
            n+=check(staged.size()==1,"prepare owns one private staging directory");

            Trip edited=Trip.from(source.json());edited.items.get(0).note="准备之后的新修改";
            Trip extra=Trip.demo();extra.title="准备之后新增旅行";
            onMain(in,()->store.save(Arrays.asList(edited,extra)));
            BackupArchive.PreparedRestore conflict=prepared;onMain(in,()->added[0]=conflict.commit(store,prefs));
            n+=check(added[0]==1,"conflicting same-ID backup adds one copy");
            ArrayList<Trip> restored=store.read();Trip current=find(restored,source.id),newTrip=find(restored,extra.id),copy=null;
            for(Trip t:restored)if(!t.id.equals(source.id)&&!t.id.equals(extra.id))copy=t;
            n+=check(current!=null&&current.items.get(0).note.equals("准备之后的新修改"),"commit preserves edits made after preparation");
            n+=check(newTrip!=null&&newTrip.title.equals(extra.title)&&restored.size()==3,"commit preserves trips added after preparation");
            n+=check(copy!=null&&!copy.id.equals(source.id)&&copy.title.endsWith("（恢复副本）"),"conflict copy has independent trip ID and visible title");
            n+=check(!copy.stops.get(0).id.equals(source.stops.get(0).id)&&copy.checkins.get(0).stopId.equals(copy.stops.get(0).id),"copy remaps stop ID and linked checkin together");
            n+=check(!copy.checkins.get(0).id.equals(memory.id)&&copy.items.get(0).note.equals("备份清单"),"copy retains backup content and regenerates checkin ID");
            prepared.close();prepared=null;
            n+=check(stages(context).equals(oldStages),"close removes only this restore's staging directory");

            prepared=BackupArchive.prepare(context,new ByteArrayInputStream(bytes));BackupArchive.PreparedRestore repeated=prepared;
            onMain(in,()->added[0]=repeated.commit(store,prefs));
            n+=check(added[0]==0&&store.read().size()==3,"repeating the same conflict backup skips its unchanged restored copy");
            prepared.close();prepared=null;

            prepared=BackupArchive.prepare(context,new ByteArrayInputStream(bytes));prepared.close();prepared.close();prepared=null;
            n+=check(stages(context).equals(oldStages),"abandoned preparation cleanup is idempotent and preserves prior stages");

            String damaged="{\"schema\":2,\"trips\":[BROKEN-AUDIT-SNAPSHOT";Set<String> oldEvidence=recoveryDirs(context);
            onMain(in,()->store.getWritableDatabase().execSQL("UPDATE snapshot SET json=? WHERE id=1",new Object[]{damaged}));
            prepared=BackupArchive.prepare(context,new ByteArrayInputStream(bytes));BackupArchive.PreparedRestore recovery=prepared;
            boolean refused=false;try{onMain(in,()->recovery.commit(store,prefs));}catch(IOException expected){refused=true;}
            n+=check(refused&&damaged.equals(rawSnapshot(store)),"default commit rejects a damaged snapshot and leaves its original text intact");
            n+=check(recoveryDirs(context).equals(oldEvidence),"default rejection creates no recovery evidence or overwrite");
            onMain(in,()->added[0]=recovery.commit(store,prefs,true));
            ArrayList<Trip> recovered=store.read();n+=check(added[0]==1&&recovered.size()==1&&recovered.get(0).id.equals(source.id),"explicit unreadable recovery restores a validated backup to real SQLite");
            Set<String> evidence=recoveryDirs(context);evidence.removeAll(oldEvidence);n+=check(evidence.size()==1,"explicit recovery creates one private evidence bundle");
            File bundle=new File(context.getFilesDir(),"recovery/"+evidence.iterator().next());
            n+=check(new File(bundle,"trips-v2.db").isFile()&&damaged.equals(new String(Files.readAllBytes(new File(bundle,"snapshot.json").toPath()),StandardCharsets.UTF_8)),"original database and exact damaged snapshot text remain available after recovery");
            prepared.close();prepared=null;
            return n;
        }finally {
            if(prepared!=null)prepared.close();
            try{onMain(in,()->store.save(originalTrips));}
            finally{try{prefs.importJson(originalPrefs);}finally{guard.close();store.close();}}
        }
    }
    private static Trip find(List<Trip> trips,String id){for(Trip t:trips)if(t.id.equals(id))return t;return null;}
    private static String rawSnapshot(TripStore store){Cursor c=store.getReadableDatabase().rawQuery("SELECT json FROM snapshot WHERE id=1",null);try{return c.moveToFirst()?c.getString(0):null;}finally{c.close();}}
    private static Set<String> recoveryDirs(Context context){Set<String> out=new HashSet<>();File[] files=new File(context.getFilesDir(),"recovery").listFiles();if(files!=null)for(File file:files)if(file.isDirectory()&&file.getName().startsWith("restore-"))out.add(file.getName());return out;}
    private static Set<String> stages(Context context){Set<String> out=new HashSet<>();File[] files=context.getCacheDir().listFiles();if(files!=null)for(File file:files)if(file.isDirectory()&&file.getName().startsWith("restore-"))out.add(file.getName());return out;}
    private interface CheckedAction {void run()throws Exception;}
    private static void onMain(Instrumentation in,CheckedAction action)throws Exception {Throwable[] failure={null};in.runOnMainSync(()->{try{action.run();}catch(Throwable e){failure[0]=e;}});if(failure[0] instanceof Exception)throw (Exception)failure[0];if(failure[0]!=null)throw new AssertionError("main-thread backup audit failed",failure[0]);}
    private static int check(boolean condition,String message){if(!condition)throw new AssertionError(message);return 1;}
}
