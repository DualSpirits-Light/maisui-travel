package cn.lvxu.travel;

import android.content.Context;
import android.content.pm.PackageInfo;
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Controlled-stream checks for update route selection, cleanup, and APK integrity. */
final class UpdateDownloadEngineTest {
    private UpdateDownloadEngineTest() {}

    static int run(Context context) throws Exception {
        int checks=0;
        checks+=manifestProvidesBothTrustedRoutes(context);
        checks+=github404IsNotAnError(context);
        checks+=githubProxyBodyTimeoutFallsBackToDirect(context);
        checks+=githubAssetRedirectAllowsSignedCdn(context);
        checks+=streamFailureRemovesPartialFile(context);
        checks+=activeCancellationInterruptsDownload(context);
        checks+=persistedApkHashIsCheckedBeforeInstallation(context);
        return checks;
    }

    private static int manifestProvidesBothTrustedRoutes(Context context) throws Exception {
        byte[] manifest=("{\"versionCode\":999,\"versionName\":\"9.9.9\",\"packageName\":\""+context.getPackageName()+"\",\"notes\":\"routes\",\"githubApkUrl\":\"https://github.com/DualSpirits-Light/maisui-travel/releases/download/v9/app.apk\",\"cloudflareApkUrl\":\"https://license.zjm0929.cn/updates/app.apk\",\"sha256\":\""+sixtyFourZeros()+"\"}").getBytes("UTF-8");
        UpdateService service=service(context,new ReplyFactory().reply(UpdateService.CLOUDFLARE_MANIFEST,new BytesReply(manifest)));
        UpdateService.Release release=service.check();
        check(release.state==UpdateService.State.AVAILABLE,"Cloudflare metadata produces an available release");
        check("https://github.com/DualSpirits-Light/maisui-travel/releases/download/v9/app.apk".equals(release.url(UpdateService.Route.GITHUB)),"manifest retains GitHub route");
        check("https://license.zjm0929.cn/updates/app.apk".equals(release.url(UpdateService.Route.CLOUDFLARE)),"manifest retains Cloudflare route");
        JSONObject legacyManifest=new JSONObject(new String(manifest,"UTF-8"));
        legacyManifest.put("apkUrl","https://github.com/DualSpirits-Light/maisui-travel/releases/download/v9/app.apk");
        legacyManifest.remove("githubApkUrl");legacyManifest.remove("cloudflareApkUrl");
        UpdateService.Release legacy=service.releaseFromManifest(legacyManifest);
        check(legacy.hasRoute(UpdateService.Route.GITHUB),"legacy apkUrl remains a GitHub route");
        check(!legacy.hasRoute(UpdateService.Route.CLOUDFLARE),"a missing Cloudflare route is not guessed");
        return 5;
    }

    private static int github404IsNotAnError(Context context) throws Exception {
        ReplyFactory replies=new ReplyFactory().reply(UpdateService.CLOUDFLARE_MANIFEST,new IOException("offline"));
        replies.reply(UpdateService.githubProxyUrl(UpdateService.GITHUB_LATEST_RELEASE),new StatusReply(404));
        replies.reply(UpdateService.GITHUB_LATEST_RELEASE,new StatusReply(404));
        UpdateService.Release release=service(context,replies).check();
        check(release.state==UpdateService.State.NOT_AVAILABLE,"deleted GitHub releases are reported as unavailable");
        return 1;
    }

    private static int githubProxyBodyTimeoutFallsBackToDirect(Context context) throws Exception {
        ReplyFactory replies=new ReplyFactory()
                .reply(UpdateService.CLOUDFLARE_MANIFEST,new IOException("offline"))
                .reply(UpdateService.githubProxyUrl(UpdateService.GITHUB_LATEST_RELEASE),new BytesReply(new TimeoutInputStream()))
                .reply(UpdateService.GITHUB_LATEST_RELEASE,new StatusReply(404));
        UpdateService.Release release=service(context,replies).check();
        check(release.state==UpdateService.State.NOT_AVAILABLE,"a GitHub proxy body timeout falls back to the direct source");
        check(replies.seen.contains(UpdateService.GITHUB_LATEST_RELEASE),"direct GitHub is requested after a proxy body timeout");
        return 2;
    }

