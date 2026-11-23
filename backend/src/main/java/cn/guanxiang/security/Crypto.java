package cn.guanxiang.security;

import cn.guanxiang.common.Problem;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

@Component
public class Crypto {
    private final byte[] key;
    private static final SecureRandom RANDOM = new SecureRandom();
    public Crypto(@Value("${app.master-key}") String master) {
        try { key=Base64.getDecoder().decode(master); } catch(Exception e) { throw new IllegalStateException("MASTER_KEY must be base64"); }
        if(key.length!=32) throw new IllegalStateException("MASTER_KEY must contain 32 random bytes");
    }
    public String encrypt(String text) {
        try {
            byte[] iv = new byte[12]; RANDOM.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,iv));
            byte[] encrypted=c.doFinal(text.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(iv)+":"+Base64.getEncoder().encodeToString(encrypted);
        } catch(Exception e) { throw new IllegalStateException("Encryption failed"); }
    }
    public String decrypt(String value) {
        try {
            String[] parts=value.split(":"); Cipher c=Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,Base64.getDecoder().decode(parts[0])));
            return new String(c.doFinal(Base64.getDecoder().decode(parts[1])),StandardCharsets.UTF_8);
        } catch(Exception e) { throw new Problem(503,"KEY_UNAVAILABLE","无法解密 API 密钥，请检查主密钥或重新配置"); }
    }
    public static String token() { byte[] b=new byte[32]; RANDOM.nextBytes(b); return HexFormat.of().formatHex(b); }
    public static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch(Exception e) { throw new IllegalStateException(); }
    }
}
