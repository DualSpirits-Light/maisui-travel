package cn.lvxu.travel;

import android.content.Context;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;

/** Portable, offline archive containing exactly one trip and its referenced media. */
public final class TripShareArchive {
    public static final long MAX_ARCHIVE=128L*1024*1024,MAX_ENTRY=32L*1024*1024;
    private static final int MAX_FILES=1000,FORMAT_VERSION=1;
    private TripShareArchive(){}

    public static final class Preview {
        public final String title,city,start;public final int days,stops,expenses,checklistItems,checkins,photos;
        Preview(Trip t,int photos){title=t.title;city=t.city;start=t.start;days=t.days;stops=t.stops.size();expenses=t.expenses.size();checklistItems=t.items.size();checkins=t.checkins.size();this.photos=photos;}
    }

    public static void write(Context context,Trip trip,OutputStream target)throws Exception {write(context,trip,target,false);}
    public static void write(Context context,Trip trip,OutputStream target,boolean linkMode)throws Exception {
        JSONObject envelope=new JSONObject(TripStore.encode(Collections.singletonList(trip)));if(linkMode)envelope.getJSONArray("trips").put(0,TripLinkCodec.pack(trip));
        byte[] tripJson=envelope.toString().getBytes(StandardCharsets.UTF_8);
        if(tripJson.length>MAX_ENTRY)throw new IOException("旅行文字内容过大，无法分享");
        LinkedHashMap<String,String> hashes=new LinkedHashMap<>();JSONObject mediaMap=new JSONObject();long[] rawBytes={0};
        try(ZipOutputStream zip=new ZipOutputStream(new BufferedOutputStream(new LimitedOutputStream(target,MAX_ARCHIVE)))){
            putBytes(zip,"trip.json",tripJson,hashes,rawBytes);
            int index=0;for(String relative:referencedMedia(trip)){
                File source=mediaFile(context,relative);String entry=String.format(Locale.ROOT,"media/%04d%s",index++,extension(source.getName()));
                putFile(zip,entry,source,hashes,rawBytes);mediaMap.put(relative,entry);
            }
            JSONObject files=new JSONObject();for(Map.Entry<String,String> e:hashes.entrySet())files.put(e.getKey(),e.getValue());
            JSONObject manifest=new JSONObject().put("format","maisui-trip").put("version",linkMode?2:FORMAT_VERSION).put("createdAt",System.currentTimeMillis()).put("title",trip.title).put("files",files).put("media",mediaMap);
            byte[] manifestBytes=manifest.toString(2).getBytes(StandardCharsets.UTF_8);addRaw(rawBytes,manifestBytes.length);putRaw(zip,"manifest.json",manifestBytes);
        }
    }

    public static Preview inspect(Context context,File archive)throws Exception {
        Stage stage=extract(context,archive);try{Loaded loaded=load(stage.dir);return new Preview(loaded.trip,loaded.media.length());}finally{deleteTree(stage.dir);}
    }

    /** Imports as an independent trip, regenerating all model IDs and media paths. */
    public static Trip importTrip(Context context,File archive,TripStore store,List<Trip> existing)throws Exception {
        if(existing.size()>=100)throw new IOException("最多支持 100 个旅行");
        Stage stage=extract(context,archive);File installed=null;boolean committed=false;
        try{
            Loaded loaded=load(stage.dir);Trip trip=loaded.trip;if(loaded.linkMode)trip.importedLinkFallbacks=TripLinkCodec.hydrate(trip,url->SourceLinksUi.resolveFresh(context,url));regenerateIds(trip);
            installed=new File(new File(context.getFilesDir(),"media"),"shared/"+trip.id).getCanonicalFile();
            File mediaRoot=new File(context.getFilesDir(),"media").getCanonicalFile();if(!within(installed,mediaRoot)||installed.exists())throw new IOException("无法准备分享照片目录");
            rewriteAndCopyMedia(trip,loaded.media,stage.dir,installed,mediaRoot);
            trip.pinned=false;trip.archived=false;trip.favorite=false;trip.updatedAt=System.currentTimeMillis();
            commitImport(context,trip,store,existing);committed=true;return trip;
        }finally{if(!committed)deleteTree(installed);deleteTree(stage.dir);}
    }

