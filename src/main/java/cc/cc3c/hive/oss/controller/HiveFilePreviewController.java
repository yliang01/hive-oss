package cc.cc3c.hive.oss.controller;

import cc.cc3c.hive.domain.entity.HiveRecord;
import cc.cc3c.hive.domain.entity.HiveRecordImageMeta;
import cc.cc3c.hive.domain.repository.HiveRecordImageMetaRepository;
import cc.cc3c.hive.domain.repository.HiveRecordRepository;
import cc.cc3c.hive.oss.service.FileGroupService;
import cc.cc3c.hive.oss.service.HiveDownloadService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

@Slf4j
@RestController
public class HiveFilePreviewController {

    private final HiveRecordRepository hiveRecordRepository;
    private final HiveRecordImageMetaRepository imageMetaRepository;
    private final FileGroupService fileGroupService;
    private final HiveDownloadService hiveDownloadService;
    private final HiveRecordVOAssembler assembler;

    public HiveFilePreviewController(HiveRecordRepository hiveRecordRepository,
                                     HiveRecordImageMetaRepository imageMetaRepository,
                                     FileGroupService fileGroupService,
                                     HiveDownloadService hiveDownloadService,
                                     HiveRecordVOAssembler assembler) {
        this.hiveRecordRepository = hiveRecordRepository;
        this.imageMetaRepository = imageMetaRepository;
        this.fileGroupService = fileGroupService;
        this.hiveDownloadService = hiveDownloadService;
        this.assembler = assembler;
    }

    @GetMapping("/categories/{category}/files/preview/thumbnail/{*fileKey}")
    public ResponseEntity<StreamingResponseBody> previewThumbnail(@PathVariable("category") String category,
                                                                   @PathVariable("fileKey") String fileKey) {
        String key = HiveRecordVOAssembler.normalizeFileKey(fileKey);
        Optional<HiveRecord> optional = hiveRecordRepository.findByBucketNameAndFileKeyAndDeletedIsFalse(
                fileGroupService.resolveBucket(category), key);
        if (optional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        HiveRecord hiveRecord = optional.get();
        Optional<HiveRecordImageMeta> metaOpt = imageMetaRepository.findByHiveRecordId(hiveRecord.getId());
        if (metaOpt.isEmpty() || !"READY".equals(metaOpt.get().getThumbStatus()) || StringUtils.isBlank(metaOpt.get().getThumbKey())) {
            return ResponseEntity.notFound().build();
        }
        StreamingResponseBody body = outputStream -> {
            try {
                hiveDownloadService.streamPreviewThumbnail(hiveRecord, outputStream);
            } catch (Exception e) {
                log.error("thumbnail stream failed {}", key, e);
                throw new IOException("thumbnail stream failed", e);
            }
        };
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "private,no-store")
                .contentType(MediaType.IMAGE_JPEG)
                .body(body);
    }

    @GetMapping("/categories/{category}/files/preview/{*fileKey}")
    public ResponseEntity<StreamingResponseBody> previewFile(@PathVariable("category") String category,
                                                              @PathVariable("fileKey") String fileKey) {
        String key = HiveRecordVOAssembler.normalizeFileKey(fileKey);
        Optional<HiveRecord> optional = hiveRecordRepository.findByBucketNameAndFileKeyAndDeletedIsFalse(
                fileGroupService.resolveBucket(category), key);
        if (optional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        HiveRecord hiveRecord = optional.get();
        String uiVariant = Optional.ofNullable(fileGroupService.resolveCategory(category))
                .map(cat -> cat.getUiVariant()).orElse(null);
        PreviewDecision previewDecision = assembler.evaluatePreview(hiveRecord, uiVariant);
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
        String fileName = StringUtils.defaultIfBlank(hiveRecord.getFileName(), hiveRecord.getFileKey());
        StreamingResponseBody body = outputStream -> {
            try {
                hiveDownloadService.streamPreview(hiveRecord, outputStream);
            } catch (Exception e) {
                log.error("preview stream failed {}", key, e);
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
