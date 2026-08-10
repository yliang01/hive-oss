package cc.cc3c.hive.oss.controller;

import cc.cc3c.hive.domain.entity.HiveRecord;
import cc.cc3c.hive.domain.entity.HiveRecordImageMeta;
import cc.cc3c.hive.domain.model.HiveRecordStatus;
import cc.cc3c.hive.domain.repository.HiveRecordImageMetaRepository;
import cc.cc3c.hive.domain.repository.HiveRecordRepository;
import cc.cc3c.hive.oss.controller.vo.FileBulkMarkDeleteRequest;
import cc.cc3c.hive.oss.controller.vo.HiveRecordVO;
import cc.cc3c.hive.oss.controller.vo.HiveRecordsVO;
import cc.cc3c.hive.oss.service.FileQueryParams;
import cc.cc3c.hive.oss.service.HiveRecordQueryService;
import cc.cc3c.hive.oss.service.HiveOssService;
import cc.cc3c.hive.oss.service.HiveRecordStateService;
import cc.cc3c.hive.oss.service.HiveRecordStateTransitionException;
import cc.cc3c.hive.oss.thumbnail.ThumbnailAsyncService;
import cc.cc3c.hive.oss.vendor.vo.HiveOssTask;
import cc.cc3c.hive.oss.web.CategoryContext;
import cc.cc3c.hive.oss.web.FileKeyNormalizer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@RestController
public class HiveFileController {

    private final HiveRecordRepository hiveRecordRepository;
    private final HiveRecordImageMetaRepository imageMetaRepository;
    private final HiveRecordQueryService hiveRecordQueryService;
    private final HiveRecordVOAssembler assembler;
    private final HiveOssService hiveOssService;
    private final ThumbnailAsyncService thumbnailAsyncService;
    private final HiveRecordStateService stateService;

    public HiveFileController(HiveRecordRepository hiveRecordRepository, HiveRecordImageMetaRepository imageMetaRepository, HiveRecordQueryService hiveRecordQueryService, HiveRecordVOAssembler assembler, HiveOssService hiveOssService, ThumbnailAsyncService thumbnailAsyncService, HiveRecordStateService stateService) {
        this.hiveRecordRepository = hiveRecordRepository;
        this.imageMetaRepository = imageMetaRepository;
        this.hiveRecordQueryService = hiveRecordQueryService;
        this.assembler = assembler;
        this.hiveOssService = hiveOssService;
        this.thumbnailAsyncService = thumbnailAsyncService;
        this.stateService = stateService;
    }

