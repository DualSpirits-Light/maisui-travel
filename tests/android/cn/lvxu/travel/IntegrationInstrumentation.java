package cn.lvxu.travel;
import android.app.Instrumentation;
import android.content.Context;
import android.os.Bundle;
import java.io.*;
import java.util.*;
import java.util.zip.*;
import org.json.*;

/** Run only on a disposable emulator. Restores the original model after assertions. */
public final class IntegrationInstrumentation extends Instrumentation {
 private int checks;private void check(boolean yes,String label){if(!yes)throw new AssertionError(label);checks++;}
 private Bundle arguments;
 @Override public void onCreate(Bundle args){super.onCreate(args);arguments=args;start();}
 @Override public void onStart(){Bundle result=new Bundle();int status=-1;TripStore store=null;ArrayList<Trip> original=null;AppPrefs prefs=null;JSONObject originalPrefs=null;
  try{Context c=getTargetContext();store=new TripStore(c);original=store.exists()?store.read():new ArrayList<>();prefs=new AppPrefs(c);originalPrefs=prefs.exportJson();ArrayList<Trip> sample=new ArrayList<>();Trip t=Trip.demo();sample.add(t);store.save(sample);check(store.read().get(0).stops.size()==6,"SQLite roundtrip");
   Trip.Expense expense=new Trip.Expense();expense.name="测试";expense.amount=1234;expense.categoryId=t.categories.get(0).id;expense.occurredAt="2026-09-07T12:30";t.expenses.add(expense);store.save(sample);check(store.read().get(0).spent()==1234,"SQLite expense persistence");
   MediaFiles media=new MediaFiles(c);String photo=media.newPath("test",".jpg");File f=MediaFiles.file(c,photo);f.getParentFile().mkdirs();try(OutputStream out=new FileOutputStream(f)){out.write(new byte[]{1,2,3,4});}t.items.get(0).photo=photo;
   String preview=media.newPath("test",".jpg"),note=media.newPath("test",".jpg"),group=media.newPath("test",".jpg"),scenery=media.newPath("test",".jpg");for(String path:new String[]{preview,note,group,scenery}){File destination=MediaFiles.file(c,path);destination.getParentFile().mkdirs();java.nio.file.Files.copy(f.toPath(),destination.toPath());}
   t.stops.get(0).previewPhoto=preview;t.stops.get(0).notePhotos.add(note);Trip.Checkin checkin=new Trip.Checkin();checkin.time="2026-09-08T12:00";checkin.groupPhotos.add(group);checkin.sceneryPhotos.add(scenery);t.checkins.add(checkin);prefs.setShowPlaceCoordinates(true);store.save(sample);
   ByteArrayOutputStream archive=new ByteArrayOutputStream();BackupArchive.write(c,sample,prefs,archive);check(archive.size()>0,"backup writes ZIP");HashSet<String> entries=new HashSet<>();try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(archive.toByteArray()))){for(ZipEntry e;(e=zip.getNextEntry())!=null;)entries.add(e.getName());}check(entries.containsAll(Arrays.asList(preview,note,group,scenery)),"all photo albums included in ZIP");for(String path:new String[]{preview,note,group,scenery})MediaFiles.file(c,path).delete();prefs.setShowPlaceCoordinates(false);int count=BackupArchive.restore(c,new ByteArrayInputStream(archive.toByteArray()),store,sample,prefs);ArrayList<Trip> restored=store.read();check(count==1&&restored.size()==2,"backup merge");check(!restored.get(0).id.equals(restored.get(1).id),"restore duplicate IDs remapped");check(MediaFiles.file(c,restored.get(1).items.get(0).photo).isFile(),"backup media reference");check(prefs.showPlaceCoordinates(),"advanced preference restored");for(String path:new String[]{preview,note,group,scenery})check(MediaFiles.file(c,path).isFile(),"restored album file");
   String before=TripStore.encode(store.read());ByteArrayOutputStream evil=new ByteArrayOutputStream();try(ZipOutputStream zip=new ZipOutputStream(evil)){zip.putNextEntry(new ZipEntry("../escape"));zip.write(1);zip.closeEntry();}boolean rejected=false;try{BackupArchive.restore(c,new ByteArrayInputStream(evil.toByteArray()),store,restored,prefs);}catch(Exception expected){rejected=true;}check(rejected,"ZIP traversal rejected");check(before.equals(TripStore.encode(store.read())),"rejected restore unchanged");
   rejected=false;try{MediaFiles.file(c,"../settings.xml");}catch(IllegalArgumentException expected){rejected=true;}check(rejected,"media path confinement");
   // A large snapshot must not exceed Android CursorWindow's per-row allocation.
   Trip large=Trip.demo();large.items.clear();String longNote=new String(new char[1800]).replace('\0','旅');for(int i=0;i<700;i++){Trip.Item item=new Trip.Item("项目 "+i);item.note=longNote;large.items.add(item);}sample.clear();sample.add(large);store.save(sample);check(store.read().get(0).items.size()==700,"large SQLite snapshot chunk read");
   if(arguments!=null&&arguments.getString("activationCode")!=null){android.content.SharedPreferences isolated=c.getSharedPreferences("app-prefs-v2",Context.MODE_PRIVATE);Map<String,?> oldLicense=new HashMap<>(isolated.getAll());try{String code=arguments.getString("activationCode");check(new ActivationService(prefs).activate(code).equals("IntegrationTest"),"signed activation accepted");check(prefs.paid(),"activation persisted");boolean invalid=false;try{String[] parts=code.split("\\.");String payload=parts[1];parts[1]=(payload.charAt(0)=='A'?"B":"A")+payload.substring(1);new ActivationService(prefs).activate(String.join(".",parts));}catch(Exception expected){invalid=true;}check(invalid,"tampered activation rejected");prefs.setActivation("ExpiredTest","2000-01-01");check(!prefs.paid(),"expired license rejected on read");}finally{android.content.SharedPreferences.Editor edit=isolated.edit().clear();for(Map.Entry<String,?> entry:oldLicense.entrySet()){Object value=entry.getValue();if(value instanceof String)edit.putString(entry.getKey(),(String)value);else if(value instanceof Boolean)edit.putBoolean(entry.getKey(),(Boolean)value);else if(value instanceof Long)edit.putLong(entry.getKey(),(Long)value);else if(value instanceof Integer)edit.putInt(entry.getKey(),(Integer)value);else if(value instanceof Float)edit.putFloat(entry.getKey(),(Float)value);}edit.commit();}}
   if(arguments!=null&&arguments.getString("fixtureCertificateHash")!=null)checks+=WebDavFixtureTest.run(c,arguments.getString("fixtureCertificateHash"));
   for(String path:new String[]{preview,note,group,scenery})MediaFiles.file(c,path).delete();f.delete();result.putString("stream","PASS: "+checks+" Android integration assertions\n");
  }catch(Throwable e){status=0;result.putString("stream","FAIL: "+e+"\n"+android.util.Log.getStackTraceString(e));}
  finally{try{if(store!=null&&original!=null)store.save(original);if(prefs!=null&&originalPrefs!=null)prefs.importJson(originalPrefs);}catch(Exception ignored){}if(store!=null)store.close();}
  finish(status,result);
 }
}
