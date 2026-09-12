package cn.lvxu.travel;
import android.app.*;import android.content.*;import java.io.*;

final class ImportLifecycle031Test {
 static int run(Instrumentation in)throws Exception {
  Context c=in.getTargetContext();MainActivity old=(MainActivity)in.startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP));MainActivity current=null;File archive=File.createTempFile("lifecycle-",".zip",c.getCacheDir());
  try{Trip incoming=Trip.demo();incoming.title="导入测试";try(OutputStream out=new FileOutputStream(archive)){TripShareArchive.write(c,incoming,out);}in.runOnMainSync(old::finish);in.waitForIdleSync();current=(MainActivity)in.startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));MainActivity owner=current;Trip edited=Trip.demo();edited.title="新页面修改必须保留";in.runOnMainSync(()->{owner.trips.clear();owner.trips.add(edited);owner.active=edited;owner.changed();});String before=TripStore.encode(owner.store.read());boolean rejected=false;try{TripShareArchive.importTrip(old,archive,old.store,old.trips);}catch(IOException expected){rejected=true;}if(!rejected||!before.equals(TripStore.encode(owner.store.read())))throw new AssertionError("destroyed owner overwrote newer page data");Trip imported=TripShareArchive.importTrip(owner,archive,owner.store,owner.trips);if(owner.trips.size()!=2||owner.store.read().stream().noneMatch(t->t.title.equals(edited.title))||owner.store.read().stream().noneMatch(t->t.id.equals(imported.id)))throw new AssertionError("live owner import did not merge latest edits");return 2;}
  finally{archive.delete();if(current!=null){MainActivity owner=current;in.runOnMainSync(owner::finish);}else in.runOnMainSync(old::finish);}
 }
}