    private static int githubAssetRedirectAllowsSignedCdn(Context context) throws Exception {
        String github="https://github.com/DualSpirits-Light/maisui-travel/releases/download/v9/app.apk";
        String cdn="https://release-assets.githubusercontent.com/12345/app.apk?token=test";
        byte[] invalidApk="not an apk".getBytes("UTF-8");
        UpdateService.Release release=new UpdateService.Release(UpdateService.State.AVAILABLE,999,"test","",github,"",sha256(invalidApk));
        ReplyFactory replies=new ReplyFactory().reply(UpdateService.githubProxyUrl(github),new RedirectReply(cdn));
        replies.reply(cdn,new BytesReply(invalidApk));
        boolean packageRejected=false;
        try{service(context,replies).downloadAndVerify(release,UpdateService.Route.GITHUB,null,new UpdateService.Cancellation());}
        catch(IOException expected){packageRejected=expected.getMessage()!=null&&expected.getMessage().contains("应用标识");}
        check(replies.seen.contains(cdn),"GitHub's signed asset CDN redirect is followed");
        check(packageRejected,"the redirected response still receives real APK verification");
        check(!part(context,release).exists(),"failed redirected APK leaves no partial file");
        return 3;
    }

    private static int streamFailureRemovesPartialFile(Context context) throws Exception {
        UpdateService.Release release=release("https://license.zjm0929.cn/updates/broken.apk",sha256("abc".getBytes("UTF-8")));
        UpdateService service=service(context,new ReplyFactory().reply(release.url(UpdateService.Route.CLOUDFLARE),new BytesReply(new FailingInputStream())));
        boolean failed=false;
        try { service.downloadAndVerify(release,UpdateService.Route.CLOUDFLARE,null,new UpdateService.Cancellation()); }
        catch(IOException expected) { failed=true; }
        check(failed,"a broken response fails the download");
        check(!part(context,release).exists(),"a broken response removes its partial APK");
        return 2;
    }

    private static int activeCancellationInterruptsDownload(Context context) throws Exception {
        BlockingReply reply=new BlockingReply();
        String direct="https://github.com/DualSpirits-Light/maisui-travel/releases/download/v9/cancel.apk";
        String proxy=UpdateService.githubProxyUrl(direct);
        UpdateService.Release release=new UpdateService.Release(UpdateService.State.AVAILABLE,999,"test","",direct,"",sixtyFourZeros());
        ReplyFactory replies=new ReplyFactory().reply(proxy,reply);
        UpdateService service=service(context,replies);
        UpdateService.Cancellation cancellation=new UpdateService.Cancellation();
        Throwable[] failure=new Throwable[1];
        Thread work=new Thread(()->{try{service.downloadAndVerify(release,UpdateService.Route.GITHUB,null,cancellation);}catch(Throwable error){failure[0]=error;}},"update-download-test");
        work.start();
        check(reply.started.await(2,TimeUnit.SECONDS),"download reaches the active response stream");
        cancellation.cancel();
        work.join(2000);
        check(!work.isAlive()&&failure[0] instanceof InterruptedIOException,"cancelling an active download interrupts it promptly");
        check(!replies.seen.contains(direct),"a cancelled GitHub proxy download does not retry the direct source");
        check(!part(context,release).exists(),"cancelling removes its partial APK");
        return 4;
    }

    private static int persistedApkHashIsCheckedBeforeInstallation(Context context) throws Exception {
        PackageInfo current=context.getPackageManager().getPackageInfo(context.getPackageName(),0);
        int version=android.os.Build.VERSION.SDK_INT>=28?(int)current.getLongVersionCode():current.versionCode;
        File installed=new File(context.getApplicationInfo().sourceDir);
        UpdateService service=new UpdateService(context,new AppPrefs(context));
        UpdateService.Release bad=new UpdateService.Release(UpdateService.State.UP_TO_DATE,version,"installed","",installed.toURI().toString(),sixtyFourZeros());
        boolean rejected=false;
        try { service.verifyDownloaded(installed,bad); } catch(IOException expected) { rejected=true; }
        check(rejected,"persisted APK with the wrong hash is rejected before installation");
        return 1;
    }