    /** UI-owned imports commit on that same live owner's main thread, after long network work. */
    private static void commitImport(Context context,Trip trip,TripStore store,List<Trip> existing)throws Exception {
        final java.util.concurrent.atomic.AtomicReference<Exception> failure=new java.util.concurrent.atomic.AtomicReference<>();
        final java.util.concurrent.CountDownLatch done=new java.util.concurrent.CountDownLatch(1);
        Runnable commit=()->{try{
            if(context instanceof MainActivity){MainActivity owner=(MainActivity)context;if(owner.isDestroyed()||owner.isFinishing()||owner.loadFailed)throw new IOException("页面已关闭，已取消导入；请重新打开分享文件或口令");}
            if(existing.size()>=100)throw new IOException("最多支持 100 个旅行");
            ArrayList<Trip> merged=new ArrayList<>(existing);merged.add(trip);TripStore.sort(merged);store.save(merged);existing.clear();existing.addAll(merged);
        }catch(Exception e){failure.set(e);}finally{done.countDown();}};
        if(context instanceof MainActivity&&android.os.Looper.myLooper()!=android.os.Looper.getMainLooper()){
            ((MainActivity)context).runOnUiThread(commit);
            // Never delete installed media after a successful commit just because the waiting thread was interrupted.
            boolean interrupted=false;for(;;)try{done.await();break;}catch(InterruptedException e){interrupted=true;}if(interrupted)Thread.currentThread().interrupt();
        }else commit.run();
        if(failure.get()!=null)throw failure.get();
    }

    private static final class Stage {final File dir;Stage(File dir){this.dir=dir;}}
    private static final class Loaded {final Trip trip;final JSONObject media;final boolean linkMode;Loaded(Trip trip,JSONObject media,boolean linkMode){this.trip=trip;this.media=media;this.linkMode=linkMode;}}
    private static Stage extract(Context context,File archive)throws Exception {
        if(archive==null||!archive.isFile()||archive.length()>MAX_ARCHIVE)throw new IOException("分享文件过大或无法读取");
        File stage=new File(context.getCacheDir(),"trip-import-"+UUID.randomUUID());if(!stage.mkdirs())throw new IOException("无法准备导入目录");
        long total=0;int count=0;HashSet<String> seen=new HashSet<>();byte[] buffer=new byte[8192];
        try(ZipInputStream zip=new ZipInputStream(new BufferedInputStream(new FileInputStream(archive)))){
            for(ZipEntry entry;(entry=zip.getNextEntry())!=null;){if(entry.isDirectory())continue;String name=clean(entry.getName());if(!seen.add(name))throw new IOException("分享文件包含重复内容");if(++count>MAX_FILES)throw new IOException("分享文件包含的文件过多");
                File out=new File(stage,name).getCanonicalFile();if(!within(out,stage.getCanonicalFile()))throw new IOException("分享文件路径无效");File parent=out.getParentFile();if(parent!=null&&!parent.mkdirs()&&!parent.isDirectory())throw new IOException("无法准备导入目录");long size=0;
                try(OutputStream target=new FileOutputStream(out)){for(int n;(n=zip.read(buffer))!=-1;){size+=n;total+=n;if(size>MAX_ENTRY||total>MAX_ARCHIVE)throw new IOException("分享文件内容过大");target.write(buffer,0,n);}}
            }
        }catch(Exception e){deleteTree(stage);throw e;}return new Stage(stage);
    }

    private static Loaded load(File stage)throws Exception {
        File manifestFile=new File(stage,"manifest.json"),tripFile=new File(stage,"trip.json");if(!manifestFile.isFile()||!tripFile.isFile())throw new IOException("这不是完整的麦穗旅序分享文件");
        JSONObject manifest=new JSONObject(read(manifestFile,2*1024*1024));if(!"maisui-trip".equals(manifest.optString("format"))||(manifest.optInt("version")!=FORMAT_VERSION&&manifest.optInt("version")!=2))throw new IOException("不支持此分享文件版本");
        JSONObject expected=manifest.getJSONObject("files"),media=manifest.optJSONObject("media");if(media==null||expected.length()>MAX_FILES)throw new IOException("分享文件清单无效");
        HashSet<String> allowed=new HashSet<>();allowed.add("manifest.json");for(Iterator<String> it=expected.keys();it.hasNext();){String name=clean(it.next());File f=new File(stage,name).getCanonicalFile();if(!within(f,stage.getCanonicalFile())||!f.isFile()||!expected.getString(name).equals(hex(f)))throw new IOException("分享文件校验失败："+name);allowed.add(name);}
        validateFileSet(stage,stage,allowed);if(!expected.has("trip.json"))throw new IOException("分享文件缺少旅行数据");
        JSONObject envelope=new JSONObject(read(tripFile,16*1024*1024));boolean linkMode=manifest.optInt("version")==2;if(linkMode){JSONArray tripsJson=envelope.getJSONArray("trips");for(int i=0;i<tripsJson.length();i++)TripLinkCodec.expand(tripsJson.getJSONObject(i));}ArrayList<Trip> trips=TripStore.decode(envelope.toString());if(trips.size()!=1)throw new IOException("分享文件必须只包含一个旅行");Trip trip=trips.get(0);validateMediaMap(trip,media,stage,expected);return new Loaded(trip,media,linkMode);
    }

