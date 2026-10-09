package cn.lvxu.travel;

import android.graphics.*;import android.os.*;import android.widget.*;import android.view.*;
import com.baidu.mapapi.map.*;import com.baidu.mapapi.model.LatLng;import com.baidu.mapapi.utils.CoordinateConverter;
import java.util.*;import java.util.concurrent.*;import java.util.function.Consumer;

/** One renderer, serial heat/base snapshots. Pixel work stays off the UI thread. */
final class BaiduHeatSampler {
 private final MainActivity a;private final BaiduMap map;private final TextureMapView view;private final MapGestureFrame frame;
 private final Handler handler=new Handler(Looper.getMainLooper());private final ExecutorService worker=Executors.newSingleThreadExecutor();
 private final Consumer<List<NearbyPlace>> updated;private final Consumer<String> progress;private Consumer<Boolean> finished=ignored->{};
 private final ImageView cover;private Bitmap coverBitmap,heatBitmap;private MapStatus original,expected;private Runnable ready;private long readyAt;private int generation,index;private boolean busy,disposed,rendered,restoring,renderQueued,fullRun,finishedNotified;
 private final Runnable delivery=this::deliver,timeoutTask=this::timedOut;private final Runnable snapshotTimeout=this::snapshotTimedOut;
 private void snapshotTimedOut(){if(busy){progress.accept("热力快照超时，可稍后重试。");cancel();}}
 private ArrayList<NearbyPlace> places=new ArrayList<>();private ArrayList<LatLng> targets=new ArrayList<>();
 BaiduHeatSampler(MainActivity a,BaiduMap map,TextureMapView view,MapGestureFrame frame,Consumer<List<NearbyPlace>> updated,Consumer<String> progress){this.a=a;this.map=map;this.view=view;this.frame=frame;this.updated=updated;this.progress=progress;cover=new ImageView(a);cover.setScaleType(ImageView.ScaleType.FIT_XY);cover.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);map.setOnMapRenderCallbadk(()->{if(!busy||ready==null||renderQueued)return;renderQueued=true;handler.post(this::rendered);});}
 boolean busy(){return busy;}
 void onFinished(Consumer<Boolean> listener){finished=listener==null?ignored->{}:listener;}
 static LatLng point(NearbyPlace p){return new CoordinateConverter().from(CoordinateConverter.CoordType.COMMON).coord(new LatLng(p.lat,p.lon)).convert();}
 void start(Collection<NearbyPlace> source,boolean all){if(disposed||busy||source.isEmpty()||view.getWidth()<50)return;places=new ArrayList<>(source);original=map.getMapStatus();if(original==null)return;targets.clear();index=0;fullRun=all;finishedNotified=false;
  if(all){for(NearbyPlace p:places){LatLng target=point(p);boolean close=false;for(LatLng old:targets)if(GeoMath.meters(old.latitude,old.longitude,target.latitude,target.longitude)<500){close=true;break;}if(!close)targets.add(target);}}
  else {if(original.zoom<11||original.zoom>21)return;targets.add(original.target);}
  busy=true;restoring=false;final int ticket=++generation;handler.postDelayed(snapshotTimeout,5000);map.snapshot(bitmap->handler.post(()->{if(!current(ticket)){recycle(bitmap);return;}handler.removeCallbacks(snapshotTimeout);if(bitmap==null){cancel();return;}coverBitmap=bitmap;cover.setImageBitmap(bitmap);frame.addView(cover,new FrameLayout.LayoutParams(-1,-1));next(all,ticket);}));
 }
 private boolean current(int ticket){return !disposed&&busy&&generation==ticket;}
 private void next(boolean all,int ticket){if(!current(ticket))return;if(index>=targets.size()){finish(ticket,all);return;}progress.accept("正在刷新热力参考 "+(index+1)+"/"+targets.size()+"，拖动地图可停止刷新");map.setBaiduHeatMapEnabled(true);if(all)map.setMapStatus(MapStatusUpdateFactory.newLatLngZoom(targets.get(index),15));expected=map.getMapStatus();waitRender(()->heat(ticket,all),all?500:180);}
 private void heat(int ticket,boolean all){if(!current(ticket)||!same(expected,map.getMapStatus())){cancel();return;}handler.postDelayed(snapshotTimeout,5000);map.snapshot(bitmap->handler.post(()->{if(!current(ticket)){recycle(bitmap);return;}handler.removeCallbacks(snapshotTimeout);if(!same(expected,map.getMapStatus())){recycle(bitmap);cancel();return;}if(bitmap==null){cancel();return;}heatBitmap=bitmap;map.setBaiduHeatMapEnabled(false);waitRender(()->base(ticket,all),250);}));}
 private void base(int ticket,boolean all){if(!current(ticket)||!same(expected,map.getMapStatus())){cancel();return;}handler.postDelayed(snapshotTimeout,5000);map.snapshot(base->handler.post(()->{if(!current(ticket)){recycle(base);return;}handler.removeCallbacks(snapshotTimeout);if(!same(expected,map.getMapStatus())){recycle(base);cancel();return;}if(base==null||heatBitmap==null||base.getWidth()!=heatBitmap.getWidth()||base.getHeight()!=heatBitmap.getHeight()){recycle(base);cancel();return;}ArrayList<Point> pixels=new ArrayList<>(places.size());for(NearbyPlace p:places)pixels.add(map.getProjection().toScreenLocation(point(p)));Bitmap heat=heatBitmap;heatBitmap=null;submitAnalysis(ticket,heat,base,pixels);}));}
 private void submitAnalysis(int ticket,Bitmap heat,Bitmap base,ArrayList<Point> pixels){worker.execute(()->{int[] values=null;long now=System.currentTimeMillis();try{int w=heat.getWidth(),h=heat.getHeight();int[] hp=new int[w*h],bp=new int[w*h];heat.getPixels(hp,0,w,0,0,w,h);base.getPixels(bp,0,w,0,0,w,h);values=new int[pixels.size()];if(detail(bp,w,h)){int radius=Math.min(64,Math.max(8,a.dp(12)));for(int i=0;i<pixels.size();i++){Point pixel=pixels.get(i);values[i]=HeatVisualEstimate.estimate(hp,bp,w,h,pixel.x,pixel.y,radius).indicators;}}}catch(Throwable error){handler.post(()->analysisFailed(ticket));return;}finally{recycle(heat);recycle(base);}final int[] result=values;handler.post(()->applyAnalysis(ticket,result,now));});}
 private void applyAnalysis(int ticket,int[] values,long now){if(!current(ticket))return;ArrayList<NearbyPlace> sampled=new ArrayList<>();for(int i=0;i<places.size()&&i<values.length;i++){if(values[i]>0){NearbyPlace p=places.get(i);p.heat=values[i];p.heatAt=now;sampled.add(p);}}if(!sampled.isEmpty())updated.accept(sampled);index++;next(fullRun,ticket);}
 private void analysisFailed(int ticket){if(current(ticket)){progress.accept("热力快照处理失败，可稍后重试。");cancel();}}
 private void waitRender(Runnable task,long floor){clearTimers();ready=task;rendered=false;renderQueued=false;readyAt=SystemClock.uptimeMillis()+floor;handler.postDelayed(timeoutTask,2500);}
 private void rendered(){renderQueued=false;if(!busy||ready==null)return;rendered=true;long delay=Math.max(0,readyAt-SystemClock.uptimeMillis());handler.removeCallbacks(delivery);handler.postDelayed(delivery,delay);}
 private void deliver(){if(!busy||ready==null||!rendered||SystemClock.uptimeMillis()<readyAt)return;Runnable task=ready;ready=null;clearTimers();task.run();}
 private void finish(int ticket,boolean all){if(!current(ticket))return;ready=null;clearTimers();restoring=true;map.setBaiduHeatMapEnabled(true);map.setMapStatus(MapStatusUpdateFactory.newMapStatus(original));waitRender(()->{if(!current(ticket))return;busy=false;restoring=false;clearCover();progress.accept("热力参考已更新；无可靠数据的地点显示未知。");notifyFinished(all);},180);}
 private void clearTimers(){handler.removeCallbacks(snapshotTimeout);handler.removeCallbacks(delivery);handler.removeCallbacks(timeoutTask);}
 private void timedOut(){if(!busy||ready==null)return;if(rendered){deliver();return;}ready=null;if(restoring){busy=false;restoring=false;clearCover();progress.accept("热力参考暂未完成，稍后可刷新。");notifyFinished(false);}else cancel();}
 void cancel(){if(!busy)return;generation++;ready=null;clearTimers();busy=false;restoring=false;try{map.setBaiduHeatMapEnabled(true);if(original!=null)map.setMapStatus(MapStatusUpdateFactory.newMapStatus(original));}catch(RuntimeException ignored){}clearCover();notifyFinished(false);}
 void destroy(){cancel();disposed=true;clearTimers();map.setOnMapRenderCallbadk(null);worker.shutdown();}
 private void notifyFinished(boolean success){if(finishedNotified)return;finishedNotified=true;finished.accept(success);}
 private void clearCover(){if(cover.getParent()!=null)((android.view.ViewGroup)cover.getParent()).removeView(cover);cover.setImageDrawable(null);recycle(coverBitmap);coverBitmap=null;recycle(heatBitmap);heatBitmap=null;}
 private static void recycle(Bitmap b){if(b!=null&&!b.isRecycled())b.recycle();}
 static boolean same(MapStatus x,MapStatus y){return x!=null&&y!=null&&Objects.equals(x.targetScreen,y.targetScreen)&&Math.abs(x.zoom-y.zoom)<.01&&Math.abs(x.rotate-y.rotate)<.01&&Math.abs(x.overlook-y.overlook)<.01&&Math.abs(x.target.latitude-y.target.latitude)<.000001&&Math.abs(x.target.longitude-y.target.longitude)<.000001;}
 private static boolean detail(int[] pixels,int w,int h){HashSet<Integer> colours=new HashSet<>();for(int y=h/10;y<h*9/10;y+=Math.max(1,h/40))for(int x=w/10;x<w*9/10;x+=Math.max(1,w/40)){int c=pixels[y*w+x];if((c>>>24)<240)return false;colours.add(c&0x00F8F8F8);}return colours.size()>20;}
}
