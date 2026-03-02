package cc.cc3c.hive.oss.controller.vo;

import lombok.Data;

@Data
public class GroupUpsertRequest {
    private String groupCode;
    private String groupName;
    private String groupDesc;
    private Integer sortOrder;
    private Boolean enabled;
}
