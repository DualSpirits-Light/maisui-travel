package cn.lvxu.travel;
import java.util.*;
public final class BackupMergePlanTest {
 static int n;static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);n++;}
 public static void main(String[] args)throws Exception {
  Trip source=Trip.demo();source.pinned=true;source.favorite=true;source.items.get(0).note="packing";
  Trip.Checkin c=new Trip.Checkin();c.stopId=source.stops.get(0).id;c.time="2031-04-05T12:30";source.checkins.add(c);
  Trip local=Trip.from(source.json());local.pinned=source.pinned;local.favorite=source.favorite;
  BackupMergePlan same=new BackupMergePlan(Collections.singletonList(local),Collections.singletonList(source));
  check(same.added.isEmpty()&&same.merged.size()==1,"identical same-id restore is skipped");
  local.items.get(0).note="new local packing";String before=local.json().toString();
  BackupMergePlan conflict=new BackupMergePlan(Collections.singletonList(local),Collections.singletonList(source));Trip copy=conflict.added.get(0);
  check(conflict.merged.size()==2&&!copy.id.equals(source.id)&&!copy.title.equals(source.title),"conflict becomes named independent copy");
  check(!copy.stops.get(0).id.equals(source.stops.get(0).id)&&!copy.checkins.get(0).id.equals(c.id)&&copy.checkins.get(0).stopId.equals(copy.stops.get(0).id),"copy IDs preserve stop associations");
  check(!copy.items.get(0).id.equals(source.items.get(0).id)&&!copy.lists.get(0).id.equals(source.lists.get(0).id)&&copy.items.get(0).listId.equals(copy.lists.get(0).id),"copy checklist IDs preserve list associations");
  check(before.equals(local.json().toString())&&copy.items.get(0).note.equals("packing"),"conflict preserves changed local snapshot");
  ArrayList<Trip> hundred=new ArrayList<>();for(int i=0;i<100;i++){Trip t=Trip.from(source.json());t.id="trip-"+i;hundred.add(t);}Trip duplicate=Trip.from(hundred.get(0).json());duplicate.pinned=true;duplicate.favorite=true;
  check(new BackupMergePlan(hundred,Collections.singletonList(duplicate)).merged.size()==100,"duplicates at trip limit remain valid");
  boolean rejected=false;try{new BackupMergePlan(hundred,Collections.singletonList(source));}catch(java.io.IOException e){rejected=true;}check(rejected,"new trip beyond limit rejected");
  Trip notes=Trip.from(source.json());notes.items.get(0).note="media/this-is-a-note";
  BackupMergePlan.MediaKey noFiles=p->{throw new AssertionError("text mistaken for photo: "+p);};
  check(new BackupMergePlan(Collections.singletonList(notes),Collections.singletonList(notes),noFiles,noFiles).added.isEmpty(),"media-looking notes are ordinary text");
  Trip photo=Trip.from(source.json());photo.items.get(0).photo="media/a.jpg";Trip photoCopy=Trip.from(photo.json());photoCopy.items.get(0).photo="media/b.jpg";
  check(new BackupMergePlan(Collections.singletonList(photo),Collections.singletonList(photoCopy),p->"identical-bytes",p->"identical-bytes").added.isEmpty(),"same photo bytes at different paths are identical");
  check(new BackupMergePlan(Collections.singletonList(photo),Collections.singletonList(photo),p->"local-bytes",p->"backup-bytes").added.size()==1,"changed photo bytes create a copy");
  Trip newer=Trip.from(source.json());newer.updatedAt+=12345;
  check(new BackupMergePlan(Collections.singletonList(newer),Collections.singletonList(source)).added.isEmpty(),"timestamp alone does not create copies");
  Trip renamed=Trip.from(source.json());renamed.title=source.title+"（恢复副本）";renamed.id="another";
  BackupMergePlan named=new BackupMergePlan(Arrays.asList(local,renamed),Collections.singletonList(source));
  check(named.added.get(0).title.endsWith("（恢复副本 2）"),"copy names avoid existing titles");
  BackupMergePlan repeated=new BackupMergePlan(conflict.merged,Collections.singletonList(source));
  check(repeated.added.isEmpty()&&repeated.merged.size()==2,"repeating a conflicting backup skips an unchanged restored copy");
  copy.items.get(0).note="user modified restored copy";
  BackupMergePlan afterEdit=new BackupMergePlan(conflict.merged,Collections.singletonList(source));
  check(afterEdit.added.size()==1&&afterEdit.merged.size()==3&&copy.items.get(0).note.equals("user modified restored copy"),"modified restored copy remains and original backup gets another copy");
  Trip otherSource=Trip.from(source.json());otherSource.id="other-source-id";Trip otherLocal=Trip.from(otherSource.json());otherLocal.items.get(0).note="other current changes";
  ArrayList<Trip> unrelated=new ArrayList<>(conflict.merged);unrelated.add(otherLocal);
  check(new BackupMergePlan(unrelated,Collections.singletonList(otherSource)).added.size()==1,"equal content from another source ID is not mistaken for a restored copy");
  BackupMergePlan renamedCopy=new BackupMergePlan(Collections.singletonList(local),Collections.singletonList(source));renamedCopy.added.get(0).title=source.title+"（恢复副本 2）";
  check(new BackupMergePlan(renamedCopy.merged,Collections.singletonList(source)).added.size()==1,"renaming a restored copy is a user modification even when its title resembles another restore name");
  System.out.println("PASS: "+n+" backup merge assertions");
 }
}
