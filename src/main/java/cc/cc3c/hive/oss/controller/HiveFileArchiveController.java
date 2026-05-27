package cc.cc3c.hive.oss.controller;

import cc.cc3c.hive.domain.entity.HiveRecord;
import cc.cc3c.hive.domain.model.CategoryStorageClass;
import cc.cc3c.hive.domain.model.HiveRestoreStatusState;
import cc.cc3c.hive.domain.repository.HiveRecordRepository;
import cc.cc3c.hive.oss.controller.vo.HiveUnfreezeStatusVO;
import cc.cc3c.hive.oss.service.HiveOssService;
import cc.cc3c.hive.oss.vendor.vo.HiveOssTask;
import cc.cc3c.hive.oss.vendor.vo.HiveRestoreResult;
import cc.cc3c.hive.oss.vendor.vo.HiveRestoreStatus;
import cc.cc3c.hive.oss.web.CategoryContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.ZoneId;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.Optional;

@RestController
public class HiveFileArchiveController {

    private final HiveRecordRepository hiveRecordRepository;
    private final HiveOssService hiveOssService;

    public HiveFileArchiveController(HiveRecordRepository hiveRecordRepository,
                                      HiveOssService hiveOssService) {
        this.hiveRecordRepository = hiveRecordRepository;
        this.hiveOssService = hiveOssService;
    }

    @PostMapping("/categories/{category}/files/unfreeze/{*fileKey}")
    public ResponseEntity<HiveUnfreezeStatusVO> unfreezeFiles(CategoryContext category,
                                                              @PathVariable("fileKey") String fileKey) {
        Optional<HiveRecord> optional = findArchiveRecord(category, fileKey);
        if (optional.isEmpty()) return ResponseEntity.notFound().build();
        HiveRecord hiveRecord = optional.get();
        if (category.storageClass() != CategoryStorageClass.ARCHIVE) return ResponseEntity.badRequest().build();

        fileKey = hiveRecord.getFileKey();
        HiveRestoreResult restoreResult = hiveOssService.using(hiveRecord.getProvider())
                .restoreCheck(HiveOssTask.createTask().withKey(fileKey).withBucket(category.bucketName()));
        if (HiveRestoreStatus.NOT_STARTED == restoreResult.getRestoreStatus()) {
            hiveOssService.using(hiveRecord.getProvider())
                    .restore(HiveOssTask.createTask().withKey(fileKey).withBucket(category.bucketName()));
            markRestoreStatus(hiveRecord, HiveRestoreStatusState.IN_PROGRESS, true, null);
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(buildStatus(hiveRecord));
        } else if (HiveRestoreStatus.IN_PROGRESS == restoreResult.getRestoreStatus()) {
            markRestoreStatus(hiveRecord, HiveRestoreStatusState.IN_PROGRESS, false, null);
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(buildStatus(hiveRecord));
        } else if (HiveRestoreStatus.COMPLETED == restoreResult.getRestoreStatus()) {
            markRestoreStatus(hiveRecord, HiveRestoreStatusState.COMPLETED, false, restoreResult);
            return ResponseEntity.ok(buildStatus(hiveRecord));
        }
        return ResponseEntity.internalServerError().build();
    }

    @GetMapping("/categories/{category}/files/unfreeze-status/{*fileKey}")
    public ResponseEntity<HiveUnfreezeStatusVO> unfreezeState(CategoryContext category,
                                                               @PathVariable("fileKey") String fileKey) {
        Optional<HiveRecord> optional = findArchiveRecord(category, fileKey);
        if (optional.isEmpty()) return ResponseEntity.notFound().build();
        HiveRecord hiveRecord = optional.get();
        if (category.storageClass() != CategoryStorageClass.ARCHIVE) return ResponseEntity.badRequest().build();

        fileKey = hiveRecord.getFileKey();
        HiveRestoreResult restoreResult = hiveOssService.using(hiveRecord.getProvider())
                .restoreCheck(HiveOssTask.createTask().withKey(fileKey).withBucket(category.bucketName()));
        if (HiveRestoreStatus.NOT_STARTED == restoreResult.getRestoreStatus()) {
            markRestoreStatus(hiveRecord, HiveRestoreStatusState.NOT_STARTED, false, null);
            return ResponseEntity.ok(buildStatus(hiveRecord));
        } else if (HiveRestoreStatus.IN_PROGRESS == restoreResult.getRestoreStatus()) {
            markRestoreStatus(hiveRecord, HiveRestoreStatusState.IN_PROGRESS, false, null);
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(buildStatus(hiveRecord));
        } else if (HiveRestoreStatus.COMPLETED == restoreResult.getRestoreStatus()) {
            markRestoreStatus(hiveRecord, HiveRestoreStatusState.COMPLETED, false, restoreResult);
            return ResponseEntity.ok(buildStatus(hiveRecord));
        }
        return ResponseEntity.internalServerError().build();
    }

    private Optional<HiveRecord> findArchiveRecord(CategoryContext category, String fileKey) {
        String normalized = HiveRecordVOAssembler.normalizeFileKey(fileKey);
        return hiveRecordRepository.findByCategory_IdAndFileKeyAndDeletedIsFalse(
                category.categoryId(), normalized);
    }

    private void markRestoreStatus(HiveRecord hiveRecord, HiveRestoreStatusState status,
                                   boolean requested, HiveRestoreResult restoreResult) {
        hiveRecord.setRestoreStatus(status);
        if (requested) hiveRecord.setRestoreRequestedAt(LocalDateTime.now());
        hiveRecord.setRestoreStatusUpdatedAt(LocalDateTime.now());
        if (status == HiveRestoreStatusState.COMPLETED && restoreResult != null && restoreResult.getExpiryDate() != null) {
            ZonedDateTime converted = restoreResult.getExpiryDate().withZoneSameInstant(ZoneId.systemDefault());
            hiveRecord.setRestoreTime(converted.toLocalDateTime());
        }
        hiveRecordRepository.save(hiveRecord);
    }

    private HiveUnfreezeStatusVO buildStatus(HiveRecord hiveRecord) {
        return HiveUnfreezeStatusVO.builder()
                .status(hiveRecord.getRestoreStatus().name())
                .unfreezeTime(hiveRecord.getRestoreTime())
                .build();
    }
}
