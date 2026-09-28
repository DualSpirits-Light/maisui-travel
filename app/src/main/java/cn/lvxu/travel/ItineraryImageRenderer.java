package cn.lvxu.travel;

import android.content.Context;
import android.graphics.*;
import android.text.*;
import java.io.*;
import java.util.*;

/** Worker-only local daily keepsake renderer. No trip-level private fields are exported. */
public final class ItineraryImageRenderer {
 public static final int WIDTH=1080, MAX_HEIGHT=10000;
 private static final int LEFT=76, CONTENT=928, TOP=102, BOTTOM=100;
 private static final int INK=0xff263b36, MUTED=0xff65766f, GREEN=0xff287d63;
 public static final class Options { public boolean notes=true,addresses=true,tags=true,costs=false,photos=false; }
 public static final class Sheet {
  public final File file; public final int width,height,day,part; public final String label;
  Sheet(File f,int h,int d,int p,String l){file=f;width=WIDTH;height=h;day=d;part=p;label=l;}
 }
 private interface Draw { void draw(Canvas c,Context context)throws Exception; }
 private static final class Page { final ArrayList<Draw> draws=new ArrayList<>(); int y=TOP; }
 private static final class Planner {
  final ArrayList<Page> pages=new ArrayList<>(); Page page;
  Planner(){next();} void next(){page=new Page();pages.add(page);}
  void gap(int h){page.y=Math.min(MAX_HEIGHT-BOTTOM,page.y+h);}
  void text(String value,int size,int color,boolean bold)throws InterruptedException {
   if(value==null||value.isEmpty())return;
   TextPaint paint=new TextPaint(Paint.ANTI_ALIAS_FLAG);paint.setTextSize(size);paint.setColor(color);paint.setTypeface(bold?Typeface.create("sans-serif",Typeface.BOLD):Typeface.create("sans-serif",Typeface.NORMAL));
   StaticLayout layout=StaticLayout.Builder.obtain(value,0,value.length(),paint,CONTENT).setIncludePad(false).setLineSpacing(5,1).setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY).build();
   int line=0;
   while(line<layout.getLineCount()){
    check();int first=line,base=layout.getLineTop(line),available=MAX_HEIGHT-BOTTOM-page.y;
    while(line<layout.getLineCount()&&layout.getLineBottom(line)-base<=available)line++;
    if(first==line){next();continue;}
    int height=layout.getLineBottom(line-1)-base,y=page.y;
    page.draws.add((canvas,ctx)->{canvas.save();canvas.clipRect(LEFT,y,LEFT+CONTENT,y+height);canvas.translate(LEFT,y-base);layout.draw(canvas);canvas.restore();});
    page.y+=height;if(line<layout.getLineCount())next();
   }
   gap(15);
  }
  void photo(Context context,String path)throws Exception {
   check();int w=0,h=0;Bitmap bitmap=null;
   try{bitmap=new MediaFiles(context).decode(path,1080);if(bitmap!=null){w=bitmap.getWidth();h=bitmap.getHeight();}}
   catch(IOException|IllegalArgumentException ignored){}finally{if(bitmap!=null)bitmap.recycle();}
   if(w==0||h==0){text("照片暂不可用（本地文件缺失或无法读取）",28,MUTED,false);return;}
   float scale=Math.min(CONTENT/(float)w,1400f/h);int dw=Math.max(1,Math.round(w*scale)),dh=Math.max(1,Math.round(h*scale));
   if(page.y+dh>MAX_HEIGHT-BOTTOM)next();int y=page.y,x=LEFT+(CONTENT-dw)/2;
   page.draws.add((canvas,ctx)->{check();Bitmap image=null;try{image=new MediaFiles(ctx).decode(path,1080);if(image==null)throw new IOException("missing photo");canvas.drawBitmap(image,null,new Rect(x,y,x+dw,y+dh),new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG));}catch(IOException|IllegalArgumentException ex){Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setColor(MUTED);p.setTextSize(28);canvas.drawText("照片暂不可用（本地文件缺失或无法读取）",LEFT,y+40,p);}finally{if(image!=null)image.recycle();}});
   page.y+=dh;gap(28);
  }
 }
 static void check()throws InterruptedException {if(Thread.interrupted())throw new InterruptedException("已取消图片生成");}
 public static List<Sheet> render(Context context,Trip snapshot,int day,Options options,File outputDir)throws Exception {
  Objects.requireNonNull(context);Objects.requireNonNull(snapshot);Objects.requireNonNull(options);check();
  if(day<0||day>=snapshot.days)throw new IllegalArgumentException("日期无效");
  if(!outputDir.isDirectory()&&!outputDir.mkdirs())throw new IOException("无法创建图片目录");
  Planner plan=new Planner();plan.text("麦穗旅序  /  随手看",27,GREEN,true);plan.gap(20);plan.text(snapshot.title,54,INK,true);plan.text("DAY "+(day+1)+"  ·  "+ItineraryFormat.dateLabel(snapshot,day),32,GREEN,true);plan.text(snapshot.city,30,MUTED,false);plan.gap(34);
  ArrayList<Trip.Stop> stops=snapshot.onDay(day);
  if(stops.isEmpty())plan.text("今天还没有安排，留一点时间给沿途的风景。",36,INK,false);
  for(int i=0;i<stops.size();i++){
   check();Trip.Stop stop=stops.get(i);
   // Keep each station heading with its time and first detail whenever possible.
   if(plan.page.y>MAX_HEIGHT-BOTTOM-300)plan.next();
   plan.text(String.format(Locale.ROOT,"%02d",i+1)+"  "+stop.name,43,INK,true);
   plan.text(ItineraryFormat.timeRange(stop),34,GREEN,true);
   if(!Trip.empty(stop.mode))plan.text("抵达交通 · "+stop.mode,29,MUTED,false);
   if(options.tags){StringBuilder tags=new StringBuilder();for(String id:stop.tagIds){String name=stop.tagNames.get(id);if(Trip.empty(name))name=snapshot.tagNames.get(id);if(!Trip.empty(name)){if(tags.length()>0)tags.append("   ");tags.append("#").append(name);}}plan.text(tags.toString(),29,GREEN,false);}
   if(options.addresses&&!Trip.empty(stop.address))plan.text("地址 · "+stop.address,30,MUTED,false);
   if(!Trip.empty(stop.openingHours))plan.text("开放时间 · "+stop.openingHours,29,MUTED,false);
   if(options.notes&&!Trip.empty(stop.note))plan.text(stop.note,33,INK,false);
   if(options.costs)plan.text("预计费用 · ¥"+Trip.money(stop.cost),30,GREEN,false);
   if(options.photos){LinkedHashSet<String> photos=new LinkedHashSet<>();if(!Trip.empty(stop.previewPhoto))photos.add(stop.previewPhoto);photos.addAll(stop.notePhotos);for(String path:photos)if(!Trip.empty(path))plan.photo(context,path);}
   plan.gap(46);
  }
  ArrayList<Sheet> result=new ArrayList<>();File current=null;
  try{
   for(int i=0;i<plan.pages.size();i++){
    check();Page page=plan.pages.get(i);int height=Math.max(600,Math.min(MAX_HEIGHT,page.y+BOTTOM));Bitmap bitmap=Bitmap.createBitmap(WIDTH,height,Bitmap.Config.ARGB_8888);
    try{Canvas canvas=new Canvas(bitmap);canvas.drawColor(0xfffaf9f3);Paint accent=new Paint(Paint.ANTI_ALIAS_FLAG);accent.setColor(0xffdbecdf);canvas.drawRoundRect(LEFT,40,LEFT+100,50,5,5,accent);
     for(Draw draw:page.draws){check();draw.draw(canvas,context);}accent.setColor(MUTED);accent.setTextSize(25);canvas.drawText("DAY "+(day+1)+"  ·  "+(i+1)+" / "+plan.pages.size()+"    麦穗旅序 · 随手看",LEFT,height-38,accent);
     current=File.createTempFile("quicklook-d"+(day+1)+"-p"+(i+1)+"-",".png",outputDir);
     try(FileOutputStream out=new FileOutputStream(current)){check();if(!bitmap.compress(Bitmap.CompressFormat.PNG,100,out))throw new IOException("图片编码失败");out.getFD().sync();}check();
     result.add(new Sheet(current,height,day,i+1,"第 "+(day+1)+" 天 · "+(i+1)+"/"+plan.pages.size()));current=null;
    }finally{bitmap.recycle();}
   }
   return result;
  }catch(Exception|OutOfMemoryError ex){if(current!=null)current.delete();for(Sheet sheet:result)sheet.file.delete();throw ex;}
 }
}
