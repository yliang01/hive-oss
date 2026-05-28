package cc.cc3c.hive.oss.controller;

import cc.cc3c.hive.domain.entity.FileGroupRecord;
import cc.cc3c.hive.domain.entity.HiveRecord;
import cc.cc3c.hive.domain.entity.HiveRecordImageMeta;
import cc.cc3c.hive.domain.model.CategoryStorageClass;
import cc.cc3c.hive.domain.model.HiveDownloadStatus;
import cc.cc3c.hive.domain.model.HiveRecordStatus;
import cc.cc3c.hive.oss.controller.vo.HiveRecordVO;
import cc.cc3c.hive.oss.service.FileCategoryService;
import cc.cc3c.hive.oss.service.HiveDownloadService;
import cc.cc3c.hive.oss.vendor.vo.HiveOssTask;
import cc.cc3c.hive.oss.web.CategoryContext;
import cc.cc3c.hive.oss.web.CategoryFileContext;
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

    private final FileCategoryService fileCategoryService;
    private final HiveDownloadService hiveDownloadService;
    private long previewMaxSizeBytes;

    public HiveRecordVOAssembler(FileCategoryService fileCategoryService, HiveDownloadService hiveDownloadService) {
        this.fileCategoryService = fileCategoryService;
        this.hiveDownloadService = hiveDownloadService;
    }

    @Value("${hive.preview.max-size-bytes:10485760}")
    public void setPreviewMaxSizeBytes(long previewMaxSizeBytes) {
        this.previewMaxSizeBytes = previewMaxSizeBytes;
    }

    public HiveRecordVO buildHiveRecordVO(HiveRecord record, CategoryContext context, FileGroupRecord groupRecord, Optional<HiveRecordImageMeta> imageMeta) {
        CategoryFileContext fileContext = CategoryFileContext.from(context, record);
        boolean restorable = isRestorable(fileContext);
        boolean downloadable = !restorable && (record.getDownloadStatus() == null || record.getDownloadStatus() == HiveDownloadStatus.failed);
        HiveOssTask downloadTask = record.getDownloadStatus() == HiveDownloadStatus.downloading
                ? hiveDownloadService.getDownLoadTask(record) : null;
        PreviewDecision previewDecision = evaluatePreview(fileContext);
        File downloadFile = hiveDownloadService.getDownloadFile(record.getZipped(), record.getFileName());
        String localPath = record.getDownloadStatus() != null && record.getDownloadStatus() == HiveDownloadStatus.success
                ? downloadFile.toURI().toString().replaceFirst("^file:/", "file:///") : null;
        Boolean localPathExists = null;
        if (StringUtils.isNotEmpty(localPath)) {
            localPathExists = downloadFile.exists();
        }
        String previewUrl = previewDecision.previewable()
                ? "/categories/" + context.code() + "/files/preview/" + record.getFileKey() : null;
        boolean thumbReady = imageMeta.map(m -> "READY".equals(m.getThumbStatus()) && StringUtils.isNotBlank(m.getThumbKey())).orElse(false);
        String thumbnailUrl = thumbReady
                ? "/categories/" + context.code() + "/files/preview/thumbnail/" + record.getFileKey() : null;
        return HiveRecordVO.builder()
                .category(context.code())
                .categoryLabel(context.name())
                .uiVariant(context.uiVariant())
                .groupCode(groupRecord == null ? null : groupRecord.getGroup().getGroupCode())
                .groupName(groupRecord == null ? null : groupRecord.getGroup().getGroupName())
                .fileName(record.getFileName())
                .fileKey(record.getFileKey())
                .zipped(record.getZipped())
                .size(record.getSize())
                .storageClass(context.storageClass().name())
                .status(record.getStatus().name())
                .updateTime(record.getUpdateTime())
                .unfreezeTime(record.getRestoreTime())
                .unfreezeStatus(record.getRestoreStatus() == null ? null : record.getRestoreStatus().name())
                .restorable(restorable)
                .downloadable(downloadable)
                .downloadStatus(record.getDownloadStatus() == null ? null : record.getDownloadStatus().name())
                .downloadProgress(downloadTask == null ? null : downloadTask.getProgress())
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

    public boolean isRestorable(CategoryFileContext file) {
        return CategoryStorageClass.ARCHIVE == file.category().storageClass()
                && (file.restoreTime() == null || file.restoreTime().isBefore(LocalDateTime.now()));
    }

    public PreviewDecision evaluatePreview(CategoryFileContext file) {
        String uiVariant = file.category().uiVariant();
        if ("archive".equals(uiVariant)) {
            return PreviewDecision.blocked(PREVIEW_BLOCKED_ARCHIVE);
        }
        if (HiveRecordStatus.OSS_ONLY.equals(file.status())) {
            return PreviewDecision.blocked(PREVIEW_BLOCKED_OSS_ONLY);
        }
        if (isRestorable(file)) {
            return PreviewDecision.blocked(PREVIEW_BLOCKED_FROZEN);
        }
        if (file.size() != null && previewMaxSizeBytes > 0 && file.size() > previewMaxSizeBytes) {
            return PreviewDecision.blocked(PREVIEW_BLOCKED_TOO_LARGE);
        }
        String mimeType = detectPreviewMimeType(file.fileName(), uiVariant);
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
}