    private static UpdateService service(Context c,UpdateService.ConnectionFactory replies) { return new UpdateService(c,new AppPrefs(c),replies); }
    private static UpdateService.Release release(String cloudflareUrl,String hash) { return new UpdateService.Release(UpdateService.State.AVAILABLE,999,"test","","",cloudflareUrl,hash); }
    private static File part(Context c,UpdateService.Release r) { return new File(new File(c.getCacheDir(),"updates"),"maisui-"+r.versionCode+".apk.part"); }
    private static String sixtyFourZeros(){return "0000000000000000000000000000000000000000000000000000000000000000";}
    private static String sha256(File file)throws Exception {try(InputStream in=new FileInputStream(file)){return sha256(in);}}
    private static String sha256(byte[] value)throws Exception{return sha256(new ByteArrayInputStream(value));}
    private static String sha256(InputStream in)throws Exception {MessageDigest digest=MessageDigest.getInstance("SHA-256");byte[] bytes=new byte[8192];for(int n;(n=in.read(bytes))!=-1;)digest.update(bytes,0,n);StringBuilder output=new StringBuilder();for(byte b:digest.digest())output.append(String.format(Locale.ROOT,"%02x",b));return output.toString();}
    private static void check(boolean value,String label){if(!value)throw new AssertionError(label);}

    private static final class ReplyFactory implements UpdateService.ConnectionFactory {
        private final Map<String,Object> replies=new HashMap<>();
        final List<String> seen=new ArrayList<>();
        ReplyFactory reply(String url,Object reply){replies.put(url,reply);return this;}
        @Override public HttpURLConnection open(URL url)throws IOException {seen.add(url.toString());Object value=replies.get(url.toString());if(value instanceof IOException)throw (IOException)value;if(value instanceof HttpURLConnection)return (HttpURLConnection)value;throw new IOException("unexpected URL "+url);}
    }
    private static class BytesReply extends HttpURLConnection {
        protected final InputStream input;private final int code;
        BytesReply(byte[] bytes)throws Exception{this(new ByteArrayInputStream(bytes),200);}
        BytesReply(InputStream input){this(input,200);}
        BytesReply(InputStream input,int code){super(null);this.input=input;this.code=code;}
        @Override public int getResponseCode(){return code;} @Override public InputStream getInputStream(){return input;} @Override public long getContentLengthLong(){return -1;} @Override public void disconnect(){try{input.close();}catch(IOException ignored){}} @Override public boolean usingProxy(){return false;} @Override public void connect(){}
    }
    private static final class StatusReply extends BytesReply { StatusReply(int status){super(new ByteArrayInputStream(new byte[0]),status);} }
    private static final class RedirectReply extends BytesReply {private final String location;RedirectReply(String location){super(new ByteArrayInputStream(new byte[0]),302);this.location=location;}@Override public String getHeaderField(String name){return "Location".equalsIgnoreCase(name)?location:null;}}
    private static final class FailingInputStream extends InputStream { private boolean first=true; @Override public int read(){if(first){first=false;return 1;}throw new RuntimeException(new IOException("stream failed"));} @Override public int read(byte[] b,int off,int len)throws IOException {if(first){first=false;b[off]=1;return 1;}throw new IOException("stream failed");} }
    private static final class BlockingReply extends BytesReply {
        final CountDownLatch started;
        BlockingReply(){super(new BlockingInputStream());started=input().started;}
        @Override public InputStream getInputStream(){return input();}
        private BlockingInputStream input(){return (BlockingInputStream)super.input;}
    }
    private static final class BlockingInputStream extends InputStream {
        final CountDownLatch started=new CountDownLatch(1);private volatile boolean closed;
        @Override public int read()throws IOException {started.countDown();while(!closed){try{Thread.sleep(10);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new InterruptedIOException("interrupted");}}throw new InterruptedIOException("cancelled");}
        @Override public void close(){closed=true;}
    }
    private static final class TimeoutInputStream extends InputStream {
        @Override public int read()throws IOException {throw new SocketTimeoutException("proxy response timed out");}
        @Override public int read(byte[] buffer,int offset,int length)throws IOException {throw new SocketTimeoutException("proxy response timed out");}
    }
}
