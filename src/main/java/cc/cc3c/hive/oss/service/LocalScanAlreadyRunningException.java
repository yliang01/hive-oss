package cc.cc3c.hive.oss.service;

public class LocalScanAlreadyRunningException extends RuntimeException {

    public LocalScanAlreadyRunningException(String categoryCode) {
        super("分类正在扫描本地目录，请稍后重试: " + categoryCode);
    }
}
