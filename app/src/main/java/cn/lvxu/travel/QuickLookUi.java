package cn.lvxu.travel;

import android.Manifest;
import android.app.Dialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Window;
import android.webkit.WebView;
import android.widget.*;
import java.io.File;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;

/** A cancellable, private-cache export session. Nothing is published before explicit Save. */
final class QuickLookUi {
    static final int STORAGE_REQUEST=7631;
    interface Renderer { List<ItineraryImageRenderer.Sheet> render(Context c,Trip t,int day,ItineraryImageRenderer.Options o,File dir)throws Exception; }
    interface Saver { Uri save(Context c,File f,String name)throws Exception; }
    final MainActivity a;
    final Renderer renderer;
    final Saver saver;
    Future<?> job;
    final ExecutorService worker=Executors.newSingleThreadExecutor(r->new Thread(r,"quicklook-export"));
    volatile String previewUrl="";
    final Handler main=new Handler(Looper.getMainLooper());
    Dialog dialog;
    LinearLayout content;
    TextView status,generate,save,previous,next,back;
    WebView preview;
    final ArrayList<CheckBox> dates=new ArrayList<>();
    CheckBox notes,addresses,tags,costs,photos;
    List<ItineraryImageRenderer.Sheet> sheets=new ArrayList<>();
    final Set<Integer> saved=new HashSet<>();
    Trip trip;
    File directory;
    volatile long session;
    volatile boolean destroyed;
    boolean busy,permissionPending;
    int page;

