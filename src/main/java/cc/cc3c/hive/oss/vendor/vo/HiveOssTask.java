package cc.cc3c.hive.oss.vendor.vo;

import cc.cc3c.hive.domain.model.HiveRecordSource;
import cc.cc3c.hive.encryption.HiveEncryption;
import cc.cc3c.hive.encryption.HiveEncryptionConfig;
import cc.cc3c.hive.oss.vendor.client.alibaba.AlibabaOssConfig;
import cc.cc3c.hive.oss.vendor.client.tencent.TencentOssConfig;
import lombok.Data;

import java.io.File;

@Data
public class HiveOssTask {
    public static HiveEncryptionConfig encryptionConfig;
    public static AlibabaOssConfig alibabaOssConfig;
    public static TencentOssConfig tencentOssConfig;

    private HiveEncryption encryption;
    private String bucket;
    private String key;
    private File file;

    protected HiveOssTask() {
    }

    protected HiveOssTask(HiveOssTask hiveOssTask) {
        this.encryption = hiveOssTask.encryption;
        this.bucket = hiveOssTask.bucket;
        this.key = hiveOssTask.key;
        this.file = hiveOssTask.file;
    }

    public static HiveOssTask createTask() {
        return new HiveOssTask();
    }

    public HiveOssUploadTask toUploadTask() {
        return new HiveOssUploadTask(this);
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

    public HiveOssTask withKeyFile(String key, File file) {
        setKey(key);
        setFile(file);
        return this;
    }
}

