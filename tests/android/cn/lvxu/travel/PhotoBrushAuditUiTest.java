package cn.lvxu.travel;

import android.app.Dialog;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import java.io.File;
import java.io.FileOutputStream;

/** Real pixel and touch regressions; compiled locally, run only on the test device. */
final class PhotoBrushAuditUiTest {
 static int run(Instrumentation in)throws Exception {
  int checks=0;Bitmap source=Bitmap.createBitmap(32,32,Bitmap.Config.ARGB_8888);source.eraseColor(Color.TRANSPARENT);
  try(PhotoEditSession session=new PhotoEditSession(source)){
   session.beginStroke();session.stroke(4,4,12,4,0xffEE554F,3);session.endStroke();int painted=session.bitmap().getPixel(8,4);
   checks+=check(painted==0xffEE554F,"first brush keeps exact red pixel");
   session.beginStroke();session.stroke(4,12,12,12,Color.BLUE,3);session.endStroke();
   session.undo();checks+=check(session.bitmap().getPixel(8,12)==Color.TRANSPARENT&&session.bitmap().getPixel(8,4)==painted,"undo restores transparency and preserves earlier stroke");
   session.undo();checks+=check(session.bitmap().getPixel(8,4)==Color.TRANSPARENT&&!session.canUndo(),"undo all restores original transparent image");
   session.redo();checks+=check(session.bitmap().getPixel(8,4)==painted&&session.canRedo(),"redo preserves exact painted color");
   session.beginStroke();session.stroke(20,20,24,20,Color.GREEN,3);session.endStroke();checks+=check(!session.canRedo(),"new stroke replaces abandoned redo branch");
   session.rotateClockwise();checks+=check(!session.canUndo()&&!session.canRedo(),"rotation safely ends brush history for old coordinates");
   session.beginStroke();session.stroke(4,4,12,4,Color.BLUE,3);session.endStroke();checks+=check(session.bitmap().getPixel(8,4)==Color.BLUE,"rotated image remains drawable");
   session.crop(new android.graphics.Rect(0,0,16,16));session.beginStroke();session.stroke(4,8,12,8,Color.GREEN,3);session.endStroke();checks+=check(session.bitmap().getPixel(8,8)==Color.GREEN,"cropped image remains drawable");
  }
  source.recycle();
  MainActivity host=(MainActivity)in.startActivitySync(new Intent(in.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP));
  Bitmap uiSource=Bitmap.createBitmap(80,80,Bitmap.Config.ARGB_8888);uiSource.eraseColor(Color.WHITE);File original=File.createTempFile("audit-brush-",".png",host.getCacheDir());try(FileOutputStream out=new FileOutputStream(original)){uiSource.compress(Bitmap.CompressFormat.PNG,100,out);}
  final Dialog[] dialog={null};
  try{
   in.runOnMainSync(()->dialog[0]=PhotoEditorUi.show(host,uiSource,Uri.fromFile(original),p->{},()->{}));in.waitForIdleSync();View decor=dialog[0].getWindow().getDecorView();View undo=find(decor,"撤销画笔"),redo=find(decor,"恢复画笔"),brush=find(decor,"画笔"),canvas=find(decor,"照片编辑画布");
   checks+=check(undo!=null&&redo!=null&&!undo.isEnabled()&&!redo.isEnabled(),"editor exposes disabled history controls before drawing");
   in.runOnMainSync(()->{brush.performClick();float x=canvas.getWidth()/2f,y=canvas.getHeight()/2f;long now=android.os.SystemClock.uptimeMillis();touch(canvas,now,MotionEvent.ACTION_DOWN,x,y);touch(canvas,now+20,MotionEvent.ACTION_MOVE,x+12,y);touch(canvas,now+40,MotionEvent.ACTION_UP,x+12,y);});
   checks+=check(undo.isEnabled()&&!redo.isEnabled(),"one actual canvas gesture enables undo");
   in.runOnMainSync(undo::performClick);checks+=check(!undo.isEnabled()&&redo.isEnabled(),"undo updates available controls");
   in.runOnMainSync(redo::performClick);checks+=check(undo.isEnabled()&&!redo.isEnabled(),"redo updates available controls");
  }finally{in.runOnMainSync(()->{if(dialog[0]!=null)dialog[0].dismiss();host.finish();});if(!uiSource.isRecycled())uiSource.recycle();original.delete();}
  return checks;
 }
 private static void touch(View canvas,long time,int action,float x,float y){MotionEvent e=MotionEvent.obtain(time,time,action,x,y,0);try{canvas.dispatchTouchEvent(e);}finally{e.recycle();}}
 private static int check(boolean ok,String label){if(!ok)throw new AssertionError(label);return 1;}
 private static View find(View v,String text){if(v instanceof android.widget.TextView&&text.contentEquals(((android.widget.TextView)v).getText())||text.contentEquals(v.getContentDescription()==null?"":v.getContentDescription()))return v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){View found=find(((ViewGroup)v).getChildAt(i),text);if(found!=null)return found;}return null;}
}
