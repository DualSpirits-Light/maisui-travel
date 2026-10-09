package cn.lvxu.travel;
import java.io.*;
import java.util.concurrent.TimeUnit;
import okhttp3.*;
import org.json.JSONObject;
final class MeituanTravelService {
 interface Credentials {JSONObject get()throws Exception;}
 private static final OkHttpClient CLIENT=new OkHttpClient.Builder().connectTimeout(15,TimeUnit.SECONDS)
  .readTimeout(135,TimeUnit.SECONDS).callTimeout(145,TimeUnit.SECONDS).retryOnConnectionFailure(false)
  .followRedirects(false).followSslRedirects(false).build();
 private final OkHttpClient client;private final Credentials credentials;
 MeituanTravelService(CloudLicenseService cloud){this(CLIENT,cloud::travelCredentials);}
 MeituanTravelService(OkHttpClient client,Credentials credentials){this.client=client;this.credentials=credentials;}
 Call newQuery(String city,String query)throws Exception{
  JSONObject body=MeituanTravelContract.request(city,query,credentials.get());
  return client.newCall(new Request.Builder().url(ApiHttp.url(LicenseConfig.ENDPOINT+"/v1/travel/query"))
   .post(RequestBody.create(MediaType.get("application/json; charset=utf-8"),body.toString())).build());
 }
 static String execute(Call call)throws Exception{
  try(Response r=call.execute()){
   if(r.body()==null)throw new IOException("服务没有返回内容，请稍后重试");
   ByteArrayOutputStream out=new ByteArrayOutputStream();
   try(InputStream in=r.body().byteStream()){byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){if(out.size()+n>512*1024)throw new IOException("结果过长，请缩小查询范围");out.write(buffer,0,n);}}
   JSONObject body;try{body=new JSONObject(out.toString("UTF-8"));}catch(Exception e){throw new IOException("服务返回异常，请稍后重试");}
   if(!r.isSuccessful()){JSONObject error=body.optJSONObject("error");throw new IOException(MeituanTravelContract.error(r.code(),error==null?"":error.optString("code","")));}
   return MeituanTravelContract.content(body);
  }catch(java.net.SocketTimeoutException e){throw new IOException("美团查询超时，请稍后重试");}
 }
}
