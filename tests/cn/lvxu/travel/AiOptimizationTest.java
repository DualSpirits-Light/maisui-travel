package cn.lvxu.travel;

public final class AiOptimizationTest {
 static int count;
 static void check(boolean b,String label){if(!b)throw new AssertionError(label);count++;}
 static void reject(Runnable r,String label){try{r.run();}catch(IllegalArgumentException e){count++;return;}throw new AssertionError(label);}
 public static void main(String[] args)throws Exception{
  Trip t=Trip.demo();String id=t.stops.get(0).id;
  String json="{\"suggestions\":[{\"stopId\":\""+id+"\",\"reason\":\"避开早高峰\",\"changes\":{\"time\":\"10:00\"}}]}";
  t.stops.get(0).timeLocked=true;reject(()->AiOptimization.parse(json,t),"locked appointment time must not be changed by AI");t.stops.get(0).timeLocked=false;
  AiOptimization p=AiOptimization.parse(json,t);
  check(AiOptimization.parse("{\"suggestions\":[]}",t).suggestions.isEmpty(),"no changes is valid");
  check(p.suggestions.size()==1,"reads suggestion");
  reject(()->p.apply(t,java.util.Collections.emptySet()),"requires explicit selection");
  Trip result=p.apply(t,java.util.Collections.singleton(id));
  check(result.stops.get(0).time.equals("10:00"),"selected field changed");
  check(t.stops.get(0).time.equals("09:00"),"source unchanged");
  check(result.stops.get(1).time.equals(t.stops.get(1).time),"unselected stop preserved");
  check(result.stops.get(0).lat.equals(t.stops.get(0).lat),"time change keeps coordinates");
  reject(()->AiOptimization.parse(json.replace(id,"unknown"),t),"unknown target rejected");
  reject(()->AiOptimization.parse(json.replace("\"time\":\"10:00\"","\"id\":\"other\""),t),"unsupported changes rejected");
  reject(()->AiOptimization.parse(json.replace("10:00","25:00"),t),"invalid time rejected");
  reject(()->AiOptimization.parse(json.replace("\"time\":\"10:00\"","\"day\":60"),t),"invalid day rejected");
  t.stops.get(0).note="用户刚修改";
  reject(()->p.apply(t,java.util.Collections.singleton(id)),"stale proposal rejected");
  t.stops.get(0).previewPhoto="photos/user.jpg";t.stops.get(0).notePhotos.add("photos/note.jpg");
  AiOptimization moved=AiOptimization.parse(json.replace("\"time\":\"10:00\"","\"name\":\"新地点\""),t);
  Trip relocation=moved.apply(t,java.util.Collections.singleton(id));
  check(relocation.stops.get(0).lat==null&&relocation.stops.get(0).address.isEmpty(),"changed place clears stale location evidence");
  check(relocation.stops.get(0).id.equals(id),"stop identity preserved");
  check(relocation.stops.get(0).previewPhoto.equals("photos/user.jpg")&&relocation.stops.get(0).notePhotos.size()==1,"user media retained");
  check(!AiOptimization.prompt(t).contains("photos/user.jpg"),"prompt omits media paths");
  Trip duplicate=Trip.from(t.json());duplicate.stops.get(1).id=duplicate.stops.get(0).id;
  reject(()->AiOptimization.parse(json,duplicate),"ambiguous source id rejected");
  AiOptimization beforeDuplicate=AiOptimization.parse(json,t);t.stops.get(1).id=id;
  reject(()->beforeDuplicate.apply(t,java.util.Collections.singleton(id)),"new duplicate id rejected before apply");
  System.out.println("PASS: "+count+" AI optimization assertions");
 }
}
