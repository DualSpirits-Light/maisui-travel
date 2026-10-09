package cn.lvxu.travel;
public final class PureShareTest {
 public static void main(String[] args)throws Exception {
  Trip original=Trip.demo();original.companions="private";original.vehicleNumber="private";
  Trip.Item completed=original.items.get(0);completed.done=true;completed.note="completed private note";
  Trip.Item unfinished=original.items.get(1);unfinished.note="keep packing note";unfinished.attributes.put("size","large");
  Trip.Expense expense=new Trip.Expense();expense.name="private bill";original.expenses.add(expense);
  Trip.Checkin memory=new Trip.Checkin();memory.companions.add("private person");original.checkins.add(memory);
  String before=original.json().toString();Trip pure=TripLinkCodec.copyForShare(original,false,true);
  if(pure.items.size()!=original.items.size()-1||pure.items.stream().anyMatch(i->i.id.equals(completed.id)))throw new AssertionError("pure share must exclude completed checklist entries");
  Trip.Item kept=pure.items.stream().filter(i->i.id.equals(unfinished.id)).findFirst().get();
  if(!kept.note.equals("keep packing note")||!kept.listId.equals(unfinished.listId)||!kept.attributes.get("size").equals("large")||pure.lists.size()!=original.lists.size())throw new AssertionError("unfinished checklist details retained");
  if(!pure.expenses.isEmpty()||!pure.checkins.isEmpty()||!pure.companions.isEmpty()||!pure.vehicleNumber.isEmpty())throw new AssertionError("pure share retained private data");
  if(!before.equals(original.json().toString()))throw new AssertionError("share mutated source");
  Trip full=TripLinkCodec.copyForShare(original,true,true);if(!full.items.get(0).done||full.items.size()!=original.items.size())throw new AssertionError("full share lost checklist history");
  System.out.println("PASS: 5 pure-share assertions");
 }
}
