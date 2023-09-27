package cc.cc3c.hive.oss.vendor.encryption;

import lombok.Getter;
import org.apache.commons.codec.digest.DigestUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Getter
public class HiveOssEncryption {
    private Cipher encryptCipher;
    private Cipher decryptCipher;

    private final HiveOssEncryptionConfig encryptionConfig;

    public HiveOssEncryption(HiveOssEncryptionConfig encryptionConfig, String fileName) throws Exception {
        this.encryptionConfig = encryptionConfig;
        initCipher(encryptionConfig.getPassword(), fileName);
    }

    private void initCipher(String pwd, String fileName) throws Exception {
        SecretKeySpec secretKey = new SecretKeySpec(createKey(pwd), encryptionConfig.getKeyAlgorithm());

        encryptCipher = Cipher.getInstance(encryptionConfig.getCipherAlgorithm());
        encryptCipher.init(Cipher.ENCRYPT_MODE, secretKey, new IvParameterSpec(createIVParameter(fileName)));

        decryptCipher = Cipher.getInstance(encryptionConfig.getCipherAlgorithm());
        decryptCipher.init(Cipher.DECRYPT_MODE, secretKey, new IvParameterSpec(createIVParameter(fileName)));
    }

    private byte[] createKey(String pwd) {
        return shaWithSaltX65536(DigestUtils.getSha256Digest(), 32, pwd);
    }

    private byte[] createIVParameter(String IV) {
        return shaWithSaltX65536(DigestUtils.getMd5Digest(), 16, IV);
    }

    private byte[] shaWithSaltX65536(MessageDigest messageDigest, int byteLength, String data) {
        byte[] buffer = new byte[byteLength * 2];
        byte[] salt = messageDigest.digest(encryptionConfig.getSalt().getBytes(StandardCharsets.UTF_8));
        byte[] sha = messageDigest.digest(data.getBytes(StandardCharsets.UTF_8));

        int total = 1 << 16;
        for (int i = 0; i < total; i++) {
            System.arraycopy(sha, 0, buffer, 0, byteLength);
            System.arraycopy(salt, 0, buffer, byteLength, byteLength);
            sha = messageDigest.digest(buffer);
        }
        return sha;
    }
}
