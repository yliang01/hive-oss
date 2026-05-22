package cc.cc3c.hive.oss.service;

import cc.cc3c.hive.oss.controller.vo.DbBackupAckVO;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class DbRestoreStatusTracker {

    private final ConcurrentMap<String, DbBackupAckVO> restoreAcks = new ConcurrentHashMap<>();

    public void markPending(String batchId, String message) {
        put(batchId, "PENDING", message);
    }

    public void markRestored(String batchId, String message) {
        put(batchId, "RESTORED", message);
    }

    public void markRestartNeeded(String batchId, String message) {
        put(batchId, "RESTART_NEEDED", message);
    }

    public void markFailed(String batchId, String message) {
        put(batchId, "FAILED", message);
    }

    public Optional<DbBackupAckVO> find(String batchId) {
        if (batchId == null || batchId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(restoreAcks.get(batchId));
    }

    private void put(String batchId, String status, String message) {
        if (batchId == null || batchId.isBlank()) {
            return;
        }
        restoreAcks.put(batchId, DbBackupAckVO.builder()
                .batchId(batchId)
                .status(status)
                .ackTime(Instant.now())
                .database(DbBackupManifestService.databaseFromBatchId(batchId))
                .message(message)
                .build());
    }

}
