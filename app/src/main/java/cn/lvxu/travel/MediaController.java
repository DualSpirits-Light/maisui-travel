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
    private MainActivity.ImageCallback callback;
    private File cameraFile; private boolean checkinCapture;
    private CheckinUi checkin;
    private Bundle restoredState;

    public MediaController(MainActivity activity){a=activity;files=new MediaFiles(a);}
    public File file(String relative){return files.file(relative);}

    public void pick(MainActivity.ImageCallback cb){callback=cb;checkinCapture=false;Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*");try{a.startActivityForResult(i,PICK);}catch(ActivityNotFoundException e){a.toast("未找到可选择图片的应用");}}

    public void takeCheckin(){if(checkin==null)checkin=new CheckinUi(a,this);checkin.show();}

    /** Keeps the page UI and activity-result coordinator on the same check-in instance. */
    void attachCheckin(CheckinUi ui){
        checkin=ui;
        if(restoredState!=null){ui.restoreState(restoredState);restoredState=null;}
        if(checkinCapture)callback=ui::photoSelected;
    }

    void chooseCheckinPhoto(){callback=path->{if(checkin!=null)checkin.photoSelected(path);};checkinCapture=true;
        AlertDialog chooser=new AlertDialog.Builder(a).setTitle("添加打卡照片").setItems(new String[]{"拍照","从相册选择","暂不添加照片"},(d,w)->{if(w==0)launchCamera();else if(w==1){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*");try{a.startActivityForResult(i,PICK);}catch(ActivityNotFoundException e){cancelSelection();a.toast("未找到可选择图片的应用");}}else{if(checkin!=null)checkin.photoSelected("");cancelSelection();}}).create();chooser.setOnCancelListener(d->cancelSelection());chooser.show();}

    private void launchCamera(){try{File dir=new File(a.getCacheDir(),"camera");if(!dir.exists()&&!dir.mkdirs())throw new IOException("Cannot create camera folder");cameraFile=File.createTempFile("capture-",".jpg",dir);Uri uri=AppFileProvider.uri(a,cameraFile);Intent i=new Intent(MediaStore.ACTION_IMAGE_CAPTURE).putExtra(MediaStore.EXTRA_OUTPUT,uri).addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_READ_URI_PERMISSION);i.setClipData(ClipData.newRawUri("capture",uri));a.startActivityForResult(i,CAMERA);}catch(Exception e){discardCamera();a.toast("无法启动相机，请改从相册选择");}}

    public boolean onResult(int req,int result,Intent data){
        if(req==EXPORT_CARD)return checkin!=null&&checkin.onResult(req,result,data);
        if(req!=PICK&&req!=CAMERA)return false;if(result!=Activity.RESULT_OK){if(req==CAMERA)discardCamera();cancelSelection();return true;}
        Uri uri=req==CAMERA&&cameraFile!=null?Uri.fromFile(cameraFile):(data==null?null:data.getData());if(uri==null){a.toast("未能读取所选照片");return true;}
        final Uri selected=uri;final Bitmap[] decoded={null};a.runJob("正在读取照片",()->{try{decoded[0]=files.decode(selected,2400);return "照片已读取";}catch(Exception e){if(req==CAMERA)discardCamera();cancelSelection();throw e;}},()->showCrop(decoded[0]));return true;
    }

    private void showCrop(Bitmap source){
        CropView crop=new CropView(a,source);FrameLayout box=new FrameLayout(a);box.setPadding(a.dp(12),a.dp(12),a.dp(12),0);box.addView(crop,new FrameLayout.LayoutParams(-1,a.dp(430)));
        AlertDialog dlg=new AlertDialog.Builder(a).setTitle("裁剪照片").setMessage("拖动照片调整位置，双指缩放").setView(box).setNegativeButton("取消",(d,w)->{source.recycle();discardCamera();cancelSelection();}).setPositiveButton("使用照片",null).create();
        dlg.setOnShowListener(x->dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{Bitmap out=crop.result(1600);dlg.dismiss();a.runJob("正在保存照片",()->{crop.savedPath=files.save(out,"photos",88);out.recycle();return crop.savedPath;},()->{if(cameraFile!=null){cameraFile.delete();cameraFile=null;}MainActivity.ImageCallback cb=callback;callback=null;checkinCapture=false;if(cb!=null)cb.selected(crop.savedPath);});}));
        dlg.setOnCancelListener(d->{if(!source.isRecycled())source.recycle();discardCamera();cancelSelection();});dlg.show();
    }

    public void showBackgroundPicker(){
        new AlertDialog.Builder(a).setTitle("主页背景").setItems(new String[]{"从相册选择并裁剪","使用图片网址","随机风景（Picsum Photos）","恢复默认插画"},(d,w)->{
            if(w==0)pick(path->{a.prefs.setBackground(path);a.render();});
            else if(w==1)backgroundUrlDialog();else if(w==2)downloadBackground("https://picsum.photos/1200/600","随机图片由 Picsum Photos 提供");
            else{a.prefs.setBackground("");a.render();}
        }).show();
    }

    private void backgroundUrlDialog(){LinearLayout f=a.col();EditText url=a.field(f,"HTTPS 图片网址","",android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI);a.dialog("使用网络图片",f,()->{String value=url.getText().toString().trim();if(!value.startsWith("https://")||value.length()>2048)throw new IllegalArgumentException("请输入有效的 HTTPS 图片网址");downloadBackground(value,"图片来自你提供的网址");},null);}

    private void downloadBackground(String url,String source){final String[] saved={null},error={null};a.runJob("正在下载背景",()->{try{saved[0]=download(url);}catch(Exception e){error[0]=e.getMessage();}return saved[0]==null?"网络图片不可用":source;},()->{if(saved[0]!=null){a.prefs.setBackground(saved[0]);a.render();a.toast(source);}else a.toast("网络图片不可用，已保留当前背景，可改用本地照片");});}
    private String download(String address)throws Exception{URL u=new URL(address);for(int redirects=0;redirects<4;redirects++){if(!"https".equalsIgnoreCase(u.getProtocol()))throw new IOException("Only HTTPS is supported");HttpURLConnection c=(HttpURLConnection)u.openConnection();c.setConnectTimeout(10000);c.setReadTimeout(15000);c.setInstanceFollowRedirects(false);c.setRequestProperty("User-Agent","Maisui/1 Android");int code=c.getResponseCode();if(code>=300&&code<400){String location=c.getHeaderField("Location");c.disconnect();if(location==null)throw new IOException("Redirect without location");u=new URL(u,location);continue;}if(code!=200)throw new IOException("Image server returned "+code);int size=c.getContentLength();if(size>24*1024*1024)throw new IOException("Image is too large");File temp=File.createTempFile("background-",".jpg",a.getCacheDir());try(InputStream in=c.getInputStream();OutputStream out=new FileOutputStream(temp)){byte[] b=new byte[16384];int n,total=0;while((n=in.read(b))!=-1){total+=n;if(total>24*1024*1024)throw new IOException("Image is too large");out.write(b,0,n);}}finally{c.disconnect();}Bitmap bitmap=files.decode(Uri.fromFile(temp),2400);temp.delete();String path=files.save(bitmap,"bgs",88);bitmap.recycle();return path;}throw new IOException("Too many redirects");}

    public void saveState(Bundle b){if(cameraFile!=null)b.putString("media.camera",cameraFile.getAbsolutePath());b.putBoolean("media.checkin",checkinCapture);if(checkin!=null)checkin.saveState(b);}
    public void restoreState(Bundle b){String p=b.getString("media.camera");if(p!=null)try{File candidate=new File(p).getCanonicalFile(),root=new File(a.getCacheDir(),"camera").getCanonicalFile();if(candidate.getPath().startsWith(root.getPath()+File.separator))cameraFile=candidate;}catch(IOException ignored){}checkinCapture=b.getBoolean("media.checkin");restoredState=new Bundle(b);}
    public void onPermissions(int request,String[] permissions,int[] results){if(request==LOCATION&&checkin!=null)checkin.onLocationPermission(results);}

    private void discardCamera(){if(cameraFile!=null)cameraFile.delete();cameraFile=null;}
    private void cancelSelection(){callback=null;checkinCapture=false;}

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
