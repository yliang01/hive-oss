package cc.cc3c.hive.oss.controller;

import cc.cc3c.hive.domain.model.HiveDownloadStatus;
import cc.cc3c.hive.oss.controller.vo.HiveDownloadStatusVO;
import cc.cc3c.hive.oss.service.HiveDownloadService;
import cc.cc3c.hive.oss.vendor.vo.HiveOssTask;
import cc.cc3c.hive.oss.web.CategoryFileContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;

@Slf4j
@RestController
public class HiveFileDownloadController {

    private final HiveDownloadService hiveDownloadService;

    public HiveFileDownloadController(HiveDownloadService hiveDownloadService) {
        this.hiveDownloadService = hiveDownloadService;
    }

    @PostMapping("/categories/{category}/files/download-task/{*fileKey}")
    public ResponseEntity<Void> downloadTask(CategoryFileContext context) {
        try {
            hiveDownloadService.reconcileStaleDownloading(context);
            hiveDownloadService.download(context);
        } catch (Exception e) {
            log.error("failed to download {}", context.key());
            return ResponseEntity.internalServerError().build();
        }
        return ResponseEntity.ok().build();
    }

    @GetMapping("/categories/{category}/files/download-task-status/{*fileKey}")
    public ResponseEntity<HiveDownloadStatusVO> downloadTaskStatus(CategoryFileContext context) {
        HiveDownloadStatus status = hiveDownloadService.reconcileStaleDownloading(context);
        if (status == null) {
            return ResponseEntity.badRequest().build();
        }
        HiveDownloadStatusVO.HiveDownloadStatusVOBuilder builder = HiveDownloadStatusVO.builder()
                .status(status);
        HiveOssTask downLoadTask = hiveDownloadService.getDownLoadTask(context);
        if (downLoadTask != null) {
            builder.progress(downLoadTask.getProgress());
        }
        if (status == HiveDownloadStatus.success) {
            File downloadFile = hiveDownloadService.getDownloadFile(context.zipped(), context.fileName());
            if (downloadFile.exists()) {
                builder.downloadUrl(downloadFile.toURI().toString().replaceFirst("^file:/", "file:///"));
            }
        }
        return ResponseEntity.ok(builder.build());
    }

    @PostMapping("/categories/{category}/files/release-local/{*fileKey}")
    public ResponseEntity<HiveDownloadStatusVO> releaseLocal(CategoryFileContext context) {
        hiveDownloadService.releaseLocalStatus(context);
        return ResponseEntity.ok().build();
    }
}
