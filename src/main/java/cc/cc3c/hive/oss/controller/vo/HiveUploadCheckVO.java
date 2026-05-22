package cc.cc3c.hive.oss.controller.vo;

import cc.cc3c.hive.domain.model.HiveRecordStatus;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class HiveUploadCheckVO {
    private Boolean exists;
    private String fileKey;
    private HiveRecordStatus status;
}
