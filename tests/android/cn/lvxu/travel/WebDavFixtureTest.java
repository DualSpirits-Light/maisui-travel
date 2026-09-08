package cn.lvxu.travel;
import android.content.*;
import java.io.*;
import java.security.*;
import java.security.cert.*;
import java.util.*;
import javax.net.ssl.*;
import okhttp3.OkHttpClient;

/** Emulator-only fixture with a caller-supplied pinned certificate. Not included in the app. */
final class WebDavFixtureTest {
 static int run(Context context,String certificateSha256)throws Exception {
  if(!certificateSha256.matches("[0-9A-Fa-f]{64}"))throw new IllegalArgumentException("Fixture certificate pin required");
  X509TrustManager trust=new X509TrustManager(){
   public X509Certificate[] getAcceptedIssuers(){return new X509Certificate[0];}
   public void checkClientTrusted(X509Certificate[] chain,String auth)throws CertificateException{throw new CertificateException();}
   public void checkServerTrusted(X509Certificate[] chain,String auth)throws CertificateException{try{if(chain.length<1)throw new Exception();byte[] hash=MessageDigest.getInstance("SHA-256").digest(chain[0].getEncoded());StringBuilder actual=new StringBuilder();for(byte b:hash)actual.append(String.format(Locale.ROOT,"%02x",b));if(!actual.toString().equalsIgnoreCase(certificateSha256))throw new Exception();chain[0].checkValidity();}catch(Exception e){throw new CertificateException("Fixture pin mismatch",e);}}
  };
  SSLContext tls=SSLContext.getInstance("TLS");tls.init(null,new TrustManager[]{trust},new SecureRandom());
  Context isolated=new ContextWrapper(context){@Override public SharedPreferences getSharedPreferences(String name,int mode){return super.getSharedPreferences("fixture-"+name,mode);}};
  OkHttpClient client=new OkHttpClient.Builder().sslSocketFactory(tls.getSocketFactory(),trust).hostnameVerifier((host,session)->host.equals("10.0.2.2")).followRedirects(false).build();
  WebDavService dav=new WebDavService(isolated,client);int checks=0;
  try{dav.configureAndTest("https://10.0.2.2:8449/backup/","test","test-pass");checks++;
   byte[] content="verified WebDAV archive contents".getBytes("UTF-8");dav.upload("integration.zip",new ByteArrayInputStream(content),content.length);checks++;
   if(!dav.list().contains("integration.zip"))throw new AssertionError("WebDAV list missing upload");checks++;
   ByteArrayOutputStream out=new ByteArrayOutputStream();dav.download("integration.zip",out);if(!Arrays.equals(content,out.toByteArray()))throw new AssertionError("WebDAV download differs");checks++;
   boolean rejected=false;try{dav.configureAndTest("https://10.0.2.2:8449/backup/","test","wrong-password");}catch(Exception expected){rejected=true;}if(!rejected)throw new AssertionError("Invalid credentials accepted");checks++;dav.test();checks++;
   rejected=false;try{dav.download("../escape.zip",new ByteArrayOutputStream());}catch(Exception expected){rejected=true;}if(!rejected)throw new AssertionError("Remote path traversal accepted");checks++;
   return checks;
  }finally{dav.clear();client.dispatcher().executorService().shutdown();client.connectionPool().evictAll();}
 }
}
