package cc.cc3c.hive.oss.controller.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class HiveRecordVO {
    private String fileName;
    private String fileKey;
    private Boolean zipped;
    private Long size;
    private String status;
    private LocalDateTime updateTime;
    private LocalDateTime unfreezeTime;
    private Boolean restorable;
    private Boolean downloadable;
    private String localPath;
    private Boolean localPathExists;
    private Boolean deletable;
}
