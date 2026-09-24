package cn.lvxu.travel;

public final class MapErrorsTest {
 public static void main(String[] args){
  int n=0;
  check(MapErrors.message("amap","10009").contains("高德地图")&&MapErrors.message("amap","10009").contains("平台类型不匹配")&&MapErrors.message("amap","10009").contains("SDK"),"amap platform mismatch");n++;
  check(MapErrors.message("tencent","199").contains("腾讯地图")&&MapErrors.message("tencent","199").contains("WebServiceAPI")&&MapErrors.message("tencent","199").contains("Web 服务类型 Key"),"tencent WebServiceAPI disabled");n++;
  check(MapErrors.message("baidu","240").contains("百度地图")&&MapErrors.message("baidu","240").contains("Web 服务不可用")&&MapErrors.message("baidu","240").contains("Android AK"),"baidu service unavailable");n++;
  check(MapErrors.success("amap","1")&&MapErrors.success("tencent","0")&&MapErrors.success("baidu","0"),"provider success codes");n++;
  String unknown=MapErrors.message("amap","99999");check(unknown.contains("高德地图")&&unknown.contains("99999")&&!unknown.contains("server detail"),"unknown code stays safe");n++;
  String failed=MapErrors.message("tencent","500");check(failed.contains("腾讯地图")&&failed.contains("500"),"business failure stays provider aware");n++;
  String unsafe=MapErrors.message("amap","server detail: secret-key");check(!unsafe.contains("server detail")&&unsafe.contains("未知"),"unsafe response code is not echoed");n++;
  System.out.println("PASS: "+n+" map error assertions");
 }
 static void check(boolean condition,String label){if(!condition)throw new AssertionError(label);}
}
