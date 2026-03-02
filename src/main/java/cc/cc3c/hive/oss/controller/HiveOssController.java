package cc.cc3c.hive.oss.controller;

import cc.cc3c.hive.domain.entity.HiveRecord;
import cc.cc3c.hive.domain.model.CategoryStorageClass;
import cc.cc3c.hive.domain.model.HiveDownloadStatus;
import cc.cc3c.hive.domain.model.HiveRecordStatus;
import cc.cc3c.hive.domain.repository.HiveRecordRepository;
import cc.cc3c.hive.oss.controller.vo.*;
import cc.cc3c.hive.oss.service.FileCategoryResolver;
import cc.cc3c.hive.oss.service.FileGroupService;
import cc.cc3c.hive.oss.service.HiveDownloadService;
import cc.cc3c.hive.oss.service.HiveOssService;
import cc.cc3c.hive.oss.service.HiveSyncService;
import cc.cc3c.hive.oss.service.HiveUploadService;
import cc.cc3c.hive.oss.vendor.client.vo.HiveOssObject;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Slf4j
@RestController
public class HiveOssController {
    private static final String PREVIEW_BLOCKED_FROZEN = "FROZEN";
    private static final String PREVIEW_BLOCKED_TOO_LARGE = "TOO_LARGE";
    private static final String PREVIEW_BLOCKED_UNSUPPORTED_TYPE = "UNSUPPORTED_TYPE";
    private static final Map<String, String> MIME_BY_EXTENSION = Map.ofEntries(
            Map.entry("jpg", MediaType.IMAGE_JPEG_VALUE),
            Map.entry("jpeg", MediaType.IMAGE_JPEG_VALUE),
            Map.entry("png", MediaType.IMAGE_PNG_VALUE),
            Map.entry("gif", MediaType.IMAGE_GIF_VALUE),
            Map.entry("webp", "image/webp"),
            Map.entry("pdf", MediaType.APPLICATION_PDF_VALUE),
            Map.entry("txt", MediaType.TEXT_PLAIN_VALUE),
            Map.entry("log", MediaType.TEXT_PLAIN_VALUE),
            Map.entry("csv", MediaType.TEXT_PLAIN_VALUE),
            Map.entry("json", MediaType.TEXT_PLAIN_VALUE),
            Map.entry("xml", MediaType.TEXT_PLAIN_VALUE)
    );

    private final HiveOssService hiveOssService;
    private final HiveUploadService hiveUploadService;
    private final HiveDownloadService hiveDownloadService;
    private final HiveSyncService hiveSyncService;
    private final HiveRecordRepository hiveRecordRepository;
    private final FileGroupService fileGroupService;
    private final FileCategoryResolver fileCategoryResolver;
    private long previewMaxSizeBytes;

    public HiveOssController(HiveOssService hiveOssService,
                             HiveUploadService hiveUploadService,
                             HiveDownloadService hiveDownloadService,
                             HiveSyncService hiveSyncService,
                             HiveRecordRepository hiveRecordRepository,
                             FileGroupService fileGroupService,
                             FileCategoryResolver fileCategoryResolver) {
        this.hiveOssService = hiveOssService;
        this.hiveUploadService = hiveUploadService;
        this.hiveDownloadService = hiveDownloadService;
        this.hiveSyncService = hiveSyncService;
        this.hiveRecordRepository = hiveRecordRepository;
        this.fileGroupService = fileGroupService;
        this.fileCategoryResolver = fileCategoryResolver;
    }

    @Value("${hive.preview.max-size-bytes:10485760}")
    public void setPreviewMaxSizeBytes(long previewMaxSizeBytes) {
        this.previewMaxSizeBytes = previewMaxSizeBytes;
    }

    @GetMapping("/categories")
    public List<FileCategoryVO> getCategories() {
        return fileGroupService.listCategories();
    }

    @GetMapping("/categories/{category}/groups")
    public List<FileGroupVO> getGroups(@PathVariable("category") String category) {
        return fileGroupService.listGroups(category);
    }

