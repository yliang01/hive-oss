package cc.cc3c.hive.oss.controller.vo;

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
