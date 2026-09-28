package cn.lvxu.travel;

import android.Manifest;
import android.content.*;
import android.content.pm.PackageManager;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import java.io.*;
import java.util.UUID;

/** Saves one image transactionally; the caller owns permission prompts and batch reporting. */
public final class ItineraryGallery {
 private ItineraryGallery(){}
 static String safeName(String value){String clean=value==null?"随手看":value.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]","_").trim();if(clean.toLowerCase(java.util.Locale.ROOT).endsWith(".png"))clean=clean.substring(0,clean.length()-4);if(clean.isEmpty())clean="随手看";if(clean.length()>80)clean=clean.substring(0,80);return clean+".png";}
 public static Uri save(Context context,File png,String displayName)throws IOException {
  if(png==null||!png.isFile()||png.length()==0)throw new IOException("图片不存在或为空");
  String name=safeName(displayName);
  if(Build.VERSION.SDK_INT>=29)return saveModern(context.getContentResolver(),png,name);
  if(context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)!=PackageManager.PERMISSION_GRANTED)throw new IOException("需要允许保存到相册");
  File directory=new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),"麦穗旅序/随手看");
  if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("无法创建相册目录");
  File target=new File(directory,name.substring(0,name.length()-4)+"-"+UUID.randomUUID()+".png");
  try{try(OutputStream out=new FileOutputStream(target)){copy(png,out);}checkCancelled();MediaScannerConnection.scanFile(context,new String[]{target.getAbsolutePath()},new String[]{"image/png"},null);return Uri.fromFile(target);}catch(IOException|RuntimeException ex){target.delete();throw new IOException("保存图片失败",ex);}
 }
 static Uri saveModern(ContentResolver resolver,File png,String name)throws IOException {
  Uri inserted=null;
  try{
   ContentValues values=new ContentValues();values.put(MediaStore.Images.Media.DISPLAY_NAME,safeName(name));values.put(MediaStore.Images.Media.MIME_TYPE,"image/png");values.put(MediaStore.Images.Media.RELATIVE_PATH,Environment.DIRECTORY_PICTURES+"/麦穗旅序/随手看/");values.put(MediaStore.Images.Media.IS_PENDING,1);
   inserted=resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values);if(inserted==null)throw new IOException("无法创建相册图片");
   try(OutputStream out=resolver.openOutputStream(inserted,"w")){if(out==null)throw new IOException("无法写入相册图片");copy(png,out);}
   checkCancelled();ContentValues ready=new ContentValues();ready.put(MediaStore.Images.Media.IS_PENDING,0);if(resolver.update(inserted,ready,null,null)!=1)throw new IOException("相册图片未完成保存");return inserted;
  }catch(IOException|RuntimeException ex){if(inserted!=null)try{resolver.delete(inserted,null,null);}catch(RuntimeException cleanup){ex.addSuppressed(cleanup);}throw new IOException("保存图片失败",ex);}
 }
 private static void checkCancelled()throws InterruptedIOException {if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("已取消保存");}
 private static void copy(File file,OutputStream out)throws IOException {try(InputStream in=new FileInputStream(file)){byte[] buffer=new byte[32768];int n;while((n=in.read(buffer))!=-1){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("已取消保存");out.write(buffer,0,n);}out.flush();checkCancelled();}}
}
