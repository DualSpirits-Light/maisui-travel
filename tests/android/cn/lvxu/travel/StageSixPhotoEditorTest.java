package cn.lvxu.travel;

import android.app.Instrumentation;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.net.Uri;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.Arrays;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;

/** Real Android bitmap assertions: catches forced crops, wrong rotation and source mutation. */
final class StageSixPhotoEditorTest {
 static int run(Instrumentation in) throws Exception {
  int checks=0;
  Bitmap source=Bitmap.createBitmap(6,4,Bitmap.Config.ARGB_8888);
  source.eraseColor(Color.WHITE);source.setPixel(0,0,Color.RED);source.setPixel(5,0,Color.GREEN);source.setPixel(0,3,Color.BLUE);source.setPixel(5,3,Color.YELLOW);
  PhotoEditSession edit=new PhotoEditSession(source);
  Bitmap full=edit.bitmap();
  checks+=check(full!=source,"editor owns a copy rather than mutating source");
  checks+=check(full.getWidth()==6&&full.getHeight()==4&&full.getPixel(0,0)==Color.RED&&full.getPixel(5,3)==Color.YELLOW,"default keeps full frame and original ratio");
  edit.rotateClockwise();Bitmap rotated=edit.bitmap();
  checks+=check(rotated.getWidth()==4&&rotated.getHeight()==6,"quarter turn swaps dimensions");
  checks+=check(rotated.getPixel(3,0)==Color.RED&&rotated.getPixel(0,0)==Color.BLUE&&rotated.getPixel(3,5)==Color.GREEN,"quarter turn maps actual pixels clockwise");
  edit.close();edit=new PhotoEditSession(source);edit.crop(new Rect(1,0,6,2));
  checks+=check(edit.bitmap().getWidth()==5&&edit.bitmap().getHeight()==2&&edit.bitmap().getPixel(4,0)==Color.GREEN,"free crop supports a non-square non-original ratio");
  edit.close();edit=new PhotoEditSession(source);edit.stroke(2,2,4,2,Color.MAGENTA,1);
  checks+=check(edit.bitmap().getPixel(3,2)!=Color.WHITE&&edit.bitmap().getPixel(0,0)==Color.RED,"brush lands at image coordinates without touching unrelated corners");
  checks+=check(source.getPixel(3,2)==Color.WHITE&&source.getWidth()==6,"edits do not change source pixels");
  edit.close();
  MediaFiles files=new MediaFiles(in.getTargetContext());File original=File.createTempFile("photo-editor-",".png",in.getTargetContext().getCacheDir());
  try {
   try(FileOutputStream out=new FileOutputStream(original)){source.compress(Bitmap.CompressFormat.PNG,100,out);}
   byte[] before=Files.readAllBytes(original.toPath());edit=new PhotoEditSession(source);
   String path=edit.save(files,Uri.fromFile(original));File saved=files.file(path);
   checks+=check(!saved.equals(original)&&Arrays.equals(before,Files.readAllBytes(saved.toPath())),"unedited save creates an independent byte-identical full-frame file");saved.delete();
   Bitmap owned=edit.bitmap();edit.close();
   checks+=check(owned.isRecycled()&&!source.isRecycled()&&Arrays.equals(before,Files.readAllBytes(original.toPath())),"cancellation releases owned bitmap and never changes original");
  } finally {edit.close();source.recycle();original.delete();}
  in.getTargetContext().getSharedPreferences("app-prefs-v2",0).edit().putBoolean("tutorial",true).apply();
  MainActivity host=(MainActivity)in.startActivitySync(new Intent(in.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP));
  final Dialog[] dialog={null};final int[] callbacks={0},cancellations={0};Bitmap uiSource=Bitmap.createBitmap(80,40,Bitmap.Config.ARGB_8888);
  File uiOriginal=File.createTempFile("editor-gallery-",".png",host.getCacheDir());try(FileOutputStream out=new FileOutputStream(uiOriginal)){uiSource.compress(Bitmap.CompressFormat.PNG,100,out);}byte[] uiBefore=Files.readAllBytes(uiOriginal.toPath());
  try {
   in.runOnMainSync(()->dialog[0]=PhotoEditorUi.show(host,uiSource,Uri.fromFile(uiOriginal),path->callbacks[0]++,()->cancellations[0]++));in.waitForIdleSync();
   View close=find(dialog[0].getWindow().getDecorView(),"放弃照片编辑");checks+=check(close!=null,"editor exposes the top-left discard action");
   in.runOnMainSync(close::performClick);in.waitForIdleSync();
   AccessibilityNodeInfo confirm=waitNode(in,"放弃本次编辑？");
   checks+=check(confirm!=null&&dialog[0].isShowing()&&cancellations[0]==0,"discard first asks for confirmation and keeps editor alive");
   AccessibilityNodeInfo discard=waitNode(in,"确认放弃");checks+=check(discard!=null,"discard confirmation offers a clear final action");
   discard.performAction(AccessibilityNodeInfo.ACTION_CLICK);android.os.SystemClock.sleep(200);in.waitForIdleSync();
   checks+=check(!dialog[0].isShowing()&&callbacks[0]==0&&cancellations[0]==1&&uiSource.isRecycled(),"confirmed cancellation releases decoded bitmap and never reports a saved photo");
   final String[] delivered={null};set(host.media,"checkinCapture",true);set(host.media,"callback",(MainActivity.ImageCallback)path->delivered[0]=path);
   in.runOnMainSync(()->host.media.onResult(MediaController.PICK,android.app.Activity.RESULT_OK,new Intent().setData(Uri.fromFile(uiOriginal))));
   AccessibilityNodeInfo save=waitNode(in,"保存照片");checks+=check(save!=null,"check-in gallery selection opens the full-frame editor");save.performAction(AccessibilityNodeInfo.ACTION_CLICK);
   for(int i=0;i<60&&delivered[0]==null;i++)android.os.SystemClock.sleep(100);
   checks+=check(delivered[0]!=null&&Arrays.equals(uiBefore,Files.readAllBytes(files.file(delivered[0]).toPath())),"gallery editor save reports an independent full original exactly once");files.file(delivered[0]).delete();
   checks+=check(Arrays.equals(uiBefore,Files.readAllBytes(uiOriginal.toPath())),"gallery save leaves selected source bytes untouched");
   File camera=File.createTempFile("editor-camera-",".png",host.getCacheDir());Files.write(camera.toPath(),uiBefore);set(host.media,"cameraFile",camera);set(host.media,"checkinCapture",true);set(host.media,"callback",(MainActivity.ImageCallback)path->callbacks[0]++);
   in.runOnMainSync(()->host.media.onResult(MediaController.CAMERA,android.app.Activity.RESULT_OK,null));waitNode(in,"放弃照片编辑").performAction(AccessibilityNodeInfo.ACTION_CLICK);waitNode(in,"确认放弃").performAction(AccessibilityNodeInfo.ACTION_CLICK);
   for(int i=0;i<30&&camera.exists();i++)android.os.SystemClock.sleep(100);
   checks+=check(!camera.exists()&&callbacks[0]==0,"camera cancellation deletes capture temporary and never delivers a photo");
  } finally {in.runOnMainSync(()->{if(dialog[0]!=null)dialog[0].dismiss();host.finish();});if(!uiSource.isRecycled())uiSource.recycle();uiOriginal.delete();}
  return checks;
 }
 private static int check(boolean value,String message){if(!value)throw new AssertionError(message);return 1;}
 private static View find(View root,String label){if(label.contentEquals(root.getContentDescription()==null?"":root.getContentDescription()))return root;if(root instanceof ViewGroup){ViewGroup group=(ViewGroup)root;for(int i=0;i<group.getChildCount();i++){View found=find(group.getChildAt(i),label);if(found!=null)return found;}}return null;}
 private static void set(Object object,String name,Object value)throws Exception{java.lang.reflect.Field field=object.getClass().getDeclaredField(name);field.setAccessible(true);field.set(object,value);}
 private static AccessibilityNodeInfo waitNode(Instrumentation in,String label){for(int i=0;i<60;i++){AccessibilityNodeInfo found=findNode(in.getUiAutomation().getRootInActiveWindow(),label);if(found!=null)return found;android.os.SystemClock.sleep(100);}throw new AssertionError("Missing photo action: "+label);}
 private static AccessibilityNodeInfo findNode(AccessibilityNodeInfo node,String label){if(node==null)return null;if(label.contentEquals(node.getText()==null?"":node.getText())||label.contentEquals(node.getContentDescription()==null?"":node.getContentDescription()))return node;for(int i=0;i<node.getChildCount();i++){AccessibilityNodeInfo found=findNode(node.getChild(i),label);if(found!=null)return found;}return null;}
}
