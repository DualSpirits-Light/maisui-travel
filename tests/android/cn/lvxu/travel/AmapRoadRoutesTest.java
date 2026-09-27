package cn.lvxu.travel;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.*;

/** Offline parser and cancellable worker checks; no service credentials or network. */
final class AmapRoadRoutesTest {
 static int run()throws Exception {
  int n=0;
  Trip.Stop a=stop("A","步行"),b=stop("B","自驾"),c=stop("C","公交");
  List<AmapRoadRoutes.Segment> segments=AmapRoadRoutes.snapshot(Arrays.asList(a,b,c));
  n+=check(segments.get(0).mode==AmapRoadRoutes.Mode.DRIVE&&segments.get(1).mode==AmapRoadRoutes.Mode.TRANSIT,"arrival modes");
  b.lat=null;List<AmapRoadRoutes.Segment> missing=AmapRoadRoutes.snapshot(Arrays.asList(a,b,c));
  n+=check(missing.size()==2&&!missing.get(0).unavailable.isEmpty()&&!missing.get(1).unavailable.isEmpty(),"missing middle never bridges");
  n+=check(Double.isFinite(segments.get(0).toLat),"snapshot independent of later edits");
  c.coordinateSystem="unknown";n+=check(AmapRoadRoutes.coordinate(c)==null,"unknown coordinate system");
  n+=check(!AmapRoadRoutes.valid(Double.NaN,120d)&&!AmapRoadRoutes.valid(91d,120d),"invalid coordinates");
  n+=check(AmapRoadRoutes.mode("火车")==AmapRoadRoutes.Mode.UNSUPPORTED&&AmapRoadRoutes.mode("地铁")==AmapRoadRoutes.Mode.TRANSIT,"unsupported and subway modes");
  ArrayList<Trip.Stop> many=new ArrayList<>();for(int i=0;i<26;i++)many.add(stop("P"+i,"步行"));
  n+=check(AmapRoadRoutes.snapshot(many).size()==25,"beyond twenty retained for later batches");
  AmapRoadRoutes.Result road=AmapRoadRoutes.parse(road("120,30;120.01,30.01"),AmapRoadRoutes.Mode.WALK);
  n+=check(road.meters==100&&road.seconds==60&&road.lines.size()==1&&!road.partial,"walk geometry and metrics");
  n+=check(AmapRoadRoutes.parse(road("120,30;120.01,30.01"),AmapRoadRoutes.Mode.DRIVE).lines.size()==1,"drive geometry");
  AmapRoadRoutes.Result broken=AmapRoadRoutes.parse(road("120,30;120.01,30.01;bad;121,31;121.01,31.01"),AmapRoadRoutes.Mode.WALK);
  n+=check(broken.partial&&broken.lines.size()==2&&broken.lines.get(0).size()==2,"malformed coordinate splits lines");
  JSONObject transit=new JSONObject("{\"status\":\"1\",\"route\":{\"transits\":[{\"distance\":\"100\",\"duration\":\"60\",\"segments\":[{\"walking\":{\"steps\":[{\"polyline\":\"120,30;120.01,30.01\"}]},\"bus\":{\"buslines\":[{\"polyline\":\"120.02,30.02;120.03,30.03\"}]}}]}]}}");
  n+=check(AmapRoadRoutes.parse(transit,AmapRoadRoutes.Mode.TRANSIT).lines.size()==2,"transit walking and bus stay separate");
  JSONObject noPath=new JSONObject("{\"status\":\"1\",\"route\":{\"paths\":[]}}");
  try{AmapRoadRoutes.parse(noPath,AmapRoadRoutes.Mode.WALK);throw new AssertionError("empty route accepted");}catch(java.io.IOException expected){n++;}
  try{AmapRoadRoutes.parse(new JSONObject("{\"status\":\"0\",\"infocode\":\"10001\"}"),AmapRoadRoutes.Mode.WALK);throw new AssertionError("API rejection accepted");}catch(java.io.IOException expected){n++;}
  CountDownLatch done=new CountDownLatch(2);AtomicInteger good=new AtomicInteger(),bad=new AtomicInteger();
  AmapRouteSession mixed=new AmapRouteSession(s->{if(s.index==0)throw new java.io.IOException("private transport details");return road;});
  mixed.start(segments,(s,r,e)->{if(r!=null)good.incrementAndGet();else if(!e.contains("private"))bad.incrementAndGet();done.countDown();});
  n+=check(done.await(5,TimeUnit.SECONDS)&&good.get()==1&&bad.get()==1,"one failure does not stop later route or leak error");
  CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1),exited=new CountDownLatch(1);AtomicInteger callbacks=new AtomicInteger();
  AmapRouteSession cancelled=new AmapRouteSession(s->{entered.countDown();try{release.await(5,TimeUnit.SECONDS);}finally{exited.countDown();}return road;});
  cancelled.start(segments,(s,r,e)->callbacks.incrementAndGet());
  n+=check(entered.await(5,TimeUnit.SECONDS),"worker starts");cancelled.cancel();release.countDown();
  n+=check(exited.await(5,TimeUnit.SECONDS),"cancel interrupts active work");
  // Drain both executor workers using a separate batch after cancellation.
  CountDownLatch drained=new CountDownLatch(1);new AmapRouteSession(s->road).start(segments.subList(0,1),(s,r,e)->drained.countDown());
  n+=check(drained.await(5,TimeUnit.SECONDS)&&callbacks.get()==0,"cancelled work cannot deliver stale result");
  CountDownLatch retried=new CountDownLatch(1);new AmapRouteSession(s->road).start(segments.subList(0,1),(s,r,e)->{if(r!=null)retried.countDown();});
  n+=check(retried.await(5,TimeUnit.SECONDS),"failed segment can be retried in new session");
  try{new AmapRouteSession(s->road).start(AmapRoadRoutes.snapshot(many),(s,r,e)->{});throw new AssertionError("unbounded batch");}catch(IllegalArgumentException expected){n++;}
  ArrayList<Trip.Stop> retryStops=new ArrayList<>();for(int i=0;i<46;i++)retryStops.add(stop("R"+i,"步行"));
  List<AmapRoadRoutes.Segment> retrySegments=AmapRoadRoutes.snapshot(retryStops);
  Set<Integer> failed=new HashSet<>(),visited=new HashSet<>();for(int i=0;i<45;i++)failed.add(i);
  AmapRoadRoutes.RetryCursor cursor=new AmapRoadRoutes.RetryCursor();
  List<AmapRoadRoutes.Segment> first=cursor.batch(retrySegments,failed),second=cursor.batch(retrySegments,failed),third=cursor.batch(retrySegments,failed);
  n+=check(first.size()==20&&first.get(0).index==0&&second.size()==20&&second.get(0).index==20,"retry advances despite persistent first-page failures");
  for(AmapRoadRoutes.Segment s:first)visited.add(s.index);for(AmapRoadRoutes.Segment s:second)visited.add(s.index);for(AmapRoadRoutes.Segment s:third)visited.add(s.index);
  Set<Integer> unique=new HashSet<>();for(AmapRoadRoutes.Segment s:third)unique.add(s.index);
  n+=check(visited.size()==45&&third.size()==20&&unique.size()==20,"retry reaches every failure and wraps without duplicates");
  failed.clear();failed.add(44);List<AmapRoadRoutes.Segment> sparse=cursor.batch(retrySegments,failed);
  n+=check(sparse.size()==1&&sparse.get(0).index==44,"retry skips recovered segments");
  n+=check(cursor.batch(Collections.emptyList(),failed).isEmpty(),"empty retry list");
  return n;
 }
 private static Trip.Stop stop(String name,String mode){Trip.Stop s=new Trip.Stop();s.name=name;s.mode=mode;s.lat=30d;s.lon=120d;s.coordinateSystem="GCJ02";return s;}
 private static JSONObject road(String line)throws Exception{return new JSONObject().put("status","1").put("route",new JSONObject().put("paths",new JSONArray().put(new JSONObject().put("distance","100").put("duration","60").put("steps",new JSONArray().put(new JSONObject().put("polyline",line))))));}
 private static int check(boolean ok,String label){if(!ok)throw new AssertionError(label);return 1;}
}
