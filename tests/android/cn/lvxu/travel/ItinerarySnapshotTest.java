package cn.lvxu.travel;
/** Export snapshots must not mutate defaults, share mutable fields, or include private trip fields. */
final class ItinerarySnapshotTest {
 static int run()throws Exception{
  Trip source=new Trip();source.title="快照";source.city="杭州";source.companions="私密同行人";
  Trip.Stop stop=new Trip.Stop();stop.name="西湖";stop.notePhotos.add("media/photos/sample.jpg");stop.tagIds.add("sample");stop.tagNames.put("sample","风景");source.stops.add(stop);
  Trip copy=source.itinerarySnapshot();
  check(source.categories.isEmpty()&&source.lists.isEmpty(),"snapshot must not normalize source");
  check(copy.stops.size()==1&&copy.stops.get(0)!=stop&&copy.title.equals(source.title),"detached itinerary copied");
  copy.stops.get(0).name="修改";copy.stops.get(0).notePhotos.clear();copy.stops.get(0).tagNames.clear();
  check(stop.name.equals("西湖")&&stop.notePhotos.size()==1&&stop.tagNames.size()==1,"mutable stop fields detached");
  check(copy.companions.isEmpty()&&copy.expenses.isEmpty()&&copy.checkins.isEmpty(),"irrelevant private data excluded");
  return 4;
 }
 private static void check(boolean pass,String label){if(!pass)throw new AssertionError(label);}
}
