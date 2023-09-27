package cc.cc3c.hive.oss.vendor.client;

import cc.cc3c.hive.oss.vendor.client.vo.HiveOssPartUploadResult;
import cc.cc3c.hive.oss.vendor.client.vo.HiveOssObject;
import cc.cc3c.hive.oss.vendor.vo.HiveOssTask;
import cc.cc3c.hive.oss.vendor.vo.HiveOssUploadTask;

import java.util.List;

public interface HiveOssClient {

    String initiateMultipartUpload(HiveOssUploadTask task);

    String getExistingMultipartUploadId(HiveOssUploadTask task);

    void listParts(HiveOssUploadTask task);

    HiveOssPartUploadResult uploadPart(HiveOssUploadTask task, byte[] buffer, int read, int part);

    void completeMultipartUpload(HiveOssUploadTask task);

    void restore(HiveOssTask task);

    boolean isRestored(HiveOssTask task);

    HiveOssObject getObject(HiveOssTask task);

    List<HiveOssObject> listObject(HiveOssTask task);

    void deleteObject(HiveOssTask task);
}
