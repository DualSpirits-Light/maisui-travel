package cn.lvxu.travel;

import android.content.Context;
import android.graphics.Bitmap;
import org.json.JSONObject;
import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.zip.*;

/** Receipt photo backup and privacy regression using a real image. */
final class ExpensePhotosTest {
 static int run(Context context,TripStore store)throws Exception {
  ArrayList<Trip> saved=store.read();AppPrefs prefs=new AppPrefs(context);JSONObject savedPrefs=prefs.exportJson();
  MediaFiles media=new MediaFiles(context);String path=media.newPath("test",".png");File file=media.file(path);int checks=0;
  try {
   Bitmap image=Bitmap.createBitmap(18,12,Bitmap.Config.ARGB_8888);image.eraseColor(0xff7aab73);try(OutputStream out=new FileOutputStream(file)){image.compress(Bitmap.CompressFormat.PNG,100,out);}finally{image.recycle();}
   byte[] expected=Files.readAllBytes(file.toPath());Trip trip=Trip.demo();Trip.Expense expense=new Trip.Expense();expense.name="账单票据";expense.amount=1234;expense.occurredAt="2026-10-05T12:30";expense.photo=path;trip.expenses.add(expense);
   ArrayList<Trip> sample=new ArrayList<>();sample.add(trip);ByteArrayOutputStream backup=new ByteArrayOutputStream();BackupArchive.write(context,sample,prefs,backup);
   boolean found=false;try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(backup.toByteArray()))){for(ZipEntry e;(e=zip.getNextEntry())!=null;)if(e.getName().equals(path))found=true;}if(!found)throw new AssertionError("receipt not included in backup");checks++;
   file.delete();ArrayList<Trip> existing=new ArrayList<>();store.save(existing);BackupArchive.restore(context,new ByteArrayInputStream(backup.toByteArray()),store,existing,prefs);
   Trip restored=store.read().get(0);if(!restored.expenses.get(0).photo.equals(path)||!Arrays.equals(expected,Files.readAllBytes(file.toPath())))throw new AssertionError("receipt backup bytes/reference differ");checks++;
   Trip full=TripLinkCodec.copyForShare(trip,true,true);if(!full.expenses.get(0).photo.equals(path))throw new AssertionError("complete share lost receipt");checks++;
   Trip pure=TripLinkCodec.copyForShare(trip,false,true);if(!pure.expenses.isEmpty())throw new AssertionError("pure itinerary leaked expense");checks++;
   Trip withoutPhotos=TripLinkCodec.copyForShare(trip,true,false);if(withoutPhotos.expenses.size()!=1||!withoutPhotos.expenses.get(0).photo.isEmpty())throw new AssertionError("photo-free share leaked receipt");checks++;
   return checks;
  }finally{file.delete();store.save(saved);prefs.importJson(savedPrefs);}
 }
}
