package cn.lvxu.travel;

import android.app.*;
import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;

/** A self-contained full-screen About and support page. MainActivity only needs to call show(). */
public final class AboutUi {
    private static final String PREFS="about-support-v1", AUTO="auto-update";
    private final MainActivity a; private final Runnable checkUpdate; private final DonationsService donations;
    private Dialog current;
    public AboutUi(MainActivity activity,Runnable checkUpdate){a=activity;this.checkUpdate=checkUpdate;donations=new DonationsService(activity);}
    public static boolean autoUpdates(Context context){return context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getBoolean(AUTO,true);}
    public void show(){
        if(current!=null&&current.isShowing())return;
        Dialog dialog=new Dialog(a); dialog.requestWindowFeature(Window.FEATURE_NO_TITLE); current=dialog;
        LinearLayout page=a.col(); page.setBackgroundColor(MainActivity.BG);
        FrameLayout header=new FrameLayout(a); TextView title=a.bold("关于麦穗旅序",21,MainActivity.INK);title.setGravity(Gravity.CENTER);header.addView(title,new FrameLayout.LayoutParams(-1,a.dp(64)));
        ImageButton back=new ImageButton(a);back.setImageResource(R.drawable.ic_back);back.setColorFilter(MainActivity.INK);back.setBackgroundColor(Color.TRANSPARENT);back.setContentDescription("返回");back.setOnClickListener(v->dialog.dismiss());header.addView(back,new FrameLayout.LayoutParams(a.dp(56),a.dp(56),Gravity.START|Gravity.CENTER_VERTICAL));page.addView(header);
        ScrollView scroll=new ScrollView(a);scroll.setFillViewport(true);LinearLayout body=a.col();body.setPadding(a.dp(22),a.dp(10),a.dp(22),a.dp(32));scroll.addView(body);page.addView(scroll,new LinearLayout.LayoutParams(-1,0,1)); build(body,dialog);
        dialog.setContentView(page);dialog.setOnDismissListener(v->{if(current==dialog)current=null;});dialog.show(); Window w=dialog.getWindow();if(w!=null){w.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);w.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);w.setLayout(-1,-1);w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));w.setStatusBarColor(MainActivity.BG);w.setNavigationBarColor(MainActivity.BG);w.getDecorView().setSystemUiVisibility(MainActivity.BG==0xff14231E?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);}
    }
    private void build(LinearLayout box,Dialog dialog){
        LinearLayout identity=a.col();identity.setGravity(Gravity.CENTER_HORIZONTAL);ImageView icon=new ImageView(a);icon.setImageResource(R.drawable.ic_launcher);icon.setBackground(a.shape(MainActivity.SURFACE,24));icon.setPadding(a.dp(8),a.dp(8),a.dp(8),a.dp(8));identity.addView(icon,new LinearLayout.LayoutParams(a.dp(92),a.dp(92)));a.space(identity,12);TextView appName=a.bold("麦穗旅序",25,MainActivity.INK);appName.setGravity(Gravity.CENTER);identity.addView(appName);String version="";try{version=a.getPackageManager().getPackageInfo(a.getPackageName(),0).versionName;}catch(Exception ignored){}TextView appVersion=a.text("版本 "+version,14,MainActivity.MUTED);appVersion.setGravity(Gravity.CENTER);identity.addView(appVersion);box.addView(identity);
        section(box,"应用");LinearLayout app=a.card(box);app.addView(row("检查更新",()->{dialog.dismiss();if(checkUpdate!=null)checkUpdate.run();}));app.addView(row("更新记录",()->ReleaseNotes.showHistory(a)));Switch auto=new Switch(a);auto.setText("启动时自动检查更新");auto.setTextColor(MainActivity.INK);auto.setChecked(autoUpdates(a));auto.setContentDescription("启动时自动检查更新");auto.setOnCheckedChangeListener((v,on)->a.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putBoolean(AUTO,on).apply());app.addView(auto);
        section(box,"支持作者");LinearLayout support=a.card(box);support.addView(row("支付宝赞赏码",()->poster("支付宝赞赏码",R.drawable.support_alipay)));support.addView(row("微信赞赏码",()->poster("微信赞赏码",R.drawable.support_wechat)));support.addView(row("爱发电",()->{poster("爱发电",R.drawable.support_afdian);open("https://afdian.com/a/dutmaisuisui");}));support.addView(row("GitHub 项目主页",()->open("https://github.com/DualSpirits-Light/maisui-travel")));
        section(box,"捐赠记录");LinearLayout donor=a.card(box);renderRecords(donor);a.space(donor,8);donor.addView(a.action("刷新捐赠记录",false,()->a.runJob("正在刷新捐赠记录",()->{donations.refresh();return "";},()->{a.toast("捐赠记录已更新");dialog.dismiss();show();})));
        section(box,"帮助");LinearLayout help=a.card(box);help.addView(row("重新查看使用教程",()->{dialog.dismiss();a.showTutorial();}));help.addView(row("设置可信更新镜像",()->{LinearLayout form=a.col();EditText url=a.field(form,"GitHub API 或可信 HTTPS 镜像",a.prefs.updateMirror(),android.text.InputType.TYPE_TEXT_VARIATION_URI);a.dialog("更新来源",form,()->{String value=a.required(url,1000);ApiHttp.url(value);a.prefs.setUpdateMirror(value);a.toast("更新来源已保存");},null);}));String mirror=a.prefs.updateMirror();help.addView(a.text("更新来源：官方 GitHub\n"+mirror,12,MainActivity.MUTED));
        section(box,"联系我们");LinearLayout contact=a.card(box);contact.addView(row("发送建议或反馈",this::mail));contact.addView(a.text("你的意见会帮助麦穗旅序继续变得更好。",13,MainActivity.MUTED));
    }
    private View row(String label,Runnable action){TextView v=a.action(label+"  ›",false,action);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    private void section(LinearLayout box,String label){a.space(box,22);box.addView(a.bold(label,18,MainActivity.INK));a.space(box,10);}
    private void renderRecords(LinearLayout box){List<DonationsService.Record> records=donations.cached();if(records.isEmpty()){box.addView(a.text("暂时还没有公开的捐赠记录，感谢每一份支持。",14,MainActivity.MUTED));return;}for(DonationsService.Record r:records){TextView line=a.text(r.name+" · "+r.date+(r.message.isEmpty()?"":"\n"+r.message),14,MainActivity.INK);line.setPadding(0,a.dp(7),0,a.dp(7));box.addView(line);}}
    private void poster(String name,int resource){Dialog dialog=new Dialog(a);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);LinearLayout body=a.col();body.setPadding(a.dp(18),a.dp(18),a.dp(18),a.dp(18));TextView title=a.bold(name,20,MainActivity.INK);title.setGravity(Gravity.CENTER);body.addView(title,new LinearLayout.LayoutParams(-1,-2));a.space(body,12);ImageView image=new ImageView(a);image.setImageResource(resource);image.setScaleType(ImageView.ScaleType.FIT_CENTER);image.setAdjustViewBounds(true);body.addView(image,new LinearLayout.LayoutParams(-1,a.dp(430)));a.space(body,12);a.pair(body,a.action("保存图片",true,()->savePoster(resource,name)),a.action("分享图片",false,()->sharePoster(resource,name)));dialog.setContentView(body);dialog.show();if(dialog.getWindow()!=null){dialog.getWindow().setBackgroundDrawable(a.shape(MainActivity.SURFACE,24));dialog.getWindow().setLayout(-1,-2);}}
    private void savePoster(int resource,String name){if(Build.VERSION.SDK_INT<29){sharePoster(resource,name);a.toast("已打开分享，可选择保存到设备");return;}Uri uri=null;try{ContentValues values=new ContentValues();values.put(MediaStore.Images.Media.DISPLAY_NAME,"麦穗旅序-"+name+extension(resource));values.put(MediaStore.Images.Media.MIME_TYPE,mime(resource));values.put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/麦穗旅序");values.put(MediaStore.Images.Media.IS_PENDING,1);uri=a.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values);if(uri==null)throw new IOException();try(OutputStream out=a.getContentResolver().openOutputStream(uri);InputStream in=a.getResources().openRawResource(resource)){copy(in,out);}values.clear();values.put(MediaStore.Images.Media.IS_PENDING,0);a.getContentResolver().update(uri,values,null,null);a.toast("图片已保存到相册");}catch(Exception e){if(uri!=null)try{a.getContentResolver().delete(uri,null,null);}catch(Exception ignored){}a.toast("无法保存图片，请使用分享功能");}}
    private void sharePoster(int resource,String title){try{File folder=new File(a.getCacheDir(),"shares");if(!folder.exists()&&!folder.mkdirs())throw new IOException();File file=new File(folder,"support-"+resource+extension(resource));try(OutputStream out=new FileOutputStream(file);InputStream in=a.getResources().openRawResource(resource)){copy(in,out);}Uri uri=AppFileProvider.uriFor(a,file);Intent send=new Intent(Intent.ACTION_SEND).setType("image/*").putExtra(Intent.EXTRA_STREAM,uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);send.setClipData(ClipData.newRawUri(title,uri));a.startActivity(Intent.createChooser(send,"分享"+title));}catch(Exception e){a.toast("暂时无法分享图片");}}
    private static String extension(int resource){return resource==R.drawable.support_alipay?".png":".jpg";}
    private static String mime(int resource){return resource==R.drawable.support_alipay?"image/png":"image/jpeg";}
    private static void copy(InputStream in,OutputStream out)throws IOException{byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}
    private void mail(){Intent email=new Intent(Intent.ACTION_SENDTO,Uri.parse("mailto:dutmaisuisui@vip.qq.com?subject="+Uri.encode("关于麦穗旅序的见解")));try{a.startActivity(email);}catch(Exception e){Intent share=new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_EMAIL,new String[]{"dutmaisuisui@vip.qq.com"}).putExtra(Intent.EXTRA_SUBJECT,"关于麦穗旅序的见解");try{a.startActivity(Intent.createChooser(share,"联系开发者"));}catch(Exception ignored){a.toast("未找到可用的邮件应用");}}}
    private void open(String url){try{a.startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}catch(Exception e){a.toast("未找到可用的浏览器");}}
}
