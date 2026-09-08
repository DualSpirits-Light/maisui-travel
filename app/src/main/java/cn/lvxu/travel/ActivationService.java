package cn.lvxu.travel;

import android.util.Base64;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.*;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/** Verifies developer-issued offline licenses. The APK contains only the public key. */
public final class ActivationService {
    // Matching private key is retained outside the repository and never packaged in the APK.
    public static final String PUBLIC_KEY_DER_BASE64="MIIBojANBgkqhkiG9w0BAQEFAAOCAY8AMIIBigKCAYEAs9QdZvFgtvkG17d4lndUGfTQikEBG3X+8yBwNZTMiPh72zPWGS3TJe9Yhi4HPbnPdi4hJy/OKu5HSYNRnLkzUEHphYLoROmGo8VdGltGt4DW2Iu0nq7MwU29zkstPrK1YpBk1HMobwc4/i8TEteTtr1s+gGGLmff5qTzbzYoFYZCtFtLjx1O56AW8bQyGiYzNH5xr38x9rw0ihydgA0UtIkfm5Uv3WWfNb4GARzEWXd58DSlDoN878p+2AZkiv9uY9cK2hAdO20/j45V86MMrOZCyYzn9FWyafAPVumjZuqdNIF1Zc3o65RQSV5vrgbSwyJWfnT/cvbz2CmnqfrOO1Hh2QjGiqiCnO6iaxMYRBmWtuSC2mtzU1ILaQYHsMs+wpLFECUDx/nEMmlnhLsIzZ/e8S9dRxwyOxxvxEEVDdSZpPe7jZJlLToE9bzfcjujP6pEGIPKf3La33qAXa5CPdPiCmTQIhLxCDorDRaMWx3HX1RrZZ/diiR2M/NGOQx5AgMBAAE=";
    private final AppPrefs prefs;
    public ActivationService(AppPrefs p){prefs=p;}
    public String activate(String code)throws Exception {code=code==null?"":code.trim();if(code.length()>12000)throw new IllegalArgumentException("激活码过长");String[] parts=code.split("\\.",-1);if(parts.length!=3||!"LVX1".equals(parts[0])||parts[1].isEmpty()||parts[2].isEmpty())throw new IllegalArgumentException("激活码格式无效");byte[] payload,sig;try{payload=Base64.decode(parts[1],Base64.URL_SAFE|Base64.NO_WRAP|Base64.NO_PADDING);sig=Base64.decode(parts[2],Base64.URL_SAFE|Base64.NO_WRAP|Base64.NO_PADDING);}catch(Exception e){throw new IllegalArgumentException("激活码格式无效");}if(payload.length>4096||sig.length>1024)throw new IllegalArgumentException("激活码内容过长");Signature v=Signature.getInstance("SHA256withRSA");v.initVerify(publicKey());v.update(payload);if(!v.verify(sig))throw new IllegalArgumentException("激活码签名无效");JSONObject o=new JSONObject(new String(payload,StandardCharsets.UTF_8));if(!"maisui-travel".equals(o.getString("product")))throw new IllegalArgumentException("激活码不适用于此应用");String subject=o.getString("subject").trim();if(subject.isEmpty()||subject.length()>120)throw new IllegalArgumentException("激活信息无效");String expires=o.optString("expires","").trim();try{if(!expires.isEmpty()&&LocalDate.parse(expires).isBefore(LocalDate.now()))throw new IllegalArgumentException("激活码已过期");}catch(DateTimeParseException e){throw new IllegalArgumentException("激活码有效期无效");}prefs.setActivation(subject,expires);return subject;}
    private static PublicKey publicKey()throws Exception{return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Base64.decode(PUBLIC_KEY_DER_BASE64,Base64.DEFAULT)));}
    /** Developer-side helper. Call only from an offline utility supplied with a private PKCS#8 key. */
    public static String issue(JSONObject payload,PrivateKey privateKey)throws Exception {byte[] body=payload.toString().getBytes(StandardCharsets.UTF_8);Signature s=Signature.getInstance("SHA256withRSA");s.initSign(privateKey);s.update(body);return "LVX1."+Base64.encodeToString(body,Base64.URL_SAFE|Base64.NO_WRAP|Base64.NO_PADDING)+"."+Base64.encodeToString(s.sign(),Base64.URL_SAFE|Base64.NO_WRAP|Base64.NO_PADDING);}
}
