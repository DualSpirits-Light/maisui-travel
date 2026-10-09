package cn.lvxu.travel;
public final class SearchCityScopeTest {
 public static int run(){int n=0;
  check("兰州".equals(SearchCityScope.require(" 兰州 ")),"trim without guessing city");n++;
  check(SearchCityScope.matches("兰州","兰州市"),"city suffix matches");n++;
  check(!SearchCityScope.matches("兰州","杭州")&&!SearchCityScope.matches("兰州","兰州新区")&&!SearchCityScope.matches("兰州",null),"outside and missing city rejected");n++;
  for(String city:new String[]{"","全国","兰州、杭州","兰州,杭州","兰州/杭州","兰州→杭州"}){boolean rejected=false;try{SearchCityScope.require(city);}catch(IllegalArgumentException expected){rejected=true;}check(rejected,"ambiguous scope rejected");n++;}
  check(SearchCityScope.matches("甘肃省兰州市","兰州市","甘肃省")&&!SearchCityScope.matches("青海省兰州市","兰州市","甘肃省"),"province prefix must exactly match provider administrative fields");n++;
  check("甘肃省兰州市".equals(SearchCityScope.require("甘肃省兰州市")),"free-form input not rewritten");return n+1;
 }
 private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 public static void main(String[] args){System.out.println("PASS: "+run()+" search city assertions");}
}
