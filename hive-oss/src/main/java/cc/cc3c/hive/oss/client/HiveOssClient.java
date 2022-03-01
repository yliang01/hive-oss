package cc.cc3c.hive.oss.client;

import cc.cc3c.hive.oss.client.vo.HiveOssPartUploadResult;
import cc.cc3c.hive.oss.client.vo.HiveOssObject;
import cc.cc3c.hive.oss.vo.HiveOssTask;
import cc.cc3c.hive.oss.vo.HiveOssUploadTask;

import java.io.InputStream;
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
