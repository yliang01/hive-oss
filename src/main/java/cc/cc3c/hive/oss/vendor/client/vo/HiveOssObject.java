package cc.cc3c.hive.oss.vendor.client.vo;

import lombok.Data;

import java.io.InputStream;
import java.util.Date;

@Data
public class HiveOssObject {
    private String fileKey;
    private long size;
    private Date lastModified;
    private String storageClass;
    private InputStream objectContent;
}
