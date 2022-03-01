package cc.cc3c.hive.oss.vo;

import lombok.Data;

@Data
public class HiveOssDownloadTask extends HiveOssTask {
    public HiveOssDownloadTask(HiveOssTask hiveOssTask) {
        super(hiveOssTask);
    }
}
