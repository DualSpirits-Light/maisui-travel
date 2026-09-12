package cn.lvxu.travel;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import java.io.*;
import java.nio.file.Files;
import java.util.*;

/** Emulator-only round trips using real, decodable images in every shareable photo field. */
final class TripShareArchiveFixtureTest {
 static int run(Context context,TripStore store)throws Exception {
  int checks=0;String root="media/test/share-fixture-"+UUID.randomUUID();File archive=File.createTempFile("trip-share-test-",".zip",context.getCacheDir()),reShared=File.createTempFile("trip-share-reshared-",".zip",context.getCacheDir());File broken=null;ArrayList<File> installed=new ArrayList<>();
  LinkedHashMap<String,byte[]> expected=new LinkedHashMap<>();Trip trip=Trip.demo();trip.pinned=true;trip.archived=true;trip.favorite=true;trip.companions="小麦、小穗";Trip.Stop stop=trip.stops.get(0);String originalSnapshot;
  try {
   stop.previewPhoto=writeImage(context,root+"/preview.png",Bitmap.CompressFormat.PNG,0xffd44d4d);expected.put("preview",bytes(context,stop.previewPhoto));
   stop.notePhotos.add(writeImage(context,root+"/note-1.jpg",Bitmap.CompressFormat.JPEG,0xff4d83d4));expected.put("note-1",bytes(context,stop.notePhotos.get(0)));
   stop.notePhotos.add(writeImage(context,root+"/note-2.png",Bitmap.CompressFormat.PNG,0xff4dd47c));expected.put("note-2",bytes(context,stop.notePhotos.get(1)));
   Trip.Item item=trip.items.get(0);item.done=true;item.photo=writeImage(context,root+"/item.jpg",Bitmap.CompressFormat.JPEG,0xffd4b14d);expected.put("item",bytes(context,item.photo));
   Trip.Checkin checkin=new Trip.Checkin();checkin.place="真实照片打卡";checkin.time="2031-04-05T12:30";
   checkin.photo=writeImage(context,root+"/checkin.jpg",Bitmap.CompressFormat.JPEG,0xff8a4dd4);expected.put("checkin",bytes(context,checkin.photo));
   checkin.card=writeImage(context,root+"/card.png",Bitmap.CompressFormat.PNG,0xff4dc9d4);expected.put("card",bytes(context,checkin.card));
   checkin.groupPhotos.add(writeImage(context,root+"/group.jpg",Bitmap.CompressFormat.JPEG,0xffd44d9b));expected.put("group",bytes(context,checkin.groupPhotos.get(0)));
   checkin.sceneryPhotos.add(writeImage(context,root+"/scenery.png",Bitmap.CompressFormat.PNG,0xff7b9b4d));expected.put("scenery",bytes(context,checkin.sceneryPhotos.get(0)));trip.checkins.add(checkin);
   originalSnapshot=TripStore.encode(Collections.singletonList(trip));assertPhotos(context,trip,expected,"original");checks++;

   Trip full=TripLinkCodec.copyForShare(trip,true,true);if(full==trip||full.stops.get(0)==stop||full.checkins.get(0)==checkin||!full.companions.equals(trip.companions)||!photoPaths(full).equals(photoPaths(trip))||full.pinned||full.archived||full.favorite)throw new AssertionError("full share copy differs from source content");checks++;
   Trip compact=TripLinkCodec.copyForShare(trip,false,false);if(!compact.companions.isEmpty()||!compact.checkins.isEmpty()||!photoPaths(compact).isEmpty()||compact.items.get(0).done)throw new AssertionError("compact share copy retains excluded content");checks++;
   if(!originalSnapshot.equals(TripStore.encode(Collections.singletonList(trip))))throw new AssertionError("share copies mutate original trip");checks++;

   try(OutputStream out=new FileOutputStream(archive)){TripShareArchive.write(context,trip,out);}if(archive.length()<=0)throw new AssertionError("trip share archive empty");checks++;
   TripShareArchive.Preview preview=TripShareArchive.inspect(context,archive);if(!trip.title.equals(preview.title)||preview.photos!=expected.size()||preview.stops!=trip.stops.size())throw new AssertionError("trip share preview differs");checks++;
   ArrayList<Trip> existing=new ArrayList<>();store.save(existing);Trip imported=TripShareArchive.importTrip(context,archive,store,existing);installed.add(sharedDirectory(context,imported));
   if(existing.size()!=1||!store.read().get(0).id.equals(imported.id))throw new AssertionError("trip share import not persisted");checks++;
   if(imported.id.equals(trip.id)||imported.stops.get(0).id.equals(stop.id)||imported.checkins.get(0).id.equals(checkin.id))throw new AssertionError("trip share IDs not regenerated");checks++;
   assertPhotos(context,imported,expected,"first import");checks++;
   if(!originalSnapshot.equals(TripStore.encode(Collections.singletonList(trip))))throw new AssertionError("share import mutates original trip");assertPhotos(context,trip,expected,"original after import");checks++;

   try(OutputStream out=new FileOutputStream(reShared)){TripShareArchive.write(context,imported,out);}TripShareArchive.Preview repeatedPreview=TripShareArchive.inspect(context,reShared);if(repeatedPreview.photos!=expected.size())throw new AssertionError("re-shared photo count differs");checks++;
   Trip reImported=TripShareArchive.importTrip(context,reShared,store,existing);installed.add(sharedDirectory(context,reImported));
   if(existing.size()!=2||store.read().size()!=2)throw new AssertionError("re-import not persisted");checks++;
   assertEquivalentPayload(imported,reImported);checks++;
   assertPhotos(context,reImported,expected,"re-import");checks++;
   assertPhotos(context,trip,expected,"original after re-import");checks++;

   String before=TripStore.encode(existing);broken=File.createTempFile("trip-share-broken-",".zip",context.getCacheDir());Files.write(broken.toPath(),new byte[]{1,2,3,4});boolean rejected=false;try{TripShareArchive.importTrip(context,broken,store,existing);}catch(Exception expectedFailure){rejected=true;}if(!rejected)throw new AssertionError("corrupt trip share accepted");checks++;if(!before.equals(TripStore.encode(existing))||!before.equals(TripStore.encode(store.read())))throw new AssertionError("rejected trip share changed trips");checks++;
   return checks;
  }finally{archive.delete();reShared.delete();if(broken!=null)broken.delete();for(File directory:installed)deleteTree(directory);deleteTree(MediaFiles.file(context,root));}
 }
 private static String writeImage(Context context,String path,Bitmap.CompressFormat format,int color)throws Exception {File file=MediaFiles.file(context,path);file.getParentFile().mkdirs();Bitmap bitmap=Bitmap.createBitmap(18,12,Bitmap.Config.ARGB_8888);try{new Canvas(bitmap).drawColor(color);try(OutputStream out=new FileOutputStream(file)){if(!bitmap.compress(format,100,out))throw new IOException("image fixture encoding failed");}}finally{bitmap.recycle();}if(BitmapFactory.decodeFile(file.getAbsolutePath())==null)throw new AssertionError("fixture is not decodable: "+path);return path;}
 private static byte[] bytes(Context context,String path)throws Exception{return Files.readAllBytes(MediaFiles.file(context,path).toPath());}
 private static File sharedDirectory(Context context,Trip trip){return new File(new File(context.getFilesDir(),"media"),"shared/"+trip.id);}
 private static void assertPhotos(Context context,Trip trip,Map<String,byte[]> expected,String stage)throws Exception {Map<String,String> paths=photoPaths(trip);if(!paths.keySet().equals(expected.keySet()))throw new AssertionError(stage+" photo fields differ: "+paths.keySet());for(Map.Entry<String,byte[]> entry:expected.entrySet()){File file=MediaFiles.file(context,paths.get(entry.getKey()));if(BitmapFactory.decodeFile(file.getAbsolutePath())==null)throw new AssertionError(stage+" photo is not decodable: "+entry.getKey());if(!Arrays.equals(entry.getValue(),Files.readAllBytes(file.toPath())))throw new AssertionError(stage+" photo bytes differ: "+entry.getKey());}}
 private static Map<String,String> photoPaths(Trip trip){LinkedHashMap<String,String> paths=new LinkedHashMap<>();Trip.Stop stop=trip.stops.get(0);put(paths,"preview",stop.previewPhoto);for(int i=0;i<stop.notePhotos.size();i++)put(paths,"note-"+(i+1),stop.notePhotos.get(i));for(Trip.Item item:trip.items)if(!item.photo.isEmpty()){put(paths,"item",item.photo);break;}Trip.Checkin checkin=trip.checkins.isEmpty()?null:trip.checkins.get(0);if(checkin!=null){put(paths,"checkin",checkin.photo);put(paths,"card",checkin.card);for(int i=0;i<checkin.groupPhotos.size();i++)put(paths,"group"+(i==0?"":"-"+(i+1)),checkin.groupPhotos.get(i));for(int i=0;i<checkin.sceneryPhotos.size();i++)put(paths,"scenery"+(i==0?"":"-"+(i+1)),checkin.sceneryPhotos.get(i));}return paths;}
 private static void put(Map<String,String> values,String key,String path){if(path!=null&&!path.isEmpty())values.put(key,path);}
 private static void assertEquivalentPayload(Trip first,Trip second)throws Exception {if(!first.title.equals(second.title)||!first.city.equals(second.city)||!first.start.equals(second.start)||first.days!=second.days||!first.companions.equals(second.companions)||first.stops.size()!=second.stops.size()||first.items.size()!=second.items.size()||first.checkins.size()!=second.checkins.size())throw new AssertionError("re-import payload differs");Trip.Stop a=first.stops.get(0),b=second.stops.get(0);if(!a.name.equals(b.name)||!a.note.equals(b.note)||a.notePhotos.size()!=b.notePhotos.size())throw new AssertionError("re-import stop content differs");Trip.Checkin left=first.checkins.get(0),right=second.checkins.get(0);if(!left.place.equals(right.place)||!left.time.equals(right.time)||left.groupPhotos.size()!=right.groupPhotos.size()||left.sceneryPhotos.size()!=right.sceneryPhotos.size())throw new AssertionError("re-import checkin content differs");}
 private static void deleteTree(File file){if(file==null)return;File[] children=file.listFiles();if(children!=null)for(File child:children)deleteTree(child);file.delete();}
}
