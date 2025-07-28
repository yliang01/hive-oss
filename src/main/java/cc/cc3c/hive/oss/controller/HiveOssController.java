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
import cc.cc3c.hive.oss.service.HiveUploadService;
import cc.cc3c.hive.oss.vendor.vo.HiveOssTask;
import cc.cc3c.hive.oss.vendor.vo.HiveRestoreResult;
import cc.cc3c.hive.oss.vendor.vo.HiveRestoreStatus;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.fileupload2.core.FileItemInput;
import org.apache.commons.fileupload2.core.FileItemInputIterator;
import org.apache.commons.fileupload2.jakarta.servlet6.JakartaServletFileUpload;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
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

    private final HiveOssService hiveOssService;
    private final HiveUploadService hiveUploadService;
    private final HiveDownloadService hiveDownloadService;
    private final HiveSyncService hiveSyncService;
    private final HiveRecordRepository hiveRecordRepository;

    public HiveOssController(HiveOssService hiveOssService, HiveUploadService hiveUploadService, HiveDownloadService hiveDownloadService, HiveSyncService hiveSyncService, HiveRecordRepository hiveRecordRepository) {
        this.hiveOssService = hiveOssService;
        this.hiveUploadService = hiveUploadService;
        this.hiveDownloadService = hiveDownloadService;
        this.hiveSyncService = hiveSyncService;
        this.hiveRecordRepository = hiveRecordRepository;
    }

    @GetMapping("/buckets")
    public List<String> getBuckets() {
        return Arrays.stream(HiveRecordSource.values()).map(Enum::name).toList();
    }

    @GetMapping("/buckets/{bucket}/files/{fileKey}")
    public ResponseEntity<HiveRecordVO> getFile(@PathVariable("bucket") HiveRecordSource source, @PathVariable("fileKey") String fileKey) {
        Optional<HiveRecord> optional = hiveRecordRepository.findBySourceAndFileKey(source, fileKey);
        if (optional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(buildHiveRecordVO(optional.get()));
    }

    @GetMapping("/buckets/{bucket}/files")
    public HiveRecordsVO getFiles(@PathVariable("bucket") HiveRecordSource source, @RequestParam("page") Integer page, @RequestParam("pageSize") Integer pageSize) {

        Pageable pageable = PageRequest.of(page, pageSize, Sort.by("id").descending());
        Page<HiveRecord> pageResult = hiveRecordRepository.findBySourceAndDeletedIsFalse(pageable, source);

        List<HiveRecordVO> list = pageResult.stream().map(this::buildHiveRecordVO).toList();
        return HiveRecordsVO.builder().files(list).total((int) pageResult.getTotalElements()).page(page).pageSize(pageSize).build();
    }

    private HiveRecordVO buildHiveRecordVO(HiveRecord record) {
        boolean restorable = record.getSource().isRestoreRequired() && (record.getRestoreTime() == null || record.getRestoreTime().isBefore(LocalDateTime.now()));
        boolean downloadable = !restorable && (record.getDownloadStatus() == null || record.getDownloadStatus() == HiveDownloadStatus.failed);

        File downloadFile = hiveDownloadService.getDownloadFile(record);
        String localPath = record.getDownloadStatus() != null && record.getDownloadStatus() == HiveDownloadStatus.success ? downloadFile.toURI().toString().replaceFirst("^file:/", "file:///") : null;
        Boolean localPathExists = null;
        if (StringUtils.isNotEmpty(localPath)) {
            localPathExists = downloadFile.exists();
        }
        return HiveRecordVO.builder().fileName(record.getFileName()).fileKey(record.getFileKey()).zipped(record.getZipped()).size(record.getSize()).status(record.getStatus().name()).updateTime(record.getUpdateTime()).unfreezeTime(record.getRestoreTime()).restorable(restorable).downloadable(downloadable).localPath(localPath).localPathExists(localPathExists).deletable(record.getDeletable()).build();
    }

    @DeleteMapping("/buckets/{bucket}/files/{fileKey}")
    public ResponseEntity<Void> deleteFiles(@PathVariable("bucket") HiveRecordSource source, @PathVariable("fileKey") String fileKey) {
        Optional<HiveRecord> optional = hiveRecordRepository.findBySourceAndFileKey(source, fileKey);
        if (optional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        HiveRecord hiveRecord = optional.get();
        hiveRecord.setStatus(HiveRecordStatus.TO_BE_DELETED);
        hiveRecord.setDeletable(false);
        hiveRecordRepository.save(hiveRecord);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/buckets/{bucket}/files/unfreeze/{fileKey}")
    public ResponseEntity<Void> unfreezeFiles(@PathVariable("bucket") HiveRecordSource source, @PathVariable("fileKey") String fileKey) {
        Optional<HiveRecord> optional = hiveRecordRepository.findBySourceAndFileKey(source, fileKey);
        if (optional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        HiveRecord hiveRecord = optional.get();
        HiveRestoreResult restoreResult = hiveOssService.using(hiveRecord.getSource()).restoreCheck(HiveOssTask.createTask().withKey(fileKey).withBucket(hiveRecord.getSource()));
        if (HiveRestoreStatus.NOT_STARTED == restoreResult.getRestoreStatus()) {
            hiveOssService.using(hiveRecord.getSource()).restore(HiveOssTask.createTask().withKey(fileKey).withBucket(hiveRecord.getSource()));
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

    @GetMapping("/buckets/{bucket}/files/unfreeze-status/{fileKey}")
    public ResponseEntity<Void> unfreezeState(@PathVariable("bucket") HiveRecordSource source, @PathVariable("fileKey") String fileKey) {
        Optional<HiveRecord> optional = hiveRecordRepository.findBySourceAndFileKey(source, fileKey);
        if (optional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        HiveRecord hiveRecord = optional.get();
        HiveRestoreResult restoreResult = hiveOssService.using(hiveRecord.getSource()).restoreCheck(HiveOssTask.createTask().withKey(fileKey).withBucket(hiveRecord.getSource()));
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

    @PostMapping("/buckets/{bucket}/files/download-task/{fileKey}")
    public ResponseEntity<Void> downloadTask(@PathVariable("bucket") HiveRecordSource source, @PathVariable("fileKey") String fileKey) {
        Optional<HiveRecord> optional = hiveRecordRepository.findBySourceAndFileKey(source, fileKey);
        if (optional.isEmpty()) {
            return ResponseEntity.notFound().build();
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

    @GetMapping("/buckets/{bucket}/files/download-task-status/{fileKey}")
    public ResponseEntity<HiveDownloadStatusVO> downloadTaskStatus(@PathVariable("bucket") HiveRecordSource source, @PathVariable("fileKey") String fileKey) {
        Optional<HiveRecord> optional = hiveRecordRepository.findBySourceAndFileKey(source, fileKey);
        if (optional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        HiveRecord hiveRecord = optional.get();
        if (hiveRecord.getDownloadStatus() == null) {
            return ResponseEntity.badRequest().build();
        }
        HiveDownloadStatusVO.HiveDownloadStatusVOBuilder builder = HiveDownloadStatusVO.builder().status(hiveRecord.getDownloadStatus());
        HiveOssTask downLoadTask = hiveDownloadService.getDownLoadTask(fileKey);
        if (downLoadTask != null) {
            builder.progress(downLoadTask.getProgress()).build();
        }
        return ResponseEntity.ok(builder.build());
    }

    @PostMapping("/buckets/{bucket}/files/release-local/{fileKey}")
    public ResponseEntity<HiveDownloadStatusVO> releaseLocal(@PathVariable("bucket") HiveRecordSource source, @PathVariable("fileKey") String fileKey) {
        Optional<HiveRecord> optional = hiveRecordRepository.findBySourceAndFileKey(source, fileKey);
        if (optional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        HiveRecord hiveRecord = optional.get();
        hiveRecordRepository.updateDownloadStatus(fileKey, null);
        File downloadFile = hiveDownloadService.getDownloadFile(hiveRecord);
        boolean deleted = FileUtils.deleteQuietly(downloadFile);
        if (!deleted) {
            log.error("failed to delete file {}", downloadFile);
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

    @PostMapping("/buckets/{bucket}/files/upload")
    public ResponseEntity<HiveUploadVO> upload(@PathVariable("bucket") HiveRecordSource source, HttpServletRequest request) throws IOException {
        JakartaServletFileUpload upload = new JakartaServletFileUpload();
        upload.setFileSizeMax(-1); // 不限制大小
        FileItemInputIterator iter = upload.getItemIterator(request);
        FileItemInput item = iter.next();
        try {
            String fileKey = hiveUploadService.uploadSync(source, item.getName(), item.getInputStream());
            return ResponseEntity.ok(HiveUploadVO.builder().fileKey(fileKey).build());
        } catch (Exception e) {
            log.error("upload failed", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
