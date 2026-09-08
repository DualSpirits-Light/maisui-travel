package cn.lvxu.travel;
import android.content.Context;
import java.io.*;
import java.util.*;

/** Emulator-only archive checks. The caller is responsible for restoring its TripStore snapshot. */
final class TripShareArchiveFixtureTest {
 static int run(Context context,TripStore store)throws Exception {
  int checks=0;File archive=File.createTempFile("trip-share-test-",".zip",context.getCacheDir());String sourcePath="media/test/share-source.jpg";File source=MediaFiles.file(context,sourcePath);source.getParentFile().mkdirs();byte[] photo={9,7,5,3,1};try(OutputStream out=new FileOutputStream(source)){out.write(photo);}
  Trip trip=Trip.demo();String originalId=trip.id,originalStopId=trip.stops.get(0).id;trip.stops.get(0).previewPhoto=sourcePath;trip.companions="小麦、小穗";final File[] installed={null};
  try {
   try(OutputStream out=new FileOutputStream(archive)){TripShareArchive.write(context,trip,out);}if(archive.length()<=0)throw new AssertionError("trip share archive empty");checks++;
   TripShareArchive.Preview preview=TripShareArchive.inspect(context,archive);if(!trip.title.equals(preview.title)||preview.photos!=1||preview.stops!=trip.stops.size())throw new AssertionError("trip share preview differs");checks++;
   ArrayList<Trip> existing=new ArrayList<>();store.save(existing);Trip imported=TripShareArchive.importTrip(context,archive,store,existing);installed[0]=new File(new File(context.getFilesDir(),"media"),"shared/"+imported.id);if(existing.size()!=1||!store.read().get(0).id.equals(imported.id))throw new AssertionError("trip share import not persisted");checks++;
   if(imported.id.equals(originalId)||imported.stops.get(0).id.equals(originalStopId))throw new AssertionError("trip share IDs not regenerated");checks++;
   if(sourcePath.equals(imported.stops.get(0).previewPhoto)||!Arrays.equals(photo,java.nio.file.Files.readAllBytes(MediaFiles.file(context,imported.stops.get(0).previewPhoto).toPath())))throw new AssertionError("trip share photo not remapped");checks++;
   String before=TripStore.encode(existing);File broken=File.createTempFile("trip-share-broken-",".zip",context.getCacheDir());java.nio.file.Files.write(broken.toPath(),new byte[]{1,2,3,4});boolean rejected=false;try{TripShareArchive.importTrip(context,broken,store,existing);}catch(Exception expected){rejected=true;}finally{broken.delete();}if(!rejected)throw new AssertionError("corrupt trip share accepted");checks++;if(!before.equals(TripStore.encode(existing))||!before.equals(TripStore.encode(store.read())))throw new AssertionError("rejected trip share changed trips");checks++;
   return checks;
  }finally{archive.delete();source.delete();if(installed[0]!=null)deleteTree(installed[0]);}
 }
 private static void deleteTree(File file){File[] children=file.listFiles();if(children!=null)for(File child:children)deleteTree(child);file.delete();}
}
