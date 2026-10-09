package cn.lvxu.travel;

/** Administrative scope. Coordinates are GCJ-02; an empty city means the whole province. */
final class RegionSelection {
 final String province,city,district,adcode,level; final double lat,lon;
 RegionSelection(String province,String city,String district,String adcode,String level,double lat,double lon){this.province=clean(province);this.city=clean(city);this.district=clean(district);this.adcode=clean(adcode);this.level=clean(level);this.lat=lat;this.lon=lon;}
 static String clean(String s){return s==null?"":s.trim();}
 static RegionSelection nationwide(){return new RegionSelection("","","","100000","country",35,105);}
 String query(){return !adcode.isEmpty()?adcode:keyword(province,city,district);}
 static String keyword(String province,String city,String district){String p=clean(province),c=clean(city),d=clean(district);return !d.isEmpty()?p+(p.equals(c)?"":c)+d:!c.isEmpty()?c:p;}
 String label(){return province.isEmpty()?"全国":!district.isEmpty()?district:!city.isEmpty()?city:province;}
 boolean hasCenter(){return Double.isFinite(lat)&&Double.isFinite(lon)&&Math.abs(lat)<=90&&Math.abs(lon)<=180;}
 float zoom(){return "country".equals(level)?5:"province".equals(level)?8:"city".equals(level)?12:14;}
 boolean sameArea(RegionSelection other){return other!=null&&province.equals(other.province)&&city.equals(other.city)&&district.equals(other.district);}
}
