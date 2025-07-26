package cc.cc3c.hive.oss.vendor.client;

import cc.cc3c.hive.oss.vendor.client.vo.HiveOssObject;
import cc.cc3c.hive.oss.vendor.client.vo.HiveOssPartUploadResult;
import cc.cc3c.hive.oss.vendor.vo.HiveOssTask;
import cc.cc3c.hive.oss.vendor.vo.HiveRestoreResult;

import java.util.List;

public interface HiveOssClient {

    String initiateMultipartUpload(HiveOssTask task);

    String getExistingMultipartUploadId(HiveOssTask task);

    void listParts(HiveOssTask task);

    HiveOssPartUploadResult uploadPart(HiveOssTask task, byte[] buffer, int read, int part);

    void completeMultipartUpload(HiveOssTask task);

    void restore(HiveOssTask task);

    HiveRestoreResult restoreCheck(HiveOssTask task);

    boolean doesObjectExist(HiveOssTask task);

    HiveOssObject getObject(HiveOssTask task);

    List<HiveOssObject> listObject(HiveOssTask task);

    void deleteObject(HiveOssTask task);
}
