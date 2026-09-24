package cn.lvxu.travel;

import android.content.Context;
import android.content.ContextWrapper;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/** Tests a candidate connection through the actual WebDavService without external network access. */
final class WebDavCandidateTest {
 static int run(Context context) throws Exception {
  Context isolated = new ContextWrapper(context) {
   @Override public android.content.SharedPreferences getSharedPreferences(String name, int mode) {
    return super.getSharedPreferences("candidate-" + name, mode);
   }
  };
  Interceptor davSuccess = chain -> {
   Request request = chain.request();
   if (!"PROPFIND".equals(request.method())) throw new AssertionError("candidate must use PROPFIND");
   if (request.header("Authorization") == null) throw new AssertionError("candidate must authenticate");
   return new Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(207).message("Multi-Status")
    .body(ResponseBody.create("", null)).build();
  };
  WebDavService dav = new WebDavService(isolated, new OkHttpClient.Builder().addInterceptor(davSuccess).build());
  try {
   dav.configure("https://stored.example/backup/", "stored", "stored-password");
   dav.testCandidate("https://candidate.example/backup/", "candidate", "candidate-password");
   if (!"https://stored.example/backup/".equals(dav.endpoint())) throw new AssertionError("candidate test must not save credentials");
   return 1;
  } finally {
   dav.clear();
  }
 }
}
