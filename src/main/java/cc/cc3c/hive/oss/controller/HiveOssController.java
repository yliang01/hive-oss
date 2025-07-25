package cc.cc3c.hive.oss.controller;

import cc.cc3c.hive.domain.entity.HiveRecord;
import cc.cc3c.hive.domain.model.HiveDownloadStatus;
import cc.cc3c.hive.domain.model.HiveRecordSource;
import cc.cc3c.hive.domain.model.HiveRecordStatus;
import cc.cc3c.hive.domain.repository.HiveRecordRepository;
import cc.cc3c.hive.oss.controller.vo.*;
import cc.cc3c.hive.oss.service.HiveDownloadService;
import cc.cc3c.hive.oss.service.HiveOssService;
import cc.cc3c.hive.oss.service.HiveSyncService;
import cc.cc3c.hive.oss.vendor.vo.HiveOssTask;
import cc.cc3c.hive.oss.vendor.vo.HiveRestoreResult;
import cc.cc3c.hive.oss.vendor.vo.HiveRestoreStatus;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Slf4j
@RestController
public class HiveOssController {

    private HiveOssService hiveOssService;
    private HiveDownloadService hiveDownloadService;
    private HiveSyncService hiveSyncService;
    private HiveRecordRepository hiveRecordRepository;

    public HiveOssController(HiveOssService hiveOssService,
                             HiveDownloadService hiveDownloadService,
                             HiveSyncService hiveSyncService,
                             HiveRecordRepository hiveRecordRepository) {
        this.hiveOssService = hiveOssService;
        this.hiveDownloadService = hiveDownloadService;
        this.hiveSyncService = hiveSyncService;
        this.hiveRecordRepository = hiveRecordRepository;
    }

    @GetMapping("/buckets")
    public List<String> getBuckets() {
        return Arrays.stream(HiveRecordSource.values()).map(x -> x.name()).toList();
    }

    @GetMapping("/buckets/{bucket}/files")
    public HiveRecordsVO getFiles(@PathVariable("bucket") HiveRecordSource source,
                                  @RequestParam("page") Integer page,
                                  @RequestParam("pageSize") Integer pageSize) {

        Pageable pageable = PageRequest.of(page, pageSize, Sort.by("id").descending());
        Page<HiveRecord> pageResult = hiveRecordRepository.findBySourceAndDeletedIsFalse(pageable, source);

        List<HiveRecordVO> list = pageResult.stream().map(x -> {
            boolean restorable = source.isRestoreRequired() &&
                    (x.getRestoreTime() == null || x.getRestoreTime().isBefore(LocalDateTime.now()));
            boolean downloadable = !restorable &&
                    (x.getDownloadStatus() == null || x.getDownloadStatus() == HiveDownloadStatus.failed);

            File downloadFile = hiveDownloadService.getDownloadFile(x);
            String localPath = x.getDownloadStatus() != null && x.getDownloadStatus() == HiveDownloadStatus.success ?
                    downloadFile.toURI().toString().replaceFirst("^file:/", "file:///") : null;
            Boolean localPathExists = null;
            if (StringUtils.isNotEmpty(localPath)) {
                localPathExists = downloadFile.exists();
            }
            return HiveRecordVO.builder().fileName(x.getFileName())
                    .fileKey(x.getFileKey())
                    .zipped(x.getZipped())
                    .size(x.getSize())
                    .status(x.getStatus().name())
                    .updateTime(x.getUpdateTime())
                    .unfreezeTime(x.getRestoreTime())
                    .restorable(restorable)
                    .downloadable(downloadable)
                    .localPath(localPath)
                    .localPathExists(localPathExists)
                    .deletable(x.getDeletable())
                    .build();
        }).toList();
        return HiveRecordsVO.builder().files(list).total((int) pageResult.getTotalElements()).page(page).pageSize(pageSize).build();
    }

