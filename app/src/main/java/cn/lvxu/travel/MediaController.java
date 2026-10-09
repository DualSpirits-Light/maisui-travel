package cn.lvxu.travel;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.net.*;
import java.util.concurrent.Callable;

/** Gallery, camera, crop and background coordinator. */
public final class MediaController {
    static final int PICK=200,CAMERA=201,LOCATION=202,EXPORT_CARD=203;
    final MainActivity a; final MediaFiles files;
    enum SelectionOwner { NONE, CHECKIN, EXPENSE, PLACE_PREVIEW, PLACE_NOTE, AVATAR, BACKGROUND }
    private SelectionOwner selectionOwner=SelectionOwner.NONE;
    private Intent queuedData; private int queuedRequest,queuedResult;
    private MainActivity.ImageCallback callback;private boolean cropSelection=true;
    private File cameraFile; private boolean checkinCapture;private String checkinPhotoKind="group";
    private CheckinUi checkin;
    private Bundle restoredState;
    private OriginalImport originalImport;
    private static final java.util.concurrent.ExecutorService ORIGINALS=java.util.concurrent.Executors.newFixedThreadPool(2);
    private static final java.util.concurrent.ConcurrentHashMap<String,OriginalImport> IMPORTS=new java.util.concurrent.ConcurrentHashMap<>();
    // Test barrier runs on the actual import worker, before any source bytes are read.
    static volatile Runnable originalImportBarrier;
    private static final class OriginalImport {
        final String token;final Uri uri;final File camera,receipt;final MediaFiles files;
        volatile boolean done,cancelled;volatile String path;
        java.lang.ref.WeakReference<MediaController> receiver;
        OriginalImport(String token,Uri uri,File camera,Context context){this.token=token;this.uri=uri;this.camera=camera;files=new MediaFiles(context.getApplicationContext());File directory=new File(context.getCacheDir(),"media-imports");directory.mkdirs();receipt=new File(directory,token+".result");}
        void start(){ORIGINALS.execute(()->{String saved=null;try{Runnable barrier=originalImportBarrier;if(barrier!=null)barrier.run();if(!cancelled)saved=files.importOriginal(uri);}catch(Exception|OutOfMemoryError ignored){}if(saved!=null)try{java.nio.file.Files.write(receipt.toPath(),saved.getBytes(java.nio.charset.StandardCharsets.UTF_8));}catch(IOException ignored){}if(camera!=null&&receipt.isFile())camera.delete();path=saved;done=true;new android.os.Handler(android.os.Looper.getMainLooper()).post(()->{if(cancelled){discard();return;}MediaController controller=receiver==null?null:receiver.get();if(controller!=null)controller.deliverOriginal();});});}
        void discard(){receipt.delete();if(camera!=null)camera.delete();if(path!=null){File f=files.file(path);if(f!=null)f.delete();}IMPORTS.remove(token,this);}
    }
    private void beginOriginal(Uri uri,int req,Intent data){
        if(data!=null&&"content".equals(uri.getScheme()))try{a.getContentResolver().takePersistableUriPermission(uri,data.getFlags()&(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION));}catch(SecurityException|IllegalArgumentException ignored){}
        File captured=req==CAMERA?cameraFile:null;if(captured!=null)cameraFile=null;
        originalImport=new OriginalImport(java.util.UUID.randomUUID().toString(),uri,captured,a.getApplicationContext());originalImport.receiver=new java.lang.ref.WeakReference<>(this);IMPORTS.put(originalImport.token,originalImport);originalImport.start();
    }
    private void deliverOriginal(){
        OriginalImport job=originalImport;if(job==null||!job.done||job.cancelled||callback==null||a.isFinishing()||a.isDestroyed()||job.receiver==null||job.receiver.get()!=this)return;
        MainActivity.ImageCallback selected=callback;String path=job.path;originalImport=null;cancelSelection();
        // Keep recent completed receipts available to state saved just before worker completion.
        if(IMPORTS.size()>64)for(OriginalImport previous:IMPORTS.values())if(previous!=job&&previous.done&&previous.receiver!=null&&previous.receiver.get()==null)IMPORTS.remove(previous.token,previous);
        if(path==null)a.toast("失败：无法保存照片，请检查存储空间");else selected.selected(path);
    }
    private void restoreOriginal(Bundle b){
        String token=b.getString("media.import.token");if(token==null||!token.matches("[a-fA-F0-9-]{36}"))return;originalImport=IMPORTS.get(token);
        if(originalImport==null){String uri=b.getString("media.import.uri");if(uri==null)return;File camera=null;String savedCamera=b.getString("media.import.camera");if(savedCamera!=null)try{File f=new File(savedCamera).getCanonicalFile(),root=new File(a.getCacheDir(),"camera").getCanonicalFile();if(f.getPath().startsWith(root.getPath()+File.separator))camera=f;}catch(IOException ignored){}
            originalImport=new OriginalImport(token,Uri.parse(uri),camera,a.getApplicationContext());IMPORTS.put(token,originalImport);try{String path=new String(java.nio.file.Files.readAllBytes(originalImport.receipt.toPath()),java.nio.charset.StandardCharsets.UTF_8);File result=files.file(path);if(result!=null&&result.isFile()){originalImport.path=path;originalImport.done=true;}}catch(IOException|IllegalArgumentException ignored){}if(!originalImport.done)originalImport.start();}
        originalImport.receiver=new java.lang.ref.WeakReference<>(this);
    }

