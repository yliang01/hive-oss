package cc.cc3c.hive.oss.vendor.vo;

import cc.cc3c.hive.oss.vendor.client.alibaba.AlibabaOssConfig;
import cc.cc3c.hive.oss.vendor.client.tencent.TencentOssConfig;
import cc.cc3c.hive.oss.vendor.encryption.HiveOssEncryption;
import cc.cc3c.hive.oss.vendor.encryption.HiveOssEncryptionConfig;
import lombok.Data;

import java.io.File;

@Data
public class HiveOssTask {
    public static HiveOssEncryptionConfig encryptionConfig;
    public static AlibabaOssConfig alibabaOssConfig;
    public static TencentOssConfig tencentOssConfig;

    private HiveOssEncryption encryption;
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

    public HiveOssDownloadTask toDownloadTask() {
        return new HiveOssDownloadTask(this);
    }

    public boolean isEncrypted() {
        return encryption != null;
    }

    public HiveOssTask withAlibabaStandard() {
        setBucket(alibabaOssConfig.getStandardBucket());
        return this;
    }

    public HiveOssTask withAlibabaAchieve() {
        setBucket(alibabaOssConfig.getAchieveBucket());
        return this;
    }

    public HiveOssTask withTencent() {
        setBucket(tencentOssConfig.getBucket());
        return this;
    }

    public HiveOssTask withEncryption(String fileName) throws Exception {
        setEncryption(new HiveOssEncryption(encryptionConfig, fileName));
        return this;
    }

    public HiveOssTask withKeyFile(String key, File file) {
        setKey(key);
        setFile(file);
        return this;
    }
}