    @GetMapping("/categories/{category}/files:batch-get")
    public ResponseEntity<List<HiveRecordVO>> batchGetFiles(CategoryContext category,
                                                            @RequestParam("fileKeys") List<String> fileKeys) {
        if (fileKeys == null || fileKeys.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        List<String> normalizedKeys = FileKeyNormalizer.normalizeDistinct(fileKeys);
        if (normalizedKeys.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        List<HiveRecord> records = hiveRecordRepository
                .findWithGroupByCategory_IdAndFileKeyInAndDeletedIsFalse(category.categoryId(), normalizedKeys);
        return ResponseEntity.ok(buildRecordVOs(category, records));
    }

    @GetMapping("/categories/{category}/files")
    public HiveRecordsVO getFiles(CategoryContext category,
                                  @RequestParam(value = "groupCode", required = false) String groupCode,
                                  @RequestParam(value = "ungroupedOnly", required = false) Boolean ungroupedOnly,
                                  @RequestParam(value = "keyword", required = false) String keyword,
                                  @RequestParam("page") Integer page,
                                  @RequestParam("pageSize") Integer pageSize) {
        Long categoryId = category.categoryId();
        Page<HiveRecord> pageResult = hiveRecordQueryService.query(categoryId,
                new FileQueryParams(groupCode, Boolean.TRUE.equals(ungroupedOnly), keyword, page, pageSize));

        List<HiveRecordVO> list = buildRecordVOs(category, pageResult.stream().toList());
        return HiveRecordsVO.builder().files(list).total((int) pageResult.getTotalElements()).page(page).pageSize(pageSize).build();
    }

    private List<HiveRecordVO> buildRecordVOs(CategoryContext context, List<HiveRecord> records) {
        if (records.isEmpty()) {
            return List.of();
        }
        List<Integer> recordIds = records.stream().map(HiveRecord::getId).toList();
        Map<Integer, HiveRecordImageMeta> imageMetaMap = imageMetaRepository.findByHiveRecordIdIn(recordIds).stream()
                .collect(Collectors.toMap(HiveRecordImageMeta::getHiveRecordId, m -> m));
        return records.stream()
                .map(record -> assembler.buildHiveRecordVO(record, context,
                        Optional.ofNullable(imageMetaMap.get(record.getId()))))
                .toList();
    }

    @PostMapping("/categories/{category}/files:mark-delete")
    public ResponseEntity<?> bulkMarkDelete(CategoryContext category,
                                                @RequestBody FileBulkMarkDeleteRequest request) {
        if (request.getFileKeys() == null || request.getFileKeys().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        List<String> normalizedKeys = FileKeyNormalizer.normalizeDistinct(request.getFileKeys());
        if (normalizedKeys.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        List<HiveRecord> records = hiveRecordRepository
                .findByCategory_IdAndFileKeyInAndDeletedIsFalse(category.categoryId(), normalizedKeys);
        if (records.size() != normalizedKeys.size()) {
            return ResponseEntity.badRequest().build();
        }
        try {
            for (HiveRecord record : records) {
                stateService.markDelete(record);
            }
        } catch (HiveRecordStateTransitionException e) {
            return ResponseEntity.status(409).body(Map.of("error", e.getMessage()));
        }
        hiveRecordRepository.saveAll(records);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/categories/{category}/files/confirm-delete")
    public ResponseEntity<Void> confirmDelete(CategoryContext category,
                                              @RequestBody FileBulkMarkDeleteRequest request) {
        if (request.getFileKeys() == null || request.getFileKeys().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        List<String> normalizedKeys = FileKeyNormalizer.normalizeDistinct(request.getFileKeys());
        if (normalizedKeys.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        List<HiveRecord> recordList = hiveRecordRepository
                .findByCategory_IdAndFileKeyInAndDeletedIsFalse(category.categoryId(), normalizedKeys);
        if (recordList.size() != normalizedKeys.size()
                || recordList.stream().anyMatch(record -> !HiveRecordStatus.TO_BE_DELETED.equals(record.getStatus()))) {
            return ResponseEntity.badRequest().build();
        }
        for (HiveRecord record : recordList) {
            String bucketName = record.getCategory().getBucketName();
            String fileKey = record.getFileKey();
            HiveRecordImageMeta meta = imageMetaRepository.findByHiveRecordId(record.getId()).orElse(null);
            if (meta != null && StringUtils.isNotBlank(meta.getThumbKey())) {
                HiveOssTask thumbTask = HiveOssTask.createTask()
                        .withBucket(bucketName)
                        .withKey(meta.getThumbKey());
                try {
                    hiveOssService.using(record.getProvider()).delete(thumbTask);
                } catch (Exception e) {
                    log.error("failed to delete thumbnail {}", thumbTask, e);
                    return ResponseEntity.internalServerError().build();
                }
            }
            if (meta != null) {
                imageMetaRepository.delete(meta);
            }
            HiveOssTask hiveOssTask = HiveOssTask.createTask()
                    .withBucket(bucketName)
                    .withKey(fileKey);
            try {
                hiveOssService.using(record.getProvider()).delete(hiveOssTask);
            } catch (Exception e) {
                log.error("failed to delete {}", hiveOssTask);
                return ResponseEntity.internalServerError().build();
            }
            record.clearGroup();
            thumbnailAsyncService.deleteCacheFiles(fileKey);
            stateService.confirmDeleted(record);
            hiveRecordRepository.save(record);
        }
        return ResponseEntity.ok().build();
    }

    @PostMapping("/categories/{category}/files:cleanup-local")
    public ResponseEntity<?> cleanupLocalRecords(CategoryContext category,
                                                 @RequestBody FileBulkMarkDeleteRequest request) {
        if (request.getFileKeys() == null || request.getFileKeys().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        List<String> normalizedKeys = FileKeyNormalizer.normalizeDistinct(request.getFileKeys());
        if (normalizedKeys.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        List<HiveRecord> records = hiveRecordRepository
                .findByCategory_IdAndFileKeyInAndDeletedIsFalse(category.categoryId(), normalizedKeys);
        if (records.size() != normalizedKeys.size()) {
            return ResponseEntity.badRequest().build();
        }
        try {
            for (HiveRecord record : records) {
                record.clearGroup();
                imageMetaRepository.findByHiveRecordId(record.getId()).ifPresent(imageMetaRepository::delete);
                thumbnailAsyncService.deleteCacheFiles(record.getFileKey());
                stateService.cleanupLocalRecord(record);
            }
        } catch (HiveRecordStateTransitionException e) {
            return ResponseEntity.status(409).body(Map.of("error", e.getMessage()));
        }
        hiveRecordRepository.saveAll(records);
        return ResponseEntity.ok().build();
    }
}
