package cn.lvxu.travel;

import android.content.Context;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;

/** Complete, portable archive of trips, media and non-secret preferences. */
public final class BackupArchive {
    public static final long MAX_ARCHIVE=256L*1024*1024, MAX_ENTRY=64L*1024*1024;private static final int MAX_FILES=2000;
    private BackupArchive(){}
    public static void write(Context c,List<Trip> trips,AppPrefs prefs,OutputStream target)throws Exception {
        LinkedHashMap<String,String> hashes=new LinkedHashMap<>();ArchiveByteBudget budget=new ArchiveByteBudget(MAX_ARCHIVE,MAX_ENTRY);ByteArrayOutputStream trip=new ByteArrayOutputStream();trip.write(TripStore.encode(trips).getBytes(StandardCharsets.UTF_8));if(trip.size()>16*1024*1024)throw new IOException("旅行文字内容过大");
        try(ZipOutputStream z=new ZipOutputStream(new BufferedOutputStream(target))){put(z,"trips.json",trip.toByteArray(),hashes,budget);put(z,"preferences.json",bounded(prefs.exportJson().toString(2),2*1024*1024),hashes,budget);for(String path:referencedMedia(trips,prefs))addReferenced(z,c,path,hashes,budget);JSONObject manifest=new JSONObject().put("format",1).put("createdAt",System.currentTimeMillis());JSONObject files=new JSONObject();for(Map.Entry<String,String> e:hashes.entrySet())files.put(e.getKey(),e.getValue());manifest.put("sha256",files);put(z,"manifest.json",bounded(manifest.toString(2),2*1024*1024),new LinkedHashMap<>(),budget);}
    }
    /** The worker phase only writes into its private staging directory. */
    public static PreparedRestore prepare(Context context,InputStream source)throws Exception {
        Context c=context.getApplicationContext();
        File stage=new File(c.getCacheDir(),"restore-"+UUID.randomUUID());if(!stage.mkdirs())throw new IOException("无法准备恢复目录");ArchiveByteBudget budget=new ArchiveByteBudget(MAX_ARCHIVE,MAX_ENTRY);try{byte[] buf=new byte[8192];HashSet<String> seen=new HashSet<>();int fileCount=0;try(ZipInputStream z=new ZipInputStream(new BufferedInputStream(source))){for(ZipEntry e;(e=z.getNextEntry())!=null;){String n=clean(e.getName());if(e.isDirectory())continue;if(!seen.add(n))throw new IOException("备份包含重复条目："+n);if(++fileCount>MAX_FILES)throw new IOException("备份文件数量过多");File out=new File(stage,n);if(!out.getCanonicalPath().startsWith(stage.getCanonicalPath()+File.separator))throw new IOException("备份路径无效");File parent=out.getParentFile();if(parent!=null&&!parent.mkdirs()&&!parent.isDirectory())throw new IOException("无法创建恢复目录");long entry=0;try(OutputStream w=new FileOutputStream(out)){for(int r;(r=z.read(buf))!=-1;){budget.consume(entry,r);entry+=r;w.write(buf,0,r);}}}}
            File mf=new File(stage,"manifest.json"),tf=new File(stage,"trips.json");if(!mf.isFile()||!tf.isFile())throw new IOException("备份内容不完整");JSONObject manifest=new JSONObject(read(mf,2*1024*1024));if(manifest.getInt("format")!=1)throw new IOException("不支持此备份版本");JSONObject expected=manifest.getJSONObject("sha256");Iterator<String> keys=expected.keys();while(keys.hasNext()){String n=clean(keys.next());File f=new File(stage,n);if(!f.isFile()||!expected.getString(n).equals(hex(f)))throw new IOException("备份校验失败："+n);}validateFileSet(stage,stage,expected);
            ArrayList<Trip> imported=TripStore.decode(read(tf,16*1024*1024));
            File incoming=new File(stage,"media");validateTripMedia(imported,incoming,incoming);JSONObject importedPrefs=null;File pf=new File(stage,"preferences.json");if(pf.isFile()){importedPrefs=new JSONObject(read(pf,2*1024*1024));validatePreferenceMedia(importedPrefs,incoming,incoming);validatePreferences(importedPrefs);}
            return new PreparedRestore(c,stage,imported,importedPrefs);
        }catch(Exception e){deleteTree(stage);throw e;}
    }
    /** Compatibility entry point. The supplied Activity snapshot is deliberately ignored. */
    public static int restore(Context c,InputStream source,TripStore store,List<Trip> existing,AppPrefs prefs)throws Exception {
        try(PreparedRestore prepared=prepare(c,source)){return prepared.commit(store,prefs);}
    }
    public static final class PreparedRestore implements AutoCloseable {
        private final Context context;private final File stage;private final ArrayList<Trip> imported;private final JSONObject preferences;
        private boolean closed,committed;private int added,skipped,copies;
        private PreparedRestore(Context c,File stage,ArrayList<Trip> imported,JSONObject preferences){context=c;this.stage=stage;this.imported=imported;this.preferences=preferences;}
        /** Call from the UI success callback so no Activity save can interleave with read/save. */
        public synchronized int commit(TripStore store,AppPrefs prefs)throws Exception {return commit(store,prefs,false);}
        public synchronized int commit(TripStore store,AppPrefs prefs,boolean allowUnreadable)throws Exception {
            if(closed||committed)throw new IllegalStateException("恢复任务已结束");
            ArrayList<Trip> latest=store.readForRestore(allowUnreadable);
            File incoming=new File(stage,"media"),dest=new File(context.getFilesDir(),"media");
            TagRepository registry=new TagRepository(context);TagRepository.MergePlan tagPlan=null;
            ArrayList<Trip> candidates=new ArrayList<>();for(Trip t:imported)candidates.add(Trip.from(t.json()));
            JSONObject settings=preferences==null?null:new JSONObject(preferences.toString());
            if(settings!=null&&settings.optJSONObject("tagRegistry")!=null){tagPlan=registry.prepareImport(settings.getJSONObject("tagRegistry"));for(Trip t:candidates)registry.remap(t,tagPlan.ids);}
            BackupMergePlan plan=new BackupMergePlan(latest,candidates,p->{File f=new File(context.getFilesDir(),p);return f.isFile()?hex(f):"missing:"+p;},p->hex(new File(stage,p)));
            Map<String,String> mediaPaths=new LinkedHashMap<>();prepareMediaPaths(incoming,incoming,dest,mediaPaths);
            for(Trip t:plan.added)remapMedia(t,mediaPaths);
            if(settings!=null)for(String key:new String[]{"avatar","background"}){String path=settings.optString(key,"");if(mediaPaths.containsKey(path))settings.put(key,mediaPaths.get(path));}
            copyMappedMedia(stage,context.getFilesDir(),mediaPaths);
            store.save(plan.merged);
            added=plan.added.size();skipped=imported.size()-added;copies=plan.copies.size();committed=true;
            if(tagPlan!=null)registry.commitImport(tagPlan);
            if(settings!=null){settings.remove("tagRegistry");prefs.importJson(settings);}
            return added;
        }
        public String summary(){return "恢复完成：新增 "+added+" 个旅行，跳过 "+skipped+" 个相同旅行"+(copies==0?"":"，其中 "+copies+" 个为恢复副本");}
        @Override public synchronized void close(){if(!closed){closed=true;deleteTree(stage);}}
    }
    private static void prepareMediaPaths(File root,File dir,File dest,Map<String,String> paths)throws Exception {
        File[] files=dir.listFiles();if(files==null)return;
        for(File file:files){if(file.isDirectory()){prepareMediaPaths(root,file,dest,paths);continue;}String relative=root.toPath().relativize(file.toPath()).toString().replace('\\','/');String old="media/"+relative;File local=new File(dest,relative);String next=old;
            if(local.exists()&&!hex(file).equals(hex(local))){String name=file.getName();int dot=name.lastIndexOf('.');String suffix=dot>=0?name.substring(dot):"";do{next="media/restore-"+UUID.randomUUID()+suffix;}while(new File(dest.getParentFile(),next).exists()||paths.containsValue(next));}
            paths.put(old,next);
        }
    }
    private static void copyMappedMedia(File sourceRoot,File destRoot,Map<String,String> paths)throws IOException {
        for(Map.Entry<String,String> path:paths.entrySet()){File source=new File(sourceRoot,path.getKey()),target=new File(destRoot,path.getValue());if(target.exists())continue;File parent=target.getParentFile();if(!parent.isDirectory()&&!parent.mkdirs())throw new IOException("无法恢复媒体");java.nio.file.Files.copy(source.toPath(),target.toPath());}
    }
    private static String remapped(String path,Map<String,String> paths){return paths.getOrDefault(path,path);}
    private static void remapMedia(Trip t,Map<String,String> paths){
        for(Trip.Stop s:t.stops){s.previewPhoto=remapped(s.previewPhoto,paths);s.notePhotos.replaceAll(p->remapped(p,paths));}
        for(Trip.Expense e:t.expenses)e.photo=remapped(e.photo,paths);for(Trip.Item i:t.items)i.photo=remapped(i.photo,paths);
        for(Trip.Checkin c:t.checkins){c.photo=remapped(c.photo,paths);c.card=remapped(c.card,paths);c.groupPhotos.replaceAll(p->remapped(p,paths));c.sceneryPhotos.replaceAll(p->remapped(p,paths));}
    }
    private static LinkedHashSet<String> referencedMedia(List<Trip> trips,AppPrefs prefs){LinkedHashSet<String> out=new LinkedHashSet<>();addPath(out,prefs.avatar());addPath(out,prefs.background());for(Trip t:trips){for(Trip.Stop s:t.stops){addPath(out,s.previewPhoto);for(String x:s.notePhotos)addPath(out,x);}for(Trip.Expense e:t.expenses)addPath(out,e.photo);for(Trip.Item i:t.items)addPath(out,i.photo);for(Trip.Checkin c:t.checkins){addPath(out,c.photo);addPath(out,c.card);for(String x:c.groupPhotos)addPath(out,x);for(String x:c.sceneryPhotos)addPath(out,x);}}return out;}
    private static void addPath(Set<String> out,String path){if(path!=null&&!path.isEmpty())out.add(path.replace('\\','/'));}
    private static void addReferenced(ZipOutputStream z,Context c,String path,Map<String,String> hashes,ArchiveByteBudget budget)throws Exception {if(!path.startsWith("media/")||path.startsWith("/")||path.contains("../")||path.contains(":")||path.endsWith("/"))throw new IOException("引用的媒体路径无效");File root=new File(c.getFilesDir(),"media").getCanonicalFile(),file=new File(c.getFilesDir(),path).getCanonicalFile();if(!file.getPath().startsWith(root.getPath()+File.separator)||!file.isFile())throw new IOException("引用的照片文件缺失："+path);if(file.length()>MAX_ENTRY)throw new IOException("媒体文件过大："+path);put(z,path,java.nio.file.Files.readAllBytes(file.toPath()),hashes,budget);}
    private static void put(ZipOutputStream z,String n,byte[] b,Map<String,String> hashes,ArchiveByteBudget budget)throws Exception {budget.add(b.length);if(hashes.size()>=MAX_FILES-1)throw new IOException("备份文件数量过多");putRaw(z,n,b);hashes.put(n,hex(b));}
    private static void putRaw(ZipOutputStream z,String n,byte[] b)throws IOException {z.putNextEntry(new ZipEntry(n));z.write(b);z.closeEntry();}
    private static byte[] bounded(String text,int max)throws IOException {byte[] bytes=text.getBytes(StandardCharsets.UTF_8);if(bytes.length>max)throw new IOException("备份条目过大");return bytes;}
    private static String clean(String n)throws IOException {if(n==null||n.startsWith("/")||n.contains("\\")||n.contains("../")||n.equals("..")||n.contains(":"))throw new IOException("备份路径无效");return n;}
    private static String read(File f,int max)throws IOException {if(f.length()>max)throw new IOException("备份条目过大");return new String(java.nio.file.Files.readAllBytes(f.toPath()),StandardCharsets.UTF_8);}
    private static String hex(File f)throws Exception {try(InputStream in=new FileInputStream(f)){MessageDigest d=MessageDigest.getInstance("SHA-256");byte[] b=new byte[8192];for(int n;(n=in.read(b))!=-1;)d.update(b,0,n);return hexDigest(d.digest());}}
    private static String hex(byte[] b)throws Exception {return hexDigest(MessageDigest.getInstance("SHA-256").digest(b));}
    private static String hexDigest(byte[] b){StringBuilder s=new StringBuilder();for(byte x:b)s.append(String.format(Locale.ROOT,"%02x",x));return s.toString();}
    private static void validateFileSet(File root,File dir,JSONObject expected)throws Exception {File[] fs=dir.listFiles();if(fs==null)return;for(File f:fs){if(f.isDirectory())validateFileSet(root,f,expected);else{String rel=root.toPath().relativize(f.toPath()).toString().replace('\\','/');if(!"manifest.json".equals(rel)&&!expected.has(rel))throw new IOException("备份包含未登记文件："+rel);}}}
    private static void validatePreferenceMedia(JSONObject p,File incoming,File existing)throws Exception {for(String key:new String[]{"avatar","background"}){String rel=p.optString(key,"");if(rel.isEmpty())continue;if(!rel.startsWith("media/")||rel.contains("..")||rel.contains("\\")||rel.contains(":"))throw new IOException("偏好中的媒体路径无效");rel=rel.substring(6);File a=new File(incoming,rel).getCanonicalFile(),b=new File(existing,rel).getCanonicalFile();if(!a.getPath().startsWith(incoming.getCanonicalPath()+File.separator)&&!b.getPath().startsWith(existing.getCanonicalPath()+File.separator))throw new IOException("偏好中的媒体路径无效");if(!a.isFile()&&!b.isFile())throw new IOException("头像或背景文件缺失");}}
    private static void validateTripMedia(List<Trip> trips,File incoming,File existing)throws Exception {for(Trip t:trips){for(Trip.Stop s:t.stops){validateMediaPath(s.previewPhoto,incoming,existing);for(String x:s.notePhotos)validateMediaPath(x,incoming,existing);}for(Trip.Expense e:t.expenses)validateMediaPath(e.photo,incoming,existing);for(Trip.Item i:t.items)validateMediaPath(i.photo,incoming,existing);for(Trip.Checkin x:t.checkins){validateMediaPath(x.photo,incoming,existing);validateMediaPath(x.card,incoming,existing);for(String path:x.groupPhotos)validateMediaPath(path,incoming,existing);for(String path:x.sceneryPhotos)validateMediaPath(path,incoming,existing);}}}
    private static void validateMediaPath(String path,File incoming,File existing)throws Exception {if(path==null||path.isEmpty())return;if(!path.startsWith("media/")||path.contains("..")||path.contains("\\")||path.contains(":"))throw new IOException("旅行中的媒体路径无效");String rel=path.substring(6);File a=new File(incoming,rel).getCanonicalFile(),b=new File(existing,rel).getCanonicalFile();String ia=incoming.getCanonicalPath()+File.separator,ib=existing.getCanonicalPath()+File.separator;if(!a.getPath().startsWith(ia)||!b.getPath().startsWith(ib))throw new IOException("旅行中的媒体路径无效");if(!a.isFile()&&!b.isFile())throw new IOException("旅行照片文件缺失");}
    private static void validatePreferences(JSONObject p)throws Exception {AppPrefs.validateColorPreferences(p);JSONArray favorites=p.optJSONArray("nearbyFavorites");if(favorites!=null)NearbyFavorites.parse(favorites);JSONArray n=p.optJSONArray("nav");if(n!=null){if(n.length()>20)throw new IOException("偏好内容无效");for(int i=0;i<n.length();i++){if(!(n.opt(i) instanceof JSONObject))throw new IOException("偏好内容无效");String id=n.getJSONObject(i).optString("id","");if(!Arrays.asList("home","itinerary","budget","checklist","checkin").contains(id))throw new IOException("偏好中的底栏项目无效");}}JSONObject registry=p.optJSONObject("tagRegistry");if(registry!=null)try{TagRepository.validateSnapshot(registry);}catch(IllegalArgumentException e){throw new IOException("标签备份内容无效",e);}}
    private static void deleteTree(File f){File[] fs=f.listFiles();if(fs!=null)for(File x:fs)deleteTree(x);f.delete();}
}
