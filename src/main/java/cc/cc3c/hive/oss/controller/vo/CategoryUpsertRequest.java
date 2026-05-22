package cc.cc3c.hive.oss.controller.vo;

import lombok.Data;

@Data
public class CategoryUpsertRequest {
    private String code;
    private String name;
    private String bucketName;
    private String storageClass;
    private String uiVariant;
    private Boolean enabled;
    private Integer sortOrder;
}
