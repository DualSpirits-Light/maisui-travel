package cn.lvxu.travel;
import android.content.Context;
import android.view.MotionEvent;
import android.widget.FrameLayout;
/** Own the whole gesture before native map children consume touch events. */
final class MapGestureFrame extends FrameLayout {
 private Runnable gestureStart;
 MapGestureFrame(Context context){super(context);setTag("map-gesture");}
 void onGestureStart(Runnable listener){gestureStart=listener;}
 @Override public boolean dispatchTouchEvent(MotionEvent e){int action=e.getActionMasked();if(action==MotionEvent.ACTION_DOWN&&gestureStart!=null)gestureStart.run();if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(true);try{return super.dispatchTouchEvent(e);}finally{if((action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL)&&getParent()!=null)getParent().requestDisallowInterceptTouchEvent(false);}}
}
