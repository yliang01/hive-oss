package cc.cc3c.hive.oss.business.controller.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class HiveLocalReuploadStatusVO {
    private String taskId;
    private String fileKey;
    private String status;
    private String message;
}
