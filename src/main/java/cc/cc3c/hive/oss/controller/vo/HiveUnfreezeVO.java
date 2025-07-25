package cc.cc3c.hive.oss.controller.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class HiveUnfreezeVO {
    private Boolean unfrozen;
}
