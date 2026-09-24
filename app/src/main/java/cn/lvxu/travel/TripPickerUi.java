package cn.lvxu.travel;

import android.app.Dialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.*;
import android.widget.*;

/** Select a trip without modifying its archive state or saved content. */
final class TripPickerUi {
    final MainActivity a;
    final Dialog dialog;
    final LinearLayout list;
    final TextView footer;
    final LinearLayout surface;
    final ScrollView scroll;
    final TextView summary;
    boolean archived;

    TripPickerUi(MainActivity activity) {
        a=activity;
        dialog=new Dialog(a);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        surface=new LinearLayout(a) {
            @Override protected void onMeasure(int width,int height) {
                int max=(int)(a.getResources().getDisplayMetrics().heightPixels*.80f);
                super.onMeasure(width,MeasureSpec.makeMeasureSpec(Math.min(max,MeasureSpec.getSize(height)),MeasureSpec.AT_MOST));
            }
        };
        surface.setOrientation(LinearLayout.VERTICAL);
        surface.setPadding(a.dp(18),a.dp(14),a.dp(18),a.dp(14));
        surface.setBackground(a.shape(MainActivity.BG,26));
        surface.setClipToOutline(true);
        LinearLayout header=a.row();
        header.addView(a.bold("切换旅行",23,MainActivity.INK),new LinearLayout.LayoutParams(0,-2,1));
        TextView close=a.text("×",28,MainActivity.MUTED);
        close.setGravity(Gravity.CENTER);
        close.setContentDescription("关闭旅行选择");
        close.setFocusable(true);
        close.setOnClickListener(v->dialog.dismiss());
        header.addView(close,new LinearLayout.LayoutParams(a.dp(48),a.dp(48)));
        surface.addView(header);
        summary=a.text("",13,MainActivity.MUTED);
        summary.setPadding(0,0,0,a.dp(14));
        surface.addView(summary);
        scroll=new ScrollView(a);
        scroll.setClipToPadding(false);
        list=a.col();
        scroll.addView(list);
        surface.addView(scroll,new LinearLayout.LayoutParams(-1,-2,1));
        a.space(surface,10);
        footer=a.action("",false,()->{archived=!archived;renderList();scroll.scrollTo(0,0);});
        surface.addView(footer,new LinearLayout.LayoutParams(-1,-2));
        dialog.setContentView(surface);
        dialog.setCanceledOnTouchOutside(true);
        renderList();
    }

    private void renderList() {
        list.removeAllViews();
        int count=0,other=0;
        for(Trip t:a.trips)if(t.archived==archived){addCard(t);count++;}else other++;
        summary.setText((archived?"已归档":"正在规划")+" · "+count+" 段旅行");
        if(count==0){
            LinearLayout empty=a.col();
            empty.setPadding(a.dp(16),a.dp(28),a.dp(16),a.dp(28));
            TextView label=a.bold(archived?"还没有已归档的旅行":"还没有正在规划的旅行",17,MainActivity.INK);
            label.setGravity(Gravity.CENTER);empty.addView(label);a.space(empty,8);
            TextView help=a.text(archived?"归档后的旅行会出现在这里。":"可以查看已归档旅行，或返回旅行页新建。",13,MainActivity.MUTED);
            help.setGravity(Gravity.CENTER);empty.addView(help);list.addView(empty);
        }
        footer.setText((archived?"正在规划的旅行":"已归档的旅行")+"（"+other+"）  ›");
        footer.setContentDescription(archived?"正在规划的旅行":"已归档的旅行");
        ThemeViews.apply(surface,MainActivity.BG);
    }

    private void addCard(Trip t) {
        boolean selected=a.active!=null&&a.active.id.equals(t.id);
        LinearLayout card=a.col();
        card.setPadding(a.dp(17),a.dp(16),a.dp(17),a.dp(16));
        GradientDrawable background=a.shape(selected?MainActivity.PALE:MainActivity.SURFACE,20);
        if(selected)background.setStroke(a.dp(2),MainActivity.GREEN);
        card.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22000000),background,null));
        card.addView(a.text(t.city+" / "+t.days+" 天",12,MainActivity.MUTED));
        a.space(card,8);
        card.addView(a.bold((t.favorite?"★ ":"")+t.title,19,MainActivity.INK));
        a.space(card,8);
        card.addView(a.text(t.start.replace('-','/')+" 出发 · "+t.stops.size()+" 个地点",13,MainActivity.MUTED));
        a.space(card,10);
        card.addView(a.bold(selected?"✓ 当前旅行":"切换到这段旅行  →",13,MainActivity.GREEN));
        card.setContentDescription("选择旅行："+t.title);
        card.setFocusable(true);
        card.setSelected(selected);
        card.setOnClickListener(v->{
            if(!a.trips.contains(t)){renderList();return;}
            a.active=t;a.day=0;dialog.dismiss();a.render();
        });
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);
        params.bottomMargin=a.dp(10);
        list.addView(card,params);
    }

    Dialog show() {
        if(a.isFinishing()||a.isDestroyed())return dialog;
        Window w=dialog.getWindow();
        if(w!=null){
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setGravity(Gravity.TOP|Gravity.CENTER_HORIZONTAL);
            w.setLayout(Math.min(a.getResources().getDisplayMetrics().widthPixels-a.dp(24),a.dp(560)),-2);
            WindowManager.LayoutParams params=w.getAttributes();params.y=a.dp(12);w.setAttributes(params);
            w.setWindowAnimations(R.style.SelectionWindowAnimation);
            w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        dialog.show();
        return dialog;
    }
}
