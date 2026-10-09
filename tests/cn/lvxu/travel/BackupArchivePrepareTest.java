package cn.lvxu.travel;

import android.content.Context;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Local ZIP harness: compile with the lightweight Context/store/prefs fixtures, not android.jar. */
public final class BackupArchivePrepareTest {
 private static int assertions;
 private static void check(boolean result,String message){if(!result)throw new AssertionError(message);assertions++;}
 private static final class FixtureContext extends Context {
  private final File root;
  FixtureContext(File root){this.root=root;new File(root,"cache").mkdirs();new File(root,"files").mkdirs();}
  public File getCacheDir(){return new File(root,"cache");}
  public File getFilesDir(){return new File(root,"files");}
 }
 private static byte[] archive(Context c,Trip trip)throws Exception {ByteArrayOutputStream out=new ByteArrayOutputStream();BackupArchive.write(c,Collections.singletonList(trip),new AppPrefs(),out);return out.toByteArray();}
 private static byte[] rewritten(byte[] zip,String replace)throws Exception {
  ByteArrayOutputStream out=new ByteArrayOutputStream();try(ZipInputStream input=new ZipInputStream(new ByteArrayInputStream(zip));ZipOutputStream output=new ZipOutputStream(out)){for(ZipEntry e;(e=input.getNextEntry())!=null;){output.putNextEntry(new ZipEntry(e.getName()));byte[] bytes=input.readAllBytes();output.write(e.getName().equals(replace)?"tampered".getBytes(java.nio.charset.StandardCharsets.UTF_8):bytes);output.closeEntry();}}return out.toByteArray();
 }
 public static void main(String[] args)throws Exception {
  Path temporary=Files.createTempDirectory("backup-prepare-test-");
  try {
   FixtureContext source=new FixtureContext(temporary.resolve("source").toFile()),destination=new FixtureContext(temporary.resolve("destination").toFile());
   Trip backup=Trip.demo();backup.items.get(0).note="original";backup.items.get(0).photo="media/photo.jpg";
   File originalPhoto=new File(source.getFilesDir(),"media/photo.jpg");originalPhoto.getParentFile().mkdirs();Files.write(originalPhoto.toPath(),new byte[]{1,2,3});
   byte[] bytes=archive(source,backup);TripStore store=new TripStore();store.save(Collections.singletonList(backup));AppPrefs prefs=new AppPrefs();int saved=store.saves;
   try(BackupArchive.PreparedRestore prepared=BackupArchive.prepare(destination,new ByteArrayInputStream(bytes))){
    check(store.saves==saved&&prefs.imports==0,"prepare leaves database and settings untouched");
    check(!new File(destination.getFilesDir(),"media/photo.jpg").exists(),"prepare leaves live media untouched");
    Trip edited=Trip.from(backup.json());edited.items.get(0).note="edited after prepare";Trip newTrip=Trip.from(backup.json());newTrip.id="new-after-prepare";newTrip.title="new local trip";
    store.save(Arrays.asList(edited,newTrip));File localPhoto=new File(destination.getFilesDir(),"media/photo.jpg");localPhoto.getParentFile().mkdirs();Files.write(localPhoto.toPath(),new byte[]{9,8,7});
    check(prepared.commit(store,prefs)==1,"commit adds one conflicting backup copy");
    check(store.data.size()==3&&store.data.get(0).items.get(0).note.equals("edited after prepare")&&store.data.get(1).id.equals(newTrip.id),"commit preserves changes made after prepare");
    Trip restored=store.data.get(2);check(restored.title.endsWith("（恢复副本）")&&!restored.id.equals(backup.id),"conflict copy has a visible name and independent ID");
    check(Arrays.equals(Files.readAllBytes(localPhoto.toPath()),new byte[]{9,8,7}),"conflicting current photo remains unchanged");
    check(!restored.items.get(0).photo.equals(backup.items.get(0).photo)&&Arrays.equals(Files.readAllBytes(new File(destination.getFilesDir(),restored.items.get(0).photo).toPath()),new byte[]{1,2,3}),"restored photo gets a new path and correct bytes");
    check(prepared.summary().contains("新增 1")&&prepared.summary().contains("1 个为恢复副本"),"restore summary reports conflict copy");
    boolean ended=false;try{prepared.commit(store,prefs);}catch(IllegalStateException e){ended=true;}check(ended,"prepared restore cannot be committed twice");
   }
   check(destination.getCacheDir().list().length==0,"successful close removes staging files");
   try(BackupArchive.PreparedRestore repeated=BackupArchive.prepare(destination,new ByteArrayInputStream(bytes))){check(repeated.commit(store,prefs)==0&&store.data.size()==3,"repeated conflict archive skips its copy even after media paths were remapped");}
   FixtureContext matching=new FixtureContext(temporary.resolve("matching").toFile());File photo=new File(matching.getFilesDir(),"media/photo.jpg");photo.getParentFile().mkdirs();Files.write(photo.toPath(),new byte[]{1,2,3});TripStore equalStore=new TripStore();equalStore.save(Collections.singletonList(backup));
   try(BackupArchive.PreparedRestore prepared=BackupArchive.prepare(matching,new ByteArrayInputStream(bytes))){check(prepared.commit(equalStore,new AppPrefs())==0&&equalStore.data.size()==1,"identical same-ID archive is skipped");check(prepared.summary().contains("跳过 1"),"summary reports skipped matching trip");}
   BackupArchive.PreparedRestore abandoned=BackupArchive.prepare(matching,new ByteArrayInputStream(bytes));abandoned.close();abandoned.close();check(matching.getCacheDir().list().length==0,"abandoned prepare cleanup is idempotent");
   boolean invalid=false;try{BackupArchive.prepare(matching,new ByteArrayInputStream(rewritten(bytes,"media/photo.jpg")));}catch(IOException e){invalid=e.getMessage().contains("校验失败");}check(invalid&&matching.getCacheDir().list().length==0,"corrupt ZIP is rejected and staging is cleaned");
   FixtureContext missing=new FixtureContext(temporary.resolve("missing").toFile());TripStore missingStore=new TripStore();missingStore.save(Collections.singletonList(backup));
   try(BackupArchive.PreparedRestore prepared=BackupArchive.prepare(missing,new ByteArrayInputStream(bytes))){check(prepared.commit(missingStore,new AppPrefs())==1&&new File(missing.getFilesDir(),missingStore.data.get(1).items.get(0).photo).isFile(),"missing current photo does not prevent restoring a valid backup copy");}
   System.out.println("PASS: "+assertions+" real ZIP prepare/commit assertions (local storage fixtures)");
  }finally{try(java.util.stream.Stream<Path> entries=Files.walk(temporary)){entries.sorted(Comparator.reverseOrder()).forEach(p->{try{Files.delete(p);}catch(IOException ignored){}});}}
 }
}
