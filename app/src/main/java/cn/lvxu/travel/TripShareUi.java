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
        int notes=0,photos=0;for(Trip.Stop s:trip.stops){if(!s.note.trim().isEmpty())notes++;if(!s.previewPhoto.isEmpty())photos++;photos+=s.notePhotos.size();}for(Trip.Item i:trip.items)if(!i.photo.isEmpty())photos++;for(Trip.Checkin c:trip.checkins){if(!c.photo.isEmpty())photos++;if(!c.card.isEmpty())photos++;photos+=c.groupPhotos.size()+c.sceneryPhotos.size();}
        StringBuilder included=new StringBuilder("将生成可由麦穗旅序导入的离线文件，内容包括：\n\n")
            .append("• 旅行名称、日期、出发地、交通与住宿信息\n")
            .append("• ").append(trip.stops.size()).append(" 个地点与 ").append(notes).append(" 条地点备注\n")
            .append("• 同行人、").append(trip.expenses.size()).append(" 笔费用、").append(trip.items.size()).append(" 个清单项、").append(trip.checkins.size()).append(" 条打卡\n")
            .append("• ").append(photos).append(" 张引用照片\n\n")
            .append("不会包含账号、激活信息或应用设置。生成后由你选择发送方式。");
        new AlertDialog.Builder(activity).setTitle("分享“"+trip.title+"”？").setMessage(included.toString()).setNegativeButton("取消",null).setPositiveButton("生成分享文件",(d,w)->createAndShare(trip)).show();
    }

    public void pickImport(){
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
        String message=p.city+" · "+p.start+" · "+p.days+" 天\n\n"+p.stops+" 个地点 · "+p.expenses+" 笔费用\n"+p.checklistItems+" 个清单项 · "+p.checkins+" 条打卡 · "+p.photos+" 张照片\n\n导入后会创建一份独立旅行，不会覆盖已有旅行。";
        AlertDialog dialog=new AlertDialog.Builder(activity).setTitle("导入“"+p.title+"”？").setMessage(message).setNegativeButton("取消",null).setPositiveButton("导入",null).create();
        dialog.setOnShowListener(x->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{dialog.setOnDismissListener(null);dialog.dismiss();importArchive(archive);}));
        dialog.setOnDismissListener(x->archive.delete());dialog.show();
    }

    private void importArchive(File archive){
        if(activity.loadFailed){archive.delete();activity.toast("请先通过备份恢复本地数据，再导入旅行");return;}
        final Trip[] imported=new Trip[1];activity.runJob("正在导入旅行",()->{try{imported[0]=TripShareArchive.importTrip(activity,archive,activity.store,activity.trips);return null;}finally{archive.delete();}},()->{activity.active=imported[0];activity.day=0;activity.page=0;activity.render();activity.toast("已导入“"+imported[0].title+"”");});
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
