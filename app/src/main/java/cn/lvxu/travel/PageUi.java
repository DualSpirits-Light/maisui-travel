package cn.lvxu.travel;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.*;
import android.widget.*;

/** Independent native page with system back and optional circular reveal. */
final class PageUi {
 final MainActivity a; final Dialog dialog; final LinearLayout body,surface;
 PageUi(MainActivity a,String title){this.a=a;dialog=new Dialog(a);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);surface=a.col();surface.setBackgroundColor(MainActivity.BG);FrameLayout head=new FrameLayout(a);TextView name=a.bold(title,21,MainActivity.INK);name.setGravity(Gravity.CENTER);name.setMinHeight(a.dp(56));name.setPadding(0,a.dp(10),0,a.dp(10));FrameLayout.LayoutParams titleParams=new FrameLayout.LayoutParams(-1,-2);titleParams.leftMargin=a.dp(56);titleParams.rightMargin=a.dp(56);head.setMinimumHeight(a.dp(56));head.addView(name,titleParams);ImageButton back=new ImageButton(a);back.setImageResource(R.drawable.ic_back);back.setColorFilter(MainActivity.INK);back.setBackgroundColor(Color.TRANSPARENT);back.setContentDescription("返回");back.setOnClickListener(v->dialog.dismiss());head.addView(back,new FrameLayout.LayoutParams(a.dp(56),a.dp(56),Gravity.START|Gravity.CENTER_VERTICAL));surface.addView(head);ScrollView scroll=new ScrollView(a);body=a.col();a.pad(body,20);scroll.addView(body);surface.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));dialog.setContentView(surface);}
 void show(){show(false);}
 void show(boolean reveal){dialog.show();surface.post(()->ThemeViews.apply(surface,MainActivity.BG));Window w=dialog.getWindow();if(w!=null){w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);w.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);w.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);w.getDecorView().setSystemUiVisibility(ThemeColors.isDark(MainActivity.BG)?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));w.setLayout(-1,-1);w.setStatusBarColor(MainActivity.BG);w.setNavigationBarColor(MainActivity.BG);}if(reveal)surface.post(()->{if(!surface.isAttachedToWindow())return;try{android.animation.Animator anim=ViewAnimationUtils.createCircularReveal(surface,surface.getWidth()/2,a.dp(30),0,(float)Math.hypot(surface.getWidth(),surface.getHeight()));anim.setDuration(350);anim.start();}catch(Exception ignored){surface.setAlpha(1);}});}
 boolean alive(){return dialog.isShowing()&&!a.isFinishing()&&!a.isDestroyed();}
}