    @GetMapping("/categories/{category}/files/{fileKey}")
    public ResponseEntity<HiveRecordVO> getFile(@PathVariable("category") String category, @PathVariable("fileKey") String fileKey) {
        Optional<HiveRecord> optional = hiveRecordRepository.findByBucketNameAndFileKeyAndDeletedIsFalse(fileGroupService.resolveBucket(category), fileKey);
        if (optional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(buildHiveRecordVO(optional.get(), category, null));
    }

    @GetMapping("/categories/{category}/files")
    public HiveRecordsVO getFiles(@PathVariable("category") String category,
                                  @RequestParam(value = "groupId", required = false) Long groupId,
                                  @RequestParam(value = "ungroupedOnly", required = false) Boolean ungroupedOnly,
                                  @RequestParam(value = "keyword", required = false) String keyword,
                                  @RequestParam("page") Integer page,
                                  @RequestParam("pageSize") Integer pageSize) {
        return queryFiles(category, groupId, Boolean.TRUE.equals(ungroupedOnly), keyword, page, pageSize);
    }

    private HiveRecordsVO queryFiles(String category, Long groupId, boolean ungroupedOnly, String keyword, Integer page, Integer pageSize) {
        String bucketName = fileGroupService.resolveBucket(category);
        String normalizedKeyword = StringUtils.trimToNull(keyword);
        Pageable pageable = PageRequest.of(page, pageSize, Sort.by("id").descending());
        Page<HiveRecord> pageResult;

        if (groupId != null) {
            Set<Integer> ids = fileGroupService.resolveRecordIdsForQuery(category, groupId);
            if (ids.isEmpty()) {
                return HiveRecordsVO.builder().files(List.of()).total(0).page(page).pageSize(pageSize).build();
            }
            if (normalizedKeyword != null) {
                pageResult = hiveRecordRepository.findByBucketNameAndDeletedIsFalseAndIdInAndFileNameContainingIgnoreCase(pageable, bucketName, ids, normalizedKeyword);
            } else {
                pageResult = hiveRecordRepository.findByBucketNameAndDeletedIsFalseAndIdIn(pageable, bucketName, ids);
            }
        } else if (ungroupedOnly) {
            Set<Integer> groupedIds = fileGroupService.resolveRecordIdsForQuery(category, null);
            if (groupedIds.isEmpty()) {
                if (normalizedKeyword != null) {
                    pageResult = hiveRecordRepository.findByBucketNameAndDeletedIsFalseAndFileNameContainingIgnoreCase(pageable, bucketName, normalizedKeyword);
                } else {
                    pageResult = hiveRecordRepository.findByBucketNameAndDeletedIsFalse(pageable, bucketName);
                }
            } else {
                if (normalizedKeyword != null) {
                    pageResult = hiveRecordRepository.findByBucketNameAndDeletedIsFalseAndIdNotInAndFileNameContainingIgnoreCase(pageable, bucketName, groupedIds, normalizedKeyword);
                } else {
                    pageResult = hiveRecordRepository.findByBucketNameAndDeletedIsFalseAndIdNotIn(pageable, bucketName, groupedIds);
                }
            }
        } else if (normalizedKeyword != null) {
            pageResult = hiveRecordRepository.findByBucketNameAndDeletedIsFalseAndFileNameContainingIgnoreCase(pageable, bucketName, normalizedKeyword);
        } else {
            pageResult = hiveRecordRepository.findByBucketNameAndDeletedIsFalse(pageable, bucketName);
        }

        Map<Integer, cc.cc3c.hive.domain.entity.FileGroupRecord> groupRecordMap =
                fileGroupService.findGroupRecordMapByRecordIds(pageResult.stream().map(HiveRecord::getId).toList());
        List<HiveRecordVO> list = pageResult.stream()
                .map(record -> buildHiveRecordVO(record, category, groupRecordMap.get(record.getId())))
                .toList();
        return HiveRecordsVO.builder().files(list).total((int) pageResult.getTotalElements()).page(page).pageSize(pageSize).build();
    }

    @GetMapping("/categories/{category}/files/preview/{fileKey}")
    public ResponseEntity<StreamingResponseBody> previewFile(@PathVariable("category") String category, @PathVariable("fileKey") String fileKey) {
        Optional<HiveRecord> optional = hiveRecordRepository.findByBucketNameAndFileKeyAndDeletedIsFalse(fileGroupService.resolveBucket(category), fileKey);
        if (optional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        HiveRecord hiveRecord = optional.get();
        PreviewDecision previewDecision = evaluatePreview(hiveRecord);
        if (!previewDecision.previewable) {
            if (PREVIEW_BLOCKED_FROZEN.equals(previewDecision.blockedReason)) {
                return ResponseEntity.status(HttpStatus.LOCKED).build();
            }
            if (PREVIEW_BLOCKED_TOO_LARGE.equals(previewDecision.blockedReason)) {
                return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).build();
            }
            return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).build();
        }
        MediaType mediaType = MediaType.parseMediaType(previewDecision.mimeType);
        String fileName = StringUtils.defaultIfBlank(hiveRecord.getFileName(), hiveRecord.getFileKey());
        StreamingResponseBody body = outputStream -> {
            try {
                hiveDownloadService.streamPreview(hiveRecord, outputStream);
            } catch (Exception e) {
                log.error("preview stream failed {}", fileKey, e);
                throw new IOException("preview stream failed", e);
            }
        };
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "private,no-store")
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(fileName, StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .contentType(mediaType)
                .body(body);
    }

