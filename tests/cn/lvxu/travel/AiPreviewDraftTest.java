package cn.lvxu.travel;

public final class AiPreviewDraftTest {
 static int count;
 static void check(boolean b,String label){if(!b)throw new AssertionError(label);count++;}
 public static void main(String[] args){
  AiPlan p=AiPlan.parse("{\"days\":[{\"day\":1,\"items\":[{\"name\":\"西湖\",\"time\":\"09:00\",\"duration\":60,\"address\":\"旧地址\",\"lat\":30,\"lon\":120}]}]}",1);
  AiPlan.Item i=p.days.get(0).items.get(0);i.selected=false;
  check(p.toStops().isEmpty(),"unselected excluded");i.selected=true;
  try{AiPlan.edit(i,"新地点","25:00","60","0","新地址","备注");throw new AssertionError("invalid edit accepted");}catch(IllegalArgumentException expected){count++;}
  check(i.name.equals("西湖")&&i.time.equals("09:00"),"failed edit atomic");
  AiPlan.edit(i,"西湖","10:00","90","12.50","旧地址","新备注");
  check(i.lat!=null&&i.lon!=null,"schedule edit keeps position");
  check(p.toStops().get(0).cost==1250&&p.toStops().get(0).duration==90,"edited fields imported");
  AiPlan.edit(i,"新地点","10:00","90","0","新地址","备注");
  check(i.lat==null&&i.lon==null&&i.verifyNeeded,"relocation clears stale position");
  check(p.toStops().get(0).address.equals("新地址"),"edited address retained");
  System.out.println("PASS: "+count+" AI preview draft assertions");
 }
}
