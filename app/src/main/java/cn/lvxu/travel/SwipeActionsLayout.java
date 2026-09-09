package cn.lvxu.travel;
import android.content.Context;
import android.view.*;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;

/** Short deliberate swipes reveal a compact grid; vertical scrolling stays native. */
final class SwipeActionsLayout extends FrameLayout {
 private float downX,downY,start;private boolean dragging,targetOpen,wasOpen;private final int slop;private VelocityTracker velocity;
 SwipeActionsLayout(Context c){super(c);slop=ViewConfiguration.get(c).getScaledTouchSlop();setTag("trip-card");setClipChildren(true);}
 private View front(){return getChildCount()>1?getChildAt(1):null;}
 private float dp(float n){return n*getResources().getDisplayMetrics().density;}
 private float reveal(){return Math.min(getWidth()*.6f,dp(176));}
 boolean isOpen(){return targetOpen||(front()!=null&&front().getTranslationX()<-1);}
 void close(){settle(false);}
 private void settle(boolean open){View f=front();if(f==null)return;targetOpen=open;if(open)getChildAt(0).setVisibility(VISIBLE);f.animate().cancel();f.animate().translationX(open?-reveal():0).setDuration(230).setInterpolator(new DecelerateInterpolator(1.7f)).withEndAction(()->{if(!targetOpen)getChildAt(0).setVisibility(INVISIBLE);}).start();}
 @Override protected void onSizeChanged(int w,int h,int ow,int oh){super.onSizeChanged(w,h,ow,oh);if(getChildCount()>0){View actions=getChildAt(0);LayoutParams p=(LayoutParams)actions.getLayoutParams();p.width=(int)reveal();actions.setLayoutParams(p);if(targetOpen&&front()!=null)front().setTranslationX(-reveal());}}
 @Override public boolean onInterceptTouchEvent(MotionEvent e){if(front()==null)return false;if(e.getActionMasked()==MotionEvent.ACTION_DOWN){downX=e.getX();downY=e.getY();start=front().getTranslationX();wasOpen=targetOpen;dragging=false;front().animate().cancel();recycleVelocity();velocity=VelocityTracker.obtain();velocity.addMovement(e);}else if(e.getActionMasked()==MotionEvent.ACTION_MOVE){float dx=e.getX()-downX,dy=e.getY()-downY;if(Math.abs(dx)>slop&&Math.abs(dx)>Math.abs(dy)*1.3f){getChildAt(0).setVisibility(VISIBLE);dragging=true;getParent().requestDisallowInterceptTouchEvent(true);return true;}}else if(e.getActionMasked()==MotionEvent.ACTION_CANCEL||e.getActionMasked()==MotionEvent.ACTION_UP)recycleVelocity();return false;}
 @Override public boolean onTouchEvent(MotionEvent e){if(!dragging||front()==null)return super.onTouchEvent(e);if(velocity!=null)velocity.addMovement(e);if(e.getActionMasked()==MotionEvent.ACTION_MOVE){front().setTranslationX(Math.max(-reveal(),Math.min(0,start+e.getX()-downX)));return true;}if(e.getActionMasked()==MotionEvent.ACTION_UP||e.getActionMasked()==MotionEvent.ACTION_CANCEL){float dx=e.getX()-downX,speed=0;if(velocity!=null){velocity.computeCurrentVelocity(1000);speed=velocity.getXVelocity();}boolean open=wasOpen;if(e.getActionMasked()==MotionEvent.ACTION_UP){if(Math.abs(dx)>=Math.max(dp(12),slop*1.25f))open=dx<0;else if(Math.abs(speed)>dp(250))open=speed<0;}if(open&&getParent() instanceof ViewGroup){ViewGroup siblings=(ViewGroup)getParent();for(int i=0;i<siblings.getChildCount();i++){View v=siblings.getChildAt(i);if(v!=this&&v instanceof SwipeActionsLayout)((SwipeActionsLayout)v).close();}}dragging=false;recycleVelocity();settle(open);getParent().requestDisallowInterceptTouchEvent(false);return true;}return true;}
 private void recycleVelocity(){if(velocity!=null){velocity.recycle();velocity=null;}}
 @Override protected void onDetachedFromWindow(){recycleVelocity();super.onDetachedFromWindow();}
}