    @PostMapping("/categories/{category}/groups/{groupId}/files:assign")
    public ResponseEntity<Void> assignFilesToGroup(@PathVariable("category") String category,
                                                   @PathVariable("groupId") Long groupId,
                                                   @RequestBody FileGroupAssignRequest request) {
        fileGroupService.assignFiles(category, groupId, request.getFileKeys(), request.getOperator());
        return ResponseEntity.ok().build();
    }

    private HiveRecordVO buildHiveRecordVO(HiveRecord record, String category, cc.cc3c.hive.domain.entity.FileGroupRecord groupRecord) {
        boolean restorable = isRestorable(record);
        boolean downloadable = !restorable && (record.getDownloadStatus() == null || record.getDownloadStatus() == HiveDownloadStatus.failed);
        PreviewDecision previewDecision = evaluatePreview(record);

        File downloadFile = hiveDownloadService.getDownloadFile(record);
        String localPath = record.getDownloadStatus() != null && record.getDownloadStatus() == HiveDownloadStatus.success ? downloadFile.toURI().toString().replaceFirst("^file:/", "file:///") : null;
        Boolean localPathExists = null;
        if (StringUtils.isNotEmpty(localPath)) {
            localPathExists = downloadFile.exists();
        }
        cc.cc3c.hive.domain.entity.FileCategoryEntity finalCategory = category != null
                ? fileCategoryResolver.resolveCategory(category)
                : fileCategoryResolver.resolveFallbackCategory(record);
        String finalCategoryCode = finalCategory == null ? null : finalCategory.getCode();
        String previewUrl = previewDecision.previewable && finalCategoryCode != null
                ? "/categories/" + finalCategoryCode + "/files/preview/" + record.getFileKey() : null;
        return HiveRecordVO.builder()
                .category(finalCategoryCode)
                .categoryLabel(finalCategory == null ? null : finalCategory.getName())
                .uiVariant(finalCategory == null ? null : finalCategory.getUiVariant())
                .groupId(groupRecord == null ? null : groupRecord.getGroup().getId())
                .groupName(groupRecord == null ? null : groupRecord.getGroup().getGroupName())
                .fileName(record.getFileName())
                .fileKey(record.getFileKey())
                .zipped(record.getZipped())
                .size(record.getSize())
                .storageClass(record.getStorageClassCache() == null ? null : record.getStorageClassCache().name())
                .status(record.getStatus().name())
                .updateTime(record.getUpdateTime())
                .unfreezeTime(record.getRestoreTime())
                .restorable(restorable)
                .downloadable(downloadable)
                .localPath(localPath)
                .localPathExists(localPathExists)
                .deletable(record.getDeletable())
                .previewable(previewDecision.previewable)
                .previewUrl(previewUrl)
                .mimeType(previewDecision.mimeType)
                .previewBlockedReason(previewDecision.blockedReason)
                .previewMaxSizeBytes(previewMaxSizeBytes)
                .build();
    }

    private boolean isRestorable(HiveRecord record) {
        return isArchiveStorageClass(record.getStorageClassCache())
                && (record.getRestoreTime() == null || record.getRestoreTime().isBefore(LocalDateTime.now()));
    }

    private PreviewDecision evaluatePreview(HiveRecord record) {
        if (isRestorable(record)) {
            return PreviewDecision.blocked(PREVIEW_BLOCKED_FROZEN);
        }
        if (record.getSize() != null && previewMaxSizeBytes > 0 && record.getSize() > previewMaxSizeBytes) {
            return PreviewDecision.blocked(PREVIEW_BLOCKED_TOO_LARGE);
        }
        String mimeType = detectPreviewMimeType(record.getFileName());
        if (mimeType == null) {
            return PreviewDecision.blocked(PREVIEW_BLOCKED_UNSUPPORTED_TYPE);
        }
        return PreviewDecision.allowed(mimeType);
    }

    private String detectPreviewMimeType(String fileName) {
        if (StringUtils.isBlank(fileName)) {
            return null;
        }
        int index = fileName.lastIndexOf('.');
        if (index < 0 || index == fileName.length() - 1) {
            return null;
        }
        String extension = fileName.substring(index + 1).toLowerCase(Locale.ROOT);
        return MIME_BY_EXTENSION.get(extension);
    }

