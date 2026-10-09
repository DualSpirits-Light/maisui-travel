package cn.lvxu.travel;
import android.app.Instrumentation;import android.graphics.Bitmap;import android.os.SystemClock;import android.view.*;import android.widget.*;import com.baidu.mapapi.map.*;import com.baidu.mapapi.model.LatLng;import java.io.*;import java.util.*;
/** Disposable probe for one renderer and bounded urban gestures. */
final class HeatRenderProbe {
 static void run(Instrumentation in,BaiduHeatmapUi ui,File evidence)throws Exception{
  View body=(View)BaiduHeatmapUiTest.field(ui,"body");in.runOnMainSync(()->((ScrollView)body.getParent()).scrollTo(0,0));SystemClock.sleep(800);
  BaiduMap map=(BaiduMap)BaiduHeatmapUiTest.field(ui,"map");View frame=(View)BaiduHeatmapUiTest.field(ui,"mapFrame");
  if(BaiduHeatmapUiTest.field(ui,"view")==null||BaiduHeatmapUiTest.field(ui,"sampler")==null||hasField("referenceMap")||hasField("referenceView")||hasField("referenceRendered"))throw new AssertionError("heatmap must use one renderer");
  in.runOnMainSync(()->map.setMapStatus(MapStatusUpdateFactory.newLatLngZoom(new LatLng(36.061,103.834),15)));SystemClock.sleep(4000);
  drag(in,frame,evidence);shot(in,evidence,"drag-single-map.png");
 }
 private static void drag(Instrumentation in,View view,File evidence)throws Exception{
  for(int attempt=0;attempt<2;attempt++){final long down=SystemClock.uptimeMillis();
   for(int i=0;i<=18;i++){final int step=i;in.runOnMainSync(()->{int action=step==0?MotionEvent.ACTION_DOWN:step==18?MotionEvent.ACTION_UP:MotionEvent.ACTION_MOVE;MotionEvent e=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,view.getWidth()*.62f+step*view.getWidth()*.006f,view.getHeight()*.5f,0);view.dispatchTouchEvent(e);e.recycle();});if(attempt==0&&i==9)shot(in,evidence,"drag-during-single-map.png");SystemClock.sleep(80);}
   SystemClock.sleep(1200);
  }SystemClock.sleep(1800);
 }
 private static void shot(Instrumentation in,File dir,String name)throws Exception{Bitmap b=in.getUiAutomation().takeScreenshot();if(b==null)throw new AssertionError("single renderer screenshot unavailable");try(FileOutputStream out=new FileOutputStream(new File(dir,name))){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();}
 private static boolean hasField(String name){try{BaiduHeatmapUi.class.getDeclaredField(name);return true;}catch(NoSuchFieldException missing){return false;}}
}
