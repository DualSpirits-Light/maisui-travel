package cn.lvxu.travel;

import android.app.*;
import android.graphics.*;
import android.graphics.drawable.ColorDrawable;
import android.view.*;
import android.widget.*;

/** Full-image viewing is separate from the explicit, non-destructive crop action. */
final class PhotoPreviewUi {
    private PhotoPreviewUi(){}

    static Dialog show(MainActivity a,String path){
        Dialog dialog=new Dialog(a,android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        FrameLayout root=new FrameLayout(a);root.setBackgroundColor(Color.BLACK);
        ImageView image=new ImageView(a);image.setScaleType(ImageView.ScaleType.FIT_CENTER);image.setAlpha(1f);
        image.setContentDescription("完整照片");root.addView(image,new FrameLayout.LayoutParams(-1,-1));
        ImageButton close=new ImageButton(a);close.setImageResource(R.drawable.ic_close);close.setColorFilter(Color.WHITE);
        close.setBackground(a.shape(0xaa26312C,24));close.setPadding(a.dp(12),a.dp(12),a.dp(12),a.dp(12));close.setContentDescription("关闭图片");
        FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(a.dp(48),a.dp(48),Gravity.TOP|Gravity.END);p.setMargins(a.dp(16),a.dp(16),a.dp(16),0);root.addView(close,p);close.setOnClickListener(v->dialog.dismiss());
        dialog.setContentView(root);dialog.show();Window w=dialog.getWindow();if(w!=null){w.setBackgroundDrawable(new ColorDrawable(Color.BLACK));w.setLayout(-1,-1);w.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);}
        PlaceMediaUi.load(a,image,path,2400,()->!dialog.isShowing());return dialog;
    }

    static void crop(MainActivity a,String path,MainActivity.ImageCallback saved){
        final Bitmap[] source={null};a.runJob("正在读取照片",()->{source[0]=a.media.files.decode(path,2400);return null;},()->{
            if(source[0]==null){a.toast("无法读取照片");return;}
            MediaController.CropView crop=new MediaController.CropView(a,source[0]);
            FrameLayout box=new FrameLayout(a){@Override protected void onMeasure(int width,int height){int side=MeasureSpec.getSize(width);super.onMeasure(width,MeasureSpec.makeMeasureSpec(side,MeasureSpec.EXACTLY));}};
            box.addView(crop,new FrameLayout.LayoutParams(-1,-1));
            AlertDialog dialog=new AlertDialog.Builder(a).setTitle("裁剪照片").setMessage("拖动调整位置，双指缩放。保存后替换这张照片。")
                .setView(box).setNegativeButton("放弃",null).setPositiveButton("保存",null).create();
            dialog.setOnDismissListener(v->{if(!source[0].isRecycled())source[0].recycle();});
            dialog.setOnShowListener(v->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button->{
                if(crop.getWidth()==0||crop.getHeight()==0)return;
                Bitmap output=crop.result(1600);dialog.dismiss();final String[] result={null};
                a.runJob("正在保存裁剪",()->{try{result[0]=a.media.files.save(output,"photos",92);return result[0];}finally{output.recycle();}},()->saved.selected(result[0]));
            }));dialog.show();
        });
    }
}