    private static void validateMediaMap(Trip trip,JSONObject map,File stage,JSONObject expected)throws Exception {
        Set<String> references=referencedMedia(trip);if(map.length()!=references.size())throw new IOException("分享照片清单不完整");HashSet<String> entries=new HashSet<>();
        for(String original:references){validateOriginalMediaPath(original);if(!map.has(original))throw new IOException("分享照片清单不完整");String entry=clean(map.getString(original));if(!entry.startsWith("media/")||!entries.add(entry)||!expected.has(entry)||!new File(stage,entry).isFile())throw new IOException("分享照片清单无效");}
        for(Iterator<String> it=map.keys();it.hasNext();)if(!references.contains(it.next()))throw new IOException("分享照片清单包含未知内容");
    }

    private static void rewriteAndCopyMedia(Trip trip,JSONObject map,File stage,File installed,File mediaRoot)throws Exception {
        if(map.length()>0&&!installed.mkdirs())throw new IOException("无法保存分享照片");HashMap<String,String> rewritten=new HashMap<>();int index=0;
        for(Iterator<String> it=map.keys();it.hasNext();){String old=it.next(),entry=map.getString(old);File source=new File(stage,entry).getCanonicalFile();String name=String.format(Locale.ROOT,"%04d%s",index++,extension(source.getName()));File dest=new File(installed,name).getCanonicalFile();if(!within(dest,mediaRoot))throw new IOException("分享照片路径无效");java.nio.file.Files.copy(source.toPath(),dest.toPath());rewritten.put(old,"media/shared/"+trip.id+"/"+name);}
        applyMedia(trip,rewritten);
    }

