package cc.cc3c.hive.oss.business.controller.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class HiveRecordVO {
    private String groupCode;
    private String groupName;
    private String fileName;
    private String fileKey;
    private Boolean zipped;
    private Long size;
    private Long originalSize;
    private String status;
    private LocalDateTime updateTime;
    private LocalDateTime unfreezeTime;
    private String unfreezeStatus;
    private Boolean restorable;
    private Boolean downloadable;
    private String downloadStatus;
    private Integer downloadProgress;
    private Boolean localFileAvailable;
    private String localCheckStatus;
    private Long localCheckObservedSize;
    private Boolean deletable;
    private Boolean previewable;
    private String previewUrl;
    private String mimeType;
    private String previewBlockedReason;
    /** URL to load a ready thumbnail image. Falls back to previewUrl if absent. */
    private String thumbnailUrl;
}
