package cn.lvxu.travel;
import android.app.*;import android.content.*;import android.graphics.Bitmap;import android.net.Uri;import android.os.Bundle;import android.view.*;import android.widget.*;import java.io.File;import java.util.*;
/** Recreates form controllers and exercises a result that arrives before its form attaches. */
final class MediaDraftResumeTest {
 static int run(Instrumentation in)throws Exception{
  Context context=in.getTargetContext();TestStartupGuard startup=new TestStartupGuard(context);MainActivity host=(MainActivity)in.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));in.waitForIdleSync();
  ArrayList<String> created=new ArrayList<>();int checks=0;Trip trip=Trip.demo();
  try{
   final Bundle[] draft={null};final FinanceUi[] ui={null};
   in.runOnMainSync(()->{host.trips.clear();host.trips.add(trip);host.active=trip;ui[0]=new FinanceUi(host);ui[0].expense(null);});in.waitForIdleSync();
   LinearLayout form=(LinearLayout)get(ui[0],"draftForm");ArrayList<EditText> fields=new ArrayList<>();collect(form,fields);
   in.runOnMainSync(()->{fields.get(0).setText("未保存的餐费");fields.get(1).setText("28.5.0");fields.get(2).setText("2026-10-12 13:40");Bundle b=new Bundle();ui[0].saveState(b);draft[0]=b;((AlertDialog)getUnchecked(ui[0],"draftDialog")).dismiss();FinanceUi next=new FinanceUi(host);next.restoreState(b);next.reopenDraft();ui[0]=next;});in.waitForIdleSync();
   fields.clear();collect((View)get(ui[0],"draftForm"),fields);if(!fields.get(0).getText().toString().equals("未保存的餐费")||!fields.get(1).getText().toString().equals("28.5.0")||!fields.get(2).getText().toString().equals("2026-10-12 13:40"))throw new AssertionError("expense draft fields changed on recreation");checks++;
   in.runOnMainSync(()->((AlertDialog)getUnchecked(ui[0],"draftDialog")).dismiss());if(!trip.expenses.isEmpty())throw new AssertionError("draft restoration wrote expense");checks++;
   String source=fixture(host);created.add(source);final MediaController[] media={null};final String[] received={null};Bundle pending=new Bundle();pending.putString("media.owner","PLACE_PREVIEW");pending.putBoolean("media.crop",false);
   in.runOnMainSync(()->{media[0]=new MediaController(host);media[0].restoreState(pending);media[0].onResult(MediaController.PICK,Activity.RESULT_OK,new Intent().setData(Uri.fromFile(host.mediaFile(source))));});if(received[0]!=null)throw new AssertionError("unattached result should remain pending");checks++;
   Bundle queued=new Bundle();in.runOnMainSync(()->{media[0].saveState(queued);media[0]=new MediaController(host);media[0].restoreState(queued);media[0].attachSelection(MediaController.SelectionOwner.EXPENSE,path->received[0]=path);});if(received[0]!=null)throw new AssertionError("wrong form consumed result");checks++;
   in.runOnMainSync(()->media[0].attachSelection(MediaController.SelectionOwner.PLACE_PREVIEW,path->received[0]=path));for(int i=0;i<100&&received[0]==null;i++)android.os.SystemClock.sleep(50);in.waitForIdleSync();if(received[0]==null||!host.mediaFile(received[0]).isFile())throw new AssertionError("queued original result lost");created.add(received[0]);checks++;
   File cameraDirectory=new File(host.getCacheDir(),"camera");cameraDirectory.mkdirs();File camera=File.createTempFile("resume-camera-",".jpg",cameraDirectory);java.nio.file.Files.copy(host.mediaFile(source).toPath(),camera.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);
   Bundle cameraState=new Bundle();cameraState.putString("media.owner","EXPENSE");cameraState.putBoolean("media.crop",false);cameraState.putString("media.camera",camera.getAbsolutePath());final String[] capture={null};
   in.runOnMainSync(()->{media[0]=new MediaController(host);media[0].restoreState(cameraState);media[0].onResult(MediaController.CAMERA,Activity.RESULT_OK,null);Bundle b=new Bundle();media[0].saveState(b);media[0]=new MediaController(host);media[0].restoreState(b);media[0].attachSelection(MediaController.SelectionOwner.EXPENSE,path->capture[0]=path);});
   for(int i=0;i<100&&capture[0]==null;i++)android.os.SystemClock.sleep(50);in.waitForIdleSync();if(capture[0]==null||camera.exists()||!java.util.Arrays.equals(java.nio.file.Files.readAllBytes(host.mediaFile(source).toPath()),java.nio.file.Files.readAllBytes(host.mediaFile(capture[0]).toPath())))throw new AssertionError("restored camera original result lost or temp file leaked");created.add(capture[0]);checks++;
   java.util.concurrent.CountDownLatch started=new java.util.concurrent.CountDownLatch(1),release=new java.util.concurrent.CountDownLatch(1);final String[] oldImport={null},resumedImport={null};final MediaController[] importing={null};final Bundle inflight=new Bundle();
   MediaController.originalImportBarrier=()->{started.countDown();try{if(!release.await(10,java.util.concurrent.TimeUnit.SECONDS))throw new AssertionError("import barrier timeout");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}};
   try{
    in.runOnMainSync(()->{importing[0]=new MediaController(host);importing[0].restoreState(pending);importing[0].attachSelection(MediaController.SelectionOwner.PLACE_PREVIEW,path->oldImport[0]=path);importing[0].onResult(MediaController.PICK,Activity.RESULT_OK,new Intent().setData(Uri.fromFile(host.mediaFile(source))));});
    if(!started.await(5,java.util.concurrent.TimeUnit.SECONDS))throw new AssertionError("actual import worker did not enter barrier");
    in.runOnMainSync(()->{importing[0].saveState(inflight);media[0]=new MediaController(host);media[0].restoreState(inflight);media[0].attachSelection(MediaController.SelectionOwner.PLACE_PREVIEW,path->resumedImport[0]=path);importing[0].detachSelection(MediaController.SelectionOwner.PLACE_PREVIEW);});
    if(!"PLACE_PREVIEW".equals(inflight.getString("media.owner"))||inflight.getString("media.import.token")==null)throw new AssertionError("inflight owner/token missing from saved state");checks++;
    release.countDown();for(int i=0;i<100&&resumedImport[0]==null;i++)android.os.SystemClock.sleep(50);in.waitForIdleSync();if(oldImport[0]!=null||resumedImport[0]==null||!host.mediaFile(resumedImport[0]).isFile())throw new AssertionError("worker result sent to obsolete controller or deleted");created.add(resumedImport[0]);checks++;
    // Simulate losing the application job registry after completion but before the saved state is reopened.
    Object jobs=getStatic(MediaController.class,"IMPORTS");((java.util.Map<?,?>)jobs).remove(inflight.getString("media.import.token"));final String[] recovered={null};
    in.runOnMainSync(()->{media[0]=new MediaController(host);media[0].restoreState(inflight);media[0].attachSelection(MediaController.SelectionOwner.PLACE_PREVIEW,path->recovered[0]=path);});
    if(!resumedImport[0].equals(recovered[0]))throw new AssertionError("completed import receipt copied or lost existing result");checks++;
   }finally{release.countDown();MediaController.originalImportBarrier=null;}
   Trip.Stop stop=trip.stops.get(0);stop.previewPhoto=source;Bundle photo=new Bundle();photo.putString("place.media.preview",source);photo.putStringArrayList("place.media.notes",new ArrayList<>(Arrays.asList(received[0])));photo.putStringArrayList("place.media.owned",new ArrayList<>(Arrays.asList(source,received[0])));
   in.runOnMainSync(()->{PlaceMediaUi restored=new PlaceMediaUi(host,stop);restored.restoreState(photo);restored.closeDraft(false);});if(!host.mediaFile(source).isFile()||host.mediaFile(received[0]).exists())throw new AssertionError("draft cleanup removed reference or leaked uncommitted file");checks++;
   if(!FinanceUi.money(2800).equals("28.00")||!FinanceUi.money(2850).equals("28.50"))throw new AssertionError("finance precision differs");checks++;
   // Exercise MainActivity's actual save/restore hook with a real stop editor, rather than only the generic helper.
   final Bundle[] stopState={null};final String originalName=stop.name;final ArrayList<EditText> stopFields=new ArrayList<>();
   in.runOnMainSync(()->host.stopEditor(stop));in.waitForIdleSync();collect((View)get(host,"stopDraftForm"),stopFields);
   in.runOnMainSync(()->{stopFields.get(0).setText("相册期间未保存地点");stopFields.get(1).setText("14:25");stopFields.get(2).setText("125");((View)getUnchecked(host,"stopDraftExtra")).setVisibility(View.VISIBLE);Bundle b=new Bundle();host.onSaveInstanceState(b);stopState[0]=b;((AlertDialog)getUnchecked(host,"stopDraftDialog")).dismiss();host.media=new MediaController(host);host.media.restoreState(b);invoke(host,"restoreStopDraft",b);});in.waitForIdleSync();
   stopFields.clear();collect((View)get(host,"stopDraftForm"),stopFields);if(!stopFields.get(0).getText().toString().equals("相册期间未保存地点")||!stopFields.get(1).getText().toString().equals("14:25")||!stopFields.get(2).getText().toString().equals("125")||((View)get(host,"stopDraftExtra")).getVisibility()!=View.VISIBLE)throw new AssertionError("Main stop draft restore lost name/time/duration/expanded fields");checks++;
   Bundle restoredPhotos=new Bundle();((PlaceMediaUi)get(host,"stopDraftPhotos")).saveState(restoredPhotos);if(!source.equals(restoredPhotos.getString("place.media.preview"))||!stop.name.equals(originalName))throw new AssertionError("stop photo draft changed or restoration wrote model");checks++;
   in.runOnMainSync(()->((AlertDialog)getUnchecked(host,"stopDraftDialog")).dismiss());if(!host.mediaFile(source).isFile())throw new AssertionError("restored stop cancel removed shared preview");checks++;
   return checks;
  }finally{in.runOnMainSync(host::finish);in.waitForIdleSync();for(String path:created){File file=host.mediaFile(path);if(file!=null)file.delete();}startup.close();}
 }
 private static String fixture(MainActivity a)throws Exception{Bitmap b=Bitmap.createBitmap(18,12,Bitmap.Config.ARGB_8888);try{return a.media.files.save(b,"test",90);}finally{b.recycle();}}
 private static void collect(View v,List<EditText> out){if(v instanceof EditText)out.add((EditText)v);else if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++)collect(g.getChildAt(i),out);}}
 private static void invoke(MainActivity host,String name,Bundle state){try{java.lang.reflect.Method method=MainActivity.class.getDeclaredMethod(name,Bundle.class);method.setAccessible(true);method.invoke(host,state);}catch(Exception e){throw new AssertionError(e);}}
 private static Object getStatic(Class<?> type,String name)throws Exception{java.lang.reflect.Field f=type.getDeclaredField(name);f.setAccessible(true);return f.get(null);}
 private static Object get(Object o,String name)throws Exception{java.lang.reflect.Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
 private static Object getUnchecked(Object o,String name){try{return get(o,name);}catch(Exception e){throw new AssertionError(e);}}
}
