package cc.cc3c.hive.oss.controller;

import cc.cc3c.hive.domain.model.CategoryStorageClass;
import cc.cc3c.hive.domain.model.HiveRestoreStatusState;
import cc.cc3c.hive.domain.repository.HiveRecordRepository;
import cc.cc3c.hive.oss.controller.vo.HiveUnfreezeStatusVO;
import cc.cc3c.hive.oss.service.HiveOssService;
import cc.cc3c.hive.oss.vendor.vo.HiveOssTask;
import cc.cc3c.hive.oss.vendor.vo.HiveRestoreResult;
import cc.cc3c.hive.oss.vendor.vo.HiveRestoreStatus;
import cc.cc3c.hive.oss.web.CategoryFileContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZoneId;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;

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
    public ResponseEntity<HiveUnfreezeStatusVO> unfreezeFiles(CategoryFileContext context) {
        if (context.category().storageClass() != CategoryStorageClass.ARCHIVE) return ResponseEntity.badRequest().build();

        HiveRestoreResult restoreResult = hiveOssService.using(context.provider())
                .restoreCheck(HiveOssTask.createTask().withKey(context.key()).withBucket(context.category().bucketName()));
        if (HiveRestoreStatus.NOT_STARTED == restoreResult.getRestoreStatus()) {
            hiveOssService.using(context.provider())
                    .restore(HiveOssTask.createTask().withKey(context.key()).withBucket(context.category().bucketName()));
            markRestoreStatus(context.recordId(), HiveRestoreStatusState.IN_PROGRESS, true, null);
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(buildStatus(HiveRestoreStatusState.IN_PROGRESS, context.restoreTime()));
        } else if (HiveRestoreStatus.IN_PROGRESS == restoreResult.getRestoreStatus()) {
            markRestoreStatus(context.recordId(), HiveRestoreStatusState.IN_PROGRESS, false, null);
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(buildStatus(HiveRestoreStatusState.IN_PROGRESS, context.restoreTime()));
        } else if (HiveRestoreStatus.COMPLETED == restoreResult.getRestoreStatus()) {
            LocalDateTime restoreTime = resolveRestoreTime(restoreResult);
            markRestoreStatus(context.recordId(), HiveRestoreStatusState.COMPLETED, false, restoreTime);
            return ResponseEntity.ok(buildStatus(HiveRestoreStatusState.COMPLETED, restoreTime == null ? context.restoreTime() : restoreTime));
        }
        return ResponseEntity.internalServerError().build();
    }

    @GetMapping("/categories/{category}/files/unfreeze-status/{*fileKey}")
    public ResponseEntity<HiveUnfreezeStatusVO> unfreezeState(CategoryFileContext context) {
        if (context.category().storageClass() != CategoryStorageClass.ARCHIVE) return ResponseEntity.badRequest().build();

        HiveRestoreResult restoreResult = hiveOssService.using(context.provider())
                .restoreCheck(HiveOssTask.createTask().withKey(context.key()).withBucket(context.category().bucketName()));
        if (HiveRestoreStatus.NOT_STARTED == restoreResult.getRestoreStatus()) {
            markRestoreStatus(context.recordId(), HiveRestoreStatusState.NOT_STARTED, false, null);
            return ResponseEntity.ok(buildStatus(HiveRestoreStatusState.NOT_STARTED, context.restoreTime()));
        } else if (HiveRestoreStatus.IN_PROGRESS == restoreResult.getRestoreStatus()) {
            markRestoreStatus(context.recordId(), HiveRestoreStatusState.IN_PROGRESS, false, null);
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(buildStatus(HiveRestoreStatusState.IN_PROGRESS, context.restoreTime()));
        } else if (HiveRestoreStatus.COMPLETED == restoreResult.getRestoreStatus()) {
            LocalDateTime restoreTime = resolveRestoreTime(restoreResult);
            markRestoreStatus(context.recordId(), HiveRestoreStatusState.COMPLETED, false, restoreTime);
            return ResponseEntity.ok(buildStatus(HiveRestoreStatusState.COMPLETED, restoreTime == null ? context.restoreTime() : restoreTime));
        }
        return ResponseEntity.internalServerError().build();
    }

    private void markRestoreStatus(Integer recordId, HiveRestoreStatusState status,
                                   boolean requested, LocalDateTime restoreTime) {
        hiveRecordRepository.updateRestoreStatus(recordId, status, requested, restoreTime);
    }

    private LocalDateTime resolveRestoreTime(HiveRestoreResult restoreResult) {
        if (restoreResult == null || restoreResult.getExpiryDate() == null) {
            return null;
        }
        ZonedDateTime converted = restoreResult.getExpiryDate().withZoneSameInstant(ZoneId.systemDefault());
        return converted.toLocalDateTime();
    }

    private HiveUnfreezeStatusVO buildStatus(HiveRestoreStatusState status, LocalDateTime restoreTime) {
        return HiveUnfreezeStatusVO.builder()
                .status(status.name())
                .unfreezeTime(restoreTime)
                .build();
    }
}
