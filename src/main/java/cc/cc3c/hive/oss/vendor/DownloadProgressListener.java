package cc.cc3c.hive.oss.vendor;

import cc.cc3c.hive.oss.vendor.vo.HiveOssTask;
import com.aliyun.oss.event.ProgressEvent;
import com.aliyun.oss.event.ProgressEventType;
import com.aliyun.oss.event.ProgressListener;

public class DownloadProgressListener implements ProgressListener {
    private long bytesRead = 0;
    private long totalBytes = -1;
    private boolean succeed = false;

    private final HiveOssTask task;

    public DownloadProgressListener(HiveOssTask task) {
        this.task = task;
    }

    @Override
    public void progressChanged(ProgressEvent progressEvent) {
        ProgressEventType eventType = progressEvent.getEventType();
        switch (eventType) {
            case TRANSFER_STARTED_EVENT:
                System.out.println("开始下载...");
                break;
            case RESPONSE_CONTENT_LENGTH_EVENT:
                totalBytes = progressEvent.getBytes();
                System.out.println("总大小: " + totalBytes + " 字节");
                break;
            case RESPONSE_BYTE_TRANSFER_EVENT:
                bytesRead += progressEvent.getBytes();
                if (totalBytes != -1) {
                    int percent = (int) (100.0 * bytesRead / totalBytes);
                    System.out.print("\r已下载: " + bytesRead + " / " + totalBytes + " 字节 (" + percent + "%)");
                    task.setProgress(percent);
                } else {
                    System.out.print("\r已下载: " + bytesRead + " 字节");
                }
                break;
            case TRANSFER_COMPLETED_EVENT:
                succeed = true;
                System.out.println("\n下载完成。");
                break;
            case TRANSFER_FAILED_EVENT:
                System.out.println("\n下载失败！");
                break;
            default:
                break;
        }
    }
}