    private static void regenerateIds(Trip trip){trip.id=Trip.uid();Map<String,String> categories=new HashMap<>(),lists=new HashMap<>();for(Trip.Category c:trip.categories){String old=c.id;c.id=Trip.uid();categories.put(old,c.id);}for(Trip.Checklist l:trip.lists){String old=l.id;l.id=Trip.uid();lists.put(old,l.id);}for(Trip.Stop s:trip.stops)s.id=Trip.uid();for(Trip.Expense e:trip.expenses){e.id=Trip.uid();if(categories.containsKey(e.categoryId))e.categoryId=categories.get(e.categoryId);}for(Trip.Item i:trip.items){i.id=Trip.uid();if(lists.containsKey(i.listId))i.listId=lists.get(i.listId);}for(Trip.Checkin c:trip.checkins)c.id=Trip.uid();trip.normalize();}
    private static LinkedHashSet<String> referencedMedia(Trip trip){LinkedHashSet<String> out=new LinkedHashSet<>();for(Trip.Stop s:trip.stops){add(out,s.previewPhoto);for(String p:s.notePhotos)add(out,p);}for(Trip.Item i:trip.items)add(out,i.photo);for(Trip.Checkin c:trip.checkins){add(out,c.photo);add(out,c.card);for(String p:c.groupPhotos)add(out,p);for(String p:c.sceneryPhotos)add(out,p);}return out;}
    private static void applyMedia(Trip t,Map<String,String> paths){for(Trip.Stop s:t.stops){s.previewPhoto=replace(s.previewPhoto,paths);replaceAll(s.notePhotos,paths);}for(Trip.Item i:t.items)i.photo=replace(i.photo,paths);for(Trip.Checkin c:t.checkins){c.photo=replace(c.photo,paths);c.card=replace(c.card,paths);replaceAll(c.groupPhotos,paths);replaceAll(c.sceneryPhotos,paths);}}
    private static void replaceAll(List<String> values,Map<String,String> paths){for(int i=0;i<values.size();i++)values.set(i,replace(values.get(i),paths));}
    private static String replace(String value,Map<String,String> paths){return value==null||value.isEmpty()?"":paths.get(value);}
    private static void add(Set<String> values,String path){if(path!=null&&!path.isEmpty())values.add(path.replace('\\','/'));}
    private static File mediaFile(Context c,String relative)throws Exception {String path=validateOriginalMediaPath(relative);File root=new File(c.getFilesDir(),"media").getCanonicalFile(),file=new File(c.getFilesDir(),path).getCanonicalFile();if(!within(file,root)||!file.isFile()||file.length()>MAX_ENTRY)throw new IOException("旅行照片缺失或过大："+relative);return file;}
    private static String validateOriginalMediaPath(String relative)throws IOException {String path=clean(relative);if(!path.startsWith("media/")||path.endsWith("/"))throw new IOException("旅行中的照片路径无效");return path;}
    private static boolean within(File file,File root)throws IOException{return !file.equals(root)&&file.getPath().startsWith(root.getPath()+File.separator);}
    private static String clean(String name)throws IOException {if(name==null||name.isEmpty()||name.startsWith("/")||name.contains("\\")||name.contains("../")||name.equals("..")||name.contains(":"))throw new IOException("分享文件路径无效");return name;}
    private static String extension(String name){int dot=name.lastIndexOf('.');String ext=dot<0?"":name.substring(dot).toLowerCase(Locale.ROOT);return ext.matches("\\.[a-z0-9]{1,8}")?ext:".bin";}
    private static void putBytes(ZipOutputStream zip,String name,byte[] data,Map<String,String> hashes,long[] raw)throws Exception {addRaw(raw,data.length);putRaw(zip,name,data);hashes.put(name,hex(data));}
    private static void putFile(ZipOutputStream zip,String name,File file,Map<String,String> hashes,long[] raw)throws Exception {if(hashes.size()>=MAX_FILES-1)throw new IOException("分享照片数量过多");MessageDigest digest=MessageDigest.getInstance("SHA-256");zip.putNextEntry(new ZipEntry(name));long size=0;try(InputStream in=new FileInputStream(file)){byte[] buffer=new byte[8192];for(int n;(n=in.read(buffer))!=-1;){size+=n;if(size>MAX_ENTRY)throw new IOException("单张照片过大");addRaw(raw,n);zip.write(buffer,0,n);digest.update(buffer,0,n);}}zip.closeEntry();hashes.put(name,hexDigest(digest.digest()));}
    private static void addRaw(long[] count,int amount)throws IOException {if(amount<0||count[0]+amount>MAX_ARCHIVE)throw new IOException("分享文件内容过大");count[0]+=amount;}
    private static void putRaw(ZipOutputStream zip,String name,byte[] data)throws IOException {zip.putNextEntry(new ZipEntry(name));zip.write(data);zip.closeEntry();}
    private static String read(File file,int max)throws IOException {if(file.length()>max)throw new IOException("分享文件条目过大");return new String(java.nio.file.Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8);}
    private static String hex(File file)throws Exception {MessageDigest d=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(file)){byte[] b=new byte[8192];for(int n;(n=in.read(b))!=-1;)d.update(b,0,n);}return hexDigest(d.digest());}
    private static String hex(byte[] data)throws Exception{return hexDigest(MessageDigest.getInstance("SHA-256").digest(data));}
    private static String hexDigest(byte[] bytes){StringBuilder out=new StringBuilder();for(byte b:bytes)out.append(String.format(Locale.ROOT,"%02x",b));return out.toString();}
    private static void validateFileSet(File root,File dir,Set<String> allowed)throws Exception {File[] files=dir.listFiles();if(files==null)return;for(File f:files)if(f.isDirectory())validateFileSet(root,f,allowed);else{String rel=root.toPath().relativize(f.toPath()).toString().replace('\\','/');if(!allowed.contains(rel))throw new IOException("分享文件包含未登记内容："+rel);}}
    private static void deleteTree(File file){if(file==null)return;File[] children=file.listFiles();if(children!=null)for(File child:children)deleteTree(child);file.delete();}
    private static final class LimitedOutputStream extends FilterOutputStream {long written;final long max;LimitedOutputStream(OutputStream out,long max){super(out);this.max=max;}private void check(int n)throws IOException{if(n<0||written+n>max)throw new IOException("分享文件过大");}public void write(int b)throws IOException{check(1);out.write(b);written++;}public void write(byte[] b,int off,int len)throws IOException{check(len);out.write(b,off,len);written+=len;}}
}
