package cc.cc3c.hive.oss.business.service;

import cc.cc3c.hive.domain.entity.HiveRecord;
import cc.cc3c.hive.domain.model.HiveLocalCheckStatus;
import cc.cc3c.hive.domain.model.HiveRecordStatus;
import cc.cc3c.hive.domain.repository.HiveRecordRepository;
import cc.cc3c.hive.oss.business.controller.vo.HiveLocalReuploadRequest;
import cc.cc3c.hive.oss.business.controller.vo.HiveLocalReuploadStatusVO;
import cc.cc3c.hive.oss.business.service.HiveLocalCheckService.LocalCheckResult;
import cc.cc3c.hive.oss.business.thumbnail.ThumbnailAsyncService;
import cc.cc3c.hive.oss.business.vendor.client.vo.HiveOssObject;
import cc.cc3c.hive.oss.business.vendor.vo.HiveOssTask;
import cc.cc3c.hive.oss.business.web.CategoryContext;
import cc.cc3c.hive.oss.business.web.FileKeyNormalizer;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class HiveLocalReuploadService {

    private final HiveRecordRepository hiveRecordRepository;
    private final HiveLocalCheckService hiveLocalCheckService;
    private final HiveOssService hiveOssService;
    private final HiveRecordStateService stateService;
    private final ThumbnailAsyncService thumbnailAsyncService;
    private final Map<String, ReuploadTask> tasks = new ConcurrentHashMap<>();
    private final Map<String, String> reuploading = new ConcurrentHashMap<>();

    public HiveLocalReuploadService(HiveRecordRepository hiveRecordRepository,
                                     HiveLocalCheckService hiveLocalCheckService,
                                     HiveOssService hiveOssService,
                                     HiveRecordStateService stateService,
                                     ThumbnailAsyncService thumbnailAsyncService) {
        this.hiveRecordRepository = hiveRecordRepository;
        this.hiveLocalCheckService = hiveLocalCheckService;
        this.hiveOssService = hiveOssService;
        this.stateService = stateService;
        this.thumbnailAsyncService = thumbnailAsyncService;
    }

    public HiveLocalReuploadStatusVO reuploadLocal(CategoryContext category, HiveLocalReuploadRequest request) throws Exception {
        ReuploadTask task = validate(category, request);
        String reuploadKey = category.categoryId() + ":" + task.record.getFileKey();
        if (reuploading.putIfAbsent(reuploadKey, "") != null) {
            throw new LocalReuploadConflictException("文件正在重新上传，请稍后重试");
        }
        tasks.put(task.getTaskId(), task);
        Thread.ofVirtual().start(() -> doReuploadLocal(category, task, reuploadKey));
        return HiveLocalReuploadStatusVO.builder().taskId(task.getTaskId()).status(ReuploadStatus.RUNNING.name()).build();
    }

    public HiveLocalReuploadStatusVO getStatus(String taskId) {
        ReuploadTask task = tasks.get(taskId);
        if (task == null) {
            return null;
        }
        return HiveLocalReuploadStatusVO.builder()
                .taskId(task.taskId)
                .fileKey(task.record.getFileKey())
                .status(task.status.name())
                .message(task.message)
                .build();
    }

    private void doReuploadLocal(CategoryContext category, ReuploadTask task, String reuploadKey) {
        boolean uploadAttempted = false;
        try {
            HiveRecord record = task.getRecord();
            if (Files.size(task.getLocalFile()) != task.getLocalSize()) {
                throw new LocalReuploadConflictException("本地副本已变化，请刷新列表后再确认上传");
            }
            try (InputStream inputStream = Files.newInputStream(task.getLocalFile())) {
                HiveOssTask uploadTask = HiveOssTask.createTask()
                        .withBucket(category.bucketName())
                        .withKey(record.getFileKey())
                        .withInputStream(inputStream)
                        .withStorageClass(category.storageClass().name())
                        .withEncryption(record.getFileName());
                uploadAttempted = true;
                hiveOssService.using(record.getProvider()).uploadStreaming(uploadTask);
            }
            HiveOssTask metadataTask = HiveOssTask.createTask()
                    .withBucket(category.bucketName())
                    .withKey(record.getFileKey());
            HiveOssObject metadata = hiveOssService.using(record.getProvider()).getObjectMetadata(metadataTask);
            if (metadata == null || metadata.getLastModified() == null) {
                throw new IllegalStateException("重新上传后未获得完整云端元数据");
            }
            stateService.refreshReuploaded(record, record.getCategory(), metadata, category.storageClass());
            record.setOriginalSize(task.getLocalSize());
            hiveRecordRepository.save(record);
            String message = refreshThumbnail(record, category, task.getLocalFile());
            task.setStatus(ReuploadStatus.SUCCESS);
            task.setMessage(message);
        } catch (LocalReuploadConflictException e) {
            task.setStatus(ReuploadStatus.FAILED);
            task.setMessage(e.getMessage());
        } catch (Exception e) {
            log.error("local reupload failed, task={}", task, e);
            if (uploadAttempted) {
                task.setStatus(ReuploadStatus.UNKNOWN);
                task.setMessage("上传请求结果未知，请扫描云端后确认: " + safeMessage(e));
            } else {
                task.setStatus(ReuploadStatus.FAILED);
                task.setMessage(safeMessage(e));
            }
        } finally {
            reuploading.remove(reuploadKey);
        }
    }

    private String refreshThumbnail(HiveRecord record, CategoryContext category, Path localFile) {
        if (!thumbnailAsyncService.isThumbnailEligible(category.uiVariant(), record.getFileName())) {
            return null;
        }
        try {
            thumbnailAsyncService.refreshAsync(record, record.getFileKey(), localFile.toFile());
            return null;
        } catch (Exception e) {
            log.warn("reupload succeeded but thumbnail refresh failed, fileKey={}", record.getFileKey(), e);
            return "文件已覆盖上传，但缩略图刷新失败";
        }
    }

    private ReuploadTask validate(CategoryContext category, HiveLocalReuploadRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request is required");
        }
        String fileKey = FileKeyNormalizer.normalize(request.getFileKey());
        if (fileKey == null || fileKey.isBlank()) {
            throw new IllegalArgumentException("fileKey is required");
        }
        HiveRecord record = hiveRecordRepository
                .findWithGroupByCategory_IdAndFileKeyInAndDeletedIsFalse(category.categoryId(), List.of(fileKey))
                .stream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("file not found"));
        if (!HiveRecordStatus.UPLOADED.equals(record.getStatus())) {
            throw new LocalReuploadConflictException("文件当前不是已上传状态");
        }
        LocalCheckResult localCheck = hiveLocalCheckService.checkRecord(category, record);
        if (!HiveLocalCheckStatus.SIZE_CHANGED.equals(localCheck.status())
                && !HiveLocalCheckStatus.BASELINE_MISSING.equals(localCheck.status())) {
            throw new LocalReuploadConflictException("本地副本状态不允许重新上传，请刷新列表后再确认上传");
        }
        return ReuploadTask.builder()
                .taskId(UUID.randomUUID().toString())
                .record(record)
                .localFile(localCheck.localFile())
                .localSize(localCheck.observedSize())
                .status(ReuploadStatus.RUNNING)
                .build();
    }

    private String safeMessage(Exception e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }

    @Data
    @Builder
    private static class ReuploadTask {
        private final String taskId;
        private final HiveRecord record;
        private final Path localFile;
        private final long localSize;
        private ReuploadStatus status;
        private String message;
    }

    private enum ReuploadStatus {
        RUNNING,
        SUCCESS,
        FAILED,
        UNKNOWN
    }
}