    public MediaController(MainActivity activity){a=activity;files=new MediaFiles(a);}
    public File file(String relative){return files.file(relative);}

    public void pick(MainActivity.ImageCallback cb){pick(SelectionOwner.NONE,cb);}
    public void pick(SelectionOwner owner,MainActivity.ImageCallback cb){selectionOwner=owner;pick(cb,true);}
    public void pickOriginal(MainActivity.ImageCallback cb){pickOriginal(SelectionOwner.NONE,cb);}
    public void pickOriginal(SelectionOwner owner,MainActivity.ImageCallback cb){selectionOwner=owner;pick(cb,false);}
    private void pick(MainActivity.ImageCallback cb,boolean crop){callback=cb;cropSelection=crop;checkinCapture=false;Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*");try{a.startActivityForResult(i,PICK);}catch(ActivityNotFoundException e){cancelSelection();a.toast("失败：未找到可选择图片的应用");}}

    /** Original photo chooser shared by forms. The form owns the resulting private copy. */
    public void choosePhoto(MainActivity.ImageCallback cb){choosePhoto(SelectionOwner.NONE,cb);}
    public void choosePhoto(SelectionOwner owner,MainActivity.ImageCallback cb){
        selectionOwner=owner;
        callback=cb;cropSelection=false;checkinCapture=false;
        AlertDialog chooser=new RoundedDialogs.Builder(a).setTitle("添加照片").setItems(new String[]{"拍照","从相册选择"},(d,w)->{if(w==0)launchCamera();else pick(cb,false);}).setNegativeButton("取消",(d,w)->cancelSelection()).create();
        chooser.setOnCancelListener(d->cancelSelection());chooser.show();
    }

    public void takeCheckin(){if(checkin==null)checkin=new CheckinUi(a,this);checkin.show();}

    /** Keeps the page UI and activity-result coordinator on the same check-in instance. */
    void attachCheckin(CheckinUi ui){
        checkin=ui;
        if(restoredState!=null){ui.restoreState(restoredState);restoredState=null;}
        if(checkinCapture)attachSelection(SelectionOwner.CHECKIN,path->ui.photoSelected(path,checkinPhotoKind));
    }

    void chooseCheckinPhoto(){chooseCheckinPhoto("group");}
    void chooseCheckinPhoto(String kind){selectionOwner=SelectionOwner.CHECKIN;cropSelection=false;checkinPhotoKind=kind;callback=path->{if(checkin!=null)checkin.photoSelected(path,kind);};checkinCapture=true;
        String label="scenery".equals(kind)?"风景":"合照";AlertDialog chooser=new RoundedDialogs.Builder(a).setTitle("添加"+label).setItems(new String[]{"拍照","从相册选择","暂不添加照片"},(d,w)->{if(w==0)launchCamera();else if(w==1){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*");try{a.startActivityForResult(i,PICK);}catch(ActivityNotFoundException e){cancelSelection();a.toast("失败：未找到可选择图片的应用");}}else{if(checkin!=null)checkin.photoSelected("",kind);cancelSelection();}}).create();chooser.setOnCancelListener(d->cancelSelection());chooser.show();}

    private void launchCamera(){try{File dir=new File(a.getCacheDir(),"camera");if(!dir.exists()&&!dir.mkdirs())throw new IOException("Cannot create camera folder");cameraFile=File.createTempFile("capture-",".jpg",dir);Uri uri=AppFileProvider.uri(a,cameraFile);Intent i=new Intent(MediaStore.ACTION_IMAGE_CAPTURE).putExtra(MediaStore.EXTRA_OUTPUT,uri).addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_READ_URI_PERMISSION);i.setClipData(ClipData.newRawUri("capture",uri));a.startActivityForResult(i,CAMERA);}catch(Exception e){discardCamera();cancelSelection();a.toast("失败：无法启动相机，请改从相册选择");}}

    public boolean onResult(int req,int result,Intent data){
        if(req==EXPORT_CARD)return checkin!=null&&checkin.onResult(req,result,data);
        if(req!=PICK&&req!=CAMERA)return false;
        if(callback==null&&selectionOwner!=SelectionOwner.NONE){queuedRequest=req;queuedResult=result;queuedData=data;return true;}
        if(result!=Activity.RESULT_OK){if(req==CAMERA)discardCamera();cancelSelection();return true;}
        Uri uri=req==CAMERA&&cameraFile!=null?Uri.fromFile(cameraFile):(data==null?null:data.getData());if(uri==null){discardCamera();cancelSelection();a.toast("失败：未能读取所选照片");return true;}
        if(checkinCapture){showCheckinEditor(uri);return true;}
        if(callback==null){discardCamera();cancelSelection();return true;}
        if(!cropSelection){beginOriginal(uri,req,data);return true;}
        final Uri selected=uri;final Bitmap[] decoded={null};a.runJob("正在读取照片",()->{try{decoded[0]=files.decode(selected,2400);return null;}catch(Exception e){if(req==CAMERA)discardCamera();cancelSelection();throw new IOException("失败：无法读取照片，请选择其他图片");}},()->showCrop(decoded[0]));return true;
    }

    private void showCheckinEditor(Uri uri){
        final MainActivity.ImageCallback selectedCallback=callback;
        // Post our own completion so a destroyed activity cannot strand a decoded bitmap.
        a.runJob("正在读取照片",()->{Bitmap decoded=null;try{decoded=files.decode(uri,2400);}catch(Exception|OutOfMemoryError ignored){}final Bitmap source=decoded;
            a.runOnUiThread(()->{if(a.isFinishing()||a.isDestroyed()){if(source!=null)source.recycle();discardCamera();cancelSelection();return;}
                if(source==null){discardCamera();cancelSelection();a.toast("失败：无法读取照片，请选择其他图片");return;}
                cancelSelection();
                try{PhotoEditorUi.show(a,source,uri,path->{discardCamera();if(selectedCallback!=null)selectedCallback.selected(path);},()->{discardCamera();cancelSelection();});}
                catch(Exception|OutOfMemoryError ignored){if(!source.isRecycled())source.recycle();discardCamera();a.toast("失败：无法打开照片编辑，请选择较小的图片");}
            });return null;
        },null);
    }

    private void showCrop(Bitmap source){
        CropView crop=new CropView(a,source);FrameLayout box=new FrameLayout(a);box.setPadding(a.dp(12),a.dp(12),a.dp(12),0);box.addView(crop,new FrameLayout.LayoutParams(-1,a.dp(430)));
        AlertDialog dlg=new RoundedDialogs.Builder(a).setTitle("裁剪照片").setMessage("拖动照片调整位置，双指缩放").setView(box).setNegativeButton("取消",(d,w)->{source.recycle();discardCamera();cancelSelection();}).setPositiveButton("使用照片",null).create();
        dlg.setOnShowListener(x->dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{Bitmap out=crop.result(1600);dlg.dismiss();a.runJob("正在保存照片",()->{try{crop.savedPath=files.save(out,"photos",88);return "成功";}catch(Exception e){discardCamera();cancelSelection();throw new IOException("失败：无法保存照片，请检查存储空间");}finally{out.recycle();}},()->{if(cameraFile!=null){cameraFile.delete();cameraFile=null;}MainActivity.ImageCallback cb=callback;cancelSelection();if(cb!=null)cb.selected(crop.savedPath);});}));
        dlg.setOnCancelListener(d->{if(!source.isRecycled())source.recycle();discardCamera();cancelSelection();});dlg.show();
    }

    public void showBackgroundPicker(){
        new RoundedDialogs.Builder(a).setTitle("主页背景").setItems(new String[]{"从相册选择并裁剪","使用图片网址","随机风景（Picsum Photos）","恢复默认插画"},(d,w)->{
            if(w==0)pick(SelectionOwner.BACKGROUND,path->{a.prefs.setBackground(path);a.render();});
            else if(w==1)backgroundUrlDialog();else if(w==2)downloadBackground(new ApiConfig(a).scenery(),"随机图片来自高级设置中的风景接口");
            else{a.prefs.setBackground("");a.render();}
        }).show();
    }

    private void backgroundUrlDialog(){LinearLayout f=a.col();EditText url=a.field(f,"HTTPS 图片网址","",android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI);a.dialog("使用网络图片",f,()->{String value=url.getText().toString().trim();if(!value.startsWith("https://")||value.length()>2048)throw new IllegalArgumentException("请输入有效的 HTTPS 图片网址");downloadBackground(value,"图片来自你提供的网址");},null);}

    private void downloadBackground(String url,String source){final String[] saved={null},error={null};a.runJob("正在下载背景",()->{try{saved[0]=download(url);}catch(Exception e){error[0]=e.getMessage();}return saved[0]==null?"网络图片不可用":source;},()->{if(saved[0]!=null){a.prefs.setBackground(saved[0]);a.render();a.toast(source);}else a.toast("网络图片不可用，已保留当前背景，可改用本地照片");});}
    private String download(String address)throws Exception{URL u=new URL(address);for(int redirects=0;redirects<4;redirects++){if(!"https".equalsIgnoreCase(u.getProtocol()))throw new IOException("Only HTTPS is supported");HttpURLConnection c=(HttpURLConnection)u.openConnection();c.setConnectTimeout(10000);c.setReadTimeout(15000);c.setInstanceFollowRedirects(false);c.setRequestProperty("User-Agent","Maisui/1 Android");int code=c.getResponseCode();if(code>=300&&code<400){String location=c.getHeaderField("Location");c.disconnect();if(location==null)throw new IOException("Redirect without location");u=new URL(u,location);continue;}if(code!=200)throw new IOException("Image server returned "+code);int size=c.getContentLength();if(size>24*1024*1024)throw new IOException("Image is too large");File temp=File.createTempFile("background-",".jpg",a.getCacheDir());try(InputStream in=c.getInputStream();OutputStream out=new FileOutputStream(temp)){byte[] b=new byte[16384];int n,total=0;while((n=in.read(b))!=-1){total+=n;if(total>24*1024*1024)throw new IOException("Image is too large");out.write(b,0,n);}}finally{c.disconnect();}Bitmap bitmap=files.decode(Uri.fromFile(temp),2400);temp.delete();String path=files.save(bitmap,"bgs",88);bitmap.recycle();return path;}throw new IOException("Too many redirects");}

    void attachSelection(SelectionOwner owner,MainActivity.ImageCallback cb){
        if(selectionOwner!=owner)return;
        callback=cb;deliverOriginal();
        if(queuedRequest!=0){int request=queuedRequest,result=queuedResult;Intent data=queuedData;queuedRequest=0;queuedData=null;onResult(request,result,data);}
    }
    void detachSelection(SelectionOwner owner){if(selectionOwner==owner){discardCamera();cancelSelection();}}
    SelectionOwner selectionOwner(){return selectionOwner;}
    public void saveState(Bundle b){if(originalImport!=null){b.putString("media.import.token",originalImport.token);b.putString("media.import.uri",originalImport.uri.toString());if(originalImport.camera!=null)b.putString("media.import.camera",originalImport.camera.getAbsolutePath());}b.putString("media.owner",selectionOwner.name());b.putInt("media.queued.request",queuedRequest);b.putInt("media.queued.result",queuedResult);b.putParcelable("media.queued.data",queuedData);if(cameraFile!=null)b.putString("media.camera",cameraFile.getAbsolutePath());b.putBoolean("media.crop",cropSelection);b.putBoolean("media.checkin",checkinCapture);b.putString("media.checkin.kind",checkinPhotoKind);if(checkin!=null)checkin.saveState(b);}
    public void restoreState(Bundle b){try{selectionOwner=SelectionOwner.valueOf(b.getString("media.owner","NONE"));}catch(IllegalArgumentException ignored){selectionOwner=SelectionOwner.NONE;}queuedRequest=b.getInt("media.queued.request");queuedResult=b.getInt("media.queued.result");queuedData=b.getParcelable("media.queued.data");String p=b.getString("media.camera");if(p!=null)try{File candidate=new File(p).getCanonicalFile(),root=new File(a.getCacheDir(),"camera").getCanonicalFile();if(candidate.getPath().startsWith(root.getPath()+File.separator))cameraFile=candidate;}catch(IOException ignored){}cropSelection=b.getBoolean("media.crop",true);checkinCapture=b.getBoolean("media.checkin");checkinPhotoKind=b.getString("media.checkin.kind","group");restoredState=new Bundle(b);restoreOriginal(b);if(selectionOwner==SelectionOwner.AVATAR)attachSelection(selectionOwner,path->{a.prefs.setAvatar(path);a.render();});if(selectionOwner==SelectionOwner.BACKGROUND)attachSelection(selectionOwner,path->{a.prefs.setBackground(path);a.render();});}
    public void onPermissions(int request,String[] permissions,int[] results){if(request==LOCATION&&checkin!=null)checkin.onLocationPermission(results);}

    private void discardCamera(){if(cameraFile!=null)cameraFile.delete();cameraFile=null;}
    private void cancelSelection(){if(originalImport!=null){OriginalImport job=originalImport;originalImport=null;if(job.receiver==null||job.receiver.get()==this){job.cancelled=true;if(job.done)job.discard();}}callback=null;checkinCapture=false;selectionOwner=SelectionOwner.NONE;queuedRequest=0;queuedData=null;}

    static final class CropView extends View {
        final Bitmap bitmap;final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);float scale=1,tx,ty,lastX,lastY,span;boolean multi;String savedPath;
        CropView(Context c,Bitmap b){super(c);bitmap=b;setBackgroundColor(Color.BLACK);}
        private float minScale(){return Math.max(getWidth()/(float)bitmap.getWidth(),getHeight()/(float)bitmap.getHeight());}
        @Override protected void onSizeChanged(int w,int h,int ow,int oh){scale=minScale();tx=(w-bitmap.getWidth()*scale)/2;ty=(h-bitmap.getHeight()*scale)/2;}
        @Override protected void onDraw(Canvas c){c.drawBitmap(bitmap,null,new RectF(tx,ty,tx+bitmap.getWidth()*scale,ty+bitmap.getHeight()*scale),paint);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2);paint.setColor(0xbbffffff);c.drawRect(1,1,getWidth()-1,getHeight()-1,paint);paint.setStyle(Paint.Style.FILL);}
        @Override public boolean onTouchEvent(android.view.MotionEvent e){if(e.getPointerCount()>1){float dx=e.getX(0)-e.getX(1),dy=e.getY(0)-e.getY(1),s=(float)Math.hypot(dx,dy);if(span>0){float old=scale;scale=Math.max(minScale(),Math.min(minScale()*5,scale*s/span));float cx=getWidth()/2f,cy=getHeight()/2f;tx=cx-(cx-tx)*scale/old;ty=cy-(cy-ty)*scale/old;}span=s;multi=true;}else{if(e.getAction()==0){lastX=e.getX();lastY=e.getY();multi=false;}else if(e.getAction()==2&&!multi){tx+=e.getX()-lastX;ty+=e.getY()-lastY;lastX=e.getX();lastY=e.getY();}}if(e.getAction()==1||e.getAction()==3){span=0;clamp();}invalidate();return true;}
        void clamp(){tx=Math.min(0,Math.max(getWidth()-bitmap.getWidth()*scale,tx));ty=Math.min(0,Math.max(getHeight()-bitmap.getHeight()*scale,ty));}
        Bitmap result(int max){clamp();int w=Math.min(max,getWidth()),h=Math.min(max,getHeight());Bitmap out=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(out);c.scale(w/(float)getWidth(),h/(float)getHeight());c.drawBitmap(bitmap,null,new RectF(tx,ty,tx+bitmap.getWidth()*scale,ty+bitmap.getHeight()*scale),paint);bitmap.recycle();return out;}
    }
}
