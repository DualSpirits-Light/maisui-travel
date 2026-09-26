package cn.lvxu.travel;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;
import java.security.MessageDigest;
import java.util.Locale;

/** Registration details from this installed APK, never a build-time certificate assumption. */
final class AmapIdentity {
    static String description(Context context) {
        String prefix="应用包名："+context.getPackageName()+"\n安装证书 SHA-1：\n";
        try {
            PackageInfo info=context.getPackageManager().getPackageInfo(context.getPackageName(),
                    Build.VERSION.SDK_INT>=28?PackageManager.GET_SIGNING_CERTIFICATES:PackageManager.GET_SIGNATURES);
            Signature[] signers=Build.VERSION.SDK_INT>=28?info.signingInfo.getApkContentsSigners():info.signatures;
            StringBuilder result=new StringBuilder(prefix);
            if(signers==null||signers.length==0)throw new IllegalStateException();
            for(Signature signer:signers){
                if(result.length()>prefix.length())result.append('\n');
                byte[] hash=MessageDigest.getInstance("SHA-1").digest(signer.toByteArray());
                for(int i=0;i<hash.length;i++){if(i>0)result.append(':');result.append(String.format(Locale.ROOT,"%02X",hash[i]&255));}
            }
            return result.toString();
        } catch(Exception error){return prefix+"读取失败，请稍后重试。";}
    }
}
