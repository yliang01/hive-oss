package cc.cc3c.hive.oss.business.controller;

import cc.cc3c.hive.domain.entity.HiveRecord;
import cc.cc3c.hive.domain.entity.HiveRecordImageMeta;
import cc.cc3c.hive.domain.model.CategoryStorageClass;
import cc.cc3c.hive.domain.model.HiveDownloadStatus;
import cc.cc3c.hive.domain.model.HiveRecordStatus;
import cc.cc3c.hive.oss.business.controller.vo.HiveRecordVO;
import cc.cc3c.hive.oss.business.service.HiveDownloadService;
import cc.cc3c.hive.oss.business.service.HiveLocalCheckService.LocalCheckResult;
import cc.cc3c.hive.oss.business.vendor.vo.HiveOssTask;
import cc.cc3c.hive.oss.business.web.CategoryContext;
import cc.cc3c.hive.oss.business.web.CategoryFileContext;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Component
public class HiveRecordVOAssembler {

    static final String PREVIEW_BLOCKED_ARCHIVE = "ARCHIVE";
    static final String PREVIEW_BLOCKED_LOCAL_MISSING = "LOCAL_MISSING";
    static final String PREVIEW_BLOCKED_ZIP_ARCHIVE = "ZIP_ARCHIVE";
    static final String PREVIEW_BLOCKED_RECORD_NOT_READY = "RECORD_NOT_READY";
    static final String PREVIEW_BLOCKED_TOO_LARGE = "TOO_LARGE";
    static final String PREVIEW_BLOCKED_UNSUPPORTED_TYPE = "UNSUPPORTED_TYPE";
    static final String PREVIEW_BLOCKED_OSS_ONLY = "OSS_ONLY";
    static final Map<String, String> MIME_BY_EXTENSION = Map.of(
            "jpg", MediaType.IMAGE_JPEG_VALUE,
            "jpeg", MediaType.IMAGE_JPEG_VALUE,
            "png", MediaType.IMAGE_PNG_VALUE,
            "gif", MediaType.IMAGE_GIF_VALUE,
            "webp", "image/webp"
    );
    static final Map<String, String> FILE_MIME_BY_EXTENSION = Map.ofEntries(
            Map.entry("pdf", "application/pdf"),
            Map.entry("txt", MediaType.TEXT_PLAIN_VALUE),
            Map.entry("log", MediaType.TEXT_PLAIN_VALUE),
            Map.entry("json", "application/json"),
            Map.entry("xml", "text/xml"),
            Map.entry("csv", "text/csv"),
            Map.entry("yaml", "text/yaml"),
            Map.entry("yml", "text/yaml"),
            Map.entry("md", "text/markdown")
    );

    private final HiveDownloadService hiveDownloadService;
    private long previewMaxSizeBytes;

    public HiveRecordVOAssembler(HiveDownloadService hiveDownloadService) {
        this.hiveDownloadService = hiveDownloadService;
    }

    @Value("${hive.preview.max-size-bytes:10485760}")
    public void setPreviewMaxSizeBytes(long previewMaxSizeBytes) {
        this.previewMaxSizeBytes = previewMaxSizeBytes;
    }

