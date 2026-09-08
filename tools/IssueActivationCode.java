import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.time.LocalDate;

/** Offline developer utility. Compile/run outside the Android project; never copy the private key into it. */
public final class IssueActivationCode {
    public static void main(String[] args)throws Exception {
        if(args.length<2||args.length>3)throw new IllegalArgumentException("Usage: IssueActivationCode private-key-pkcs8.der subject [expires-yyyy-mm-dd]");
        if(args[1].trim().isEmpty()||args[1].length()>120)throw new IllegalArgumentException("Subject must contain 1-120 characters");
        if(args.length==3)LocalDate.parse(args[2]);
        PrivateKey key=KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(Files.readAllBytes(Paths.get(args[0]))));
        String json="{\"product\":\"maisui-travel\",\"subject\":\""+escape(args[1])+"\""+(args.length==3?",\"expires\":\""+escape(args[2])+"\"":"")+"}";
        byte[] body=json.getBytes(StandardCharsets.UTF_8);Signature signature=Signature.getInstance("SHA256withRSA");signature.initSign(key);signature.update(body);Base64.Encoder b64=Base64.getUrlEncoder().withoutPadding();System.out.println("LVX1."+b64.encodeToString(body)+"."+b64.encodeToString(signature.sign()));
    }
    private static String escape(String s){StringBuilder out=new StringBuilder();for(int i=0;i<s.length();i++){char c=s.charAt(i);if(c=='"'||c=='\\')out.append('\\').append(c);else if(c<32)throw new IllegalArgumentException("Control characters are not allowed");else out.append(c);}return out.toString();}
}
