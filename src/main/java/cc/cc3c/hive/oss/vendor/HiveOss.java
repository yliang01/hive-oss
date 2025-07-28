package cc.cc3c.hive.oss.vendor;

import cc.cc3c.hive.oss.vendor.client.vo.HiveOssObject;
import cc.cc3c.hive.oss.vendor.vo.HiveOssTask;
import cc.cc3c.hive.oss.vendor.vo.HiveRestoreResult;

import java.util.List;

public interface HiveOss {

    boolean doesObjectExist(HiveOssTask task);

    List<HiveOssObject> listObjects(HiveOssTask task) throws Exception;

    void uploadSync(HiveOssTask task) throws Exception;

    void upload(HiveOssTask task) throws Exception;

    HiveRestoreResult restoreCheck(HiveOssTask task);

    void restore(HiveOssTask task);

    void download(HiveOssTask task) throws Exception;

    void delete(HiveOssTask task) throws Exception;
}