    @DeleteMapping("/files/{fileKey}")
    public ResponseEntity<Void> deleteFiles(@PathVariable("fileKey") String fileKey) {
        Optional<HiveRecord> optional = hiveRecordRepository.findByFileKey(fileKey);
        if (optional.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        HiveRecord hiveRecord = optional.get();
        hiveRecord.setStatus(HiveRecordStatus.TO_BE_DELETED);
        hiveRecord.setDeletable(false);
        hiveRecordRepository.save(hiveRecord);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/files/unfreeze/{fileKey}")
    public ResponseEntity<Void> unfreezeFiles(@PathVariable("fileKey") String fileKey) {
        Optional<HiveRecord> optional = hiveRecordRepository.findByFileKey(fileKey);
        if (optional.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        HiveRecord hiveRecord = optional.get();
        HiveRestoreResult restoreResult = hiveOssService.using(hiveRecord.getSource()).restoreCheck(HiveOssTask.createTask().withKey(fileKey).withBucket(hiveRecord.getSource()));
        if (HiveRestoreStatus.NOT_STARTED == restoreResult.getRestoreStatus()) {
            hiveOssService.using(hiveRecord.getSource()).restore(HiveOssTask.createTask().withKey(fileKey).withBucket(hiveRecord.getSource()));
        }
        return ResponseEntity.ok().build();
    }

    @GetMapping("/files/unfreeze-status/{fileKey}")
    public ResponseEntity<HiveUnfreezeVO> unfreezeState(@PathVariable("fileKey") String fileKey) {
        Optional<HiveRecord> optional = hiveRecordRepository.findByFileKey(fileKey);
        if (optional.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        HiveRecord hiveRecord = optional.get();
        HiveRestoreResult restoreResult = hiveOssService.using(hiveRecord.getSource()).restoreCheck(HiveOssTask.createTask().withKey(fileKey).withBucket(hiveRecord.getSource()));
        if (HiveRestoreStatus.COMPLETED == restoreResult.getRestoreStatus()) {
            ZonedDateTime converted = restoreResult.getExpiryDate().withZoneSameInstant(ZoneId.systemDefault());
            hiveRecord.setRestoreTime(converted.toLocalDateTime());
            hiveRecordRepository.save(hiveRecord);
            return ResponseEntity.ok(HiveUnfreezeVO.builder().unfrozen(true).build());
        }
        return ResponseEntity.ok(HiveUnfreezeVO.builder().unfrozen(false).build());
    }

    @PostMapping("/files/download-task/{fileKey}")
    public ResponseEntity<Void> downloadTask(@PathVariable("fileKey") String fileKey) {
        Optional<HiveRecord> optional = hiveRecordRepository.findByFileKey(fileKey);
        if (optional.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        HiveRecord hiveRecord = optional.get();
        try {
            hiveDownloadService.download(hiveRecord);
        } catch (Exception e) {
            log.error("failed to download {}", fileKey);
            return ResponseEntity.internalServerError().build();
        }
        return ResponseEntity.ok().build();
    }

    @GetMapping("/files/download-task-status/{fileKey}")
    public ResponseEntity<HiveDownloadStatusVO> downloadTaskStatus(@PathVariable("fileKey") String fileKey) {
        Optional<HiveRecord> optional = hiveRecordRepository.findByFileKey(fileKey);
        if (optional.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        HiveRecord hiveRecord = optional.get();
        if (hiveRecord.getDownloadStatus() == null) {
            return ResponseEntity.badRequest().build();
        }
        HiveDownloadStatusVO.HiveDownloadStatusVOBuilder builder = HiveDownloadStatusVO.builder()
                .status(hiveRecord.getDownloadStatus());
        HiveOssTask downLoadTask = hiveDownloadService.getDownLoadTask(fileKey);
        if (downLoadTask != null) {
            builder.progress(downLoadTask.getProgress()).build();
        }
        return ResponseEntity.ok(builder.build());
    }

    @PostMapping("/files/release-local/{fileKey}")
    public ResponseEntity<HiveDownloadStatusVO> releaseLocal(@PathVariable("fileKey") String fileKey) {
        Optional<HiveRecord> optional = hiveRecordRepository.findByFileKey(fileKey);
        if (optional.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        HiveRecord hiveRecord = optional.get();
        hiveRecordRepository.updateDownloadStatus(fileKey, null);
        File downloadFile = hiveDownloadService.getDownloadFile(hiveRecord);
        try {
            FileUtils.forceDelete(downloadFile);
        } catch (IOException e) {
            log.error("failed to delete file {}", downloadFile, e);
            return ResponseEntity.internalServerError().build();
        }
        return ResponseEntity.ok().build();
    }

    @PostMapping("/files/confirm-delete/{bucket}")
    public ResponseEntity<Void> syncLocal(@PathVariable("bucket") HiveRecordSource source) {
        List<HiveRecord> recordList = hiveRecordRepository.findByStatusAndDeletedIsFalse(HiveRecordStatus.DB_ONLY);
        for (HiveRecord record : recordList) {
            record.setDeleted(true);
            hiveRecordRepository.save(record);
        }
        recordList = hiveRecordRepository.findByStatusAndDeletedIsFalse(HiveRecordStatus.TO_BE_DELETED);
        for (HiveRecord record : recordList) {
            HiveOssTask hiveOssTask = HiveOssTask.createTask().withBucket(record.getSource()).withKey(record.getFileKey());
            try {
                hiveOssService.using(record.getSource()).delete(hiveOssTask);
            } catch (Exception e) {
                log.error("failed to delete {}", hiveOssTask);
                return ResponseEntity.internalServerError().build();
            }
            record.setDeleted(true);
            hiveRecordRepository.save(record);
        }
        return ResponseEntity.ok().build();
    }

    @PostMapping("/files/sync-remote/{bucket}")
    public ResponseEntity<HiveSyncVO> syncRemote(@PathVariable("bucket") HiveRecordSource source) {
        try {
            HiveSyncVO hiveSyncVO = hiveSyncService.syncRemote(source);
            return ResponseEntity.ok(hiveSyncVO);
        } catch (Exception e) {
            log.error("sync remote failed {}", source, e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
