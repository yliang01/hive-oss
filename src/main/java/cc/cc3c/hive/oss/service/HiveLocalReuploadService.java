package cc.cc3c.hive.oss.service;

import cc.cc3c.hive.domain.entity.FileCategoryEntity;
import cc.cc3c.hive.domain.entity.HiveRecord;
import cc.cc3c.hive.domain.model.HiveLocalCheckStatus;
import cc.cc3c.hive.domain.model.HiveRecordStatus;
import cc.cc3c.hive.domain.repository.HiveRecordRepository;
import cc.cc3c.hive.oss.controller.vo.HiveLocalReuploadRequest;
import cc.cc3c.hive.oss.controller.vo.HiveLocalReuploadStatusVO;
import cc.cc3c.hive.oss.thumbnail.ThumbnailAsyncService;
import cc.cc3c.hive.oss.vendor.client.vo.HiveOssObject;
import cc.cc3c.hive.oss.vendor.vo.HiveOssTask;
import cc.cc3c.hive.oss.web.CategoryContext;
import cc.cc3c.hive.oss.web.FileKeyNormalizer;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class HiveLocalReuploadService {

    private final HiveRecordRepository hiveRecordRepository;
    private final HiveLocalScanService hiveLocalScanService;
    private final HiveOssService hiveOssService;
    private final HiveRecordStateService stateService;
    private final ThumbnailAsyncService thumbnailAsyncService;
    private final Map<String, ReuploadTask> tasks = new ConcurrentHashMap<>();
    private final Map<String, String> reuploading = new ConcurrentHashMap<>();

    public HiveLocalReuploadService(HiveRecordRepository hiveRecordRepository,
                                    HiveLocalScanService hiveLocalScanService,
                                    HiveOssService hiveOssService,
                                    HiveRecordStateService stateService,
                                    ThumbnailAsyncService thumbnailAsyncService) {
        this.hiveRecordRepository = hiveRecordRepository;
        this.hiveLocalScanService = hiveLocalScanService;
        this.hiveOssService = hiveOssService;
        this.stateService = stateService;
        this.thumbnailAsyncService = thumbnailAsyncService;
    }

    public HiveLocalReuploadStatusVO reuploadLocal(CategoryContext category, HiveLocalReuploadRequest request) throws Exception {
        ReuploadTask task = validate(category, request);
        if (reuploading.putIfAbsent(task.record.getFileKey(), "") != null) {
            throw new LocalReuploadConflictException("文件正在重新上传，请稍后重试");
        }
        tasks.put(task.getTaskId(), task);
        Thread.ofVirtual().start(() -> doReuploadLocal(category, task));
        return HiveLocalReuploadStatusVO.builder().taskId(task.getTaskId()).status(ReuploadStatus.RUNNING.name()).build();
    }

    public HiveLocalReuploadStatusVO getStatus(String taskId) {
        ReuploadTask task = tasks.get(taskId);
        if (task == null) {
            return null;
        }
        return HiveLocalReuploadStatusVO.builder()
                .taskId(task.taskId)
                .fileKey(task.getRecord().getFileKey())
                .status(task.status.name())
                .message(task.message)
                .build();
    }

    private void doReuploadLocal(CategoryContext category, ReuploadTask task) {
        boolean uploadAttempted = false;
        try {
            HiveRecord record = task.getRecord();
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
            record.setLocalCheckStatus(HiveLocalCheckStatus.SIZE_MATCHED);
            record.setLocalCheckTime(LocalDateTime.now());
            record.setLocalCheckObservedSize(task.getLocalSize());
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
            reuploading.remove(task.getRecord().getFileKey());
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

    private ReuploadTask validate(CategoryContext category, HiveLocalReuploadRequest request) throws Exception {
        if (request == null || request.getObservedSize() == null || request.getLocalCheckTime() == null) {
            throw new IllegalArgumentException("localCheckTime and observedSize are required");
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
        if (!HiveLocalCheckStatus.SIZE_CHANGED.equals(record.getLocalCheckStatus())
                && !HiveLocalCheckStatus.BASELINE_MISSING.equals(record.getLocalCheckStatus())) {
            throw new LocalReuploadConflictException("文件本地状态不允许重新上传，请先重新检查本地文件");
        }
        if (!request.getLocalCheckTime().equals(record.getLocalCheckTime())
                || !request.getObservedSize().equals(record.getLocalCheckObservedSize())) {
            throw new LocalReuploadConflictException("本地检查结果已过期，请重新检查后再确认上传");
        }
        String groupCode = record.getGroup() == null ? null : record.getGroup().getGroupCode();
        Path localFile = hiveLocalScanService.resolveExistingLocalFile(category.code(), record.getFileName(), groupCode);
        long localSize = Files.size(localFile);
        if (localSize != request.getObservedSize()) {
            throw new LocalReuploadConflictException("本地文件已变化，请重新检查后再确认上传");
        }
        if (HiveLocalCheckStatus.SIZE_CHANGED.equals(record.getLocalCheckStatus())
                && record.getOriginalSize() == null) {
            throw new LocalReuploadConflictException("文件大小基线异常，请重新检查本地文件");
        }
        if (record.getOriginalSize() != null && localSize == record.getOriginalSize()) {
            throw new LocalReuploadConflictException("本地文件大小已恢复一致，请重新检查本地文件");
        }
        return ReuploadTask.builder()
                .taskId(UUID.randomUUID().toString())
                .record(record)
                .localFile(localFile)
                .localSize(localSize)
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
