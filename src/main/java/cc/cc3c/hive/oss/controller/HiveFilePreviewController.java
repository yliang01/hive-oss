package cc.cc3c.hive.oss.controller;

import cc.cc3c.hive.domain.repository.HiveRecordImageMetaRepository;
import cc.cc3c.hive.oss.service.HiveDownloadService;
import cc.cc3c.hive.oss.web.CategoryFileContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.FileNotFoundException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Slf4j
@RestController
public class HiveFilePreviewController {

    private final HiveRecordImageMetaRepository imageMetaRepository;
    private final HiveDownloadService hiveDownloadService;
    private final HiveRecordVOAssembler assembler;

    public HiveFilePreviewController(HiveRecordImageMetaRepository imageMetaRepository,
                                       HiveDownloadService hiveDownloadService,
                                       HiveRecordVOAssembler assembler) {
        this.imageMetaRepository = imageMetaRepository;
        this.hiveDownloadService = hiveDownloadService;
        this.assembler = assembler;
    }

    @GetMapping("/categories/{category}/files/preview/thumbnail/{*fileKey}")
    public ResponseEntity<StreamingResponseBody> previewThumbnail(CategoryFileContext file) {
        var metaOpt = imageMetaRepository.findByHiveRecordId(file.recordId());
        boolean thumbReady = metaOpt.map(meta -> "READY".equals(meta.getThumbStatus())
                && StringUtils.isNotBlank(meta.getThumbKey())).orElse(false);
        if (!assembler.isThumbnailAvailable(file, thumbReady)) {
            return ResponseEntity.notFound().build();
        }
        StreamingResponseBody body = outputStream -> {
            try {
                hiveDownloadService.streamPreviewThumbnail(file, outputStream);
            } catch (Exception e) {
                log.error("thumbnail stream failed {}", file.key(), e);
                throw new IOException("thumbnail stream failed", e);
            }
        };
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "private,no-store")
                .contentType(MediaType.IMAGE_JPEG)
                .body(body);
    }

    @GetMapping("/categories/{category}/files/preview/{*fileKey}")
    public ResponseEntity<?> previewFile(CategoryFileContext file) {
        PreviewDecision previewDecision = assembler.evaluatePreview(file, hiveDownloadService.isDownloadFileAvailable(file));
        if (!previewDecision.previewable()) {
            if (HiveRecordVOAssembler.PREVIEW_BLOCKED_TOO_LARGE.equals(previewDecision.blockedReason())) {
                return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(Map.of("error", "文件超过预览大小限制"));
            }
            if (HiveRecordVOAssembler.PREVIEW_BLOCKED_LOCAL_MISSING.equals(previewDecision.blockedReason())
                    || HiveRecordVOAssembler.PREVIEW_BLOCKED_RECORD_NOT_READY.equals(previewDecision.blockedReason())) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", previewBlockedMessage(previewDecision.blockedReason())));
            }
            return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(Map.of("error", previewBlockedMessage(previewDecision.blockedReason())));
        }
        MediaType mediaType = MediaType.parseMediaType(previewDecision.mimeType());
        String fileName = StringUtils.defaultIfBlank(file.fileName(), file.key());
        long contentLength;
        try {
            contentLength = hiveDownloadService.getAvailableDownloadFileSize(file);
        } catch (FileNotFoundException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "请先下载到本地"));
        } catch (IOException | IllegalArgumentException e) {
            log.error("local preview file unavailable {}", file.key(), e);
            return ResponseEntity.internalServerError().body(Map.of("error", "无法读取本地预览文件"));
        }
        StreamingResponseBody body = outputStream -> {
            try {
                hiveDownloadService.streamLocalPreview(file, outputStream);
            } catch (Exception e) {
                log.error("preview stream failed {}", file.key(), e);
                throw new IOException("preview stream failed", e);
            }
        };
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "private,no-store")
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(fileName, StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .contentLength(contentLength)
                .contentType(mediaType)
                .body(body);
    }

    private String previewBlockedMessage(String blockedReason) {
        return switch (blockedReason) {
            case HiveRecordVOAssembler.PREVIEW_BLOCKED_LOCAL_MISSING -> "请先下载到本地";
            case HiveRecordVOAssembler.PREVIEW_BLOCKED_ARCHIVE -> "归档分类不支持在线预览";
            case HiveRecordVOAssembler.PREVIEW_BLOCKED_ZIP_ARCHIVE -> "ZIP 文件不支持在线预览";
            case HiveRecordVOAssembler.PREVIEW_BLOCKED_OSS_ONLY -> "仅云端存在，无法预览";
            case HiveRecordVOAssembler.PREVIEW_BLOCKED_RECORD_NOT_READY -> "文件当前状态不允许预览";
            case HiveRecordVOAssembler.PREVIEW_BLOCKED_UNSUPPORTED_TYPE -> "当前文件类型不支持预览";
            default -> "文件不可预览";
        };
    }
}
