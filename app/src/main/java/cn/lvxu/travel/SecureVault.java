package cn.lvxu.travel;

import android.content.*;
import android.security.keystore.*;
import android.util.Base64;
import java.security.*;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;

/** Small encrypted credential store backed by a non-exportable Android Keystore key. */
public final class SecureVault {
    private static final String ALIAS="lvxu.webdav.v1", TRANSFORM="AES/GCM/NoPadding";
    private final SharedPreferences p;
    public SecureVault(Context c){p=c.getSharedPreferences("secure-vault",Context.MODE_PRIVATE);}
    private SecretKey key()throws Exception {KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);if(!ks.containsAlias(ALIAS)){KeyGenerator g=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");g.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setRandomizedEncryptionRequired(true).build());g.generateKey();}return (SecretKey)ks.getKey(ALIAS,null);}
    public synchronized void put(String name,String value)throws Exception {Cipher c=Cipher.getInstance(TRANSFORM);c.init(Cipher.ENCRYPT_MODE,key());byte[] enc=c.doFinal(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));p.edit().putString(name,Base64.encodeToString(c.getIV(),Base64.NO_WRAP)+"."+Base64.encodeToString(enc,Base64.NO_WRAP)).commit();}
    public synchronized String get(String name)throws Exception {String raw=p.getString(name,"");if(raw.isEmpty())return "";String[] q=raw.split("\\.",2);Cipher c=Cipher.getInstance(TRANSFORM);c.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(q[0],Base64.NO_WRAP)));return new String(c.doFinal(Base64.decode(q[1],Base64.NO_WRAP)),java.nio.charset.StandardCharsets.UTF_8);}
    public void clear(){p.edit().clear().apply();}
}
