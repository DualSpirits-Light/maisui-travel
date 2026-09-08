package cn.lvxu.travel;
import android.content.Context;
import android.view.*;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;

/** Horizontal ownership begins after touch slop, leaving vertical scrolling and taps intact. */
final class SwipeActionsLayout extends FrameLayout {
    private float downX,downY,start;private boolean dragging;private final int slop;
    SwipeActionsLayout(Context c){super(c);slop=ViewConfiguration.get(c).getScaledTouchSlop();setTag("trip-card");setClipChildren(true);}
    private View front(){return getChildAt(1);}
    private float reveal(){return Math.min(getWidth()*.90f,320*getResources().getDisplayMetrics().density);}
    @Override protected void onSizeChanged(int w,int h,int ow,int oh){super.onSizeChanged(w,h,ow,oh);if(getChildCount()>0){View actions=getChildAt(0);LayoutParams p=(LayoutParams)actions.getLayoutParams();p.width=(int)reveal();actions.setLayoutParams(p);}}
    @Override public boolean onInterceptTouchEvent(MotionEvent e){if(e.getActionMasked()==MotionEvent.ACTION_DOWN){downX=e.getX();downY=e.getY();start=front().getTranslationX();dragging=false;front().animate().cancel();}else if(e.getActionMasked()==MotionEvent.ACTION_MOVE){float dx=e.getX()-downX,dy=e.getY()-downY;if(Math.abs(dx)>slop&&Math.abs(dx)>Math.abs(dy)*1.3f){getChildAt(0).setVisibility(VISIBLE);dragging=true;getParent().requestDisallowInterceptTouchEvent(true);return true;}}return false;}
    @Override public boolean onTouchEvent(MotionEvent e){if(!dragging)return super.onTouchEvent(e);if(e.getActionMasked()==MotionEvent.ACTION_MOVE){float x=Math.max(-reveal(),Math.min(0,start+e.getX()-downX));front().setTranslationX(x);return true;}if(e.getActionMasked()==MotionEvent.ACTION_UP||e.getActionMasked()==MotionEvent.ACTION_CANCEL){boolean open=e.getActionMasked()!=MotionEvent.ACTION_CANCEL&&front().getTranslationX()<-reveal()*.35f;front().animate().translationX(open?-reveal():0).setDuration(240).setInterpolator(new DecelerateInterpolator(1.7f)).withEndAction(()->{if(!open)getChildAt(0).setVisibility(INVISIBLE);}).start();dragging=false;getParent().requestDisallowInterceptTouchEvent(false);return true;}return true;}
}
