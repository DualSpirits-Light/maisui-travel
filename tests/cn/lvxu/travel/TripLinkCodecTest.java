package cn.lvxu.travel;
import org.json.*;
public final class TripLinkCodecTest {
 static int n;static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);n++;}
 public static void main(String[] args)throws Exception {
  Trip t=Trip.demo();t.stops.clear();Trip.Stop s=new Trip.Stop();s.name="原地点";s.address="原地址";s.sourceUrl="https://amap.com/place/B001";s.lat=30.0;s.lon=120.0;s.coordinateSystem="GCJ02";s.rating=4.0f;s.sourceSnapshot=TripLinkCodec.snapshot(s);s.name="我的名称";s.note="保留备注";s.previewPhoto="media/photos/a.png";t.stops.add(s);
  JSONObject packed=TripLinkCodec.pack(t);JSONObject p=packed.getJSONArray("stops").getJSONObject(0);check(p.has("amap")&&!p.has("name"),"linked source uses compact envelope");check(p.getJSONObject("amap").getJSONObject("changes").getString("name").equals("我的名称"),"name patch recorded");
  Trip read=Trip.from(TripLinkCodec.expand(packed));check(read.stops.get(0).name.equals(s.name)&&read.stops.get(0).previewPhoto.equals(s.previewPhoto),"fallback expansion retains changes and photo paths");
  TripLinkCodec.hydrate(read,url->new JSONObject().put("name","高德新名称").put("address","最新地址").put("lat",31).put("lon",121).put("rating",4.5).put("coordinateSystem","GCJ02"));check(read.stops.get(0).name.equals("我的名称"),"user name overrides remote");check(read.stops.get(0).address.equals("最新地址"),"unchanged source refreshes");check(read.stops.get(0).note.equals("保留备注"),"local notes retained");check(read.stops.get(0).lat==31.0&&read.stops.get(0).lon==121.0&&read.stops.get(0).rating==4.5f,"unchanged numeric fields refresh after JSON roundtrip");
  Trip again=Trip.from(TripLinkCodec.expand(TripLinkCodec.pack(read)));check(again.stops.get(0).name.equals("我的名称")&&again.stops.get(0).address.equals("最新地址"),"reshare keeps rebased edits");
  String before=again.json().toString();int failures=TripLinkCodec.hydrate(again,url->{throw new Exception("offline");});check(failures==1&&before.equals(again.json().toString()),"failed fetch preserves snapshot exactly");
  Trip legacy=Trip.from(t.json());legacy.stops.get(0).sourceSnapshot="";legacy=Trip.from(TripLinkCodec.expand(TripLinkCodec.pack(legacy)));TripLinkCodec.hydrate(legacy,url->new JSONObject().put("name","remote").put("address","remote"));check(legacy.stops.get(0).name.equals(s.name)&&legacy.stops.get(0).address.equals(s.address),"legacy user edits never overwritten");
  s.sourceUrl="";s.sourceSnapshot="";check(!TripLinkCodec.pack(t).getJSONArray("stops").getJSONObject(0).has("amap"),"deleted link becomes plain data");TripLinkCodec.hydrate(t,url->{throw new AssertionError("deleted link fetched");});check(true,"deleted link never fetched");
  t.accommodation="自定义住宿";t.accommodationAddress="旧地址";t.accommodationSourceUrl="https://amap.com/place/B002";t.accommodationSnapshot=new JSONObject().put("name","原住宿").put("address","旧地址").toString();Trip lodging=Trip.from(TripLinkCodec.expand(TripLinkCodec.pack(t)));TripLinkCodec.hydrate(lodging,url->new JSONObject().put("name","新住宿").put("address","新地址"));check(lodging.accommodation.equals("自定义住宿")&&lodging.accommodationAddress.equals("新地址"),"lodging patch and unchanged refresh merge");
  JSONObject base=new JSONObject().put("lat",30).put("lon",120).put("coordinateSystem","GCJ02"),current=new JSONObject().put("lat",30).put("lon",120).put("coordinateSystem","WGS84");JSONObject patch=TripLinkCodec.changes(base,current);check(patch.has("lat")&&patch.has("lon"),"coordinate edits preserve whole coordinate group");
  System.out.println("PASS: "+n+" source-link assertions");
 }
}
