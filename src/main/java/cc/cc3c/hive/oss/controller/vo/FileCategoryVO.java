package cc.cc3c.hive.oss.controller.vo;

import lombok.Builder;
import lombok.Data;

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
}
