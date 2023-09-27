package cc.cc3c.hive.oss.vendor.vo;

import lombok.Data;

@Data
public class HiveOssDownloadTask extends HiveOssTask {
    public HiveOssDownloadTask(HiveOssTask hiveOssTask) {
        super(hiveOssTask);
    }
}
