package cc.cc3c.hive.oss.controller;

import cc.cc3c.hive.domain.entity.HiveRecord;
import cc.cc3c.hive.domain.model.HiveDownloadStatus;
import cc.cc3c.hive.domain.repository.HiveRecordRepository;
import cc.cc3c.hive.oss.controller.vo.HiveDownloadStatusVO;
import cc.cc3c.hive.oss.service.FileGroupService;
import cc.cc3c.hive.oss.service.HiveDownloadService;
import cc.cc3c.hive.oss.vendor.vo.HiveOssTask;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.util.Optional;

@Slf4j
@RestController
public class HiveFileDownloadController {

    private final HiveRecordRepository hiveRecordRepository;
    private final FileGroupService fileGroupService;
    private final HiveDownloadService hiveDownloadService;

    public HiveFileDownloadController(HiveRecordRepository hiveRecordRepository,
                                      FileGroupService fileGroupService,
                                      HiveDownloadService hiveDownloadService) {
        this.hiveRecordRepository = hiveRecordRepository;
        this.fileGroupService = fileGroupService;
        this.hiveDownloadService = hiveDownloadService;
    }

    @PostMapping("/categories/{category}/files/download-task/{*fileKey}")
    public ResponseEntity<Void> downloadTask(@PathVariable("category") String category,
                                              @PathVariable("fileKey") String fileKey) {
        fileKey = HiveRecordVOAssembler.normalizeFileKey(fileKey);
        Optional<HiveRecord> optional = hiveRecordRepository.findByBucketNameAndFileKeyAndDeletedIsFalse(
                fileGroupService.resolveBucket(category), fileKey);
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

    @GetMapping("/categories/{category}/files/download-task-status/{*fileKey}")
    public ResponseEntity<HiveDownloadStatusVO> downloadTaskStatus(@PathVariable("category") String category,
                                                                    @PathVariable("fileKey") String fileKey) {
        fileKey = HiveRecordVOAssembler.normalizeFileKey(fileKey);
        Optional<HiveRecord> optional = hiveRecordRepository.findByBucketNameAndFileKeyAndDeletedIsFalse(
                fileGroupService.resolveBucket(category), fileKey);
        if (optional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        HiveRecord hiveRecord = optional.get();
        if (hiveRecord.getDownloadStatus() == null) {
            return ResponseEntity.badRequest().build();
        }
        HiveDownloadStatusVO.HiveDownloadStatusVOBuilder builder = HiveDownloadStatusVO.builder()
                .status(hiveRecord.getDownloadStatus());
        HiveOssTask downLoadTask = hiveDownloadService.getDownLoadTask(fileKey);
        if (downLoadTask != null) {
            builder.progress(downLoadTask.getProgress());
        }
        if (hiveRecord.getDownloadStatus() == HiveDownloadStatus.success) {
            File downloadFile = hiveDownloadService.getDownloadFile(hiveRecord);
            if (downloadFile.exists()) {
                builder.downloadUrl(downloadFile.toURI().toString().replaceFirst("^file:/", "file:///"));
            }
        }
        return ResponseEntity.ok(builder.build());
    }

    @PostMapping("/categories/{category}/files/release-local/{*fileKey}")
    public ResponseEntity<HiveDownloadStatusVO> releaseLocal(@PathVariable("category") String category,
                                                              @PathVariable("fileKey") String fileKey) {
        fileKey = HiveRecordVOAssembler.normalizeFileKey(fileKey);
        Optional<HiveRecord> optional = hiveRecordRepository.findByBucketNameAndFileKeyAndDeletedIsFalse(
                fileGroupService.resolveBucket(category), fileKey);
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
}
