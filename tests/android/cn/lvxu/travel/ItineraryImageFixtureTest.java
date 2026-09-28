package cn.lvxu.travel;

import android.content.*;
import android.database.Cursor;
import android.graphics.*;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import java.io.*;
import java.util.*;

/** Dedicated emulator only. Own files/MediaStore entries are removed, except visual evidence. */
final class ItineraryImageFixtureTest {
 static int run(Context context)throws Exception {
  int checks=0;File work=new File(context.getCacheDir(),"quicklook-test-"+UUID.randomUUID());work.mkdirs();String photo="media/test/quicklook-"+UUID.randomUUID()+".png";File photoFile=MediaFiles.file(context,photo);photoFile.getParentFile().mkdirs();Uri saved=null;
  try{
   Bitmap image=Bitmap.createBitmap(800,400,Bitmap.Config.ARGB_8888);try{new Canvas(image).drawColor(0xffa5cbbb);try(OutputStream out=new FileOutputStream(photoFile)){image.compress(Bitmap.CompressFormat.PNG,100,out);}}finally{image.recycle();}
   Trip trip=Trip.demo();trip.stops.get(0).previewPhoto=photo;trip.stops.get(0).tagIds.add("walk");trip.stops.get(0).tagNames.put("walk","慢慢走");trip.stops.get(0).address="浙江省杭州市西湖区北山街";trip.stops.get(0).note="沿着湖边走走，留一点时间拍照。\n中文换行、标点与第二段都完整保留。";
   ItineraryImageRenderer.Options options=new ItineraryImageRenderer.Options();options.photos=true;options.costs=true;
   List<ItineraryImageRenderer.Sheet> sheets=ItineraryImageRenderer.render(context,trip,0,options,work);if(sheets.size()!=1)throw new AssertionError("ordinary day should be one sheet");verify(sheets);checks++;
   File evidence=new File(context.getExternalFilesDir(null),"stage3-evidence");evidence.mkdirs();try(InputStream in=new FileInputStream(sheets.get(0).file);OutputStream out=new FileOutputStream(new File(evidence,"quicklook-demo.png"))){byte[] buffer=new byte[32768];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);}
   if(Build.VERSION.SDK_INT>=29){saved=ItineraryGallery.save(context,sheets.get(0).file,"quicklook-fixture-"+UUID.randomUUID()+".png");try(Cursor c=context.getContentResolver().query(saved,new String[]{MediaStore.Images.Media.IS_PENDING,MediaStore.Images.Media.RELATIVE_PATH},null,null,null)){if(c==null||!c.moveToFirst()||c.getInt(0)!=0||!c.getString(1).contains("麦穗旅序/随手看"))throw new AssertionError("gallery publish incomplete");}try(InputStream in=context.getContentResolver().openInputStream(saved)){Bitmap decoded=BitmapFactory.decodeStream(in);if(decoded==null)throw new AssertionError("gallery image invalid");decoded.recycle();}checks++;context.getContentResolver().delete(saved,null,null);saved=null;}
   Trip empty=Trip.demo();empty.stops.clear();List<ItineraryImageRenderer.Sheet> blank=ItineraryImageRenderer.render(context,empty,0,options,work);if(blank.size()!=1)throw new AssertionError("empty day unavailable");verify(blank);checks++;
   Trip longTrip=Trip.demo();longTrip.stops.clear();Trip.Stop longStop=new Trip.Stop();longStop.name="跨页中文长备注";StringBuilder longNote=new StringBuilder();for(int i=0;i<400;i++)longNote.append("逐行保留中文行程，不省略任何内容。\n");longStop.note=longNote.toString();longStop.previewPhoto="media/test/not-found.png";longTrip.stops.add(longStop);List<ItineraryImageRenderer.Sheet> pages=ItineraryImageRenderer.render(context,longTrip,0,options,work);if(pages.size()<2)throw new AssertionError("long note did not paginate");verify(pages);checks++;
   Thread.currentThread().interrupt();boolean cancelled=false;try{ItineraryImageRenderer.render(context,trip,0,options,work);}catch(InterruptedException expected){cancelled=true;}finally{Thread.interrupted();}if(!cancelled)throw new AssertionError("cancel ignored");checks++;
   if(!ItineraryGallery.safeName("../bad\\name:photo.png").equals(".._bad_name_photo.png"))throw new AssertionError("unsafe filename");checks++;
   if(Build.VERSION.SDK_INT>=29){String failedName="quicklook-rollback-"+UUID.randomUUID()+".png";boolean failed=false;
    try{ItineraryGallery.saveModern(context.getContentResolver(),new File(work,"missing-source.png"),failedName);}catch(IOException expected){failed=true;}
    if(!failed)throw new AssertionError("missing source did not fail");
    try(Cursor c=context.getContentResolver().query(MediaStore.setIncludePending(MediaStore.Images.Media.EXTERNAL_CONTENT_URI),new String[]{MediaStore.Images.Media._ID},MediaStore.Images.Media.DISPLAY_NAME+"=?",new String[]{failedName},null)){if(c==null||c.getCount()!=0)throw new AssertionError("failed gallery insert not cleaned");}checks++;}
   java.lang.reflect.Method copier=ItineraryGallery.class.getDeclaredMethod("copy",File.class,OutputStream.class);copier.setAccessible(true);boolean flushCancelled=false;
   try{copier.invoke(null,sheets.get(0).file,new ByteArrayOutputStream(){@Override public void flush(){Thread.currentThread().interrupt();}});}catch(java.lang.reflect.InvocationTargetException e){flushCancelled=e.getCause() instanceof InterruptedIOException;}finally{Thread.interrupted();}
   if(!flushCancelled)throw new AssertionError("cancel during final flush ignored");checks++;
   return checks;
  }finally{if(saved!=null)context.getContentResolver().delete(saved,null,null);photoFile.delete();File[] files=work.listFiles();if(files!=null)for(File f:files)f.delete();work.delete();}
 }
 private static void verify(List<ItineraryImageRenderer.Sheet> sheets){for(int i=0;i<sheets.size();i++){ItineraryImageRenderer.Sheet sheet=sheets.get(i);BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeFile(sheet.file.getAbsolutePath(),bounds);if(bounds.outWidth!=1080||bounds.outHeight>10000||bounds.outHeight!=sheet.height||sheet.part!=i+1)throw new AssertionError("bad sheet bounds/part");}}
}
