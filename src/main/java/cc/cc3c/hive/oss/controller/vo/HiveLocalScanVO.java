package cc.cc3c.hive.oss.controller.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class HiveLocalScanVO {
    private String status;
    private LocalDateTime checkedAt;
    private String message;
    private int recordTotal;
    private int sizeMatched;
    private int sizeChanged;
    private int missing;
    private int baselineMissing;
    private int unverifiable;
}
