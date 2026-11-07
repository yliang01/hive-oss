package cc.cc3c.hive.oss.business.controller.vo;

import lombok.Data;

@Data
public class GroupUpsertRequest {
    private String groupCode;
    private String groupName;
    private Integer sortOrder;
}
