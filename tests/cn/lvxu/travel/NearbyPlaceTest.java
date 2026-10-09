package cn.lvxu.travel;
import java.util.*;
import org.json.*;
public final class NearbyPlaceTest {
 private static int count;private static void check(boolean yes,String label){if(!yes)throw new AssertionError(label);count++;}
 public static int run()throws Exception{count=0;NearbyPlace a=new NearbyPlace("a","博物馆","地址","兰州","博物馆",36.06,103.81,4.8f),b=new NearbyPlace("b","公园","地址","兰州","风景",36.08,103.80,4.2f),unknown=new NearbyPlace("c","商场","地址","兰州","逛街",36.09,103.80,null);
  check(NearbyPlace.sorted(Arrays.asList(b,a,unknown),"评分",null).get(0)==a,"rating desc");check(NearbyPlace.sorted(Arrays.asList(unknown,b,a),"评分",null).get(2)==unknown,"missing score last");
  a.heat=5;a.heatAt=System.currentTimeMillis();b.heat=1;b.heatAt=a.heatAt;
  check(NearbyPlace.sorted(Arrays.asList(unknown,a,b),"拥挤程度",null).get(0)==b,"low heat first");check(NearbyPlace.sorted(Arrays.asList(unknown,a,b),"拥挤程度",null).get(2)==unknown,"unknown heat last");
  b.heatAt=System.currentTimeMillis()-1_860_000L;check(!b.freshHeat()&&b.hasHeat()&&b.staleHeat(),"old visual reference retained and marked stale");
  b.heatAt=System.currentTimeMillis()-7*24*60*60_000L-1;check(!b.hasHeat(),"reference beyond retention rejected");b.heatAt=System.currentTimeMillis()+60_000;check(!b.hasHeat()&&!b.freshHeat(),"future reference rejected");
  double[] current=GeoMath.wgs(a.lat,a.lon,"GCJ02");check(a.distance(current)<.1,"distance origin current WGS converted");check(NearbyPlace.sorted(Arrays.asList(b,a),"距离",current).get(0)==a,"distance from actual position");check(Double.isInfinite(a.distance(null)),"no location unknown distance");
  NearbyPlace saved=NearbyPlace.from(a.json());check(saved.heat==0&&saved.heatAt==0,"favorites never persist live heat");check(saved.rating.equals(a.rating)&&saved.city.equals("兰州"),"POI snapshot retains rating/city");
  check(new NearbyPlace("q","未知","","兰州","",36,104,Float.NaN).rating==null,"invalid rating unknown");
  boolean reject=false;try{new NearbyPlace("q","坏坐标","","","",91,104,null);}catch(IllegalArgumentException e){reject=true;}check(reject,"invalid latitude rejected");return count;
 }
 public static void main(String[] args)throws Exception{System.out.println("PASS: "+run()+" nearby place assertions");}
}
