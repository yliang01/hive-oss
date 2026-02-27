package cc.cc3c.hive.oss.controller;

import cc.cc3c.hive.oss.controller.vo.DbBackupAckVO;
import cc.cc3c.hive.oss.controller.vo.DbBackupListVO;
import cc.cc3c.hive.oss.service.DbBackupOrchestrationService;
import cc.cc3c.hive.oss.service.DbBackupQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/db-backup")
@RequiredArgsConstructor
public class DbBackupController {

    private final DbBackupOrchestrationService orchestrationService;
    private final DbBackupQueryService dbBackupQueryService;

    @PostMapping("/backup")
    public ResponseEntity<DbBackupAckVO> backup() {
        String batchId = orchestrationService.startBackup();
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(DbBackupAckVO.builder()
                .batchId(batchId)
                .status("PENDING")
                .message("backup task accepted")
                .build());
    }

    @GetMapping("/ack/{batchId}")
    public ResponseEntity<DbBackupAckVO> getAck(@PathVariable("batchId") String batchId) {
        return dbBackupQueryService.getByBatchId(batchId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/backups")
    public DbBackupListVO listBackups() {
        return dbBackupQueryService.listBackups();
    }

    @PostMapping("/restore/{batchId}")
    public ResponseEntity<DbBackupAckVO> restore(@PathVariable("batchId") String batchId) {
        try {
            orchestrationService.startRestore(batchId);
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(DbBackupAckVO.builder()
                    .batchId(batchId)
                    .status("PENDING")
                    .message("restore task accepted")
                    .build());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(DbBackupAckVO.builder()
                    .batchId(batchId)
                    .status("FAILED")
                    .message(e.getMessage())
                    .build());
        }
    }
}
