package cc.cc3c.hive.oss.controller;

import cc.cc3c.hive.domain.entity.HiveRecord;
import cc.cc3c.hive.domain.repository.HiveRecordRepository;
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
    public ResponseEntity<Void> unfreezeFiles(@PathVariable("category") String category,
                                               @PathVariable("fileKey") String fileKey) {
        fileKey = HiveRecordVOAssembler.normalizeFileKey(fileKey);
        Optional<HiveRecord> optional = hiveRecordRepository.findByBucketNameAndFileKeyAndDeletedIsFalse(
                fileGroupService.resolveBucket(category), fileKey);
        if (optional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        HiveRecord hiveRecord = optional.get();
        if (!assembler.isArchiveStorageClass(fileCategoryResolver.resolveRecordStorageClass(hiveRecord))) {
            return ResponseEntity.badRequest().build();
        }
        HiveRestoreResult restoreResult = hiveOssService.using(hiveRecord.getProvider())
                .restoreCheck(HiveOssTask.createTask().withKey(fileKey).withBucket(hiveRecord.getBucketName()));
        if (HiveRestoreStatus.NOT_STARTED == restoreResult.getRestoreStatus()) {
            hiveOssService.using(hiveRecord.getProvider())
                    .restore(HiveOssTask.createTask().withKey(fileKey).withBucket(hiveRecord.getBucketName()));
            return ResponseEntity.status(HttpStatus.ACCEPTED).build();
        } else if (HiveRestoreStatus.IN_PROGRESS == restoreResult.getRestoreStatus()) {
            return ResponseEntity.status(HttpStatus.ACCEPTED).build();
        } else if (HiveRestoreStatus.COMPLETED == restoreResult.getRestoreStatus()) {
            ZonedDateTime converted = restoreResult.getExpiryDate().withZoneSameInstant(ZoneId.systemDefault());
            hiveRecord.setRestoreTime(converted.toLocalDateTime());
            hiveRecordRepository.save(hiveRecord);
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.internalServerError().build();
    }

    @GetMapping("/categories/{category}/files/unfreeze-status/{*fileKey}")
    public ResponseEntity<Void> unfreezeState(@PathVariable("category") String category,
                                               @PathVariable("fileKey") String fileKey) {
        fileKey = HiveRecordVOAssembler.normalizeFileKey(fileKey);
        Optional<HiveRecord> optional = hiveRecordRepository.findByBucketNameAndFileKeyAndDeletedIsFalse(
                fileGroupService.resolveBucket(category), fileKey);
        if (optional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        HiveRecord hiveRecord = optional.get();
        if (!assembler.isArchiveStorageClass(fileCategoryResolver.resolveRecordStorageClass(hiveRecord))) {
            return ResponseEntity.badRequest().build();
        }
        HiveRestoreResult restoreResult = hiveOssService.using(hiveRecord.getProvider())
                .restoreCheck(HiveOssTask.createTask().withKey(fileKey).withBucket(hiveRecord.getBucketName()));
        if (HiveRestoreStatus.NOT_STARTED == restoreResult.getRestoreStatus()) {
            return ResponseEntity.badRequest().build();
        } else if (HiveRestoreStatus.IN_PROGRESS == restoreResult.getRestoreStatus()) {
            return ResponseEntity.status(HttpStatus.ACCEPTED).build();
        } else if (HiveRestoreStatus.COMPLETED == restoreResult.getRestoreStatus()) {
            ZonedDateTime converted = restoreResult.getExpiryDate().withZoneSameInstant(ZoneId.systemDefault());
            hiveRecord.setRestoreTime(converted.toLocalDateTime());
            hiveRecordRepository.save(hiveRecord);
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.internalServerError().build();
    }
}
