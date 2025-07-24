package cc.cc3c.hive.oss.vendor;

import cc.cc3c.hive.oss.vendor.client.vo.HiveOssObject;
import cc.cc3c.hive.oss.vendor.vo.HiveOssTask;
import cc.cc3c.hive.oss.vendor.vo.HiveOssUploadTask;

import java.util.List;

public interface HiveOss {

    boolean doesObjectExist(HiveOssTask task);

    List<HiveOssObject> listObjects(HiveOssTask task) throws Exception;

    void upload(HiveOssUploadTask task) throws Exception;

    void restore(HiveOssTask task) throws Exception;

    void download(HiveOssTask task) throws Exception;

    void delete(HiveOssTask task);
}
