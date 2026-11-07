package cc.cc3c.hive.oss.business.controller.vo;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class DbBackupAckVO {
    private String batchId;
    private String status;
    private Instant ackTime;
    private String database;
    private String message;
}
