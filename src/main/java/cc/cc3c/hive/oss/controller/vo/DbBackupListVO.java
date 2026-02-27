package cc.cc3c.hive.oss.controller.vo;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
@Builder
public class DbBackupListVO {
    private List<DbBackupItemVO> items;
    private long total;

    @Data
    @Builder
    public static class DbBackupItemVO {
        private String batchId;
        private String database;
        private String status;
        private Instant createdAt;
    }
}