    public HiveRecordVO buildHiveRecordVO(HiveRecord record, CategoryContext context,
                                           Optional<HiveRecordImageMeta> imageMeta, LocalCheckResult localCheck) {
        CategoryFileContext fileContext = CategoryFileContext.from(context, record);
        boolean restorable = hiveDownloadService.isRestoreRequired(fileContext);
        boolean localFileAvailable = hiveDownloadService.isDownloadFileAvailable(fileContext);
        boolean downloadable = hiveDownloadService.isDownloadable(fileContext, record.getDownloadStatus());
        HiveOssTask downloadTask = record.getDownloadStatus() == HiveDownloadStatus.downloading
                ? hiveDownloadService.getDownLoadTask(record) : null;
        PreviewDecision previewDecision = evaluatePreview(fileContext, localFileAvailable);
        String previewUrl = previewDecision.previewable()
                ? "/api/categories/" + context.code() + "/files/preview/" + record.getFileKey() : null;
        boolean thumbReady = imageMeta.map(m -> "READY".equals(m.getThumbStatus()) && StringUtils.isNotBlank(m.getThumbKey())).orElse(false);
        String thumbnailUrl = isThumbnailAvailable(fileContext, thumbReady)
                ? "/api/categories/" + context.code() + "/files/preview/thumbnail/" + record.getFileKey() : null;
        return HiveRecordVO.builder()
                .groupCode(record.getGroup() == null ? null : record.getGroup().getGroupCode())
                .groupName(record.getGroup() == null ? null : record.getGroup().getGroupName())
                .fileName(record.getFileName())
                .fileKey(record.getFileKey())
                .zipped(record.getZipped())
                .size(record.getSize())
                .originalSize(record.getOriginalSize())
                .status(record.getStatus().name())
                .updateTime(record.getUpdateTime())
                .unfreezeTime(record.getRestoreTime())
                .unfreezeStatus(record.getRestoreStatus() == null ? null : record.getRestoreStatus().name())
                .restorable(restorable)
                .downloadable(downloadable)
                .downloadStatus(record.getDownloadStatus() == null ? null : record.getDownloadStatus().name())
                .downloadProgress(downloadTask == null ? null : downloadTask.getProgress())
                .localFileAvailable(localFileAvailable)
                .localCheckStatus(localCheck == null ? null : localCheck.status().name())
                .localCheckObservedSize(localCheck == null ? null : localCheck.observedSize())
                .deletable(record.getDeletable())
                .previewable(previewDecision.previewable())
                .previewUrl(previewUrl)
                .thumbnailUrl(thumbnailUrl)
                .mimeType(previewDecision.mimeType())
                .previewBlockedReason(previewDecision.blockedReason())
                .build();
    }

    public PreviewDecision evaluatePreview(CategoryFileContext file, boolean localFileAvailable) {
        String uiVariant = file.category().uiVariant();
        if (CategoryStorageClass.ARCHIVE == file.category().storageClass() || "archive".equals(uiVariant)) {
            return PreviewDecision.blocked(PREVIEW_BLOCKED_ARCHIVE);
        }
        if (HiveRecordStatus.OSS_ONLY.equals(file.status())) {
            return PreviewDecision.blocked(PREVIEW_BLOCKED_OSS_ONLY);
        }
        if (HiveRecordStatus.UPLOADED != file.status()) {
            return PreviewDecision.blocked(PREVIEW_BLOCKED_RECORD_NOT_READY);
        }
        if (Boolean.TRUE.equals(file.zipped())) {
            return PreviewDecision.blocked(PREVIEW_BLOCKED_ZIP_ARCHIVE);
        }
        if (file.size() != null && previewMaxSizeBytes > 0 && file.size() > previewMaxSizeBytes) {
            return PreviewDecision.blocked(PREVIEW_BLOCKED_TOO_LARGE);
        }
        String mimeType = detectPreviewMimeType(file.fileName(), uiVariant);
        if (mimeType == null) {
            return PreviewDecision.blocked(PREVIEW_BLOCKED_UNSUPPORTED_TYPE);
        }
        if (!localFileAvailable) {
            return PreviewDecision.blocked(PREVIEW_BLOCKED_LOCAL_MISSING);
        }
        return PreviewDecision.allowed(mimeType);
    }

    public boolean isThumbnailAvailable(CategoryFileContext file, boolean thumbReady) {
        return thumbReady
                && "image".equalsIgnoreCase(file.category().uiVariant())
                && CategoryStorageClass.ARCHIVE != file.category().storageClass()
                && HiveRecordStatus.UPLOADED == file.status();
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
}
