package cn.lvxu.travel;

import android.app.*;
import android.graphics.*;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.*;
import android.widget.*;

final class PhotoEditorUi {
 static Dialog show(MainActivity a,Bitmap source,Uri original,MainActivity.ImageCallback saved,Runnable cancelled){return new Editor(a,source,original,saved,cancelled).show();}

 private static final class Editor implements Application.ActivityLifecycleCallbacks {
  final MainActivity a;final Uri original;final MainActivity.ImageCallback saved;final Runnable cancelled;
  final PhotoEditSession session;final Dialog dialog;final EditorView canvas;final LinearLayout root;final TextView hint,save;TextView crop,brush;
  boolean busy,delivered,closed;AlertDialog confirmation;
  Editor(MainActivity a,Bitmap source,Uri original,MainActivity.ImageCallback saved,Runnable cancelled){
   this.a=a;this.original=original;this.saved=saved;this.cancelled=cancelled;
   try{session=new PhotoEditSession(source);}finally{source.recycle();}
   dialog=new Dialog(a){@Override public void onBackPressed(){discard();}};dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);dialog.setCanceledOnTouchOutside(false);
   root=a.col();root.setBackground(a.shape(MainActivity.BG,24));root.setClipToOutline(true);a.pad(root,12);
   FrameLayout header=new FrameLayout(a);TextView title=a.bold("编辑照片",20,MainActivity.INK);title.setGravity(Gravity.CENTER);header.addView(title,new FrameLayout.LayoutParams(-1,a.dp(56)));
   TextView close=a.action("×",false,this::discard);close.setTextSize(28);close.setContentDescription("放弃照片编辑");header.addView(close,new FrameLayout.LayoutParams(a.dp(52),a.dp(52),Gravity.START|Gravity.CENTER_VERTICAL));
   save=a.action("√",true,this::save);save.setTextSize(26);save.setContentDescription("保存照片");header.addView(save,new FrameLayout.LayoutParams(a.dp(52),a.dp(52),Gravity.END|Gravity.CENTER_VERTICAL));root.addView(header);
   hint=a.text("保留完整画面与原比例，可直接保存",13,MainActivity.MUTED);hint.setGravity(Gravity.CENTER);hint.setPadding(0,a.dp(14),0,a.dp(14));root.addView(hint);
   canvas=new EditorView(a,session);canvas.setContentDescription("照片编辑画布");canvas.setBackground(a.shape(0xff17231F,20));canvas.setClipToOutline(true);root.addView(canvas,new LinearLayout.LayoutParams(-1,0,1));a.space(root,12);
   LinearLayout tools=a.row();crop=a.action("自由裁剪",false,()->{if(busy)return;if(canvas.mode==1){canvas.applyCrop();normal();}else{canvas.startCrop();crop.setText("应用裁剪");brush.setText("画笔");hint.setText("拖动四个角自由调整范围，再点应用裁剪");}});
   TextView rotate=a.action("旋转 90°",false,()->{if(busy)return;canvas.applyCrop();session.rotateClockwise();canvas.mode=0;normal();canvas.invalidate();});
   brush=a.action("画笔",false,()->{if(busy)return;canvas.applyCrop();canvas.mode=canvas.mode==2?0:2;crop.setText("自由裁剪");brush.setText(canvas.mode==2?"完成画笔":"画笔");hint.setText(canvas.mode==2?"在照片上绘画，画笔颜色为红色":"保留当前画面，点右上角 √ 保存");canvas.invalidate();});
   for(TextView tool:new TextView[]{crop,rotate,brush}){LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,a.dp(52),1);params.setMargins(a.dp(3),0,a.dp(3),0);tools.addView(tool,params);}root.addView(tools);
   dialog.setContentView(root);dialog.setOnDismissListener(d->{closed=true;if(confirmation!=null)confirmation.dismiss();a.getApplication().unregisterActivityLifecycleCallbacks(this);if(!busy)session.close();if(!delivered&&cancelled!=null)cancelled.run();});
  }
  Dialog show(){a.getApplication().registerActivityLifecycleCallbacks(this);dialog.show();Window window=dialog.getWindow();if(window!=null){window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));window.setLayout(-1,-1);window.setStatusBarColor(MainActivity.BG);window.setNavigationBarColor(MainActivity.BG);window.getDecorView().setSystemUiVisibility(MainActivity.DARK?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);}return dialog;}
  void normal(){canvas.mode=0;crop.setText("自由裁剪");brush.setText("画笔");hint.setText("保留当前画面，点右上角 √ 保存");canvas.invalidate();}
  void discard(){if(busy||closed)return;if(confirmation!=null&&confirmation.isShowing())return;confirmation=new RoundedDialogs.Builder(a).setTitle("放弃本次编辑？").setMessage("本次修改不会保存，原照片保持不变。").setNegativeButton("继续编辑",null).setPositiveButton("确认放弃",(d,w)->dialog.dismiss()).create();confirmation.show();}
  void save(){
   if(busy||closed)return;canvas.applyCrop();busy=true;save.setEnabled(false);hint.setText("正在保存照片…");canvas.setEnabled(false);
   new Thread(()->{String result=null;try{result=session.save(a.media.files,original);}catch(Exception|OutOfMemoryError ignored){}final String path=result;
    a.runOnUiThread(()->{busy=false;if(closed||a.isFinishing()||a.isDestroyed()){if(path!=null)a.media.files.file(path).delete();session.close();if(dialog.isShowing())dialog.dismiss();return;}
     if(path==null){save.setEnabled(true);canvas.setEnabled(true);hint.setText("保存失败，可重试或放弃编辑");a.toast("失败：无法保存照片，请检查存储空间");return;}
     delivered=true;dialog.dismiss();if(saved!=null)saved.selected(path);a.toast("成功");
    });
   },"photo-editor-save").start();
  }
  @Override public void onActivityDestroyed(Activity activity){if(activity==a&&dialog.isShowing())dialog.dismiss();}
  @Override public void onActivityCreated(Activity activity,Bundle state){}
  @Override public void onActivityStarted(Activity activity){}
  @Override public void onActivityResumed(Activity activity){}
  @Override public void onActivityPaused(Activity activity){}
  @Override public void onActivityStopped(Activity activity){}
  @Override public void onActivitySaveInstanceState(Activity activity,Bundle state){}
 }

 /** Fit-center display only; all edits are expressed in the image's own coordinates. */
 private static final class EditorView extends View {
  final PhotoEditSession session;final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);final RectF frame=new RectF(),selection=new RectF();
  int mode,corner;float lastX,lastY;
  EditorView(MainActivity a,PhotoEditSession session){super(a);this.session=session;}
  void imageFrame(){Bitmap b=session.bitmap();float scale=Math.min(getWidth()/(float)b.getWidth(),getHeight()/(float)b.getHeight());float w=b.getWidth()*scale,h=b.getHeight()*scale;frame.set((getWidth()-w)/2,(getHeight()-h)/2,(getWidth()+w)/2,(getHeight()+h)/2);}
  void startCrop(){mode=1;selection.set(0,0,session.bitmap().getWidth(),session.bitmap().getHeight());invalidate();}
  void applyCrop(){if(mode!=1)return;Rect area=new Rect(Math.max(0,(int)selection.left),Math.max(0,(int)selection.top),Math.min(session.bitmap().getWidth(),(int)Math.ceil(selection.right)),Math.min(session.bitmap().getHeight(),(int)Math.ceil(selection.bottom)));if(!area.isEmpty())session.crop(area);mode=0;invalidate();}
  float imageX(float x){return Math.max(0,Math.min(session.bitmap().getWidth(),(x-frame.left)*session.bitmap().getWidth()/frame.width()));}
  float imageY(float y){return Math.max(0,Math.min(session.bitmap().getHeight(),(y-frame.top)*session.bitmap().getHeight()/frame.height()));}
  @Override protected void onDraw(Canvas c){super.onDraw(c);if(session.bitmap().isRecycled())return;imageFrame();paint.setStyle(Paint.Style.FILL);paint.setColor(Color.WHITE);c.drawBitmap(session.bitmap(),null,frame,paint);if(mode!=1)return;
   float scale=frame.width()/session.bitmap().getWidth();RectF r=new RectF(frame.left+selection.left*scale,frame.top+selection.top*scale,frame.left+selection.right*scale,frame.top+selection.bottom*scale);
   paint.setColor(0x99000000);c.drawRect(frame.left,frame.top,frame.right,r.top,paint);c.drawRect(frame.left,r.bottom,frame.right,frame.bottom,paint);c.drawRect(frame.left,r.top,r.left,r.bottom,paint);c.drawRect(r.right,r.top,frame.right,r.bottom,paint);
   paint.setColor(Color.WHITE);paint.setStrokeWidth(2*getResources().getDisplayMetrics().density);paint.setStyle(Paint.Style.STROKE);c.drawRect(r,paint);paint.setStyle(Paint.Style.FILL);float radius=7*getResources().getDisplayMetrics().density;for(float x:new float[]{r.left,r.right})for(float y:new float[]{r.top,r.bottom})c.drawCircle(x,y,radius,paint);
  }
  @Override public boolean onTouchEvent(MotionEvent e){if(!isEnabled()||mode==0||session.bitmap().isRecycled())return false;imageFrame();float x=imageX(e.getX()),y=imageY(e.getY());int action=e.getActionMasked();
   if(action==MotionEvent.ACTION_DOWN){if(!frame.contains(e.getX(),e.getY()))return false;lastX=x;lastY=y;corner=(x>selection.centerX()?1:0)+(y>selection.centerY()?2:0);getParent().requestDisallowInterceptTouchEvent(true);if(mode==2)session.stroke(x,y,x,y,0xffEE554F,Math.max(2,session.bitmap().getWidth()/180f));}
   else if(action==MotionEvent.ACTION_MOVE){if(mode==2){session.stroke(lastX,lastY,x,y,0xffEE554F,Math.max(2,session.bitmap().getWidth()/180f));lastX=x;lastY=y;}else{if((corner&1)==0)selection.left=Math.min(x,selection.right-1);else selection.right=Math.max(x,selection.left+1);if((corner&2)==0)selection.top=Math.min(y,selection.bottom-1);else selection.bottom=Math.max(y,selection.top+1);}}
   else if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL)getParent().requestDisallowInterceptTouchEvent(false);invalidate();return true;
  }
 }
}
