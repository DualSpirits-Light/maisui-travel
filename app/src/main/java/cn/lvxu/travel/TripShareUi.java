package cn.lvxu.travel;

import android.app.*;
import android.content.*;
import android.net.Uri;
import java.io.*;
import java.time.LocalDate;
import java.util.Locale;

/** UI flow for sharing one trip offline and importing a received trip archive. */
public final class TripShareUi {
    public static final int IMPORT_TRIP=352;
    private final MainActivity activity;

    public TripShareUi(MainActivity activity){this.activity=activity;}

    public void share(Trip trip){
        if(trip==null){activity.toast("请先选择一个旅行");return;}
        new AlertDialog.Builder(activity).setTitle("分享旅行").setItems(new String[]{"纯净分享（攻略）","完整分享（回忆）"},(d,w)->chooseChannel(trip,w==1)).setNegativeButton("取消",null).show();
    }
    private void chooseChannel(Trip trip,boolean memories){
        String message=memories?"包含行程、地点备注和图片，以及同行人、费用、清单与打卡回忆。":"包含行程、地点备注、攻略图片和未勾选的清单；不含同行人、车次、实际支出和打卡回忆。";
        new AlertDialog.Builder(activity).setTitle(memories?"完整分享（回忆）":"纯净分享（攻略）").setMessage(message).setNegativeButton("取消",null).setPositiveButton("本地分享",(d,w)->{try{createAndShare(TripLinkCodec.copyForShare(trip,memories,true));}catch(Exception e){activity.toast(e.getMessage());}}).setNeutralButton("口令分享",(d,w)->codeOptions(trip,memories)).show();
    }
    private void codeOptions(Trip trip,boolean memories){android.widget.LinearLayout form=activity.col();activity.pad(form,22);android.widget.CheckBox images=new android.widget.CheckBox(activity);images.setText("包含图片（总文件最多 32 MB）");images.setChecked(true);form.addView(images);form.addView(activity.text("口令有效 3 天。生成后上传到云端，知道口令的人都可以导入。高德来源以链接、快照和修改记录分享。到期后口令失效，云端文件自动清理。",14,MainActivity.MUTED));new AlertDialog.Builder(activity).setTitle("生成旅行口令").setView(form).setNegativeButton("取消",null).setPositiveButton("生成口令",(d,w)->{try{createCode(TripLinkCodec.copyForShare(trip,memories,images.isChecked()));}catch(Exception e){activity.toast(e.getMessage());}}).show();}
    private void createCode(Trip trip){final org.json.JSONObject[] result=new org.json.JSONObject[1];activity.runJob("正在上传旅行与图片",()->{File file=File.createTempFile("code-",".zip",shareDir());try{try(OutputStream out=new FileOutputStream(file)){TripShareArchive.write(activity,trip,out,true);}result[0]=new ShareCodeService().upload(file);return null;}finally{file.delete();}},()->{String code=result[0].optString("code"),expiry=java.time.Instant.ofEpochSecond(result[0].optLong("expiresAt")).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime().toString().replace('T',' ');String message="麦穗旅行口令："+code+"\n"+trip.title+"\n有效至 "+expiry+"\n在麦穗旅序 0.3.1 或更新版本中选择导入旅行 → 口令导入。";android.widget.TextView text=activity.text(message,16,MainActivity.INK);activity.pad(text,22);text.setTextIsSelectable(true);new AlertDialog.Builder(activity).setTitle("口令已生成").setView(text).setNegativeButton("关闭",null).setNeutralButton("复制",(d,w)->{((ClipboardManager)activity.getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("旅行口令",message));activity.toast("已复制口令");}).setPositiveButton("发给好友",(d,w)->activity.startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,message),"分享旅行口令"))).show();});}
    public void enterCode(String initial){android.widget.LinearLayout form=activity.col();activity.pad(form,22);android.widget.EditText input=activity.field(form,"粘贴口令或分享文字",initial,android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);AlertDialog dialog=new AlertDialog.Builder(activity).setTitle("口令导入").setView(form).setNegativeButton("取消",null).setPositiveButton("读取",null).create();dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(v->{String code;try{code=ShareCodeService.extract(input.getText().toString());}catch(Exception e){input.setError(e.getMessage());return;}dialog.dismiss();final File[] file=new File[1];final TripShareArchive.Preview[] preview=new TripShareArchive.Preview[1];activity.runJob("正在读取分享旅行",()->{file[0]=File.createTempFile("received-code-",".zip",shareDir());try{new ShareCodeService().download(code,file[0]);preview[0]=TripShareArchive.inspect(activity,file[0]);return null;}catch(Exception e){file[0].delete();throw e;}},()->showImportPreview(file[0],preview[0]));}));dialog.show();}

    public void pickImport(){new AlertDialog.Builder(activity).setTitle("导入旅行").setItems(new String[]{"本地文件导入","口令导入"},(d,w)->{if(w==0)pickFile();else enterCode("");}).setNegativeButton("取消",null).show();}
    private void pickFile(){
        if(activity.loadFailed){activity.toast("请先通过备份恢复本地数据，再导入旅行");return;}
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/zip");
        activity.startActivityForResult(intent,IMPORT_TRIP);
    }

    public boolean onActivityResult(int requestCode,int resultCode,Intent data){
        if(requestCode!=IMPORT_TRIP)return false;if(resultCode!=Activity.RESULT_OK||data==null||data.getData()==null)return true;
        receive(data.getData());return true;
    }

    /** Opens the same verified preview flow for a ZIP received from ACTION_SEND/ACTION_VIEW. */
    public void receive(Uri uri){
        if(activity.loadFailed){activity.toast("请先通过备份恢复本地数据，再导入旅行");return;}if(uri==null){activity.toast("没有可读取的分享文件");return;}
        final File[] cached=new File[1];final TripShareArchive.Preview[] preview=new TripShareArchive.Preview[1];
        activity.runJob("正在检查分享文件",()->{try{cached[0]=copyIncoming(uri);preview[0]=TripShareArchive.inspect(activity,cached[0]);return null;}catch(Exception e){if(cached[0]!=null)cached[0].delete();throw e;}},()->showImportPreview(cached[0],preview[0]));
    }

    private void createAndShare(Trip trip){
        final File[] result=new File[1];activity.runJob("正在生成分享文件",()->{File dir=shareDir();cleanOld(dir);String base=safeName(trip.title);File file=new File(dir,base+"-"+LocalDate.now()+"-"+System.currentTimeMillis()+".zip");try{try(OutputStream out=new FileOutputStream(file)){TripShareArchive.write(activity,trip,out);}result[0]=file;return null;}catch(Exception e){file.delete();throw e;}},()->{
            Uri uri=AppFileProvider.uriFor(activity,result[0]);Intent send=new Intent(Intent.ACTION_SEND).setType("application/zip").putExtra(Intent.EXTRA_STREAM,uri).putExtra(Intent.EXTRA_SUBJECT,trip.title+" · 麦穗旅序").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);send.setClipData(ClipData.newRawUri("麦穗旅序旅行分享",uri));activity.startActivity(Intent.createChooser(send,"发送旅行分享文件"));
        });
    }

    private void showImportPreview(File archive,TripShareArchive.Preview p){
        String message=p.city+" · "+p.start+" · "+p.days+" 天\n\n"+p.stops+" 个地点 · "+p.expenses+" 笔费用\n"+p.checklistItems+" 个清单项 · "+p.checkins+" 条打卡 · "+p.photos+" 张照片\n\n导入后会创建一份独立旅行，不会覆盖已有旅行。口令中的高德链接会重新读取并合并修改；无法读取时保留分享快照。";
        AlertDialog dialog=new AlertDialog.Builder(activity).setTitle("导入“"+p.title+"”？").setMessage(message).setNegativeButton("取消",null).setPositiveButton("导入",null).create();
        dialog.setOnShowListener(x->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{dialog.setOnDismissListener(null);dialog.dismiss();importArchive(archive);}));
        dialog.setOnDismissListener(x->archive.delete());dialog.show();
    }

    private void importArchive(File archive){
        if(activity.loadFailed){archive.delete();activity.toast("请先通过备份恢复本地数据，再导入旅行");return;}
        final Trip[] imported=new Trip[1];activity.runJob("正在导入旅行",()->{try{imported[0]=TripShareArchive.importTrip(activity,archive,activity.store,activity.trips);return null;}finally{archive.delete();}},()->{activity.active=imported[0];activity.day=0;activity.page=0;activity.render();activity.toast("已导入“"+imported[0].title+"”");if(imported[0].importedLinkFallbacks>0)new AlertDialog.Builder(activity).setTitle("已使用分享快照").setMessage(imported[0].importedLinkFallbacks+" 个高德来源暂时无法更新，已保留分享时的数据与修改记录，行程未丢失。").setPositiveButton("知道了",null).show();});
    }

    private File copyIncoming(Uri uri)throws Exception {
        File dir=shareDir(),file=File.createTempFile("received-",".zip",dir);long total=0;byte[] buffer=new byte[8192];
        try(InputStream in=activity.getContentResolver().openInputStream(uri);OutputStream out=new FileOutputStream(file)){if(in==null)throw new IOException("无法读取所选文件");for(int n;(n=in.read(buffer))!=-1;){total+=n;if(total>TripShareArchive.MAX_ARCHIVE)throw new IOException("分享文件过大");out.write(buffer,0,n);}}
        catch(Exception e){file.delete();throw e;}return file;
    }
    private File shareDir()throws IOException {File dir=new File(activity.getCacheDir(),"shares");if(!dir.exists()&&!dir.mkdirs())throw new IOException("无法准备分享目录");return dir;}
    private static String safeName(String title){String clean=title.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]","-").trim();if(clean.isEmpty())clean="旅行";return clean.substring(0,Math.min(clean.length(),40));}
    private static void cleanOld(File dir){long before=System.currentTimeMillis()-3L*24*60*60*1000;File[] files=dir.listFiles();if(files!=null)for(File file:files)if(file.isFile()&&file.lastModified()<before)file.delete();}
}
