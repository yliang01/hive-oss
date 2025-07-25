package cc.cc3c.hive.oss.controller.vo;

import cc.cc3c.hive.domain.model.HiveDownloadStatus;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class HiveDownloadStatusVO {
    private HiveDownloadStatus status;
    private int progress;
}
