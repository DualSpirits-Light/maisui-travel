package cn.lvxu.travel;

import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.net.Uri;
import android.view.*;
import java.util.function.BooleanSupplier;
import android.widget.*;
import java.io.*;
import java.net.*;
import java.util.*;

/** Local preview and note photos; drafts are only copied into the model on Save. */
final class PlaceMediaUi {
    private static final java.util.concurrent.ExecutorService IMAGES=java.util.concurrent.Executors.newFixedThreadPool(2);
    private static final android.util.LruCache<String,Bitmap> CACHE=new android.util.LruCache<String,Bitmap>(8*1024*1024){@Override protected int sizeOf(String key,Bitmap bitmap){return bitmap.getAllocationByteCount();}};
    private final MainActivity a;
    private String preview;
    private final ArrayList<String> notes;
    private LinearLayout panel,notePanel;
    PlaceMediaUi(MainActivity a,Trip.Stop stop){this.a=a;preview=stop.previewPhoto;notes=new ArrayList<>(stop.notePhotos);}
    void editor(LinearLayout form){panel=a.col();form.addView(panel);refresh();}
    void noteEditor(LinearLayout form){notePanel=a.col();form.addView(notePanel);refresh();}
    private void refresh(){
        if(panel!=null){panel.removeAllViews();if(!preview.isEmpty()){image(a,panel,preview,140,false);a.space(panel,8);}else{panel.addView(a.text("预览图 · 可选，无图时显示地点名称",13,MainActivity.MUTED));a.space(panel,8);}a.pair(panel,a.action(preview.isEmpty()?"＋ 添加预览图":"更换预览图",false,()->a.media.pickOriginal(path->{preview=path;refresh();})),a.action("使用文字预览",false,()->{preview="";refresh();}));a.space(panel,18);}
        if(notePanel!=null){
            notePanel.removeAllViews();notePanel.addView(a.text("备注照片 · 可选，已添加 "+notes.size()+" 张",13,MainActivity.MUTED));a.space(notePanel,10);
            for(int start=0;start<notes.size();start+=2){LinearLayout row=a.row();row.setGravity(Gravity.TOP);
                for(int column=0;column<2;column++){int index=start+column;LinearLayout cell=a.col();LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.setMargins(column==0?0:a.dp(5),0,column==0?a.dp(5):0,a.dp(12));row.addView(cell,lp);if(index>=notes.size())continue;
                    String path=notes.get(index);image(a,cell,path,0,true);LinearLayout controls=a.row();controls.setGravity(Gravity.CENTER);ImageButton remove=icon(a,R.drawable.ic_trash,"删除备注照片 "+(index+1),()->a.confirm("删除这张备注照片？",()->{notes.remove(path);refresh();}));ImageButton crop=icon(a,R.drawable.ic_crop,"裁剪备注照片 "+(index+1),()->PhotoPreviewUi.crop(a,path,result->{int at=notes.indexOf(path);if(at>=0){notes.set(at,result);refresh();}}));controls.addView(remove,new LinearLayout.LayoutParams(0,a.dp(48),1));controls.addView(crop,new LinearLayout.LayoutParams(0,a.dp(48),1));cell.addView(controls);
                }notePanel.addView(row);
            }
            notePanel.addView(a.action("＋ 添加备注照片",false,()->{if(notes.size()>=20){a.toast("备注照片最多 20 张");return;}a.media.pickOriginal(path->{notes.add(path);refresh();});}));a.space(notePanel,18);
        }
    }
    private static ImageButton icon(MainActivity a,int resource,String description,Runnable click){ImageButton button=new ImageButton(a);button.setImageResource(resource);button.setColorFilter(MainActivity.INK);button.setBackgroundColor(android.graphics.Color.TRANSPARENT);button.setPadding(a.dp(12),a.dp(12),a.dp(12),a.dp(12));button.setContentDescription(description);button.setOnClickListener(v->click.run());return button;}
    void apply(Trip.Stop stop){stop.previewPhoto=preview;stop.notePhotos.clear();stop.notePhotos.addAll(notes);}
    static void preview(MainActivity a,LinearLayout box,Trip.Stop stop){if(!stop.previewPhoto.isEmpty()){image(a,box,stop.previewPhoto,150,false);a.space(box,14);}}
    static void notes(MainActivity a,LinearLayout box,Trip.Stop stop){if(stop.notePhotos.isEmpty())return;a.space(box,10);HorizontalScrollView scroll=new HorizontalScrollView(a);scroll.setHorizontalScrollBarEnabled(false);LinearLayout row=a.row();for(String path:stop.notePhotos){LinearLayout thumbnail=a.col();image(a,thumbnail,path,0,true);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(a.dp(90),-2);lp.rightMargin=a.dp(8);row.addView(thumbnail,lp);}scroll.addView(row);box.addView(scroll);}
    private static void image(MainActivity a,LinearLayout parent,String path,int height,boolean square){ImageView view=square?new ImageView(a){@Override protected void onMeasure(int width,int h){int side=MeasureSpec.getSize(width);super.onMeasure(width,MeasureSpec.makeMeasureSpec(side,MeasureSpec.EXACTLY));}}:new ImageView(a);view.setScaleType(square?ImageView.ScaleType.CENTER_CROP:ImageView.ScaleType.FIT_CENTER);view.setBackground(a.shape(MainActivity.PALE,14));view.setClipToOutline(true);view.setAlpha(1f);view.setContentDescription("查看地点照片");view.setOnClickListener(v->PhotoPreviewUi.show(a,path));parent.addView(view,new LinearLayout.LayoutParams(-1,square?-2:a.dp(height)));load(a,view,path,square?420:900,()->view.getParent()==null);}
    static void load(MainActivity a,ImageView view,String path,int size,BooleanSupplier invalid){String key=path+":"+size;Bitmap cached=CACHE.get(key);if(cached!=null){view.setImageBitmap(cached);return;}IMAGES.execute(()->{try{Bitmap bitmap=a.media.files.decode(path,size);if(bitmap==null)return;CACHE.put(key,bitmap);a.runOnUiThread(()->{if(!a.isDestroyed()&&!invalid.getAsBoolean()){view.setAlpha(1f);view.setImageBitmap(bitmap);}});}catch(Exception e){a.runOnUiThread(()->{if(!a.isDestroyed()&&!invalid.getAsBoolean())view.setContentDescription("照片暂时无法读取");});}});}
    /** Downloads only official public AMap image hosts, with a bound and HTTPS-only redirects. */
    static String download(MainActivity a,String address)throws Exception{
        if(address==null||address.isEmpty())return "";URL url=new URL(address);File tmp=File.createTempFile("poi-image-",".tmp",a.getCacheDir());
        try{for(int redirects=0;redirects<4;redirects++){
            if(!allowedImage(url))throw new IOException("图片来源暂不支持");HttpURLConnection c=(HttpURLConnection)url.openConnection();c.setConnectTimeout(7000);c.setReadTimeout(7000);c.setInstanceFollowRedirects(false);
            try{int status=c.getResponseCode();if(status>=300&&status<400){String location=c.getHeaderField("Location");if(location==null)throw new IOException();url=new URL(url,location);continue;}if(status!=200)throw new IOException();long size=0;try(InputStream in=c.getInputStream();OutputStream out=new FileOutputStream(tmp)){byte[] b=new byte[8192];for(int n;(n=in.read(b))!=-1;){size+=n;if(size>12*1024*1024)throw new IOException("图片过大");out.write(b,0,n);}}Bitmap bitmap=a.media.files.decode(Uri.fromFile(tmp),1200);try{return a.media.files.save(bitmap,"places",88);}finally{bitmap.recycle();}}finally{c.disconnect();}
        }throw new IOException("图片跳转次数过多");}finally{tmp.delete();}
    }
    static boolean allowedImage(URL u){String h=u.getHost().toLowerCase(Locale.ROOT);return "https".equals(u.getProtocol())&&u.getUserInfo()==null&&(u.getPort()==-1||u.getPort()==443)&&(h.equals("amap.com")||h.endsWith(".amap.com")||h.equals("autonavi.com")||h.endsWith(".autonavi.com")||h.equals("is.autonavi.com"));}
}
