package cn.lvxu.travel;
import android.content.Context;
import android.graphics.*;
import java.io.*;
import java.util.*;
import org.json.JSONObject;

/** Explicit opt-in network smoke test; uploads only a generated color image and fixture trip. */
final class LiveShare031Test {
 static String run(Context c,TripStore store)throws Exception {
  Trip t=Trip.demo();t.title="031 自动测试旅行";String path=new MediaFiles(c).newPath("test",".png");File photo=MediaFiles.file(c,path),archive=File.createTempFile("live-share-",".zip",c.getCacheDir()),download=File.createTempFile("live-receive-",".zip",c.getCacheDir());File installed=null;
  try{Bitmap b=Bitmap.createBitmap(160,100,Bitmap.Config.ARGB_8888);b.eraseColor(Color.rgb(24,130,75));try(OutputStream out=new FileOutputStream(photo)){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();for(Trip.Stop s:t.stops){s.sourceUrl="";s.sourceSnapshot="";}t.stops.get(0).previewPhoto=path;t.stops.get(0).notePhotos.add(path);byte[] expected=java.nio.file.Files.readAllBytes(photo.toPath());try(OutputStream out=new FileOutputStream(archive)){TripShareArchive.write(c,t,out,true);}ShareCodeService service=new ShareCodeService();JSONObject shared=service.upload(archive);service.download(shared.getString("code"),download);if(!Arrays.equals(java.nio.file.Files.readAllBytes(archive.toPath()),java.nio.file.Files.readAllBytes(download.toPath())))throw new AssertionError("cloud archive differs");TripShareArchive.Preview p=TripShareArchive.inspect(c,download);if(p.photos!=1)throw new AssertionError("cloud photo manifest differs");ArrayList<Trip> existing=new ArrayList<>();Trip imported=TripShareArchive.importTrip(c,download,store,existing);installed=new File(c.getFilesDir(),"media/shared/"+imported.id);String target=imported.stops.get(0).previewPhoto;if(!Arrays.equals(expected,java.nio.file.Files.readAllBytes(MediaFiles.file(c,target).toPath())))throw new AssertionError("cloud photo bytes differ");Bitmap decoded=BitmapFactory.decodeFile(MediaFiles.file(c,target).getAbsolutePath());if(decoded==null||decoded.getWidth()!=160||decoded.getHeight()!=100)throw new AssertionError("cloud photo is unreadable");decoded.recycle();if(!store.read().get(0).stops.get(0).notePhotos.get(0).equals(target))throw new AssertionError("cloud photo reference not persisted");return shared.getString("code");}
  finally{archive.delete();download.delete();photo.delete();if(installed!=null)delete(installed);}
 }
 static void delete(File f){File[] children=f.listFiles();if(children!=null)for(File child:children)delete(child);f.delete();}
}
