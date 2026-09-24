package cn.lvxu.travel;

import android.graphics.*;
import android.net.Uri;
import java.io.IOException;

/** Owns the editable image, leaving its source untouched. */
final class PhotoEditSession implements AutoCloseable {
 private Bitmap image;private boolean changed;
 PhotoEditSession(Bitmap source){image=source.copy(Bitmap.Config.ARGB_8888,true);if(image==null)throw new IllegalArgumentException("无法编辑这张照片");}
 Bitmap bitmap(){return image;}
 void rotateClockwise(){Matrix matrix=new Matrix();matrix.setRotate(90);replace(Bitmap.createBitmap(image,0,0,image.getWidth(),image.getHeight(),matrix,false));}
 void crop(Rect area){
  if(area.left<0||area.top<0||area.right>image.getWidth()||area.bottom>image.getHeight()||area.isEmpty())throw new IllegalArgumentException("请选择有效的裁剪范围");
  if(area.left==0&&area.top==0&&area.width()==image.getWidth()&&area.height()==image.getHeight())return;
  replace(Bitmap.createBitmap(image,area.left,area.top,area.width(),area.height()));
 }
 void stroke(float x1,float y1,float x2,float y2,int color,float width){Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);paint.setColor(color);paint.setStrokeWidth(width);paint.setStrokeCap(Paint.Cap.ROUND);Canvas canvas=new Canvas(image);canvas.drawLine(x1,y1,x2,y2,paint);canvas.drawCircle(x2,y2,width/2,paint);changed=true;}
 String save(MediaFiles files,Uri original)throws IOException{return changed?files.save(image,"photos",92):files.importOriginal(original);}
 private void replace(Bitmap next){if(next!=image){image.recycle();image=next;}changed=true;}
 @Override public void close(){if(image!=null&&!image.isRecycled())image.recycle();}
}
