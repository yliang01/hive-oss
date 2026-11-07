package cc.cc3c.hive.oss.business.controller;

import cc.cc3c.hive.domain.model.HiveDownloadStatus;
import cc.cc3c.hive.oss.business.controller.vo.HiveDownloadStatusVO;
import cc.cc3c.hive.oss.business.service.HiveDownloadService;
import cc.cc3c.hive.oss.business.vendor.vo.HiveOssTask;
import cc.cc3c.hive.oss.business.web.CategoryFileContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.FileSystemException;
import java.util.Map;

@Slf4j
@RestController
public class HiveFileDownloadController {

    private final HiveDownloadService hiveDownloadService;

    public HiveFileDownloadController(HiveDownloadService hiveDownloadService) {
        this.hiveDownloadService = hiveDownloadService;
    }

    @PostMapping("/categories/{category}/files/download-task/{*fileKey}")
    public ResponseEntity<?> downloadTask(CategoryFileContext context) {
        try {
            if (hiveDownloadService.isRestoreRequired(context)) {
                return ResponseEntity.status(HttpStatus.LOCKED).body(Map.of("error", "归档文件尚未解冻"));
            }
            if (!hiveDownloadService.download(context)) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "文件当前不可下载"));
            }
        } catch (Exception e) {
            log.error("failed to download {}", context.key(), e);
            return ResponseEntity.internalServerError().body(Map.of("error", "创建下载任务失败"));
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
        return ResponseEntity.ok(builder.build());
    }

    @PostMapping("/categories/{category}/files/release-local/{*fileKey}")
    public ResponseEntity<?> releaseLocal(CategoryFileContext context) {
        if (HiveDownloadStatus.downloading == context.downloadStatus()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
        try {
            hiveDownloadService.releaseLocalStatus(context);
            return ResponseEntity.ok().build();
        } catch (FileSystemException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "本地文件正在使用，请稍后重试"));
        } catch (Exception e) {
            log.error("failed to release local file {}", context.key(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

}