    private static class PreviewDecision {
        private final boolean previewable;
        private final String mimeType;
        private final String blockedReason;

        private PreviewDecision(boolean previewable, String mimeType, String blockedReason) {
            this.previewable = previewable;
            this.mimeType = mimeType;
            this.blockedReason = blockedReason;
        }

        private static PreviewDecision allowed(String mimeType) {
            return new PreviewDecision(true, mimeType, null);
        }

        private static PreviewDecision blocked(String blockedReason) {
            return new PreviewDecision(false, null, blockedReason);
        }
    }

    @DeleteMapping("/categories/{category}/files/{fileKey}")
    public ResponseEntity<Void> deleteFiles(@PathVariable("category") String category, @PathVariable("fileKey") String fileKey) {
        Optional<HiveRecord> optional = hiveRecordRepository.findByBucketNameAndFileKeyAndDeletedIsFalse(fileGroupService.resolveBucket(category), fileKey);
        if (optional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        HiveRecord hiveRecord = optional.get();
        hiveRecord.setStatus(HiveRecordStatus.TO_BE_DELETED);
        hiveRecord.setDeletable(false);
        hiveRecordRepository.save(hiveRecord);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/categories/{category}/files/unfreeze/{fileKey}")
    public ResponseEntity<Void> unfreezeFiles(@PathVariable("category") String category, @PathVariable("fileKey") String fileKey) {
        Optional<HiveRecord> optional = hiveRecordRepository.findByBucketNameAndFileKeyAndDeletedIsFalse(fileGroupService.resolveBucket(category), fileKey);
        if (optional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        HiveRecord hiveRecord = optional.get();
        refreshStorageClassCache(hiveRecord);
        if (!isArchiveStorageClass(hiveRecord.getStorageClassCache())) {
            return ResponseEntity.badRequest().build();
        }
        HiveRestoreResult restoreResult = hiveOssService.using(hiveRecord.getProvider()).restoreCheck(HiveOssTask.createTask().withKey(fileKey).withBucket(hiveRecord.getBucketName()));
        if (HiveRestoreStatus.NOT_STARTED == restoreResult.getRestoreStatus()) {
            hiveOssService.using(hiveRecord.getProvider()).restore(HiveOssTask.createTask().withKey(fileKey).withBucket(hiveRecord.getBucketName()));
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

    @GetMapping("/categories/{category}/files/unfreeze-status/{fileKey}")
    public ResponseEntity<Void> unfreezeState(@PathVariable("category") String category, @PathVariable("fileKey") String fileKey) {
        Optional<HiveRecord> optional = hiveRecordRepository.findByBucketNameAndFileKeyAndDeletedIsFalse(fileGroupService.resolveBucket(category), fileKey);
        if (optional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        HiveRecord hiveRecord = optional.get();
        refreshStorageClassCache(hiveRecord);
        if (!isArchiveStorageClass(hiveRecord.getStorageClassCache())) {
            return ResponseEntity.badRequest().build();
        }
        HiveRestoreResult restoreResult = hiveOssService.using(hiveRecord.getProvider()).restoreCheck(HiveOssTask.createTask().withKey(fileKey).withBucket(hiveRecord.getBucketName()));
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

    @PostMapping("/categories/{category}/files/download-task/{fileKey}")
    public ResponseEntity<Void> downloadTask(@PathVariable("category") String category, @PathVariable("fileKey") String fileKey) {
        Optional<HiveRecord> optional = hiveRecordRepository.findByBucketNameAndFileKeyAndDeletedIsFalse(fileGroupService.resolveBucket(category), fileKey);
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

    @GetMapping("/categories/{category}/files/download-task-status/{fileKey}")
    public ResponseEntity<HiveDownloadStatusVO> downloadTaskStatus(@PathVariable("category") String category, @PathVariable("fileKey") String fileKey) {
        Optional<HiveRecord> optional = hiveRecordRepository.findByBucketNameAndFileKeyAndDeletedIsFalse(fileGroupService.resolveBucket(category), fileKey);
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
        if (hiveRecord.getDownloadStatus() == HiveDownloadStatus.success) {
            File downloadFile = hiveDownloadService.getDownloadFile(hiveRecord);
            if (downloadFile.exists()) {
                builder.downloadUrl(downloadFile.toURI().toString().replaceFirst("^file:/", "file:///"));
            }
        }
        return ResponseEntity.ok(builder.build());
    }

    @PostMapping("/categories/{category}/files/release-local/{fileKey}")
    public ResponseEntity<HiveDownloadStatusVO> releaseLocal(@PathVariable("category") String category, @PathVariable("fileKey") String fileKey) {
        Optional<HiveRecord> optional = hiveRecordRepository.findByBucketNameAndFileKeyAndDeletedIsFalse(fileGroupService.resolveBucket(category), fileKey);
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

    @PostMapping("/categories/{category}/files/confirm-delete")
    public ResponseEntity<Void> syncLocal(@PathVariable("category") String category) {
        String bucketName = fileGroupService.resolveBucket(category);
        List<HiveRecord> recordList = hiveRecordRepository.findByStatusAndDeletedIsFalse(HiveRecordStatus.DB_ONLY);
        for (HiveRecord record : recordList) {
            if (!StringUtils.equals(record.getBucketName(), bucketName)) {
                continue;
            }
            record.setDeleted(true);
            hiveRecordRepository.save(record);
        }
        recordList = hiveRecordRepository.findByStatusAndDeletedIsFalse(HiveRecordStatus.TO_BE_DELETED);
        for (HiveRecord record : recordList) {
            if (!StringUtils.equals(record.getBucketName(), bucketName)) {
                continue;
            }
            HiveOssTask hiveOssTask = HiveOssTask.createTask().withBucket(record.getBucketName()).withKey(record.getFileKey());
            try {
                hiveOssService.using(record.getProvider()).delete(hiveOssTask);
            } catch (Exception e) {
                log.error("failed to delete {}", hiveOssTask);
                return ResponseEntity.internalServerError().build();
            }
            record.setDeleted(true);
            hiveRecordRepository.save(record);
        }
        return ResponseEntity.ok().build();
    }

    @PostMapping("/categories/{category}/files/sync-remote")
    public ResponseEntity<HiveSyncVO> syncRemote(@PathVariable("category") String category) {
        try {
            HiveSyncVO hiveSyncVO = hiveSyncService.syncRemote(fileCategoryResolver.resolveCategory(category));
            return ResponseEntity.ok(hiveSyncVO);
        } catch (Exception e) {
            log.error("sync remote failed {}", category, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/categories/{category}/files/upload")
    public ResponseEntity<HiveUploadVO> upload(@PathVariable("category") String category, HttpServletRequest request) throws IOException {
        String bucketName = fileGroupService.resolveBucket(category);
        CategoryStorageClass storageClass = fileCategoryResolver.resolveStorageClass(category);
        JakartaServletFileUpload<?, ?> upload = new JakartaServletFileUpload<>();
        upload.setFileSizeMax(100 * 1024 * 1024);
        FileItemInputIterator iter = upload.getItemIterator(request);
        FileItemInput item = iter.next();
        try {
            String fileKey = hiveUploadService.uploadSync(bucketName, storageClass, item.getName(), item.getInputStream());
            return ResponseEntity.ok(HiveUploadVO.builder().fileKey(fileKey).build());
        } catch (Exception e) {
            log.error("upload failed", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    private void refreshStorageClassCache(HiveRecord record) {
        if (StringUtils.isBlank(record.getBucketName())) {
            return;
        }
        HiveOssTask task = HiveOssTask.createTask().withBucket(record.getBucketName()).withKey(record.getFileKey());
        try {
            List<HiveOssObject> objects = hiveOssService.using(record.getProvider()).listObjects(task);
            for (HiveOssObject object : objects) {
                if (!StringUtils.equals(object.getFileKey(), record.getFileKey())) {
                    continue;
                }
                CategoryStorageClass remoteStorageClass = toStorageClass(object.getStorageClass());
                if (remoteStorageClass != record.getStorageClassCache()) {
                    record.setStorageClassCache(remoteStorageClass);
                    hiveRecordRepository.save(record);
                }
                return;
            }
        } catch (Exception e) {
            log.warn("refresh storageClass cache failed, key={}", record.getFileKey(), e);
        }
    }

    private boolean isArchiveStorageClass(CategoryStorageClass storageClass) {
        return storageClass == CategoryStorageClass.ARCHIVE;
    }

    private CategoryStorageClass toStorageClass(String storageClass) {
        if (StringUtils.isBlank(storageClass)) {
            return CategoryStorageClass.STANDARD;
        }
        return storageClass.toUpperCase(Locale.ROOT).contains(CategoryStorageClass.ARCHIVE.name())
                ? CategoryStorageClass.ARCHIVE
                : CategoryStorageClass.STANDARD;
    }
}
