package cn.lvxu.travel;

import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.net.Uri;
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
        if(panel!=null){panel.removeAllViews();if(!preview.isEmpty()){image(a,panel,preview,140);a.space(panel,8);}else{panel.addView(a.text("预览图 · 可选，无图时显示地点名称",13,MainActivity.MUTED));a.space(panel,8);}a.pair(panel,a.action(preview.isEmpty()?"＋ 添加预览图":"更换预览图",false,()->a.pickImage(path->{preview=path;refresh();})),a.action("使用文字预览",false,()->{preview="";refresh();}));a.space(panel,18);}
        if(notePanel!=null){notePanel.removeAllViews();notePanel.addView(a.text("备注照片 · 可选，已添加 "+notes.size()+" 张",13,MainActivity.MUTED));for(String path:new ArrayList<>(notes)){LinearLayout row=a.row();row.addView(a.action("查看照片 "+(notes.indexOf(path)+1),false,()->show(a,path)),new LinearLayout.LayoutParams(0,-2,1));row.addView(a.action("移除",false,()->{notes.remove(path);refresh();}));notePanel.addView(row);}a.space(notePanel,8);notePanel.addView(a.action("＋ 添加备注照片",false,()->{if(notes.size()>=20){a.toast("备注照片最多 20 张");return;}a.pickImage(path->{notes.add(path);refresh();});}));a.space(notePanel,18);}
    }

    void apply(Trip.Stop stop){stop.previewPhoto=preview;stop.notePhotos.clear();stop.notePhotos.addAll(notes);}
    static void preview(MainActivity a,LinearLayout box,Trip.Stop stop){if(!stop.previewPhoto.isEmpty()){image(a,box,stop.previewPhoto,150);a.space(box,14);}}
    static void notes(MainActivity a,LinearLayout box,Trip.Stop stop){if(stop.notePhotos.isEmpty())return;a.space(box,10);HorizontalScrollView scroll=new HorizontalScrollView(a);scroll.setHorizontalScrollBarEnabled(false);LinearLayout row=a.row();for(String path:stop.notePhotos){LinearLayout thumbnail=a.col();image(a,thumbnail,path,76);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(a.dp(90),-2);lp.rightMargin=a.dp(8);row.addView(thumbnail,lp);}scroll.addView(row);box.addView(scroll);}
    private static void image(MainActivity a,LinearLayout parent,String path,int height){ImageView image=new ImageView(a);image.setScaleType(ImageView.ScaleType.CENTER_CROP);image.setBackground(a.shape(MainActivity.PALE,16));image.setClipToOutline(true);image.setContentDescription("查看地点照片");image.setOnClickListener(v->show(a,path));parent.addView(image,new LinearLayout.LayoutParams(-1,a.dp(height)));Bitmap cached=CACHE.get(path);if(cached!=null){image.setImageBitmap(cached);return;}IMAGES.execute(()->{try{Bitmap bitmap=a.media.files.decode(path,600);CACHE.put(path,bitmap);a.runOnUiThread(()->{if(!a.isDestroyed()&&image.getParent()!=null){image.setImageBitmap(bitmap);image.setAlpha(.4f);image.animate().alpha(1).setDuration(180).start();}});}catch(Exception ignored){}});}
    private static void show(MainActivity a,String path){try{Bitmap bitmap=a.media.files.decode(path,1600);ImageView view=new ImageView(a);view.setAdjustViewBounds(true);view.setImageBitmap(bitmap);ScrollView scroll=new ScrollView(a);scroll.addView(view);new AlertDialog.Builder(a).setView(scroll).setPositiveButton("关闭",null).show();}catch(Exception e){a.toast("无法读取照片");}}
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
