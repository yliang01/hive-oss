package cc.cc3c.hive.oss.controller.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class HiveSyncVO {
    private int ossTotal;
    private int ossOnly;
    private int ossToDbMatched;
    private int ossToDbMismatched;
    private int dbToOssMismatched;
}
