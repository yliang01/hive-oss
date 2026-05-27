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
import java.nio.charset.StandardCharsets;

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
        if (metaOpt.isEmpty() || !"READY".equals(metaOpt.get().getThumbStatus()) || StringUtils.isBlank(metaOpt.get().getThumbKey())) {
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
    public ResponseEntity<StreamingResponseBody> previewFile(CategoryFileContext file) {
        String uiVariant = file.category().uiVariant();
        PreviewDecision previewDecision = assembler.evaluatePreview(file);
        if (!previewDecision.previewable()) {
            if (HiveRecordVOAssembler.PREVIEW_BLOCKED_FROZEN.equals(previewDecision.blockedReason())) {
                return ResponseEntity.status(HttpStatus.LOCKED).build();
            }
            if (HiveRecordVOAssembler.PREVIEW_BLOCKED_TOO_LARGE.equals(previewDecision.blockedReason())) {
                return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).build();
            }
            return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).build();
        }
        MediaType mediaType = MediaType.parseMediaType(previewDecision.mimeType());
        String fileName = StringUtils.defaultIfBlank(file.fileName(), file.key());
        StreamingResponseBody body = outputStream -> {
            try {
                hiveDownloadService.streamPreview(file, outputStream, uiVariant);
            } catch (Exception e) {
                log.error("preview stream failed {}", file.key(), e);
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
}
