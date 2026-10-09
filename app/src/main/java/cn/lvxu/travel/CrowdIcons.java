package cn.lvxu.travel;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/** Vector people remain sharp at any density; unknown is one neutral person and a question mark. */
final class CrowdIcons extends View {
    private final Paint paint=new Paint(3);private int level;
    CrowdIcons(Context c,int level){super(c);this.level=level;setContentDescription(level==0?"热力未知":level<=2?"热力参考：较低":level<=4?"热力参考：中等":"热力参考：较高");}
    @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);float d=getResources().getDisplayMetrics().density,x=6*d,y=getHeight()/2f;
        paint.setColor(MainActivity.readable(level==0?MainActivity.MUTED:level<=2?0xff228649:level<=4?0xffBD8400:0xffC74444,MainActivity.SURFACE));
        int count=level==0?1:level<=2?2:level<=4?3:5;
        for(int i=0;i<count;i++){float px=x+i*16*d;canvas.drawCircle(px+5*d,y-7*d,3*d,paint);canvas.drawRoundRect(px+2*d,y-3*d,px+8*d,y+5*d,2*d,2*d,paint);paint.setStrokeWidth(2*d);canvas.drawLine(px+3*d,y+4*d,px+3*d,y+11*d,paint);canvas.drawLine(px+7*d,y+4*d,px+7*d,y+11*d,paint);}
        if(level==0){paint.setTextSize(20*d);paint.setTypeface(Typeface.DEFAULT_BOLD);canvas.drawText("?",x+18*d,y+7*d,paint);}
    }
}
