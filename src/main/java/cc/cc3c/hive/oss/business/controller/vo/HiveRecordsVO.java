package cc.cc3c.hive.oss.business.controller.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class HiveRecordsVO {
    private List<HiveRecordVO> files;
    private Integer total;
    private Integer page;
    private Integer pageSize;
}
