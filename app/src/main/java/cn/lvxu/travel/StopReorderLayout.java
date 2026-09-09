package cn.lvxu.travel;

import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.Rect;
import android.view.*;
import android.view.animation.DecelerateInterpolator;
import android.widget.*;
import java.util.*;

/** Local-day drag reordering with animated insertion and edge scrolling. */
final class StopReorderLayout extends LinearLayout {
    private final MainActivity a;private final Trip trip;private final int day;
    private final IdentityHashMap<View,Trip.Stop> stops=new IdentityHashMap<>();
    private final ArrayList<View> original=new ArrayList<>();
    private View dragged;private Object dragToken;private boolean dropped,moving;private float screenY;
    StopReorderLayout(MainActivity a,Trip trip,int day){super(a);this.a=a;this.trip=trip;this.day=day;setOrientation(VERTICAL);setTag("stop-list");setClipChildren(false);setOnDragListener(this::dragEvent);}

    void bind(View card,Trip.Stop stop){stops.put(card,stop);installLongPress(card,card);}
    private void installLongPress(View view,View card){view.setOnLongClickListener(v->begin(card));if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)installLongPress(group.getChildAt(i),card);}}
    private boolean begin(View card){
        if(dragged!=null||getChildCount()<2)return true;
        original.clear();for(int i=0;i<getChildCount();i++)original.add(getChildAt(i));dragged=card;dragToken=new Object();dropped=false;
        View.DragShadowBuilder shadow=new View.DragShadowBuilder(card){float scale;
            @Override public void onProvideShadowMetrics(Point size,Point touch){scale=Math.min(1f,a.dp(210)/(float)Math.max(1,card.getHeight()));size.set(Math.max(1,Math.round(card.getWidth()*scale)),Math.max(1,Math.round(card.getHeight()*scale)));touch.set(size.x/2,size.y/2);}
            @Override public void onDrawShadow(Canvas canvas){canvas.scale(scale,scale);card.draw(canvas);}
        };
        if(!card.startDragAndDrop(null,shadow,dragToken,0)){dragged=null;dragToken=null;return true;}
        card.setAlpha(.2f);card.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);return true;
    }
    private boolean dragEvent(View view,DragEvent event){
        if(dragToken==null||event.getLocalState()!=dragToken)return false;
                switch(event.getAction()){
            case DragEvent.ACTION_DRAG_STARTED:return true;
            case DragEvent.ACTION_DRAG_LOCATION:
                int[] location=new int[2];getLocationOnScreen(location);screenY=location[1]+event.getY();moveForY(event.getY());removeCallbacks(edgeScroll);postOnAnimation(edgeScroll);return true;
            case DragEvent.ACTION_DROP:moveForY(event.getY());dropped=true;return true;
            case DragEvent.ACTION_DRAG_ENDED:finish(dropped&&event.getResult());return true;
            default:return true;
        }
    }
    private final Runnable edgeScroll=new Runnable(){public void run(){if(dragged==null)return;ScrollView scroll=scroll();if(scroll==null)return;Rect visible=new Rect();scroll.getGlobalVisibleRect(visible);int edge=a.dp(72),dy=screenY<visible.top+edge?-a.dp(9):screenY>visible.bottom-edge?a.dp(9):0;if(dy!=0){scroll.scrollBy(0,dy);int[] location=new int[2];getLocationOnScreen(location);moveForY(screenY-location[1]);}postOnAnimation(this);}};
    private ScrollView scroll(){for(ViewParent p=getParent();p!=null;p=p.getParent())if(p instanceof ScrollView)return (ScrollView)p;return null;}
    private void moveForY(float y){
        if(dragged==null||moving)return;int target=0;for(int i=0;i<getChildCount();i++){View child=getChildAt(i);if(child!=dragged&&y>child.getTop()+child.getHeight()/2f)target++;}
        int current=indexOfChild(dragged);if(target==current)return;IdentityHashMap<View,Float> old=positions();removeView(dragged);addView(dragged,Math.min(target,getChildCount()));animatePositions(old);
    }
    private IdentityHashMap<View,Float> positions(){IdentityHashMap<View,Float> old=new IdentityHashMap<>();for(int i=0;i<getChildCount();i++){View child=getChildAt(i);old.put(child,child.getY());child.animate().cancel();}return old;}
    private void animatePositions(IdentityHashMap<View,Float> old){
        moving=true;getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener(){public boolean onPreDraw(){getViewTreeObserver().removeOnPreDrawListener(this);moving=false;for(int i=0;i<getChildCount();i++){View child=getChildAt(i);Float before=old.get(child);if(child!=dragged&&before!=null){child.setTranslationY(before-child.getTop());child.animate().translationY(0).setDuration(190).setInterpolator(new DecelerateInterpolator(1.6f)).start();}}return true;}});
    }
    private void finish(boolean accept){
        removeCallbacks(edgeScroll);View held=dragged;dragged=null;dragToken=null;moving=false;if(held==null)return;held.setAlpha(1f);
        ArrayList<String> ids=new ArrayList<>();boolean different=false;for(int i=0;i<getChildCount();i++){View child=getChildAt(i);child.animate().cancel();child.setTranslationY(0);ids.add(stops.get(child).id);if(i>=original.size()||original.get(i)!=child)different=true;}
        if(accept&&different&&a.trips.contains(trip)){ScrollView oldScroll=scroll();int offset=oldScroll==null?0:oldScroll.getScrollY();try{trip.reorderDay(day,ids);a.changed();if(a.body.getParent() instanceof ScrollView){ScrollView current=(ScrollView)a.body.getParent();current.post(()->current.scrollTo(0,offset));}}catch(IllegalArgumentException e){a.toast(e.getMessage());a.render();}}
        else if(!accept&&different){IdentityHashMap<View,Float> old=positions();removeAllViews();for(View child:original)addView(child);animatePositions(old);}
        original.clear();
    }
    @Override protected void onDetachedFromWindow(){removeCallbacks(edgeScroll);super.onDetachedFromWindow();}
}
