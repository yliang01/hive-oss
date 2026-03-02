package cc.cc3c.hive.oss.controller.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FileGroupVO {
    private Long groupId;
    private String categoryCode;
    private String groupCode;
    private String groupName;
    private String groupDesc;
    private Integer sortOrder;
    private Boolean enabled;
}
