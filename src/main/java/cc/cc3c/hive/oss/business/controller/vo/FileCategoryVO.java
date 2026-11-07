package cc.cc3c.hive.oss.business.controller.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class FileCategoryVO {
    private Long id;
    private String code;
    private String name;
    private String bucketName;
    private String storageClass;
    private String uiVariant;
    private Boolean enabled;
    private Integer sortOrder;
    private LocalDateTime lastSyncTime;
    private String lastSyncStatus;
    private Integer lastSyncOssTotal;
    private Integer lastSyncOssOnly;
    private Integer lastSyncOssToDbMatched;
    private Integer lastSyncOssToDbMismatched;
    private Integer lastSyncDbToOssMismatched;
    private Integer lastSyncSkipped;
    private String lastSyncMessage;
}
