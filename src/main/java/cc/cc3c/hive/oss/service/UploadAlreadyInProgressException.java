package cc.cc3c.hive.oss.service;

public class UploadAlreadyInProgressException extends RuntimeException {

    public UploadAlreadyInProgressException(String fileName) {
        super("同名文件正在上传，请稍后重试: " + fileName);
    }
}
