package cc.cc3c.hive.oss.vendor.vo;

import cc.cc3c.hive.domain.model.HiveRecordSource;
import cc.cc3c.hive.encryption.HiveEncryption;
import cc.cc3c.hive.encryption.HiveEncryptionConfig;
import cc.cc3c.hive.oss.vendor.client.alibaba.AlibabaOssConfig;
import cc.cc3c.hive.oss.vendor.client.tencent.TencentOssConfig;
import lombok.Data;
import lombok.ToString;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Data
@ToString
public class HiveOssTask {
    public static HiveEncryptionConfig encryptionConfig;
    public static AlibabaOssConfig alibabaOssConfig;
    public static TencentOssConfig tencentOssConfig;

    private HiveEncryption encryption;
    @ToString.Include
    private String bucket;
    @ToString.Include
    private String key;

    private File file;

    private String uploadId;
    private AtomicInteger currentPart = new AtomicInteger(0);
    private Map<Integer, String> uploadedMap = new ConcurrentHashMap<>();

    private int progress;

    public static HiveOssTask createTask() {
        return new HiveOssTask();
    }

    public boolean isEncrypted() {
        return encryption != null;
    }

    public HiveOssTask withBucket(HiveRecordSource source) {
        if (source == HiveRecordSource.ALIBABA_ACHIEVE) {
            setBucket(alibabaOssConfig.getArchiveBucket());
        } else if (source == HiveRecordSource.ALIBABA_STANDARD) {
            setBucket(alibabaOssConfig.getStandardBucket());
        } else {
            throw new IllegalArgumentException("bad record source");
        }
        return this;
    }

    public HiveOssTask withTencent() {
        setBucket(tencentOssConfig.getBucket());
        return this;
    }

    public HiveOssTask withEncryption(String fileName) throws Exception {
        setEncryption(new HiveEncryption(encryptionConfig, fileName));
        return this;
    }

    public HiveOssTask withKey(String key) {
        setKey(key);
        return this;
    }

    public HiveOssTask withFile(File file) {
        setFile(file);
        return this;
    }
}

