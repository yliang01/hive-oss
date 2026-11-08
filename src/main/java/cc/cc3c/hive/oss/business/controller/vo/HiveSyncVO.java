package cc.cc3c.hive.oss.business.controller.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class HiveSyncVO {
    private String status;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private LocalDateTime syncedAt;
    private String message;
    private int ossTotal;
    private int ossOnly;
    private int ossToDbMatched;
    private int ossToDbMismatched;
    private int dbToOssMismatched;
    private int skipped;
}
