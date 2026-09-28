package cn.lvxu.travel;
import org.json.JSONObject;
public final class CheckinAssociationTest {
 public static void main(String[] args)throws Exception {run();}
 public static int run()throws Exception {
  JSONObject legacy=new JSONObject().put("place","西湖").put("time","2026-09-28T10:00");
  Trip.Checkin old=Trip.Checkin.from(legacy);
  if(!old.json().optString("stopId", "").isEmpty())throw new AssertionError("legacy defaults unlinked");
  Trip.Checkin linked=Trip.Checkin.from(new JSONObject(legacy.toString()).put("stopId","stop-stable-id"));
  if(!"stop-stable-id".equals(linked.json().optString("stopId")))throw new AssertionError("explicit association survives roundtrip");
  boolean rejected=false;try{Trip.Checkin.from(new JSONObject(legacy.toString()).put("stopId","x".repeat(101)));}catch(IllegalArgumentException expected){rejected=true;}
  if(!rejected)throw new AssertionError("bounded identifier");
  System.out.println("PASS: 3 Checkin association assertions");return 3;
 }
}
