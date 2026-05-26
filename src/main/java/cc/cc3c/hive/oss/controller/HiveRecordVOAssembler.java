package cc.cc3c.hive.oss.controller;

import cc.cc3c.hive.domain.entity.FileCategoryEntity;
import cc.cc3c.hive.domain.entity.FileGroupRecord;
import cc.cc3c.hive.domain.entity.HiveRecord;
import cc.cc3c.hive.domain.entity.HiveRecordImageMeta;
import cc.cc3c.hive.domain.model.CategoryStorageClass;
import cc.cc3c.hive.domain.model.HiveDownloadStatus;
import cc.cc3c.hive.domain.model.HiveRecordStatus;
import cc.cc3c.hive.oss.controller.vo.HiveRecordVO;
import cc.cc3c.hive.oss.service.FileCategoryResolver;
import cc.cc3c.hive.oss.service.HiveDownloadService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.File;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Component
public class HiveRecordVOAssembler {

    static final String PREVIEW_BLOCKED_FROZEN = "FROZEN";
    static final String PREVIEW_BLOCKED_TOO_LARGE = "TOO_LARGE";
    static final String PREVIEW_BLOCKED_UNSUPPORTED_TYPE = "UNSUPPORTED_TYPE";
    static final String PREVIEW_BLOCKED_OSS_ONLY = "OSS_ONLY";
    static final String PREVIEW_BLOCKED_ARCHIVE = "ARCHIVE";
    static final Map<String, String> MIME_BY_EXTENSION = Map.of(
            "jpg",  MediaType.IMAGE_JPEG_VALUE,
            "jpeg", MediaType.IMAGE_JPEG_VALUE,
            "png",  MediaType.IMAGE_PNG_VALUE,
            "gif",  MediaType.IMAGE_GIF_VALUE,
            "webp", "image/webp"
    );
    static final Map<String, String> FILE_MIME_BY_EXTENSION = Map.ofEntries(
            Map.entry("pdf",  "application/pdf"),
            Map.entry("txt",  MediaType.TEXT_PLAIN_VALUE),
            Map.entry("log",  MediaType.TEXT_PLAIN_VALUE),
            Map.entry("json", "application/json"),
            Map.entry("xml",  "text/xml"),
            Map.entry("csv",  "text/csv"),
            Map.entry("yaml", "text/yaml"),
            Map.entry("yml",  "text/yaml"),
            Map.entry("md",   "text/markdown")
    );

    private final FileCategoryResolver fileCategoryResolver;
    private final HiveDownloadService hiveDownloadService;
    private long previewMaxSizeBytes;

    public HiveRecordVOAssembler(FileCategoryResolver fileCategoryResolver, HiveDownloadService hiveDownloadService) {
        this.fileCategoryResolver = fileCategoryResolver;
        this.hiveDownloadService = hiveDownloadService;
    }

    @Value("${hive.preview.max-size-bytes:10485760}")
    public void setPreviewMaxSizeBytes(long previewMaxSizeBytes) {
        this.previewMaxSizeBytes = previewMaxSizeBytes;
    }

