package cc.cc3c.hive.oss.business.controller.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class HiveUnfreezeStatusVO {
    private String status;
    private LocalDateTime unfreezeTime;
}