    QuickLookUi(MainActivity a){this(a,ItineraryImageRenderer::render,ItineraryGallery::save);}
    QuickLookUi(MainActivity a,Renderer renderer,Saver saver){this.a=a;this.renderer=renderer;this.saver=saver;}
    void show(Trip t,int selectedDay){
        if(destroyed||a.isFinishing())return;
        close(); trip=t; notes=null;addresses=null;tags=null;costs=null;photos=null; dates.clear();sheets=new ArrayList<>();saved.clear();page=0;
        dialog=new Dialog(a);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        content=a.col();content.setPadding(a.dp(18),a.dp(16),a.dp(18),a.dp(16));content.setBackground(a.shape(MainActivity.SURFACE,24));content.setClipToOutline(true);
        dialog.setContentView(content);dialog.setOnDismissListener(d->close());dialog.show();
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        dialog.getWindow().setLayout(Math.min(a.getResources().getDisplayMetrics().widthPixels-a.dp(24),a.dp(600)),(int)(a.getResources().getDisplayMetrics().heightPixels*.86f));
        selection(selectedDay);
    }
    private TextView action(String label,Runnable run){TextView v=a.action(label,false,run);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=a.dp(6);content.addView(v,lp);return v;}
    private CheckBox check(LinearLayout target,String label,boolean checked){CheckBox c=new CheckBox(a);c.setText(label);c.setTextColor(MainActivity.INK);c.setChecked(checked);target.addView(c);return c;}
    private void selection(int selected){
        releasePreview();content.removeAllViews();content.addView(a.bold("生成随手看",22,MainActivity.INK));
        ScrollView scroll=new ScrollView(a);LinearLayout fields=a.col();scroll.addView(fields);content.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        fields.addView(a.text("选择日期（可多选）",15,MainActivity.MUTED));
        if(dates.isEmpty())for(int d=0;d<trip.days;d++)dates.add(check(fields,date(trip,d),d==Math.max(0,Math.min(selected,trip.days-1))));
        else for(CheckBox c:dates){if(c.getParent()!=null)((android.view.ViewGroup)c.getParent()).removeView(c);fields.addView(c);}
        fields.addView(a.text("图片包含",15,MainActivity.MUTED));
        boolean n=notes==null||notes.isChecked(),ad=addresses==null||addresses.isChecked(),ta=tags==null||tags.isChecked(),co=costs!=null&&costs.isChecked(),ph=photos!=null&&photos.isChecked();
        notes=check(fields,"备注",n);addresses=check(fields,"地址",ad);tags=check(fields,"标签",ta);costs=check(fields,"预计费用",co);photos=check(fields,"照片",ph);
        status=a.text("生成后可预览，确认后保存到相册。",13,MainActivity.MUTED);content.addView(status);
        generate=action("生成预览",this::generate);action("关闭",()->dialog.dismiss());
    }
    static String date(Trip t,int day){try{return LocalDate.parse(t.start).plusDays(day)+" · 第 "+(day+1)+" 天";}catch(Exception e){return "第 "+(day+1)+" 天";}}
    void generate(){
        if(busy||destroyed)return;ArrayList<Integer> chosen=new ArrayList<>();for(int i=0;i<dates.size();i++)if(dates.get(i).isChecked())chosen.add(i);
        if(chosen.isEmpty()){status.setText("请至少选择一天。");return;}
        final Trip snapshot;
        try{snapshot=trip.itinerarySnapshot();}catch(Exception e){status.setText("无法读取行程："+reason(e));return;}
        ItineraryImageRenderer.Options options=new ItineraryImageRenderer.Options();options.notes=notes.isChecked();options.addresses=addresses.isChecked();options.tags=tags.isChecked();options.costs=costs.isChecked();options.photos=photos.isChecked();
        busy=true;generate.setEnabled(false);final long token=session;final File dir=new File(a.getCacheDir(),"quicklook-"+UUID.randomUUID());directory=dir;status.setText("正在生成…");
        job=worker.submit(()->{try{
            if(!dir.mkdirs())throw new java.io.IOException("无法创建临时目录");ArrayList<ItineraryImageRenderer.Sheet> result=new ArrayList<>();
            for(int d:chosen){if(!alive(token))return;post(token,()->status.setText("正在生成 "+date(snapshot,d)));result.addAll(renderer.render(a.getApplicationContext(),snapshot,d,options,dir));}
            if(result.isEmpty())throw new java.io.IOException("没有生成可保存的图片");
            post(token,()->{busy=false;sheets=result;saved.clear();page=0;showPreview();});
        }catch(Exception|OutOfMemoryError e){post(token,()->{busy=false;generate.setEnabled(true);status.setText("生成失败："+reason(e));});delete(dir);}finally{if(!alive(token))delete(dir);}});
    }
    private void showPreview(){
        content.removeAllViews();content.addView(a.bold("随手看预览",22,MainActivity.INK));
        status=a.text("",13,MainActivity.MUTED);content.addView(status);
        preview=new WebView(a);preview.setBackgroundColor(MainActivity.SURFACE);preview.getSettings().setJavaScriptEnabled(false);preview.getSettings().setAllowFileAccess(true);preview.getSettings().setAllowContentAccess(false);preview.getSettings().setBuiltInZoomControls(true);preview.getSettings().setDisplayZoomControls(false);preview.getSettings().setUseWideViewPort(true);preview.getSettings().setLoadWithOverviewMode(true);preview.getSettings().setBlockNetworkLoads(true);preview.getSettings().setAllowFileAccessFromFileURLs(false);preview.getSettings().setAllowUniversalAccessFromFileURLs(false);
        preview.setWebViewClient(new android.webkit.WebViewClient(){
            public boolean shouldOverrideUrlLoading(WebView view,android.webkit.WebResourceRequest request){return true;}
            public android.webkit.WebResourceResponse shouldInterceptRequest(WebView view,android.webkit.WebResourceRequest request){String expected=previewUrl;return expected.equals(request.getUrl().toString())?null:new android.webkit.WebResourceResponse("text/plain","UTF-8",new java.io.ByteArrayInputStream(new byte[0]));}
        });
        content.addView(preview,new LinearLayout.LayoutParams(-1,0,1));content.addView(a.text("上下滑动查看长图；双指缩放查看文字。",12,MainActivity.MUTED));
        LinearLayout navigation=a.row();content.addView(navigation);previous=a.action("上一张",false,()->{if(!busy&&page>0){page--;loadPage();}});next=a.action("下一张",false,()->{if(!busy&&page+1<sheets.size()){page++;loadPage();}});LinearLayout.LayoutParams previousLayout=new LinearLayout.LayoutParams(0,-2,1);previousLayout.rightMargin=a.dp(4);LinearLayout.LayoutParams nextLayout=new LinearLayout.LayoutParams(0,-2,1);nextLayout.leftMargin=a.dp(4);navigation.addView(previous,previousLayout);navigation.addView(next,nextLayout);
        save=action("保存全部到相册",this::requestSave);back=action("返回重选",()->{if(!busy){File old=directory;directory=null;worker.execute(()->delete(old));sheets=new ArrayList<>();saved.clear();selection(0);}});
        loadPage();
    }
    private void loadPage(){ItineraryImageRenderer.Sheet s=sheets.get(page);previewUrl=Uri.fromFile(s.file).toString();preview.loadUrl(previewUrl);status.setText(date(trip,s.day)+" · 本日第 "+s.part+" 张 · 总第 "+(page+1)+" / "+sheets.size()+" 张");buttons();}
    private void buttons(){if(save==null)return;save.setEnabled(!busy&&!permissionPending&&saved.size()<sheets.size());save.setText(saved.isEmpty()?"保存全部到相册":saved.size()==sheets.size()?"已全部保存":"继续保存剩余 "+(sheets.size()-saved.size())+" 张");previous.setEnabled(!busy&&page>0);next.setEnabled(!busy&&page+1<sheets.size());back.setEnabled(!busy&&!permissionPending);}
    void requestSave(){if(busy||permissionPending||sheets.isEmpty()||saved.size()==sheets.size())return;
        if(Build.VERSION.SDK_INT<=28&&a.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)!=PackageManager.PERMISSION_GRANTED){permissionPending=true;buttons();a.requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},STORAGE_REQUEST);return;}saveRemaining();}
    void onRequestPermissionsResult(int code,String[] permissions,int[] results){if(code!=STORAGE_REQUEST||!permissionPending)return;permissionPending=false;if(destroyed||dialog==null)return;
        if(results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED)saveRemaining();else{status.setText("未获得相册写入权限，预览已保留。点击保存可再次申请。");buttons();}}
    private void saveRemaining(){busy=true;buttons();long token=session;List<ItineraryImageRenderer.Sheet> batch=new ArrayList<>(sheets);Set<Integer> done=new HashSet<>(saved);File dir=directory;
        job=worker.submit(()->{String failure=null;try{for(int i=0;i<batch.size();i++){if(!alive(token))return;if(done.contains(i))continue;final int current=i;post(token,()->status.setText("正在保存第 "+(current+1)+" / "+batch.size()+" 张…"));ItineraryImageRenderer.Sheet s=batch.get(i);saver.save(a.getApplicationContext(),s.file,"麦穗旅序_"+s.file.getName());done.add(i);}}catch(Exception|OutOfMemoryError e){failure=reason(e);}finally{if(!alive(token))delete(dir);}
            final String error=failure;post(token,()->{saved.clear();saved.addAll(done);busy=false;status.setText(error==null?"已保存 "+saved.size()+" 张到相册。":"已保存 "+saved.size()+" / "+batch.size()+" 张；其余未保存："+error+"。可重试剩余图片。");buttons();});});
    }
    private boolean alive(long token){return !destroyed&&session==token;}
    private void post(long token,Runnable r){main.post(()->{if(alive(token)&&dialog!=null&&dialog.isShowing()&&!a.isFinishing())r.run();});}
    static String reason(Throwable e){if(e instanceof OutOfMemoryError)return "图片过大，内存不足，请减少所选日期或关闭照片";String m=e.getMessage();return m==null||m.trim().isEmpty()?e.getClass().getSimpleName():m;}
    private void releasePreview(){previewUrl="";if(preview!=null){preview.stopLoading();if(preview.getParent()!=null)((android.view.ViewGroup)preview.getParent()).removeView(preview);preview.loadUrl("about:blank");preview.destroy();preview=null;}}
    private void close(){session++;if(job!=null){job.cancel(true);job=null;}permissionPending=false;busy=false;releasePreview();Dialog old=dialog;dialog=null;if(old!=null){old.setOnDismissListener(null);old.dismiss();}File dir=directory;directory=null;if(!worker.isShutdown())worker.execute(()->delete(dir));}
    void destroy(){if(destroyed)return;destroyed=true;close();worker.shutdown();}
    private static void delete(File f){if(f==null)return;File[] children=f.listFiles();if(children!=null)for(File c:children)delete(c);f.delete();}
}
