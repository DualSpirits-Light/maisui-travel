package cn.lvxu.travel;

import android.graphics.*;
import android.net.Uri;
import java.io.IOException;

/** Owns the editable image, leaving its source untouched. */
final class PhotoEditSession implements AutoCloseable {
 private Bitmap image,brushBase;private boolean changed,changedBeforeBrush;private final BrushHistory history=new BrushHistory();
 static final class Segment {final float x1,y1,x2,y2,width;final int color;Segment(float x1,float y1,float x2,float y2,int color,float width){this.x1=x1;this.y1=y1;this.x2=x2;this.y2=y2;this.color=color;this.width=width;}}
 static final class BrushHistory {
  final java.util.ArrayList<java.util.ArrayList<Segment>> strokes=new java.util.ArrayList<>();private java.util.ArrayList<Segment> current;int applied;
  void begin(){end();while(strokes.size()>applied)strokes.remove(strokes.size()-1);current=new java.util.ArrayList<>();strokes.add(current);applied++;}
  void add(float x1,float y1,float x2,float y2,int color,float width){if(current!=null)current.add(new Segment(x1,y1,x2,y2,color,width));}
  void end(){if(current!=null&&current.isEmpty()){strokes.remove(strokes.size()-1);applied--;}current=null;}
  boolean canUndo(){return applied>0;}boolean canRedo(){return applied<strokes.size();}
  void undo(){end();if(canUndo())applied--;}void redo(){end();if(canRedo())applied++;}
  void clear(){strokes.clear();current=null;applied=0;}
 }
 PhotoEditSession(Bitmap source){image=source.copy(Bitmap.Config.ARGB_8888,true);if(image==null)throw new IllegalArgumentException("无法编辑这张照片");}
 Bitmap bitmap(){return image;}
 void rotateClockwise(){Matrix matrix=new Matrix();matrix.setRotate(90);replace(Bitmap.createBitmap(image,0,0,image.getWidth(),image.getHeight(),matrix,false));}
 void crop(Rect area){
  if(area.left<0||area.top<0||area.right>image.getWidth()||area.bottom>image.getHeight()||area.isEmpty())throw new IllegalArgumentException("请选择有效的裁剪范围");
  if(area.left==0&&area.top==0&&area.width()==image.getWidth()&&area.height()==image.getHeight())return;
  replace(Bitmap.createBitmap(image,area.left,area.top,area.width(),area.height()));
 }
 void beginStroke(){if(brushBase==null){try{brushBase=image.copy(Bitmap.Config.ARGB_8888,false);changedBeforeBrush=changed;}catch(OutOfMemoryError ignored){}}if(brushBase!=null)history.begin();}
 void endStroke(){history.end();}
 boolean canUndo(){return history.canUndo();}
 boolean canRedo(){return brushBase!=null&&history.canRedo();}
 void undo(){endStroke();if(canUndo()){history.undo();redrawBrush();}}
 void redo(){endStroke();if(canRedo()){history.redo();redrawBrush();}}
 void stroke(float x1,float y1,float x2,float y2,int color,float width){history.add(x1,y1,x2,y2,color,width);drawStroke(x1,y1,x2,y2,color,width);changed=true;}
 private void drawStroke(float x1,float y1,float x2,float y2,int color,float width){Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);paint.setColor(color);paint.setStrokeWidth(width);paint.setStrokeCap(Paint.Cap.ROUND);Canvas canvas=new Canvas(image);canvas.drawLine(x1,y1,x2,y2,paint);canvas.drawCircle(x2,y2,width/2,paint);}
 private void redrawBrush(){Paint restore=new Paint();restore.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC));new Canvas(image).drawBitmap(brushBase,0,0,restore);for(int i=0;i<history.applied;i++)for(Segment s:history.strokes.get(i))drawStroke(s.x1,s.y1,s.x2,s.y2,s.color,s.width);changed=changedBeforeBrush||history.applied>0;}
 private void clearBrush(){if(brushBase!=null)brushBase.recycle();brushBase=null;history.clear();}
 String save(MediaFiles files,Uri original)throws IOException{return changed?files.save(image,"photos",92):files.importOriginal(original);}
 private void replace(Bitmap next){if(!next.isMutable()){Bitmap mutable=next.copy(Bitmap.Config.ARGB_8888,true);if(mutable==null)throw new IllegalArgumentException("无法编辑这张照片");if(next!=image)next.recycle();next=mutable;}clearBrush();if(next!=image){image.recycle();image=next;}changed=true;}
 @Override public void close(){clearBrush();if(image!=null&&!image.isRecycled())image.recycle();}
}