    public HiveRecordVO buildHiveRecordVO(HiveRecord record, String category, FileGroupRecord groupRecord, Optional<HiveRecordImageMeta> imageMeta) {
        CategoryStorageClass storageClass = fileCategoryResolver.resolveRecordStorageClass(record);
        boolean restorable = isRestorable(record);
        boolean downloadable = !restorable && (record.getDownloadStatus() == null || record.getDownloadStatus() == HiveDownloadStatus.failed);
        FileCategoryEntity finalCategory = category != null
                ? fileCategoryResolver.resolveCategory(category)
                : fileCategoryResolver.resolveFallbackCategory(record);
        String uiVariant = finalCategory == null ? null : finalCategory.getUiVariant();
        PreviewDecision previewDecision = evaluatePreview(record, uiVariant);

        File downloadFile = hiveDownloadService.getDownloadFile(record);
        String localPath = record.getDownloadStatus() != null && record.getDownloadStatus() == HiveDownloadStatus.success
                ? downloadFile.toURI().toString().replaceFirst("^file:/", "file:///") : null;
        Boolean localPathExists = null;
        if (StringUtils.isNotEmpty(localPath)) {
            localPathExists = downloadFile.exists();
        }
        String finalCategoryCode = finalCategory == null ? null : finalCategory.getCode();
        String previewUrl = previewDecision.previewable() && finalCategoryCode != null
                ? "/categories/" + finalCategoryCode + "/files/preview/" + record.getFileKey() : null;
        boolean thumbReady = imageMeta.map(m -> "READY".equals(m.getThumbStatus()) && StringUtils.isNotBlank(m.getThumbKey())).orElse(false);
        String thumbnailUrl = thumbReady && finalCategoryCode != null
                ? "/categories/" + finalCategoryCode + "/files/preview/thumbnail/" + record.getFileKey() : null;
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
                .storageClass(storageClass.name())
                .status(record.getStatus().name())
                .updateTime(record.getUpdateTime())
                .unfreezeTime(record.getRestoreTime())
                .restorable(restorable)
                .downloadable(downloadable)
                .localPath(localPath)
                .localPathExists(localPathExists)
                .deletable(record.getDeletable())
                .previewable(previewDecision.previewable())
                .previewUrl(previewUrl)
                .thumbnailUrl(thumbnailUrl)
                .thumbKey(imageMeta.map(HiveRecordImageMeta::getThumbKey).orElse(null))
                .thumbStatus(imageMeta.map(HiveRecordImageMeta::getThumbStatus).orElse(null))
                .imageWidth(imageMeta.map(HiveRecordImageMeta::getImageWidth).orElse(null))
                .imageHeight(imageMeta.map(HiveRecordImageMeta::getImageHeight).orElse(null))
                .mimeType(previewDecision.mimeType())
                .previewBlockedReason(previewDecision.blockedReason())
                .previewMaxSizeBytes(previewMaxSizeBytes)
                .build();
    }

    public boolean isRestorable(HiveRecord record) {
        return isArchiveStorageClass(fileCategoryResolver.resolveRecordStorageClass(record))
                && (record.getRestoreTime() == null || record.getRestoreTime().isBefore(LocalDateTime.now()));
    }

    public boolean isArchiveStorageClass(CategoryStorageClass storageClass) {
        return storageClass == CategoryStorageClass.ARCHIVE;
    }

    public PreviewDecision evaluatePreview(HiveRecord record, String uiVariant) {
        if ("archive".equals(uiVariant)) {
            return PreviewDecision.blocked(PREVIEW_BLOCKED_ARCHIVE);
        }
        if (HiveRecordStatus.OSS_ONLY.equals(record.getStatus())) {
            return PreviewDecision.blocked(PREVIEW_BLOCKED_OSS_ONLY);
        }
        if (isRestorable(record)) {
            return PreviewDecision.blocked(PREVIEW_BLOCKED_FROZEN);
        }
        if (record.getSize() != null && previewMaxSizeBytes > 0 && record.getSize() > previewMaxSizeBytes) {
            return PreviewDecision.blocked(PREVIEW_BLOCKED_TOO_LARGE);
        }
        String mimeType = detectPreviewMimeType(record.getFileName(), uiVariant);
        if (mimeType == null) {
            return PreviewDecision.blocked(PREVIEW_BLOCKED_UNSUPPORTED_TYPE);
        }
        return PreviewDecision.allowed(mimeType);
    }

    public String detectPreviewMimeType(String fileName, String uiVariant) {
        if (StringUtils.isBlank(fileName)) {
            return null;
        }
        int index = fileName.lastIndexOf('.');
        if (index < 0 || index == fileName.length() - 1) {
            return null;
        }
        String extension = fileName.substring(index + 1).toLowerCase(Locale.ROOT);
        String mime = MIME_BY_EXTENSION.get(extension);
        if (mime == null && "file".equals(uiVariant)) {
            mime = FILE_MIME_BY_EXTENSION.get(extension);
        }
        return mime;
    }

    /** Normalize {*fileKey} path variable — strips leading slash if present. */
    public static String normalizeFileKey(String fileKey) {
        if (fileKey == null) {
            return null;
        }
        return fileKey.startsWith("/") ? fileKey.substring(1) : fileKey;
    }
}
