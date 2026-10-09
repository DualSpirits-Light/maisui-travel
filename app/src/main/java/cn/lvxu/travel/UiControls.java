package cn.lvxu.travel;

import android.graphics.Rect;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Shared touch targets, accessible button roles and width-aware action rows. */
final class UiControls {
    static void button(View view){view.setAccessibilityDelegate(new View.AccessibilityDelegate(){
        @Override public void onInitializeAccessibilityNodeInfo(View host,AccessibilityNodeInfo info){
            super.onInitializeAccessibilityNodeInfo(host,info);info.setClassName("android.widget.Button");
        }
    });}
    static void error(EditText field,String message){
        field.setError(message);field.requestFocus();
        field.post(()->{if(field.isAttachedToWindow())field.requestRectangleOnScreen(new Rect(0,0,field.getWidth(),field.getHeight()),false);});
    }
    static final class ActionPair extends LinearLayout {
        private final MainActivity a;private final View left,right;
        ActionPair(MainActivity a,View left,View right){super(a);this.a=a;this.left=left;this.right=right;addView(left);addView(right);}
        private int needed(View view){if(!(view instanceof TextView))return a.dp(112);TextView text=(TextView)view;
            return (int)Math.ceil(text.getPaint().measureText(text.getText().toString()))+text.getPaddingLeft()+text.getPaddingRight();}
        @Override protected void onMeasure(int widthSpec,int heightSpec){
            int available=MeasureSpec.getSize(widthSpec)-getPaddingLeft()-getPaddingRight(),gap=a.dp(10),half=Math.max(0,(available-gap)/2);
            boolean stack=needed(left)>half||needed(right)>half;setOrientation(stack?VERTICAL:HORIZONTAL);
            LayoutParams lp=(LayoutParams)left.getLayoutParams(),rp=(LayoutParams)right.getLayoutParams();
            lp.width=rp.width=stack?LayoutParams.MATCH_PARENT:0;lp.height=rp.height=LayoutParams.WRAP_CONTENT;
            lp.weight=rp.weight=stack?0:1;rp.topMargin=stack?gap:0;rp.leftMargin=stack?0:gap;
            super.onMeasure(widthSpec,heightSpec);
            if(!stack){int height=Math.max(left.getMeasuredHeight(),right.getMeasuredHeight());lp.height=height;rp.height=height;super.onMeasure(widthSpec,heightSpec);}
        }
    }
}
