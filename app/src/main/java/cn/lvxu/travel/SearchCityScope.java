package cn.lvxu.travel;

/** Never guess a search city from a free-form, potentially multi-city destination. */
final class SearchCityScope {
 static String require(String value) {
  String city=value==null?"":value.trim();
  if(city.isEmpty()||city.matches(".*[,，、;；/／|→\\n\\r].*")||"全国".equals(city)||"中国".equals(city))
   throw new IllegalArgumentException("请先将旅行目的地设为一个明确城市，再搜索地点");
  return city;
 }
 static boolean matches(String requested,String returned) {
  if(returned==null||returned.trim().isEmpty())return false;
  return name(requested).equals(name(returned));
 }
 static boolean matches(String requested,String returned,String province) {
  return matches(requested,returned)||(province!=null&&!province.trim().isEmpty()&&returned!=null&&!returned.trim().isEmpty()&&name(requested).equals(province.trim()+name(returned)));
 }
 private static String name(String value) {
  String clean=value==null?"":value.trim();
  return clean.endsWith("市")?clean.substring(0,clean.length()-1):clean;
 }
}
