package cc.cc3c.hive.oss.controller;

import cc.cc3c.hive.domain.entity.HiveRecord;
import cc.cc3c.hive.domain.model.HiveRestoreStatusState;
import cc.cc3c.hive.domain.repository.HiveRecordRepository;
import cc.cc3c.hive.oss.controller.vo.HiveUnfreezeStatusVO;
import cc.cc3c.hive.oss.service.FileCategoryResolver;
import cc.cc3c.hive.oss.service.FileGroupService;
import cc.cc3c.hive.oss.service.HiveOssService;
import cc.cc3c.hive.oss.vendor.vo.HiveOssTask;
import cc.cc3c.hive.oss.vendor.vo.HiveRestoreResult;
import cc.cc3c.hive.oss.vendor.vo.HiveRestoreStatus;
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
    private final FileGroupService fileGroupService;
    private final FileCategoryResolver fileCategoryResolver;
    private final HiveOssService hiveOssService;
    private final HiveRecordVOAssembler assembler;

    public HiveFileArchiveController(HiveRecordRepository hiveRecordRepository,
                                     FileGroupService fileGroupService,
                                     FileCategoryResolver fileCategoryResolver,
                                     HiveOssService hiveOssService,
                                     HiveRecordVOAssembler assembler) {
        this.hiveRecordRepository = hiveRecordRepository;
        this.fileGroupService = fileGroupService;
        this.fileCategoryResolver = fileCategoryResolver;
        this.hiveOssService = hiveOssService;
        this.assembler = assembler;
    }

    @PostMapping("/categories/{category}/files/unfreeze/{*fileKey}")
    public ResponseEntity<HiveUnfreezeStatusVO> unfreezeFiles(@PathVariable("category") String category,
                                                              @PathVariable("fileKey") String fileKey) {
        Optional<HiveRecord> optional = findArchiveRecord(category, fileKey);
        if (optional.isEmpty()) return ResponseEntity.notFound().build();
        HiveRecord hiveRecord = optional.get();
        if (!assembler.isArchiveStorageClass(fileCategoryResolver.resolveRecordStorageClass(hiveRecord))) return ResponseEntity.badRequest().build();

        fileKey = hiveRecord.getFileKey();
        HiveRestoreResult restoreResult = hiveOssService.using(hiveRecord.getProvider())
                .restoreCheck(HiveOssTask.createTask().withKey(fileKey).withBucket(hiveRecord.getBucketName()));
        if (HiveRestoreStatus.NOT_STARTED == restoreResult.getRestoreStatus()) {
            hiveOssService.using(hiveRecord.getProvider())
                    .restore(HiveOssTask.createTask().withKey(fileKey).withBucket(hiveRecord.getBucketName()));
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
    public ResponseEntity<HiveUnfreezeStatusVO> unfreezeState(@PathVariable("category") String category,
                                                              @PathVariable("fileKey") String fileKey) {
        Optional<HiveRecord> optional = findArchiveRecord(category, fileKey);
        if (optional.isEmpty()) return ResponseEntity.notFound().build();
        HiveRecord hiveRecord = optional.get();
        if (!assembler.isArchiveStorageClass(fileCategoryResolver.resolveRecordStorageClass(hiveRecord))) return ResponseEntity.badRequest().build();

        fileKey = hiveRecord.getFileKey();
        HiveRestoreResult restoreResult = hiveOssService.using(hiveRecord.getProvider())
                .restoreCheck(HiveOssTask.createTask().withKey(fileKey).withBucket(hiveRecord.getBucketName()));
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

    private Optional<HiveRecord> findArchiveRecord(String category, String fileKey) {
        String normalized = HiveRecordVOAssembler.normalizeFileKey(fileKey);
        return hiveRecordRepository.findByBucketNameAndFileKeyAndDeletedIsFalse(
                fileGroupService.resolveBucket(category), normalized);
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
        HiveRestoreStatusState status = hiveRecord.getRestoreStatus() == null
                ? HiveRestoreStatusState.NOT_STARTED : hiveRecord.getRestoreStatus();
        return HiveUnfreezeStatusVO.builder()
                .status(status.name())
                .unfreezeTime(hiveRecord.getRestoreTime())
                .build();
    }
}
