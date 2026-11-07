package cc.cc3c.hive.oss.business.service;

public class SyncAlreadyRunningException extends RuntimeException {

    public SyncAlreadyRunningException(String categoryCode) {
        super("分类正在扫描云端，请稍后重试: " + categoryCode);
    }
}
