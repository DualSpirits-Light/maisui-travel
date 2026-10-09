package cn.lvxu.travel;
import org.json.*;

/** Regression cases from the 2026-10-05 hands-on user audit. */
public final class AuditModelIntegrityTest {
 static int checks;
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);checks++;}
 public static void main(String[] args)throws Exception{
  Trip original=Trip.demo();original.pinned=true;original.favorite=true;original.archived=true;original.updatedAt=123456789;
  Trip copy=Trip.from(new JSONObject(original.json().toString()));
  check(copy.favorite,"opening and saving an unchanged trip retains favorite");
  check(copy.pinned,"editing retains pin");check(copy.archived,"editing retains archive");
  check(copy.updatedAt==original.updatedAt,"copy retains modification timestamp");
  JSONObject old=original.json();for(String k:new String[]{"favorite","pinned","archived","updatedAt"})old.remove(k);
  Trip legacy=Trip.from(old);check(!legacy.favorite&&!legacy.pinned&&!legacy.archived,"legacy trip defaults remain valid");
  Trip.Checkin scenery=new Trip.Checkin();scenery.photo="media/photos/view.jpg";scenery.sceneryPhotos.add(scenery.photo);
  Trip.Checkin read=Trip.Checkin.from(scenery.json());
  check(read.groupPhotos.isEmpty(),"scenery cover must not migrate into group album");
  check(read.sceneryPhotos.size()==1,"scenery album survives round trip");
  JSONObject cleared=scenery.json().put("groupPhotos",new JSONArray()).put("sceneryPhotos",new JSONArray()).put("photo","");
  read=Trip.Checkin.from(cleared);check(read.photo.isEmpty()&&read.groupPhotos.isEmpty()&&read.sceneryPhotos.isEmpty(),"empty classified albums stay empty");
  JSONObject unclassified=new JSONObject().put("photo","media/photos/old.jpg");
  read=Trip.Checkin.from(unclassified);check(read.groupPhotos.size()==1,"unclassified legacy photo remains accessible");
  JSONObject modernEmpty=new JSONObject().put("photo","media/photos/stale.jpg").put("groupPhotos",new JSONArray()).put("sceneryPhotos",new JSONArray());
  read=Trip.Checkin.from(modernEmpty);check(read.groupPhotos.isEmpty(),"explicit empty classified albums do not resurrect legacy group");
  System.out.println("PASS: "+checks+" audit model integrity assertions");
 }
}
