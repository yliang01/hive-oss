package cc.cc3c.hive.oss.vendor.vo;

import cc.cc3c.hive.domain.model.HiveRecordSource;
import cc.cc3c.hive.encryption.HiveEncryption;
import cc.cc3c.hive.encryption.HiveEncryptionConfig;
import cc.cc3c.hive.oss.vendor.client.alibaba.AlibabaOssConfig;
import cc.cc3c.hive.oss.vendor.client.tencent.TencentOssConfig;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class HiveOssTask {
    public static HiveEncryptionConfig encryptionConfig;
    public static AlibabaOssConfig alibabaOssConfig;
    public static TencentOssConfig tencentOssConfig;

    private HiveEncryption encryption;
    @Getter
    @ToString.Include
    private String bucket;
    @Getter
    @ToString.Include
    private String key;

    private InputStream inputStream;
    private OutputStream outputStream;

    @Getter
    @Setter
    private String uploadId;
    @Getter
    private AtomicInteger currentPart = new AtomicInteger(0);
    @Getter
    @Setter
    private Map<Integer, String> uploadedMap = new ConcurrentHashMap<>();
    @Getter
    @Setter
    private int progress;

    public static HiveOssTask createTask() {
        return new HiveOssTask();
    }

    public boolean isEncrypted() {
        return encryption != null;
    }

    public HiveOssTask withBucket(HiveRecordSource source) {
        if (source == HiveRecordSource.ALIBABA_ACHIEVE) {
            this.bucket = alibabaOssConfig.getArchiveBucket();
        } else if (source == HiveRecordSource.ALIBABA_STANDARD) {
            this.bucket = alibabaOssConfig.getStandardBucket();
        } else {
            throw new IllegalArgumentException("bad record source");
        }
        return this;
    }

    public HiveOssTask withEncryption(String fileName) throws Exception {
        this.encryption = new HiveEncryption(encryptionConfig, fileName.getBytes(StandardCharsets.UTF_8));
        return this;
    }

    public HiveOssTask withKey(String key) {
        this.key = key;
        return this;
    }

    public HiveOssTask withInputStream(InputStream inputStream) {
        this.inputStream = inputStream;
        return this;
    }

    public HiveOssTask withOutputStream(OutputStream outputStream) {
        this.outputStream = outputStream;
        return this;
    }


    public InputStream getInputStream() {
        if (this.isEncrypted()) {
            return new CipherInputStream(inputStream, encryption.getEncryptCipher());
        } else {
            return inputStream;
        }
    }

    public OutputStream getOutputStream() {
        if (this.isEncrypted()) {
            return new CipherOutputStream(outputStream, encryption.getDecryptCipher());
        } else {
            return outputStream;
        }
    }
}

