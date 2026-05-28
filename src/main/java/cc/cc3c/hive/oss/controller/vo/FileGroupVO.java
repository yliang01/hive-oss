package cc.cc3c.hive.oss.controller.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FileGroupVO {
    private String categoryCode;
    private String groupCode;
    private String groupName;
    private Integer sortOrder;
}
